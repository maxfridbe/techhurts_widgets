package com.techhurts.himawari8;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;

class HimawariService {

    static String fetchLatestImageUrl() {
        return HimawariFetchLogic.fetchLatestImageUrl();
    }

    /**
     * Download the full-disk JPEG (~21 MB) and decode at auto-chosen inSampleSize
     * so the result is ≤1500 px on the long edge.  No crop — CropConfig handles that.
     */
    static Bitmap downloadFull(String url) throws IOException {
        byte[] data = HimawariFetchLogic.downloadImageBytes(url);

        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, probe);

        int longEdge = Math.max(probe.outWidth, probe.outHeight);
        int sample = 1;
        while (longEdge / sample > 1500) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (bmp == null) throw new IOException("BitmapFactory failed for " + url);
        return bmp;
    }
}
