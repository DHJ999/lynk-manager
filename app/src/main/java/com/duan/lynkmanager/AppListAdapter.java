package com.duan.lynkmanager;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网格单元：一个 logo，logo 下面依次是软件名、包名、版本/类型。
 * 不再使用横向长条行。
 *
 * 图标缓存：车机上有 126 个包，每次 getView 都 loadIcon 会持续放大
 * Drawable 实例，滚动久了容易顶到内存上限。这里按包名缓存，命中直接复用。
 */
public class AppListAdapter extends BaseAdapter {

    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int CARD = 0xFF1B2128;
    private static final int SYSTEM_TAG = 0xFFE6A23C;
    private static final int USER_TAG = 0xFF6FA8DC;

    private final Context ctx;
    private final List<AppInfo> data;
    /** 包名 → 图标，避免重复加载。 */
    private final Map<String, Drawable> iconCache = new HashMap<>();

    public AppListAdapter(Context ctx, List<AppInfo> data) {
        this.ctx = ctx;
        this.data = data;
    }

    /** 数据源换过之后调用，丢掉旧图标，避免卸载应用后仍显示旧图标。 */
    public void clearIconCache() {
        iconCache.clear();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Cell cell;
        if (convertView == null) {
            cell = new Cell();

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setBackground(box(CARD, 14));
            int ph = dp(8);
            int pv = dp(12);
            root.setPadding(ph, pv, ph, dp(10));

            cell.icon = new ImageView(ctx);
            cell.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(84), dp(84));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            root.addView(cell.icon, ilp);

            cell.label = new TextView(ctx);
            cell.label.setTextSize(16);
            cell.label.setTextColor(TEXT);
            cell.label.setTypeface(null, Typeface.BOLD);
            cell.label.setGravity(Gravity.CENTER);
            cell.label.setSingleLine(true);
            cell.label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(10);
            root.addView(cell.label, llp);

            cell.pkg = new TextView(ctx);
            cell.pkg.setTextSize(10);
            cell.pkg.setTextColor(MUTED);
            cell.pkg.setGravity(Gravity.CENTER);
            cell.pkg.setSingleLine(true);
            cell.pkg.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = dp(4);
            root.addView(cell.pkg, plp);

            cell.meta = new TextView(ctx);
            cell.meta.setTextSize(10);
            cell.meta.setGravity(Gravity.CENTER);
            cell.meta.setSingleLine(true);
            cell.meta.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = dp(2);
            root.addView(cell.meta, mlp);

            root.setTag(cell);
            convertView = root;
        } else {
            cell = (Cell) convertView.getTag();
        }

        AppInfo a = data.get(position);
        Drawable icon = a.icon;
        if (icon == null) {
            // 加载阶段失败的包，这里按需补一次；仍失败就保持空图标，不影响其它项。
            icon = iconCache.get(a.pkg);
            if (icon == null && a.pkg != null) {
                try {
                    icon = ctx.getPackageManager().getApplicationIcon(a.pkg);
                    iconCache.put(a.pkg, icon);
                } catch (Throwable ignored) {
                    icon = null;
                }
            }
        }
        cell.icon.setImageDrawable(icon);
        cell.label.setText(a.label != null && !a.label.isEmpty() ? a.label : a.pkg);
        // 包名太长会把卡片撑爆，只留最后两段（com.ecarx.cs11.launcher → cs11.launcher），
        // 完整包名在点开卡片的菜单标题里仍然能看到。
        cell.pkg.setText(shortPkg(a.pkg));

        String ver = a.versionName == null || a.versionName.isEmpty() ? "?" : a.versionName;
        // 车机系统应用的版本号常是 CS11A4_DEV_V_2511261236_e580269_N 这种内部构建号，
        // 30 多个字符会把整行顶出去，这里统一压到 12 字符以内。
        if (ver.length() > 12) {
            ver = ver.substring(0, 11) + "…";
        }
        // 类型不再写进文字：系统应用用橙色、用户应用用蓝色区分，能省出小半行宽度
        cell.meta.setText("v" + ver + " · " + fmtSize(a.size));
        cell.meta.setTextColor(a.system ? SYSTEM_TAG : USER_TAG);

        return convertView;
    }

    /** 包名压缩：去掉常见域名前缀，超过两段只保留后两段。 */
    static String shortPkg(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return "";
        }
        String p = pkg;
        if (p.startsWith("com.")) {
            p = p.substring(4);
        } else if (p.startsWith("cn.")) {
            p = p.substring(3);
        } else if (p.startsWith("org.")) {
            p = p.substring(4);
        } else if (p.startsWith("net.")) {
            p = p.substring(4);
        }
        String[] parts = p.split("\\.");
        if (parts.length <= 2) {
            return p;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    static String fmtSize(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private static class Cell {
        ImageView icon;
        TextView label;
        TextView pkg;
        TextView meta;
    }
}
