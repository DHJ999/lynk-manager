package com.duan.lynkmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * 文件管理器列表：每行 = 类型徽章 + 文件名 + 大小/时间。
 *
 * 用 ListView 而不是 RecyclerView：项目里没有引入 support 库，
 * 全靠平台 API，ListView + BaseAdapter 是零依赖方案。
 */
public class FileListAdapter extends BaseAdapter {

    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF8A97A6;
    private static final int CARD = 0xFF1B2128;

    private final Context ctx;
    private final List<FileEntry> items;

    public FileListAdapter(Context c, List<FileEntry> items) {
        this.ctx = c;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public FileEntry getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
        LinearLayout row;
        if (convert instanceof LinearLayout) {
            row = (LinearLayout) convert;
        } else {
            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));

            // 图标区：同时放「文字徽章」和「文件夹图形」两个子 View，
            // 按文件类型显示其中一个（文件夹显示图形，其余显示文字徽章）。
            FrameLayout iconBox = new FrameLayout(ctx);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(52), dp(40));
            row.addView(iconBox, ilp);

            TextView badge = new TextView(ctx);
            badge.setGravity(Gravity.CENTER);
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(0xFFFFFFFF);
            badge.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            badge.setId(android.R.id.icon1);
            iconBox.addView(badge);

            // 文件夹图形：用 FolderIconView 自绘
            FolderIconView folder = new FolderIconView(ctx);
            folder.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            folder.setId(android.R.id.icon2);
            folder.setVisibility(View.GONE);
            iconBox.addView(folder);

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.leftMargin = dp(12);
            row.addView(col, clp);

            TextView name = new TextView(ctx);
            name.setTextSize(14);
            name.setTextColor(TEXT);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            name.setId(android.R.id.text1);
            col.addView(name);

            TextView meta = new TextView(ctx);
            meta.setTextSize(11);
            meta.setTextColor(MUTED);
            meta.setPadding(0, dp(3), 0, 0);
            meta.setId(android.R.id.text2);
            col.addView(meta);
        }

        FileEntry e = items.get(position);

        TextView badge = row.findViewById(android.R.id.icon1);
        FolderIconView folderIcon = row.findViewById(android.R.id.icon2);
        if (e.isDir) {
            // 文件夹：显示图形，隐藏文字徽章
            badge.setVisibility(View.GONE);
            folderIcon.setVisibility(View.VISIBLE);
        } else {
            // 文件：显示文字徽章（APK/IMG/VID...），隐藏图形
            badge.setVisibility(View.VISIBLE);
            folderIcon.setVisibility(View.GONE);
            badge.setText(e.badge());
            badge.setBackground(badgeBg(e.kind()));
        }

        TextView name = row.findViewById(android.R.id.text1);
        name.setText(e.name());

        TextView meta = row.findViewById(android.R.id.text2);
        StringBuilder sb = new StringBuilder();
        if (e.isDir) {
            sb.append("文件夹");
        } else {
            sb.append(FileEntry.sizeText(e.size));
        }
        String d = FileEntry.dateText(e.lastModified);
        if (!d.isEmpty()) {
            sb.append(" · ").append(d);
        }
        meta.setText(sb.toString());

        row.setBackground(roundBg(
                position % 2 == 0 ? 0xFF161C22 : 0xFF141A20, 8));
        return row;
    }

    /** 按文件类型给不同底色，扫一眼就能区分。 */
    private GradientDrawable badgeBg(int kind) {
        int color;
        switch (kind) {
            case FileEntry.KIND_DIR:
                color = 0xFF3D7EBF;
                break;
            case FileEntry.KIND_APK:
                color = 0xFF4CAF50;
                break;
            case FileEntry.KIND_IMAGE:
                color = 0xFF9C6ADE;
                break;
            case FileEntry.KIND_VIDEO:
                color = 0xFFE05B5B;
                break;
            case FileEntry.KIND_AUDIO:
                color = 0xFFE0A15B;
                break;
            case FileEntry.KIND_TEXT:
                color = 0xFF5BA8E0;
                break;
            case FileEntry.KIND_ARCHIVE:
                color = 0xFFBFA23D;
                break;
            default:
                color = 0xFF5C6874;
        }
        return roundBg(color, 8);
    }

    private GradientDrawable roundBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics()));
    }
}
