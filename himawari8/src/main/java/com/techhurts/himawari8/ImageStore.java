package com.techhurts.himawari8;

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
    private static final String DIR = "himawari_frames";
    private static final long KEEP_MS = 24L * 60 * 60 * 1000;

    static File getDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), DIR); d.mkdirs(); return d;
    }

    static void save(Context ctx, Bitmap bmp) {
        String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        try (FileOutputStream fos = new FileOutputStream(new File(getDir(ctx), name))) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        } catch (IOException ignored) {}
        cleanup(ctx);
    }

    private static final String PREVIEW = "_preview.jpg";
    private static final int   PREVIEW_PX = 300;

    static void savePreview(Context ctx, Bitmap fullDisk) {
        Bitmap scaled = Bitmap.createScaledBitmap(fullDisk, PREVIEW_PX, PREVIEW_PX, true);
        try (FileOutputStream fos = new FileOutputStream(new File(getDir(ctx), PREVIEW))) {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        } catch (IOException ignored) {} finally { scaled.recycle(); }
    }

    static File getPreview(Context ctx) {
        File f = new File(getDir(ctx), PREVIEW);
        return f.exists() ? f : null;
    }

    static List<File> getLast24h(Context ctx) {
        File[] all = getDir(ctx).listFiles((d, n) -> n.endsWith(".jpg") && !n.startsWith("_"));
        if (all == null || all.length == 0) return Collections.emptyList();
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        List<File> keep = new ArrayList<>();
        for (File f : all) if (f.lastModified() >= cutoff) keep.add(f);
        Collections.sort(keep, (a, b) -> a.getName().compareTo(b.getName()));
        return keep;
    }

    static File getLatest(Context ctx) {
        List<File> l = getLast24h(ctx);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    static void cleanup(Context ctx) {
        File[] all = getDir(ctx).listFiles();
        if (all == null) return;
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        for (File f : all)
            if (!f.getName().startsWith("_") && f.lastModified() < cutoff) f.delete();
    }
}
