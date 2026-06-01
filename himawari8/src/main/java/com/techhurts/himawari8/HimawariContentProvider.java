package com.techhurts.himawari8;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public class HimawariContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.techhurts.himawari8.images";

    static Uri uriForFile(File f) {
        return Uri.parse("content://" + AUTHORITY + "/" + f.getName());
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) throw new FileNotFoundException("no context");
        String path = uri.getPath();
        if (path != null && path.startsWith("/cache/")) {
            File f = new File(getContext().getCacheDir(), path.substring("/cache/".length()));
            if (!f.exists()) throw new FileNotFoundException("cache file not found");
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        }
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
        if (p != null && p.endsWith(".gif")) return "image/gif";
        if (p != null && p.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
    @Override public Cursor  query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri     insert(Uri u, ContentValues v) { return null; }
    @Override public int     delete(Uri u, String s, String[] a) { return 0; }
    @Override public int     update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
