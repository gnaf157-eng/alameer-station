package com.alameer.station.shifts;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** لوحة التحكم: ملخّص داكن قابل للطي للورديات والصناديق والمواد والديون. */
public class ControlPanelActivity extends Activity {

    // ألوان الثيم الداكن الخاص باللوحة.
    static final int DARK_BG = 0xff12201c;
    static final int PANEL = 0xff1b2b26;
    static final int PANEL_SOFT = 0xff223530;
    static final int DIVIDER = 0xff2c403a;
    static final int HEAD = 0xff0f6b52;
    static final int GOLD = 0xffF5C518;
    static final int GREEN_T = 0xff2ecc8f;
    static final int RED_T = 0xffff6b74;
    static final int AMBER_T = 0xffE0A32E;
    static final int TEXT = 0xffeaf3f0;
    static final int TEXT_DIM = 0xff8fa6a0;

    private Db db;
    private LinearLayout content;

    private static final double LOW_CASH = 50000;
    private static final double BIG_DEBT = 100000;
    private static final int STALE_DAYS = 21;

    /** حالة الطي محفوظة بين الفتحات. */
    private boolean openStock, openCash, openDebt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        openStock = "1".equals(db.setting("panel_stock", "1"));
        openCash = "1".equals(db.setting("panel_cash", "0"));
        openDebt = "1".equals(db.setting("panel_debt", "0"));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(DARK_BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(HEAD);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("لوحة التحكم", 18, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + "  •  " + ShiftDates.today(), 11, 0xffBFE3D6, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(20));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(bottomNav());
        setContentView(shell);
    }

    @Override protected void onResume() { super.onResume(); build(); }

    private void build() {
        content.removeAllViews();
        content.addView(statusRow());
        section("المخزون", openStock, v -> { openStock = !openStock; save("panel_stock", openStock); build(); }, this::stockBody);
        section("الصناديق", openCash, v -> { openCash = !openCash; save("panel_cash", openCash); build(); }, this::cashBody);
        section("الديون", openDebt, v -> { openDebt = !openDebt; save("panel_debt", openDebt); build(); }, this::debtBody);
    }

    private void save(String key, boolean value) { db.setSetting(key, value ? "1" : "0"); }

    // ==================== شريط الحالة الثلاثي ====================

    /** ثلاث بطاقات: الورديات، الصناديق، المواد — سليم أو يحتاج انتباه. */
    private View statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        int open = db.openShifts() + db.pendingCount();
        row.addView(statusCard("الورديات", open == 0 ? "0" : String.valueOf(open),
                open == 0, open == 0 ? "سليم" : "معلّقة"), cell());

