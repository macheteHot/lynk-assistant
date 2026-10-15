package com.geely.lynkassistant;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Shared UI toolkit: a dark "car dashboard" look, an app bar with an explicit
 * back button, and a left/right split (master–detail) body.
 *
 * <p>Everything is built in code on purpose — the project has no androidx and
 * no layout resources beyond strings and icons. Keeping one toolkit here means
 * every screen shares the same palette, spacing and back-button behaviour.
 */
final class Ui {

    // ------------------------------------------------------------- palette
    static final int BG = 0xFF0E1116;
    static final int SURFACE = 0xFF171B22;
    static final int SURFACE_ALT = 0xFF232A35;
    static final int ACCENT = 0xFF3D7EFF;
    static final int TEXT = 0xFFE9ECF1;
    static final int TEXT_DIM = 0xFF98A2B3;
    static final int OK = 0xFF39D98A;
    static final int WARN = 0xFFF5C451;
    static final int ERR = 0xFFF0685A;
    static final int LINE = 0xFF2B3340;

    private Ui() {
    }

    // ------------------------------------------------------------- basics

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        return round(c, color, radiusDp, 0);
    }

    static GradientDrawable round(Context c, int color, float radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) {
            d.setStroke(Math.max(1, dp(c, 1)), stroke);
        }
        return d;
    }

    // ------------------------------------------------------------- text

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    static TextView body(Context c, CharSequence s) {
        TextView t = text(c, s, 20, TEXT);
        t.setLineSpacing(dp(c, 5), 1f);
        return t;
    }

    static TextView dim(Context c, CharSequence s) {
        TextView t = text(c, s, 17, TEXT_DIM);
        t.setLineSpacing(dp(c, 4), 1f);
        return t;
    }

    static TextView label(Context c, CharSequence s) {
        TextView t = text(c, s, 19, ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 2), 0, dp(c, 10));
        return t;
    }

    /** Monospace, selectable text well suited to logs and dumps. */
    static TextView mono(Context c) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setLineSpacing(dp(c, 5), 1f);
        return t;
    }

    // ------------------------------------------------------------- buttons

    private static Button base(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 24), dp(c, 20), dp(c, 24), dp(c, 20));
        b.setOnClickListener(l);
        return b;
    }

    static Button primary(Context c, String s, View.OnClickListener l) {
        Button b = base(c, s, l);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(c, ACCENT, 10));
        return b;
    }

    static Button ghost(Context c, String s, View.OnClickListener l) {
        Button b = base(c, s, l);
        b.setTextColor(TEXT);
        b.setBackground(round(c, SURFACE_ALT, 10, LINE));
        return b;
    }

    /** Left-aligned, card-like row; multi-line text is fine. */
    static Button row(Context c, String s, View.OnClickListener l) {
        Button b = base(c, s, l);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setBackground(round(c, SURFACE_ALT, 10, LINE));
        return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackground(round(c, SURFACE, 14, LINE));
        int p = dp(c, 22);
        ll.setPadding(p, p, p, p);
        return ll;
    }

    static View gap(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    /** Adds {@code v} as a full-width row with a small bottom margin. */
    static void addRow(LinearLayout parent, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(parent.getContext(), 12);
        parent.addView(v, lp);
    }

    /** Adds two views side by side, each taking half the width. */
    static void addPair(LinearLayout parent, View a, View b) {
        Context c = parent.getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(c, 14);
        r.addView(a, lp);
        r.addView(b, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(c, 14);
        parent.addView(r, rlp);
    }

    // ------------------------------------------------------------- screen

    /** One scrollable column of a screen. */
    static final class Pane {
        final ScrollView scroll;
        final LinearLayout body;

        Pane(ScrollView s, LinearLayout b) {
            scroll = s;
            body = b;
        }
    }

    /** Root view plus the two columns, already wired with an app bar. */
    static final class Screen {
        final LinearLayout root;
        final Pane left;
        final Pane right;

        Screen(LinearLayout root, Pane left, Pane right) {
            this.root = root;
            this.left = left;
            this.right = right;
        }
    }

    /** Two-column screen with a back button in the app bar. */
    static Screen create(Activity a, String title, float leftWeight, float rightWeight) {
        return build(a, title, leftWeight, rightWeight, true);
    }

    /** Two-column screen without a back button (used by the home screen). */
    static Screen createHome(Activity a, String title, float leftWeight, float rightWeight) {
        return build(a, title, leftWeight, rightWeight, false);
    }

    private static Screen build(final Activity a, String title,
                                float leftWeight, float rightWeight, boolean back) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(appBar(a, title, back));

        LinearLayout bodyRow = new LinearLayout(a);
        bodyRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(bodyRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Pane left = pane(a, leftWeight);
        bodyRow.addView(left.scroll);

        Pane right = null;
        if (rightWeight > 0f) {
            bodyRow.addView(vDivider(a));
            right = pane(a, rightWeight);
            bodyRow.addView(right.scroll);
        }
        return new Screen(root, left, right);
    }

    static LinearLayout appBar(final Activity a, String title, boolean back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SURFACE);
        int h = dp(a, 18);
        bar.setPadding(h, dp(a, 16), h, dp(a, 16));

        if (back) {
            Button b = base(a, "← 返回", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.finish();
                }
            });
            b.setTextColor(TEXT);
            b.setBackground(round(a, SURFACE_ALT, 10, LINE));
            bar.addView(b);
        }

        TextView t = text(a, title, 30, TEXT);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(a, back ? 20 : 4), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private static Pane pane(Context c, float weight) {
        ScrollView sv = new ScrollView(c);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 22);
        body.setPadding(p, p, p, p);
        sv.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Pane(sv, body);
    }

    private static View vDivider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                Math.max(1, dp(c, 1)), ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }
}
