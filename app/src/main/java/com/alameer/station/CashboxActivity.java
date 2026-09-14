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
import java.util.List;
import java.util.Locale;

/** مطابقة الصناديق: الوارد والصادر وأرصدة كل صندوق بالريال اليمني. */
public class CashboxActivity extends Activity {
    private Db db;
    private LinearLayout summaryBox, listBox, entriesBox;
    private TextView totalText;
    private long filterBox = 0;

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
        words.addView(text("مطابقة الصناديق", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + " • الريال اليمني", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        totalText = text("", 17, 0xffCFE2FA, true);
        totalText.setTextDirection(View.TEXT_DIRECTION_RTL);
        header.addView(totalText);
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(20));

        content.addView(sectionTitle("ملخص أرصدة الصناديق"));
        summaryBox = panel();
        content.addView(summaryBox, space());

        Button add = action("تسجيل وارد أو صادر", true);
        add.setOnClickListener(v -> entryDialog());
        content.addView(add, space());

        content.addView(sectionTitle("الصناديق"));
        listBox = panel();
        content.addView(listBox, space());

        Button newBox = action("إضافة صندوق جديد", false);
        newBox.setOnClickListener(v -> boxDialog(0, "", 0));
        content.addView(newBox, space());

        content.addView(sectionTitle("آخر الحركات"));
        entriesBox = panel();
        content.addView(entriesBox, space());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
        refresh();
    }

    private void refresh() {
        refreshSummary();
        refreshBoxes();
        refreshEntries();
    }

    /** أرصدة الصناديق النشطة مع الإجمالي العام. */
    private void refreshSummary() {
        summaryBox.removeAllViews();
        double total = 0;
        int count = 0;
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                count++;
                double balance = c.getDouble(6);
                total += balance;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(11), 0, dp(11));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(text(c.getString(1), 17, Util.NAVY, true));
                words.addView(text("وارد " + money(c.getDouble(4)) + "  •  صادر " + money(c.getDouble(5)), 12, 0xff7c8186, false));
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amount = text(money(balance), 19, balance < 0 ? Util.RED : Util.GREEN, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(amount);
                summaryBox.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                summaryBox.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (count == 0) {
            summaryBox.addView(text("لا توجد صناديق بعد. أضف صندوقًا لتبدأ.", 15, 0xff777d84, false));
            totalText.setText("");
            return;
        }
        LinearLayout sum = new LinearLayout(this);
        sum.setGravity(Gravity.CENTER_VERTICAL);
        sum.setPadding(dp(10), dp(13), dp(10), dp(13));
        sum.setBackground(Util.round(Util.ACCENT_SOFT, dp(12)));
        sum.addView(text("إجمالي الأرصدة", 17, Util.NAVY, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView grand = text(money(total) + " ر.ي", 20, total < 0 ? Util.RED : Util.NAVY, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        sum.addView(grand);
        summaryBox.addView(sum, space());
        totalText.setText(money(total) + " ر.ي");
    }

    /** قائمة الصناديق مع تعديل كل صندوق. */
    private void refreshBoxes() {
        listBox.removeAllViews();
        int count = 0;
        try (Cursor c = db.cashboxes(false)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                final String name = c.getString(1);
                final double opening = c.getDouble(2);
                final boolean active = c.getInt(3) == 1;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(text(name + (active ? "" : "  •  موقوف"), 16, active ? Util.NAVY : 0xff9aa0a6, true));
                words.addView(text("افتتاحي " + money(opening) + "  •  الرصيد " + money(c.getDouble(6)), 12, 0xff7c8186, false));
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                Button edit = action("تعديل", false);
                edit.setTextSize(13);
                edit.setOnClickListener(v -> boxDialog(id, name, opening));
                row.addView(edit);
                listBox.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                listBox.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (count == 0) listBox.addView(text("لم تُضف صناديق بعد.", 15, 0xff777d84, false));
    }

    /** آخر الحركات مع إمكانية الحذف بضغطة مطوّلة. */
    private void refreshEntries() {
        entriesBox.removeAllViews();
        int count = 0;
        try (Cursor c = db.cashboxEntries(filterBox, 40)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                boolean in = "IN".equals(c.getString(1));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                String note = c.getString(3);
                words.addView(text(note.isEmpty() ? (in ? "وارد" : "صادر") : note, 16, Util.NAVY, true));
                words.addView(text(c.getString(5) + "  •  " + c.getString(4), 12, 0xff7c8186, false));
                TextView badge = text(in ? "وارد" : "صادر", 11, in ? Util.GREEN : Util.RED, true);
                badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                badge.setBackground(Util.round(in ? 0xffe7f1e7 : 0xfffbe9e9, dp(8)));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
                bp.setMargins(0, dp(6), 0, 0);
                words.addView(badge, bp);
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amount = text((in ? "+ " : "− ") + money(c.getDouble(2)), 17, in ? Util.GREEN : Util.RED, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(amount);
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this).setTitle("حذف الحركة")
                            .setMessage("سيُحذف هذا السطر ويتغيّر رصيد الصندوق.")
                            .setPositiveButton("حذف", (d, w) -> { db.deleteCashboxEntry(id); refresh(); })
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

    /** إضافة صندوق أو تعديله. */
    private void boxDialog(long id, String name, double opening) {
        final boolean isNew = id == 0;
        EditText nameInput = new EditText(this);
        styleInput(nameInput);
        nameInput.setHint("اسم الصندوق");
        nameInput.setText(name);
        EditText openingInput = new EditText(this);
        styleInput(openingInput);
        openingInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        openingInput.setHint("الرصيد الافتتاحي");
        if (!isNew) openingInput.setText(trim(opening));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(text("اسم الصندوق", 13, 0xff7c8186, false));
        box.addView(nameInput);
        box.addView(text("الرصيد الافتتاحي (ريال يمني)", 13, 0xff7c8186, false), space());
        box.addView(openingInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isNew ? "صندوق جديد" : "تعديل الصندوق")
                .setView(box)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null);
        if (!isNew) builder.setNeutralButton("خيارات", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = nameInput.getText().toString().trim();
                if (value.isEmpty()) { nameInput.setError("اكتب اسم الصندوق"); return; }
                double start = Calc.number(openingInput.getText().toString());
                try {
                    if (isNew) db.addCashbox(value, start);
                    else { db.renameCashbox(id, value); db.setCashboxOpening(id, start); }
                } catch (IllegalArgumentException e) { nameInput.setError(e.getMessage()); return; }
                dialog.dismiss();
                refresh();
            });
            if (!isNew) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                boxOptions(id, name);
            });
        });
        dialog.show();
    }