        int badBoxes = 0;
        double cash = db.cashboxesTotal();
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) if (c.getDouble(6) < LOW_CASH) badBoxes++;
        }
        row.addView(statusCard("الصناديق", money(cash) + " ر.ي",
                badBoxes == 0, badBoxes == 0 ? "سليم" : badBoxes + " منخفض"), cell());

        int lowStock = 0;
        for (String material : Db.MATERIALS) {
            double left = db.materialSummary(material)[3];
            if (left < db.capacity(material) * 0.25) lowStock++;
        }
        row.addView(statusCard("المواد", lowStock == 0 ? "سليم" : lowStock + " مادة",
                lowStock == 0, lowStock == 0 ? "سليم" : "منخفضة"), cell());
        return row;
    }

    private View statusCard(String title, String value, boolean ok, String state) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(12));
        box.setBackground(Util.round(PANEL, dp(14)));

        TextView label = text(title, 12, TEXT_DIM, false);
        label.setGravity(Gravity.CENTER);
        box.addView(label);

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, dp(8), 0, dp(6));
        TextView mark = text(ok ? "✅" : "⚠️", 15, ok ? GREEN_T : AMBER_T, true);
        line.addView(mark);
        TextView number = text(value, 16, ok ? GREEN_T : AMBER_T, true);
        number.setPadding(dp(6), 0, 0, 0);
        number.setTextDirection(View.TEXT_DIRECTION_LTR);
        line.addView(number);
        box.addView(line);

        TextView tail = text(state, 11, TEXT_DIM, false);
        tail.setGravity(Gravity.CENTER);
        box.addView(tail);
        return box;
    }

    // ==================== قسم قابل للطي ====================

    private interface Body { void fill(LinearLayout into); }

    private void section(String title, boolean open, View.OnClickListener toggle, Body body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Util.round(PANEL, dp(14)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(9), 0, 0);
        card.setLayoutParams(cp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(18), dp(16), dp(18));
        head.setClickable(true);
        head.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF),
                Util.round(PANEL, dp(14)), null));
        head.setOnClickListener(toggle);
        TextView name = text(title, 16, GOLD, true);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text(open ? "▼ ‣" : "◀ ‣", 13, GOLD, false);
        head.addView(arrow);
        card.addView(head);

        if (open) {
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(dp(12), 0, dp(12), dp(12));
            body.fill(inner);
            card.addView(inner);
            inner.setAlpha(0f);
            inner.setTranslationY(dp(-10));
            inner.animate().alpha(1f).translationY(0).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        content.addView(card);
    }

    // ==================== المخزون ====================

    private void stockBody(LinearLayout into) {
        for (String material : Db.MATERIALS) {
            final String name = material;
            double left = db.materialSummary(material)[3];
            double capacity = Math.max(1, db.capacity(material));
            int percent = (int) Math.round(Math.max(0, left) * 100 / capacity);
            int tint = percent <= 12 ? RED_T : percent <= 30 ? AMBER_T : GREEN_T;

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(13), dp(14), dp(14));
            box.setBackground(Util.round(PANEL_SOFT, dp(12)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.setMargins(0, dp(6), 0, dp(6));
            box.setLayoutParams(bp);
            box.setClickable(true);
            box.setOnLongClickListener(v -> { capacityDialog(name); return true; });

            LinearLayout top = new LinearLayout(this);
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text(material, 16, TEXT, true));
            words.addView(text("السعة " + money(capacity) + " · الحالي " + money(left), 11, TEXT_DIM, false));
            top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            TextView amount = text(money(left), 19, tint, true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);
            amount.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(amount);
            box.addView(top);

            box.addView(bar(percent, tint), barParams());
            into.addView(box);
        }
        TextView hint = text("اضغط مطوّلًا على المادة لتعديل سعة الخزان", 10, TEXT_DIM, false);
        hint.setPadding(dp(4), dp(6), 0, 0);
        into.addView(hint);
    }

    /** شريط امتلاء ينمو بحركة من الصفر. */
    private LinearLayout bar(int percent, int tint) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(Util.round(0xffE6E4DC, dp(5)));
        final View fill = new View(this);
        fill.setBackground(Util.round(tint, dp(5)));
        final LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(10), 0.01f);
        bar.addView(fill, fp);
        View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(10), Math.max(0.01f, 100 - percent)));

        ValueAnimator grow = ValueAnimator.ofFloat(0.01f, Math.max(0.01f, Math.min(100, percent)));
        grow.setDuration(620);
        grow.setStartDelay(120);
        grow.setInterpolator(new DecelerateInterpolator());
        grow.addUpdateListener(a -> { fp.weight = (Float) a.getAnimatedValue(); fill.setLayoutParams(fp); });
        grow.start();
        return bar;
    }

    private LinearLayout.LayoutParams barParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(10));
        p.setMargins(0, dp(11), 0, 0);
        return p;
    }

    private void capacityDialog(final String material) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER);
        input.setText(money(db.capacity(material)).replace(",", ""));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(26), dp(14), dp(26), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("سعة خزان " + material)
                .setMessage("اكتب السعة الكاملة باللتر.")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double value = Calc.number(input.getText().toString());
                    if (value > 0) { db.setCapacity(material, value); build(); }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    // ==================== الصناديق ====================

    private void cashBody(LinearLayout into) {
        List<String[]> boxes = new ArrayList<>();
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) boxes.add(new String[]{c.getString(1), String.valueOf(c.getDouble(6))});
        }
        boxes.sort((a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));
        if (boxes.isEmpty()) { into.addView(emptyRow("لم تُنشئ صناديق بعد.")); return; }
        for (int i = 0; i < boxes.size(); i++) {
            double balance = Double.parseDouble(boxes.get(i)[1]);
            into.addView(row(boxes.get(i)[0], money(balance) + " ر.ي",
                    balance < 0 ? RED_T : balance < LOW_CASH ? AMBER_T : GREEN_T));
            if (i < boxes.size() - 1) into.addView(divider());
        }
    }

    // ==================== الديون ====================

    private void debtBody(LinearLayout into) {
        List<String[]> people = new ArrayList<>();
        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                double balance = c.getDouble(7);
                if (Math.abs(balance) < 0.009) continue;
                people.add(new String[]{c.getString(1), String.valueOf(balance),
                        String.valueOf(daysSince(db.debtorLastActivity(c.getLong(0))))});
            }
        }
        people.sort((a, b) -> Double.compare(
                Math.abs(Double.parseDouble(b[1])), Math.abs(Double.parseDouble(a[1]))));
        if (people.isEmpty()) { into.addView(emptyRow("لا ديون مستحقة — ممتاز.")); return; }
        for (int i = 0; i < people.size(); i++) {
            double balance = Double.parseDouble(people.get(i)[1]);
            int idle = Integer.parseInt(people.get(i)[2]);
            into.addView(debtRow(people.get(i)[0], balance, idle));
            if (i < people.size() - 1) into.addView(divider());
        }
    }

    /** سطر مدين بمفتاح ذهبي يشير إلى حالته. */
    private View debtRow(String name, double balance, int idle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(13), dp(4), dp(13));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(name, 16, TEXT, true));
        TextView value = text(money(-Math.abs(balance)) + " ر.ي", 12, TEXT_DIM, false);
        value.setTextDirection(View.TEXT_DIRECTION_LTR);
        value.setPadding(0, dp(4), 0, 0);
        words.addView(value);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        boolean heavy = Math.abs(balance) > BIG_DEBT || idle >= STALE_DAYS;
        row.addView(toggle(heavy));
        return row;
    }

    /** مفتاح مرسوم: خلفية داكنة وكرة ذهبية. */
    private View toggle(boolean flagged) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(Util.round(flagged ? 0xff2f4038 : 0xff26362f, dp(15)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(dp(60), dp(30));
        wrap.setLayoutParams(wp);
        View knob = new View(this);
        knob.setBackground(Util.round(GOLD, dp(11)));
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(22), dp(22));
        kp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        kp.leftMargin = dp(4);
        wrap.addView(knob, kp);
        View tip = new View(this);
        tip.setBackground(Util.round(GREEN_T, dp(2)));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(4), dp(12));
        tp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        tp.rightMargin = dp(5);
        wrap.addView(tip, tp);
        return wrap;
    }

    // ==================== لبنات مشتركة ====================

    private View row(String name, String value, int tint) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(15), dp(4), dp(15));
        row.addView(text(name, 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = text(value, 17, tint, true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        row.addView(amount);
        return row;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return line;
    }

    private View emptyRow(String message) {
        TextView t = text(message, 14, TEXT_DIM, false);
        t.setPadding(dp(4), dp(14), dp(4), dp(14));
        return t;
    }

    // ==================== شريط التنقل السفلي ====================

    private View bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(DARK_BG);
        nav.setPadding(dp(8), dp(8), dp(8), dp(10));
        nav.addView(navButton("الرئيسية", "⌂", true, v -> { }), navCell());
        nav.addView(navButton("وردية", "◷", false,
                v -> startActivity(new Intent(this, ShiftActivity.class))), navCell());
        nav.addView(navButton("الصناديق", "▣", false,
                v -> startActivity(new Intent(this, CashboxActivity.class))), navCell());
        nav.addView(navButton("المواد", "◱", false,
                v -> startActivity(new Intent(this, MaterialActivity.class))), navCell());
        return nav;
    }

    private LinearLayout.LayoutParams navCell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private View navButton(String label, String glyph, boolean active, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(12), dp(4), dp(11));
        box.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF),
                Util.round(active ? GOLD : PANEL, dp(13)), null));
        box.setClickable(true);
        box.setOnClickListener(action);
        int ink = active ? 0xff1a1a1a : TEXT;
        TextView icon = text(glyph, 17, ink, false);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);
        TextView name = text(label, 12, ink, active);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(4), 0, 0);
        box.addView(name);
        return box;
    }

    // ==================== أدوات ====================

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private int daysSince(String date) {
        if (date == null || date.isEmpty()) return 0;
        try {
            java.time.LocalDate then = java.time.LocalDate.parse(date);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(then, java.time.LocalDate.now());
        } catch (Exception e) { return 0; }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
