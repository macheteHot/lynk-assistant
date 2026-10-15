package com.geely.lynkassistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal file provider (no androidx) used to hand an APK to the system package
 * installer as a {@code content://} URI.
 *
 * <p>Android 7+ forbids {@code file://} URIs across processes, and FileProvider
 * lives in androidx which this project deliberately does not depend on. Only
 * files inside our own cache sub-directory are served, and path traversal is
 * rejected; the provider is not exported and is only reachable through a
 * per-intent read grant.
 */
public class ApkFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.geely.lynkassistant.files";
    private static final String DIR = "apk";

    /** Directory APKs are copied into before installing. */
    public static File dir(Context ctx) {
        File d = new File(ctx.getCacheDir(), DIR);
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    public static Uri uriFor(String fileName) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(DIR)
                .appendPath(fileName)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        java.util.List<String> segs = uri.getPathSegments();
        if (segs == null || segs.size() != 2 || !DIR.equals(segs.get(0))) {
            throw new FileNotFoundException("bad uri: " + uri);
        }
        String name = segs.get(1);
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("bad name: " + name);
        }
        File f = new File(dir(getContext()), name);
        if (!f.isFile()) {
            throw new FileNotFoundException("missing: " + name);
        }
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }
}
