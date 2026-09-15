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

/** لوحة التحكم: ملخّص ملوّن ومتحرّك للصناديق والمواد والديون والمخاريج. */
public class ControlPanelActivity extends Activity {
    private Db db;
    private LinearLayout content;

    /** حدود التلوين — تُقرأ من الإعدادات لاحقًا إن لزم. */
    private static final double LOW_CASH = 50000;      // صندوق تحت هذا الرقم يُعد منخفضًا
    private static final double LOW_LITRES = 2000;     // مخزون تحت هذا الرقم يُعد حرجًا
    private static final double WARN_LITRES = 6000;    // ومن هنا إلى أعلى يُعد منتبهًا
    private static final double BIG_DEBT = 100000;     // مدين فوق هذا الرقم يُعد ثقيلًا
    private static final int STALE_DAYS = 21;          // زبون لم يتحرك منذ هذه المدة

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(16));
        header.setBackgroundColor(Util.NAVY);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("لوحة التحكم", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + "  •  " + ShiftDates.today(), 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(6), dp(14), dp(24));

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
        delay = add(alerts(), delay);
        delay = add(cashSection(), delay);
        delay = add(materialSection(), delay);
        delay = add(debtSection(), delay);
        add(expenseSection(), delay);
        TextView note = text("الأرقام محدّثة الآن  •  الألوان تشير إلى الحالة", 11, 0xff8b9097, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(18), 0, 0);
        content.addView(note);
    }

    /** كل قسم يدخل بحركة صعود خفيفة متتابعة. */
    private int add(View section, int delay) {
        if (section == null) return delay;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(8));
        content.addView(section, p);
        section.setAlpha(0f);
        section.setTranslationY(dp(22));
        section.animate().alpha(1f).translationY(0)
                .setStartDelay(delay).setDuration(340)
                .setInterpolator(new DecelerateInterpolator()).start();
        return delay + 70;
    }

    // ==================== التنبيهات ====================

    /** شريط التنبيهات الحمراء والبرتقالية في أعلى اللوحة. */
    private View alerts() {
        List<String[]> found = new ArrayList<>();   // {نص, "red"|"amber"}

        for (String material : Db.MATERIALS) {
            double left = db.materialSummary(material)[3];
            if (left <= 0) found.add(new String[]{"نفد " + material + " من المخزون", "red"});
            else if (left < LOW_LITRES) found.add(new String[]{
                    material + " على وشك النفاد — بقي " + money(left) + " لتر", "red"});
            else if (left < WARN_LITRES) found.add(new String[]{
                    material + " منخفض — " + money(left) + " لتر", "amber"});
        }
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                double balance = c.getDouble(6);
                if (balance < 0) found.add(new String[]{"صندوق " + c.getString(1) + " رصيده سالب", "red"});
                else if (balance < LOW_CASH) found.add(new String[]{
                        "صندوق " + c.getString(1) + " منخفض — " + money(balance) + " ر.ي", "amber"});
            }
        }
        if (db.defaultCashbox() <= 0)
            found.add(new String[]{"لم تختر صندوق الورديات — النقد لا يُرحَّل", "amber"});

        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                double balance = c.getDouble(7);
                if (balance <= 0.009) continue;
                long id = c.getLong(0);
                String name = c.getString(1);
                if (balance > BIG_DEBT) found.add(new String[]{
                        "دين ثقيل على " + name + " — " + money(balance) + " ر.ي", "amber"});
                int idle = daysSince(db.debtorLastActivity(id));
                if (idle >= STALE_DAYS) found.add(new String[]{
                        name + " لم يتعامل منذ " + idle + " يومًا — اتصل به", "amber"});
            }
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        if (found.isEmpty()) {
            LinearLayout ok = card(0xffe9f6f1);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(dot(Util.GREEN));
            TextView t = text("كل شيء تحت السيطرة — لا تنبيهات", 15, Util.GREEN, true);
            t.setPadding(dp(10), 0, 0, 0);
            line.addView(t);
            ok.addView(line);
            box.addView(ok);
            return box;
        }
        box.addView(sectionTitle("تنبيهات (" + found.size() + ")"));
        for (String[] alert : found) {
            boolean red = "red".equals(alert[1]);
            int tint = red ? Util.RED : 0xffB86A00;
            LinearLayout row = card(red ? 0xfffdeef0 : 0xfffff4e2);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            View bar = new View(this);
            bar.setBackground(Util.round(tint, dp(3)));
            row.addView(bar, new LinearLayout.LayoutParams(dp(5), dp(34)));
            TextView t = text(alert[0], 14, tint, true);
            t.setPadding(dp(12), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(0, dp(4), 0, dp(4));
            box.addView(row, p);
            if (red) pulse(bar);
        }
        return box;
    }

    // ==================== الصناديق ====================

    private View cashSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionTitle("ملخّص الصناديق"));
        double total = db.cashboxesTotal();
        box.addView(bigCard("إجمالي النقد", total, "ر.ي",
                total < 0 ? Util.RED : total < LOW_CASH ? 0xffB86A00 : Util.GREEN));

        int count = 0;
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                count++;
                double balance = c.getDouble(6);
                int tint = balance < 0 ? Util.RED : balance < LOW_CASH ? 0xffB86A00 : Util.GREEN;
                String state = balance < 0 ? "سالب" : balance < LOW_CASH ? "منخفض" : "جيد";
                int share = total > 0 ? (int) Math.round(Math.max(0, balance) * 100 / total) : 0;
                box.addView(lineCard(c.getString(1), money(balance) + " ر.ي", state, tint, share));
            }
        }
        if (count == 0) box.addView(emptyCard("لم تُنشئ صناديق بعد."));
        return box;
    }

    // ==================== المواد ====================

    private View materialSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionTitle("المواد المتبقية"));
        double all = 0;
        for (String material : Db.MATERIALS) all += Math.max(0, db.materialSummary(material)[3]);
        for (String material : Db.MATERIALS) {
            double[] summary = db.materialSummary(material);
            double left = summary[3];
            int tint = left <= 0 ? Util.RED : left < LOW_LITRES ? Util.RED
                    : left < WARN_LITRES ? 0xffB86A00 : Util.GREEN;
            String state = left <= 0 ? "نفد" : left < LOW_LITRES ? "حرج"
                    : left < WARN_LITRES ? "منخفض" : "متوفر";
            int share = all > 0 ? (int) Math.round(Math.max(0, left) * 100 / all) : 0;
            box.addView(lineCard(material, money(left) + " لتر", state, tint, share));
        }
        return box;
    }

    // ==================== الديون ====================

    private View debtSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionTitle("ملخّص الديون"));
        double total = db.debtsTotal();
        box.addView(bigCard("إجمالي المستحق على الزبائن", total, "ر.ي",
                total <= 0.009 ? Util.GREEN : total > BIG_DEBT * 3 ? Util.RED : 0xffB86A00));

        // أكبر خمسة مدينين فقط، لتبقى اللوحة مقروءة.
        List<String[]> top = new ArrayList<>();
        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                double balance = c.getDouble(7);
                if (balance <= 0.009) continue;
                top.add(new String[]{c.getString(1), String.valueOf(balance),
                        String.valueOf(daysSince(db.debtorLastActivity(c.getLong(0))))});
            }
        }
        top.sort((a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));
        if (top.isEmpty()) box.addView(emptyCard("لا ديون مستحقة — ممتاز."));
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            double balance = Double.parseDouble(top.get(i)[1]);
            int idle = Integer.parseInt(top.get(i)[2]);
            int tint = balance > BIG_DEBT ? Util.RED : idle >= STALE_DAYS ? 0xffB86A00 : Util.NAVY;
            String state = balance > BIG_DEBT ? "ثقيل" : idle >= STALE_DAYS ? "راكد " + idle + "ي" : "طبيعي";
            int share = total > 0 ? (int) Math.round(balance * 100 / total) : 0;
            box.addView(lineCard(top.get(i)[0], money(balance) + " ر.ي", state, tint, share));
        }
        if (top.size() > 5) box.addView(text("و " + (top.size() - 5) + " مدينًا آخر", 11, 0xff8b9097, false));
        return box;
    }

    // ==================== المخاريج ====================

    private View expenseSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionTitle("المخاريج"));
        double total = db.expensesTotal();
        double month = db.expensesInMonth(ShiftDates.today().substring(0, 7));
        box.addView(bigCard("مخاريج هذا الشهر", month, "ر.ي", month > 0 ? Util.RED : Util.GREEN));
        int shown = 0;
        try (Cursor c = db.expenseCategories()) {
            while (c.moveToNext() && shown < 4) {
                shown++;
                double sum = c.getDouble(1);
                int share = total > 0 ? (int) Math.round(sum * 100 / total) : 0;
                box.addView(lineCard(c.getString(0), money(sum) + " ر.ي", share + "٪", Util.ACCENT, share));
            }
        }
        if (shown == 0) box.addView(emptyCard("لا مخاريج مسجّلة بعد."));
        return box;
    }

    // ==================== لبنات البناء ====================

    /** بطاقة كبيرة برقم يعدّ تصاعديًا. */
    private View bigCard(String label, double value, String unit, int tint) {
        LinearLayout card = card(Color.WHITE);
        card.addView(text(label, 12, 0xff7c8186, false));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView number = text("0", 30, tint, true);
        number.setTextDirection(View.TEXT_DIRECTION_LTR);
        row.addView(number);
        TextView u = text("  " + unit, 13, 0xff8b9097, false);
        u.setPadding(dp(6), dp(8), 0, 0);
        row.addView(u);
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        row.addView(dot(tint));
        card.addView(row);
        countUp(number, value);
        return card;
    }

    /** سطر: اسم، حالة ملوّنة، مبلغ، وشريط نسبة. */
    private View lineCard(String name, String value, String state, int tint, int share) {
        LinearLayout card = card(Color.WHITE);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(dot(tint));
        TextView title = text(name, 16, Util.NAVY, true);
        title.setPadding(dp(9), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(state, 10, tint, true);
        badge.setPadding(dp(9), dp(3), dp(9), dp(3));
        badge.setBackground(Util.round(soften(tint), dp(8)));
        top.addView(badge);
        card.addView(top);

        TextView amount = text(value, 19, tint, true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        amount.setPadding(dp(17), dp(6), 0, dp(8));
        card.addView(amount);

        // شريط ينمو بحركة من الصفر إلى نسبته.
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(Util.round(0xffedf0f3, dp(4)));
        final View fill = new View(this);
        fill.setBackground(Util.round(tint, dp(4)));
        final LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(7), 0.01f);
        bar.addView(fill, fp);
        final View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(7), 100f));
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(7)));

        final float target = Math.max(0.01f, Math.min(100, share));
        ValueAnimator grow = ValueAnimator.ofFloat(0.01f, target);
        grow.setDuration(620);
        grow.setStartDelay(160);
        grow.setInterpolator(new DecelerateInterpolator());
        grow.addUpdateListener(a -> {
            fp.weight = (Float) a.getAnimatedValue();
            fill.setLayoutParams(fp);
        });
        grow.start();
        return card;
    }

    /** عدّاد تصاعدي حتى القيمة النهائية. */
    private void countUp(final TextView view, final double value) {
        if (Math.abs(value) < 0.01) { view.setText("0"); return; }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) value);
        animator.setDuration(760);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> view.setText(money(((Float) a.getAnimatedValue()).doubleValue())));
        animator.start();
    }

    /** نبضة هادئة تلفت النظر للتنبيه الأحمر. */
    private void pulse(final View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0.25f, 1f);
        animator.setDuration(1400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> view.setAlpha((Float) a.getAnimatedValue()));
        animator.start();
    }

    private View dot(int color) {
        View v = new View(this);
        v.setBackground(Util.round(color, dp(5)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(10), dp(10));
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout card(int background) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(Util.round(background, dp(16)));
        if (background == Color.WHITE) box.setElevation(dp(2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(5), 0, dp(5));
        box.setLayoutParams(p);
        return box;
    }

    private View emptyCard(String message) {
        LinearLayout box = card(Color.WHITE);
        box.addView(text(message, 14, 0xff8b9097, false));
        return box;
    }

    private TextView sectionTitle(String name) {
        TextView t = text(name, 16, Util.NAVY, true);
        t.setPadding(dp(4), dp(14), dp(4), dp(4));
        return t;
    }

    /** لون فاتح جدًا مشتق من لون الحالة لخلفية الشارة. */
    private int soften(int color) {
        return Color.argb(28, Color.red(color), Color.green(color), Color.blue(color));
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
