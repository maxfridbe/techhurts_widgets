package com.techhurts.hisense_remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Any colour, not just the eight in the palette.
 *
 * Three sliders and a hex box, which is about as much as is worth building
 * without a colour-wheel library: the sliders are for finding a colour, the
 * hex box for typing one you already know. Each keeps the other in step.
 */
final class ColorPickerDialog {

    interface OnPicked {
        void picked(int color);
    }

    private final Activity activity;
    private final int[] channels = new int[3];
    private final SeekBar[] sliders = new SeekBar[3];
    private View preview;
    private EditText hex;
    private boolean updating;

    private ColorPickerDialog(Activity activity, int startingColor) {
        this.activity = activity;
        channels[0] = Color.red(startingColor);
        channels[1] = Color.green(startingColor);
        channels[2] = Color.blue(startingColor);
    }

    static void show(Activity activity, String title, int startingColor, OnPicked onPicked) {
        new ColorPickerDialog(activity, startingColor).build(title, onPicked);
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private int current() {
        return Color.rgb(channels[0], channels[1], channels[2]);
    }

    private void build(String title, OnPicked onPicked) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(4));

        preview = new View(activity);
        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        previewParams.bottomMargin = dp(14);
        preview.setLayoutParams(previewParams);
        root.addView(preview);

        String[] names = {"Red", "Green", "Blue"};
        for (int i = 0; i < 3; i++) {
            root.addView(channelRow(names[i], i));
        }

        LinearLayout hexRow = new LinearLayout(activity);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        hexRow.setPadding(0, dp(8), 0, 0);

        TextView hash = new TextView(activity);
        hash.setText("#");
        hash.setTextColor(Color.parseColor("#AAFFFFFF"));
        hash.setTextSize(16);
        hexRow.addView(hash);

        hex = new EditText(activity);
        hex.setSingleLine(true);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        hex.setTextColor(Color.WHITE);
        hex.setBackgroundResource(R.drawable.edit_text_bg);
        hex.setPadding(dp(10), dp(8), dp(10), dp(8));
        hex.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable editable) {
                if (updating) return;
                String text = editable.toString().trim();
                if (text.length() != 6) return;
                try {
                    int typed = Color.parseColor("#" + text);
                    channels[0] = Color.red(typed);
                    channels[1] = Color.green(typed);
                    channels[2] = Color.blue(typed);
                    refresh(true);
                } catch (IllegalArgumentException notAColour) {
                    // Half-typed, so leave the sliders where they are.
                }
            }
        });
        hexRow.addView(hex);
        root.addView(hexRow);

        refresh(false);

        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(root)
                .setPositiveButton("Use this colour", (dialog, which) -> onPicked.picked(current()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View channelRow(String name, int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextColor(Color.parseColor("#AAFFFFFF"));
        label.setTextSize(12);
        label.setWidth(dp(48));
        row.addView(label);

        SeekBar bar = new SeekBar(activity);
        bar.setMax(255);
        bar.setProgress(channels[index]);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                channels[index] = progress;
                refresh(false);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(bar);
        sliders[index] = bar;
        return row;
    }

    /** Keeps the swatch, the sliders and the hex box saying the same thing. */
    private void refresh(boolean fromHex) {
        int color = current();

        GradientDrawable swatch = new GradientDrawable();
        swatch.setColor(color);
        swatch.setCornerRadius(dp(10));
        swatch.setStroke(dp(1), Color.parseColor("#55FFFFFF"));
        preview.setBackground(swatch);

        if (fromHex) {
            for (int i = 0; i < 3; i++) {
                if (sliders[i] != null) sliders[i].setProgress(channels[i]);
            }
            return;
        }

        updating = true;
        hex.setText(String.format("%06X", color & 0xFFFFFF));
        hex.setSelection(hex.getText().length());
        updating = false;
    }
}
