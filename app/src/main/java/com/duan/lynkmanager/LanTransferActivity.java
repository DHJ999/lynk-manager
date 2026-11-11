package com.duan.lynkmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LanTransferActivity extends BaseActivity {

    private static final int BG = 0xFF101418;
    private static final int CARD = 0xFF1B2128;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int OK = 0xFF4CAF50;
    private static final int WARN = 0xFFE6A23C;
    private static final int LINE = 0xFF2C343E;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SimpleHttpServer server;
    private ImageView qrView;
    private TextView urlView;
    private TextView statusView;
    private TextView logView;
    private Button startBtn;
    private File uploadDir;
    private File exportDir;
    private LinearLayout listBox;
    private TextView listSummary;
    private LinearLayout exportBox;
    private TextView exportSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File base = getExternalFilesDir(null);
        uploadDir = new File(base, "uploads");
        exportDir = new File(base, "export");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        migrateLegacyExports(base, exportDir);
        setContentView(buildUi());
        statusView.setText("未启动");
        log("上传目录：" + uploadDir.getAbsolutePath());
        log("导出目录：" + exportDir.getAbsolutePath());
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    // ---------------- 服务 ----------------

    private void startServer() {
        if (server != null && server.isRunning()) {
            return;
        }
        String ip = localIp();
        if (ip == null) {
            toast("没有找到局域网地址，请先连上 Wi-Fi");
            return;
        }
        server = new SimpleHttpServer(uploadDir, exportDir, new SimpleHttpServer.Listener() {
            @Override
            public void onEvent(final String msg) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        log(msg);
                        // 上传落盘后再刷新列表，等写入收尾
                        ui.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                refreshAll();
                            }
                        }, 400);
                    }
                });
            }
        });
        try {
            server.start();
        } catch (Throwable t) {
            server = null;
            toast("启动失败：" + t.getMessage());
            log("启动失败：" + t.getMessage());
            return;
        }
        String url = "http://" + ip + ":" + server.getPort();
        urlView.setText(url);
        statusView.setText("运行中 · 手机连同一 Wi-Fi 后扫码");
        statusView.setTextColor(OK);
        qrView.setImageBitmap(makeQr(url, Math.min(640, dp(300))));
        startBtn.setText("停止服务");
        startBtn.setBackground(box(WARN, 12));
        log("二维码地址：" + url);
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        statusView.setText("未启动");
        statusView.setTextColor(MUTED);
        urlView.setText("—");
        qrView.setImageDrawable(null);
        startBtn.setText("启动服务");
        startBtn.setBackground(box(OK, 12));
    }

    private Bitmap makeQr(String text, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) {
                int off = y * size;
                for (int x = 0; x < size; x++) {
                    px[off + x] = m.get(x, y) ? 0xFF0C1014 : 0xFFFFFFFF;
                }
            }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            log("二维码生成失败：" + t.getMessage());
            return null;
        }
    }

    private String localIp() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip == null || !addr.isSiteLocalAddress()) {
                        continue;
                    }
                    if (name.startsWith("wlan") || name.startsWith("wifi")
                            || name.startsWith("eth")) {
                        return ip;
                    }
                    if (fallback == null) {
                        fallback = ip;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    // ---------------- 界面 ----------------

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

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(tv("传文件", 24, TEXT, true));

        TextView sub = tv("手机扫码后选文件上传，落到车机本地；上传记录会保留，可随时删除",
                13, MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(box(CARD, 14));
        int cp = dp(18);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(14);
        root.addView(card, clp);

        statusView = tv("未启动", 14, MUTED, true);
        card.addView(statusView);

        qrView = new ImageView(this);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(240), dp(240));
        qlp.topMargin = dp(12);
        qlp.bottomMargin = dp(12);
        card.addView(qrView, qlp);

        urlView = tv("—", 15, OK, true);
        urlView.setTextIsSelectable(true);
        urlView.setGravity(Gravity.CENTER);
        card.addView(urlView);

        TextView hint = tv("上传直接打开上面的地址 · 下载在地址后加 /dl", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        card.addView(hint);

        Button copyBtn = new Button(this);
        copyBtn.setAllCaps(false);
        copyBtn.setText("复制地址");
        copyBtn.setTextSize(14);
        copyBtn.setTextColor(TEXT);
        copyBtn.setBackground(box(LINE, 10));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        cblp.topMargin = dp(10);
        card.addView(copyBtn, cblp);
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", urlView.getText().toString()));
                toast("已复制");
            }
        });

        startBtn = new Button(this);
        startBtn.setAllCaps(false);
        startBtn.setText("启动服务");
        startBtn.setTextSize(18);
        startBtn.setTextColor(0xFFFFFFFF);
        startBtn.setBackground(box(OK, 12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        slp.bottomMargin = dp(14);
        root.addView(startBtn, slp);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (server != null && server.isRunning()) {
                    stopServer();
                } else {
                    startServer();
                }
            }
        });

        // ---- 上传记录 ----
        LinearLayout recHead = new LinearLayout(this);
        recHead.setOrientation(LinearLayout.HORIZONTAL);
        recHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rhlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rhlp.bottomMargin = dp(2);
        root.addView(recHead, rhlp);

        TextView recTitle = tv("上传记录", 16, TEXT, true);
        recHead.addView(recTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        listSummary = tv("", 12, MUTED, false);
        recHead.addView(listSummary);

        TextView dir = tv(uploadDir == null ? "" : uploadDir.getAbsolutePath(), 11, 0xFF6B7785, false);
        dir.setTextIsSelectable(true);
        dir.setPadding(0, dp(2), 0, dp(8));
        root.addView(dir);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 导出的 APK ----
        LinearLayout expHead = new LinearLayout(this);
        expHead.setOrientation(LinearLayout.HORIZONTAL);
        expHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ehlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ehlp.topMargin = dp(18);
        ehlp.bottomMargin = dp(2);
        root.addView(expHead, ehlp);

        TextView expTitle = tv("导出的 APK", 16, TEXT, true);
        expHead.addView(expTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        exportSummary = tv("", 12, MUTED, false);
        expHead.addView(exportSummary);

        TextView expHint = tv("启动服务后，在浏览器打开地址 + /dl 即可下载到电脑或手机", 11, 0xFF6B7785, false);
        expHint.setPadding(0, dp(2), 0, dp(8));
        root.addView(expHint);

        exportBox = new LinearLayout(this);
        exportBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(exportBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        logView = tv("", 12, 0xFFA8B4C0, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setBackground(box(0xFF0C1014, 10));
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(logView);

        return sv;
    }

    private void log(String msg) {
        if (logView != null) {
            logView.append("· " + msg + "\n");
        }
    }

    /** 1.4.0 之前把导出的 APK 直接放在 files/ 根目录，这里搬到 export/，避免继续散落。 */
    private void migrateLegacyExports(File base, File exportDir) {
        if (base == null) {
            return;
        }
        File[] fs = base.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".apk")) {
                File dst = new File(exportDir, f.getName());
                if (!dst.exists()) {
                    f.renameTo(dst);
                }
            }
        }
    }

    // ---------------- 上传记录 ----------------

    /** 直接扫描目录，文件在即在记录在，无需额外持久化。 */
    private void refreshAll() {
        refreshSection(listBox, listSummary, uploadDir, "还没有上传记录");
        refreshSection(exportBox, exportSummary, exportDir, "还没有导出的文件");
    }

    private void refreshSection(LinearLayout box, TextView summary, File folder, String emptyText) {
        if (box == null) {
            return;
        }
        box.removeAllViews();
        List<File> files = listFiles(folder);

        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        if (summary != null) {
            summary.setText(files.isEmpty() ? "" : files.size() + " 个 · " + sizeText(total));
        }

        if (files.isEmpty()) {
            TextView empty = tv(emptyText, 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(26), 0, dp(26));
            box.addView(empty);
            return;
        }
        for (File f : files) {
            box.addView(recordRow(f));
        }
    }

    private List<File> listFiles(File folder) {
        List<File> files = new ArrayList<>();
        File[] fs = folder == null ? null : folder.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && !f.getName().startsWith(".")) {
                    files.add(f);
                }
            }
        }
        files.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return files;
    }

    private View recordRow(final File f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(box(CARD, 12));
        int p = dp(14);
        row.setPadding(p, dp(12), p, dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(8);
        row.setLayoutParams(rlp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = tv(f.getName(), 14, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(name);

        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        TextView meta = tv(sizeText(f.length()) + " · " + fmt.format(new Date(f.lastModified())),
                11, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        col.addView(meta);

        Button del = new Button(this);
        del.setAllCaps(false);
        del.setText("删除");
        del.setTextSize(13);
        del.setTextColor(0xFFE86C6C);
        del.setMinWidth(0);
        del.setMinimumWidth(0);
        del.setPadding(dp(14), 0, dp(14), 0);
        del.setBackground(box(LINE, 9));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        dlp.leftMargin = dp(12);
        row.addView(del, dlp);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(f);
            }
        });
        return row;
    }

    private void confirmDelete(final File f) {
        new AlertDialog.Builder(this)
                .setTitle("删除上传记录")
                .setMessage(f.getName() + "\n\n删除上传记录的同时删除本地文件。")
                .setPositiveButton("删除", (d, w) -> {
                    boolean ok = f.delete();
                    toast(ok ? "已删除：" + f.getName() : "删除失败，文件可能被占用");
                    refreshAll();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
