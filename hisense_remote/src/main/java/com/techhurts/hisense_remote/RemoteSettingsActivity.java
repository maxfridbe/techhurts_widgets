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
            empty.setTextColor(Color.parseColor("#CCFFFFFF"));
            empty.setTextSize(14);
            empty.setPadding(8, 24, 8, 24);
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
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(20, 18, 20, 18);
        row.setBackgroundResource(R.drawable.remote_button_bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 12);
        row.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(size.columns + "×" + size.rows + " remote");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        row.addView(title);

        TextView detail = new TextView(this);
        detail.setText(ip.isEmpty()
                ? "No TV set — tap to choose one"
                : ip + " · " + protocolLabel(protocol) + " · " + placedButtons(appWidgetId)
                        + " of " + size.cells() + " buttons");
        detail.setTextColor(Color.parseColor("#AAFFFFFF"));
        detail.setTextSize(12);
        detail.setPadding(0, 4, 0, 0);
        row.addView(detail);

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

    private void showMenu(int appWidgetId, RemoteSize size) {
        String[] choices = {
            "Edit buttons",
            "Change TV",
            "Reset buttons to default",
        };
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
