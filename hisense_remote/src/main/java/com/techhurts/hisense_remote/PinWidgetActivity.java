package com.techhurts.hisense_remote;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Asks the launcher to add this widget to the home screen, so it can be placed
 * without hunting through the widget picker. The launcher shows its own
 * confirmation; nothing happens silently.
 */
public class PinWidgetActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        // "provider" picks a size variant, e.g. Remote2x2Provider.
        String requested = getIntent().getStringExtra("provider");
        ComponentName provider = requested == null || requested.isEmpty()
                ? new ComponentName(this, HisenseRemoteWidgetProvider.class)
                : new ComponentName(getPackageName(), getPackageName() + "." + requested);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(provider, null, null);
        } else {
            Toast.makeText(this, "Add the widget from your launcher's widget list",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
