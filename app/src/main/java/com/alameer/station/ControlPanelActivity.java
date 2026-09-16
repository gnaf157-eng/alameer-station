package com.alameer.station.shifts;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** لوحة التحكم: حالة الورديات والصناديق والمواد والديون بألوان هوية التطبيق. */
public class ControlPanelActivity extends Activity {
    private Db db;
    private LinearLayout content;

    private static final double LOW_CASH = 50000;
    private static final double BIG_DEBT = 100000;
    private static final int STALE_DAYS = 21;
    private static final int AMBER = 0xffB86A00;

    private boolean debtsOpen = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(15), dp(16), dp(15));
        header.setBackgroundColor(Util.NAVY);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("لوحة التحكم", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + "  •  " + ShiftDates.today(), 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(24));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
    }

    @Override protected void onResume() { super.onResume(); build(); }

    private void build() {
        content.removeAllViews();
        int delay = 0;
        delay = add(statusRow(), delay);
        delay = add(stockSection(), delay);
        delay = add(cashSection(), delay);
        add(debtSection(), delay);
    }

    private int add(View section, int delay) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(6));
        content.addView(section, p);
        section.setAlpha(0f);
        section.setTranslationY(dp(18));
        section.animate().alpha(1f).translationY(0)
                .setStartDelay(delay).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();
        return delay + 70;
    }

    // ==================== صف الحالة العلوي ====================

    /** ثلاث بطاقات: الورديات، الصناديق، المواد — سليم أو يحتاج انتباه. */
    private View statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        int pending = db.pendingCount();
        row.addView(statusCard("الورديات",
                pending == 0 ? "0" : String.valueOf(pending),
                pending == 0 ? "سليم" : "بانتظار الاعتماد", pending == 0), cell());

        int badBoxes = 0;
        double cash = db.cashboxesTotal();
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) if (c.getDouble(6) < LOW_CASH) badBoxes++;
        }
        row.addView(statusCard("الصناديق", money(cash) + " ر.ي",
                badBoxes == 0 ? "سليم" : badBoxes + " صندوق منخفض", badBoxes == 0), cell());

        int badMaterials = 0;
        for (String material : Db.MATERIALS) {
            double left = db.materialSummary(material)[3];
            if (left / capacity(material) < 0.25) badMaterials++;
        }
        row.addView(statusCard("المواد", badMaterials == 0 ? "كامل" : badMaterials + " مادة",
                badMaterials == 0 ? "سليم" : "تحتاج تعبئة", badMaterials == 0), cell());
        return row;
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private View statusCard(String title, String value, String state, boolean good) {
        int tint = good ? Util.GREEN : AMBER;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(14));
        box.setBackground(Util.round(Color.WHITE, dp(16)));
        box.setElevation(dp(2));

        TextView name = text(title, 12, 0xff7c8186, false);
        name.setGravity(Gravity.CENTER);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, dp(7), 0, dp(6));
        View mark = new View(this);
        mark.setBackground(Util.round(tint, dp(6)));
        line.addView(mark, new LinearLayout.LayoutParams(dp(12), dp(12)));
        TextView number = text(value, 15, tint, true);
        number.setPadding(dp(7), 0, 0, 0);
        number.setTextDirection(View.TEXT_DIRECTION_LTR);
        line.addView(number);
        box.addView(line, new LinearLayout.LayoutParams(-1, -2));

        TextView badge = text(state, 10, tint, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(3), dp(9), dp(3));
        badge.setBackground(Util.round(soften(tint), dp(8)));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.addView(badge);
        box.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    // ==================== المخزون (ظاهر دائمًا) ====================

    private View stockSection() {
        LinearLayout box = panel();
        box.addView(sectionHead("المخزون"));
        for (String material : Db.MATERIALS) {
            double left = Math.max(0, db.materialSummary(material)[3]);
            double cap = capacity(material);
            int percent = (int) Math.round(Math.min(100, left * 100 / cap));
            int tint = percent < 15 ? Util.RED : percent < 30 ? AMBER : Util.GREEN;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13), dp(12), dp(13), dp(13));
            card.setBackground(Util.round(0xffF7FAFE, dp(13)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(dp(10), dp(5), dp(10), dp(5));
            card.setLayoutParams(cp);

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text(material, 16, Util.NAVY, true));
            words.addView(text("السعة " + money(cap) + "  •  الحالي " + money(left), 11, 0xff8b9097, false));
            top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            TextView amount = text(money(left), 18, tint, true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);
            top.addView(amount);
            card.addView(top);

            card.addView(bar(percent, tint), barParams());
            box.addView(card);
        }
        return box;
    }

    // ==================== الصناديق (ظاهرة دائمًا) ====================

    private View cashSection() {
        LinearLayout box = panel();
        box.addView(sectionHead("الصناديق"));
        int count = 0;
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                count++;
                double balance = c.getDouble(6);
                box.addView(flatRow(c.getString(1), money(balance) + " ر.ي",
                        balance < 0 ? Util.RED : balance < LOW_CASH ? AMBER : Util.GREEN));
                box.addView(divider());
            }
        }
        if (count == 0) box.addView(emptyLine("لم تُنشئ صناديق بعد."));
        else {
            double total = db.cashboxesTotal();
            LinearLayout sum = flatRow("الإجمالي", money(total) + " ر.ي", Util.NAVY);
            sum.setBackground(Util.round(Util.ACCENT_SOFT, 0));
            box.addView(sum);
        }
        return box;
    }

    // ==================== الديون (قائمة منسدلة) ====================

    private View debtSection() {
        final LinearLayout box = panel();
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        final TextView arrow = text(debtsOpen ? "▼" : "◀", 13, Util.ACCENT, true);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(14), dp(14), dp(14));
        head.setClickable(true);
        head.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x14000000), null, null));
        head.addView(arrow);
        TextView title = text("الديون", 16, Util.NAVY, true);
        title.setPadding(dp(9), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        double total = db.debtsTotal();
        TextView sum = text(money(total) + " ر.ي", 14, total > 0.009 ? Util.RED : Util.GREEN, true);
        sum.setTextDirection(View.TEXT_DIRECTION_LTR);
        head.addView(sum);
        head.setOnClickListener(v -> {
            debtsOpen = !debtsOpen;
            arrow.setText(debtsOpen ? "▼" : "◀");
            body.setVisibility(debtsOpen ? View.VISIBLE : View.GONE);
            if (debtsOpen) {
                body.setAlpha(0f);
                body.animate().alpha(1f).setDuration(260).start();
            }
        });
        box.addView(head);

        List<String[]> rows = new ArrayList<>();
        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                double balance = c.getDouble(7);
                if (Math.abs(balance) < 0.009) continue;
                rows.add(new String[]{c.getString(1), String.valueOf(balance),
                        String.valueOf(daysSince(db.debtorLastActivity(c.getLong(0))))});
            }
        }
        rows.sort((a, b) -> {
            double x = Double.parseDouble(a[1]), y = Double.parseDouble(b[1]);
            boolean xc = x < -0.009, yc = y < -0.009;
            if (xc != yc) return xc ? 1 : -1;
            return Double.compare(Math.abs(y), Math.abs(x));
        });
        if (rows.isEmpty()) body.addView(emptyLine("لا ديون مستحقة."));
        boolean creditHeaderShown = false;
        for (String[] row : rows) {
            if (Double.parseDouble(row[1]) < -0.009 && !creditHeaderShown) {
                creditHeaderShown = true;
                TextView head = text("أرصدة لهم عندنا", 12, Util.GREEN, true);
                head.setPadding(dp(14), dp(12), dp(14), dp(6));
                body.addView(head);
            }
            double balance = Double.parseDouble(row[1]);
            int idle = Integer.parseInt(row[2]);
            // الرصيد السالب يعني أن الزبون دفع أكثر مما عليه.
            int tint = balance < -0.009 ? Util.GREEN : Util.RED;
            body.addView(flatRow(row[0], money(balance) + " ر.ي",
                    balance < -0.009 ? "له رصيد عندنا"
                    : idle >= STALE_DAYS ? "راكد " + idle + " يومًا" : null, tint));
            body.addView(divider());
        }
        body.setVisibility(debtsOpen ? View.VISIBLE : View.GONE);
        box.addView(body);
        return box;
    }

    // ==================== لبنات البناء ====================

    private LinearLayout panel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Util.round(Color.WHITE, dp(16)));
        box.setElevation(dp(2));
        box.setPadding(0, 0, 0, dp(6));
        return box;
    }

    /** عنوان قسم ثابت غير قابل للطي. */
    private View sectionHead(String name) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(14), dp(14), dp(10));
        View mark = new View(this);
        mark.setBackground(Util.round(Util.ACCENT, dp(2)));
        head.addView(mark, new LinearLayout.LayoutParams(dp(4), dp(18)));
        TextView t = text(name, 16, Util.NAVY, true);
        t.setPadding(dp(9), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return head;
    }

    private LinearLayout flatRow(String name, String value, int tint) {
        return flatRow(name, value, null, tint);
    }

    private LinearLayout flatRow(String name, String value, String note, int tint) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        // بعرض كامل، وإلا انهار الوزن والتصق المبلغ بالاسم.
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(name, 15, Util.NAVY, true);
        title.setMaxLines(2);
        words.addView(title);
        if (note != null) words.addView(text(note, 10, tint == Util.GREEN ? Util.GREEN : AMBER, false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = text(value, 16, tint, true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        amount.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.setMargins(dp(12), 0, 0, 0);
        row.addView(amount, ap);
        return row;
    }

    /** شريط امتلاء ينمو بحركة من الصفر. */
    private LinearLayout bar(int percent, int tint) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(Util.round(0xffe6eaef, dp(5)));
        final View fill = new View(this);
        fill.setBackground(Util.round(tint, dp(5)));
        final LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(9), 0.01f);
        bar.addView(fill, fp);
        View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(9), Math.max(0.01f, 100f - percent)));
        ValueAnimator grow = ValueAnimator.ofFloat(0.01f, Math.max(0.01f, percent));
        grow.setDuration(640);
        grow.setStartDelay(180);
        grow.setInterpolator(new DecelerateInterpolator());
        grow.addUpdateListener(a -> { fp.weight = (Float) a.getAnimatedValue(); fill.setLayoutParams(fp); });
        grow.start();
        return bar;
    }

    private LinearLayout.LayoutParams barParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(9));
        p.setMargins(0, dp(11), 0, 0);
        return p;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(0xffeef1f4);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.setMargins(dp(14), 0, dp(14), 0);
        line.setLayoutParams(p);
        return line;
    }

    private View emptyLine(String message) {
        TextView t = text(message, 14, 0xff8b9097, false);
        t.setPadding(dp(14), dp(10), dp(14), dp(14));
        return t;
    }

    private double capacity(String material) { return Math.max(1, db.capacity(material)); }

    private int soften(int color) {
        return Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));
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
