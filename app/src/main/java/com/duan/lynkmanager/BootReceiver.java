package com.duan.lynkmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * 车机重启后 ADB 会自动关闭。开机广播到达时把开关写回去，
 * 前提是用户已在设置里勾选"开机自动开启"，且一次性授权仍然有效。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";
    private static final String ACTION_QUICKBOOT_HTC = "com.htc.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !ACTION_QUICKBOOT.equals(action)
                && !ACTION_QUICKBOOT_HTC.equals(action)) {
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(AdbSwitchActivity.PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(AdbSwitchActivity.KEY_AUTO_BOOT, true)) {
            return;
        }

        if (context.checkSelfPermission(AdbSwitchActivity.PERM) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.ADB_ENABLED, 1);
        } catch (Throwable ignored) {
            // 授权被撤销或厂商 ROM 拦截时静默失败，用户可在应用内看到状态
        }
    }
}
