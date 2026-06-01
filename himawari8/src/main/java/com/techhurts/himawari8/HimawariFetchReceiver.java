package com.techhurts.himawari8;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public class HimawariFetchReceiver extends BroadcastReceiver {

    static final String ACTION_FETCH = "com.techhurts.himawari8.ACTION_FETCH";
    private static final String TAG = "Himawari8";
    private static final long INTERVAL_MS = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot — scheduling");
            schedule(context);
            return;
        }
        if (!ACTION_FETCH.equals(intent.getAction())) return;

        // Schedule NEXT alarm FIRST — ensures the chain survives even if this fetch dies
        schedule(context);

        Log.i(TAG, "Fetch triggered");
        final PendingResult result = goAsync();
        new Thread(() -> {
            try { runFetch(context); }
            catch (Throwable t) { Log.e(TAG, "Fetch failed: " + t, t); }
            finally { result.finish(); }
        }).start();
    }

    /**
     * Trigger an immediate fetch by broadcasting ACTION_FETCH to ourselves.
     * Goes through onReceive/goAsync so the system holds a wake lock for the download.
     */
    static void triggerFetch(Context ctx) {
        Log.i(TAG, "Broadcasting immediate fetch");
        ctx.sendBroadcast(new Intent(ACTION_FETCH)
                .setClass(ctx, HimawariFetchReceiver.class));
    }

    private static void runFetch(Context ctx) throws Exception {
        String url = HimawariService.fetchLatestImageUrl();
        Log.i(TAG, "Downloading: " + url);
        Bitmap full = HimawariService.downloadFull(url);
        Log.i(TAG, "Full disk decoded: " + full.getWidth() + "×" + full.getHeight());
        ImageStore.savePreview(ctx, full);
        long ts = System.currentTimeMillis();
        Bitmap processed = CropConfig.cropAndBurn(full, ctx, ts);
        Log.i(TAG, "Cropped+burned: " + processed.getWidth() + "×" + processed.getHeight());
        ImageStore.save(ctx, processed);
        processed.recycle();
        HimawariWidgetProvider.updateAllWidgets(ctx);
        Log.i(TAG, "Widget updated");
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long trigger = SystemClock.elapsedRealtime() + INTERVAL_MS;
        PendingIntent pi = buildIntent(ctx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            else
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        }
        Log.i(TAG, "Next alarm in " + (INTERVAL_MS / 60_000) + " min");
    }

    static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(buildIntent(ctx));
    }

    private static PendingIntent buildIntent(Context ctx) {
        return PendingIntent.getBroadcast(ctx, 0,
                new Intent(ACTION_FETCH).setClass(ctx, HimawariFetchReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
