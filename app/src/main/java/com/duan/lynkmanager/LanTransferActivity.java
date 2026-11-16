package com.duan.lynkmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    /** 安装权限自检条：权限被系统设成 deny 时显示，点开给出修复命令。 */
    private TextView permBar;
    private TextView logView;
    /** 多网段候选地址提示，只有一个候选时隐藏。 */
    private TextView candView;
    /** WiFi 直连状态行。 */
    private TextView wifiState;
    /** 车机开热点 / 连手机热点的执行者。 */
    private WifiDirect wifiDirect;
    /** ① 连热点用的二维码（WIFI: 协议）。 */
    private ImageView qrWifiView;
    private TextView qrWifiCaption;
    /** ② 上传页二维码（http://ip:port）。 */
    private TextView qrHttpCaption;
    /** 当前热点凭据，用于生成 WiFi 二维码；停止服务时清空。 */
    private String hotSsid;
    private String hotPass;
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
        wifiDirect = new WifiDirect(this);
        log("上传目录：" + uploadDir.getAbsolutePath());
        log("导出目录：" + exportDir.getAbsolutePath());
        registerNetCallback();
        refreshAll();
        requestWifiPermissionsIfNeeded();
        refreshWifiState();
    }

    /** Android 10+ 开热点/连热点必须给定位权限，这里主动申请一次。 */
    private void requestWifiPermissionsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return;
        }
        if (wifiDirect != null && wifiDirect.hasLocationPermission()) {
            return;
        }
        try {
            requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        } catch (Throwable t) {
            log("申请定位权限失败：" + t.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1001) {
            boolean ok = results.length > 0
                    && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            log(ok ? "已获得定位权限，可以开热点/连热点" : "未授予定位权限，热点功能不可用");
            refreshWifiState();
        }
    }

    /** 刷新 WiFi 状态行，告诉用户当前该点哪个按钮。 */
    private void refreshWifiState() {
        if (wifiState == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String[]> ips = listLocalIps();
                boolean hasWifi = false;
                final StringBuilder sb = new StringBuilder();
                for (String[] c : ips) {
                    String n = c[0];
                    if (isWifiNic(n)) {
                        hasWifi = true;
                        sb.append(n).append(" = ").append(c[1]).append('\n');
                    }
                }
                // WiFi 硬件开着但没拿到 IP：这属于「开着却没连上」，
                // 和「WiFi 整个关闭」是两回事，提示要分开写，
                // 否则用户会以为自己明明连着热点、软件却瞎报。
                final boolean wifiEnabled = isWifiEnabled();
                final boolean ok = hasWifi;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            wifiState.setText("已连接：" + sb.toString().trim());
                            wifiState.setTextColor(OK);
                        } else if (wifiEnabled) {
                            wifiState.setText("WiFi 已开启，但没有连上任何热点，"
                                    + "所以拿不到局域网地址。\n"
                                    + "点「连手机热点」输入热点名和密码，或点「车机开热点」。");
                            wifiState.setTextColor(WARN);
                        } else {
                            wifiState.setText("WiFi 未开启。点「车机开热点」让手机连车机，"
                                    + "或点「连手机热点」让车机连手机。");
                            wifiState.setTextColor(WARN);
                        }
                    }
                });
            }
        }).start();
    }

    private boolean isWifiNic(String name) {
        String n = name == null ? "" : name.toLowerCase();
        return n.startsWith("wlan") || n.startsWith("ap")
                || n.startsWith("swlan") || n.startsWith("softap")
                || n.startsWith("wifi");
    }

    /** WiFi 硬件是否处于开启状态。 */
    private boolean isWifiEnabled() {
        try {
            android.net.wifi.WifiManager wm =
                    (android.net.wifi.WifiManager) getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 生成「扫码连 WiFi」二维码。
     *
     * 内容用标准 WIFI: 协议，手机自带相机/微信扫一扫能直接弹出「加入网络」，
     * 不用手输那串随机密码。
     * 格式：WIFI:T:WPA;S:<ssid>;P:<pass>;;
     */
    private void showWifiQr() {
        if (qrWifiView == null) {
            return;
        }
        if (hotSsid == null || hotSsid.isEmpty() || hotPass == null || hotPass.isEmpty()) {
            // 拿不到凭据时不显示半残的二维码，改为文字提示
            qrWifiView.setVisibility(View.GONE);
            if (qrWifiCaption != null) {
                qrWifiCaption.setVisibility(View.VISIBLE);
                qrWifiCaption.setText("热点：" + (hotSsid == null ? "(未知)" : hotSsid)
                        + "\n在手机 WiFi 列表里手动连接");
            }
            return;
        }
        String payload = "WIFI:T:WPA;S:" + escapeWifi(hotSsid)
                + ";P:" + escapeWifi(hotPass) + ";;";
        Bitmap bm = makeQr(payload, Math.min(720, dp(220)));
        qrWifiView.setImageBitmap(bm);
        qrWifiView.setVisibility(View.VISIBLE);
        if (qrWifiCaption != null) {
            qrWifiCaption.setVisibility(View.VISIBLE);
            qrWifiCaption.setText("① 扫码连热点\n" + hotSsid);
        }
        log("WiFi 二维码已生成：" + hotSsid);
    }

    /** WIFI: 协议里 ; , : \ " 需要转义。 */
    private String escapeWifi(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void hideWifiQr() {
        hotSsid = null;
        hotPass = null;
        if (qrWifiView != null) {
            qrWifiView.setImageDrawable(null);
            qrWifiView.setVisibility(View.GONE);
        }
        if (qrWifiCaption != null) {
            qrWifiCaption.setText("");
            qrWifiCaption.setVisibility(View.GONE);
        }
    }

    /** 车机开热点：手机扫屏幕上的 WiFi 码即可连上。 */
    private void startCarHotspot() {
        if (wifiDirect == null) {
            return;
        }
        log("正在开热点…");
        toast("正在开热点…");
        wifiDirect.startHotspot(new WifiDirect.Callback() {
            @Override
            public void onHotspotStarted(String ssid, String pass) {
                hotSsid = ssid;
                hotPass = pass;
                log("热点已开启：" + ssid + (pass == null ? "（密码未知）" : " / " + pass));
                showWifiQr();
                refreshWifiState();
                waitForIpThenStart(20);
            }

            @Override
            public void onJoined(String ssid) {
                // 不会走到这里
            }

            @Override
            public void onError(String reason) {
                log("热点失败：" + reason);
                toast(reason);
                refreshWifiState();
            }
        });
    }

    /**
     * 等网卡拿到 IP 再启动服务。
     *
     * 开热点后 DHCP 分配 IP 有几百毫秒到几秒的延迟，
     * 立刻取 localIp() 往往还是 null。这里每秒重试一次，
     * 拿到就启动，超过次数就如实报错。
     */
    private void waitForIpThenStart(final int retriesLeft) {
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                String ip = localIp();
                if (ip != null) {
                    log("网络就绪：" + ip);
                    refreshWifiState();
                    if (server == null || !server.isRunning()) {
                        startServer();
                    }
                    return;
                }
                if (retriesLeft <= 0) {
                    log("等了很久仍未拿到局域网地址");
                    toast("没拿到局域网地址，请检查热点是否真的开启");
                    refreshWifiState();
                    return;
                }
                waitForIpThenStart(retriesLeft - 1);
            }
        }, 1000);
    }

    /** 车机连手机热点：输入 SSID 和密码。 */
    private void showJoinDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(6));

        EditText ssidEt = new EditText(this);
        ssidEt.setHint("热点名称（SSID）");
        ssidEt.setSingleLine(true);
        box.addView(ssidEt);

        EditText passEt = new EditText(this);
        passEt.setHint("密码");
        passEt.setSingleLine(true);
        passEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(passEt);

        new AlertDialog.Builder(this)
                .setTitle("连手机热点")
                .setView(box)
                .setPositiveButton("连接", (d, w) -> {
                    String ssid = ssidEt.getText().toString().trim();
                    String pass = passEt.getText().toString();
                    joinHotspot(ssid, pass);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void joinHotspot(String ssid, String pass) {
        if (wifiDirect == null) {
            return;
        }
        log("正在连接 " + ssid + " …");
        wifiDirect.joinNetwork(ssid, pass, new WifiDirect.Callback() {
            @Override
            public void onHotspotStarted(String s, String p) {
            }

            @Override
            public void onJoined(String s) {
                log("已连上 " + s);
                toast("已连接 " + s + "，正在启动服务…");
                refreshWifiState();
                waitForIpThenStart(15);
            }

            @Override
            public void onError(String reason) {
                log("连接失败：" + reason);
                toast(reason);
                refreshWifiState();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        refreshWifiState();
        // 每次回到页面都查一次安装权限：用户可能刚在安装弹窗上点了取消，
        // 那种情况下权限会变成 deny，必须立刻让他看到。
        refreshPermBar();
        // 预启用无障碍助手：不要等用户点「安装」才启用。
        // 实测应用被 force-stop 后无障碍服务要几秒才被系统重新绑定，
        // 如果在点安装的那一刻才启用，确认界面弹出时助手还没 ready，
        // 只能靠重试补救。提前到进页面就启用，用户上传/选文件那几十秒里
        // 服务早已连上，点安装即可立刻代点。
        if (readInstallOpMode().equals("allow") || readInstallOpMode().equals("default")) {
            final boolean[] done = {false};
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (done[0]) {
                        return;
                    }
                    done[0] = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // 只启用，不武装：此时用户还没点安装，
                            // 武装会让助手去点别的应用的安装窗口。
                            enableInstallClickService(false);
                            android.util.Log.i("LynkMgr", "无障碍助手已预启用（未武装）");
                        }
                    }).start();
                }
            }, 500);
        }
        // 一进页面就尝试自动拉起服务：有地址就起服务，没地址就引导去连网。
        // 用户不需要理解「WiFi 状体 -> IP -> 端口」这一串，点了就能用。
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (server != null && server.isRunning()) {
                    refreshAddrIfRunning();
                    return;
                }
                if (localIp() != null) {
                    startServer();
                }
            }
        }, 600);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注意：这里不再主动 stopServer()。
        // 原因：onDestroy 在「按返回退出」时也会调用，但用户往往只是切走一会儿，
        // 手机那侧可能还在传。服务跟着前台 Service 的生命周期走，
        // 由 stopServer()（用户点停止）或 TransferService 被系统销毁来收尾。
        if (wifiDirect != null) {
            wifiDirect.release();
            wifiDirect = null;
        }
        stopForegroundShell();
    }

    /** 拉起前台 Service，把进程优先级钉住，避免后台被回收导致上传卡死。 */
    private void startForegroundShell() {
        try {
            Intent i = new Intent(this, TransferService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            android.util.Log.i("LynkMgr", "前台服务已启动");
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "前台服务启动失败", t);
        }
    }

    private void stopForegroundShell() {
        try {
            stopService(new Intent(this, TransferService.class));
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 服务 ----------------

    private void startServer() {
        if (server != null && server.isRunning()) {
            return;
        }
        // 不再因为「没检测到 WiFi 地址」就拒绝启动。
        // 实测这台车机：WiFi 断开后 wlan0 没有 IP，但用户以为一直连着，
        // 弹「先打开 WiFi」只会让人困惑。改为：能启动就启动（绑 0.0.0.0），
        // 地址栏如实显示当前可用的地址或明确的不可达提示。
        String ip = localIp();
        if (ip == null) {
            // 没有可用局域网地址：直接开热点，屏幕上出 WiFi 二维码。
            // 手机扫码连上 -> 车机拿到 IP -> 自动启动服务并换成上传二维码。
            // 不需要用户理解网络概念，也不用先弹一堆选项。
            log("当前无局域网地址，自动开启热点");
            startCarHotspot();
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
        statusView.setText(hotSsid != null
                ? "运行中 · 手机先用①扫码连热点，再用②扫码上传"
                : "运行中 · 手机连同一个 WiFi 后访问上面的地址");
        statusView.setTextColor(OK);
        qrView.setImageBitmap(makeQr(url, Math.min(720, dp(220))));
        if (qrHttpCaption != null) {
            qrHttpCaption.setVisibility(View.VISIBLE);
            qrHttpCaption.setText("② 扫码上传");
        }
        startBtn.setText("停止服务");
        startBtn.setBackground(box(WARN, 12));
        log("服务地址：" + url);
        showCandidates(server.getPort());
        // 服务起来就把进程钉住：否则退到后台被系统回收后，
        // 手机上再点上传会一直卡在进度条（请求连不上）。
        startForegroundShell();
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        // 关服务同时关热点：车机热点常开会耗电、也会让原厂网络设置变乱，
        // 用户要的是「用完就收干净」。
        if (wifiDirect != null) {
            wifiDirect.release();
        }
        hideWifiQr();
        statusView.setText("未启动");
        statusView.setTextColor(MUTED);
        urlView.setText("—");
        qrView.setImageDrawable(null);
        if (qrHttpCaption != null) {
            qrHttpCaption.setText("");
            qrHttpCaption.setVisibility(View.GONE);
        }
        if (candView != null) {
            candView.setVisibility(View.GONE);
        }
        startBtn.setText("启动服务");
        startBtn.setBackground(box(OK, 12));
        // 用户主动停止：连前台 Service 一起收掉
        stopForegroundShell();
        refreshWifiState();
        log("已停止服务并关闭热点");
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

    /** 判断网卡是不是虚拟设备（virtio / vdevs）。 */
    private boolean isVirtualNic(String name) {
        try {
            String link = new java.io.File("/sys/class/net/" + name).getCanonicalPath();
            if (link.contains("virtio") || link.contains("vdevs")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 选一个手机能访问到的局域网地址。
     *
     * 实车教训：车机上有 eth1 = 192.168.8.2/24，那是 virtio 虚拟网卡
     * （/sys/devices/platform/vdevs/.../virtio18/net/eth1），对端 192.168.8.1
     * 的 MAC 是 AA:AA:AA:AA:AA:AA，手机根本路由不到。
     * 旧逻辑「eth 开头优先」正好选中它，导致地址栏显示一个永远打不开的 IP。
     *
     * 现在按网卡类型打分：
     *   wlan/ap/swlan/softap/wifi  → 100（真正的 WiFi，最可能是手机所在网段）
     *   eth                         → 50（有线，可能是真 LAN）
     *   其它                        → 10
     *   rmnet/ppp/ccmni/tun 等蜂窝 → 直接排除
     *   virtio 虚拟网卡            → 直接排除
     * 同时把全部候选记录下来给界面展示，避免只押一个地址。
     */
    private String localIp() {
        List<String[]> cands = listLocalIps();
        return cands.isEmpty() ? null : cands.get(0)[1];
    }

    /** @return 按优先级排序的 [网卡名, IP] 列表 */
    private List<String[]> listLocalIps() {
        List<String[]> scored = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                // 蜂窝 / 隧道 / 点对点链路一律跳过
                if (name.startsWith("rmnet") || name.startsWith("ppp")
                        || name.startsWith("ccmni") || name.startsWith("tun")
                        || name.startsWith("dummy") || name.startsWith("sit")) {
                    continue;
                }
                // 虚拟网卡（virtio）也要排除：
                // 车机上 eth1=192.168.8.2 就是 virtio18 虚拟链路，手机到不了。
                if (isVirtualNic(name)) {
                    continue;
                }
                int score;
                if (name.startsWith("wlan") || name.startsWith("ap")
                        || name.startsWith("swlan") || name.startsWith("softap")
                        || name.startsWith("wifi")) {
                    score = 100;
                } else if (name.startsWith("eth")) {
                    score = 50;
                } else {
                    score = 10;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    if (!addr.isSiteLocalAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip == null) {
                        continue;
                    }
                    scored.add(new String[]{name, ip, String.valueOf(score)});
                }
            }
        } catch (Throwable ignored) {
        }
        scored.sort(new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Integer.parseInt(b[2]) - Integer.parseInt(a[2]);
            }
        });
        // 去掉重复 IP
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] c : scored) {
            if (seen.add(c[1])) {
                out.add(c);
            }
        }
        android.util.Log.i("LynkMgr", "localIps=" + describeIps(out));
        return out;
    }

    private String describeIps(List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] c : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c[0]).append('=').append(c[1]);
        }
        return sb.length() == 0 ? "(无)" : sb.toString();
    }

    /**
     * 监听网络变化：车机连上热点、或打开热点后，地址会自动变。
     * 服务在跑的时候把新地址刷到界面上，免得用户看着一个过期地址打不开。
     */
    private void registerNetCallback() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }
            cm.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    refreshAddrIfRunning();
                }

                @Override
                public void onLost(android.net.Network network) {
                    refreshAddrIfRunning();
                }

                @Override
                public void onLinkPropertiesChanged(android.net.Network network,
                                                    android.net.LinkProperties lp) {
                    refreshAddrIfRunning();
                }
            });
        } catch (Throwable t) {
            log("网络监听注册失败：" + t.getMessage());
        }
    }

    /** 服务在跑时，按当前网卡重算地址并刷新界面。 */
    private void refreshAddrIfRunning() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (server == null || !server.isRunning()) {
                    return;
                }
                String ip = localIp();
                if (ip == null) {
                    // 网卡还没拿到地址，稍后再试
                    ui.postDelayed(this, 1500);
                    return;
                }
                String url = "http://" + ip + ":" + server.getPort();
                if (!url.equals(urlView.getText().toString())) {
                    urlView.setText(url);
                    qrView.setImageBitmap(makeQr(url, Math.min(640, dp(300))));
                    log("地址已更新：" + url);
                    showCandidates(server.getPort());
                }
            }
        });
    }

    /** Wi-Fi 是否处于可用状态（连上 AP 或开了热点）。 */
    private boolean wifiUsable() {
        for (String[] c : listLocalIps()) {
            String name = c[0];
            if (name.startsWith("wlan") || name.startsWith("ap")
                    || name.startsWith("swlan") || name.startsWith("softap")
                    || name.startsWith("wifi")) {
                return true;
            }
        }
        return false;
    }

    /** 跳到车机的 WiFi 设置页。不同 ROM 的入口不一样，逐个试。 */
    private void openWifiSettings() {
        String[] candidates = {
                "com.geely.settings/com.geely.settings.GlySettingsActivity",
                "com.android.settings/.Settings$WifiSettingsActivity",
                "com.android.settings/.wifi.WifiSettings",
        };
        for (String c : candidates) {
            try {
                android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(c);
                Intent i = new Intent();
                i.setComponent(cn);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            toast("打不开设置，请手动进车机设置里的 WiFi");
        }
    }

    /**
     * 把所有候选地址都列出来。
     *
     * 车机上常有多个私有网段（4G 模块、车厂内部网），只有一个手机能访问。
     * 只显示一个地址时用户没法判断，全列出来逐个试最快。
     */
    private void showCandidates(int port) {
        if (candView == null) {
            return;
        }
        List<String[]> cands = listLocalIps();
        if (cands.size() <= 1) {
            candView.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("其它候选地址（打不开就换一个试）：\n");
        for (String[] c : cands) {
            sb.append("· ").append(c[0]).append("  http://").append(c[1])
                    .append(':').append(port).append('\n');
        }
        if (!wifiUsable()) {
            sb.append("\n当前 Wi-Fi 未连接，上车手机只有连到车机同一网络才能访问。");
        }
        candView.setText(sb.toString());
        candView.setVisibility(View.VISIBLE);
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

        // 标题行：返回 + 标题，返回固定在左上角，避免只能靠系统手势退出
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        Button backBtn = new Button(this);
        backBtn.setAllCaps(false);
        backBtn.setText("← 返回");
        backBtn.setTextSize(14);
        backBtn.setTextColor(TEXT);
        backBtn.setMinWidth(0);
        backBtn.setMinimumWidth(0);
        backBtn.setMinHeight(0);
        backBtn.setMinimumHeight(0);
        backBtn.setIncludeFontPadding(false);
        backBtn.setPadding(dp(16), 0, dp(16), 0);
        backBtn.setBackground(box(LINE, 10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        blp.rightMargin = dp(14);
        head.addView(backBtn, blp);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        head.addView(tv("传文件", 24, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        TextView sub = tv("手机扫码上传 APK 到车机；上传后点记录即可安装，记录可随时删除",
                13, MUTED, false);
        sub.setPadding(0, dp(10), 0, dp(14));
        root.addView(sub);

        // 安装权限自检条：默认隐藏，权限被系统设成 deny 时显示成红色。
        // 为什么需要：车机 PackageInstaller 有厂商改过的来源校验，
        // 用户在弹窗上点一次「取消」就会把 REQUEST_INSTALL_PACKAGES 写成 deny，
        // 之后所有安装都静默失败，界面只显示"已提交安装"，看不出原因。
        permBar = tv("", 13, 0xFFFFFFFF, true);
        permBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        permBar.setBackground(box(0xFFE05B5B, 12));
        permBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pblp.bottomMargin = dp(12);
        root.addView(permBar, pblp);
        permBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPermFixDialog();
            }
        });

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

        // 两个二维码并排：
        //   ① WiFi 码——手机相机扫它直接连上热点，不用手输密码
        //   ② 上传码——连上热点后扫它打开上传页
        LinearLayout qrRow = new LinearLayout(this);
        qrRow.setOrientation(LinearLayout.HORIZONTAL);
        // TOP 对齐：两列说明文字行数不同时，二维码仍在同一水平线上
        qrRow.setGravity(Gravity.TOP);
        // 宽度用 WRAP_CONTENT，靠 card 的 CENTER_HORIZONTAL 整体居中，
        // 两个码才不会分别贴到卡片左右两端
        LinearLayout.LayoutParams qrRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrRowLp.topMargin = dp(12);
        qrRowLp.bottomMargin = dp(12);
        card.addView(qrRow, qrRowLp);

        qrWifiView = new ImageView(this);
        qrWifiView.setVisibility(View.GONE);
        LinearLayout wifiCol = new LinearLayout(this);
        wifiCol.setOrientation(LinearLayout.VERTICAL);
        wifiCol.setGravity(Gravity.CENTER_HORIZONTAL);
        // 两列固定同宽，间距一致；二维码尺寸固定，说明文字最少两行，
        // 这样左右两列的二维码必然在同一水平线上。
        LinearLayout.LayoutParams wcolLp = new LinearLayout.LayoutParams(
                dp(240), ViewGroup.LayoutParams.WRAP_CONTENT);
        wcolLp.rightMargin = dp(20);
        qrRow.addView(wifiCol, wcolLp);
        wifiCol.addView(qrWifiView, new LinearLayout.LayoutParams(dp(220), dp(220)));
        qrWifiCaption = tv("① 扫码连热点", 12, MUTED, false);
        qrWifiCaption.setGravity(Gravity.CENTER);
        qrWifiCaption.setPadding(0, dp(8), 0, 0);
        qrWifiCaption.setMinLines(2);
        qrWifiCaption.setVisibility(View.GONE);
        wifiCol.addView(qrWifiCaption);

        qrView = new ImageView(this);
        LinearLayout httpCol = new LinearLayout(this);
        httpCol.setOrientation(LinearLayout.VERTICAL);
        httpCol.setGravity(Gravity.CENTER_HORIZONTAL);
        qrRow.addView(httpCol, new LinearLayout.LayoutParams(
                dp(240), ViewGroup.LayoutParams.WRAP_CONTENT));
        httpCol.addView(qrView, new LinearLayout.LayoutParams(dp(220), dp(220)));
        qrHttpCaption = tv("② 扫码上传", 12, MUTED, false);
        qrHttpCaption.setGravity(Gravity.CENTER);
        qrHttpCaption.setPadding(0, dp(8), 0, 0);
        qrHttpCaption.setMinLines(2);
        qrHttpCaption.setVisibility(View.GONE);
        httpCol.addView(qrHttpCaption);

        urlView = tv("—", 15, OK, true);
        urlView.setTextIsSelectable(true);
        urlView.setGravity(Gravity.CENTER);
        card.addView(urlView);

        TextView hint = tv("上传直接打开上面的地址 · 下载在地址后加 /dl", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        card.addView(hint);

        // 多网段候选：车机上常有 4G 模块内部网段，只有一个手机能访问，
        // 只给一个地址用户没法判断，全部列出来逐个试最快。
        candView = tv("", 12, MUTED, false);
        candView.setVisibility(View.GONE);
        candView.setPadding(0, dp(10), 0, 0);
        card.addView(candView);

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
        // 同时写 logcat：现场排障时 adb 能直接看到，不用盯着车机屏幕滚动
        android.util.Log.i("LynkMgr", msg);
        if (logView != null) {
            logView.append("· " + msg + "\n");
        }
    }

    // ---------------- 安装权限自检 ----------------

    /**
     * 检查 REQUEST_INSTALL_PACKAGES 是否仍然放行，并刷新顶部提示条。
     *
     * 三种状态：
     *   放行        → 隐藏提示条
     *   deny/ignored → 红色提示条，点开给修复命令
     *   未知        → 隐藏（不去打扰）
     */
    private void refreshPermBar() {
        if (permBar == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String mode = readInstallOpMode();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (permBar == null) {
                            return;
                        }
                        if ("deny".equals(mode) || "ignored".equals(mode)) {
                            permBar.setVisibility(View.VISIBLE);
                            permBar.setText("⚠ 安装权限已被关闭（" + mode
                                    + "），点这里查看修复方法");
                            permBar.setBackground(box(0xFFE05B5B, 12));
                        } else {
                            permBar.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * 判断安装权限是否被关掉。
     *
     * 不能用 `cmd appops get`：那是 shell 命令，普通应用调用拿不到结果
     * （实测返回空，导致红条永不出现）。
     * 改用 AppOpsManager.checkOpNoThrow —— 这是给应用读自己 appops 状态的正式 API，
     * 不需要任何权限。
     */
    private String readInstallOpMode() {
        try {
            android.app.AppOpsManager aom =
                    (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) {
                return "unknown";
            }
            // compileSdk 36 上公开的只有字符串版 checkOpNoThrow(
            // String op, int uid, String packageName)。用 op 名字查即可，
            // 不需要知道 op code。
            int mode = aom.checkOpNoThrow(
                    "android:request_install_packages",
                    android.os.Process.myUid(), getPackageName());
            switch (mode) {
                case android.app.AppOpsManager.MODE_ALLOWED:
                    return "allow";
                case android.app.AppOpsManager.MODE_IGNORED:
                    return "ignored";
                case android.app.AppOpsManager.MODE_ERRORED:
                    return "deny";
                default:
                    return "default";
            }
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "读 appops 失败", t);
            return "unknown";
        }
    }

    /** 展示修复命令，可一键复制。 */
    private void showPermFixDialog() {
        final String cmd = "adb shell appops set " + getPackageName()
                + " REQUEST_INSTALL_PACKAGES allow";
        new AlertDialog.Builder(this)
                .setTitle("安装权限被关闭")
                .setMessage("系统把「安装未知应用」关掉了，所以点记录只会显示"
                        + "「已提交安装」但没有后续。\n\n"
                        + "这通常是因为之前在安装弹窗上点了「取消」或「设置」。\n\n"
                        + "在电脑上执行下面这条命令恢复：\n\n"
                        + cmd + "\n\n"
                        + "执行后不用重装，回到本页点记录即可安装。\n"
                        + "重启车机也不会失效。")
                .setPositiveButton("复制命令", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("cmd", cmd));
                        toast("已复制");
                    } catch (Throwable t) {
                        toast("复制失败，请手动记录");
                    }
                })
                .setNeutralButton("重新检测", (d, w) -> refreshPermBar())
                .setNegativeButton("关闭", null)
                .show();
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
        final boolean isApk = f.getName().toLowerCase(Locale.US).endsWith(".apk");
        String metaText = sizeText(f.length()) + " · " + fmt.format(new Date(f.lastModified()))
                + (isApk ? " · 点击安装" : "");
        TextView meta = tv(metaText, 11, isApk ? OK : MUTED, false);
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

        // 整行点击即安装（仅 APK）。删除按钮自己消费点击，不会误触。
        if (isApk) {
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    installApk(f);
                }
            });
        }
        return row;
    }

    /** 点记录直接拉起系统安装器。 */
    private void installApk(final File f) {
        if (!f.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            toast("只能安装 .apk 文件");
            return;
        }
        // 先在后台线程查真实 appops 状态，避免在主线程跑命令卡界面。
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String mode = readInstallOpMode();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if ("deny".equals(mode) || "ignored".equals(mode)) {
                            refreshPermBar();
                            showPermFixDialog();
                            return;
                        }
                        // 主路径用 PackageInstaller 会话（MODE_FULL_INSTALL）。
                        //
                        // 为什么不用 ACTION_VIEW + content://：
                        // 实测这台车机的 InstallStart 会把 file:// 判成 staged 安装，
                        // 走 DeleteStagedFileOnResult → 最终 InstallFailed「未安装应用。」，
                        // 而同一个 APK 用 pm install 是 Success，说明 APK 没问题，
                        // 是 ROM 的 staged 分支装不上。
                        // 会话机制不经过 InstallStart，能正常装；
                        // 确认界面上的「安装」按钮由无障碍助手代点。
                        installBySession(f);
                    }
                });
            }
        }).start();
    }

    /**
     * 安装前先查一次权限。
     *
     * 已经碰到过实例：用户在 PackageInstaller 弹窗上点了取消，
     * 系统把 REQUEST_INSTALL_PACKAGES 写成 deny，
     * 之后点记录只显示「已提交安装」但没有任何后续，且不再弹窗。
     * 这里提前拦住，直接把原因和修复命令摆出来。
     */
    private boolean ensureInstallAllowed() {
        String mode = readInstallOpMode();
        if ("deny".equals(mode) || "ignored".equals(mode)) {
            refreshPermBar();
            showPermFixDialog();
            return false;
        }
        return true;
    }

    /**
     * 授权缺失时的提示。
     *
     * 不弹「去设置」——车机上那个页面不存在，点了只会 Toast「打不开设置页」，
     * 用户白折腾。直接把唯一可行的一行 adb 命令给出来。
     */
    private void showInstallPermHelp() {
        String cmd = "adb shell appops set " + getPackageName()
                + " REQUEST_INSTALL_PACKAGES allow";
        new AlertDialog.Builder(this)
                .setTitle("需要一次性授权")
                .setMessage("系统还没允许本应用安装 APK。\n\n"
                        + "这台车机的原厂设置里没有「安装未知应用」这一页，\n"
                        + "所以只能在电脑上执行一次下面的命令：\n\n"
                        + cmd + "\n\n"
                        + "执行完回来直接点记录即可，重启车机也不会失效。")
                .setPositiveButton("复制命令", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("cmd", cmd));
                        toast("已复制，粘到电脑终端执行");
                    } catch (Throwable t) {
                        toast("复制失败，请手动记录");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /**
     * 用 PackageInstaller 会话安装。
     *
     * 为什么不用 ACTION_VIEW + content:// ：
     * 这台车机的 PackageInstaller 虽然存在（/system/priv-app/PackageInstaller），
     * 但实测 query-activities 查不到任何处理 apk 的 VIEW 入口，
     * startActivity 会直接 "unable to resolve Intent"。
     * PackageInstaller 会话是 API 21+ 的正规通道，直接与系统安装器对话，
     * 不依赖 Intent 解析，也不受 file:// 限制。
     *
     * 失败时回退到 ACTION_VIEW，两条路都走到才算真失败。
     */
    private void installBySession(final File f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            installByIntent(f);
            return;
        }
        toast("正在准备安装…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 正常情况下服务已由 onResume 预启用，这里通常立刻 ready。
                // 若是 force-stop 后首次进入，服务可能还在重绑，给足 12 秒。
                enableInstallClickService();
                for (int i = 0; i < 40 && !InstallClickService.isReady(); i++) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {
                    }
                    if (i == 8 || i == 20 || i == 32) {
                        // 再 toggle 一次，加大重新绑定的机会
                        enableInstallClickService();
                    }
                }
                if (InstallClickService.isReady()) {
                    android.util.Log.i("LynkMgr", "无障碍助手就绪");
                } else {
                    // 兜底：助手没就绪就坚决不提交会话。
                    // 否则会弹出确认框，而 ROM 的「安装」按钮被防点击劫持过滤，
                    // 用户手指点不动，只能干等到超时被判取消——非常困惑。
                    // 改为明确失败 + 可重试。
                    android.util.Log.w("LynkMgr", "无障碍助手未就绪，取消本次提交");
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("安装助手正在启动，请等两秒后重新点一次记录");
                            log("安装助手未就绪，请重新点击");
                        }
                    });
                    return;
                }
                String err = null;
                int sessionId = -1;
                android.content.pm.PackageInstaller.Session session = null;
                try {
                    android.content.pm.PackageInstaller pi =
                            getPackageManager().getPackageInstaller();
                    android.content.pm.PackageInstaller.SessionParams params =
                            new android.content.pm.PackageInstaller.SessionParams(
                                    android.content.pm.PackageInstaller.SessionParams
                                            .MODE_FULL_INSTALL);
                    params.setAppPackageName(null);
                    sessionId = pi.createSession(params);
                    session = pi.openSession(sessionId);

                    java.io.InputStream in = new java.io.FileInputStream(f);
                    java.io.OutputStream out = session.openWrite("base.apk", 0, f.length());
                    try {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        long total = 0;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            total += n;
                        }
                        session.fsync(out);
                        android.util.Log.i("LynkMgr", "会话写入完成 " + total + " B");
                    } finally {
                        try {
                            in.close();
                        } catch (Throwable ignored) {
                        }
                        try {
                            out.close();
                        } catch (Throwable ignored) {
                        }
                    }

                    // commit 需要一个 PendingIntent 接收结果
                    Intent cb = new Intent(LanTransferActivity.this, InstallResultReceiver.class);
                    cb.setAction(InstallResultReceiver.ACTION_INSTALL_RESULT);
                    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_MUTABLE
                            : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                    android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(
                            LanTransferActivity.this, sessionId, cb, flags);
                    session.commit(pending.getIntentSender());
                    android.util.Log.i("LynkMgr", "会话已提交 id=" + sessionId);
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName() + ": " + t.getMessage();
                    android.util.Log.w("LynkMgr", "会话安装失败", t);
                    if (sessionId >= 0) {
                        try {
                            getPackageManager().getPackageInstaller()
                                    .abandonSession(sessionId);
                        } catch (Throwable ignored) {
                        }
                    }
                } finally {
                    if (session != null) {
                        try {
                            session.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                final String e = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (e == null) {
                            log("已提交安装：" + f.getName());
                        } else {
                            // 会话失败时不再回头调 installByIntent，
                            // 否则两条路互相兜底会无限递归。
                            log("会话安装失败：" + e);
                            toast("安装启动失败，请重试");
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * 主路径：ACTION_VIEW + content://（走 ApkProvider）。
     *
     * 车机的 InstallStart 明确声明支持
     *   Action VIEW / INSTALL_PACKAGE + Scheme content + apk StaticType，
     * 这条路会弹出标准「安装此应用吗？」确认框，点安装能正常生效。
     * 起不来时才回退到 PackageInstaller 会话。
     */
    private void installByIntent(File f) {
        String dir = f.getParentFile() == null ? "uploads" : f.getParentFile().getName();
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(ApkProvider.AUTHORITY)
                .appendPath("apk")
                .appendQueryParameter("dir", dir)
                .appendQueryParameter("name", f.getName())
                .build();
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, ApkProvider.MIME_APK);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 先确保无障碍助手在线并「武装」，否则安装器弹出来后按钮点不动。
        enableInstallClickService();

        try {
            startActivity(i);
            log("拉起系统安装器：" + f.getName());
        } catch (Throwable t) {
            log("Intent 方式失败（" + t.getClass().getSimpleName() + "），改用会话");
            installBySession(f);
        }
    }

    /**
     * 启用并武装无障碍安装助手。
     *
     * 必须自己写 Settings.Secure：这台车机的无障碍设置页同样是
     * com.android.car.frameworkpackagestubs 的空壳，用户无处勾选。
     * 本应用持有 WRITE_SECURE_SETTINGS（adb 授过），可直接写入启用列表。
     * 写入时保留已有服务，不覆盖车机自带的百度语音助手等。
     */
    private void enableInstallClickService() {
        enableInstallClickService(true);
    }

    /**
     * @param arm true=同时武装（准备代点）；false=只确保服务启用（预启用用）
     */
    private void enableInstallClickService(boolean arm) {
        final String me = getPackageName() + "/" + InstallClickService.class.getName();
        try {
            android.content.ContentResolver cr = getContentResolver();
            String cur = android.provider.Settings.Secure.getString(cr,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) {
                cur = "";
            }
            boolean present = cur.contains(me);
            String merged;
            if (present) {
                merged = cur;
            } else {
                merged = cur.isEmpty() ? me : cur + ":" + me;
            }
            // 关键：应用被 force-stop / 被系统回收后，无障碍服务会掉线，
            // 而 AccessibilityManagerService 只在值真正变化时才重新评估绑定。
            // 直接重写相同值不生效（实测无效），所以先摘掉、再挂回，制造一次真实变化。
            if (present) {
                String without = removeService(cur, me);
                android.provider.Settings.Secure.putString(cr,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without);
            }
            android.provider.Settings.Secure.putString(cr,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged);
            android.provider.Settings.Secure.putString(cr,
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            android.util.Log.i("LynkMgr", present
                    ? "无障碍服务已重挂（摘除再挂回以触发重绑）"
                    : "已启用安装助手无障碍服务");
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "启用无障碍失败", t);
        }
        if (arm) {
            // 武装：服务和本 Activity 在同一进程，直接用静态标志，避免广播时序问题
            // （实测广播会在服务绑定完成之前发出，服务收不到）
            InstallClickService.trigger();
        }
    }

    /** 从冒号分隔的服务列表里去掉指定项。 */
    private static String removeService(String list, String target) {
        StringBuilder sb = new StringBuilder();
        for (String s : list.split(":")) {
            if (s == null || s.isEmpty() || s.equals(target)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(s);
        }
        return sb.toString();
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
