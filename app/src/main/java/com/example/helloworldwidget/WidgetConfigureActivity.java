
package com.example.helloworldwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.DatePicker; // Import DatePicker
// Removed EditText import
import android.widget.Toast;

import java.util.Locale; // Import Locale for formatting

public class WidgetConfigureActivity extends Activity {

    private static final String PREFS_NAME = "com.example.helloworldwidget.WidgetPrefs";
    private static final String PREF_PREFIX_KEY = "duedate_";

    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    DatePicker mDatePickerDueDate; // Changed from EditText

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_configure);

        // Find the DatePicker instead of EditText
        mDatePickerDueDate = findViewById(R.id.datePickerDueDate);
        Button confirmButton = findViewById(R.id.buttonConfirm);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        confirmButton.setOnClickListener(v -> {
            final Context context = WidgetConfigureActivity.this;

            // --- Get date from DatePicker ---
            int year = mDatePickerDueDate.getYear();
            int month = mDatePickerDueDate.getMonth(); // Month is 0-indexed (0=Jan, 1=Feb, ...)
            int day = mDatePickerDueDate.getDayOfMonth();

            // Format the date string as YYYY-MM-DD
            // Remember to add 1 to the month for correct formatting
            String dueDateText = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);

            // Removed validation for EditText, DatePicker provides valid components

            // Save the formatted date string
            saveDueDatePref(context, mAppWidgetId, dueDateText);

            // Update the widget
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            HelloWorldWidgetProvider.updateAppWidget(context, appWidgetManager, mAppWidgetId, dueDateText);

            // Set result and finish
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }

    // Write the prefix to the SharedPreferences object for this widget
    static void saveDueDatePref(Context context, int appWidgetId, String text) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString(PREF_PREFIX_KEY + appWidgetId, text);
        prefs.apply();
    }

    // Read the prefix from the SharedPreferences object for this widget.
    static String loadDueDatePref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null); // Returns null if not found
    }

    // Delete the preference for this widget
    static void deleteDueDatePref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId);
        prefs.apply();
    }
}
