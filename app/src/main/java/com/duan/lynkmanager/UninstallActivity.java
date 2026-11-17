package com.duan.lynkmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 卸载本应用。
 *
 * Android 的硬限制：应用被卸载时进程会被直接杀掉，自身代码无法执行，
 * 所以不存在「卸载后自动删文件」的系统回调。唯一可行路径是：
 *   用户从本应用内点「卸载」→ 先清理文件 → 再拉起系统卸载界面。
 * 从系统设置里直接卸载时，公共目录的文件不会被清理（平台限制，无法规避）。
 *
 * 清理顺序（对应需求）：
 *   1. 删除 Download/上传文件 整个目录
 *   2. 删除 Download/导出文件 整个目录
 *   3. 清除应用缓存（cacheDir、codeCacheDir、externalCacheDir）
 *   4. 拉起系统卸载确认界面
 */
public class UninstallActivity extends BaseActivity {

    private static final int BG = 0xFF0E1318;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int DANGER = 0xFFE05B5B;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(16);
        head.setPadding(p, dp(14), p, dp(10));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = barBtn("← 返回");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        blp.rightMargin = dp(12);
        head.addView(back, blp);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView title = tv("卸载 Lynk 管家", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ScrollView sv = new ScrollView(this);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(body);

        // 说明
        TextView desc = tv(
                "点下方按钮后，会按顺序执行：\n\n"
                        + "1. 删除「下载/上传文件」整个目录\n"
                        + "2. 删除「下载/导出文件」整个目录\n"
                        + "3. 清除本应用缓存\n"
                        + "4. 拉起系统卸载确认界面\n\n"
                        + "注意：只有从这里点「卸载」才会清理上面两个目录。\n"
                        + "如果从系统设置直接卸载应用，公共下载目录里的文件会保留\n"
                        + "（Android 平台限制，应用无法在卸载时执行代码）。\n\n"
                        + "两个目录的当前内容如下，确认后不可恢复。", 14, MUTED, false);
        desc.setLineSpacing(dp(4), 1f);
        body.addView(desc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 目录内容预览
        body.addView(previewCard("上传文件", uploadDir()), marginTop(dp(16)));
        body.addView(previewCard("导出文件", exportDir()), marginTop(dp(12)));

        // 卸载按钮
        Button uninstall = barBtn("清理并卸载");
        uninstall.setTextColor(0xFFFFFFFF);
        uninstall.setTextSize(16);
        uninstall.setBackground(box(DANGER, 12));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        ulp.topMargin = dp(24);
        body.addView(uninstall, ulp);
        uninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmUninstall();
            }
        });

        return root;
    }

    /** 某个目录的内容预览卡片。 */
    private View previewCard(String label, File dir) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(0xFF1B2128, 12));
        int p = dp(14);
        card.setPadding(p, p, p, p);

        TextView h = tv(label, 15, TEXT, true);
        card.addView(h);

        TextView path = tv(dir == null ? "" : dir.getAbsolutePath(), 11, 0xFF6B7785, false);
        path.setPadding(0, dp(3), 0, dp(8));
        card.addView(path);

        if (dir == null || !dir.isDirectory()) {
            card.addView(tv("（目录不存在）", 13, MUTED, false));
            return card;
        }
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) {
            card.addView(tv("（空）", 13, MUTED, false));
            return card;
        }
        long total = 0;
        int shown = 0;
        for (File f : fs) {
            if (!f.isFile()) {
                continue;
            }
            total += f.length();
            if (shown < 8) {
                TextView row = tv("· " + f.getName() + "   "
                        + FileEntry.sizeText(f.length()), 12, MUTED, false);
                row.setSingleLine(true);
                card.addView(row);
                shown++;
            }
        }
        if (fs.length > shown) {
            card.addView(tv("… 还有 " + (fs.length - shown) + " 个文件", 12, MUTED, false));
        }
        card.addView(tv("合计 " + fs.length + " 个文件 · " + FileEntry.sizeText(total),
                12, 0xFFE0A15B, true));
        return card;
    }

    private LinearLayout.LayoutParams marginTop(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px;
        return lp;
    }

    private File uploadDir() {
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return d == null ? null : new File(d, "上传文件");
    }

    private File exportDir() {
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return d == null ? null : new File(d, "导出文件");
    }

    private void confirmUninstall() {
        new AlertDialog.Builder(this)
                .setTitle("确认卸载")
                .setMessage("将删除：\n\n"
                        + "· 下载/上传文件（整个目录）\n"
                        + "· 下载/导出文件（整个目录）\n"
                        + "· 本应用缓存\n\n"
                        + "然后弹出系统卸载界面。\n此操作不可恢复。")
                .setPositiveButton("继续", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        doCleanupAndUninstall();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 清理 → 拉起卸载界面。清理动作放在后台线程，避免目录大时卡界面。 */
    private void doCleanupAndUninstall() {
        Toast.makeText(this, "正在清理…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> report = new ArrayList<>();

                // 1. 删上传文件目录
                File up = uploadDir();
                int n1 = deleteDir(up);
                report.add("上传文件：" + (up != null && up.exists() ? "删除失败" : "已删除（" + n1 + " 项）"));

                // 2. 删导出文件目录
                File ex = exportDir();
                int n2 = deleteDir(ex);
                report.add("导出文件：" + (ex != null && ex.exists() ? "删除失败" : "已删除（" + n2 + " 项）"));

                // 3. 清缓存
                int n3 = 0;
                n3 += deleteDir(getCacheDir());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    n3 += deleteDir(getCodeCacheDir());
                }
                File extCache = getExternalCacheDir();
                if (extCache != null) {
                    n3 += deleteDir(extCache);
                }
                report.add("应用缓存：已清理（" + n3 + " 项）");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        for (String s : report) {
                            sb.append("· ").append(s).append('\n');
                        }
                        new AlertDialog.Builder(UninstallActivity.this)
                                .setTitle("清理完成")
                                .setMessage(sb.toString()
                                        + "\n接下来会打开系统卸载界面，点「确定」即可卸载。")
                                .setPositiveButton("去卸载", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        launchSystemUninstall();
                                    }
                                })
                                .setCancelable(false)
                                .show();
                    }
                });
            }
        }).start();
    }

    /** 递归删除目录内容并删掉目录本身，返回删除的项数。 */
    private int deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        int count = 0;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) {
                    count += deleteDir(f);
                }
                if (f.delete()) {
                    count++;
                }
            }
        }
        dir.delete();
        return count;
    }

    private void launchSystemUninstall() {
        try {
            Intent i = new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "无法拉起系统卸载界面：" + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- 工具 ----------------

    private Button barBtn(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setIncludeFontPadding(false);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(box(0xFF232B34, 10));
        return b;
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private android.graphics.drawable.GradientDrawable box(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
