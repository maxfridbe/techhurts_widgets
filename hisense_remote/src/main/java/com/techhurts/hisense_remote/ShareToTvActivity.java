package com.techhurts.hisense_remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives a shared link and opens it in the TV's browser. With more than one
 * TV configured across the widgets, it asks which one first.
 */
public class ShareToTvActivity extends Activity {

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    /** A TV the widgets already know about. */
    private static final class Target {
        final String ip;
        final String protocol;

        Target(String ip, String protocol) {
            this.ip = ip;
            this.protocol = protocol;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = extractUrl(getIntent());
        if (url == null) {
            Toast.makeText(this, "No link found in that share", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        List<Target> targets = configuredTargets();
        if (targets.isEmpty()) {
            Toast.makeText(this, "Set up a TV widget first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (targets.size() == 1) {
            send(targets.get(0), url);
            return;
        }

        String[] names = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            names[i] = targets.get(i).ip;
        }
        new AlertDialog.Builder(this)
                .setTitle("Open on which TV?")
                .setItems(names, (dialog, which) -> send(targets.get(which), url))
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    /** Share sheets hand over plain text, which often wraps the link in words. */
    private String extractUrl(Intent intent) {
        if (intent == null) return null;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null && intent.getData() != null) {
            text = intent.getData().toString();
        }
        if (text == null) return null;
        Matcher matcher = URL.matcher(text);
        if (matcher.find()) return matcher.group();
        String trimmed = text.trim();
        return trimmed.contains(".") && !trimmed.contains(" ") ? "https://" + trimmed : null;
    }

    /** Every distinct TV any widget is pointed at. */
    private List<Target> configuredTargets() {
        SharedPreferences prefs = getSharedPreferences("com.techhurts.hisense_remote.prefs", 0);
        List<Target> targets = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("ip_")) continue;
            String ip = String.valueOf(entry.getValue());
            if (ip.isEmpty() || seen.contains(ip)) continue;
            String widgetId = entry.getKey().substring("ip_".length());
            String protocol = prefs.getString("protocol_" + widgetId, "android_tv");
            seen.add(ip);
            targets.add(new Target(ip, protocol));
        }
        if (targets.isEmpty()) {
            String last = prefs.getString("last_ip", "");
            if (!last.isEmpty()) {
                targets.add(new Target(last, prefs.getString("last_protocol", "android_tv")));
            }
        }
        return targets;
    }

    private void send(Target target, String url) {
        Toast.makeText(this, "Opening on " + target.ip, Toast.LENGTH_SHORT).show();
        // Networking, so never on the main thread.
        new Thread(() -> RemoteCommandExecutor.sendKey(
                getApplicationContext(), target.ip, target.protocol,
                RemoteButtons.APP_PREFIX + url)).start();
        finish();
    }
}
