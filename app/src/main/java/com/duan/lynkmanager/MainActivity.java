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
    private final Button[] chips = new Button[3];
    private int mode = 0; // 0 全部 / 1 用户 / 2 系统

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAdbButton();
    }

    // ---------------- UI ----------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
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
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

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

        final Button donateBtn = barBtn("捐赠");
        LinearLayout.LayoutParams donateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        donateLp.rightMargin = dp(8);
        bar.addView(donateBtn, donateLp);
        donateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDonate();
            }
        });

        adbBtn = barBtn("ADB");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        alp.rightMargin = dp(8);
        bar.addView(adbBtn, alp);
        adbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAdb();
            }
        });
        adbBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(MainActivity.this, AdbSwitchActivity.class));
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
        chipRow.setPadding(bp, 0, bp, dp(8));
        root.addView(chipRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

        // 网格（图标 + 名称 + 包名）
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
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(grid, glp);

        // 底部统计
        stats = tv("正在读取应用列表…", 12, MUTED, false);
        stats.setPadding(bp, dp(6), bp, dp(10));
        root.addView(stats);

        return root;
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
        int v = readAdb();
        boolean granted = hasSecurePerm();
        adbBtn.setText(v == 1 ? "ADB 已开" : "ADB 已关");
        adbBtn.setBackground(box(v == 1 ? OK : LINE, 10));
        adbBtn.setAlpha(granted ? 1f : 0.6f);
    }

    private int readAdb() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    private boolean hasSecurePerm() {
        return checkSelfPermission(AdbSwitchActivity.PERM) == PackageManager.PERMISSION_GRANTED;
    }

    /** 顶栏 ADB 开关：一次点击直接写 Settings.Global.ADB_ENABLED，写后回读校验。 */
    private void toggleAdb() {
        if (!hasSecurePerm()) {
            toast("缺少 WRITE_SECURE_SETTINGS，去授权页按提示执行一次 pm grant");
            startActivity(new Intent(this, AdbSwitchActivity.class));
            return;
        }
        boolean on = readAdb() != 1;
        int target = on ? 1 : 0;
        try {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            boolean ok = Settings.Global.putInt(getContentResolver(),
                    Settings.Global.ADB_ENABLED, target);
            if (!ok) {
                toast("写入被 SettingsProvider 拒绝");
                updateAdbButton();
                return;
            }
        } catch (Throwable t) {
            toast("写入失败：" + t.getClass().getSimpleName());
            updateAdbButton();
            return;
        }
        updateAdbButton();
        final int expect = target;
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                int now = readAdb();
                if (now == expect) {
                    toast(expect == 1 ? "ADB 已开启" : "ADB 已关闭");
                } else {
                    toast("写入后读数为 " + now + "，本机把开关托管在厂商设置里");
                }
                updateAdbButton();
            }
        }, 1200);
    }

    // ---------------- 数据 ----------------

    private void loadApps() {
        stats.setText("正在读取应用列表…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> out = new ArrayList<>();
                PackageManager pm = getPackageManager();
                List<PackageInfo> pkgs = pm.getInstalledPackages(0);
                for (PackageInfo pi : pkgs) {
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
                    }
                    a.versionName = pi.versionName;
                    a.versionCode = pi.versionCode;
                    a.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    a.sourceDir = ai.sourceDir;
                    a.enabled = ai.enabled;
                    a.size = ai.sourceDir == null ? 0 : new File(ai.sourceDir).length();
                    out.add(a);
                }
                out.sort(new java.util.Comparator<AppInfo>() {
                    @Override
                    public int compare(AppInfo x, AppInfo y) {
                        if (x.system != y.system) {
                            return x.system ? 1 : -1;
                        }
                        return String.valueOf(x.label).compareToIgnoreCase(String.valueOf(y.label));
                    }
                });

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        all.clear();
                        all.addAll(out);
                        applyFilter();
                    }
                });
            }
        }).start();
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
            stats.setText("共 " + all.size() + " 个应用 · 当前显示 " + shown.size()
                    + " · 点图标操作");
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

    // ---------------- 反馈 / 捐赠 ----------------

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

    /** 捐赠面板：微信 / 支付宝收款码并排。 */
    private void showDonate() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(22), dp(26), dp(18));
        root.setMinimumWidth(dp(420));
        root.setBackground(box(CARD, 18));

        TextView title = tv("请我喝杯茶", 18, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = tv("扫码支持开发者，金额随意", 12, MUTED, false);
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
