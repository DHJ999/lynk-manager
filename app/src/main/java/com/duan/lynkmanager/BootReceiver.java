package com.duan.lynkmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * 车机重启后 ADB 会自动关闭。
 *
 * 真正决定 adbd 起不停的是厂商属性 persist.sv.debug.adb_enable，
 * 仅写 Settings.Global.ADB_ENABLED 在这台车机上无效（实测重启后依旧连不上）。
 * 本机 SELinux 为 Permissive，普通应用可以直接 setprop，
 * 所以这里开机把厂商开关写回，重启后不用再进工厂模式。
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

        // 主路径：直接写厂商开关与 USB 配置
        MainActivity.sh("setprop persist.sv.debug.adb_enable 1");
        MainActivity.sh("setprop persist.sys.usb.config adb");

        // 副路径：同步 Settings 值，让系统设置页显示一致
        try {
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.ADB_ENABLED, 1);
        } catch (Throwable ignored) {
            // 授权被撤销时静默失败，用户可在应用内看到真实状态
        }
    }
}