    /** إيقاف الصندوق أو حذفه إن كان بلا حركات. */
    private void boxOptions(long id, String name) {
        int entries = db.cashboxEntryCount(id);
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"عرض حركات هذا الصندوق", "إيقاف الصندوق", "تفعيل الصندوق", "حذف الصندوق"},
                        (d, which) -> {
                            if (which == 0) { filterBox = id; refreshEntries(); Toast.makeText(this, "عرض حركات " + name, Toast.LENGTH_SHORT).show(); }
                            else if (which == 1) { db.setCashboxActive(id, false); refresh(); }
                            else if (which == 2) { db.setCashboxActive(id, true); refresh(); }
                            else {
                                if (entries > 0) {
                                    new AlertDialog.Builder(this).setTitle("لا يمكن الحذف")
                                            .setMessage("على هذا الصندوق " + entries + " حركة مسجّلة. أوقفه بدل حذفه حتى لا يضيع الأرشيف.")
                                            .setPositiveButton("حسنًا", null).show();
                                    return;
                                }
                                db.deleteCashbox(id);
                                refresh();
                            }
                        }).show();
    }

    /** تسجيل وارد أو صادر على صندوق. */
    private void entryDialog() {
        final List<Long> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) { ids.add(c.getLong(0)); names.add(c.getString(1)); }
        }
        if (ids.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("لا توجد صناديق")
                    .setMessage("أضف صندوقًا أولًا ثم سجّل الوارد والصادر.")
                    .setPositiveButton("حسنًا", null).show();
            return;
        }
        Spinner boxPicker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        boxPicker.setAdapter(adapter);

        Spinner kind = new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"وارد (دخول نقد)", "صادر (خروج نقد)"}));

        EditText amount = new EditText(this);
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

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(text("الصندوق", 13, 0xff7c8186, false));
        box.addView(boxPicker);
        box.addView(text("نوع الحركة", 13, 0xff7c8186, false), space());
        box.addView(kind);
        box.addView(text("المبلغ", 13, 0xff7c8186, false), space());
        box.addView(amount);
        box.addView(text("البيان", 13, 0xff7c8186, false), space());
        box.addView(note);
        box.addView(dateButton, space());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("تسجيل حركة صندوق")
                .setView(box)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double value = Calc.number(amount.getText().toString());
            if (!(value > 0)) { amount.setError("اكتب مبلغًا أكبر من صفر"); return; }
            long boxId = ids.get(boxPicker.getSelectedItemPosition());
            String direction = kind.getSelectedItemPosition() == 0 ? "IN" : "OUT";
            if ("OUT".equals(direction) && db.cashboxBalance(boxId) < value) {
                new AlertDialog.Builder(this).setTitle("رصيد غير كافٍ")
                        .setMessage("رصيد الصندوق " + money(db.cashboxBalance(boxId)) + " ر.ي وأنت تصرف " + money(value) + " ر.ي.\nهل تريد التسجيل رغم ذلك؟")
                        .setPositiveButton("سجّل", (d, w) -> {
                            db.addCashboxEntry(boxId, direction, value, note.getText().toString(), date[0]);
                            dialog.dismiss();
                            refresh();
                        })
                        .setNegativeButton("رجوع", null).show();
                return;
            }
            try {
                db.addCashboxEntry(boxId, direction, value, note.getText().toString(), date[0]);
            } catch (IllegalArgumentException e) { amount.setError(e.getMessage()); return; }
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    private TextView sectionTitle(String name) {
        TextView t = text(name, 17, Util.NAVY, true);
        t.setPadding(dp(4), dp(18), dp(4), dp(8));
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
