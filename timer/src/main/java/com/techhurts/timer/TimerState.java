package com.techhurts.timer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.TextUtils;

/**
 * One timer shared by every widget instance and the activity, kept in prefs so
 * the widget can render after the process is killed.
 */
final class TimerState {

    private static final String PREFS = "com.techhurts.timer.prefs";
    private static final String KEY_END_AT = "end_at";
    private static final String KEY_TOTAL = "total_ms";
    private static final String KEY_RINGING = "ringing";
    private static final String KEY_SOUND = "sound_uri";
    private static final String KEY_SOUND_NAME = "sound_name";
    private static final String KEY_LAST_DURATION = "last_duration_ms";

    private TimerState() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    static void start(Context c, long durationMs) {
        prefs(c).edit()
                .putLong(KEY_END_AT, System.currentTimeMillis() + durationMs)
                .putLong(KEY_TOTAL, durationMs)
                .putLong(KEY_LAST_DURATION, durationMs)
                .putBoolean(KEY_RINGING, false)
                .apply();
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY_END_AT).remove(KEY_TOTAL).putBoolean(KEY_RINGING, false).apply();
    }

    static void setRinging(Context c, boolean ringing) {
        prefs(c).edit().putBoolean(KEY_RINGING, ringing).apply();
    }

    static boolean isRinging(Context c) {
        return prefs(c).getBoolean(KEY_RINGING, false);
    }

    /** Counting down right now (not yet expired). */
    static boolean isRunning(Context c) {
        return !isRinging(c) && prefs(c).getLong(KEY_END_AT, 0) > System.currentTimeMillis();
    }

    /** Set, i.e. counting down or ringing. */
    static boolean isActive(Context c) {
        return isRinging(c) || isRunning(c);
    }

    static long endAt(Context c) {
        return prefs(c).getLong(KEY_END_AT, 0);
    }

    static long totalMs(Context c) {
        return prefs(c).getLong(KEY_TOTAL, 0);
    }

    static long remainingMs(Context c) {
        return Math.max(0, endAt(c) - System.currentTimeMillis());
    }

    static long lastDurationMs(Context c) {
        return prefs(c).getLong(KEY_LAST_DURATION, 5 * 60 * 1000L);
    }

    /** The chosen mp3, or the system alarm sound when nothing is picked. */
    static Uri soundUri(Context c) {
        String stored = prefs(c).getString(KEY_SOUND, null);
        if (!TextUtils.isEmpty(stored)) return Uri.parse(stored);
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return alarm != null ? alarm : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    static String soundName(Context c) {
        return prefs(c).getString(KEY_SOUND_NAME, "Default alarm");
    }

    static void setSound(Context c, Uri uri, String name) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (uri == null) {
            e.remove(KEY_SOUND).remove(KEY_SOUND_NAME);
        } else {
            e.putString(KEY_SOUND, uri.toString()).putString(KEY_SOUND_NAME, name);
        }
        e.apply();
    }

    /** "5:00", or "1:05:00" once an hour is on the clock. */
    static String format(long ms) {
        long total = (ms + 999) / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
