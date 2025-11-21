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
    private static final String ACTION_REFRESH = "com.techhurts.weatherwidget.ACTION_REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        final String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLogger.log("Received refresh action for widget ID: " + appWidgetId);
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

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
            WidgetConfigureActivity.LocationData locationData = WidgetConfigureActivity.loadLocationDataPref(context, appWidgetId);
            final String locationName = (locationData != null) ? locationData.locationName : null;
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_layout);

            // Set up the refresh button click listener
            Intent refreshIntent = new Intent(context, WeatherWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent);

            // Setup intent to launch configuration activity if location data is not set
            if (locationData == null || locationName == null || locationName.isEmpty()) {
                WidgetLogger.log("Location data not configured for widget ID: " + appWidgetId + ". Launching configuration.");
                Intent configIntent = new Intent(context, WidgetConfigureActivity.class);
                configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                PendingIntent configPendingIntent = PendingIntent.getActivity(context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setTextViewText(R.id.widget_location, "Tap to Configure");
                views.setTextViewText(R.id.widget_temperature, "");
                views.setTextViewText(R.id.widget_forecast, "");
                views.setTextViewText(R.id.widget_temp_emoji, "⚙️");
                views.setTextViewText(R.id.widget_weather_emoji, "");
                views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent);
                appWidgetManager.updateAppWidget(appWidgetId, views);
                return; // Exit early as no location data is set
            }

            // Set OnClickListener to open weather.gov with the specific location name when temperature is clicked
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://forecast.weather.gov/zipcity.php?inputstring=" + Uri.encode(locationName)));
            PendingIntent browserPendingIntent = PendingIntent.getActivity(context, appWidgetId, browserIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_temperature, browserPendingIntent);
            
            if (!isNetworkAvailable(context)) {
                WidgetLogger.log("No network connection available.");
                handler.post(() -> {
                    views.setTextViewText(R.id.widget_location, locationName);
                    views.setTextViewText(R.id.widget_temperature, "");
                    views.setTextViewText(R.id.widget_forecast, "No Network");
                    views.setTextViewText(R.id.widget_temp_emoji, "❌");
                    views.setTextViewText(R.id.widget_weather_emoji, "🌐");
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });
                return; // Exit early as no network is available
            }

            try {
                WidgetLogger.log("Fetching weather for " + locationName);
                String[] weatherData = WeatherService.getWeatherData(context, locationData.latitude, locationData.longitude);
                WidgetLogger.log("WeatherService returned: " + (weatherData != null ? java.util.Arrays.toString(weatherData) : "null"));

                handler.post(() -> {
                    if (weatherData != null && weatherData.length == 3) {
                        String name = weatherData[0];
                        String temperatureStr = weatherData[1];
                        String shortForecast = weatherData[2];

                        WidgetLogger.log("Updating widget UI with weather data.");
                        views.setTextViewText(R.id.widget_location, locationName);
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
                        views.setTextViewText(R.id.widget_location, locationName);
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
                handler.post(() -> {
                    views.setTextViewText(R.id.widget_location, locationName);
                    views.setTextViewText(R.id.widget_temperature, "");
                    views.setTextViewText(R.id.widget_forecast, "Update Failed (Error)");
                    views.setTextViewText(R.id.widget_temp_emoji, "❓");
                    views.setTextViewText(R.id.widget_weather_emoji, "❓");
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });
            }
        });
    }
    
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
            WidgetConfigureActivity.deleteLocationDataPref(context, appWidgetId);
        }
        editor.apply();
    }
}