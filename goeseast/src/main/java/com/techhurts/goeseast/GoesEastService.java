package com.techhurts.goeseast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;

class GoesEastService {

    static String fetchLatestImageUrl() {
        return GoesEastFetchLogic.fetchLatestImageUrl();
    }

    /**
     * Download the 1808×1808 full-disk JPEG and decode at inSampleSize=2 (904×904).
     * No crop is applied here — CropConfig.cropAndBurn() handles that at display time,
     * allowing the user to change the region without re-downloading.
     */
    static Bitmap downloadFull(String url) throws IOException {
        byte[] data = GoesEastFetchLogic.downloadImageBytes(url);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2; // 1808 → 904 px, peak ~3.3 MB raw
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (bmp == null) throw new IOException("BitmapFactory failed for " + url);
        return bmp;
    }
}
