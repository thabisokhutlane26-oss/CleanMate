
package com.cleanmate.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

public class StorageGaugeView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcBounds = new RectF();

    private final long used;
    private final long total;

    private final int accent;
    private final int track;
    private final int text;
    private final int muted;

    public StorageGaugeView(
            Context context,
            long used,
            long total,
            int accent,
            int track,
            int text,
            int muted) {

        super(context);

        this.used = Math.max(0, used);
        this.total = Math.max(0, total);
        this.accent = accent;
        this.track = track;
        this.text = text;
        this.muted = muted;

        trackPaint.setColor(track);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(dp(13));

        progressPaint.setColor(accent);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(dp(13));

        percentPaint.setColor(text);
        percentPaint.setTextSize(dp(29));
        percentPaint.setTypeface(
                android.graphics.Typeface.create(
                        "sans-serif",
                        android.graphics.Typeface.BOLD
                )
        );
        percentPaint.setTextAlign(Paint.Align.CENTER);

        captionPaint.setColor(muted);
        captionPaint.setTextSize(dp(11));
        captionPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return value * getResources()
                .getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float radius = Math.min(
                getWidth(),
                getHeight()
        ) * 0.36f;

        arcBounds.set(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius
        );

        float percentage = total > 0
                ? Math.min(100f, used * 100f / total)
                : 0f;

        canvas.drawArc(
                arcBounds,
                -90,
                360,
                false,
                trackPaint
        );

        canvas.drawArc(
                arcBounds,
                -90,
                percentage * 3.6f,
                false,
                progressPaint
        );

        String percentText = String.format(
                Locale.US,
                "%.0f%%",
                percentage
        );

        Paint.FontMetrics metrics =
                percentPaint.getFontMetrics();

        float baseline = cy
                - (metrics.ascent + metrics.descent) / 2f
                - dp(5);

        canvas.drawText(
                percentText,
                cx,
                baseline,
                percentPaint
        );

        canvas.drawText(
                "USED",
                cx,
                cy + dp(23),
                captionPaint
        );
    }
}
