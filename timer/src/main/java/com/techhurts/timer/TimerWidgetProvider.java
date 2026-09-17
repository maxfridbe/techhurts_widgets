package com.techhurts.timer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.RemoteViews;

public class TimerWidgetProvider extends AppWidgetProvider {

    /** Square bitmap for the dial; the widget's ImageView scales it to the cell. */
    private static final int DIAL_PX = 192;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, appWidgetManager, id);
    }

    /** Redraws every placed widget; called on each tick of TimerService. */
    static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, TimerWidgetProvider.class));
        for (int id : ids) updateWidget(context, mgr, id);
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.timer_widget_layout);

        if (TimerState.isActive(context)) {
            long remaining = TimerState.remainingMs(context);
            long total = Math.max(1, TimerState.totalMs(context));
            views.setViewVisibility(R.id.widget_icon, View.GONE);
            views.setViewVisibility(R.id.widget_dial, View.VISIBLE);
            views.setImageViewBitmap(R.id.widget_dial,
                    renderDial(remaining, total, TimerState.isRinging(context)));
        } else {
            views.setViewVisibility(R.id.widget_dial, View.GONE);
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE);
        }

        Intent intent = new Intent(context, TimerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * Ring showing how much of the timer is left, with the remaining time in the
     * middle. Ringing draws a full red ring so it reads at a glance.
     */
    static Bitmap renderDial(long remainingMs, long totalMs, boolean ringing) {
        Bitmap bmp = Bitmap.createBitmap(DIAL_PX, DIAL_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        float stroke = DIAL_PX * 0.11f;
        float inset = stroke / 2f + DIAL_PX * 0.04f;
        RectF ring = new RectF(inset, inset, DIAL_PX - inset, DIAL_PX - inset);
        int accent = ringing ? Color.parseColor("#FF5252") : Color.parseColor("#FFB300");

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);

        paint.setColor(Color.parseColor("#40FFFFFF"));
        canvas.drawArc(ring, 0, 360, false, paint);

        float fraction = ringing ? 1f : Math.max(0f, Math.min(1f, remainingMs / (float) totalMs));
        paint.setColor(accent);
        canvas.drawArc(ring, -90, 360 * fraction, false, paint);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);

        String label = ringing ? "0:00" : TimerState.format(remainingMs);
        // Shrink until the label fits inside the ring.
        float size = DIAL_PX * 0.34f;
        float maxWidth = ring.width() - stroke * 2.2f;
        text.setTextSize(size);
        while (text.measureText(label) > maxWidth && size > 8) {
            size -= 1f;
            text.setTextSize(size);
        }
        Paint.FontMetrics fm = text.getFontMetrics();
        canvas.drawText(label, DIAL_PX / 2f, DIAL_PX / 2f - (fm.ascent + fm.descent) / 2f, text);

        return bmp;
    }
}
