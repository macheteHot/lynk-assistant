package com.geely.lynkassistant;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private TextView body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.device_info_title));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        Button copy = new Button(this);
        copy.setText("复制全部");
        copy.setAllCaps(false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("device-info", body.getText()));
                    toast("已复制");
                }
            }
        });
        root.addView(copy);

        body = new TextView(this);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.setTextIsSelectable(true);
        body.setPadding(0, dp(12), 0, 0);
        root.addView(body);

        body.setText(buildReport());
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("== 标准 Build 信息 ==\n");
        sb.append("MANUFACTURER : ").append(Build.MANUFACTURER).append('\n');
        sb.append("BRAND        : ").append(Build.BRAND).append('\n');
        sb.append("MODEL        : ").append(Build.MODEL).append('\n');
        sb.append("DEVICE       : ").append(Build.DEVICE).append('\n');
        sb.append("HARDWARE     : ").append(Build.HARDWARE).append('\n');
        sb.append("DISPLAY      : ").append(Build.DISPLAY).append('\n');
        sb.append("Android      : ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("TYPE/TAGS    : ").append(Build.TYPE).append(" / ").append(Build.TAGS).append('\n');

        sb.append("\n== 车机系统属性 ==\n");
        for (String[] p : PROPS) {
            String v = getProperty(p[1]);
            sb.append(pad(p[0])).append(" : ").append(v).append('\n');
        }

        sb.append("\n注: 系统属性为只读展示；读取失败表示该机型不支持或受限。\n");
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

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
