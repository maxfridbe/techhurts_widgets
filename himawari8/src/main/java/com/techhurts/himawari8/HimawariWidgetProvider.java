package com.techhurts.himawari8;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HimawariWidgetProvider extends AppWidgetProvider {

    private static final int WIDGET_BMP_SIZE = 400;
    private static final float CORNER_RADIUS_PX = 20f;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
        HimawariFetchReceiver.schedule(ctx);
        HimawariFetchReceiver.triggerFetch(ctx);
    }

    @Override public void onEnabled(Context ctx) {
        HimawariFetchReceiver.schedule(ctx);
        HimawariFetchReceiver.triggerFetch(ctx);
        requestBatteryOptExemption(ctx);
    }
    @Override public void onDisabled(Context ctx) { HimawariFetchReceiver.cancel(ctx); }

    static void updateAllWidgets(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, HimawariWidgetProvider.class));
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.himawari_widget_layout);
        File latest = ImageStore.getLatest(ctx);
        if (latest != null) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap raw = BitmapFactory.decodeFile(latest.getPath(), opts);
            if (raw != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(raw, WIDGET_BMP_SIZE, WIDGET_BMP_SIZE, true);
                raw.recycle();
                Bitmap rounded = roundCorners(scaled, CORNER_RADIUS_PX);
                scaled.recycle();
                views.setImageViewBitmap(R.id.widget_image, rounded);
                views.setViewVisibility(R.id.widget_loading, View.GONE);
                String ts = new SimpleDateFormat("HH:mm", Locale.US)
                        .format(new Date(latest.lastModified()));
                views.setTextViewText(R.id.widget_timestamp, ts);
                views.setViewVisibility(R.id.widget_timestamp, View.VISIBLE);
            }
        } else {
            views.setViewVisibility(R.id.widget_loading, View.VISIBLE);
            views.setViewVisibility(R.id.widget_timestamp, View.GONE);
        }
        Intent intent = new Intent(ctx, HimawariAnimationActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(ctx, widgetId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        mgr.updateAppWidget(widgetId, views);
    }

    private static Bitmap roundCorners(Bitmap src, float r) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), r, r, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private static void requestBatteryOptExemption(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) return;
        try {
            ctx.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + ctx.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { Log.w("Himawari8", "Battery opt request failed", e); }
    }
}
