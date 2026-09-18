package com.techhurts.goeseast;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves cached GOES East JPEG frames to the home-screen launcher via content:// URI.
 * Using setImageViewUri avoids the ~1 MB Binder IPC limit that caps setImageViewBitmap.
 *
 * URI format: content://com.techhurts.goeseast.images/{filename.jpg}
 *
 * Exported without any read permission — satellite imagery is public data.
 */
public class GoesEastContentProvider extends ContentProvider {

    /** Derived from the package, since this library ships in two apps. */
    public static String authority(android.content.Context context) {
        return context.getPackageName() + ".images";
    }

    /** Build a unique URI for a given frame file (filename encodes the timestamp). */
    static Uri uriForFile(android.content.Context context, File f) {
        return Uri.parse("content://" + authority(context) + "/" + f.getName());
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) throw new FileNotFoundException("no context");
        String path = uri.getPath();   // e.g. "/cache/goeseast.gif" or "/20260530_042000.jpg"

        // Serve generated GIF (or any file) from the app's cache dir
        if (path != null && path.startsWith("/cache/")) {
            File f = new File(getContext().getCacheDir(), path.substring("/cache/".length()));
            if (!f.exists()) throw new FileNotFoundException("cache file not found: " + f);
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        // Serve a stored frame by filename
        File f = new File(ImageStore.getDir(getContext()), uri.getLastPathSegment());
        if (!f.exists()) {
            File latest = ImageStore.getLatest(getContext());
            if (latest == null) throw new FileNotFoundException("no frames stored");
            f = latest;
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String p = uri.getPath();
        if (p != null && p.endsWith(".gif"))  return "image/gif";
        if (p != null && p.endsWith(".webp")) return "image/webp";
        if (p != null && p.endsWith(".mp4"))  return "video/mp4";
        return "image/jpeg";
    }

    // Unused stubs required by ContentProvider contract
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri    insert(Uri u, ContentValues v) { return null; }
    @Override public int    delete(Uri u, String s, String[] a) { return 0; }
    @Override public int    update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
