
package com.techhurts.weatherwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Locale;

public class WidgetConfigureActivity extends Activity {

    private static final String PREFS_NAME = "com.techhurts.weatherwidget.prefs";
    private static final String PREF_PREFIX_KEY = "zipcode_";

    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    EditText mEditTextZipCode;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_configure);

        mEditTextZipCode = findViewById(R.id.editTextZipCode);
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

            String zipCodeText = mEditTextZipCode.getText().toString();

            if (zipCodeText.length() != 5) {
                Toast.makeText(context, "Please enter a valid 5-digit zip code", Toast.LENGTH_SHORT).show();
                return;
            }

            saveZipCodePref(context, mAppWidgetId, zipCodeText);

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            WeatherWidgetProvider.updateAppWidget(context, appWidgetManager, mAppWidgetId);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }

    static void saveZipCodePref(Context context, int appWidgetId, String text) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString(PREF_PREFIX_KEY + appWidgetId, text);
        prefs.apply();
    }

    static String loadZipCodePref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null);
    }

    static void deleteZipCodePref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId);
        prefs.apply();
    }
}
