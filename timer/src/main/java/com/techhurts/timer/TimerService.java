package com.techhurts.timer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * Owns the running timer: ticks the widget once a second, then rings until the
 * alarm is stopped from the notification shade or the app.
 */
public class TimerService extends Service {

    private static final String TAG = "TechHurtsTimer";

    static final String ACTION_START = "com.techhurts.timer.START";
    static final String ACTION_CANCEL = "com.techhurts.timer.CANCEL";
    static final String ACTION_EXPIRED = "com.techhurts.timer.EXPIRED";
    static final String ACTION_STOP_ALARM = "com.techhurts.timer.STOP_ALARM";
    static final String EXTRA_DURATION_MS = "duration_ms";

    private static final String CHANNEL_RUNNING = "timer_running";
    private static final String CHANNEL_ALARM = "timer_alarm";
    private static final int NOTIFICATION_ID = 1;
    /** The alarm gives up on its own so it can never ring forever. */
    private static final long RING_TIMEOUT_MS = 10 * 60 * 1000L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mPlayer;
    private Vibrator mVibrator;
    private PowerManager.WakeLock mWakeLock;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (TimerState.remainingMs(TimerService.this) <= 0) {
                expire();
                return;
            }
            TimerWidgetProvider.updateAll(TimerService.this);
            notifyRunning();
            // Re-align to the next whole second so the display never skips one.
            long delay = TimerState.remainingMs(TimerService.this) % 1000;
            mHandler.postDelayed(this, delay == 0 ? 1000 : delay);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) action = ACTION_CANCEL;

        switch (action) {
            case ACTION_START:
                long duration = intent.getLongExtra(EXTRA_DURATION_MS, 0);
                if (duration <= 0) { stopEverything(); break; }
                stopAlarmSound();
                TimerState.start(this, duration);
                startForegroundCompat(buildRunningNotification(), false);
                scheduleBackstopAlarm(TimerState.endAt(this));
                TimerWidgetProvider.updateAll(this);
                mHandler.removeCallbacks(mTick);
                mHandler.post(mTick);
                break;
            case ACTION_EXPIRED:
                expire();
                break;
            case ACTION_STOP_ALARM:
            case ACTION_CANCEL:
            default:
                stopEverything();
                break;
        }
        return START_STICKY;
    }

    /** Timer reached zero: start ringing and swap in the stoppable notification. */
    private void expire() {
        if (TimerState.endAt(this) == 0) {
            // A stale backstop alarm for a timer that was already cancelled.
            stopEverything();
            return;
        }
        mHandler.removeCallbacks(mTick);
        cancelBackstopAlarm();
        TimerState.setRinging(this, true);
        TimerWidgetProvider.updateAll(this);

        acquireWakeLock();
        startForegroundCompat(buildAlarmNotification(), true);
        playAlarm();
        vibrate();

        mHandler.postDelayed(this::stopEverything, RING_TIMEOUT_MS);
    }

    private void stopEverything() {
        mHandler.removeCallbacksAndMessages(null);
        cancelBackstopAlarm();
        stopAlarmSound();
        TimerState.clear(this);
        TimerWidgetProvider.updateAll(this);
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
    }

    // --- Alarm playback -------------------------------------------------------

    private void playAlarm() {
        stopAlarmSound();
        try {
            mPlayer = new MediaPlayer();
            mPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mPlayer.setDataSource(this, TimerState.soundUri(this));
            mPlayer.setLooping(true);
            mPlayer.prepare();
            mPlayer.start();
        } catch (Exception e) {
            // A picked mp3 can disappear or lose its permission; fall back to the
            // system alarm sound rather than ringing silently.
            Log.w(TAG, "alarm sound failed: " + e);
            stopAlarmSound();
            try {
                mPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
                if (mPlayer != null) {
                    mPlayer.setLooping(true);
                    mPlayer.start();
                }
            } catch (Exception ignored) {
                Log.w(TAG, "default alarm sound failed too: " + ignored);
            }
        }
    }

    private void stopAlarmSound() {
        if (mPlayer != null) {
            try { mPlayer.stop(); } catch (IllegalStateException ignored) { }
            mPlayer.release();
            mPlayer = null;
        }
        if (mVibrator != null) mVibrator.cancel();
    }

    private void vibrate() {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;
        long[] pattern = {0, 500, 500};
        mVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    // --- Notifications --------------------------------------------------------

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel running = new NotificationChannel(
                CHANNEL_RUNNING, "Running timer", NotificationManager.IMPORTANCE_LOW);
        running.setShowBadge(false);
        // The alarm sound is played by this service, so the channel stays silent.
        NotificationChannel alarm = new NotificationChannel(
                CHANNEL_ALARM, "Timer alarm", NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null);
        alarm.enableVibration(false);
        nm.createNotificationChannel(running);
        nm.createNotificationChannel(alarm);
    }

    private Notification buildRunningNotification() {
        return baseBuilder(CHANNEL_RUNNING)
                .setContentTitle("Timer running")
                .setContentText(TimerState.format(TimerState.remainingMs(this)) + " left")
                .setOngoing(true)
                .addAction(0, "Cancel", servicePendingIntent(ACTION_CANCEL))
                .build();
    }

    private Notification buildAlarmNotification() {
        return baseBuilder(CHANNEL_ALARM)
                .setContentTitle("Time's up")
                .setContentText(TimerState.format(TimerState.totalMs(this)) + " timer finished")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(activityPendingIntent(), true)
                .addAction(0, "Stop alarm", servicePendingIntent(ACTION_STOP_ALARM))
                .build();
    }

    private Notification.Builder baseBuilder(String channel) {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channel)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_timer)
                .setContentIntent(activityPendingIntent())
                .setShowWhen(false);
    }

    private void notifyRunning() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildRunningNotification());
    }

    private void startForegroundCompat(Notification notification, boolean ringing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ringing
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent servicePendingIntent(String action) {
        Intent intent = new Intent(this, TimerService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent activityPendingIntent() {
        Intent intent = new Intent(this, TimerActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // --- Backstop alarm -------------------------------------------------------

    /**
     * The one-second ticks stop in Doze, so an alarm-clock alarm guarantees the
     * ring happens on time even if the process was frozen or killed.
     */
    private void scheduleBackstopAlarm(long triggerAtMs) {
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am == null) return;
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMs, activityPendingIntent()),
                backstopPendingIntent());
    }

    private void cancelBackstopAlarm() {
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am != null) am.cancel(backstopPendingIntent());
    }

    private PendingIntent backstopPendingIntent() {
        Intent intent = new Intent(this, TimerAlarmReceiver.class).setAction(ACTION_EXPIRED);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // --- Wake lock ------------------------------------------------------------

    private void acquireWakeLock() {
        if (mWakeLock != null) return;
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null) return;
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "techhurts:timer");
        mWakeLock.acquire(RING_TIMEOUT_MS);
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
        mWakeLock = null;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        stopAlarmSound();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
