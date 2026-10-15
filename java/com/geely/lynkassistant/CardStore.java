package com.geely.lynkassistant;

import android.content.Context;
import android.content.SharedPreferences;

/** 桌面卡片槽位 → 目标应用；以及全局的图标填充方案。 */
public final class CardStore {

    /** 卡片槽位数（每个槽位对应一个 AppWidgetProvider，桌面「添加卡片」里各占一项）。 */
    public static final int SLOTS = 6;

    /** 填充方案：铺满并裁掉超出（保持比例）。 */
    public static final int FILL_CROP = 0;
    /** 填充方案：拉伸铺满（会变形）。 */
    public static final int FILL_STRETCH = 1;
    /** 填充方案：完整显示、留边（保持比例）。 */
    public static final int FILL_FIT = 2;

    private static final String PREFS = "lynk_card";
    private static final String KEY = "slot_";
    private static final String KEY_FILL = "fill_mode";

    private CardStore() {
    }

    /** 该槽位选中的应用包名，未选择时为 null。 */
    public static String target(Context c, int slot) {
        String p = prefs(c).getString(KEY + slot, null);
        return (p == null || p.isEmpty()) ? null : p;
    }

    public static void setTarget(Context c, int slot, String pkg) {
        prefs(c).edit().putString(KEY + slot, pkg).apply();
    }

    public static void clear(Context c, int slot) {
        prefs(c).edit().remove(KEY + slot).apply();
    }

    public static int fillMode(Context c) {
        return prefs(c).getInt(KEY_FILL, FILL_CROP);
    }

    public static void setFillMode(Context c, int mode) {
        prefs(c).edit().putInt(KEY_FILL, mode).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
