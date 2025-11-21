package com.techhurts.weatherwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "WeatherWidget";
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final String ACTION_TOGGLE_LOG_VISIBILITY = "com.techhurts.weatherwidget.TOGGLE_LOG_VISIBILITY";
    private static final String PREFS_NAME = "com.techhurts.weatherwidget.prefs";
    private static final String PREF_LOG_VISIBLE_PREFIX = "log_visible_";

//    @Override
//    public void onReceive(Context context, Intent intent) {
//        final String action = intent.getAction();
//        if (ACTION_TOGGLE_LOG_VISIBILITY.equals(action)) {
//            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
//            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
//                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
//                boolean isLogVisible = prefs.getBoolean(PREF_LOG_VISIBLE_PREFIX + appWidgetId, false);
//                SharedPreferences.Editor editor = prefs.edit();
//                editor.putBoolean(PREF_LOG_VISIBLE_PREFIX + appWidgetId, !isLogVisible);
//                editor.apply();
//
//                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
//                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_layout);
//                views.setViewVisibility(R.id.log_scroll_view, !isLogVisible ? View.VISIBLE : View.GONE);
//                
//                // We also need to re-set the on-click handler every time we update the view
//                Intent toggleIntent = new Intent(context, WeatherWidgetProvider.class);
//                toggleIntent.setAction(ACTION_TOGGLE_LOG_VISIBILITY);
//                toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
//                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
//                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
//                
//                // It's important to update the widget fully, not just the visibility.
//                // We need to re-fetch the weather data and update all fields.
//                // A simpler approach for the toggle is to just update the visibility
//                // and assume the rest of the content is up-to-date.
//                // Let's just update the widget with the modified views.
//                appWidgetManager.updateAppWidget(appWidgetId, views);
//            }
//        } else {
//            super.onReceive(context, intent);
//        }
//    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        WidgetLogger.clearLogs();
        WidgetLogger.log("updateAppWidget started for widget ID: " + appWidgetId);

        executorService.execute(() -> {
            try {
                String zipCode = WidgetConfigureActivity.loadZipCodePref(context, appWidgetId);
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_layout);

                // Get the accent color from the theme
                int accentColor;
                TypedValue typedValue = new TypedValue();
                if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
                    accentColor = typedValue.data;
                } else {
                    // Fallback color if colorAccent is not found or for older Android versions
                    accentColor = context.getResources().getColor(android.R.color.holo_blue_light, context.getTheme());
                }
                // Apply the desired transparency (#44)
                int transparentAccentColor = (accentColor & 0x00FFFFFF) | (0x44 << 24); // Keep original RGB, set alpha to #44
                views.setInt(R.id.widget_root, "setBackgroundColor", transparentAccentColor);
                
                // Set OnClickListener to open weather.gov with the specific zip code
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://forecast.weather.gov/zipcity.php?inputstring=" + zipCode));
                PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
                
                if (!isNetworkAvailable(context)) {
                    WidgetLogger.log("No network connection available.");
                    handler.post(() -> {
                        views.setTextViewText(R.id.widget_location, "N/A");
                        views.setTextViewText(R.id.widget_temperature, "0°F");
                        views.setTextViewText(R.id.widget_forecast, "No Network");
                        views.setTextViewText(R.id.widget_temp_emoji, "❓");
                        views.setTextViewText(R.id.widget_weather_emoji, "❓");
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                    });
                    return;
                }

                WidgetLogger.log("Fetching weather for " + zipCode);
                String[] weatherData = WeatherService.getWeatherData(context, zipCode);
                WidgetLogger.log("WeatherService returned: " + (weatherData != null ? java.util.Arrays.toString(weatherData) : "null"));

                handler.post(() -> {
                    if (weatherData != null && weatherData.length == 3) {
                        String name = weatherData[0];
                        String temperatureStr = weatherData[1];
                        String shortForecast = weatherData[2];

                        WidgetLogger.log("Updating widget UI with weather data.");
                        views.setTextViewText(R.id.widget_location, zipCode);
                        views.setTextViewText(R.id.widget_forecast, shortForecast);

                        try {
                            int temp = Integer.parseInt(temperatureStr);
                            views.setTextViewText(R.id.widget_temperature, temp + "°F");
                            views.setTextViewText(R.id.widget_temp_emoji, getTempEmoji(temp));
                        } catch (NumberFormatException nfe) {
                            WidgetLogger.log("Error parsing temperature.");
                            views.setTextViewText(R.id.widget_temperature, "0°F");
                            views.setTextViewText(R.id.widget_temp_emoji, "❓");
                        }
                        views.setTextViewText(R.id.widget_weather_emoji, getWeatherEmoji(shortForecast, name));
                    } else {
                        WidgetLogger.log("Weather data is null or invalid.");
                        views.setTextViewText(R.id.widget_location, "N/A");
                        views.setTextViewText(R.id.widget_temperature, "0°F");
                        views.setTextViewText(R.id.widget_forecast, "Update Failed");
                        views.setTextViewText(R.id.widget_temp_emoji, "❓");
                        views.setTextViewText(R.id.widget_weather_emoji, "❓");
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });
            } catch (Exception e) {
                WidgetLogger.log("Unhandled exception in background thread: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                for (StackTraceElement ste : e.getStackTrace()) {
                    WidgetLogger.log("    " + ste.toString());
                }
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_layout);
                views.setTextViewText(R.id.widget_forecast, "Update Failed (Error)");
                appWidgetManager.updateAppWidget(appWidgetId, views);
            }
        });
    }
    
//    private static void updateLogs(Context context, AppWidgetManager appWidgetManager, int appWidgetId, RemoteViews views) {
//        StringBuilder logText = new StringBuilder();
//        for (String log : WidgetLogger.getLogs()) {
//            logText.append(log).append("\n");
//        }
//        views.setTextViewText(R.id.widget_log_viewer, logText.toString());
//        appWidgetManager.updateAppWidget(appWidgetId, views);
//        WidgetLogger.log("Final widget update complete for widget ID: " + appWidgetId);
//    }
    
    private static String getTempEmoji(int temp) {
        if (temp > 95) return "🔥";
        if (temp > 80) return "😎";
        if (temp > 65) return "😊";
        if (temp > 50) return "😌";
        if (temp > 32) return "🧥";
        return "🥶";
    }

    private static String getWeatherEmoji(String forecast, String name) {
        String lowerCaseForecast = forecast.toLowerCase();
        if (lowerCaseForecast.contains("thunderstorm")) return "⛈️";
        if (lowerCaseForecast.contains("snow")) return "❄️";
        if (lowerCaseForecast.contains("rain")) return "🌧️";
        if (lowerCaseForecast.contains("drizzle")) return "💧";
        if (lowerCaseForecast.contains("fog")) return "🌫️";
        if (lowerCaseForecast.contains("cloudy")) return "☁️";
        if (lowerCaseForecast.contains("partly")) return "🌥️";
        if (lowerCaseForecast.contains("few")) return "🌤️";
        if (lowerCaseForecast.contains("sunny")) return "☀️";
        if (lowerCaseForecast.contains("clear")) {
            return name.toLowerCase().contains("night") ? "🌙" : "☀️";
        }
        return "";
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 0).edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove(PREF_LOG_VISIBLE_PREFIX + appWidgetId);
            WidgetConfigureActivity.deleteZipCodePref(context, appWidgetId);
        }
        editor.apply();
    }
}

