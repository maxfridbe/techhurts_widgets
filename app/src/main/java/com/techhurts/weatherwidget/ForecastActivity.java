package com.techhurts.weatherwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForecastActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String mForecastText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forecast);

        TextView title = findViewById(R.id.forecast_title);
        TextView content = findViewById(R.id.forecast_content);
        View loading = findViewById(R.id.forecast_loading);
        Button copyBtn = findViewById(R.id.btn_copy);
        Button closeBtn = findViewById(R.id.btn_close);

        closeBtn.setOnClickListener(v -> finish());
        copyBtn.setOnClickListener(v -> {
            if (mForecastText.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Weather Forecast", mForecastText));
            Toast.makeText(this, "Forecast copied!", Toast.LENGTH_SHORT).show();
        });

        int appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        WidgetConfigureActivity.LocationData locationData =
                WidgetConfigureActivity.loadLocationDataPref(this, appWidgetId);

        if (locationData == null) {
            title.setText("No location configured");
            loading.setVisibility(View.GONE);
            return;
        }

        String header = "📅 Weather Forecast for " + locationData.locationName;
        title.setText(header);

        executor.execute(() -> {
            List<String[]> periods = WeatherService.getAllForecastPeriods(
                    this, locationData.latitude, locationData.longitude);
            handler.post(() -> {
                loading.setVisibility(View.GONE);
                if (periods == null || periods.isEmpty()) {
                    content.setText("Failed to load forecast.");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("──────────────────────────────────────────\n");
                for (String[] period : periods) {
                    String name = period[0];
                    String temp = period[1];
                    String forecast = period[2];
                    String weatherEmoji = WeatherWidgetProvider.getWeatherEmoji(forecast, name);
                    String tempEmoji = WeatherWidgetProvider.getTempEmoji(Integer.parseInt(temp));
                    sb.append(String.format("%-20s: %s°F %s %s\n", name, temp, weatherEmoji, tempEmoji));
                }
                mForecastText = header + "\n" + sb;
                content.setText(sb.toString());
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
