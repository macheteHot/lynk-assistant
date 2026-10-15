package com.geely.lynkassistant;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 「添加桌面组件」：左边选槽位，右边选应用 + 选图标填充方案。
 *
 * <p>每个槽位在桌面的「添加卡片」里各占一项（领克助手 · 卡片 1..6），
 * 用户把需要的摆到桌面即可；卡片只显示对应应用的图标，点击拉起它。
 */
public class WidgetCardActivity extends Activity {

    /** 由空白卡片点击带入，指定要配置的槽位。 */
    public static final String EXTRA_SLOT = "slot";

    private LinearLayout slotsBox;
    private LinearLayout fillBox;
    private LinearLayout appsBox;
    private TextView appsTitle;
    private int selectedSlot = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.Screen sc = Ui.create(this, getString(R.string.widget_card_title), 1f, 1.3f);
        setContentView(sc.root);
        LinearLayout left = sc.left.body;
        LinearLayout right = sc.right.body;

        selectedSlot = clampSlot(getIntent().getIntExtra(EXTRA_SLOT, 1));

        // ---- 左栏：槽位 ----
        left.addView(Ui.label(this, "卡片槽位"));
        slotsBox = new LinearLayout(this);
        slotsBox.setOrientation(LinearLayout.VERTICAL);
        left.addView(slotsBox);
        left.addView(Ui.gap(this, 12));
        left.addView(Ui.dim(this, "选一个槽位，再在右边选应用。\n"
                + "桌面长按空白处 → 添加卡片 → 选「领克助手 · 卡片N」。\n"
                + "卡片上的图标就是该应用的图标，点一下直接打开它。"));

        // ---- 右栏：填充方案 + 应用列表 ----
        right.addView(Ui.label(this, "图标填充"));
        fillBox = new LinearLayout(this);
        fillBox.setOrientation(LinearLayout.HORIZONTAL);
        right.addView(fillBox);
        right.addView(Ui.gap(this, 6));
        right.addView(Ui.dim(this, "填满·裁切：铺满并保持比例（推荐）；拉伸铺满：会变形；完整·留边：不裁剪。\n"
                + "改完立即生效于已摆放的卡片。"));

        right.addView(Ui.gap(this, 16));
        appsTitle = Ui.label(this, "");
        right.addView(appsTitle);
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        right.addView(appsBox);
        right.addView(Ui.gap(this, 8));
        Ui.addRow(right, Ui.ghost(this, "清除该卡片", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CardStore.clear(WidgetCardActivity.this, selectedSlot);
                BaseCardWidget.refreshAll(WidgetCardActivity.this);
                render();
                toast("已清除卡片 " + selectedSlot);
            }
        }));

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (slotsBox != null) {
            render();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectedSlot = clampSlot(intent.getIntExtra(EXTRA_SLOT, selectedSlot));
        render();
    }

    private static int clampSlot(int slot) {
        return (slot < 1 || slot > CardStore.SLOTS) ? 1 : slot;
    }

    private void render() {
        renderSlots();
        renderFill();
        renderApps();
    }

    private void renderSlots() {
        slotsBox.removeAllViews();
        for (int slot = 1; slot <= CardStore.SLOTS; slot++) {
            final int s = slot;
            String pkg = CardStore.target(this, slot);
            String text = "卡片 " + slot + "：" + (pkg == null ? "未选择" : label(pkg));
            Button b = (slot == selectedSlot)
                    ? Ui.primary(this, text, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSlot = s;
                            render();
                        }
                    })
                    : Ui.row(this, text, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSlot = s;
                            render();
                        }
                    });
            Ui.addRow(slotsBox, b);
        }
    }

    private void renderFill() {
        fillBox.removeAllViews();
        int mode = CardStore.fillMode(this);
        addFill(FILL_LABELS[0], CardStore.FILL_CROP, mode);
        addFill(FILL_LABELS[1], CardStore.FILL_STRETCH, mode);
        addFill(FILL_LABELS[2], CardStore.FILL_FIT, mode);
    }

    private static final String[] FILL_LABELS = {"填满·裁切", "拉伸铺满", "完整·留边"};

    private void addFill(String text, final int value, int current) {
        Button b;
        View.OnClickListener l = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CardStore.setFillMode(WidgetCardActivity.this, value);
                BaseCardWidget.refreshAll(WidgetCardActivity.this);
                render();
                toast("填充方案：" + FILL_LABELS[value]);
            }
        };
        b = (value == current) ? Ui.primary(this, text, l) : Ui.ghost(this, text, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = value == 2 ? 0 : Ui.dp(this, 10);
        fillBox.addView(b, lp);
    }

    private void renderApps() {
        String sel = CardStore.target(this, selectedSlot);
        String slotName = "卡片 " + selectedSlot;
        appsTitle.setText(slotName + " · 选择应用" + (sel == null ? "" : "（已选 " + label(sel) + "）"));

        appsBox.removeAllViews();
        for (final String pkg : launchable()) {
            String mark = pkg.equals(sel) ? "✓ " : "";
            Button b = pkg.equals(sel)
                    ? Ui.primary(this, mark + label(pkg), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setApp(pkg);
                        }
                    })
                    : Ui.row(this, mark + label(pkg), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setApp(pkg);
                        }
                    });
            Ui.addRow(appsBox, b);
        }
    }

    private void setApp(String pkg) {
        CardStore.setTarget(this, selectedSlot, pkg);
        BaseCardWidget.refreshAll(this);
        render();
        toast("卡片 " + selectedSlot + " 已设为 " + label(pkg));
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    /** 已安装且带 LAUNCHER 入口、且不是自己的应用。 */
    private List<String> launchable() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(main, 0);
        List<String> out = new ArrayList<>();
        if (all == null) {
            return out;
        }
        for (ResolveInfo ri : all) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(getPackageName()) && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
