package com.techhurts.goeseast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Shows the full-disk satellite image with:
 *  - a semi-transparent dark overlay on the area OUTSIDE the crop
 *  - a bright red rectangle on the crop boundary
 * Updates instantly as zoom / centre sliders change.
 */
class CropPreviewView extends View {

    private Bitmap mBitmap;
    private float  mZoom, mXctr, mYctr;

    private final Paint mImg = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mDim = new Paint();
    private final Paint mBox = new Paint();

    CropPreviewView(Context ctx, float zoom, float xctr, float yctr) {
        super(ctx);
        mZoom = zoom; mXctr = xctr; mYctr = yctr;

        mDim.setColor(0xAA000000);
        mDim.setStyle(Paint.Style.FILL);

        mBox.setColor(Color.RED);
        mBox.setStyle(Paint.Style.STROKE);
        mBox.setStrokeWidth(4f);
        mBox.setAntiAlias(true);
    }

    void setBitmap(Bitmap bmp) { mBitmap = bmp; invalidate(); }

    void setCrop(float zoom, float xctr, float yctr) {
        mZoom = zoom; mXctr = xctr; mYctr = yctr; invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth(), vh = getHeight();

        if (mBitmap == null) {
            canvas.drawColor(0xFF222222);
            return;
        }

        // Full-disk image stretched to fill the view
        canvas.drawBitmap(mBitmap, null, new RectF(0, 0, vw, vh), mImg);

        // Crop rectangle in view-space
        float half = 0.5f / mZoom;
        float l = Math.max(0f, mXctr - half) * vw;
        float t = Math.max(0f, mYctr - half) * vh;
        float r = Math.min(1f, mXctr + half) * vw;
        float b = Math.min(1f, mYctr + half) * vh;

        // Dim the four strips outside the selection
        canvas.drawRect(0, 0, vw, t, mDim);      // top
        canvas.drawRect(0, b, vw, vh, mDim);     // bottom
        canvas.drawRect(0, t, l, b, mDim);       // left
        canvas.drawRect(r, t, vw, b, mDim);      // right

        // Red crop border
        canvas.drawRect(l, t, r, b, mBox);
    }
}
