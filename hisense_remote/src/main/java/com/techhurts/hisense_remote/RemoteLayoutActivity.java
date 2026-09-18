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
    private GridLayout mGrid;
    private GridLayout mPalette;
    private LinearLayout mColorRow;

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
        mCells.clear();
        mCells.addAll(RemoteLayout.load(this, mAppWidgetId));

        mGrid = findViewById(R.id.layout_grid);
        mPalette = findViewById(R.id.layout_palette);
        mColorRow = findViewById(R.id.layout_colors);
        mGrid.setColumnCount(mSize.columns);

        ((TextView) findViewById(R.id.layout_subtitle)).setText(
                "Widget is " + mSize.columns + "×" + mSize.rows
                        + ". Tap an icon then a cell to place it; tap a placed button to colour it.");

        buildColorRow();
        buildPalette();
        buildGrid();

        findViewById(R.id.btn_layout_save).setOnClickListener(v -> {
            RemoteLayout.save(this, mAppWidgetId, mCells);
            RemoteLayout.setGlobalColor(this, mAppWidgetId, mGlobalColor);
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
            buildColorRow();
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
            TextView swatch = new TextView(this);
            swatch.setText(index == mGlobalColor ? "●" : "○");
            swatch.setTextSize(24);
            swatch.setTextColor(RemoteButtons.PALETTE[index]);
            swatch.setGravity(Gravity.CENTER);
            swatch.setPadding(10, 4, 10, 4);
            swatch.setOnClickListener(v -> {
                mGlobalColor = index;
                buildColorRow();
                buildGrid();
            });
            mColorRow.addView(swatch);
        }
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
            mCells.set(target, new RemoteLayout.Cell(
                    payload.substring(DRAG_FROM_PALETTE.length()), mCells.get(target).color));
        } else if (payload.startsWith(DRAG_FROM_CELL)) {
            int source = Integer.parseInt(payload.substring(DRAG_FROM_CELL.length()));
            RemoteLayout.Cell moved = mCells.get(source);
            mCells.set(source, mCells.get(target));   // swap, so nothing is lost
            mCells.set(target, moved);
        }
        buildGrid();
    }

    /** Per-button colour, or "Use widget colour" to fall back to the global one. */
    private void pickColor(int index) {
        String[] names = new String[RemoteButtons.PALETTE_NAMES.length];
        names[0] = "Use widget colour";
        System.arraycopy(RemoteButtons.PALETTE_NAMES, 1, names, 1, names.length - 1);
        new AlertDialog.Builder(this)
                .setTitle("Button colour")
                .setItems(names, (dialog, which) -> {
                    RemoteLayout.Cell cell = mCells.get(index);
                    mCells.set(index, new RemoteLayout.Cell(cell.key, which));
                    buildGrid();
                })
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

        int colorIndex = cellIndex >= 0 && mCells.get(cellIndex).color > 0
                ? mCells.get(cellIndex).color : mGlobalColor;

        TextView icon = new TextView(this);
        icon.setText(button.glyph);
        icon.setTypeface(RemoteButtons.typeface(this));
        icon.setTextSize(cellIndex >= 0 ? 22 : 24);
        icon.setTextColor(RemoteButtons.PALETTE[colorIndex]);
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
