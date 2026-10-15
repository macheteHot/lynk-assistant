package com.geely.lynkassistant;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Access to the Geely/OneOS app-store provider that feeds the launcher's
 * "My Apps" (App List).
 *
 * <p>Verified by decompiling OneOS-AppStore.apk:
 * <pre>
 *   permission com.geely.appstore.APPSTORE_PROVIDER  protectionLevel = normal
 *   provider   authorities="com.geely.appstore.AppstoreProvider"  exported=true
 *   table      t_app_info(_id INTEGER PRIMARY KEY, package_name TEXT, apk_name TEXT,
 *                         apk_icon TEXT, apk_size TEXT, apk_version_name TEXT,
 *                         apk_version_code LONG, apk_type TEXT, apk_type_name TEXT,
 *                         display_screen TEXT)
 *   insert()   passes ContentValues straight to SQLiteDatabase.insert -> every
 *              column except _id is optional and the package name is not validated.
 *   delete()   on /appInfo runs delete(table, null, null) -> WIPES THE WHOLE TABLE.
 *              Always delete by /appInfo/&lt;_id&gt; .
 * </pre>
 */
public final class AppStoreBridge {

    public static final String PERMISSION = "com.geely.appstore.APPSTORE_PROVIDER";

    private static final Uri APPINFO =
            Uri.parse("content://com.geely.appstore.AppstoreProvider/appInfo");
    /** Read-only view maintained by the launcher itself (best effort). */
    private static final Uri LAUNCHER_SHORTCUTS =
            Uri.parse("content://com.android.launcher3.interactive/shortcutEntity");

    private AppStoreBridge() {
    }

    /** One row of t_app_info. */
    public static final class Row {
        public final long id;
        public final String packageName;
        public final String apkName;
        public final String typeName;

        Row(long id, String packageName, String apkName, String typeName) {
            this.id = id;
            this.packageName = packageName;
            this.apkName = apkName;
            this.typeName = typeName;
        }
    }

    public static boolean hasPermission(Context ctx) {
        return ctx.checkCallingOrSelfPermission(PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static List<Row> listWritten(Context ctx) {
        List<Row> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(APPINFO,
                    new String[]{"_id", "package_name", "apk_name", "apk_type_name"},
                    null, null, "_id ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    out.add(new Row(
                            c.getLong(0),
                            c.isNull(1) ? "" : c.getString(1),
                            c.isNull(2) ? "" : c.getString(2),
                            c.isNull(3) ? "" : c.getString(3)));
                }
            }
        } catch (Throwable t) {
            // provider missing / permission missing -> empty list
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    public static boolean isWritten(Context ctx, String pkg) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(APPINFO, new String[]{"_id"},
                    "package_name=?", new String[]{pkg}, null);
            return c != null && c.getCount() > 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** Insert (or refresh) one app. Returns null on success, else an error text. */
    public static String add(Context ctx, String pkg, String label, String typeName) {
        try {
            String versionName = "";
            long versionCode = 0;
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
                versionName = pi.versionName == null ? "" : pi.versionName;
                versionCode = Build.VERSION.SDK_INT >= 28
                        ? pi.getLongVersionCode() : pi.versionCode;
            } catch (Throwable ignored) {
                // uninstalled / hidden: keep empty version
            }

            ContentValues v = new ContentValues();
            v.put("package_name", pkg);
            v.put("apk_name", label);
            v.put("apk_version_name", versionName);
            v.put("apk_version_code", versionCode);
            v.put("apk_type", "0");
            v.put("apk_type_name", typeName);   // containing "游戏" -> launcher itemType=3
            v.put("display_screen", "0");
            // apk_icon intentionally omitted: TEXT column, the launcher falls back
            // to the installed app's icon.

            Uri r = ctx.getContentResolver().insert(APPINFO, v);
            return r == null ? "insert 返回 null" : null;
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /** Remove one row. MUST be by id: the no-id form clears the whole table. */
    public static String removeById(Context ctx, long id) {
        try {
            int n = ctx.getContentResolver().delete(
                    ContentUris.withAppendedId(APPINFO, id), null, null);
            return n > 0 ? null : "未删除任何行";
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /** Packages the launcher already stores; itemType 1 = asset whitelist, 2 = store. */
    public static List<String> launcherShortcutPackages(Context ctx) {
        List<String> out = new ArrayList<>();
        Cursor c = null;
        try {
            ContentResolver cr = ctx.getContentResolver();
            c = cr.query(LAUNCHER_SHORTCUTS, new String[]{"packageName"}, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    out.add(c.isNull(0) ? "" : c.getString(0));
                }
            }
        } catch (Throwable t) {
            // read-only, may be restricted; best effort only
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }
}
