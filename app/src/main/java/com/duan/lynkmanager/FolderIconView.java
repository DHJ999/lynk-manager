package com.duan.lynkmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 文件夹图标：用代码画一个文件夹形状。
 *
 * 为什么不放 PNG：项目里没有现成的文件夹图片资源，
 * 为一个小图标引入 svg/png 会增加 APK 体积和多密度维护成本。
 * 自绘一个文件夹只需几十行，且随密度自动清晰。
 *
 * 形状：经典文件夹 - 上方一个梯形标签（tab），下方一个圆角矩形主体。
 */
public class FolderIconView extends View {

    /** 文件夹主色（与徽章配色体系协调）。 */
    private static final int FOLDER_BODY = 0xFF3D7EBF;
    /** 标签部分稍浅，产生层次感。 */
    private static final int FOLDER_TAB = 0xFF5A9BD8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public FolderIconView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // 留一点内边距，让图标不贴边
        float padX = w * 0.08f;
        float padY = h * 0.14f;
        float left = padX;
        float right = w - padX;
        float top = padY;
        float bottom = h - padY;

        float radius = (bottom - top) * 0.16f;
        // 标签高度占整体的 28%
        float tabH = (bottom - top) * 0.28f;
        float tabW = (right - left) * 0.46f;

        // ---- 标签（左上角凸起）----
        path.reset();
        rect.set(left, top, left + tabW, top + tabH + radius);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        paint.setColor(FOLDER_TAB);
        canvas.drawPath(path, paint);

        // ---- 主体（圆角矩形）----
        path.reset();
        rect.set(left, top + tabH * 0.55f, right, bottom);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        paint.setColor(FOLDER_BODY);
        canvas.drawPath(path, paint);

        // ---- 主体顶部高光线，增加层次 ----
        paint.setColor(0x33FFFFFF);
        rect.set(left + radius * 0.5f, top + tabH * 0.55f + radius * 0.45f,
                right - radius * 0.5f, top + tabH * 0.55f + radius * 0.45f + dp(1.5f));
        canvas.drawRect(rect, paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
