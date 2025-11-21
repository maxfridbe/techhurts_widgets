package com.techhurts.weatherwidget;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WidgetLogger {
    private static final String TAG = "WeatherWidget";
    private static final List<String> logBuffer = Collections.synchronizedList(new ArrayList<>());

    public static void log(String message) {
        Log.d(TAG, message); // Log to Logcat
        logBuffer.add(message);
    }

    public static List<String> getLogs() {
        return new ArrayList<>(logBuffer);
    }

    public static void clearLogs() {
        logBuffer.clear();
    }
}
