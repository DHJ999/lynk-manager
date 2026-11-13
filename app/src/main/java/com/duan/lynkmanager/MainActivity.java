package com.duan.lynkmanager;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {

    private static final int BG = 0xFF101418;
    private static final int CARD = 0xFF1B2128;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int OK = 0xFF4CAF50;
    private static final int LINE = 0xFF2C343E;

    /** 反馈邮箱。 */
    private static final String EMAIL = "hjd2002@yeah.net";
    /** 收款码品牌色，用于标签文字。 */
    private static final int WECHAT = 0xFF07C160;
    private static final int ALIPAY = 0xFF1677FF;

    private final List<AppInfo> all = new ArrayList<>();
    private final List<AppInfo> shown = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private AppListAdapter adapter;
    private EditText search;
    private GridView grid;
    private TextView stats;
    private Button adbBtn;
    private Button refreshBtn;
    /** 根布局，供 onWindowFocusChanged 里做高度校正。 */
    private LinearLayout rootLayout;
    /** 正在读取/刷新应用列表，防止重复点击叠加扫描。 */
    private volatile boolean loading = false;
    /** 上一次 getInstalledPackages 返回的原始包数量。-1 表示还没跑过。 */
    private int lastRawCount = -1;
    /** 实际命中的采集来源，显示在统计栏便于现场判断。 */
    private String sourceTag = "";
    private final Button[] chips = new Button[3];
    private int mode = 0; // 0 全部 / 1 用户 / 2 系统

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 回到 1.0.0 的写法：直接 setContentView(buildUi())。
        // 中间几版套过 FrameLayout 外壳 + root.setLayoutParams(MATCH_PARENT)，
        // 结果 grid 高度被算成 0（数据 126 个全在、列数正确，就是一行都不显示）。
        // 1.0.0 在车机上显示正常，证明这条路是通的，不再加壳。
        setContentView(buildUi());
        loadApps();

        // 诊断入口：am start ... --ez probe true
        // 用无害的 persist.sv.debug.lm_probe 验证「应用身份能否 setprop」，
        // 不碰真正的 adb 开关，测完清空。
        if (getIntent() != null && getIntent().getBooleanExtra("probe", false)) {
            final String r = sh("setprop persist.sv.debug.lm_probe 7");
            final String v = prop("persist.sv.debug.lm_probe");
            android.util.Log.i("LynkMgr", "PROBE setprop rc=" + r + " readback=" + v);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAdbButton();
        // 布局把网格挤成 0 高时界面看起来是「空的」，但日志里数据是全的。
        // 这里把关键尺寸打出来，方便现场一眼区分「没数据」和「没显示」。
        if (grid != null) {
            grid.postDelayed(new Runnable() {
                @Override
                public void run() {
                    StringBuilder sb = new StringBuilder();
                    sb.append("grid=").append(grid.getWidth()).append("x").append(grid.getHeight())
                            .append(" shown=").append(shown.size())
                            .append(" cols=").append(grid.getNumColumns())
                            .append(" colW=").append(grid.getColumnWidth())
                            .append(" dpi=").append(getResources().getDisplayMetrics().densityDpi)
                            .append(" winH=").append(getWindow().getDecorView().getHeight());
                    android.util.Log.i("LynkMgr", sb.toString());
                    View rootView = grid.getRootView();
                    if (rootView instanceof ViewGroup) {
                        dumpChildHeights((ViewGroup) rootView, "  ", 0);
                    }
                }
            }, 1500);
        }
    }

    /**
     * 窗口拿到焦点时布局已经走完，root 有真实高度。
     * weight 在这台车机的窗口模式下不可靠（会算成 0 高），
     * 所以在这里按「实测剩余空间」给 GridView 定高。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && rootLayout != null) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    fitGridHeight(rootLayout);
                }
            });
        }
    }

    // ---------------- UI ----------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    /** 递归打印子 View 高度，定位「谁把空间占光了」。 */
    private void dumpChildHeights(ViewGroup vg, String indent, int depth) {
        if (depth > 3) {
            return;
        }
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            String cls = c.getClass().getSimpleName();
            android.util.Log.i("LynkMgr", indent + cls + " h=" + c.getHeight()
                    + " w=" + c.getWidth()
                    + " measuredH=" + c.getMeasuredHeight());
            if (c instanceof ViewGroup && depth < 3) {
                dumpChildHeights((ViewGroup) c, indent + "  ", depth + 1);
            }
        }
    }

    /**
     * 按实测剩余高度给 GridView 定高。
     *
     * 车机上 weight 分配会把 GridView 算成 0 高（width 正常、height=0），
     * 这里改成：容器高度 - 其它所有子 View 的高度 = GridView 应得高度。
     */
    private void fitGridHeightLegacy(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup vg = (ViewGroup) root;
        int rootH = vg.getHeight();
        if (rootH <= 0) {
            // 还没量完，下一帧再试
            grid.post(new Runnable() {
                @Override
                public void run() {
                    fitGridHeight(root);
                }
            });
            return;
        }
        int used = 0;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c == grid) {
                continue;
            }
            used += c.getMeasuredHeight();
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                used += mlp.topMargin + mlp.bottomMargin;
            }
        }
        int target = Math.max(0, rootH - used);
        ViewGroup.LayoutParams lp = grid.getLayoutParams();
        if (lp != null && lp.height != target) {
            lp.height = target;
            grid.setLayoutParams(lp);
            android.util.Log.i("LynkMgr", "fitGridHeight rootH=" + rootH
                    + " used=" + used + " gridH=" + target);
        }
    }

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(null, Typeface.BOLD);
        }
        return t;
    }

    /** 顶栏小按钮：LINE 底、白字、44dp 高、左右 20dp 内边距，四个按钮共用同一套尺寸。 */
    private Button barBtn(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(0xFFFFFFFF);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        // 中文默认带 font padding，会看起来偏上/偏下，关掉后垂直居中更准
        b.setIncludeFontPadding(false);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setBackground(box(LINE, 10));
        return b;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        // 和 1.0.0 一致：不显式设 LayoutParams，交给 setContentView 决定。
        // 之前手工塞 ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        // 反而让 weight 分配失效，grid 高度被算成 0。

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int bp = dp(16);
        bar.setPadding(bp, dp(14), bp, dp(12));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = tv("Lynk 管家", 22, TEXT, true);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button transferBtn = barBtn("传文件");
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        trlp.rightMargin = dp(8);
        bar.addView(transferBtn, trlp);
        transferBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LanTransferActivity.class));
            }
        });

        final Button feedBtn = barBtn("反馈");
        LinearLayout.LayoutParams feedLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        feedLp.rightMargin = dp(8);
        bar.addView(feedBtn, feedLp);
        feedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFeedback();
            }
        });

        final Button appreciateBtn = barBtn("赞赏");
        LinearLayout.LayoutParams appreciateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        appreciateLp.rightMargin = dp(8);
        bar.addView(appreciateBtn, appreciateLp);
        appreciateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppreciate();
            }
        });

        adbBtn = barBtn("ADB");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        alp.rightMargin = dp(8);
        bar.addView(adbBtn, alp);
        // 单击只查看状态，绝不写设置。
        // 写 ADB_ENABLED 会让 adbd 重启、USB gadget 重新枚举，
        // 正在连着电脑的 adb 连接会当场断掉（车机上尤其明显）。
        adbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdbStatus();
            }
        });
        // 长按才允许切换，且必须二次确认。
        adbBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmToggleAdb();
                return true;
            }
        });

        // 搜索
        search = new EditText(this);
        search.setHint("搜索应用名或包名");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(box(CARD, 10));
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = bp;
        slp.rightMargin = bp;
        slp.bottomMargin = dp(8);
        root.addView(search, slp);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        // 筛选
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        chipRow.setPadding(bp, 0, bp, dp(8));
        // 高度写死 44dp：实测这台车机把这一行的 WRAP_CONTENT 算成了 1136px，
        // 把 GridView 的剩余空间全吃光（grid 高 0、一行数据都显示不出来）。
        root.addView(chipRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        String[] names = {"全部", "用户应用", "系统应用"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button c = new Button(this);
            c.setAllCaps(false);
            c.setText(names[i]);
            c.setTextSize(13);
            c.setMinWidth(0);
            c.setMinimumWidth(0);
            c.setPadding(dp(16), 0, dp(16), 0);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            clp.rightMargin = dp(8);
            chipRow.addView(c, clp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mode = idx;
                    applyFilter();
                }
            });
            chips[i] = c;
        }

        // 刷新按钮靠右对齐：筛选行右侧留白足够，不占用顶栏宽度
        View spacer = new View(this);
        chipRow.addView(spacer, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refreshBtn = new Button(this);
        refreshBtn.setAllCaps(false);
        refreshBtn.setText("刷新");
        refreshBtn.setTextSize(13);
        refreshBtn.setTextColor(0xFFFFFFFF);
        refreshBtn.setMinWidth(0);
        refreshBtn.setMinimumWidth(0);
        refreshBtn.setMinHeight(0);
        refreshBtn.setMinimumHeight(0);
        refreshBtn.setIncludeFontPadding(false);
        refreshBtn.setPadding(dp(18), 0, dp(18), 0);
        refreshBtn.setBackground(box(LINE, 18));
        chipRow.addView(refreshBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshApps();
            }
        });

        // 网格（图标 + 名称 + 包名）
        // 不能用 height=0 + weight=1：实测这台车机上该组合被结算成 0 高，
        // root 有 1328px 可用空间却一个像素都不分给 GridView（width 却是 2560，
        // 说明它参与了布局，只是高度被算成 0）。
        // 改为先给一个屏幕高度的初始值，真正高度由 fitGridHeight() 在布局完成后校正。
        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(230));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(12));
        grid.setPadding(bp, 0, bp, dp(4));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setBackgroundColor(BG);
        grid.setCacheColorHint(0);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new AppListAdapter(this, shown);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                showActions(adapter.getItem(position));
            }
        });
        // 与 1.0.0 相同的标准写法：height=0 + weight=1，由 LinearLayout 分配剩余空间。
        // 只有在实测到 0 高时，fitGridHeight() 才会兜底改成固定像素。
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(grid, glp);

        // 底部统计
        stats = tv("正在读取应用列表…", 12, MUTED, false);
        stats.setPadding(bp, dp(6), bp, dp(10));
        root.addView(stats);

        // 布局与数据都就绪后再校正一次高度
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (grid.getHeight() > 0) {
                            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            fitGridHeight(root);
                        }
                    }
                });

        return root;
    }

    /**
     * 兜底：按「容器高度 - 其它子 View 实测高度」给 GridView 定高。
     *
     * 实车日志：root h=1328，grid 宽 2560（说明参与了布局）但高 0。
     * 说明这台 ROM 上 weight 的剩余空间分配没有生效。
     * 这里直接算出差值写进 LayoutParams，绕开 weight。
     */
    private void fitGridHeight(View root) {
        if (root == null || !(root instanceof ViewGroup) || grid == null) {
            return;
        }
        ViewGroup vg = (ViewGroup) root;
        int rootH = vg.getHeight();
        if (rootH <= 0) {
            return; // 还没量完，等下一次布局回调
        }
        // weight 已经正常分配出高度时不要插手，否则窗口尺寸变化后就失去自适应。
        // 只有实测 0 高（这台车机上出现过的 ROM 缺陷）才兜底。
        ViewGroup.LayoutParams cur = grid.getLayoutParams();
        if (cur != null && cur.height > 0) {
            return;
        }
        int used = 0;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c == grid) {
                continue;
            }
            int ch = c.getMeasuredHeight();
            used += ch;
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            detail.append(c.getClass().getSimpleName())
                    .append(":lp=").append(lp == null ? "null" : lp.height)
                    .append("/m=").append(ch).append(' ');
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                used += mlp.topMargin + mlp.bottomMargin;
            }
        }
        int target = Math.max(0, rootH - used);
        if (target <= 0) {
            android.util.Log.w("LynkMgr", "fitGridHeight 放弃：rootH=" + rootH
                    + " used=" + used + " [" + detail + "]");
            return;
        }
        ViewGroup.LayoutParams lp = grid.getLayoutParams();
        if (lp != null && lp.height != target) {
            lp.height = target;
            grid.setLayoutParams(lp);
            android.util.Log.i("LynkMgr", "fitGridHeight rootH=" + rootH
                    + " used=" + used + " -> gridH=" + target);
        }
    }

    private void setChips() {
        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) {
                continue;
            }
            boolean on = i == mode;
            chips[i].setTextColor(on ? 0xFFFFFFFF : MUTED);
            chips[i].setBackground(box(on ? OK : CARD, 18));
        }
    }

    private void updateAdbButton() {
        boolean granted = hasSecurePerm();
        // 只看系统设置值就显示「已开」会误导：这台车机上 adb_enabled=1
        // 但 USB 配置里没有 adb 时电脑根本连不上。
        // 这里同时读 USB 配置、adbd 状态和厂商开关，三者都通才算真开。
        String usbCfg = readUsbConfig();
        String adbd = readAdbdState();
        String vendorAdb = readVendorAdbEnable();
        boolean usbHasAdb = usbCfg != null && usbCfg.contains("adb");
        boolean adbdUp = "running".equals(adbd);
        // 厂商属性不存在（"(空)"/"?"）时不当成关闭，避免误判别的机型
        boolean vendorKnown = "0".equals(vendorAdb) || "1".equals(vendorAdb);
        boolean vendorOn = !vendorKnown || "1".equals(vendorAdb);

        if (usbHasAdb && adbdUp && vendorOn) {
            adbBtn.setText("ADB 已通");
            adbBtn.setBackground(box(OK, 10));
        } else if (usbHasAdb && adbdUp) {
            // 通道在、但厂商开关是关的：长按可以一键写开
            adbBtn.setText("ADB 待开启");
            adbBtn.setBackground(box(0xFFD8A03C, 10));
        } else {
            adbBtn.setText("ADB 未通");
            adbBtn.setBackground(box(0xFFE05B5B, 10));
        }
        adbBtn.setAlpha(granted ? 1f : 0.6f);
    }

    private int readAdb() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 读厂商私有的 USB gadget 配置，这决定 adb 到底能不能连上。 */
    private String readUsbConfig() {
        return readProp("sys.usb.config");
    }

    private String readVendorUsbConfig() {
        return readProp("persist.vendor.usb.config");
    }

    /** 厂商自己的 ADB 持久化开关，和 Settings.Global.adb_enabled 是两套。 */
    private String readVendorAdbEnable() {
        return readProp("persist.sv.debug.adb_enable");
    }

    private String readAdbdState() {
        return readProp("init.svc.adbd");
    }

    /** 读系统属性。getprop 走 Runtime，失败返回 "?"。 */
    private String readProp(String key) {
        java.io.BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor();
            return line == null || line.trim().isEmpty() ? "(空)" : line.trim();
        } catch (Throwable t) {
            return "?";
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean hasSecurePerm() {
        return checkSelfPermission(AdbSwitchActivity.PERM) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 执行 shell 命令，返回 "rc|stdout|stderr"。
     *
     * 用于 setprop 这类系统操作。这台车机 SELinux 是 Permissive，
     * 实测 shell 身份写 persist.sv.debug.adb_enable 返回 rc=0，
     * 普通应用在 Permissive 下同样不被 SELinux 拦截。
     */
    static String sh(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            java.io.BufferedReader o = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            java.io.BufferedReader e = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getErrorStream()));
            String line;
            while ((line = o.readLine()) != null) {
                out.append(line).append(' ');
            }
            while ((line = e.readLine()) != null) {
                err.append(line).append(' ');
            }
            int rc = p.waitFor();
            return rc + "|" + out.toString().trim() + "|" + err.toString().trim();
        } catch (Throwable t) {
            return "-1||" + t.getClass().getSimpleName();
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 读一个属性，等价于 getprop（供后台线程/接收器复用）。 */
    static String prop(String key) {
        String r = sh("getprop " + key);
        int p1 = r.indexOf('|');
        int p2 = r.indexOf('|', p1 + 1);
        if (p1 < 0 || p2 < 0) {
            return "(空)";
        }
        String v = r.substring(p1 + 1, p2).trim();
        return v.isEmpty() ? "(空)" : v;
    }

    /**
     * 顶栏按钮：纯只读。
     *
     * 为什么不再让单击切换：实测这台车机上写 ADB_ENABLED 会让 USB gadget
     * 从 PID_2D0A（含 ADB 接口的复合设备）重新枚举成 PID_4EE7（诊断单功能设备），
     * ADB 接口当场消失，电脑端只能看到 offline。用户感知就是「掉线」。
     */
    private void showAdbStatus() {
        int v = readAdb();
        boolean granted = hasSecurePerm();

        String settingsVal = v == 1 ? "1（开）" : v == 0 ? "0（关）" : "未知 " + v;
        String usbCfg = readUsbConfig();
        String vendorCfg = readVendorUsbConfig();
        String vendorAdb = readVendorAdbEnable();
        String adbd = readAdbdState();
        boolean usbHasAdb = usbCfg != null && usbCfg.contains("adb");

        StringBuilder sb = new StringBuilder();
        sb.append("系统设置 adb_enabled：").append(settingsVal).append('\n');
        sb.append("厂商 adb 开关：").append(vendorAdb).append('\n');
        sb.append("adbd 服务：").append(adbd).append('\n');
        sb.append("当前 USB 配置：").append(usbCfg).append('\n');
        sb.append("厂商 USB 配置：").append(vendorCfg).append('\n');
        sb.append("写入权限：").append(granted ? "已授予" : "未授予").append('\n');
        sb.append('\n');

        if (usbHasAdb && "running".equals(adbd)) {
            sb.append("结论：ADB 通道当前是通的。");
        } else {
            sb.append("结论：ADB 通道当前不通。");
            sb.append("\n注意：系统设置值即使显示为「开」，只要 USB 配置里没有 adb，");
            sb.append("电脑就连不上 —— 这台车机的开关由厂商这一套决定。");
        }
        sb.append("\n\n单击只读；长按可以直接开启/关闭 ADB（会掉线约 1 分钟）。");

        new AlertDialog.Builder(this)
                .setTitle("ADB 状态（只读）")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .setNeutralButton("高级", (d, w) -> confirmToggleAdb())
                .show();
    }

    /**
     * 高级入口：写操作藏在这里，且必须二次确认。
     *
     * 这一步是「A+C 方案」里的 C 部分（诚实化）：
     * 把风险明确写在按钮上，而不是让人误以为是普通开关。
     */
    private void confirmToggleAdb() {
        final boolean willTurnOn = !"1".equals(readVendorAdbEnable());
        String action = willTurnOn ? "开启" : "关闭";

        StringBuilder msg = new StringBuilder();
        if (willTurnOn) {
            msg.append("会把 ADB 真正打开，不需要再进工厂模式：\n\n");
            msg.append("① persist.sv.debug.adb_enable = 1\n");
            msg.append("   车机真正的调试开关，adbd 靠它启动\n\n");
            msg.append("② persist.sys.usb.config = adb\n");
            msg.append("   重启后 USB 仍带 adb 功能\n\n");
            msg.append("③ sys.usb.config = adb\n");
            msg.append("   让 init 立即重配 USB，adbd 当场生效\n\n");
            msg.append("代价：USB 会重新枚举，电脑端掉线约 1 分钟，之后自动恢复。\n\n");
            msg.append("本机 SELinux 是 Permissive，普通应用可以直接 setprop。");
        } else {
            msg.append("会把厂商调试开关写回 0：\n\n");
            msg.append("· persist.sv.debug.adb_enable = 0\n");
            msg.append("· Settings.Global.adb_enabled = 0\n\n");
            msg.append("关掉之后 adbd 不再启动，需要重新开启才能连电脑。");
        }

        new AlertDialog.Builder(this)
                .setTitle(action + " ADB")
                .setMessage(msg.toString())
                .setPositiveButton("确认" + action, (d, w) -> toggleAdb())
                .setNegativeButton("取消", null)
                .show();
    }

    /** 真正写入 Settings.Global.ADB_ENABLED，写后回读校验。只由确认框调用。 */
    /**
     * 真正开关车机 ADB。
     *
     * 决定 adbd 起不停的是厂商属性 persist.sv.debug.adb_enable（工厂模式写的那个），
     * 而本机 SELinux 是 Permissive，普通应用 setprop 不会被拦，
     * 所以能直接落到真正的开关上，不再需要进工厂模式。
     *
     * 顺序：
     *   ① persist.sv.debug.adb_enable  决定 adbd 起不停
     *   ② persist.sys.usb.config=adb   重启后仍带 adb 功能
     *   ③ sys.usb.config=adb           让 init 立即重配 USB（会掉线几十秒）
     */
    private void toggleAdb() {
        final boolean turnOn = !"1".equals(readVendorAdbEnable());
        final String flag = turnOn ? "1" : "0";

        String r1 = sh("setprop persist.sv.debug.adb_enable " + flag);
        String r2 = "";
        String r3 = "";
        if (turnOn) {
            r2 = sh("setprop persist.sys.usb.config adb");
            r3 = sh("setprop sys.usb.config adb");
        }

        // Settings 值同步一份，让系统设置页显示一致（它不是生效开关，但能避免自相矛盾）
        try {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.ADB_ENABLED, turnOn ? 1 : 0);
        } catch (Throwable ignored) {
        }

        String now = readVendorAdbEnable();
        boolean ok = (turnOn && "1".equals(now)) || (!turnOn && "0".equals(now));

        StringBuilder msg = new StringBuilder();
        msg.append(turnOn ? "已尝试开启" : "已尝试关闭").append('\n');
        msg.append(ok ? "回读校验：成功\n" : "回读校验：未生效\n");
        msg.append("厂商开关 = ").append(now).append('\n');
        msg.append("adbd = ").append(readAdbdState()).append('\n');
        msg.append("USB 配置 = ").append(readUsbConfig());
        if (turnOn) {
            msg.append("\n\nUSB 会重新枚举，电脑端大约 1 分钟后恢复。");
        }
        if (!ok) {
            msg.append("\n\n命令回执：\n").append(r1).append('\n').append(r3);
        }

        new AlertDialog.Builder(this)
                .setTitle("ADB " + (turnOn ? "开启" : "关闭"))
                .setMessage(msg.toString())
                .setPositiveButton("知道了", null)
                .show();

        android.util.Log.i("LynkMgr", "toggleAdb on=" + turnOn
                + " r1=" + r1 + " r2=" + r2 + " r3=" + r3);
        updateAdbButton();
    }

    // ---------------- 数据 ----------------

    private void loadApps() {
        loadApps(false);
    }

    /**
     * 三路兜底采集已安装包。
     *
     * 车机上遇到过 getInstalledPackages(0) 返回空的情况，原因可能是
     * 包可见性策略、或厂商 ROM 对 QUERY_ALL_PACKAGES 的特殊处理。
     * 这里按「覆盖面从宽到窄」依次尝试，任意一路拿到结果就返回：
     *   ① getInstalledPackages(0)              常规全集
     *   ② getInstalledPackages(MATCH_UNINSTALLED_PACKAGES)  含已卸载残留
     *   ③ queryIntentActivities(LAUNCHER)      只含有桌面入口的（最保守）
     *
     * @return 包列表；三路都拿不到时返回空表
     */
    private List<PackageInfo> collectPackages(PackageManager pm) {
        List<PackageInfo> pkgs = null;

        try {
            pkgs = pm.getInstalledPackages(0);
            android.util.Log.i("LynkMgr", "src1 getInstalledPackages(0) = "
                    + (pkgs == null ? "null" : pkgs.size()));
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "src1 failed: " + t, t);
            pkgs = null;
        }
        if (pkgs != null && !pkgs.isEmpty()) {
            sourceTag = "常规";
            return pkgs;
        }

        try {
            List<PackageInfo> p2 = pm.getInstalledPackages(
                    PackageManager.MATCH_UNINSTALLED_PACKAGES);
            android.util.Log.i("LynkMgr", "src2 MATCH_UNINSTALLED = "
                    + (p2 == null ? "null" : p2.size()));
            if (p2 != null && !p2.isEmpty()) {
                sourceTag = "含残留";
                return p2;
            }
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "src2 failed: " + t, t);
        }

        // 第三路：只能看到有桌面图标的应用，但至少不会全空
        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER);
            List<android.content.pm.ResolveInfo> ris =
                    pm.queryIntentActivities(launcher, 0);
            android.util.Log.i("LynkMgr", "src3 launcher activities = "
                    + (ris == null ? "null" : ris.size()));
            if (ris != null && !ris.isEmpty()) {
                List<PackageInfo> out = new ArrayList<>();
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (android.content.pm.ResolveInfo ri : ris) {
                    String pn = ri.activityInfo == null ? null : ri.activityInfo.packageName;
                    if (pn == null || !seen.add(pn)) {
                        continue;
                    }
                    try {
                        out.add(pm.getPackageInfo(pn, 0));
                    } catch (Throwable ignored) {
                        // 单个包拿不到就跳过
                    }
                }
                if (!out.isEmpty()) {
                    sourceTag = "桌面入口";
                    return out;
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "src3 failed: " + t, t);
        }

        sourceTag = "无";
        return new ArrayList<>();
    }

    /** @param isRefresh 用户手动刷新时给一次完成提示，首次加载不提示 */
    private void loadApps(final boolean isRefresh) {
        if (loading) {
            return;
        }
        loading = true;
        setRefreshState(true, "刷新中…");
        stats.setText(isRefresh ? "正在刷新应用列表…" : "正在读取应用列表…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> out = new ArrayList<>();
                Throwable error = null;
                int skipped = 0;
                int rawCount = -1;
                try {
                    PackageManager pm = getPackageManager();
                    List<PackageInfo> pkgs = collectPackages(pm);
                    // 记下系统原始返回数量：为 0 说明是包可见性问题，
                    // 不为 0 而列表空说明是我们的代码出了问题。
                    rawCount = pkgs == null ? 0 : pkgs.size();
                    for (PackageInfo pi : pkgs) {
                        // 单包失败必须跳过而不是整体失败：
                        // loadIcon 遇到超大图标可能抛 OOM，一旦冒泡到外层，
                        // 整个列表会变成 0 项，表现为「应用列表空白」。
                        try {
                            ApplicationInfo ai = pi.applicationInfo;
                            if (ai == null) {
                                continue;
                            }
                            AppInfo a = new AppInfo();
                            a.pkg = pi.packageName;
                            try {
                                a.label = String.valueOf(ai.loadLabel(pm));
                            } catch (Throwable t) {
                                a.label = pi.packageName;
                            }
                            try {
                                a.icon = ai.loadIcon(pm);
                            } catch (Throwable ignored) {
                                // 图标拿不到就留空，名字和包名照常显示
                            }
                            a.versionName = pi.versionName;
                            a.versionCode = pi.versionCode;
                            a.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            a.sourceDir = ai.sourceDir;
                            a.enabled = ai.enabled;
                            a.size = ai.sourceDir == null ? 0 : new File(ai.sourceDir).length();
                            out.add(a);
                        } catch (Throwable t) {
                            skipped++;
                            android.util.Log.w("LynkMgr",
                                    "skip pkg " + pi.packageName + ": " + t, t);
                        }
                    }
                    out.sort(new java.util.Comparator<AppInfo>() {
                        @Override
                        public int compare(AppInfo x, AppInfo y) {
                            if (x.system != y.system) {
                                return x.system ? 1 : -1;
                            }
                            return String.valueOf(x.label)
                                    .compareToIgnoreCase(String.valueOf(y.label));
                        }
                    });
                } catch (Throwable t) {
                    error = t;
                }
                final Throwable err = error;
                final int skipCount = skipped;
                final int raw = rawCount;
                android.util.Log.i("LynkMgr", "loadApps raw=" + raw + " built=" + out.size()
                        + " skipped=" + skipCount + " src=" + sourceTag + " err=" + err);

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        setRefreshState(false, "刷新");
                        if (err != null) {
                            // 失败也要解锁按钮，否则刷新入口会永久卡死
                            stats.setText("读取失败：" + err.getClass().getSimpleName()
                                    + " · 点「刷新」重试");
                            toast("读取应用列表失败，请重试");
                            return;
                        }
                        all.clear();
                        all.addAll(out);
                        lastRawCount = raw;
                        if (adapter != null) {
                            adapter.clearIconCache();
                        }
                        applyFilter();
                        // 数据到位后再校正一次网格高度：
                        // 部分 ROM 上 weight 失效，会导致「有数据但显示不出来」。
                        grid.post(new Runnable() {
                            @Override
                            public void run() {
                                fitGridHeight(grid.getParent() instanceof View
                                        ? (View) grid.getParent() : null);
                            }
                        });
                        if (skipCount > 0) {
                            toast("已跳过 " + skipCount + " 个读取失败的包");
                        } else if (isRefresh) {
                            toast("已刷新 · 共 " + all.size() + " 个应用");
                        }
                    }
                });
            }
        }).start();
    }

    /** 刷新按钮：重扫已安装应用，保留当前搜索词与筛选档位。 */
    private void refreshApps() {
        if (loading) {
            toast("正在刷新，请稍候");
            return;
        }
        loadApps(true);
    }

    private void setRefreshState(boolean busy, String label) {
        if (refreshBtn == null) {
            return;
        }
        refreshBtn.setText(label);
        refreshBtn.setEnabled(!busy);
        refreshBtn.setAlpha(busy ? 0.6f : 1f);
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (AppInfo a : all) {
            if (mode == 1 && a.system) {
                continue;
            }
            if (mode == 2 && !a.system) {
                continue;
            }
            if (!q.isEmpty()) {
                String label = a.label == null ? "" : a.label.toLowerCase();
                String pkg = a.pkg == null ? "" : a.pkg.toLowerCase();
                if (!label.contains(q) && !pkg.contains(q)) {
                    continue;
                }
            }
            shown.add(a);
        }
        setChips();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (stats != null) {
            if (all.isEmpty()) {
                // 区分「系统没给包」和「给了但全部读取失败」，这是两种完全不同的故障
                if (lastRawCount == 0) {
                    stats.setText("系统返回 0 个包 · 包可见性被限制，点「刷新」重试");
                } else if (lastRawCount > 0) {
                    stats.setText("系统返回 " + lastRawCount + " 个包，但全部读取失败 · 点「刷新」重试");
                } else {
                    stats.setText("未读取到任何应用 · 点「刷新」重试");
                }
            } else if (shown.isEmpty()) {
                stats.setText("共 " + all.size() + " 个应用 · 当前筛选无结果");
            } else {
                stats.setText("共 " + all.size() + " 个应用 · 当前显示 " + shown.size()
                        + " · 来源" + sourceTag);
            }
        }
    }

    // ---------------- 操作 ----------------

    private void showActions(final AppInfo a) {
        // 系统应用不提供卸载入口：无 root 无法真正卸载，误点只会拉起失败的卸载界面。
        final boolean sys = a.system;
        String[] items = sys
                ? new String[]{"强制停止", "导出 APK", "应用详情"}
                : new String[]{"强制停止", "卸载", "导出 APK", "应用详情"};
        new AlertDialog.Builder(this)
                .setTitle(a.label + "\n" + a.pkg)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        forceStop(a);
                    } else if (sys) {
                        if (which == 1) {
                            exportApk(a);
                        } else {
                            details(a);
                        }
                    } else {
                        if (which == 1) {
                            uninstall(a);
                        } else if (which == 2) {
                            exportApk(a);
                        } else {
                            details(a);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void forceStop(AppInfo a) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(a.pkg);
            toast("已发送停止指令：" + a.label);
        } catch (Throwable t) {
            toast("停止失败：" + t.getClass().getSimpleName());
        }
    }

    private void uninstall(AppInfo a) {
        if (a.system) {
            toast("系统应用不能卸载（无 root）");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
        } catch (Throwable t) {
            toast("无法打开卸载界面");
        }
    }

    private void details(AppInfo a) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg)));
        } catch (Throwable t) {
            toast("无法打开系统详情页");
        }
    }

    // ---------------- 反馈 / 赞赏 ----------------

    private void showFeedback() {
        new AlertDialog.Builder(this)
                .setTitle("反馈与联系")
                .setMessage("遇到 bug、想加功能，欢迎直接发邮件：\n\n" + EMAIL
                        + "\n\n附上机型、系统版本和复现步骤，处理会快很多。")
                .setPositiveButton("发邮件", (d, w) -> sendMail())
                .setNeutralButton("复制邮箱", (d, w) -> copyEmail())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void copyEmail() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("邮箱", EMAIL));
            }
            toast("已复制：" + EMAIL);
        } catch (Throwable t) {
            toast("复制失败，请手动记录：" + EMAIL);
        }
    }

    private void sendMail() {
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL));
            i.putExtra(Intent.EXTRA_SUBJECT, "Lynk 管家 反馈");
            startActivity(i);
        } catch (Throwable t) {
            copyEmail();
            toast("没有可用的邮件客户端，已复制邮箱");
        }
    }

    /** 赞赏面板：微信 / 支付宝收款码并排。 */
    private void showAppreciate() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(22), dp(26), dp(18));
        root.setMinimumWidth(dp(420));
        root.setBackground(box(CARD, 18));

        TextView title = tv("赞赏一杯茶", 18, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = tv("扫码赞赏开发者，金额随意", 12, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout wc = qrCard(R.drawable.pay_wechat, "微信", WECHAT);
        LinearLayout.LayoutParams wclp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wclp.rightMargin = dp(14);
        row.addView(wc, wclp);

        row.addView(qrCard(R.drawable.pay_alipay, "支付宝", ALIPAY),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = tv("关闭", 13, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(16), 0, 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        root.addView(close);

        dlg.setContentView(root);
        dlg.show();
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 单张收款码卡片：深色底 + 二维码 + 品牌色标签。 */
    private LinearLayout qrCard(int resId, String label, int brandColor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(box(BG, 14));
        c.setPadding(dp(12), dp(12), dp(12), dp(12));

        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        c.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));

        TextView l = tv(label, 13, brandColor, true);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(10), 0, 0);
        c.addView(l);
        return c;
    }

    private void exportApk(final AppInfo a) {
        if (a.sourceDir == null) {
            toast("拿不到 APK 路径");
            return;
        }
        final File base = getExternalFilesDir(null);
        if (base == null) {
            toast("外部存储不可用");
            return;
        }
        // 统一落在应用私有目录的 export/ 下：卸载应用时系统会连目录一起删，不会遗留。
        final File dir = new File(base, "export");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        toast("正在导出 " + a.label + "…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    String ver = a.versionName == null ? "unknown" : a.versionName;
                    File out = new File(dir, a.pkg + "-" + ver + ".apk");
                    copy(new File(a.sourceDir), out);
                    msg = "已导出：" + out.getName() + "，在「传文件」页点启动服务后用浏览器下载";
                } catch (Throwable t) {
                    msg = "导出失败：" + t.getMessage();
                }
                final String m = msg;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        toast(m);
                    }
                });
            }
        }).start();
    }

    private static void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream os = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            try {
                os.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
