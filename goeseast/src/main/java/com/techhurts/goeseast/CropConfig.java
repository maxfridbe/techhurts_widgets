package com.techhurts.goeseast;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Stores and applies the user-configured crop region.
 *
 * The region is described by:
 *   zoom  — how much to zoom in (1× = full disk, 2× = 50% of width/height visible, etc.)
 *   xctr  — horizontal centre of the visible region as a fraction of full-disk width  (0–1)
 *   yctr  — vertical centre                                                           (0–1)
 *
 * Default (1.67×, 0.30, 0.30) = the original hard-coded 60 % × 60 % top-left crop.
 */
class CropConfig {

    static final float DEF_ZOOM = 1.67f;  // 60 % view
    static final float DEF_XCTR = 0.30f;
    static final float DEF_YCTR = 0.30f;

    static float getZoom(Context ctx) { return prefs(ctx).getFloat("zoom", DEF_ZOOM); }
    static float getX(Context ctx)    { return prefs(ctx).getFloat("xctr", DEF_XCTR); }
    static float getY(Context ctx)    { return prefs(ctx).getFloat("yctr", DEF_YCTR); }

    static void save(Context ctx, float zoom, float x, float y) {
        prefs(ctx).edit()
                .putFloat("zoom", zoom).putFloat("xctr", x).putFloat("yctr", y)
                .apply();
    }

    /** Crop + timestamp-burn using the persisted config. Input bitmap is recycled. */
    static Bitmap cropAndBurn(Bitmap src, Context ctx, long tsMs) {
        return cropAndBurn(src, getZoom(ctx), getX(ctx), getY(ctx), tsMs);
    }

    /**
     * Crop a full-disk bitmap to the specified region and burn the timestamp
     * into the bottom-right corner.  The input bitmap is recycled.
     */
    static Bitmap cropAndBurn(Bitmap src, float zoom, float xctr, float yctr, long tsMs) {
        int sw = src.getWidth(), sh = src.getHeight();
        float half = 0.5f / zoom;
        int x0 = Math.max(0,  (int)((xctr - half) * sw));
        int y0 = Math.max(0,  (int)((yctr - half) * sh));
        int x1 = Math.min(sw, (int)((xctr + half) * sw));
        int y1 = Math.min(sh, (int)((yctr + half) * sh));
        int cw = Math.max(2, x1 - x0), ch = Math.max(2, y1 - y0);

        Bitmap out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(src, -x0, -y0, null);
        src.recycle();

        // Burn timestamp — white text with dark outline for legibility on any background
        if (tsMs > 0) {
            String ts = new SimpleDateFormat("MM/dd HH:mm", Locale.US).format(new Date(tsMs));
            float sz  = cw * 0.036f;
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setTextSize(sz);
            stroke.setColor(0xCC000000);
            stroke.setTextAlign(Paint.Align.RIGHT);
            stroke.setStyle(Paint.Style.FILL_AND_STROKE);
            stroke.setStrokeWidth(sz * 0.28f);
            Paint white = new Paint(stroke);
            white.setColor(0xFFFFFFFF);
            white.setStyle(Paint.Style.FILL);
            float tx = cw - sz * 0.4f, ty = ch - sz * 0.4f;
            canvas.drawText(ts, tx, ty, stroke);
            canvas.drawText(ts, tx, ty, white);
        }
        return out;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(ctx.getPackageName() + ".crop", 0);
    }
}
