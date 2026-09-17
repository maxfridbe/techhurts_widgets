package com.techhurts.hisense_remote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class HisenseRemoteWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("com.techhurts.hisense_remote.prefs", 0);
        String ip = prefs.getString("ip_" + appWidgetId, "");
        String protocol = prefs.getString("protocol_" + appWidgetId, "");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.hisense_remote_widget_layout);

        if (ip.isEmpty()) {
            views.setTextViewText(R.id.txt_status, "Tap to Configure");
            
            Intent configIntent = new Intent(context, WidgetConfigureActivity.class);
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent);
        } else {
            views.setTextViewText(R.id.txt_status, ip + " (" + getProtocolLabel(protocol) + ")");
            
            Intent configIntent = new Intent(context, WidgetConfigureActivity.class);
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.txt_title, configPendingIntent);

            // Bind click pending intents for each remote button
            views.setOnClickPendingIntent(R.id.btn_power, getPendingIntent(context, appWidgetId, "KEY_POWER"));
            views.setOnClickPendingIntent(R.id.btn_input, getPendingIntent(context, appWidgetId, "KEY_INPUT"));
            views.setOnClickPendingIntent(R.id.btn_up, getPendingIntent(context, appWidgetId, "KEY_UP"));
            views.setOnClickPendingIntent(R.id.btn_left, getPendingIntent(context, appWidgetId, "KEY_LEFT"));
            views.setOnClickPendingIntent(R.id.btn_ok, getPendingIntent(context, appWidgetId, "KEY_OK"));
            views.setOnClickPendingIntent(R.id.btn_right, getPendingIntent(context, appWidgetId, "KEY_RIGHT"));
            views.setOnClickPendingIntent(R.id.btn_down, getPendingIntent(context, appWidgetId, "KEY_DOWN"));
            views.setOnClickPendingIntent(R.id.btn_back, getPendingIntent(context, appWidgetId, "KEY_BACK"));
            views.setOnClickPendingIntent(R.id.btn_home, getPendingIntent(context, appWidgetId, "KEY_HOME"));
            views.setOnClickPendingIntent(R.id.btn_vol_down, getPendingIntent(context, appWidgetId, "KEY_VOL_DOWN"));
            views.setOnClickPendingIntent(R.id.btn_mute, getPendingIntent(context, appWidgetId, "KEY_MUTE"));
            views.setOnClickPendingIntent(R.id.btn_vol_up, getPendingIntent(context, appWidgetId, "KEY_VOL_UP"));
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent getPendingIntent(Context context, int appWidgetId, String key) {
        Intent intent = new Intent(context, HisenseRemoteWidgetProvider.class);
        intent.setAction("com.techhurts.hisense_remote.ACTION_SEND_KEY");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra("extra_key", key);
        int requestCode = appWidgetId * 100 + key.hashCode();
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static String getProtocolLabel(String protocol) {
        if ("android_tv".equalsIgnoreCase(protocol)) return "AndroidTV";
        if ("roku".equalsIgnoreCase(protocol)) return "Roku";
        if ("ascii".equalsIgnoreCase(protocol)) return "ASCII";
        if ("hex".equalsIgnoreCase(protocol)) return "HEX";
        return protocol;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("com.techhurts.hisense_remote.ACTION_SEND_KEY".equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String key = intent.getStringExtra("extra_key");

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && key != null) {
                SharedPreferences prefs = context.getSharedPreferences("com.techhurts.hisense_remote.prefs", 0);
                String ip = prefs.getString("ip_" + appWidgetId, "");
                String protocol = prefs.getString("protocol_" + appWidgetId, "");

                if (!ip.isEmpty() && !protocol.isEmpty()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            RemoteCommandExecutor.sendKey(context, ip, protocol, key);
                        }
                    }).start();
                }
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences.Editor editor = context.getSharedPreferences("com.techhurts.hisense_remote.prefs", 0).edit();
        for (int id : appWidgetIds) {
            editor.remove("ip_" + id);
            editor.remove("protocol_" + id);
        }
        editor.apply();
    }
}
