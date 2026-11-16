package com.duan.lynkmanager;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * 局域网直连助手，两个方向都覆盖：
 *
 * A. 车机开热点（手机连车机）
 *    WifiManager.startLocalOnlyHotspot()，Android 8+ 起普通应用就能用。
 *    不需要联网能力，只是建一个本地热点，正好适合传文件。
 *
 * B. 车机连手机热点
 *    WifiNetworkSpecifier + ConnectivityManager.requestNetwork()，
 *    Android 10+ 允许应用主动点对点连到指定 SSID，不必进系统设置。
 *
 * 两个方向都成功之后，wlan0 / ap0 会拿到 IP，
 * LanTransferActivity.localIp() 就能取到手机可达的地址。
 */
public class WifiDirect {

    public interface Callback {
        /** 热点已开启：ssid/pass 给手机连；msg 用于界面提示 */
        void onHotspotStarted(String ssid, String pass);

        /** 已连上指定 SSID */
        void onJoined(String ssid);

        /** 失败或断开 */
        void onError(String reason);
    }

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** 车机热点句柄，必须持有，否则热点会被系统关掉。 */
    private WifiManager.LocalOnlyHotspotReservation reservation;
    /** 当前热点凭据，重复调用 startHotspot 时直接复用。 */
    private String hotSsid;
    private String hotPass;
    /** 主动连接的 WiFi 网络句柄。 */
    private ConnectivityManager.NetworkCallback joinCallback;

    public WifiDirect(Context ctx) {
        this.ctx = ctx;
    }

    private WifiManager wifi() {
        return (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    /** 是否有定位权限（Android 10+ 开热点/连热点强制要求）。 */
    public boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ---------------- A. 车机开热点 ----------------

    /**
     * 车机开一个本地热点，手机会看到形如 "AndroidShare_xxxx" 的名字。
     * 系统会随机生成密码，这里回调出去给界面显示二维码。
     */
    @SuppressWarnings("deprecation")
    public void startHotspot(final Callback cb) {
        // 已经开着就复用：重复调 startLocalOnlyHotspot 会抛 IllegalStateException
        if (reservation != null) {
            cb.onHotspotStarted(hotSsid, hotPass);
            return;
        }
        wifi().setWifiEnabled(true);
        if (!hasLocationPermission()) {
            cb.onError("缺少定位权限，无法开热点");
            return;
        }
        try {
            wifi().startLocalOnlyHotspot(
                    new WifiManager.LocalOnlyHotspotCallback() {
                        @Override
                        public void onStarted(WifiManager.LocalOnlyHotspotReservation r) {
                            reservation = r;
                            String ssid = null;
                            String pass = null;

                            // Android 11(API 30) 起用 getSoftApConfiguration()，
                            // 旧的 getWifiConfiguration() 已被废弃且字段会被抹空
                            // （实测这台车机返回 SSID=null、preSharedKey=null）。
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    android.net.wifi.SoftApConfiguration sac =
                                            r.getSoftApConfiguration();
                                    if (sac != null) {
                                        ssid = stripQuotes(sac.getSsid());
                                        try {
                                            pass = stripQuotes(sac.getPassphrase());
                                        } catch (Throwable ignored) {
                                            // 没权限读密码时保持 null
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                            }

                            // 兜底：老接口
                            if (ssid == null || ssid.isEmpty()) {
                                try {
                                    android.net.wifi.WifiConfiguration cfg =
                                            r.getWifiConfiguration();
                                    if (cfg != null) {
                                        ssid = stripQuotes(cfg.SSID);
                                        pass = stripQuotes(cfg.preSharedKey);
                                        if (pass != null && pass.contains("****")) {
                                            pass = null;
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                            }

                            final String s = (ssid == null || ssid.isEmpty())
                                    ? "AndroidShare_xxxx" : ssid;
                            final String p = pass;
                            hotSsid = s;
                            hotPass = p;
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    cb.onHotspotStarted(s, p);
                                }
                            });
                        }

                        @Override
                        public void onStopped() {
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    cb.onError("热点已停止");
                                }
                            });
                        }

                        @Override
                        public void onFailed(int reason) {
                            final String msg = reasonText(reason);
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    cb.onError("开热点失败：" + msg);
                                }
                            });
                        }
                    },
                    new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            cb.onError("开热点异常：" + t.getClass().getSimpleName());
        }
    }

    /** 去掉系统给 SSID/密码加的双引号。 */
    private String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private String reasonText(int reason) {
        switch (reason) {
            case WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL:
                return "无可用信道";
            case WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE:
                return "已有热点在运行";
            case WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED:
                return "系统禁止开热点";
            default:
                return "错误码 " + reason;
        }
    }

    public void stopHotspot() {
        if (reservation != null) {
            try {
                reservation.close();
            } catch (Throwable ignored) {
            }
            reservation = null;
        }
        hotSsid = null;
        hotPass = null;
    }

    // ---------------- B. 车机连手机热点 ----------------

    /**
     * 主动连接到指定 SSID。
     *
     * Android 10 起 WifiNetworkSpecifier 不需要系统设置界面，
     * 但连上的网络只对请求它的应用可见 —— 对本应用的 HTTP 服务足够。
     */
    public void joinNetwork(final String ssid, final String password, final Callback cb) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cb.onError("系统版本过低，请在设置里手动连接");
            return;
        }
        if (ssid == null || ssid.trim().isEmpty()) {
            cb.onError("SSID 不能为空");
            return;
        }
        if (!hasLocationPermission()) {
            cb.onError("缺少定位权限，无法连接 WiFi");
            return;
        }
        wifi().setWifiEnabled(true);

        try {
            WifiNetworkSpecifier.Builder sb = new WifiNetworkSpecifier.Builder()
                    .setSsid(ssid);
            if (password != null && !password.isEmpty()) {
                sb.setWpa2Passphrase(password);
            }
            NetworkRequest req = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(sb.build())
                    .build();

            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (joinCallback != null) {
                try {
                    cm.unregisterNetworkCallback(joinCallback);
                } catch (Throwable ignored) {
                }
            }
            joinCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onJoined(ssid);
                        }
                    });
                }

                @Override
                public void onUnavailable() {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError("连不上 " + ssid + "，检查密码或热点是否开启");
                        }
                    });
                }

                @Override
                public void onLost(Network network) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError("连接已断开：" + ssid);
                        }
                    });
                }
            };
            cm.requestNetwork(req, joinCallback);
        } catch (Throwable t) {
            cb.onError("连接异常：" + t.getClass().getSimpleName());
        }
    }

    /** 断开主动连接。 */
    public void releaseJoin() {
        if (joinCallback != null) {
            try {
                ConnectivityManager cm =
                        (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                cm.unregisterNetworkCallback(joinCallback);
            } catch (Throwable ignored) {
            }
            joinCallback = null;
        }
    }

    /** 释放全部资源。 */
    public void release() {
        stopHotspot();
        releaseJoin();
    }
}
