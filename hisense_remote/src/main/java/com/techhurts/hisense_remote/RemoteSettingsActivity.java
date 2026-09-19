package com.techhurts.hisense_remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The way back into a remote you have already placed.
 *
 * A widget's configuration activity runs once, when the launcher first puts it
 * on the home screen, and there is no way back to it afterwards on most
 * launchers. So this is a normal app entry: it lists every remote on the home
 * screen and lets you edit any of them.
 */
public class RemoteSettingsActivity extends Activity {

    /** The providers a remote can be, biggest first so the list reads sensibly. */
    private static final Class<?>[] PROVIDERS = {
        Remote3x3Provider.class,
        Remote3x1Provider.class,
        Remote2x2Provider.class,
        Remote2x1Provider.class,
        Remote1x1Provider.class,
        HisenseRemoteWidgetProvider.class,
    };

    private LinearLayout mList;

    /** setPadding and friends take pixels; everything here is written in dp. */
    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_settings);

        mList = findViewById(R.id.settings_list);
        findViewById(R.id.btn_add_widget).setOnClickListener(v ->
                startActivity(new Intent(this, PinWidgetActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuilt every time, so a layout edited and backed out of shows its
        // new button count without a restart.
        buildList();
    }

    private void buildList() {
        mList.removeAllViews();
        List<Integer> widgets = placedWidgets();

        if (widgets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No remotes on the home screen yet.\n\n"
                    + "Add one below, or long-press the home screen and pick "
                    + "a TV Remote widget.");
            empty.setTextColor(Color.parseColor("#99FFFFFF"));
            empty.setTextSize(14);
            empty.setLineSpacing(dp(4), 1f);
            empty.setPadding(dp(4), dp(24), dp(4), dp(24));
            mList.addView(empty);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("com.techhurts.hisense_remote.prefs", 0);
        for (int appWidgetId : widgets) {
            mList.addView(makeRow(appWidgetId, prefs));
        }
    }

    /** Every remote widget the launcher is currently showing, whatever its shape. */
    private List<Integer> placedWidgets() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        List<Integer> found = new ArrayList<>();
        for (Class<?> provider : PROVIDERS) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(this, provider));
            for (int id : ids) found.add(id);
        }
        return found;
    }

    private View makeRow(int appWidgetId, SharedPreferences prefs) {
        RemoteSize size = RemoteSize.of(this, appWidgetId);
        String ip = prefs.getString("ip_" + appWidgetId, "");
        String protocol = prefs.getString("protocol_" + appWidgetId, "android_tv");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundResource(R.drawable.remote_settings_row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);

        // The grid shape, so a list of several remotes is told apart at a glance.
        TextView shape = new TextView(this);
        shape.setText(size.columns + "×" + size.rows);
        shape.setTextColor(Color.WHITE);
        shape.setTextSize(15);
        shape.setGravity(Gravity.CENTER);
        shape.setBackgroundResource(R.drawable.remote_button_bg);
        LinearLayout.LayoutParams shapeParams =
                new LinearLayout.LayoutParams(dp(48), dp(48));
        shapeParams.setMargins(0, 0, dp(14), 0);
        shape.setLayoutParams(shapeParams);
        row.addView(shape);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(ip.isEmpty() ? "Not set up yet" : ip);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        text.addView(title);

        TextView detail = new TextView(this);
        detail.setText(ip.isEmpty()
                ? "Tap to choose a TV"
                : protocolLabel(protocol) + " · " + placedButtons(appWidgetId)
                        + " of " + size.cells() + " buttons");
        detail.setTextColor(Color.parseColor("#99FFFFFF"));
        detail.setTextSize(12);
        detail.setPadding(0, dp(2), 0, 0);
        text.addView(detail);
        row.addView(text);

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(Color.parseColor("#66FFFFFF"));
        chevron.setTextSize(22);
        row.addView(chevron);

        row.setOnClickListener(v -> showMenu(appWidgetId, size));
        return row;
    }

    /** How many cells actually have a button on them. */
    private int placedButtons(int appWidgetId) {
        int count = 0;
        for (RemoteLayout.Cell cell : RemoteLayout.load(this, appWidgetId)) {
            if (RemoteButtons.byKey(cell.key) != null) count++;
        }
        return count;
    }

    /** Dark dialogs, so a menu does not flash white over a black screen. */
    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void showMenu(int appWidgetId, RemoteSize size) {
        String[] choices = {
            "Edit buttons",
            "Change TV",
            "Reset buttons to default",
        };
        dialog()
                .setTitle(size.columns + "×" + size.rows + " remote")
                .setItems(choices, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            open(RemoteLayoutActivity.class, appWidgetId);
                            break;
                        case 1:
                            open(WidgetConfigureActivity.class, appWidgetId);
                            break;
                        default:
                            confirmReset(appWidgetId);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Both activities key everything off the widget id, so it has to go along. */
    private void open(Class<?> activity, int appWidgetId) {
        startActivity(new Intent(this, activity)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
    }

    private void confirmReset(int appWidgetId) {
        dialog()
                .setTitle("Reset buttons?")
                .setMessage("The layout goes back to the default for this size. "
                        + "The TV it points at is left alone.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    RemoteLayout.remove(this, appWidgetId);
                    HisenseRemoteWidgetProvider.updateWidget(
                            this, AppWidgetManager.getInstance(this), appWidgetId);
                    Toast.makeText(this, "Buttons reset", Toast.LENGTH_SHORT).show();
                    buildList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String protocolLabel(String protocol) {
        switch (protocol) {
            case "roku": return "Roku";
            case "hisense": return "Hisense";
            default: return "Android TV";
        }
    }
}
