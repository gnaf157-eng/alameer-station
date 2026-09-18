package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.Locale;

/**
 * تصفية الحساب الوسيط: يعرض الحركات التي لم يُحدَّد طرفها المقابل،
 * ويصرّف كل واحدة إلى حسابها الصحيح حتى يصير الرصيد صفرًا.
 */
public class SuspenseActivity extends Activity {
    private Db db;
    private LinearLayout listBox;
    private TextView balanceText, stateText;

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
        words.addView(text("الحساب الوسيط", 19, Color.WHITE, true));
        words.addView(text("حركات لم يُحدَّد طرفها — صرّفها حتى يصير الرصيد صفرًا", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Util.round(Color.WHITE, dp(16)));
        card.setElevation(dp(2));
        card.addView(text("الرصيد المعلّق", 12, 0xff7c8186, false));
        balanceText = text("", 26, Util.NAVY, true);
        balanceText.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(balanceText);
        stateText = text("", 13, 0xff7c8186, true);
        card.addView(stateText);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        Button clean = new Button(this);
        clean.setText("تنظيف التصريفات المكرّرة");
        clean.setAllCaps(false);
        clean.setTextSize(14);
        clean.setTextColor(Util.NAVY);
        clean.setBackground(Util.round(Util.ACCENT_SOFT, dp(12)));
        clean.setPadding(dp(14), dp(11), dp(14), dp(11));
        clean.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("تنظيف التصريفات المكرّرة")
                .setMessage("يُلغى أثر التصريفات التي تكرّرت بالخطأ، وتُحذف قيود الديون المكرّرة الناتجة عنها.\n\n"
                        + "القيود الأصلية تبقى، والأثر محفوظ في سجل التدقيق.")
                .setPositiveButton("تنظيف", (d, w) -> {
                    int n = 0;
                    try { n = db.cleanSuspenseMess(); }
                    catch (Exception e) { Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show(); }
                    Toast.makeText(this, "نُظّف " + n + " سطرًا", Toast.LENGTH_LONG).show();
                    refresh();
                })
                .setNegativeButton("إلغاء", null).show());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(10), 0, 0);
        content.addView(clean, cp);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(14), 0, 0);
        content.addView(listBox);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
    }

    @Override protected void onResume() {
        super.onResume();
        if (listBox != null) refresh();
    }

    private void refresh() {
        double balance = db.suspenseBalance();
        boolean clean = Math.abs(balance) < 0.01;
        balanceText.setText(money(Math.abs(balance)) + " ر.ي");
        balanceText.setTextColor(clean ? Util.GREEN : Util.RED);
        stateText.setText(clean ? "✓ الحساب الوسيط مُصفّى بالكامل"
                : (balance > 0 ? "رصيد مدين معلّق" : "رصيد دائن معلّق") + " — يحتاج تصريفًا");
        stateText.setTextColor(clean ? Util.GREEN : Util.RED);

        listBox.removeAllViews();
        int count = 0;
        // ملخّص المصادر: كم حركة من كل شاشة.
        final java.util.LinkedHashMap<String, Integer> bySource = new java.util.LinkedHashMap<>();
        try (Cursor c = db.suspenseEntries()) {
            while (c.moveToNext()) {
                count++;
                final long entryId = c.getLong(0);
                final String memo = c.getString(1);
                String date = c.getString(2);
                final boolean suspenseDebit = "DEBIT".equals(c.getString(3));
                double amount = c.getDouble(4);
                String other = c.getString(5);
                final String source = c.getString(6);
                long recordId = c.getLong(7);
                final String recordName = c.getString(8);
                String recordNote = c.getString(9);
                String actor = c.getString(10);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x18000000),
                        Util.round(Color.WHITE, dp(14)), null));
                row.setClickable(true);
                row.setOnClickListener(v -> settleDialog(entryId, memo, source, recordName, suspenseDebit));

                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout lines = new LinearLayout(this);
                lines.setOrientation(LinearLayout.VERTICAL);
                lines.addView(text(memo.isEmpty() ? "حركة #" + entryId : memo, 16, Util.NAVY, true));
                // المصدر واضحًا: من أي شاشة جاءت الحركة وفي أي سجل.
                String origin = sourceLabel(source)
                        + (recordName == null || recordName.isEmpty() ? "" : " — " + recordName)
                        + (recordId > 0 ? "  #" + recordId : "");
                TextView originText = text(origin, 12, Util.ACCENT, true);
                lines.addView(originText);
                lines.addView(text(date + "  •  مقابل " + other
                        + (actor == null || actor.isEmpty() ? "" : "  •  " + actor), 11, 0xff8b9097, false));
                if (recordNote != null && !recordNote.trim().isEmpty() && !recordNote.equals(memo))
                    lines.addView(text("البيان: " + recordNote.trim(), 11, 0xff8b9097, false));
                top.addView(lines, new LinearLayout.LayoutParams(0, -2, 1));
                TextView value = text(money(amount), 16,
                        suspenseDebit ? Util.RED : Util.GREEN, true);
                value.setTextDirection(View.TEXT_DIRECTION_LTR);
                top.addView(value);
                row.addView(top);
                row.addView(text(suspenseDebit ? "الوسيط مدين — اضغط لتصريفه"
                        : "الوسيط دائن — اضغط لتصريفه", 12, Util.ACCENT, true));

                String key = sourceLabel(source);
                bySource.put(key, (bySource.containsKey(key) ? bySource.get(key) : 0) + 1);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(4), 0, dp(4));
                listBox.addView(row, lp);
            }
        }
        if (count == 0) {
            listBox.addView(text("لا توجد حركات معلّقة.\nكل الحركات لها طرفها الصحيح.",
                    14, 0xff777d84, false));
            return;
        }
        StringBuilder sum = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : bySource.entrySet()) {
            if (sum.length() > 0) sum.append("  •  ");
            sum.append(e.getKey()).append(": ").append(e.getValue());
        }
        TextView summary = text(sum.toString(), 12, Util.NAVY, true);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(10), dp(9), dp(10), dp(9));
        summary.setBackground(Util.round(Util.ACCENT_SOFT, dp(11)));
        listBox.addView(summary, 0);
    }

    /** يختار الحساب الصحيح للحركة ثم يصرّفها إليه. */
    /** اسم الشاشة التي جاءت منها الحركة. */
    private String sourceLabel(String source) {
        if (source == null) return "مصدر غير معروف";
        switch (source) {
            case "CASHBOX": return "من حركة الصناديق";
            case "DEBT": return "من حركة الديون";
            case "EXPENSE": return "من حركة المخاريج";
            case "SHIFT": return "من وردية";
            default: return "مصدر غير معروف";
        }
    }

    /**
     * الحساب المرجَّح حسب المصدر والجهة، فيُختار مسبقًا ويوفّر على المستخدم التفكير.
     * وارد صندوق غالبًا سداد مدين أو مبيعات، وصادره غالبًا مخاريج.
     */
    private int suggestTarget(String source, boolean suspenseDebit) {
        String[] targets = Db.SUSPENSE_TARGETS;
        String want;
        if ("CASHBOX".equals(source)) want = suspenseDebit ? Journal.EXPENSE : Journal.RECEIVABLE;
        else if ("DEBT".equals(source)) want = Journal.CASH;
        else if ("EXPENSE".equals(source)) want = Journal.CASH;
        else want = Journal.RECEIVABLE;
        for (int i = 0; i < targets.length; i++) if (targets[i].equals(want)) return i;
        return 0;
    }

    private void settleDialog(final long entryId, final String memo,
                              final String source, final String recordName, final boolean suspenseDebit) {
        final String[] targets = Db.SUSPENSE_TARGETS;
        final String[] labels = new String[targets.length];
        for (int i = 0; i < targets.length; i++) labels[i] = targets[i];

        final int suggested = suggestTarget(source, suspenseDebit);
        final int[] chosen = {suggested};
        final EditText party = new EditText(this);
        party.setHint("اسم المدين (عند اختيار ذمم المدينين)");
        party.setTextSize(16);
        party.setSingleLine(true);
        // الاسم مأخوذ من بيان الحركة إن أمكن.
        party.setText(recordName != null && !recordName.isEmpty() ? recordName : guessName(memo));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(text(sourceLabel(source)
                + (recordName == null || recordName.isEmpty() ? "" : " — " + recordName), 13, Util.ACCENT, true));
        wrap.addView(text(suspenseDebit
                ? "الوسيط مدين: المال خرج، فما وجهته؟"
                : "الوسيط دائن: المال دخل، فمن أين جاء؟", 13, 0xff7c8186, false));

        final RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < labels.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setText(labels[i]);
            b.setTextSize(16);
            b.setId(1000 + i);
            group.addView(b);
        }
        group.check(1000 + suggested);
        group.setOnCheckedChangeListener((g, id) -> chosen[0] = id - 1000);
        wrap.addView(group);
        wrap.addView(party);

        ScrollView form = new ScrollView(this);
        form.addView(wrap);

        new AlertDialog.Builder(this)
                .setTitle("تصريف: " + (memo.isEmpty() ? "حركة #" + entryId : memo))
                .setView(form)
                .setPositiveButton("تصريف", (d, w) -> {
                    String target = targets[chosen[0]];
                    String name = party.getText().toString().trim();
                    if (Journal.RECEIVABLE.equals(target)) {
                        if (name.isEmpty()) {
                            Toast.makeText(this, "اكتب اسم المدين", Toast.LENGTH_LONG).show();
                            return;
                        }
                        db.ensureDebtor(name);
                    }
                    try {
                        db.settleSuspense(entryId, target, name, "تصريف إلى " + target);
                        Toast.makeText(this, "صُرّفت الحركة إلى " + target, Toast.LENGTH_LONG).show();
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** يستخرج اسمًا محتملًا من بيان الحركة. */
    private String guessName(String memo) {
        if (memo == null) return "";
        String s = memo.trim();
        for (String prefix : new String[]{"وارد ", "صادر ", "سداد من ", "دين على ", "مخاريج: "})
            if (s.startsWith(prefix)) return s.substring(prefix.length()).trim();
        return s;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setPadding(0, dp(2), 0, dp(2));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
