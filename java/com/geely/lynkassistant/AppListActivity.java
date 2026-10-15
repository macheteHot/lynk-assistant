package com.geely.lynkassistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic "put an app into the launcher's App List" tool.
 *
 * <p>Deliberately NOT hardcoded to one package: the app-store provider
 * permission is normal-level and its {@code insert()} validates nothing, so any
 * installed app can be registered. A fixed package name would simply break the
 * moment the app is renamed or reinstalled.
 *
 * <p>UI: left column = apps not yet written, right column = existing records.
 */
public class AppListActivity extends Activity {

    private static final String[] CATEGORIES = {
            "常用工具", "影音娱乐", "系统工具", "游戏"
    };

    private static final String REBOOT_HINT =
            "请重启车机使其生效。\n\n"
                    + "车机可以用硬件按键强制重启（不同车型组合不同，常见为长按电源键或"
                    + "音量键组合）。重启后新应用会出现在桌面「我的应用」里。";

    private TextView status;
    private LinearLayout pendingBox;
    private LinearLayout writtenBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.Screen sc = Ui.create(this, getString(R.string.app_list_title), 1f, 1.1f);
        setContentView(sc.root);
        LinearLayout left = sc.left.body;
        LinearLayout right = sc.right.body;

        // ---- left: control + pending list ----
        left.addView(Ui.label(this, "写入桌面"));
        Ui.addPair(left,
                Ui.ghost(this, "刷新", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        refresh();
                    }
                }),
                Ui.primary(this, "写入全部未写入", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        writeAllMissing();
                    }
                }));

        status = Ui.dim(this, "");
        left.addView(status);
        left.addView(Ui.gap(this, 10));
        left.addView(Ui.label(this, "未写入桌面的应用（点 ＋ 加入）"));
        pendingBox = new LinearLayout(this);
        pendingBox.setOrientation(LinearLayout.VERTICAL);
        left.addView(pendingBox);

        // ---- right: existing records ----
        right.addView(Ui.label(this, "已写入 t_app_info 的记录（按 _id 删除）"));
        writtenBox = new LinearLayout(this);
        writtenBox.setOrientation(LinearLayout.VERTICAL);
        right.addView(writtenBox);

        right.addView(Ui.gap(this, 14));
        LinearLayout note = Ui.card(this);
        note.addView(Ui.dim(this, "写入的是车机自带应用商店的数据库记录（t_app_info）。\n"
                + "应用商店同步/刷新时可能清理这些记录，届时重新写入即可。\n"
                + "删除只按 _id 单条删，绝不清空整表。"));
        right.addView(note);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ------------------------------------------------------------------ data

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        boolean perm = AppStoreBridge.hasPermission(this);
        sb.append("权限 ").append(AppStoreBridge.PERMISSION).append(": ")
                .append(perm ? "已授予" : "未授予（无法读写）").append('\n');

        List<AppStoreBridge.Row> written = AppStoreBridge.listWritten(this);
        Set<String> writtenPkgs = new HashSet<>();
        for (AppStoreBridge.Row r : written) {
            writtenPkgs.add(r.packageName);
        }
        sb.append("t_app_info 记录数: ").append(written.size()).append('\n');

        List<String> launcherPkgs = AppStoreBridge.launcherShortcutPackages(this);
        sb.append("桌面 shortcutEntity: ").append(launcherPkgs.isEmpty()
                ? "读取不到（只读接口，属正常）" : launcherPkgs.size() + " 条");
        status.setText(sb.toString());

        List<AppEntry> apps = installedLauncherApps();
        pendingBox.removeAllViews();
        writtenBox.removeAllViews();

        int pending = 0;
        for (final AppEntry a : apps) {
            if (writtenPkgs.contains(a.pkg)) {
                continue;
            }
            pending++;
            Ui.addRow(pendingBox, Ui.row(this,
                    "＋ " + a.label + "\n" + a.pkg,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            chooseCategoryAndAdd(a);
                        }
                    }));
        }
        if (pending == 0) {
            pendingBox.addView(Ui.dim(this, "（全部已写入）"));
        }

        if (written.isEmpty()) {
            writtenBox.addView(Ui.dim(this, "（暂无记录）"));
        }
        for (final AppStoreBridge.Row r : written) {
            Ui.addRow(writtenBox, Ui.row(this,
                    "删除  " + safe(r.apkName) + "\n" + safe(r.packageName) + "   _id=" + r.id,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            confirmRemove(r);
                        }
                    }));
        }
    }

    private static final class AppEntry {
        final String pkg;
        final String label;

        AppEntry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    private List<AppEntry> installedLauncherApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        List<AppEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (ris == null) {
            return out;
        }
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            CharSequence label = ri.loadLabel(pm);
            out.add(new AppEntry(pkg, label == null ? pkg : label.toString()));
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    // --------------------------------------------------------------- actions

    private void chooseCategoryAndAdd(final AppEntry a) {
        new AlertDialog.Builder(this)
                .setTitle("分类: " + a.label)
                .setItems(CATEGORIES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String err = AppStoreBridge.add(AppListActivity.this,
                                a.pkg, a.label, CATEGORIES[which]);
                        if (err == null) {
                            showRebootDialog("已写入: " + a.label + "\n\n" + REBOOT_HINT);
                        } else {
                            toast("失败: " + err);
                        }
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void writeAllMissing() {
        List<AppEntry> apps = installedLauncherApps();
        int ok = 0;
        int fail = 0;
        for (AppEntry a : apps) {
            if (AppStoreBridge.isWritten(this, a.pkg)) {
                continue;
            }
            if (AppStoreBridge.add(this, a.pkg, a.label, CATEGORIES[0]) == null) {
                ok++;
            } else {
                fail++;
            }
        }
        if (ok > 0) {
            showRebootDialog("已写入 " + ok + " 个应用"
                    + (fail > 0 ? "，失败 " + fail + " 个" : "") + "。\n\n" + REBOOT_HINT);
        } else {
            toast(fail > 0 ? "写入失败 " + fail + " 个" : "没有需要写入的应用");
        }
        refresh();
    }

    private void showRebootDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("需要重启车机")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void confirmRemove(final AppStoreBridge.Row r) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("从 t_app_info 删除 " + safe(r.packageName)
                        + "（_id=" + r.id + "）？\n只删这一条。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String err = AppStoreBridge.removeById(AppListActivity.this, r.id);
                        toast(err == null ? "已删除（重启车机后从桌面消失）" : "失败: " + err);
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // --------------------------------------------------------------- helpers

    private static String safe(String s) {
        return TextUtils.isEmpty(s) ? "(空)" : s;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
