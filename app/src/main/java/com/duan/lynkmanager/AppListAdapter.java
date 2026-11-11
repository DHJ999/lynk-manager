package com.duan.lynkmanager;

import android.content.Context;
import android.graphics.Typeface;
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

import java.util.List;

/**
 * 网格单元：一个 logo，logo 下面依次是软件名、包名、版本/类型。
 * 不再使用横向长条行。
 */
public class AppListAdapter extends BaseAdapter {

    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int CARD = 0xFF1B2128;
    private static final int SYSTEM_TAG = 0xFFE6A23C;
    private static final int USER_TAG = 0xFF6FA8DC;

    private final Context ctx;
    private final List<AppInfo> data;

    public AppListAdapter(Context ctx, List<AppInfo> data) {
        this.ctx = ctx;
        this.data = data;
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
            cell.pkg.setTextSize(11);
            cell.pkg.setTextColor(MUTED);
            cell.pkg.setGravity(Gravity.CENTER);
            cell.pkg.setMaxLines(2);
            cell.pkg.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = dp(4);
            root.addView(cell.pkg, plp);

            cell.meta = new TextView(ctx);
            cell.meta.setTextSize(10);
            cell.meta.setGravity(Gravity.CENTER);
            cell.meta.setSingleLine(true);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = dp(4);
            root.addView(cell.meta, mlp);

            root.setTag(cell);
            convertView = root;
        } else {
            cell = (Cell) convertView.getTag();
        }

        AppInfo a = data.get(position);
        cell.icon.setImageDrawable(a.icon);
        cell.label.setText(a.label != null && !a.label.isEmpty() ? a.label : a.pkg);
        cell.pkg.setText(a.pkg);

        String ver = a.versionName == null || a.versionName.isEmpty() ? "?" : a.versionName;
        cell.meta.setText("v" + ver + " · " + fmtSize(a.size)
                + (a.system ? " · 系统" : ""));
        cell.meta.setTextColor(a.system ? SYSTEM_TAG : USER_TAG);

        return convertView;
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
