package com.techhurts.timer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Expand screen: set the duration, choose the alarm sound, start or stop. */
public class TimerActivity extends Activity {

    private static final int REQ_PICK_SOUND = 1;
    private static final int REQ_POST_NOTIFICATIONS = 2;

    private ImageView mDial;
    private TextView mStatus;
    private TextView mSound;
    private EditText mHours, mMinutes, mSeconds;
    private View mStartRow, mRunningRow, mAlarmRow;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            render();
            mHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer);

        mDial = findViewById(R.id.dial);
        mStatus = findViewById(R.id.tv_status);
        mSound = findViewById(R.id.tv_sound);
        mHours = findViewById(R.id.et_hours);
        mMinutes = findViewById(R.id.et_minutes);
        mSeconds = findViewById(R.id.et_seconds);
        mStartRow = findViewById(R.id.row_start);
        mRunningRow = findViewById(R.id.row_running);
        mAlarmRow = findViewById(R.id.row_alarm);

        findViewById(R.id.btn_start).setOnClickListener(v -> start());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> send(TimerService.ACTION_CANCEL));
        findViewById(R.id.btn_stop_alarm).setOnClickListener(v -> send(TimerService.ACTION_STOP_ALARM));
        findViewById(R.id.btn_sound_default).setOnClickListener(v -> {
            TimerState.setSound(this, null, null);
            render();
        });
        findViewById(R.id.btn_sound_pick).setOnClickListener(v -> pickSound());

        int[] presetIds = {R.id.btn_1m, R.id.btn_5m, R.id.btn_10m, R.id.btn_30m};
        int[] presetMinutes = {1, 5, 10, 30};
        for (int i = 0; i < presetIds.length; i++) {
            final int minutes = presetMinutes[i];
            findViewById(presetIds[i]).setOnClickListener(v -> setFields(minutes * 60_000L));
        }

        setFields(TimerState.lastDurationMs(this));
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTick);
    }

    private void render() {
        boolean ringing = TimerState.isRinging(this);
        boolean active = TimerState.isActive(this);

        mAlarmRow.setVisibility(ringing ? View.VISIBLE : View.GONE);
        mRunningRow.setVisibility(active && !ringing ? View.VISIBLE : View.GONE);
        mStartRow.setVisibility(active ? View.GONE : View.VISIBLE);

        if (active) {
            long remaining = TimerState.remainingMs(this);
            long total = Math.max(1, TimerState.totalMs(this));
            mDial.setImageBitmap(TimerWidgetProvider.renderDial(remaining, total, ringing));
            mStatus.setText(ringing
                    ? "Time's up"
                    : TimerState.format(remaining) + " left");
        } else {
            long preview = fieldsToMillis();
            mDial.setImageBitmap(TimerWidgetProvider.renderDial(preview, Math.max(1, preview), false));
            mStatus.setText("Set a timer");
        }
        mSound.setText("Alarm sound: " + TimerState.soundName(this));
    }

    private void start() {
        long duration = fieldsToMillis();
        if (duration <= 0) {
            Toast.makeText(this, "Set a duration first", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, TimerService.class)
                .setAction(TimerService.ACTION_START)
                .putExtra(TimerService.EXTRA_DURATION_MS, duration);
        startService(intent);
        render();
    }

    private void send(String action) {
        startService(new Intent(this, TimerService.class).setAction(action));
        mHandler.postDelayed(this::render, 100);
    }

    // --- Duration fields ------------------------------------------------------

    private long fieldsToMillis() {
        return (value(mHours) * 3600L + value(mMinutes) * 60L + value(mSeconds)) * 1000L;
    }

    private long value(EditText field) {
        String text = field.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return 0;
        try {
            return Math.max(0, Long.parseLong(text));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setFields(long ms) {
        long total = ms / 1000;
        mHours.setText(String.valueOf(total / 3600));
        mMinutes.setText(String.valueOf((total % 3600) / 60));
        mSeconds.setText(String.valueOf(total % 60));
        render();
    }

    // --- Alarm sound ----------------------------------------------------------

    private void pickSound() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_PICK_SOUND);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_SOUND || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            // Persist so the service can still read the file after a reboot.
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "Could not keep access to that file", Toast.LENGTH_SHORT).show();
        }
        TimerState.setSound(this, uri, displayName(uri));
        render();
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) {
            // fall through to the raw uri
        }
        return uri.getLastPathSegment();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }
}
