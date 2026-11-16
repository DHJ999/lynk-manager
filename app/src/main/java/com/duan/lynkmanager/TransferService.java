package com.duan.lynkmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 传文件服务的前台壳。
 *
 * 为什么需要它：SimpleHttpServer 只是一个普通线程，
 * 应用退到后台后车机的省电策略会杀进程，ServerSocket 连线程一起消失，
 * 手机上表现为「点上传进度条一直不动」——因为请求根本连不上。
 * 套一层前台 Service 并挂常驻通知，进程优先级升到不会被随手回收的级别。
 *
 * 真正的 HTTP 逻辑仍在 LanTransferActivity 里（需要它的 uploadDir/exportDir
 * 与 UI 回调），本 Service 只负责把进程"钉住"。
 */
public class TransferService extends Service {

    public static final String CHANNEL_ID = "lynk_transfer";
    public static final int NOTIF_ID = 0x4C4B;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        // START_STICKY：被系统杀掉后如果能重建，就尝试重建进程，
        // 让 Activity 有机会在下次打开时重新拉起服务。
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "传文件服务",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("局域网传文件时保持后台运行");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, LanTransferActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("传文件服务运行中")
                .setContentText("手机可继续上传 APK")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
