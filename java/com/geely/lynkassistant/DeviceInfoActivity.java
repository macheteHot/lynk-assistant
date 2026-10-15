package com.geely.lynkassistant;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * Read-only device information.
 *
 * <p>Standard fields come from {@link Build}. Vendor fields require
 * {@code android.os.SystemProperties}, which is a hidden API; it is read
 * through reflection and every read is fail-soft so a blocked read just shows
 * "读取失败" instead of crashing.
 *
 * <p>UI: left column = standard Build fields, right column = vehicle properties.
 */
public class DeviceInfoActivity extends Activity {

    private static final String[][] PROPS = {
            {"版本号 (build)", "ro.build.display.id"},
            {"系统版本", "ro.build.version.release"},
            {"构建类型", "ro.build.type"},
            {"品牌 / 型号", "ro.product.brand"},
            {"设备代号", "ro.product.device"},
            {"序列号", "ro.serialno"},
            {"车辆 VIN", "sys.vehicle.hardware.vin.code"},
            {"硬件版本", "sys.vehicle.hardware.version"},
            {"MCU 版本", "sys.vehicle.mcu.version"},
            {"TBOX IMEI", "persist.tbox.imei"},
            {"TBOX ICCID", "persist.tbox.iccid"},
            {"ADB 开关", "persist.sv.debug.adb_enable"},
            {"网络安全", "ro.desay.cybersecurity"},
            {"区域", "persist.sys.locale"},
    };

    private String standardText;
    private String vehicleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.Screen sc = Ui.create(this, getString(R.string.device_info_title), 1f, 1f);
        setContentView(sc.root);
        LinearLayout left = sc.left.body;
        LinearLayout right = sc.right.body;

        standardText = buildStandard();
        vehicleText = buildVehicle();

        left.addView(Ui.label(this, "标准 Build 信息"));
        left.addView(Ui.primary(this, "复制全部", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAll();
            }
        }));
        left.addView(Ui.gap(this, 8));
        LinearLayout leftCard = Ui.card(this);
        TextView std = Ui.mono(this);
        std.setText(standardText);
        leftCard.addView(std);
        left.addView(leftCard);

        right.addView(Ui.label(this, "车机系统属性"));
        LinearLayout rightCard = Ui.card(this);
        TextView veh = Ui.mono(this);
        veh.setText(vehicleText);
        rightCard.addView(veh);
        right.addView(rightCard);

        right.addView(Ui.gap(this, 14));
        right.addView(Ui.dim(this,
                "注: 系统属性为只读展示；读取失败表示该机型不支持或受限。"));
    }

    private void copyAll() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("device-info",
                    standardText + "\n" + vehicleText));
            toast("已复制");
        }
    }

    private String buildStandard() {
        StringBuilder sb = new StringBuilder();
        sb.append("MANUFACTURER : ").append(Build.MANUFACTURER).append('\n');
        sb.append("BRAND        : ").append(Build.BRAND).append('\n');
        sb.append("MODEL        : ").append(Build.MODEL).append('\n');
        sb.append("DEVICE       : ").append(Build.DEVICE).append('\n');
        sb.append("HARDWARE     : ").append(Build.HARDWARE).append('\n');
        sb.append("DISPLAY      : ").append(Build.DISPLAY).append('\n');
        sb.append("Android      : ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("TYPE/TAGS    : ").append(Build.TYPE).append(" / ").append(Build.TAGS).append('\n');
        return sb.toString();
    }

    private String buildVehicle() {
        StringBuilder sb = new StringBuilder();
        for (String[] p : PROPS) {
            String v = getProperty(p[1]);
            sb.append(pad(p[0])).append(" : ").append(v).append('\n');
        }
        return sb.toString();
    }

    private static String pad(String s) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < 12) {
            b.append(' ');
        }
        return b.toString();
    }

    /** Read a system property through reflection; fail-soft. */
    private String getProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            Object v = get.invoke(null, key);
            String s = v == null ? "" : v.toString();
            return TextUtils.isEmpty(s) ? "(空)" : s;
        } catch (Throwable t) {
            return "读取失败";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
