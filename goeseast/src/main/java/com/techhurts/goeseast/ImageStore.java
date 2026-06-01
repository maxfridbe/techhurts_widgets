package com.techhurts.goeseast;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class ImageStore {

    private static final String DIR_NAME = "goeseast_frames";
    private static final long KEEP_MS = 24L * 60 * 60 * 1000;
    private static final int  JPEG_QUALITY = 82;

    static File getDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR_NAME);
        dir.mkdirs();
        return dir;
    }

    /** Save bitmap as timestamped JPEG and prune anything older than 24 h. */
    static void save(Context ctx, Bitmap bmp) {
        String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".jpg";
        File f = new File(getDir(ctx), name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
        } catch (IOException ignored) {}
        cleanup(ctx);
    }

    /** Chronologically sorted list of files from the last 24 h. */
    private static final String PREVIEW = "_preview.jpg";
    private static final int   PREVIEW_PX = 300;

    /** Save a small full-disk thumbnail used by the crop-config preview dialog. */
    static void savePreview(Context ctx, Bitmap fullDisk) {
        Bitmap scaled = Bitmap.createScaledBitmap(fullDisk, PREVIEW_PX, PREVIEW_PX, true);
        try (FileOutputStream fos = new FileOutputStream(new File(getDir(ctx), PREVIEW))) {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        } catch (IOException ignored) {} finally { scaled.recycle(); }
    }

    /** Returns the full-disk preview file, or null if none has been saved yet. */
    static File getPreview(Context ctx) {
        File f = new File(getDir(ctx), PREVIEW);
        return f.exists() ? f : null;
    }

    static List<File> getLast24h(Context ctx) {
        // Exclude the preview thumbnail (_prefix) from animation frames
        File[] all = getDir(ctx).listFiles((d, n) -> n.endsWith(".jpg") && !n.startsWith("_"));
        if (all == null || all.length == 0) return Collections.emptyList();
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        List<File> keep = new ArrayList<>();
        for (File f : all) if (f.lastModified() >= cutoff) keep.add(f);
        Collections.sort(keep, (a, b) -> a.getName().compareTo(b.getName()));
        return keep;
    }

    /** Most recently saved file, or null if none exists. */
    static File getLatest(Context ctx) {
        List<File> list = getLast24h(ctx);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    static void cleanup(Context ctx) {
        File[] all = getDir(ctx).listFiles();
        if (all == null) return;
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        for (File f : all)
            if (!f.getName().startsWith("_") && f.lastModified() < cutoff) f.delete();
    }
}
