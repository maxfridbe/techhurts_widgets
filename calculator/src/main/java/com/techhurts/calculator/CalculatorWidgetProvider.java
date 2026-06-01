package com.techhurts.calculator;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

public class CalculatorWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "com.techhurts.calculator.prefs";
    private static final String PREF_EXPRESSION = "expression_";
    private static final String PREF_RESULT = "result_";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, appWidgetManager, id);
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        String expression = prefs.getString(PREF_EXPRESSION + appWidgetId, "");
        String result = prefs.getString(PREF_RESULT + appWidgetId, "");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calculator_widget_layout);

        if (result.isEmpty()) {
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE);
            views.setViewVisibility(R.id.widget_result, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_icon, View.GONE);
            views.setViewVisibility(R.id.widget_result, View.VISIBLE);
            views.setTextViewText(R.id.widget_result, result);
        }

        Intent intent = new Intent(context, CalculatorActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    static void saveAndUpdate(Context context, int appWidgetId, String expression, String result) {
        context.getSharedPreferences(PREFS_NAME, 0).edit()
                .putString(PREF_EXPRESSION + appWidgetId, expression)
                .putString(PREF_RESULT + appWidgetId, result)
                .apply();
        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId);
    }

    static void clearAndUpdate(Context context, int appWidgetId) {
        context.getSharedPreferences(PREFS_NAME, 0).edit()
                .remove(PREF_EXPRESSION + appWidgetId)
                .remove(PREF_RESULT + appWidgetId)
                .apply();
        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 0).edit();
        for (int id : appWidgetIds) {
            editor.remove(PREF_EXPRESSION + id);
            editor.remove(PREF_RESULT + id);
        }
        editor.apply();
    }
}
