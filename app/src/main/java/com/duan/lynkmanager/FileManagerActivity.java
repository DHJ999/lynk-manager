package com.duan.lynkmanager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 文件管理器。
 *
 * 为什么能做完整的文件浏览：
 * 本应用 targetSdk = 25，在 Android 11 上仍走 legacy storage 模式
 * （不受分区存储限制），配合 READ/WRITE_EXTERNAL_STORAGE 就能读写
 * /sdcard 下的任意目录，以及 U 盘挂载点。
 *
 * 功能：浏览、进入/返回、新建文件夹、重命名、删除、复制、移动、
 * 安装 APK、用系统应用打开、查看详情。
 *
 * 全部用匿名内部类而非 lambda：minSdk 23 上 lambda 依赖 desugaring，
 * 车机 ART 版本较老，匿名类零风险。
 */
public class FileManagerActivity extends BaseActivity {

    private static final int BG = 0xFF0E1318;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;

    private TextView pathView;
    private TextView summary;
    private ListView list;
    private FileListAdapter adapter;

    private File current;
    private final List<FileEntry> items = new ArrayList<>();

    /** 复制/移动的暂存区。非 null 表示有一次待粘贴的操作。 */
    private File clipboard;
    private boolean clipboardIsCut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        navigateTo(pickStartDir());
    }

    /** 选一个能看到东西的起点：优先内部存储根。 */
    private File pickStartDir() {
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null && ext.exists() && ext.canRead()) {
            return ext;
        }
        File dl = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (dl != null && dl.exists()) {
            return dl;
        }
        return new File("/");
    }

    // ---------------- UI ----------------

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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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

        // 操作按钮放左侧：驾驶位在左，方便驾驶员操作
        Button upBtn = barBtn("上一级");
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        ulp.rightMargin = dp(8);
        head.addView(upBtn, ulp);
        upBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goUp();
            }
        });

        Button pasteBtn = barBtn("粘贴");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        plp.rightMargin = dp(8);
        head.addView(pasteBtn, plp);
        pasteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPaste();
            }
        });

        Button newBtn = barBtn("新建");
        head.addView(newBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createFolder();
            }
        });

        // 标题放最后，weight=1 占满剩余空间
        TextView title = tv("文件管理", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        HorizontalScrollView shortcutBar = new HorizontalScrollView(this);
        shortcutBar.setHorizontalScrollBarEnabled(false);
        LinearLayout sl = new LinearLayout(this);
        sl.setOrientation(LinearLayout.HORIZONTAL);
        sl.setPadding(p, 0, p, dp(8));
        shortcutBar.addView(sl);
        root.addView(shortcutBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        buildShortcuts(sl);

        pathView = tv("", 12, 0xFF7FB8E0, false);
        pathView.setPadding(p, dp(4), p, dp(6));
        pathView.setTextIsSelectable(true);
        root.addView(pathView);

        summary = tv("", 12, MUTED, false);
        summary.setPadding(p, 0, p, dp(8));
        root.addView(summary);

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(dp(8), 0, dp(8), dp(8));
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new FileListAdapter(this, items);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onItemTap(position);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                showItemMenu(items.get(position));
                return true;
            }
        });

        return root;
    }

    private void buildShortcuts(LinearLayout bar) {
        addShortcut(bar, "内部存储", Environment.getExternalStorageDirectory());
        addShortcut(bar, "下载", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS));
        addShortcut(bar, "图片", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES));
        addShortcut(bar, "音乐", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC));
        addShortcut(bar, "视频", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES));
        addShortcut(bar, "文档", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS));
        addShortcut(bar, "相机", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM));
        addShortcut(bar, "根目录", new File("/"));

        for (File ud : findUsbRoots()) {
            addShortcut(bar, "U盘 " + ud.getName(), ud);
        }

        File appFiles = getExternalFilesDir(null);
        if (appFiles != null) {
            addShortcut(bar, "本应用", appFiles);
        }
    }

    private void addShortcut(LinearLayout bar, String label, final File dir) {
        if (dir == null || !dir.exists() || !dir.canRead()) {
            return;
        }
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setIncludeFontPadding(false);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(box(0xFF232B34, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.rightMargin = dp(8);
        bar.addView(b, lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateTo(dir);
            }
        });
    }

    /**
     * 找 U 盘挂载点。
     *
     * 车机的 U 盘通常出现在 /storage/XXXX-XXXX、/mnt/media_rw/XXXX-XXXX、
     * /mnt/usbhost1 等位置。逐个探测，存在且有内容就收进来。
     */
    private List<File> findUsbRoots() {
        List<File> out = new ArrayList<>();
        String self = null;
        try {
            self = Environment.getExternalStorageDirectory().getCanonicalPath();
        } catch (Throwable ignored) {
        }

        String[] roots = {"/storage", "/mnt/media_rw", "/mnt"};
        for (String r : roots) {
            File[] fs = new File(r).listFiles();
            if (fs == null) {
                continue;
            }
            for (File f : fs) {
                if (!f.isDirectory() || !f.canRead()) {
                    continue;
                }
                String n = f.getName();
                if (n.startsWith("emulated") || n.startsWith("self")
                        || n.equals("sdcard0")) {
                    continue;
                }
                try {
                    String cp = f.getCanonicalPath();
                    if (cp.equals(self) || cp.startsWith(self + "/")) {
                        continue;
                    }
                } catch (Throwable ignored) {
                }
                String[] kids = f.list();
                if (kids == null) {
                    continue;
                }
                if (kids.length == 0 && !n.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                    continue;
                }
                boolean dup = false;
                for (File x : out) {
                    if (x.getAbsolutePath().equals(f.getAbsolutePath())) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    // ---------------- 导航 ----------------

    private void navigateTo(File dir) {
        if (dir == null || !dir.isDirectory()) {
            toast("无法打开该目录");
            return;
        }
        if (!dir.canRead()) {
            toast("没有读取权限：\n" + dir.getAbsolutePath());
            return;
        }
        current = dir;
        pathView.setText(dir.getAbsolutePath());

        items.clear();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.getName().startsWith(".")) {
                    continue;
                }
                items.add(new FileEntry(f));
            }
        }
        sortItems();
        adapter.notifyDataSetChanged();
        list.setSelection(0);
        updateSummary();
    }

    private void sortItems() {
        Collections.sort(items, new Comparator<FileEntry>() {
            @Override
            public int compare(FileEntry a, FileEntry b) {
                if (a.isDir != b.isDir) {
                    return a.isDir ? -1 : 1;
                }
                return a.name().compareToIgnoreCase(b.name());
            }
        });
    }

    private void updateSummary() {
        int dirs = 0;
        int files = 0;
        long total = 0;
        for (FileEntry e : items) {
            if (e.isDir) {
                dirs++;
            } else {
                files++;
                total += e.size;
            }
        }
        summary.setText("共 " + items.size() + " 项 · " + dirs + " 个文件夹 · "
                + files + " 个文件 · 合计 " + FileEntry.sizeText(total));
    }

    /**
     * 延迟刷新列表。
     *
     * 对话框 onClick 里直接重扫列表，容易和 AlertDialog 的关闭动画抢时序，
     * 实测新建文件夹后列表不更新。放到消息队列下一拍执行，稳定生效。
     */
    private void refreshListDelayed() {
        if (current == null) {
            return;
        }
        final File dir = current;
        list.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && dir.equals(current)) {
                    navigateTo(dir);
                }
            }
        }, 250);
    }

    private void goUp() {        if (current == null) {
            return;
        }
        File parent = current.getParentFile();
        if (parent == null) {
            toast("已经是根目录");
            return;
        }
        navigateTo(parent);
    }

    @Override
    public void onBackPressed() {
        if (current != null && current.getParentFile() != null) {
            goUp();
            return;
        }
        super.onBackPressed();
    }

    // ---------------- 操作 ----------------

    private void onItemTap(int position) {
        FileEntry e = items.get(position);
        if (e.isDir) {
            navigateTo(e.file);
            return;
        }
        showItemMenu(e);
    }

    private void showItemMenu(final FileEntry e) {
        final List<String> ops = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (e.isDir) {
            ops.add("打开");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    navigateTo(e.file);
                }
            });
        } else if ("apk".equals(e.ext())) {
            ops.add("安装");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    installApk(e.file);
                }
            });
            ops.add("用其他应用打开");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    openWith(e.file);
                }
            });
        } else {
            ops.add("打开");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    openWith(e.file);
                }
            });
        }

        ops.add("复制");
        actions.add(new Runnable() {
            @Override
            public void run() {
                clipboard = e.file;
                clipboardIsCut = false;
                toast("已复制：" + e.name());
            }
        });

        ops.add("移动");
        actions.add(new Runnable() {
            @Override
            public void run() {
                clipboard = e.file;
                clipboardIsCut = true;
                toast("已剪切：" + e.name());
            }
        });

        ops.add("重命名");
        actions.add(new Runnable() {
            @Override
            public void run() {
                rename(e.file);
            }
        });

        ops.add("删除");
        actions.add(new Runnable() {
            @Override
            public void run() {
                confirmDelete(e.file);
            }
        });

        ops.add("详情");
        actions.add(new Runnable() {
            @Override
            public void run() {
                showDetail(e);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(e.name())
                .setItems(ops.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            actions.get(which).run();
                        } catch (Throwable t) {
                            toast("操作失败：" + t.getMessage());
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void installApk(File f) {
        try {
            Uri uri = new Uri.Builder()
                    .scheme("content")
                    .authority(ApkProvider.AUTHORITY)
                    .appendPath("apk")
                    .appendQueryParameter("path", f.getAbsolutePath())
                    .build();
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, ApkProvider.MIME_APK);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("无法拉起安装器：" + t.getMessage());
        }
    }

    private void openWith(File f) {
        String mime = guessMime(f);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // targetSdk 25 上 file:// 仍可用，不会触发 FileUriExposedException
            i.setDataAndType(Uri.fromFile(f), mime);
            startActivity(i);
        } catch (Throwable t) {
            toast("没有能打开 " + mime + " 的应用");
        }
    }

    private String guessMime(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) {
                return m;
            }
        }
        return "*/*";
    }

    private void rename(final File f) {
        final EditText et = new EditText(this);
        et.setText(f.getName());
        et.setSelection(et.getText().length());
        et.setTextColor(TEXT);
        et.setHintTextColor(MUTED);
        new AlertDialog.Builder(this)
                .setTitle("重命名为")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (n.isEmpty() || n.contains("/")) {
                            toast("名称不合法");
                            return;
                        }
                        File dst = new File(f.getParentFile(), n);
                        if (dst.exists()) {
                            toast("同名文件已存在");
                            return;
                        }
                        if (f.renameTo(dst)) {
                            toast("已重命名");
                            refreshListDelayed();
                        } else {
                            toast("重命名失败");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(final File f) {
        String what = f.isDirectory() ? "文件夹及其全部内容" : "文件";
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("将删除" + what + "：\n\n" + f.getAbsolutePath()
                        + "\n\n此操作不可撤销。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        boolean ok = deleteRecursive(f);
                        toast(ok ? "已删除" : "删除失败，可能没有权限");
                        refreshListDelayed();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return true;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (!deleteRecursive(k)) {
                        return false;
                    }
                }
            }
        }
        return f.delete();
    }

    private void createFolder() {
        final EditText et = new EditText(this);
        et.setHint("文件夹名称");
        et.setTextColor(TEXT);
        et.setHintTextColor(MUTED);
        new AlertDialog.Builder(this)
                .setTitle("在当前位置新建文件夹")
                .setView(et)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (n.isEmpty() || n.contains("/")) {
                            toast("名称不合法");
                            return;
                        }
                        File dst = new File(current, n);
                        if (dst.exists()) {
                            toast("同名项已存在");
                            return;
                        }
                        if (dst.mkdirs()) {
                            toast("已创建");
                            // 对话框关闭与列表刷新存在时序竞争：直接调
                            // navigateTo 时，AlertDialog 的 dismiss 动画还没结束，
                            // 刷新可能被覆盖。延迟一拍再刷，确保用户看到新目录。
                            refreshListDelayed();
                        } else {
                            toast("创建失败，可能没有写入权限");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doPaste() {
        if (clipboard == null) {
            toast("先复制或剪切一个文件");
            return;
        }
        if (!clipboard.exists()) {
            toast("源文件已不存在");
            clipboard = null;
            return;
        }
        File dst = new File(current, clipboard.getName());
        if (dst.getAbsolutePath().equals(clipboard.getAbsolutePath())) {
            toast("源和目标是同一个位置");
            return;
        }
        if (dst.exists()) {
            toast("目标位置已有同名项");
            return;
        }
        try {
            copyRecursive(clipboard, dst);
            if (clipboardIsCut) {
                deleteRecursive(clipboard);
                toast("已移动");
                clipboard = null;
            } else {
                toast("已复制");
            }
            refreshListDelayed();
        } catch (Throwable t) {
            toast("失败：" + t.getMessage());
        }
    }

    private void copyRecursive(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new Exception("无法创建目录 " + dst.getName());
            }
            File[] kids = src.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    copyRecursive(k, new File(dst, k.getName()));
                }
            }
            return;
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void showDetail(FileEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("路径\n").append(e.file.getAbsolutePath()).append("\n\n");
        sb.append("类型\n").append(e.isDir ? "文件夹" : (e.ext().isEmpty()
                ? "未知" : e.ext().toUpperCase(Locale.US) + " 文件")).append("\n\n");
        if (!e.isDir) {
            sb.append("大小\n").append(FileEntry.sizeText(e.size))
                    .append("（").append(e.size).append(" 字节）\n\n");
        }
        sb.append("修改时间\n").append(FileEntry.dateText(e.lastModified)).append("\n\n");
        sb.append("可读 / 可写 / 可执行\n")
                .append(e.file.canRead() ? "是" : "否").append(" / ")
                .append(e.file.canWrite() ? "是" : "否").append(" / ")
                .append(e.file.canExecute() ? "是" : "否");

        new AlertDialog.Builder(this)
                .setTitle("详情")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
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
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
