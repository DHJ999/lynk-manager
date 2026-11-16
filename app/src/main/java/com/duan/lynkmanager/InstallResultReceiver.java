package com.duan.lynkmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * 接收 PackageInstaller 会话的提交结果。
 *
 * session.commit() 之后，系统安装器会通过这里回传状态：
 *   STATUS_PENDING_USER_ACTION → 需要用户确认，用 EXTRA_INTENT 拉起确认界面
 *   STATUS_SUCCESS             → 装好了
 *   其它                        → 失败，STATUS 里是错误码
 */
public class InstallResultReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_RESULT =
            "com.duan.lynkmanager.INSTALL_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_INSTALL_RESULT.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // 系统要求用户确认：把安装器自己的确认界面拉起来
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                // 先武装无障碍助手再拉起界面：
                // 这个 ROM 的「安装」按钮带防点击劫持，真实触摸和 input tap 都无效，
                // 只能靠无障碍 performAction 点。
                try {
                    InstallClickService.trigger();
                } catch (Throwable ignored) {
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                    android.util.Log.i("LynkMgr", "已拉起系统安装确认界面");
                } catch (Throwable t) {
                    android.util.Log.w("LynkMgr", "拉起确认界面失败", t);
                }
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            android.util.Log.i("LynkMgr", "安装成功");
            android.widget.Toast.makeText(context, "安装成功", android.widget.Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        android.util.Log.w("LynkMgr", "安装失败 status=" + status + " msg=" + msg);
        String hint;
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                hint = "安装被中止";
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                hint = "被系统拦截（可能缺授权）";
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                hint = "与已安装版本冲突（签名不一致，需先卸载）";
                break;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                hint = "与当前系统不兼容";
                break;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                hint = "APK 无效或已损坏";
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                hint = "存储空间不足";
                break;
            default:
                hint = "安装失败（" + status + "）";
                break;
        }
        android.widget.Toast.makeText(context, hint + "\n" + (msg == null ? "" : msg),
                android.widget.Toast.LENGTH_LONG).show();
    }
}
