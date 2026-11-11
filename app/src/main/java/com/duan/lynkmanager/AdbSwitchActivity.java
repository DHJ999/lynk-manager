package com.duan.lynkmanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class AdbSwitchActivity extends BaseActivity {

    public static final String PERM = "android.permission.WRITE_SECURE_SETTINGS";
    public static final String PREFS = "settings";
    public static final String KEY_AUTO_BOOT = "auto_boot";

    private static final int BG = 0xFF101418;
    private static final int CARD = 0xFF1B2128;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int OK = 0xFF4CAF50;
    private static final int WARN = 0xFFE6A23C;
    private static final int ERR = 0xFFE05B5B;
    private static final int LINE = 0xFF2C343E;

    private TextView permValue;
    private TextView adbValue;
    private TextView deviceValue;
    private TextView logView;
    private TextView grantCode;
    private LinearLayout grantCard;
    private Button toggleBtn;
    private Switch autoSwitch;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private int pendingTarget = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refresh();
        log("就绪。包名：" + getPackageName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (grantCode != null) {
            grantCode.setText("adb shell pm grant " + getPackageName() + " " + PERM);
        }
    }

    // ---------------- 状态 ----------------

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean hasPerm() {
        return checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }

    private int readAdb() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    private void refresh() {
        boolean granted = hasPerm();
        int adb = readAdb();

        permValue.setText(granted ? "已授权" : "未授权");
        permValue.setTextColor(granted ? OK : ERR);

        adbValue.setText(adb == 1 ? "已开启" : adb == 0 ? "已关闭" : "未知（" + adb + "）");
        adbValue.setTextColor(adb == 1 ? OK : MUTED);

        deviceValue.setText(Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")"
                + (hasSu() ? " · 有 su" : " · 无 root"));
        deviceValue.setTextColor(MUTED);

        toggleBtn.setText(adb == 1 ? "关闭 ADB" : "打开 ADB");
        toggleBtn.setEnabled(granted);
        toggleBtn.setAlpha(granted ? 1f : 0.45f);

        autoSwitch.setChecked(prefs().getBoolean(KEY_AUTO_BOOT, true));
        grantCard.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private boolean hasSu() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"};
        for (String p : paths) {
            if (new File(p).exists()) {
                return true;
            }
        }
        return false;
    }

    // ---------------- 写入 ----------------

    private boolean apply(boolean on) {
        int value = on ? 1 : 0;
        if (!hasPerm()) {
            log("缺少 WRITE_SECURE_SETTINGS，请先按下方命令授权");
            return false;
        }
        try {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

            boolean ok = Settings.Global.putInt(getContentResolver(),
                    Settings.Global.ADB_ENABLED, value);
            log("写入 adb_enabled=" + value + " → " + (ok ? "提交成功" : "被 SettingsProvider 拒绝"));
            return ok;
        } catch (SecurityException e) {
            log("SecurityException：" + e.getMessage());
            return false;
        } catch (Throwable t) {
            log("写入异常：" + t);
            return false;
        }
    }

    private boolean applyViaSu(boolean on) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "settings put global adb_enabled " + (on ? "1" : "0")});
            int code = p.waitFor();
            log("su 兜底返回码：" + code);
            return code == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private void onToggle() {
        if (!hasPerm()) {
            Toast.makeText(this, "请先在下方完成一次性授权", Toast.LENGTH_LONG).show();
            return;
        }
        boolean target = readAdb() != 1;
        pendingTarget = target ? 1 : 0;

        boolean ok = apply(target);
        if (!ok && hasSu()) {
            log("尝试 su 兜底…");
            ok = applyViaSu(target);
        }

        if (!ok) {
            refresh();
            return;
        }

        toggleBtn.setEnabled(false);
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
                verify();
            }
        }, 1500);
    }

    private void verify() {
        if (pendingTarget < 0) {
            return;
        }
        int now = readAdb();
        if (now == pendingTarget) {
            log("已生效：adb_enabled=" + now);
            Toast.makeText(this, now == 1 ? "ADB 已开启" : "ADB 已关闭", Toast.LENGTH_SHORT).show();
        } else {
            log("写入后读数仍为 " + now + "（期望 " + pendingTarget
                    + "）。该车机可能把开关托管在厂商设置里，本方法不生效。");
            Toast.makeText(this, "未生效，详见日志", Toast.LENGTH_LONG).show();
        }
        pendingTarget = -1;
    }

    // ---------------- 界面 ----------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
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

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout cardBox() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, 14));
        int p = dp(18);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private void addRow(LinearLayout parent, String label, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView l = tv(label, 15, MUTED, false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(l, llp);

        value.setGravity(Gravity.END);
        row.addView(value);
        parent.addView(row);
    }

    private void addDivider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        parent.addView(v, lp);
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

        TextView title = tv("ADB 开关", 26, TEXT, true);
        root.addView(title);

        TextView sub = tv("授权一次，以后不用再进开发者模式", 14, MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(16));
        root.addView(sub);

        LinearLayout status = cardBox();
        root.addView(status);

        permValue = tv("—", 16, TEXT, true);
        adbValue = tv("—", 16, TEXT, true);
        deviceValue = tv("—", 13, MUTED, false);

        addRow(status, "WRITE_SECURE_SETTINGS", permValue);
        addDivider(status);
        addRow(status, "当前 ADB 开关", adbValue);
        addDivider(status);
        addRow(status, "设备", deviceValue);

        toggleBtn = new Button(this);
        toggleBtn.setAllCaps(false);
        toggleBtn.setTextSize(20);
        toggleBtn.setTextColor(0xFFFFFFFF);
        toggleBtn.setBackground(box(OK, 12));
        toggleBtn.setMinHeight(0);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        tlp.bottomMargin = dp(14);
        root.addView(toggleBtn, tlp);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggle();
            }
        });

        LinearLayout autoCard = cardBox();
        root.addView(autoCard);

        autoSwitch = new Switch(this);
        autoSwitch.setText("开机自动开启 ADB");
        autoSwitch.setTextSize(16);
        autoSwitch.setTextColor(TEXT);
        autoSwitch.setPadding(0, dp(4), 0, dp(4));
        autoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs().edit().putBoolean(KEY_AUTO_BOOT, checked).apply();
                log(checked ? "已开启开机自动启用" : "已关闭开机自动启用");
            }
        });
        autoCard.addView(autoSwitch);

        TextView autoHint = tv("车机重启会把 ADB 关掉。开启后，开机广播到达时自动写回。",
                13, MUTED, false);
        autoHint.setPadding(0, dp(6), 0, 0);
        autoCard.addView(autoHint);

        grantCard = cardBox();
        root.addView(grantCard);

        TextView gTitle = tv("一次性授权", 16, WARN, true);
        grantCard.addView(gTitle);

        TextView gDesc = tv("在电脑上执行下面这一条（车机需先临时开着 USB 调试）。"
                + "授权后重启、断电都不会丢失。", 13, MUTED, false);
        gDesc.setPadding(0, dp(8), 0, dp(10));
        grantCard.addView(gDesc);

        grantCode = tv("", 12, 0xFF9BE08A, false);
        grantCode.setTypeface(Typeface.MONOSPACE);
        grantCode.setTextIsSelectable(true);
        grantCode.setBackground(box(0xFF0C1014, 8));
        grantCode.setPadding(dp(12), dp(10), dp(12), dp(10));
        grantCard.addView(grantCode);

        Button copyBtn = new Button(this);
        copyBtn.setAllCaps(false);
        copyBtn.setText("复制授权命令");
        copyBtn.setTextSize(15);
        copyBtn.setTextColor(TEXT);
        copyBtn.setBackground(box(LINE, 10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        clp.topMargin = dp(10);
        grantCard.addView(copyBtn, clp);
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(grantCode.getText().toString());
            }
        });

        Button refreshBtn = new Button(this);
        refreshBtn.setAllCaps(false);
        refreshBtn.setText("刷新状态");
        refreshBtn.setTextSize(15);
        refreshBtn.setTextColor(TEXT);
        refreshBtn.setBackground(box(LINE, 10));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rlp.bottomMargin = dp(14);
        root.addView(refreshBtn, rlp);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
                log("已刷新");
            }
        });

        TextView logTitle = tv("运行日志", 14, MUTED, false);
        logTitle.setPadding(0, 0, 0, dp(6));
        root.addView(logTitle);

        logView = tv("", 12, 0xFFA8B4C0, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setBackground(box(0xFF0C1014, 10));
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(logView);

        return sv;
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("cmd", s));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void log(String msg) {
        if (logView == null) {
            return;
        }
        logView.append("· " + msg + "\n");
    }
}
