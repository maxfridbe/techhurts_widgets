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
        if (ip.isEmpty()) {
            // A widget added later should just work with the TV already set up.
            ip = prefs.getString("last_ip", "");
            protocol = prefs.getString("last_protocol", "");
        }

        RemoteSize size = RemoteSize.of(context, appWidgetId);
        RemoteViews views = new RemoteViews(context.getPackageName(), size.layout);

        Intent configIntent = new Intent(context, WidgetConfigureActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (ip.isEmpty()) {
            views.setTextViewText(R.id.txt_status, "Tap to Configure");
            views.setViewVisibility(R.id.txt_status, android.view.View.VISIBLE);
            for (int index = 0; index < size.cells(); index++) {
                int cellId = cellId(context, index, size);
                if (cellId != 0) views.setViewVisibility(cellId, android.view.View.GONE);
            }
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent);
        } else {
            if (size == RemoteSize.FLEX) {
                views.setTextViewText(R.id.txt_status, ip + " (" + getProtocolLabel(protocol) + ")");
                views.setOnClickPendingIntent(R.id.txt_title, configPendingIntent);
            } else {
                // Hidden explicitly, not left to the layout's default: the host
                // keeps showing the last thing it was told, so a widget that was
                // "Tap to Configure" before it had a TV said so for ever after.
                views.setViewVisibility(R.id.txt_status, android.view.View.GONE);
            }

            // The grid is whatever RemoteLayoutActivity saved. A widget can't use
            // a custom typeface, so each Nerd Font glyph is drawn into a bitmap.
            java.util.List<RemoteLayout.Cell> cells = RemoteLayout.load(context, appWidgetId);
            int globalColor = RemoteLayout.globalColor(context, appWidgetId);
            // Matches the 48dp cells in remote_grid_*.xml; a smaller bitmap would
            // just be upscaled and come out soft.
            int iconPx = Math.round(context.getResources().getDisplayMetrics().density * 48);

            for (int index = 0; index < size.cells(); index++) {
                int cellId = cellId(context, index, size);
                if (cellId == 0) continue;
                RemoteLayout.Cell cell = cells.get(index);
                RemoteButtons.Button button = RemoteButtons.byKey(cell.key);
                if (button == null) {
                    views.setViewVisibility(cellId, android.view.View.INVISIBLE);
                    continue;
                }
                int color = cell.color > 0 ? RemoteButtons.PALETTE[cell.color] : globalColor;
                views.setViewVisibility(cellId, android.view.View.VISIBLE);
                views.setImageViewBitmap(cellId,
                        RemoteButtons.render(context, button.glyph, iconPx, color));
                views.setContentDescription(cellId, button.label);
                views.setOnClickPendingIntent(cellId,
                        getPendingIntent(context, appWidgetId, button.key));
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /** cell_<row>_<column> ids, resolved by name so the grid stays data-driven. */
    private static int cellId(Context context, int index, RemoteSize size) {
        String name = "cell_" + (index / size.columns) + "_" + (index % size.columns);
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
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
        for (int id : appWidgetIds) RemoteLayout.remove(context, id);
        SharedPreferences.Editor editor = context.getSharedPreferences("com.techhurts.hisense_remote.prefs", 0).edit();
        for (int id : appWidgetIds) {
            editor.remove("ip_" + id);
            editor.remove("protocol_" + id);
        }
        editor.apply();
    }
}
