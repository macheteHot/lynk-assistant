package com.geely.lynkassistant;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 领克助手 — helper for Geely / Lynk & Co (Flyme Auto / OneOS) head units.
 *
 * <p>Everything here works with ordinary app permissions only:
 * <ul>
 *   <li>the engineering-mode app declares an exported MAIN/LAUNCHER activity;</li>
 *   <li>the app-store provider that feeds the launcher's "My Apps" list is
 *       protected by a normal-level permission.</li>
 * </ul>
 * Nothing here escalates privileges or modifies system properties.
 */
public class MainActivity extends Activity {

    private static final String ENG_PKG = "com.desaysv.engmode";
    private static final String PLUGIN_PKG = "com.desaysv.engmode.custom";

    /** name -> class, probed against ENG_PKG. */
    private static final String[][] PAGES = {
            {"MainActivity (工程模式主菜单)", "com.desaysv.engmode.MainActivity"},
            {"VersionInfoActivity 版本信息", "com.desaysv.engmode.ui.VersionInfoActivity"},
            {"AppVersionActivity APP版本", "com.desaysv.engmode.ui.AppVersionActivity"},
            {"ThirdActivity 其他升级", "com.desaysv.engmode.ui.ThirdActivity"},
            {"AllUpdateActivity 全部升级", "com.desaysv.engmode.ui.AllUpdateActivity"},
            {"GpsUpdateActivity GPS升级", "com.desaysv.engmode.ui.GpsUpdateActivity"},
            {"VRUpdateActivity VR升级", "com.desaysv.engmode.ui.VRUpdateActivity"},
            {"TBOXUpdateActivity TBOX升级", "com.desaysv.engmode.ui.TBOXUpdateActivity"},
            {"ScreenTestActivity 屏幕显示测试", "com.desaysv.engmode.ui.ScreenTestActivity"},
            {"ScreenTouchTestActivity 触摸测试", "com.desaysv.engmode.ui.ScreenTouchTestActivity"},
            {"GpsActivity GPS信息", "com.desaysv.engmode.ui.GpsActivity"},
            {"MicActivity 麦克风测试", "com.desaysv.engmode.ui.MicActivity"},
            {"BurnActivity 煲机模式", "com.desaysv.engmode.ui.BurnActivity"},
            {"BoardActivity 按键测试", "com.desaysv.engmode.ui.BoardActivity"},
            {"ExhibitionActivity 展车模式", "com.desaysv.engmode.ui.ExhibitionActivity"},
            {"StrModeActivity STR模式", "com.desaysv.engmode.ui.StrModeActivity"},
            {"DTCActivity DTC", "com.desaysv.engmode.ui.DTCActivity"},
            {"FileBrowseActivity 文件浏览", "com.desaysv.engmode.ui.FileBrowseActivity"},
            {"AdbActivity Log开关", "com.desaysv.engmode.ui.AdbActivity"},
            {"TemperatureActivity 温度信息", "com.desaysv.engmode.ui.TemperatureActivity"},
            {"EthernetActivity 以太网测试", "com.desaysv.engmode.ui.EthernetActivity"},
            {"TunerActivity Tuner", "com.desaysv.engmode.ui.TunerActivity"},
            {"WifiActivity WIFI管理", "com.desaysv.engmode.ui.WifiActivity"},
            {"BtActivity 蓝牙测试", "com.desaysv.engmode.ui.BtActivity"},
            {"SourcesActivity Sources测试", "com.desaysv.engmode.ui.SourcesActivity"},
            {"UsbActivity USB测试", "com.desaysv.engmode.ui.UsbActivity"},
            {"USBAuthActivity USB2.0认证", "com.desaysv.engmode.ui.USBAuthActivity"},
            {"CameraActivity 摄像头测试", "com.desaysv.engmode.ui.CameraActivity"},
            {"SpeakerTestActivity 扬声器测试", "com.desaysv.engmode.ui.SpeakerTestActivity"},
            {"CharacterTestingActivity 字符测试", "com.desaysv.engmode.ui.CharacterTestingActivity"},
            {"SelfDiagActivity 自诊断", "com.desaysv.engmode.ui.SelfDiagActivity"},
            {"BatteryInfoActivity 电池信息", "com.desaysv.engmode.ui.BatteryInfoActivity"},
            {"LimitTestActivity 极限测试", "com.desaysv.engmode.ui.LimitTestActivity"},
            {"OfflineConfigActivity 离线配置", "com.desaysv.engmode.ui.OfflineConfigActivity"},
            {"TboxInfoActivity TBOX信息", "com.desaysv.engmode.ui.TboxInfoActivity"},
            {"RvcActivity 倒车影像", "com.desaysv.engmode.ui.RvcActivity"},
            {"DspActivity DSP", "com.desaysv.engmode.ui.DspActivity"},
            {"SoundActivity 音效", "com.desaysv.engmode.ui.SoundActivity"},
            {"CarPlayActivity CarPlay测试", "com.desaysv.engmode.ui.CarPlayActivity"},
            {"IPodActivity IPod", "com.desaysv.engmode.ui.IPodActivity"},
            {"FullScreenActivity 全屏媒体", "com.desaysv.engmode.ui.FullScreenActivity"},
            {"OtherActivity 其他", "com.desaysv.engmode.ui.OtherActivity"},
            {"PluginStubActivitySingleTask0 (插件占位)", "com.desaysv.engmode.stub.PluginStubActivitySingleTask0"},
            {"PluginStubActivityS0 (插件占位)", "com.desaysv.engmode.stub.PluginStubActivityS0"},
    };

