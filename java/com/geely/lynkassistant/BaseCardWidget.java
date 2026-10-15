package com.geely.lynkassistant;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 桌面卡片基类：只画「该槽位选中的应用」的图标，点击直接拉起它。
 *
 * <p>每个槽位由 {@link CardWidget1}…{@link CardWidget6} 中的一个子类实现，
 * 各自在桌面的「添加卡片」里占一项，因此可以放多个不同应用的卡片。
 *
 * <p>能被桌面收录的原因：车机桌面 {@code BaseWidgetVm.isNeedWidget()} 有一条兜底
 * 分支——主屏组件（displayType==0）只要包名以 {@code com.geely} 开头即通过；
 * 本应用 applicationId 是 {@code com.geely.lynkassistant}，故无需写白名单。
 */
abstract class BaseCardWidget extends AppWidgetProvider {

    /** 卡片上的图标边长（px，ARGB_8888）。过大易触发 RemoteViews 的 1MB binder 限制。 */
    private static final int ICON_PX = 400;

    /** 本 provider 对应的槽位（1..{@link CardStore#SLOTS}）。 */
    abstract int slot();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            mgr.updateAppWidget(id, build(ctx, slot()));
        }
    }

    /** 选好应用后刷新所有已摆放的卡片。 */
    static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        for (int slot = 1; slot <= CardStore.SLOTS; slot++) {
            Class<?> cls = providerClass(slot);
            if (cls == null) {
                continue;
            }
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, cls));
            for (int id : ids) {
                mgr.updateAppWidget(id, build(ctx, slot));
            }
        }
    }

    private static Class<?> providerClass(int slot) {
        switch (slot) {
            case 1: return CardWidget1.class;
            case 2: return CardWidget2.class;
            case 3: return CardWidget3.class;
            case 4: return CardWidget4.class;
            case 5: return CardWidget5.class;
            case 6: return CardWidget6.class;
            default: return null;
        }
    }

    static RemoteViews build(Context ctx, int slot) {
        int mode = CardStore.fillMode(ctx);
        int layout = R.layout.card_widget_crop;
        if (mode == CardStore.FILL_STRETCH) {
            layout = R.layout.card_widget_stretch;
        } else if (mode == CardStore.FILL_FIT) {
            layout = R.layout.card_widget_fit;
        }
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout);
        String pkg = CardStore.target(ctx, slot);
        if (pkg == null) {
            rv.setViewVisibility(R.id.card_icon, View.GONE);
            rv.setViewVisibility(R.id.card_empty, View.VISIBLE);
            rv.setTextViewText(R.id.card_empty, "空白卡片" + slot);
            // 点空白卡片 -> 直接打开「添加桌面组件」并选中该槽位
            Intent open = new Intent(ctx, WidgetCardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(WidgetCardActivity.EXTRA_SLOT, slot);
            rv.setOnClickPendingIntent(R.id.card_root, activity(ctx, open, 200 + slot));
            return rv;
        }
        rv.setViewVisibility(R.id.card_empty, View.GONE);
        rv.setViewVisibility(R.id.card_icon, View.VISIBLE);

        PackageManager pm = ctx.getPackageManager();
        Bitmap bmp = icon(pm, pkg);
        if (bmp != null) {
            rv.setImageViewBitmap(R.id.card_icon, bmp);
        }

        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            rv.setOnClickPendingIntent(R.id.card_root, activity(ctx, launch, 100));
        }
        return rv;
    }

    private static PendingIntent activity(Context ctx, Intent i, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(ctx, requestCode, i, flags);
    }

    private static Bitmap icon(PackageManager pm, String pkg) {
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            int iw = d.getIntrinsicWidth();
            int ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) {
                iw = ih = ICON_PX;
            }
            // 保持原始宽高比（如 lynkPlay 的图标是横向矩形），只等比缩放到 ICON_PX 以内
            float scale = Math.min((float) ICON_PX / iw, (float) ICON_PX / ih);
            int w = Math.max(1, Math.round(iw * scale));
            int h = Math.max(1, Math.round(ih * scale));

            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }
}
