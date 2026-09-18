package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.Locale;

/**
 * حساب شركة النفط: شراء المواد يزيد المخزون ويزيد ما علينا،
 * والتوريد من الصندوق ينقص الدين وينقص النقد.
 */
public class SupplierActivity extends Activity {
    private Db db;
    private LinearLayout summaryBox, listBox;

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
        words.addView(text("حساب شركة النفط", 19, Color.WHITE, true));
        words.addView(text("مشتريات المواد وما وُرِّد من الصناديق", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));

        summaryBox = new LinearLayout(this);
        summaryBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(summaryBox);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(12), 0, dp(4));
        Button buy = action("＋  شراء مواد", true);
        buy.setOnClickListener(v -> buyDialog());
        actions.addView(buy, cell());
        Button pay = action("توريد مبلغ", false);
        pay.setOnClickListener(v -> payDialog());
        actions.addView(pay, cell());
        content.addView(actions);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
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
        summaryBox.removeAllViews();
        double owed = db.supplierBalance();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Util.round(Util.NAVY, dp(18)));
        card.addView(text(owed > 0.009 ? "المستحق لشركة النفط"
                : owed < -0.009 ? "رصيد لنا عند الشركة" : "الحساب مسدّد", 13, 0xffCFE2FA, false));
        TextView grand = text(money(Math.abs(owed)) + "  ر.ي", 30,
                owed > 0.009 ? 0xffFFB3BC : Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(10));
        card.addView(grand);

        LinearLayout stats = new LinearLayout(this);
        stats.addView(stat("إجمالي المشتريات", money(db.supplierBought())), cell());
        stats.addView(stat("إجمالي المورّد", money(db.supplierPaid())), cell());
        card.addView(stats);
        summaryBox.addView(card);

        listBox.removeAllViews();
        listBox.addView(sectionTitle("آخر الحركات"));
        int count = 0;
        try (Cursor c = db.supplierEntries(80)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                boolean isBuy = "BUY".equals(c.getString(1));
                String material = c.getString(2);
                double litres = c.getDouble(3);
                double unit = c.getDouble(4);
                double amount = c.getDouble(5);
                String boxName = c.getString(6);
                String note = c.getString(7);
                String date = c.getString(8);

                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(13), dp(12), dp(13), dp(12));
                row.setBackground(Util.round(Color.WHITE, dp(13)));
                row.setElevation(dp(1));

                View stripe = new View(this);
                stripe.setBackground(Util.round(isBuy ? Util.RED : Util.GREEN, dp(2)));
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(4), dp(36));
                sp.setMargins(0, 0, dp(11), 0);
                row.addView(stripe, sp);

                LinearLayout lines = new LinearLayout(this);
                lines.setOrientation(LinearLayout.VERTICAL);
                lines.addView(text(isBuy
                        ? "شراء " + material + "  " + money(litres) + " لتر"
                        : "توريد من " + (boxName.isEmpty() ? "صندوق" : boxName), 16, Util.NAVY, true));
                lines.addView(text(date + (isBuy && unit > 0 ? "  •  " + money(unit) + " ريال/لتر" : "")
                        + (note == null || note.trim().isEmpty() ? "" : "  •  " + note.trim()),
                        12, 0xff8b9097, false));
                row.addView(lines, new LinearLayout.LayoutParams(0, -2, 1));

                TextView value = text((isBuy ? "+" : "−") + money(amount), 16,
                        isBuy ? Util.RED : Util.GREEN, true);
                value.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(value);

                row.setOnLongClickListener(v -> { voidDialog(id); return true; });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(4), 0, dp(4));
                listBox.addView(row, lp);
            }
        }
        if (count == 0)
            listBox.addView(text("لا توجد حركات بعد.\nسجّل شراء مواد أو توريد مبلغ.",
                    14, 0xff777d84, false));
        else
            listBox.addView(text("اضغط مطوّلًا على أي حركة لإلغائها", 11, 0xff8b9097, false));
    }

    /** شراء مواد: اللترات وسعر اللتر، والقيمة تُحسب تلقائيًا. */
    private void buyDialog() {
        final Spinner picker = new Spinner(this);
        picker.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Db.MATERIALS));

        final EditText litres = new EditText(this);
        styleInput(litres);
        litres.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        litres.setHint("الكمية باللترات");

        final EditText unit = new EditText(this);
        styleInput(unit);
        unit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        unit.setHint("سعر شراء اللتر");
        double suggested = db.buyPrice(Db.MATERIALS[0]);
        if (suggested > 0) unit.setText(fmt(suggested));

        final EditText note = new EditText(this);
        styleInput(note);
        note.setHint("البيان (رقم الفاتورة مثلًا)");

        final TextView total = text("", 16, Util.NAVY, true);
        total.setGravity(Gravity.CENTER);
        total.setPadding(0, dp(8), 0, 0);

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            public void onTextChanged(CharSequence t, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                double q = Calc.number(litres.getText().toString());
                double u = Calc.number(unit.getText().toString());
                total.setText(q > 0 && u > 0 ? "القيمة " + money(q * u) + " ر.ي" : "");
            }
        };
        litres.addTextChangedListener(watcher);
        unit.addTextChangedListener(watcher);

        // تغيير المادة يقترح سعر شرائها المحفوظ.
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                double price = db.buyPrice(Db.MATERIALS[pos]);
                if (price > 0) unit.setText(fmt(price));
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        final String[] date = {ShiftDates.today()};
        final Button dateButton = action("التاريخ: " + date[0], false);
        dateButton.setOnClickListener(v -> pickDate(date, dateButton));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(text("المادة", 13, 0xff7c8186, false));
        box.addView(picker);
        box.addView(text("الكمية", 13, 0xff7c8186, false));
        box.addView(litres);
        box.addView(text("سعر اللتر", 13, 0xff7c8186, false));
        box.addView(unit);
        box.addView(note);
        box.addView(dateButton);
        box.addView(total);
        ScrollView form = new ScrollView(this);
        form.addView(box);

        new AlertDialog.Builder(this).setTitle("شراء من شركة النفط")
                .setMessage("تدخل المواد للمخزون، وقيمتها تُسجَّل دَينًا علينا للشركة.")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        db.buyFromSupplier(Db.MATERIALS[picker.getSelectedItemPosition()],
                                Calc.number(litres.getText().toString()),
                                Calc.number(unit.getText().toString()),
                                note.getText().toString(), date[0]);
                        Toast.makeText(this, "سُجّل الشراء", Toast.LENGTH_SHORT).show();
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    /** توريد مبلغ للشركة من أحد الصناديق. */
    private void payDialog() {
        final ArrayList<Long> ids = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
                names.add(c.getString(1) + "  (" + money(c.getDouble(6)) + ")");
            }
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, "لا توجد صناديق نشطة", Toast.LENGTH_LONG).show();
            return;
        }

        final Spinner picker = new Spinner(this);
        picker.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));

        final EditText amount = new EditText(this);
        styleInput(amount);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("المبلغ المورّد");
        double owed = db.supplierBalance();
        if (owed > 0) amount.setText(fmt(owed));

        final EditText note = new EditText(this);
        styleInput(note);
        note.setHint("البيان (اختياري)");

        final String[] date = {ShiftDates.today()};
        final Button dateButton = action("التاريخ: " + date[0], false);
        dateButton.setOnClickListener(v -> pickDate(date, dateButton));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(text("المستحق الآن " + money(Math.max(0, owed)) + " ر.ي", 14, Util.NAVY, true));
        box.addView(text("من صندوق", 13, 0xff7c8186, false));
        box.addView(picker);
        box.addView(text("المبلغ", 13, 0xff7c8186, false));
        box.addView(amount);
        box.addView(note);
        box.addView(dateButton);
        ScrollView form = new ScrollView(this);
        form.addView(box);

        new AlertDialog.Builder(this).setTitle("توريد لشركة النفط")
                .setMessage("يخرج المبلغ من الصندوق وينقص ما علينا للشركة.")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        db.paySupplier(ids.get(picker.getSelectedItemPosition()),
                                Calc.number(amount.getText().toString()),
                                note.getText().toString(), date[0]);
                        Toast.makeText(this, "سُجّل التوريد", Toast.LENGTH_SHORT).show();
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void voidDialog(final long id) {
        new AlertDialog.Builder(this).setTitle("إلغاء الحركة")
                .setMessage("يُعكس قيدها ويُزال أثرها من المخزون أو الصندوق.\nيبقى الأثر في سجل التدقيق.")
                .setPositiveButton("إلغاء الحركة", (d, w) -> {
                    try {
                        db.voidSupplierEntry(id);
                        Toast.makeText(this, "أُلغيت الحركة", Toast.LENGTH_SHORT).show();
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("تراجع", null).show();
    }

    private void pickDate(final String[] date, final Button button) {
        String[] parts = date[0].split("-");
        new android.app.DatePickerDialog(this, (picker, y, m, d) -> {
            date[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            button.setText("التاريخ: " + date[0]);
        }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])).show();
    }

    private View stat(String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.addView(text(title, 11, 0xffCFE2FA, false));
        TextView v = text(value, 15, Color.WHITE, true);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(v);
        return box;
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private TextView sectionTitle(String name) {
        TextView t = text(name, 16, Util.NAVY, true);
        t.setPadding(dp(4), dp(14), dp(4), dp(6));
        return t;
    }

    private Button action(String name, boolean primary) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : Util.NAVY);
        b.setStateListAnimator(null);
        b.setBackground(Util.round(primary ? Util.ACCENT : Util.ACCENT_SOFT, dp(12)));
        b.setPadding(dp(12), dp(11), dp(12), dp(11));
        return b;
    }

    private void styleInput(EditText e) {
        e.setTextSize(17);
        e.setTextColor(Util.NAVY);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bg = Util.round(Color.WHITE, dp(10));
        bg.setStroke(dp(1), 0xffdedfe2);
        e.setBackground(bg);
        e.setMinHeight(dp(46));
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

    private String fmt(double n) {
        return n == Math.rint(n) ? String.format(Locale.US, "%.0f", n) : String.format(Locale.US, "%.2f", n);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
