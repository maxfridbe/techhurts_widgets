package com.techhurts.goeseast;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public class GoesEastFetchReceiver extends BroadcastReceiver {

    static final String ACTION_FETCH = "com.techhurts.goeseast.ACTION_FETCH";
    private static final String TAG = "GoesEast";
    private static final long INTERVAL_MS = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot — rescheduling");
            schedule(context);
            return;
        }
        if (!ACTION_FETCH.equals(intent.getAction())) return;

        // Schedule NEXT alarm FIRST — chain survives even if this fetch dies mid-flight
        schedule(context);

        Log.i(TAG, "ACTION_FETCH received");
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                runFetch(context);
            } catch (Throwable t) {
                Log.e(TAG, "Fetch failed: " + t, t);
            } finally {
                result.finish();
            }
        }).start();
    }

    /**
     * Trigger an immediate fetch by broadcasting ACTION_FETCH to ourselves.
     * This goes through onReceive/goAsync so the system holds a wake lock
     * for the entire download — more reliable than a raw background thread.
     */
    static void triggerFetch(Context ctx) {
        Log.i(TAG, "Broadcasting immediate fetch");
        ctx.sendBroadcast(new Intent(ACTION_FETCH)
                .setClass(ctx, GoesEastFetchReceiver.class));
    }

    private static void runFetch(Context ctx) throws Exception {
        String url = GoesEastService.fetchLatestImageUrl();
        Log.i(TAG, "Downloading: " + url);
        Bitmap full = GoesEastService.downloadFull(url);
        Log.i(TAG, "Full disk decoded: " + full.getWidth() + "×" + full.getHeight());
        ImageStore.savePreview(ctx, full); // small thumbnail for crop-config preview UI
        long ts = System.currentTimeMillis();
        Bitmap processed = CropConfig.cropAndBurn(full, ctx, ts);
        Log.i(TAG, "Cropped+burned: " + processed.getWidth() + "×" + processed.getHeight());
        ImageStore.save(ctx, processed);
        processed.recycle();
        GoesEastWidgetProvider.updateAllWidgets(ctx);
        Log.i(TAG, "Widget updated");
    }

    /**
     * Schedule a one-shot exact wakeup alarm.
     *
     * setExactAndAllowWhileIdle fires in Doze maintenance windows (not deferred
     * indefinitely like setRepeating).  We reschedule from runFetch so the chain
     * continues overnight without the alarm drifting or being batched away.
     */
    static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long trigger = SystemClock.elapsedRealtime() + INTERVAL_MS;
        PendingIntent pi = buildIntent(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: use exact only if the user has granted the permission;
            // otherwise fall back to the inexact-but-Doze-aware variant.
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        }
        Log.i(TAG, "Alarm set for +" + (INTERVAL_MS / 60_000) + " min");
    }

    static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(buildIntent(context));
    }

    private static PendingIntent buildIntent(Context context) {
        Intent i = new Intent(ACTION_FETCH).setClass(context, GoesEastFetchReceiver.class);
        return PendingIntent.getBroadcast(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
