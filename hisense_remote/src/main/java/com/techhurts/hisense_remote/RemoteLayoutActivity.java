package com.techhurts.hisense_remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Build the widget's button grid: drag icons from the palette into it, drag
 * between cells to swap, and drop a placed button on the palette to remove it.
 * Tapping a placed button gives it its own colour; the row at the top sets the
 * colour for everything else.
 */
public class RemoteLayoutActivity extends Activity {

    private static final String DRAG_FROM_PALETTE = "palette:";
    private static final String DRAG_FROM_CELL = "cell:";

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final List<RemoteLayout.Cell> mCells = new ArrayList<>();
    private RemoteSize mSize;
    private int mGlobalColor;
    private int mGlobalButtonColor;
    private GridLayout mGrid;
    private GridLayout mPalette;
    private LinearLayout mColorRow;
    private LinearLayout mButtonColorRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_layout);

        mAppWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        mSize = RemoteSize.of(this, mAppWidgetId);
        mGlobalColor = RemoteLayout.globalColorIndex(this, mAppWidgetId);
        mGlobalButtonColor = RemoteLayout.globalButtonColorIndex(this, mAppWidgetId);
        mCells.clear();
        mCells.addAll(RemoteLayout.load(this, mAppWidgetId));

        mGrid = findViewById(R.id.layout_grid);
        mPalette = findViewById(R.id.layout_palette);
        mColorRow = findViewById(R.id.layout_colors);
        mButtonColorRow = findViewById(R.id.layout_button_colors);
        mGrid.setColumnCount(mSize.columns);

        ((TextView) findViewById(R.id.layout_subtitle)).setText(
                "Widget is " + mSize.columns + "×" + mSize.rows
                        + ". Tap an icon then a cell to place it; tap a placed button to colour it.");

        buildColorRow();
        buildButtonColorRow();
        buildPalette();
        buildGrid();

        findViewById(R.id.btn_layout_save).setOnClickListener(v -> {
            RemoteLayout.save(this, mAppWidgetId, mCells);
            RemoteLayout.setGlobalColor(this, mAppWidgetId, mGlobalColor);
            RemoteLayout.setGlobalButtonColor(this, mAppWidgetId, mGlobalButtonColor);
            HisenseRemoteWidgetProvider.updateWidget(
                    this, AppWidgetManager.getInstance(this), mAppWidgetId);
            setResult(RESULT_OK, new Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
            finish();
        });

        findViewById(R.id.btn_layout_reset).setOnClickListener(v -> {
            RemoteLayout.remove(this, mAppWidgetId);
            mCells.clear();
            mCells.addAll(RemoteLayout.load(this, mAppWidgetId));
            mGlobalColor = 0;
            mGlobalButtonColor = 0;
            buildColorRow();
            buildButtonColorRow();
            buildGrid();
        });

        // Dropping on the palette clears the button that was dragged there.
        mPalette.setOnDragListener((view, event) -> {
            if (event.getAction() == DragEvent.ACTION_DROP) {
                String payload = payloadOf(event);
                if (payload.startsWith(DRAG_FROM_CELL)) {
                    int index = Integer.parseInt(payload.substring(DRAG_FROM_CELL.length()));
                    mCells.set(index, RemoteLayout.Cell.empty());
                    buildGrid();
                }
            }
            return true;
        });
    }

    /** Colour swatches that apply to every button without its own colour. */
    private void buildColorRow() {
        mColorRow.removeAllViews();
        for (int i = 0; i < RemoteButtons.PALETTE.length; i++) {
            final int index = i;
            mColorRow.addView(swatch(
                    RemoteButtons.PALETTE[index],
                    mGlobalColor == index,
                    false,
                    v -> {
                        mGlobalColor = index;
                        buildColorRow();
                        buildGrid();
                    }));
        }
        // Whatever accent Android is themed with, which follows the wallpaper.
        mColorRow.addView(swatch(
                RemoteColors.accent(this),
                mGlobalColor == RemoteColors.ACCENT,
                false,
                v -> {
                    mGlobalColor = RemoteColors.ACCENT;
                    buildColorRow();
                    buildGrid();
                }));
        mColorRow.addView(pickerSwatch(
                RemoteColors.isCustom(mGlobalColor) ? mGlobalColor : Color.WHITE,
                RemoteColors.isCustom(mGlobalColor),
                "Icon colour",
                color -> {
                    mGlobalColor = RemoteColors.toStored(color);
                    buildColorRow();
                    buildGrid();
                }));
    }

    /** One swatch: a filled ring when chosen, an empty one when not. */
    private TextView swatch(int color, boolean chosen, boolean filled, View.OnClickListener click) {
        TextView view = new TextView(this);
        view.setText(filled ? (chosen ? "■" : "□") : (chosen ? "●" : "○"));
        view.setTextSize(filled ? 22 : 24);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(10, 4, 10, 4);
        view.setOnClickListener(click);
        return view;
    }

    /** The last swatch in a row opens the picker rather than choosing a colour. */
    private TextView pickerSwatch(int color, boolean chosen, String title,
                                  ColorPickerDialog.OnPicked onPicked) {
        TextView view = new TextView(this);
        view.setText(chosen ? "◉" : "＋");
        view.setTextSize(chosen ? 24 : 20);
        view.setTextColor(chosen ? color : Color.parseColor("#AAFFFFFF"));
        view.setGravity(Gravity.CENTER);
        view.setPadding(10, 4, 10, 4);
        view.setOnClickListener(v -> ColorPickerDialog.show(this, title, color, onPicked));
        return view;
    }

    /**
     * Swatches for the button behind the icon. Drawn as filled blocks rather
     * than the icon row's rings, because "none" has to look like nothing and a
     * ring would look like a colour.
     */
    private void buildButtonColorRow() {
        mButtonColorRow.removeAllViews();
        for (int i = 0; i < RemoteButtons.BUTTON_PALETTE.length; i++) {
            final int index = i;
            mButtonColorRow.addView(swatch(
                    index == 0 ? Color.parseColor("#88FFFFFF")
                               : swatchColor(RemoteButtons.BUTTON_PALETTE[index]),
                    mGlobalButtonColor == index,
                    true,
                    v -> {
                        mGlobalButtonColor = index;
                        buildButtonColorRow();
                        buildGrid();
                    }));
        }
        mButtonColorRow.addView(swatch(
                swatchColor(RemoteColors.accent(this)),
                mGlobalButtonColor == RemoteColors.ACCENT,
                true,
                v -> {
                    mGlobalButtonColor = RemoteColors.ACCENT;
                    buildButtonColorRow();
                    buildGrid();
                }));
        mButtonColorRow.addView(pickerSwatch(
                RemoteColors.isCustom(mGlobalButtonColor) ? mGlobalButtonColor : Color.WHITE,
                RemoteColors.isCustom(mGlobalButtonColor),
                "Button colour",
                color -> {
                    mGlobalButtonColor = RemoteColors.toStored(color);
                    buildButtonColorRow();
                    buildGrid();
                }));
    }

    /**
     * A swatch has to be visible on a black screen. The button colours are
     * deliberately dark so a white icon reads on top of them, which makes them
     * almost invisible as swatches, so the swatch shows a lightened version of
     * the same hue. Only the swatch — the widget draws the real colour.
     */
    private static int swatchColor(int color) {
        return Color.rgb(
                Math.min(255, Color.red(color) + 90),
                Math.min(255, Color.green(color) + 90),
                Math.min(255, Color.blue(color) + 90));
    }

    private void buildPalette() {
        mPalette.removeAllViews();
        for (RemoteButtons.Button button : RemoteButtons.ALL) {
            mPalette.addView(makeChip(button, DRAG_FROM_PALETTE + button.key, -1));
        }
    }

    private void buildGrid() {
        mGrid.removeAllViews();
        for (int i = 0; i < mSize.cells(); i++) {
            final int index = i;
            RemoteLayout.Cell cell = mCells.get(i);
            RemoteButtons.Button button = RemoteButtons.byKey(cell.key);
            View view = button == null
                    ? makeEmptyCell()
                    : makeChip(button, DRAG_FROM_CELL + index, index);

            view.setOnDragListener((target, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_ENTERED:
                        target.setAlpha(0.5f);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED:
                        target.setAlpha(1f);
                        return true;
                    case DragEvent.ACTION_DROP:
                        target.setAlpha(1f);
                        handleDrop(payloadOf(event), index);
                        return true;
                    default:
                        return true;
                }
            });
            mGrid.addView(view);
        }
    }

    private void handleDrop(String payload, int target) {
        if (payload.startsWith(DRAG_FROM_PALETTE)) {
            String key = payload.substring(DRAG_FROM_PALETTE.length());
            if (RemoteButtons.WEB_PLACEHOLDER.equals(key)) {
                askForUrl(target);
                return;
            }
            mCells.set(target, new RemoteLayout.Cell(key, mCells.get(target).color));
        } else if (payload.startsWith(DRAG_FROM_CELL)) {
            int source = Integer.parseInt(payload.substring(DRAG_FROM_CELL.length()));
            RemoteLayout.Cell moved = mCells.get(source);
            mCells.set(source, mCells.get(target));   // swap, so nothing is lost
            mCells.set(target, moved);
        }
        buildGrid();
    }

    /** Turns a dropped globe into a button that opens a URL in the TV's browser. */
    private void askForUrl(int target) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("https://example.com");
        input.setText("https://");
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Open on TV")
                .setMessage("Address to open in the TV's browser:")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (url.length() > "https://".length()) {
                        mCells.set(target, new RemoteLayout.Cell(
                                RemoteButtons.APP_PREFIX + url, mCells.get(target).color));
                        buildGrid();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Tapping a placed button asks which of its two colours to change. */
    private void pickColor(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Colour this button")
                .setItems(new String[]{"Icon colour", "Button colour"}, (dialog, which) -> {
                    if (which == 0) {
                        pickIconColor(index);
                    } else {
                        pickButtonColor(index);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Per-button icon colour, or "Use widget colour" for the global one. */
    private void pickIconColor(int index) {
        chooseColor("Icon colour", RemoteButtons.PALETTE_NAMES,
                mCells.get(index).color, chosen -> {
                    RemoteLayout.Cell cell = mCells.get(index);
                    mCells.set(index, new RemoteLayout.Cell(cell.key, chosen, cell.buttonColor));
                    buildGrid();
                });
    }

    private void pickButtonColor(int index) {
        chooseColor("Button colour", RemoteButtons.BUTTON_PALETTE_NAMES,
                mCells.get(index).buttonColor, chosen -> {
                    RemoteLayout.Cell cell = mCells.get(index);
                    mCells.set(index, new RemoteLayout.Cell(cell.key, cell.color, chosen));
                    buildGrid();
                });
    }

    private interface OnColorChosen {
        /** The value to store, not the colour itself — see RemoteColors. */
        void chosen(int stored);
    }

    /**
     * The palette, then the system accent, then the picker. The last two are
     * appended rather than being palette entries because neither is a fixed
     * colour: the accent follows the wallpaper and the picker is anything.
     */
    private void chooseColor(String title, String[] paletteNames, int current,
                             OnColorChosen onChosen) {
        String[] names = new String[paletteNames.length + 2];
        names[0] = "Use widget colour";
        System.arraycopy(paletteNames, 1, names, 1, paletteNames.length - 1);
        names[paletteNames.length] = "System accent";
        names[paletteNames.length + 1] = "Pick a colour…";

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(names, (dialog, which) -> {
                    if (which == paletteNames.length) {
                        onChosen.chosen(RemoteColors.ACCENT);
                    } else if (which == paletteNames.length + 1) {
                        int start = RemoteColors.isCustom(current) ? current : Color.WHITE;
                        ColorPickerDialog.show(this, title, start,
                                color -> onChosen.chosen(RemoteColors.toStored(color)));
                    } else {
                        onChosen.chosen(which);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String payloadOf(DragEvent event) {
        ClipData data = event.getClipData();
        if (data == null || data.getItemCount() == 0) return "";
        CharSequence text = data.getItemAt(0).getText();
        return text == null ? "" : text.toString();
    }

    /** `cellIndex` is -1 for palette chips, which carry a label and never recolour. */
    private View makeChip(RemoteButtons.Button button, String payload, int cellIndex) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(6, 10, 6, 10);
        holder.setBackgroundResource(R.drawable.remote_button_bg);

        // Same two colours the widget will draw, so the editor is a preview.
        int globalIcon = RemoteColors.icon(this, mGlobalColor, Color.WHITE);
        int iconColor = cellIndex < 0
                ? globalIcon
                : RemoteColors.icon(this, mCells.get(cellIndex).color, globalIcon);
        if (cellIndex >= 0) {
            int globalFill = RemoteColors.button(this, mGlobalButtonColor, Color.TRANSPARENT);
            int fill = RemoteColors.button(this, mCells.get(cellIndex).buttonColor, globalFill);
            if (Color.alpha(fill) != 0) holder.setBackgroundColor(fill);
        }

        TextView icon = new TextView(this);
        icon.setText(button.glyph);
        icon.setTypeface(RemoteButtons.typeface(this));
        icon.setTextSize(cellIndex >= 0 ? 22 : 24);
        icon.setTextColor(iconColor);
        icon.setGravity(Gravity.CENTER);
        holder.addView(icon);

        if (cellIndex < 0) {
            TextView label = new TextView(this);
            label.setText(button.label);
            label.setTextSize(9);
            label.setTextColor(Color.parseColor("#CCFFFFFF"));
            label.setGravity(Gravity.CENTER);
            holder.addView(label);
        }

        holder.setLayoutParams(cellParams());
        holder.setOnLongClickListener(view -> startDrag(view, payload));
        if (cellIndex >= 0) {
            holder.setOnClickListener(view -> pickColor(cellIndex));
        } else {
            // Palette chips drag on a plain tap too, so no long press is needed.
            holder.setOnClickListener(view -> startDrag(view, payload));
        }
        return holder;
    }

    private boolean startDrag(View view, String payload) {
        ClipData data = ClipData.newPlainText("remote_button", payload);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, null, 0);
        } else {
            view.startDrag(data, shadow, null, 0);
        }
        return true;
    }

    private View makeEmptyCell() {
        TextView empty = new TextView(this);
        empty.setText("+");
        empty.setTextSize(18);
        empty.setTextColor(Color.parseColor("#66FFFFFF"));
        empty.setGravity(Gravity.CENTER);
        empty.setBackgroundResource(R.drawable.edit_text_bg);
        empty.setLayoutParams(cellParams());
        return empty;
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(5, 5, 5, 5);
        return params;
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Layout not saved", Toast.LENGTH_SHORT).show();
        super.onBackPressed();
    }
}
