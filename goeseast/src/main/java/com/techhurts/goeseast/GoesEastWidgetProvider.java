package com.techhurts.goeseast;

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

public class GoesEastWidgetProvider extends AppWidgetProvider {

    // 400×400 ARGB_8888 = 640 KB — safely within the ~1 MB Binder IPC limit
    private static final int WIDGET_BMP_SIZE = 400;
    // Corner radius in the 400 px bitmap space (≈ 12 dp equivalent at typical density)
    private static final float CORNER_RADIUS_PX = 20f;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
        GoesEastFetchReceiver.schedule(ctx);
        GoesEastFetchReceiver.triggerFetch(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        GoesEastFetchReceiver.schedule(ctx);
        GoesEastFetchReceiver.triggerFetch(ctx);
        requestBatteryOptExemption(ctx);
    }

    @Override
    public void onDisabled(Context ctx) { GoesEastFetchReceiver.cancel(ctx); }

    static void updateAllWidgets(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, GoesEastWidgetProvider.class));
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.goeseast_widget_layout);

        File latest = ImageStore.getLatest(ctx);
        if (latest != null) {
            // Load at half-res (stored frame is ~1085 px), then scale to widget size
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap raw = BitmapFactory.decodeFile(latest.getPath(), opts);
            if (raw != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(raw, WIDGET_BMP_SIZE, WIDGET_BMP_SIZE, true);
                raw.recycle();
                // Apply rounded corners directly to the bitmap (transparent corners).
                // On a transparent widget background the wallpaper shows through the corners.
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

        Intent intent = new Intent(ctx, GoesEastAnimationActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        mgr.updateAppWidget(widgetId, views);
    }

    // ── Bitmap helpers ────────────────────────────────────────────────────────

    private static Bitmap roundCorners(Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), radius, radius, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    // ── Battery optimisation ──────────────────────────────────────────────────

    private static void requestBatteryOptExemption(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + ctx.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Log.w("GoesEast", "Battery opt exemption request failed", e);
        }
    }
}
