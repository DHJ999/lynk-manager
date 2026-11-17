package com.duan.lynkmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 首次启动的用户协议页。
 *
 * 只显示一次：同意后写 SharedPreferences 标记，之后不再出现。
 * 不同意则退出应用（不能绕过，否则协议形同虚设）。
 */
public class AgreementActivity extends BaseActivity {

    public static final String PREFS = "lynk_agreement";
    public static final String KEY_AGREED = "agreed";

    private static final int BG = 0xFF0E1318;
    private static final int CARD = 0xFF1B2128;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int OK = 0xFF4CAF50;

    /** 判断是否已同意过。 */
    public static boolean hasAgreed(Context c) {
        if (c == null) {
            return false;
        }
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_AGREED, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 已同意过就直接进主界面，不再显示协议页
        if (hasAgreed(this)) {
            startActivity(new android.content.Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // 标题
        TextView title = new TextView(this);
        title.setText("用户协议与免责声明");
        title.setTextSize(24);
        title.setTextColor(TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(20), dp(20), dp(20), dp(10));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("请完整阅读后选择是否同意");
        sub.setTextSize(13);
        sub.setTextColor(MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(12));
        root.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 协议正文（可滚动）
        ScrollView sv = new ScrollView(this);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView body = new TextView(this);
        body.setText(AGREEMENT);
        body.setTextSize(14);
        body.setTextColor(0xFFC8D2DC);
        body.setLineSpacing(dp(6), 1f);
        body.setBackground(box(CARD, 12));
        int p = dp(18);
        body.setPadding(p, p, p, p);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(16);
        blp.rightMargin = dp(16);
        blp.bottomMargin = dp(12);
        sv.addView(body, blp);

        // 按钮区
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(dp(16), dp(8), dp(16), dp(20));
        root.addView(btnRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button disagree = new Button(this);
        disagree.setAllCaps(false);
        disagree.setText("不同意并退出");
        disagree.setTextSize(15);
        disagree.setTextColor(TEXT);
        disagree.setBackground(box(0xFF3A2228, 12));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        dlp.rightMargin = dp(12);
        btnRow.addView(disagree, dlp);
        disagree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 不同意就退出整个应用
                finishAffinity();
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        Button agree = new Button(this);
        agree.setAllCaps(false);
        agree.setText("同意并继续");
        agree.setTextSize(15);
        agree.setTextColor(0xFFFFFFFF);
        agree.setBackground(box(OK, 12));
        btnRow.addView(agree, new LinearLayout.LayoutParams(0, dp(56), 1f));
        agree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_AGREED, true).apply();
                // 直接进主界面
                startActivity(new android.content.Intent(
                        AgreementActivity.this, MainActivity.class));
                finish();
            }
        });

        return root;
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

    /** 协议正文。 */
    private static final String AGREEMENT =
            "一、本工具性质\n\n"
                    + "Lynk 管家是个人自用的技术学习产物，非官方、非商业、未授权、"
                    + "未经过任何形式的安全审计与兼容性测试。"
                    + "与吉利汽车、领克汽车、亿咖通（ECARX）、德赛西威（DesaySV）"
                    + "及任何车机厂商、经销商、服务商均无关联，"
                    + "不代表其立场，也未获得其认可或授权。\n\n"

                    + "二、它会做什么\n\n"
                    + "本工具会直接修改车机的系统状态，包括但不限于：\n"
                    + "· 写入系统属性（persist.sv.debug.adb_enable、sys.usb.config 等），"
                    + "开启或关闭 ADB 调试通道\n"
                    + "· 调用 PackageManager 卸载应用、通过 PackageInstaller 安装任意 APK\n"
                    + "· 通过 killBackgroundProcesses 强制停止进程\n"
                    + "· 开启本地热点、启动局域网 HTTP 服务、"
                    + "在系统无障碍服务列表中注册自身\n"
                    + "· 读写车机存储中的文件\n\n"
                    + "这些操作超出普通应用的权限边界，属于系统级改动。\n\n"

                    + "三、可能造成的后果\n\n"
                    + "以下风险真实存在，且部分不可逆：\n\n"
                    + "1. 系统功能异常：卸载系统应用可能导致车机功能缺失"
                    + "（车控、语音、导航等），可能需要刷机恢复。\n\n"
                    + "2. 数据丢失：卸载应用会连带清除其数据，且无法找回。\n\n"
                    + "3. ADB 连接中断：开关 ADB 会触发 USB 重新枚举，"
                    + "电脑端掉线约 1 分钟，运行中的调试会话会中断。\n\n"
                    + "4. 车机变砖：极端情况下错误操作可能导致系统无法启动，"
                    + "需到 4S 店或专业维修恢复，费用自负。\n\n"
                    + "5. 保修失效：自行修改系统状态可能被厂商认定为人为损坏，"
                    + "导致保修条款失效。\n\n"
                    + "6. 安全隐患：开启 ADB 后，任何物理接触车机 USB 接口的设备"
                    + "都可获得调试权限；局域网传文件为明文 HTTP，同网络下可被嗅探。\n\n"
                    + "7. 法律风险：在非本人所有或未获授权的车辆上使用，"
                    + "可能违反相关法律法规或车辆使用协议。\n\n"

                    + "四、责任划分\n\n"
                    + "你需自行承担使用本工具的全部风险与后果。\n\n"
                    + "作者不对因使用或无法使用本工具而产生的任何直接、间接、偶然、"
                    + "特殊或后果性损失负责，包括但不限于：车辆损坏、数据丢失、"
                    + "业务中断、保修失效、维修费用、法律纠纷、第三方索赔、利润损失等。"
                    + "即使作者已被告知此类损失的可能性，亦不承担责任。\n\n"

                    + "五、使用前提\n\n"
                    + "使用本工具即表示你确认：\n"
                    + "· 你是该车辆的所有权人或已获得明确授权的使用者\n"
                    + "· 你理解相关操作的原理与风险，具备相应的技术判断能力\n"
                    + "· 你接受修改系统状态可能带来的全部后果\n"
                    + "· 你不会将本工具用于任何商业用途或非法目的\n\n"

                    + "六、隐私说明\n\n"
                    + "本工具不收集、不上传任何个人信息。"
                    + "局域网传文件功能仅在同一 WiFi 内点对点传输，数据不经过任何服务器。"
                    + "所有文件存储在你的车机本地。\n\n"

                    + "若不同意上述任何一条，请点击「不同意并退出」。";
}
