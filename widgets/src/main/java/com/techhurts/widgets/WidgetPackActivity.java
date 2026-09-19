package com.techhurts.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Lists every widget in the pack with a button that asks the launcher to add
 * it to the home screen, so none of them has to be hunted down in the widget
 * picker.
 */
public class WidgetPackActivity extends Activity {

    /** label, then the provider that draws it. */
    private static final String[][] WIDGETS = {
            {"TechHurts Weather", "com.techhurts.weatherwidget.WeatherWidgetProvider"},
            {"TechHurts Calculator", "com.techhurts.calculator.CalculatorWidgetProvider"},
            {"GOES East satellite", "com.techhurts.goeseast.GoesEastWidgetProvider"},
            {"Himawari-8 satellite", "com.techhurts.himawari8.HimawariWidgetProvider"},
            {"TV Remote", "com.techhurts.hisense_remote.HisenseRemoteWidgetProvider"},
            {"TV Remote 1×1", "com.techhurts.hisense_remote.Remote1x1Provider"},
            {"TV Remote 2×1", "com.techhurts.hisense_remote.Remote2x1Provider"},
            {"TV Remote 2×2", "com.techhurts.hisense_remote.Remote2x2Provider"},
            {"TV Remote 3×1", "com.techhurts.hisense_remote.Remote3x1Provider"},
            {"TV Remote 3×3", "com.techhurts.hisense_remote.Remote3x3Provider"},
            {"Timer", "com.techhurts.timer.TimerWidgetProvider"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setBackgroundColor(Color.parseColor("#101010"));

        TextView title = new TextView(this);
        title.setText("TechHurts Widgets");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Every widget in one app. Add one to your home screen:");
        subtitle.setTextColor(Color.parseColor("#AAFFFFFF"));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 12, 0, 28);
        root.addView(subtitle);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        boolean canPin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && manager.isRequestPinAppWidgetSupported();

        for (String[] widget : WIDGETS) {
            Button button = new Button(this);
            button.setText(widget[0]);
            button.setAllCaps(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 16);
            button.setLayoutParams(params);
            button.setOnClickListener(v -> {
                if (!canPin) {
                    Toast.makeText(this, "Add it from your launcher's widget list",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                manager.requestPinAppWidget(
                        new ComponentName(getPackageName(), widget[1]), null, null);
            });
            root.addView(button);
        }

        // The remote is the one widget with more to set up than a pin, and its
        // own launcher entry is removed in this build, so the way in is here.
        Button remoteSettings = new Button(this);
        remoteSettings.setText("TV Remote settings");
        remoteSettings.setAllCaps(false);
        LinearLayout.LayoutParams remoteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        remoteParams.setMargins(0, 24, 0, 16);
        remoteSettings.setLayoutParams(remoteParams);
        remoteSettings.setOnClickListener(v -> startActivity(new Intent(
                this, com.techhurts.hisense_remote.RemoteSettingsActivity.class)));
        root.addView(remoteSettings);

        ScrollView scroller = new ScrollView(this);
        scroller.addView(root);
        setContentView(scroller);
    }
}
