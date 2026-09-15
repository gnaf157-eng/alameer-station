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

import java.util.Locale;

/** حركة الديون: دين وسداد ورصيد كل مدين بالريال اليمني. */
public class DebtActivity extends Activity {
    private Db db;
    private LinearLayout summaryBox, listBox, entriesBox;
    private TextView totalText, entriesTitle;
    private long filterDebtor = 0;

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
        words.addView(text("حركة الديون", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + " • الريال اليمني", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        totalText = text("", 17, 0xffCFE2FA, true);
        header.addView(totalText);
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(20));

        summaryBox = new LinearLayout(this);
        summaryBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(summaryBox, space());

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox, space());

        Button newDebtor = action("＋  إضافة مدين جديد", false);
        newDebtor.setOnClickListener(v -> debtorDialog(0, "", "", 0));
        content.addView(newDebtor, space());

        entriesTitle = sectionTitle("آخر الحركات");
        content.addView(entriesTitle);
        entriesBox = panel();
        content.addView(entriesBox, space());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(6), 0, 0);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
        refresh();
    }

    private void refresh() {
        refreshSummary();
        refreshDebtors();
        refreshEntries();
    }

    /** بطاقة إجمالي الديون غير المسدّدة. */
    private void refreshSummary() {
        summaryBox.removeAllViews();
        double total = 0, debt = 0, paid = 0;
        int count = 0, settled = 0;
        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                count++;
                total += c.getDouble(7);
                debt += c.getDouble(5);
                paid += c.getDouble(6);
                if (Math.abs(c.getDouble(7)) < 0.01) settled++;
            }
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Util.round(Util.NAVY, dp(18)));
        card.addView(text("إجمالي الديون غير المسدّدة", 13, 0xffCFE2FA, false));
        TextView grand = text(money(total) + "  ر.ي", 30, Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(12));
        card.addView(grand);
        LinearLayout stats = new LinearLayout(this);
        stats.addView(stat("المدينون", String.valueOf(count)), cell());
        stats.addView(stat("إجمالي المسدّد", money(paid)), cell());
        stats.addView(stat("مسدّدون بالكامل", String.valueOf(settled)), cell());
        card.addView(stats);
        summaryBox.addView(card);
        totalText.setText(count == 0 ? "" : money(total) + " ر.ي");
    }

    /** بطاقة لكل مدين: الضغط عليها يسجّل حركة، والضغط المطوّل يفتح خياراته. */
    private void refreshDebtors() {
        listBox.removeAllViews();
        listBox.addView(sectionTitle("المدينون — اضغط على المدين لتسجيل حركة"));
        int count = 0;
        try (Cursor c = db.debtors(false)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                final String name = c.getString(1);
                final String phone = c.getString(2);
                final double opening = c.getDouble(3);
                final boolean active = c.getInt(4) == 1;
                final double balance = c.getDouble(7);
                final boolean clear = Math.abs(balance) < 0.01;

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(16), dp(15), dp(16), dp(15));
                card.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x22000000),
                        Util.round(Color.WHITE, dp(16)), null));
                card.setElevation(dp(2));
                card.setClickable(true);

                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(text(name, 18, active ? Util.NAVY : 0xff9aa0a6, true));
                String note = !active ? "موقوف — لا تُسجَّل عليه حركات"
                        : clear ? "سدّد كامل دينه" : phone.isEmpty() ? "اضغط لتسجيل دين أو سداد" : phone;
                words.addView(text(note, 11, clear && active ? Util.GREEN : 0xff8b9097, false));
                top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                LinearLayout amountBox = new LinearLayout(this);
                amountBox.setOrientation(LinearLayout.VERTICAL);
                TextView amount = text(money(balance), 22, clear ? Util.GREEN : Util.RED, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                amount.setGravity(Gravity.LEFT);
                amountBox.addView(amount);
                TextView unit = text(clear ? "لا يوجد دين" : "ريال عليه", 10, 0xff8b9097, false);
                unit.setGravity(Gravity.LEFT);
                amountBox.addView(unit);
                top.addView(amountBox);
                card.addView(top);

                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
                lp.setMargins(0, dp(12), 0, dp(10));
                card.addView(line, lp);

                LinearLayout foot = new LinearLayout(this);
                foot.addView(chip("افتتاحي", money(opening), Util.NAVY), cell());
                foot.addView(chip("ديون", money(c.getDouble(5)), Util.RED), cell());
                foot.addView(chip("مسدّد", money(c.getDouble(6)), Util.GREEN), cell());
                card.addView(foot);

                card.setOnClickListener(v -> {
                    if (!active) { Toast.makeText(this, "المدين موقوف. فعّله أولًا.", Toast.LENGTH_SHORT).show(); return; }
                    entryDialog(id, name);
                });
                card.setOnLongClickListener(v -> { debtorOptions(id, name, phone, opening); return true; });
                listBox.addView(card, space());
            }
        }
        if (count == 0) {
            LinearLayout empty = panel();
            empty.addView(text("لم يُضف مدينون بعد. أضف مدينًا لتبدأ تسجيل الديون والسداد.", 15, 0xff777d84, false));
            listBox.addView(empty, space());
        } else {
            listBox.addView(text("اضغط مطوّلًا على المدين لتعديله أو إيقافه", 11, 0xff8b9097, false));
        }
    }

    /** آخر الحركات مع الحذف بضغطة مطوّلة. */
    private void refreshEntries() {
        entriesBox.removeAllViews();
        entriesTitle.setText(filterDebtor > 0 ? "حركات المدين المختار" : "آخر الحركات");
        int count = 0;
        try (Cursor c = db.debtEntries(filterDebtor, 40)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                boolean isDebt = "DEBT".equals(c.getString(1));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                String note = c.getString(3);
                words.addView(text(note.isEmpty() ? (isDebt ? "دين جديد" : "سداد") : note, 16, Util.NAVY, true));
                words.addView(text(c.getString(5) + "  •  " + c.getString(4), 12, 0xff7c8186, false));
                TextView badge = text(isDebt ? "دين" : "سداد", 11, isDebt ? Util.RED : Util.GREEN, true);
                badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                badge.setBackground(Util.round(isDebt ? 0xfffbe9e9 : 0xffe7f1e7, dp(8)));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
                bp.setMargins(0, dp(6), 0, 0);
                words.addView(badge, bp);
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amount = text((isDebt ? "+ " : "− ") + money(c.getDouble(2)), 17, isDebt ? Util.RED : Util.GREEN, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(amount);
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this).setTitle("حذف الحركة")
                            .setMessage("سيُحذف هذا السطر ويتغيّر رصيد المدين.")
                            .setPositiveButton("حذف", (d, w) -> { db.deleteDebtEntry(id); refresh(); })
                            .setNegativeButton("إلغاء", null).show();
                    return true;
                });
                entriesBox.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                entriesBox.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (count == 0) entriesBox.addView(text("لا توجد حركات مسجّلة بعد.", 15, 0xff777d84, false));
        else entriesBox.addView(text("اضغط مطوّلًا على أي حركة لحذفها", 11, 0xff8b9097, false), space());
    }

    /** إضافة مدين أو تعديله. */
    private void debtorDialog(long id, String name, String phone, double opening) {
        final boolean isNew = id == 0;
        EditText nameInput = new EditText(this);
        styleInput(nameInput);
        nameInput.setHint("اسم المدين");
        nameInput.setText(name);
        EditText phoneInput = new EditText(this);
        styleInput(phoneInput);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setHint("رقم الهاتف (اختياري)");
        phoneInput.setText(phone);
        EditText openingInput = new EditText(this);
        styleInput(openingInput);
        openingInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        openingInput.setHint("الدين الافتتاحي");
        if (!isNew) openingInput.setText(trim(opening));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(text("اسم المدين", 13, 0xff7c8186, false));
        box.addView(nameInput);
        box.addView(text("رقم الهاتف", 13, 0xff7c8186, false), space());
        box.addView(phoneInput);
        box.addView(text("الدين الافتتاحي (ريال يمني)", 13, 0xff7c8186, false), space());
        box.addView(openingInput);
        ScrollView form = new ScrollView(this);
        form.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "مدين جديد" : "تعديل المدين")
                .setView(form)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = nameInput.getText().toString().trim();
            if (value.isEmpty()) { nameInput.setError("اكتب اسم المدين"); return; }
            double start = Calc.number(openingInput.getText().toString());
            try {
                if (isNew) db.addDebtor(value, phoneInput.getText().toString(), start);
                else db.updateDebtor(id, value, phoneInput.getText().toString(), start);
            } catch (IllegalArgumentException e) { nameInput.setError(e.getMessage()); return; }
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    /** خيارات المدين: تعديل، عرض حركاته، إيقاف، حذف. */
    private void debtorOptions(long id, String name, String phone, double opening) {
        int entries = db.debtEntryCount(id);
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"تعديل البيانات", "عرض حركات هذا المدين", "كل الحركات", "إيقاف المدين", "تفعيل المدين", "حذف المدين"},
                        (d, which) -> {
                            if (which == 0) debtorDialog(id, name, phone, opening);
                            else if (which == 1) { filterDebtor = id; refreshEntries(); Toast.makeText(this, "عرض حركات " + name, Toast.LENGTH_SHORT).show(); }
                            else if (which == 2) { filterDebtor = 0; refreshEntries(); }
                            else if (which == 3) { db.setDebtorActive(id, false); refresh(); }
                            else if (which == 4) { db.setDebtorActive(id, true); refresh(); }
                            else {
                                if (entries > 0) {
                                    new AlertDialog.Builder(this).setTitle("لا يمكن الحذف")
                                            .setMessage("على هذا المدين " + entries + " حركة مسجّلة. أوقفه بدل حذفه حتى لا يضيع الأرشيف.")
                                            .setPositiveButton("حسنًا", null).show();
                                    return;
                                }
                                db.deleteDebtor(id);
                                refresh();
                            }
                        }).show();
    }

    /** تسجيل دين أو سداد، مع عرض رصيد المدين قبل الحفظ. */
    private void entryDialog(final long debtorId, String debtorName) {
        final double current = db.debtorBalance(debtorId);

        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        balanceCard.setBackground(Util.round(Util.ACCENT_SOFT, dp(14)));
        balanceCard.addView(text("الدين الحالي على " + debtorName, 12, 0xff5a6672, false));
        TextView balanceText = text(money(current) + "  ر.ي", 24, Math.abs(current) < 0.01 ? Util.GREEN : Util.RED, true);
        balanceText.setTextDirection(View.TEXT_DIRECTION_LTR);
        balanceCard.addView(balanceText);
        final TextView afterText = text("", 13, 0xff5a6672, true);
        balanceCard.addView(afterText);

        Spinner kind = new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"دين جديد (يزيد عليه)", "سداد (ينقص عنه)"}));

        final EditText amount = new EditText(this);
        styleInput(amount);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("المبلغ بالريال اليمني");

        EditText note = new EditText(this);
        styleInput(note);
        note.setHint("البيان (اختياري)");

        final Button dateButton = action("", false);
        final String[] date = {ShiftDates.today()};
        dateButton.setText("التاريخ: " + date[0]);
        dateButton.setOnClickListener(v -> {
            String[] parts = date[0].split("-");
            new android.app.DatePickerDialog(this, (picker, y, m, d) -> {
                date[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                dateButton.setText("التاريخ: " + date[0]);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])).show();
        });

        final Runnable preview = () -> {
            double value = Calc.number(amount.getText().toString());
            if (!(value > 0)) { afterText.setText(""); return; }
            double after = kind.getSelectedItemPosition() == 0 ? current + value : current - value;
            afterText.setText("الدين بعد الحركة: " + money(after) + " ر.ي");
            afterText.setTextColor(after > 0.01 ? Util.RED : Util.GREEN);
        };
        amount.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            public void onTextChanged(CharSequence t, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) { preview.run(); }
        });
        kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { preview.run(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(10), dp(24), 0);
        box.addView(balanceCard);
        box.addView(text("نوع الحركة", 13, 0xff7c8186, false), space());
        box.addView(kind);
        box.addView(text("المبلغ", 13, 0xff7c8186, false), space());
        box.addView(amount);
        box.addView(text("البيان", 13, 0xff7c8186, false), space());
        box.addView(note);
        box.addView(dateButton, space());
        ScrollView form = new ScrollView(this);
        form.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(debtorName)
                .setView(form)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double value = Calc.number(amount.getText().toString());
            if (!(value > 0)) { amount.setError("اكتب مبلغًا أكبر من صفر"); return; }
            String direction = kind.getSelectedItemPosition() == 0 ? "DEBT" : "PAID";
            if ("PAID".equals(direction) && current < value) {
                new AlertDialog.Builder(this).setTitle("السداد أكبر من الدين")
                        .setMessage("الدين عليه " + money(current) + " ر.ي وأنت تسجّل سداد " + money(value) + " ر.ي.\nسيصبح رصيده سالبًا. هل تريد التسجيل؟")
                        .setPositiveButton("سجّل", (d, w) -> {
                            db.addDebtEntry(debtorId, direction, value, note.getText().toString(), date[0]);
                            dialog.dismiss();
                            refresh();
                        })
                        .setNegativeButton("رجوع", null).show();
                return;
            }
            try {
                db.addDebtEntry(debtorId, direction, value, note.getText().toString(), date[0]);
            } catch (IllegalArgumentException e) { amount.setError(e.getMessage()); return; }
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private LinearLayout stat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(9), dp(8), dp(9));
        box.setBackground(Util.round(0x22FFFFFF, dp(11)));
        TextView v = text(value, 15, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(v);
        TextView l = text(label, 10, 0xffCFE2FA, false);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    private LinearLayout chip(String label, String value, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.setBackground(Util.round(0xfff4f7fb, dp(10)));
        TextView v = text(value, 14, color, true);
        v.setGravity(Gravity.CENTER);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(v);
        TextView l = text(label, 10, 0xff8b9097, false);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    private TextView sectionTitle(String name) {
        TextView t = text(name, 16, Util.NAVY, true);
        t.setPadding(dp(4), dp(16), dp(4), dp(8));
        return t;
    }

    private LinearLayout panel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(Util.round(Color.WHITE, dp(16)));
        return box;
    }

    private Button action(String name, boolean primary) {
        Button b = new Button(this);
        b.setText(name);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : Util.NAVY);
        b.setTypeface(android.graphics.Typeface.DEFAULT, primary ? 1 : 0);
        b.setMinHeight(dp(50));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setStateListAnimator(null);
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(primary ? Util.ACCENT : Util.ACCENT_SOFT, dp(12)), null));
        return b;
    }

    private void styleInput(EditText e) {
        e.setTextSize(18);
        e.setTextColor(Util.NAVY);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bg = Util.round(Color.WHITE, dp(10));
        bg.setStroke(dp(1), 0xffdedfe2);
        e.setBackground(bg);
        e.setMinHeight(dp(48));
    }

    private LinearLayout.LayoutParams space() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(7), 0, dp(7));
        return p;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    private String trim(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%.0f" : "%.2f", value);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
