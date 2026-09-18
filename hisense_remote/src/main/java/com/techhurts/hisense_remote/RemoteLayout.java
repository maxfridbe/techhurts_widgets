package com.techhurts.hisense_remote;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Each widget's button grid, stored as "key~colour" cells separated by commas.
 * Grid dimensions come from the widget's size variant.
 */
final class RemoteLayout {

    /** Kept for the default 4-column layout used by the resizable widget. */
    static final int COLUMNS = 4;
    static final int CELLS = 20;

    private static final String PREFS = "com.techhurts.hisense_remote.prefs";
    private static final String KEY_PREFIX = "layout_";

    /** One grid position: which button, and which palette colour. */
    static final class Cell {
        final String key;
        final int color;   // index into RemoteButtons.PALETTE

        Cell(String key, int color) {
            this.key = key;
            this.color = color;
        }

        boolean isEmpty() {
            return key == null || key.isEmpty();
        }

        static Cell empty() {
            return new Cell("", 0);
        }
    }

    static List<Cell> load(Context context, int appWidgetId) {
        int cells = RemoteSize.of(context, appWidgetId).cells();
        String stored = context.getSharedPreferences(PREFS, 0)
                .getString(KEY_PREFIX + appWidgetId, null);
        if (stored == null || stored.isEmpty()) {
            return pad(defaultFor(cells), cells);
        }
        List<Cell> parsed = new ArrayList<>();
        for (String part : stored.split(",", -1)) {
            int split = part.lastIndexOf('~');
            if (split < 0) {
                parsed.add(new Cell(part, 0));
            } else {
                int color = 0;
                try {
                    color = Integer.parseInt(part.substring(split + 1));
                } catch (NumberFormatException ignored) {
                    // stored by an older build without colours
                }
                parsed.add(new Cell(part.substring(0, split), color));
            }
        }
        return pad(parsed, cells);
    }

    static void save(Context context, int appWidgetId, List<Cell> cells) {
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            if (out.length() > 0) out.append(',');
            out.append(cell.key).append('~').append(cell.color);
        }
        context.getSharedPreferences(PREFS, 0).edit()
                .putString(KEY_PREFIX + appWidgetId, out.toString()).apply();
    }

    /** Colour used by every button that has no colour of its own. */
    static int globalColor(Context context, int appWidgetId) {
        int index = context.getSharedPreferences(PREFS, 0).getInt("color_" + appWidgetId, 0);
        return RemoteButtons.PALETTE[Math.max(0, Math.min(index, RemoteButtons.PALETTE.length - 1))];
    }

    static int globalColorIndex(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS, 0).getInt("color_" + appWidgetId, 0);
    }

    static void setGlobalColor(Context context, int appWidgetId, int paletteIndex) {
        context.getSharedPreferences(PREFS, 0).edit()
                .putInt("color_" + appWidgetId, paletteIndex).apply();
    }

    static void remove(Context context, int appWidgetId) {
        context.getSharedPreferences(PREFS, 0).edit()
                .remove(KEY_PREFIX + appWidgetId)
                .remove("color_" + appWidgetId)
                .apply();
    }

    /** Sensible starting grids, trimmed to whatever the widget can show. */
    private static List<Cell> defaultFor(int cells) {
        String[] order;
        if (cells <= 1) {
            order = new String[]{"KEY_POWER"};
        } else if (cells <= 2) {
            order = new String[]{"KEY_POWER", "KEY_HOME"};
        } else if (cells <= 3) {
            order = new String[]{"KEY_POWER", "KEY_HOME", "KEY_MUTE"};
        } else if (cells <= 4) {
            order = new String[]{"KEY_POWER", "KEY_HOME", "KEY_VOL_UP", "KEY_VOL_DOWN"};
        } else if (cells <= 9) {
            order = new String[]{
                    "KEY_POWER", "KEY_UP", "KEY_HOME",
                    "KEY_LEFT", "KEY_OK", "KEY_RIGHT",
                    "KEY_BACK", "KEY_DOWN", "KEY_MUTE"};
        } else {
            order = new String[]{
                    "KEY_POWER", "KEY_INPUT", "KEY_MUTE", "KEY_HOME",
                    "", "KEY_UP", "", "KEY_VOL_UP",
                    "KEY_LEFT", "KEY_OK", "KEY_RIGHT", "KEY_VOL_DOWN",
                    "", "KEY_DOWN", "", "KEY_BACK"};
        }
        List<Cell> out = new ArrayList<>();
        for (String key : order) out.add(new Cell(key, 0));
        return out;
    }

    private static List<Cell> pad(List<Cell> cells, int size) {
        List<Cell> out = new ArrayList<>(cells.subList(0, Math.min(cells.size(), size)));
        while (out.size() < size) out.add(Cell.empty());
        return out;
    }

    private RemoteLayout() {}
}
