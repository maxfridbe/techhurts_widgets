package com.techhurts.hisense_remote;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;

/**
 * What a stored colour means.
 *
 * Colours used to be an index into a fixed palette, which is all the storage
 * format can hold: cells are "key~icon~button" and both colours are read back
 * as plain integers. Rather than change the format — old layouts have to keep
 * working — the integer's range says how to read it:
 *
 *   0                inherit the widget's colour
 *   1 .. PALETTE-1   an index into the old fixed palette
 *   ACCENT           whatever accent colour Android is using
 *   negative         a literal colour, stored as an opaque ARGB int
 *
 * Opaque ARGB is always negative as a signed int (the alpha byte is 0xFF), so
 * a chosen colour can never be mistaken for an index or for ACCENT. Colours
 * from the picker are forced opaque to keep that true.
 */
final class RemoteColors {

    static final int INHERIT = 0;

    /** Deliberately past the palette and nowhere near a colour value. */
    static final int ACCENT = 1000;

    /** Used where the system offers no accent of its own. */
    private static final int FALLBACK_ACCENT = Color.parseColor("#4FC3F7");

    static boolean isCustom(int stored) {
        return stored < 0;
    }

    static int toStored(int color) {
        // Opaque, so the value stays negative and unambiguous.
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    /** The icon colour a stored value means, with `inherited` for 0. */
    static int icon(Context context, int stored, int inherited) {
        if (stored == INHERIT) return inherited;
        if (stored == ACCENT) return accent(context);
        if (isCustom(stored)) return stored;
        return RemoteButtons.PALETTE[Math.min(stored, RemoteButtons.PALETTE.length - 1)];
    }

    /**
     * The button colour a stored value means. The accent is dimmed here: a
     * button is behind an icon, and a full-strength accent behind a white glyph
     * leaves the glyph hard to read.
     */
    static int button(Context context, int stored, int inherited) {
        if (stored == INHERIT) return inherited;
        if (stored == ACCENT) return dim(accent(context));
        if (isCustom(stored)) return dim(stored);
        return RemoteButtons.buttonColor(stored);
    }

    /** A chosen button colour is shown at 80%, so a white icon still reads. */
    private static int dim(int color) {
        return Color.argb(204, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * The accent Android is themed with. Android 12 exposes the wallpaper
     * colours the launcher and quick settings use; before that, the closest
     * thing is the theme's own accent.
     */
    static int accent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_accent1_200);
            } catch (Exception noSuchColor) {
                // Some builds report S without the palette; fall through.
            }
        }
        TypedArray themed = context.obtainStyledAttributes(
                new int[]{android.R.attr.colorAccent});
        try {
            return themed.getColor(0, FALLBACK_ACCENT);
        } finally {
            themed.recycle();
        }
    }

    private RemoteColors() {}
}
