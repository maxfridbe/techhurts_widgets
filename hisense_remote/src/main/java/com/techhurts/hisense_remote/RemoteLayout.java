package com.techhurts.hisense_remote;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Each widget's button grid, stored as "key~icon~button" cells separated by
 * commas. Grid dimensions come from the widget's size variant.
 */
final class RemoteLayout {

    /** Kept for the default 4-column layout used by the resizable widget. */
    static final int COLUMNS = 4;
    static final int CELLS = 20;

    private static final String PREFS = "com.techhurts.hisense_remote.prefs";
    private static final String KEY_PREFIX = "layout_";

    /** One grid position: which button, and the two colours it is drawn with. */
    static final class Cell {
        final String key;
        final int color;         // index into RemoteButtons.PALETTE, the icon
        final int buttonColor;   // index into RemoteButtons.BUTTON_PALETTE

        Cell(String key, int color) {
            this(key, color, 0);
        }

        Cell(String key, int color, int buttonColor) {
            this.key = key;
            this.color = color;
            this.buttonColor = buttonColor;
        }

        boolean isEmpty() {
            return key == null || key.isEmpty();
        }

        static Cell empty() {
            return new Cell("", 0, 0);
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
            parsed.add(parseCell(part));
        }
        return pad(parsed, cells);
    }

    /**
     * "key", "key~icon" and "key~icon~button" all read back, because a layout
     * saved by an older build has to keep working. Colours are taken from the
     * right and only when they are numbers: a key can be a URL, and a URL can
     * contain a tilde.
     */
    private static Cell parseCell(String part) {
        String key = part;
        int icon = 0;
        int button = 0;

        int last = key.lastIndexOf('~');
        Integer lastValue = last < 0 ? null : toInt(key.substring(last + 1));
        if (lastValue == null) {
            return new Cell(key, 0, 0);
        }

        String head = key.substring(0, last);
        int previous = head.lastIndexOf('~');
        Integer previousValue = previous < 0 ? null : toInt(head.substring(previous + 1));
        if (previousValue == null) {
            icon = lastValue;              // "key~icon"
            key = head;
        } else {
            icon = previousValue;          // "key~icon~button"
            button = lastValue;
            key = head.substring(0, previous);
        }
        return new Cell(key, icon, button);
    }

    private static Integer toInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    static void save(Context context, int appWidgetId, List<Cell> cells) {
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            if (out.length() > 0) out.append(',');
            out.append(cell.key).append('~').append(cell.color)
                    .append('~').append(cell.buttonColor);
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

    /** Button colour used by every button that has none of its own. */
    static int globalButtonColor(Context context, int appWidgetId) {
        return RemoteButtons.buttonColor(globalButtonColorIndex(context, appWidgetId));
    }

    static int globalButtonColorIndex(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS, 0).getInt("button_" + appWidgetId, 0);
    }

    static void setGlobalButtonColor(Context context, int appWidgetId, int paletteIndex) {
        context.getSharedPreferences(PREFS, 0).edit()
                .putInt("button_" + appWidgetId, paletteIndex).apply();
    }

    static void remove(Context context, int appWidgetId) {
        context.getSharedPreferences(PREFS, 0).edit()
                .remove(KEY_PREFIX + appWidgetId)
                .remove("color_" + appWidgetId)
                .remove("button_" + appWidgetId)
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
