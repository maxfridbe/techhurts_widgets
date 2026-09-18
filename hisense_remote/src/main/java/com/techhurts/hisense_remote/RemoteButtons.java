package com.techhurts.hisense_remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * The buttons a remote layout can be built from. Icons are Nerd Font glyphs
 * (Material Design range) drawn with the subset font in assets/nerd_icons.ttf —
 * a home-screen widget can't apply a custom typeface, so glyphs are rendered to
 * bitmaps instead.
 */
final class RemoteButtons {

    /** Keys beginning with this launch an app instead of sending a key code. */
    static final String APP_PREFIX = "APP:";

    static final class Button {
        final String key;     // key code name, or APP:<app link>
        final String glyph;   // Nerd Font character
        final String label;   // shown in the editor

        Button(String key, int codepoint, String label) {
            this.key = key;
            this.glyph = new String(Character.toChars(codepoint));
            this.label = label;
        }

        boolean isAppLaunch() {
            return key.startsWith(APP_PREFIX);
        }

        String appLink() {
            return key.substring(APP_PREFIX.length());
        }
    }

    /** Everything an Android TV accepts; the editor shows these as a palette. */
    static final Button[] ALL = {
            new Button("KEY_POWER",    0xF0425, "Power"),
            new Button("KEY_HOME",     0xF02DC, "Home"),
            new Button("KEY_BACK",     0xF004D, "Back"),
            new Button("KEY_UP",       0xF0143, "Up"),
            new Button("KEY_DOWN",     0xF0140, "Down"),
            new Button("KEY_LEFT",     0xF0141, "Left"),
            new Button("KEY_RIGHT",    0xF0142, "Right"),
            new Button("KEY_OK",       0xF0766, "OK"),
            new Button("KEY_VOL_UP",   0xF057E, "Volume up"),
            new Button("KEY_VOL_DOWN", 0xF0580, "Volume down"),
            new Button("KEY_MUTE",     0xF075F, "Mute"),
            new Button("KEY_INPUT",    0xF072B, "Input"),
            new Button("KEY_PLAY",     0xF040A, "Play"),
            new Button("KEY_PAUSE",    0xF03E4, "Pause"),
            new Button("KEY_REWIND",   0xF045F, "Rewind"),
            new Button("KEY_FORWARD",  0xF0211, "Fast forward"),
            new Button("KEY_STOP",     0xF04DB, "Stop"),
            new Button("KEY_MENU",     0xF035C, "Menu"),
            new Button("KEY_GUIDE",    0xF02D9, "Guide"),
            new Button("KEY_SETTINGS", 0xF0493, "Settings"),
            new Button("KEY_TV",       0xF0379, "TV"),
            new Button("KEY_MIC",      0xF036C, "Assistant"),
            // Inputs
            new Button("KEY_HDMI_1",   0xF072B, "HDMI 1"),
            new Button("KEY_HDMI_2",   0xF072B, "HDMI 2"),
            new Button("KEY_HDMI_3",   0xF072B, "HDMI 3"),
            new Button("KEY_HDMI_4",   0xF072B, "HDMI 4"),
            // Apps, opened by app link rather than a key code
            new Button(APP_PREFIX + "https://www.netflix.com/title", 0xF0746, "Netflix"),
            new Button(APP_PREFIX + "https://www.youtube.com/tv",    0xF05C3, "YouTube"),
            new Button(APP_PREFIX + "plex://",                        0xF0A4E, "Plex"),
            new Button(APP_PREFIX + "spotify://",                     0xF04C2, "Spotify"),
            new Button(APP_PREFIX + "https://www.disneyplus.com",     0xF0FCE, "Disney+"),
            new Button(APP_PREFIX + "https://pbskids.org",            0xF0333, "PBS Kids"),
            new Button(APP_PREFIX + "https://app.primevideo.com",     0xF0E0F, "Prime Video"),
            new Button(APP_PREFIX + "https://tv.apple.com",           0xF0179, "Apple TV"),
    };

    /** The layout a freshly added widget starts with. */
    static List<String> defaultLayout() {
        List<String> keys = new ArrayList<>();
        keys.add("KEY_POWER");  keys.add("KEY_INPUT");    keys.add("KEY_MUTE");     keys.add("KEY_HOME");
        keys.add("");           keys.add("KEY_UP");       keys.add("");             keys.add("KEY_VOL_UP");
        keys.add("KEY_LEFT");   keys.add("KEY_OK");       keys.add("KEY_RIGHT");    keys.add("KEY_VOL_DOWN");
        keys.add("");           keys.add("KEY_DOWN");     keys.add("");             keys.add("KEY_BACK");
        return keys;
    }

    static Button byKey(String key) {
        for (Button b : ALL) {
            if (b.key.equals(key)) return b;
        }
        return null;
    }

    private static Typeface sTypeface;

    static synchronized Typeface typeface(Context context) {
        if (sTypeface == null) {
            sTypeface = Typeface.createFromAsset(context.getAssets(), "nerd_icons.ttf");
        }
        return sTypeface;
    }

    /**
     * Draws a glyph as a bitmap, which is the only way into a RemoteViews.
     * A coloured button also gets a tinted rounded background, so it reads as
     * deliberately different rather than just slightly off-white.
     */
    static Bitmap render(Context context, String glyph, int sizePx, int color, boolean standOut) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (standOut) {
            Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
            background.setColor(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)));
            float radius = sizePx * 0.22f;
            canvas.drawRoundRect(new android.graphics.RectF(0, 0, sizePx, sizePx), radius, radius, background);
        }

        paint.setTypeface(typeface(context));
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sizePx * 0.72f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(glyph, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2f, paint);
        return bmp;
    }

    static Bitmap render(Context context, String glyph, int sizePx, int color) {
        return render(context, glyph, sizePx, color, color != Color.WHITE);
    }

    /** Colours offered in the editor; the first follows the launcher's own accent. */
    static final int[] PALETTE = {
            Color.WHITE,
            Color.parseColor("#FF5252"),
            Color.parseColor("#FFB300"),
            Color.parseColor("#FFEE58"),
            Color.parseColor("#66BB6A"),
            Color.parseColor("#42A5F5"),
            Color.parseColor("#AB47BC"),
            Color.parseColor("#26C6DA"),
    };

    static final String[] PALETTE_NAMES = {
            "Default", "Red", "Amber", "Yellow", "Green", "Blue", "Purple", "Cyan",
    };

    static Bitmap render(Context context, String glyph, int sizePx) {
        return render(context, glyph, sizePx, Color.WHITE);
    }

    private RemoteButtons() {}
}
