package com.geely.lynkassistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Uninstall / disable / inspect installed applications.
 *
 * <p>What a normal app can actually do on this head unit (verified against
 * framework-res.apk):
 * <ul>
 *   <li>uninstall — {@code ACTION_DELETE}, resolved by the system UI. OK.</li>
 *   <li>disable — needs {@code CHANGE_COMPONENT_ENABLED_STATE}, which is
 *       {@code signature|privileged} (0x12). Not obtainable; the attempt is
 *       kept so it works if the app is ever built as a privileged/system app,
 *       and the exact adb command is shown when it is denied.</li>
 * </ul>
 *
 * <p>UI: left column = filter + app list, right column = help.
 */
public class AppManagerActivity extends Activity {

    private static final int MAX_ITEMS = 400;

    private LinearLayout listBox;
    private TextView status;
    private EditText search;
    private CheckBox userOnly;

    private final List<ApplicationInfo> all = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.Screen sc = Ui.create(this, getString(R.string.app_manager_title), 1.35f, 1f);
        setContentView(sc.root);
        LinearLayout left = sc.left.body;
        LinearLayout right = sc.right.body;

        // ---- left: filter + list ----
        left.addView(Ui.label(this, "筛选"));
        search = new EditText(this);
        search.setHint("搜索应用名或包名");
        search.setSingleLine(true);
        search.setTextSize(20);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.TEXT_DIM);
        search.setBackground(Ui.round(this, Ui.SURFACE_ALT, 10, Ui.LINE));
        int sp = Ui.dp(this, 12);
        search.setPadding(sp, sp, sp, sp);
        left.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        userOnly = new CheckBox(this);
        userOnly.setText("只看用户应用（可卸载的）");
        userOnly.setTextSize(20);
        userOnly.setTextColor(Ui.TEXT);
        userOnly.setChecked(false);
        userOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                render();
            }
        });
        left.addView(userOnly);

        status = Ui.dim(this, "");
        left.addView(status);
        left.addView(Ui.gap(this, 6));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        left.addView(listBox);

        // ---- right: help ----
        right.addView(Ui.label(this, "说明"));
        LinearLayout note = Ui.card(this);
        note.addView(Ui.dim(this,
                "「禁用」需要系统签名权限（CHANGE_COMPONENT_ENABLED_STATE 为 "
                        + "signature|privileged），普通应用做不到，点它会给出可用 adb 执行的命令。\n\n"
                        + "「从桌面移除」只删除车机商店记录里该应用的条目，不卸载应用。\n\n"
                        + "点列表中的应用可进行：卸载 / 禁用 / 应用详情 / 从桌面记录移除 / 复制 adb 命令。"));
        right.addView(note);

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!all.isEmpty()) {
            render();
        }
    }

    // ------------------------------------------------------------------ data

    private void load() {
        status.setText("正在读取应用列表…");
        final PackageManager pm = getPackageManager();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<ApplicationInfo> list =
                        pm.getInstalledApplications(PackageManager.GET_META_DATA);
                Collections.sort(list, new Comparator<ApplicationInfo>() {
                    @Override
                    public int compare(ApplicationInfo a, ApplicationInfo b) {
                        return a.loadLabel(pm).toString()
                                .compareToIgnoreCase(b.loadLabel(pm).toString());
                    }
                });
                synchronized (all) {
                    all.clear();
                    all.addAll(list);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        render();
                    }
                });
            }
        }, "app-list").start();
    }

    private boolean isSystem(ApplicationInfo ai) {
        return (ai.flags & (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private void render() {
        List<ApplicationInfo> snapshot;
        synchronized (all) {
            snapshot = new ArrayList<>(all);
        }
        String q = search.getText().toString().trim().toLowerCase();
        boolean onlyUser = userOnly.isChecked();
        PackageManager pm = getPackageManager();

        listBox.removeAllViews();
        int shown = 0;
        for (ApplicationInfo ai : snapshot) {
            if (onlyUser && isSystem(ai)) {
                continue;
            }
            String label = ai.loadLabel(pm).toString();
            if (!q.isEmpty()
                    && !label.toLowerCase().contains(q)
                    && !ai.packageName.toLowerCase().contains(q)) {
                continue;
            }
            if (shown >= MAX_ITEMS) {
                break;
            }
            shown++;
            Ui.addRow(listBox, item(ai, label));
        }
        status.setText("共 " + snapshot.size() + " 个应用，显示 " + shown + " 个");
    }

    private View item(final ApplicationInfo ai, String label) {
        final PackageManager pm = getPackageManager();
        String version = "";
        try {
            version = "v" + pm.getPackageInfo(ai.packageName, 0).versionName;
        } catch (Throwable ignored) {
            // ignore
        }
        String state = ai.enabled ? "" : "  [已禁用]";
        String text = label + (isSystem(ai) ? "  [系统]" : "") + state
                + "\n" + ai.packageName + "  " + version;
        return Ui.row(this, text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions(ai);
            }
        });
    }

    // --------------------------------------------------------------- actions

    private void actions(final ApplicationInfo ai) {
        final String pkg = ai.packageName;
        List<String> items = new ArrayList<>();
        items.add("卸载");
        items.add(ai.enabled ? "禁用（需系统权限）" : "启用（需系统权限）");
        items.add("应用详情");
        items.add("从桌面记录移除");
        items.add("复制 adb 命令");

        new AlertDialog.Builder(this)
                .setTitle(ai.loadLabel(getPackageManager()) + "\n" + pkg)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                uninstall(pkg);
                                break;
                            case 1:
                                toggleEnabled(ai);
                                break;
                            case 2:
                                details(pkg);
                                break;
                            case 3:
                                removeDesktopRecord(pkg);
                                break;
                            default:
                                copyAdb(pkg);
                                break;
                        }
                    }
                })
                .show();
    }

    private void uninstall(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            toast("无法打开卸载界面: " + e.getClass().getSimpleName());
        }
    }

    private void details(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            toast("无法打开应用详情: " + e.getClass().getSimpleName());
        }
    }

    private void toggleEnabled(ApplicationInfo ai) {
        int desired = ai.enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        try {
            getPackageManager().setApplicationEnabledSetting(ai.packageName, desired, 0);
            toast("已" + (ai.enabled ? "禁用" : "启用") + ": " + ai.packageName);
            load();
        } catch (SecurityException e) {
            new AlertDialog.Builder(this)
                    .setTitle("系统权限不足")
                    .setMessage("禁用/启用应用需要系统签名权限：\n"
                            + "android.permission.CHANGE_COMPONENT_ENABLED_STATE\n"
                            + "（protectionLevel = signature|privileged）\n\n"
                            + "普通应用无法获得。可以在电脑上用 adb 执行：\n\n"
                            + adbCmd(ai.packageName, ai.enabled))
                    .setPositiveButton("复制命令", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            copyAdb(ai.packageName);
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            toast("失败: " + t.getClass().getSimpleName());
        }
    }

    private String adbCmd(String pkg, boolean currentlyEnabled) {
        return currentlyEnabled
                ? "adb shell pm disable-user --user 0 " + pkg
                : "adb shell pm enable " + pkg;
    }

    private void copyAdb(String pkg) {
        boolean enabled = true;
        try {
            enabled = getPackageManager()
                    .getApplicationInfo(pkg, 0).enabled;
        } catch (Throwable ignored) {
            // ignore
        }
        String cmd = adbCmd(pkg, enabled);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("adb", cmd));
            toast("已复制: " + cmd);
        }
    }

    private void removeDesktopRecord(String pkg) {
        List<AppStoreBridge.Row> rows = AppStoreBridge.listWritten(this);
        final List<AppStoreBridge.Row> hits = new ArrayList<>();
        for (AppStoreBridge.Row r : rows) {
            if (pkg.equals(r.packageName)) {
                hits.add(r);
            }
        }
        if (hits.isEmpty()) {
            toast("桌面记录里没有这个应用");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("从桌面记录移除")
                .setMessage("删除 t_app_info 中 " + pkg + " 的 " + hits.size() + " 条记录？\n"
                        + "应用本身不会被卸载。需重启车机后桌面才刷新。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int ok = 0;
                        for (AppStoreBridge.Row r : hits) {
                            if (AppStoreBridge.removeById(AppManagerActivity.this, r.id) == null) {
                                ok++;
                            }
                        }
                        toast("已移除 " + ok + " 条");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