    private LinearLayout root;
    private TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.app_name));

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        header(getString(R.string.app_name) + "  ·  Geely / Lynk & Co 车机助手");

        addButton("① 进入工程模式", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEngineeringMode();
            }
        });

        addButton("② 扫描工程模式页面 (exported)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanPages();
            }
        });

        addButton("③ 桌面应用列表管理", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AppListActivity.class));
            }
        });

        addButton("④ 设备信息", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DeviceInfoActivity.class));
            }
        });

        addButton("⑤ 打开系统设置", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    toast("打不开系统设置: " + e.getClass().getSimpleName());
                }
            }
        });

        addButton("⑥ 安装 APK", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ApkInstallActivity.class));
            }
        });

        addButton("⑦ 应用管理（卸载 / 禁用）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AppManagerActivity.class));
            }
        });

        log = new TextView(this);
        log.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        log.setTextIsSelectable(true);
        log.setPadding(0, dp(12), 0, 0);
        root.addView(log);

        log.append("只有 exported=true 的页面才能被普通应用拉起；\n"
                + "工程模式内部页面默认不导出，需从工程模式菜单进入。\n");
    }

    // ---------------------------------------------------------------- actions

    private void openEngineeringMode() {
        clear();
        say("== 进入工程模式 ==");
        boolean ok = false;

        ok |= tryStart(new ComponentName(ENG_PKG, ENG_PKG + ".MainActivity"), "显式组件 MainActivity");

        Intent launch = getPackageManager().getLaunchIntentForPackage(ENG_PKG);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ok |= tryIntent(launch, "getLaunchIntentForPackage");
        } else {
            say("  getLaunchIntentForPackage -> null");
        }

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(ENG_PKG);
        List<ResolveInfo> hits = getPackageManager().queryIntentActivities(main, 0);
        if (hits == null || hits.isEmpty()) {
            say("  queryIntentActivities(LAUNCHER) -> none");
        } else {
            for (ResolveInfo ri : hits) {
                ActivityInfo ai = ri.activityInfo;
                say("  LAUNCHER -> " + ai.packageName + "/" + ai.name + " exported=" + ai.exported);
                ok |= tryStart(new ComponentName(ai.packageName, ai.name), "LAUNCHER 入口");
            }
        }

        try {
            getPackageManager().getPackageInfo(PLUGIN_PKG, 0);
            say("  插件包已安装: " + PLUGIN_PKG);
        } catch (PackageManager.NameNotFoundException e) {
            say("  插件包未安装（正常，Fairy 插件不是独立包）: " + PLUGIN_PKG);
        }

        say(ok ? "\n结果: 已发起启动。" : "\n结果: 全部失败 —— 见上方错误。");
    }

    private void scanPages() {
        clear();
        say("== 页面可达性扫描 (" + PAGES.length + " 项) ==");
        say("exported=yes 才可能被普通应用直接拉起\n");
        PackageManager pm = getPackageManager();
        int exported = 0;
        for (String[] page : PAGES) {
            final ComponentName cn = new ComponentName(ENG_PKG, page[1]);
            String state;
            boolean canStart = false;
            try {
                ActivityInfo ai = pm.getActivityInfo(cn, 0);
                canStart = ai.exported && ai.enabled;
                state = "exported=" + ai.exported + " enabled=" + ai.enabled;
                if (canStart) {
                    exported++;
                }
            } catch (PackageManager.NameNotFoundException e) {
                state = "未在清单中";
            }
            say((canStart ? "✓ " : "· ") + page[0] + "  [" + state + "]");
            if (canStart) {
                final String label = page[0];
                addButton("打开: " + label, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tryStart(cn, label);
                    }
                });
            }
        }
        say("\n可直接拉起: " + exported + " / " + PAGES.length);
        if (exported == 0) {
            say("工程模式未安装或无法访问。");
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean tryStart(ComponentName cn, String label) {
        Intent i = new Intent();
        i.setComponent(cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return tryIntent(i, label);
    }

    private boolean tryIntent(Intent i, String label) {
        try {
            startActivity(i);
            say("  ✓ " + label + " -> OK");
            return true;
        } catch (ActivityNotFoundException e) {
            say("  ✗ " + label + " -> ActivityNotFound");
        } catch (SecurityException e) {
            say("  ✗ " + label + " -> SecurityException (未导出/无权限)");
        } catch (Exception e) {
            say("  ✗ " + label + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return false;
    }

    private void header(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setPadding(0, 0, 0, dp(8));
        root.addView(tv);
    }

    private void addButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        root.addView(b, lp);
    }

    private void clear() {
        log.setText("");
    }

    private void say(String s) {
        log.append(s + "\n");
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
