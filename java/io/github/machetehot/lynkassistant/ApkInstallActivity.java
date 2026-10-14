package io.github.machetehot.lynkassistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Install an APK from local storage.
 *
 * <p>This cannot install silently: {@code INSTALL_PACKAGES} is
 * signature|privileged on this head unit. Instead the APK is copied into our
 * own cache and handed to the system package installer through a
 * {@code content://} URI — the user confirms the install in the system UI.
 */
public class ApkInstallActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int REQ_READ = 1002;
    private static final int MAX_SCAN = 200;

    private LinearLayout root;
    private LinearLayout listBox;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.install_title));

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setTextIsSelectable(true);
        root.addView(status);

        Button pick = new Button(this);
        pick.setText("选择 APK 文件…");
        pick.setAllCaps(false);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFile();
            }
        });
        root.addView(pick);

        Button rescan = new Button(this);
        rescan.setText("重新扫描本机 APK");
        rescan.setAllCaps(false);
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scan();
            }
        });
        root.addView(rescan);

        TextView head = new TextView(this);
        head.setText("本机找到的 APK（点一下安装）");
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        head.setPadding(0, dp(16), 0, dp(6));
        root.addView(head);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        TextView note = new TextView(this);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(16), 0, 0);
        note.setText("安装由系统安装器完成，需要在弹窗里确认。\n"
                + "若提示「不允许安装未知应用」，会先跳到系统设置里打开本应用的开关。\n"
                + "静默安装需要系统签名，普通应用做不到。");
        root.addView(note);

        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    "android.permission.READ_EXTERNAL_STORAGE"}, REQ_READ);
        } else {
            scan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        scan();
    }

    // --------------------------------------------------------------- picking

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/vnd.android.package-archive");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            // some head units have no document picker: fall back to */*
            Intent any = new Intent(Intent.ACTION_GET_CONTENT);
            any.setType("*/*");
            any.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(any, REQ_PICK);
            } catch (Exception e2) {
                toast("本机没有文件选择器，请用下面的扫描列表");
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String name = queryName(uri);
        try {
            File dst = new File(ApkFileProvider.dir(this), name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dst)) {
                if (in == null) {
                    throw new IllegalStateException("打不开所选文件");
                }
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            confirmInstall(dst);
        } catch (Throwable t) {
            toast("复制失败: " + t.getMessage());
        }
    }

    private String queryName(Uri uri) {
        String name = null;
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = c.getString(idx);
                }
            }
        } catch (Throwable ignored) {
            // fall through
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (name == null) {
            String p = uri.getLastPathSegment();
            name = (p == null ? "picked" : p.replace('/', '_'));
        }
        if (!name.toLowerCase().endsWith(".apk")) {
            name = name + ".apk";
        }
        return name;
    }

    // -------------------------------------------------------------- scanning

    private void scan() {
        listBox.removeAllViews();
        status.setText("正在扫描…");
        final List<File> found = new ArrayList<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Set<String> seen = new LinkedHashSet<>();
                for (String d : scanDirs()) {
                    walk(new File(d), 0, 2, found, seen);
                    if (found.size() >= MAX_SCAN) {
                        break;
                    }
                }
                Collections.sort(found, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFound(found);
                    }
                });
            }
        }, "apk-scan").start();
    }

    private List<String> scanDirs() {
        List<String> dirs = new ArrayList<>();
        dirs.add(Environment.getExternalStorageDirectory() + "/Download");
        dirs.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        File ext = getExternalFilesDir(null);
        if (ext != null) {
            dirs.add(ext.getAbsolutePath());
        }
        dirs.add("/mnt/media_rw/usb0");
        dirs.add("/mnt/media_rw/usb1");
        dirs.add("/storage/usb0");
        dirs.add("/storage/usb1");
        dirs.add("/storage/usb2");
        return dirs;
    }

    private void walk(File dir, int depth, int maxDepth, List<File> out, Set<String> seen) {
        if (dir == null || depth > maxDepth || out.size() >= MAX_SCAN) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (out.size() >= MAX_SCAN) {
                return;
            }
            if (f.isDirectory()) {
                walk(f, depth + 1, maxDepth, out, seen);
            } else if (f.getName().toLowerCase().endsWith(".apk")
                    && seen.add(f.getAbsolutePath())) {
                out.add(f);
            }
        }
    }

    private void showFound(List<File> files) {
        status.setText("找到 " + files.size() + " 个 APK");
        if (files.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("（没找到。可以把 APK 放到 U 盘或 Download 目录，再点重新扫描）");
            t.setTextColor(Color.GRAY);
            listBox.addView(t);
            return;
        }
        for (final File f : files) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setText(describe(f));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmInstall(f);
                }
            });
            listBox.addView(b);
        }
    }

    private String describe(File f) {
        String info = humanSize(f.length());
        PackageInfo pi = getPackageManager()
                .getPackageArchiveInfo(f.getAbsolutePath(), 0);
        if (pi != null) {
            CharSequence label = pi.applicationInfo == null
                    ? null : pi.applicationInfo.loadLabel(getPackageManager());
            info = (label == null ? "" : label + "  ") + pi.packageName
                    + "  v" + pi.versionName + "  " + info;
        }
        return f.getName() + "\n" + info;
    }

    // ------------------------------------------------------------- installing

    private void confirmInstall(final File apk) {
        String msg = "文件: " + apk.getAbsolutePath() + "\n\n"
                + "将由系统安装器打开安装界面，需要你确认。";
        new AlertDialog.Builder(this)
                .setTitle("安装 " + apk.getName())
                .setMessage(msg)
                .setPositiveButton("安装", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        doInstall(apk);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doInstall(File apk) {
        // The system installer must be able to read the file: make sure it is
        // inside our provider-visible cache directory.
        File inCache = new File(ApkFileProvider.dir(this), apk.getName());
        try {
            if (!inCache.getCanonicalPath().equals(apk.getCanonicalPath())) {
                copy(apk, inCache);
            }
        } catch (Throwable t) {
            toast("准备文件失败: " + t.getMessage());
            return;
        }

        if (Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要允许安装未知应用")
                    .setMessage("本应用还没有「安装未知应用」权限。\n"
                            + "点确定跳到系统设置，打开「" + getString(R.string.app_name)
                            + "」的开关后再回来安装。")
                    .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                startActivity(new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) {
                                startActivity(new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
                            }
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(ApkFileProvider.uriFor(inCache.getName()),
                    "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("无法打开系统安装器: " + e.getClass().getSimpleName());
        }
    }

    private void copy(File src, File dst) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
