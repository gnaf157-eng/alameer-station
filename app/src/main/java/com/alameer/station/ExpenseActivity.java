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

/** حركة المخاريج: مصروفات المحطة مبوّبة بالريال اليمني. */
public class ExpenseActivity extends Activity {
    private Db db;
    private LinearLayout summaryBox, listBox, entriesBox;
    private TextView totalText, entriesTitle;
    private String filterCategory = "";


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
        words.addView(text("حركة المخاريج", 19, Color.WHITE, true));
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

        Button add = action("＋  تسجيل مخرج جديد", true);
        add.setOnClickListener(v -> entryDialog(""));
        content.addView(add, space());

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox, space());

        entriesBox = panel();
        entriesBox.setVisibility(View.GONE);
        content.addView(entriesBox);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(6), 0, 0);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);Util.safeInsets(shell);
        refresh();
    }

    private void refresh() {
        refreshSummary();
        refreshCategories();
        refreshEntries();
    }

    /** بطاقة الإجمالي: الكل ومصروف الشهر الجاري. */
    private void refreshSummary() {
        summaryBox.removeAllViews();
        double total = db.expensesTotal();
        String month = ShiftDates.today().substring(0, 7);
        double thisMonth = db.expensesInMonth(month);
        int categories = 0;
        try (Cursor c = db.expenseCategories()) { categories = c.getCount(); }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Util.round(Util.NAVY, dp(18)));
        card.addView(text("إجمالي المخاريج", 13, 0xffCFE2FA, false));
        TextView grand = text(money(total) + "  ر.ي", 30, Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(12));
        card.addView(grand);
        LinearLayout stats = new LinearLayout(this);
        stats.addView(stat("هذا الشهر", money(thisMonth)), cell());
        stats.addView(stat("الأبواب", String.valueOf(categories)), cell());
        stats.addView(stat("الشهر", month), cell());
        card.addView(stats);
        summaryBox.addView(card);
        totalText.setText(total <= 0 ? "" : money(total) + " ر.ي");
    }

    /** بطاقة لكل باب مصروف: الضغط عليها يسجّل مخرجًا جديدًا في نفس الباب. */
    private void refreshCategories() {
        listBox.removeAllViews();
        listBox.addView(sectionTitle("أبواب المصروف — اضغط على الباب لتسجيل مخرج فيه"));
        int count = 0;
        double total = db.expensesTotal();
        try (Cursor c = db.expenseCategories()) {
            while (c.moveToNext()) {
                count++;
                final String name = c.getString(0);
                final double sum = c.getDouble(1);
                final int times = c.getInt(2);
                int share = total > 0 ? (int) Math.round(sum * 100 / total) : 0;

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
                words.addView(text(name, 18, Util.NAVY, true));
                words.addView(text(times + " حركة  •  " + share + "٪ من المخاريج", 11, 0xff626970, false));
                top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                LinearLayout amountBox = new LinearLayout(this);
                amountBox.setOrientation(LinearLayout.VERTICAL);
                TextView amount = text(money(sum), 22, Util.RED, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                amount.setGravity(Gravity.LEFT);
                amountBox.addView(amount);
                TextView unit = text("ريال يمني", 10, 0xff626970, false);
                unit.setGravity(Gravity.LEFT);
                amountBox.addView(unit);
                top.addView(amountBox);
                top.addView(historyButton(() -> categoryHistory(name)));
                card.addView(top);

                // شريط نسبة الباب من إجمالي المصروف.
                LinearLayout bar = new LinearLayout(this);
                bar.setBackground(Util.round(0xffeceef0, dp(4)));
                View fill = new View(this);
                fill.setBackground(Util.round(Util.ACCENT, dp(4)));
                bar.addView(fill, new LinearLayout.LayoutParams(0, dp(6), Math.max(1, share)));
                View rest = new View(this);
                bar.addView(rest, new LinearLayout.LayoutParams(0, dp(6), Math.max(1, 100 - share)));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(6));
                bp.setMargins(0, dp(12), 0, 0);
                card.addView(bar, bp);

                card.setOnClickListener(v -> entryDialog(name));
                card.setOnLongClickListener(v -> { categoryHistory(name); return true; });
                listBox.addView(card, space());
            }
        }
        if (count == 0) {
            LinearLayout empty = panel();
            empty.addView(text("لا توجد مخاريج بعد. سجّل مخرجًا أو أغلق وردية فيها مخاريج.", 15, 0xff777d84, false));
            listBox.addView(empty, space());
        } else {
            listBox.addView(text("اضغط أيقونة الساعة بجانب الباب لعرض مخاريجه", 11, 0xff626970, false));
        }
    }

    /** آخر المخاريج مع الحذف بضغطة مطوّلة. */
    private void refreshEntries() {
        entriesBox.removeAllViews();
        if (entriesTitle != null) entriesTitle.setText("");
        int count = 0;
        try (Cursor c = db.expenses(filterCategory, 40)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                final boolean auto = c.getLong(5) > 0;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(text(c.getString(1), 16, Util.NAVY, true));
                String note = c.getString(3);
                words.addView(text((note.isEmpty() ? "" : note + "  •  ") + c.getString(4), 12, 0xff626970, false));
                if (auto) {
                    TextView src = text("مُرحّلة تلقائيًا من وردية", 10, Util.ACCENT, false);
                    src.setPadding(0, dp(4), 0, 0);
                    words.addView(src);
                }
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amount = text("− " + money(c.getDouble(2)), 17, Util.RED, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(amount);
                row.setOnLongClickListener(v -> {
                    if (auto) {
                        new AlertDialog.Builder(this).setTitle("حركة مرتبطة بوردية")
                                .setMessage("هذا المخرج رُحّل تلقائيًا من وردية مُغلقة ولا يُحذف يدويًا، حتى لا تختلف الأرقام عن الأرشيف.")
                                .setPositiveButton("حسنًا", null).show();
                        return true;
                    }
                    new AlertDialog.Builder(this).setTitle("حذف المخرج")
                            .setMessage("سيُعكس المصروف وحركة الصندوق المرتبطة به معًا. إذا كانت الحركة القديمة بلا رابط موثوق فسيُمنع حذفها.")
                            .setPositiveButton("حذف", (d, w) -> { try{db.deleteExpense(id); refresh();}catch(Exception e){new AlertDialog.Builder(this).setTitle("تعذّر الحذف").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();} })
                            .setNegativeButton("إلغاء", null).show();
                    return true;
                });
                entriesBox.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                entriesBox.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (count == 0) entriesBox.addView(text("لا توجد مخاريج مسجّلة بعد.", 15, 0xff777d84, false));
        else entriesBox.addView(text("اضغط مطوّلًا على أي مخرج لحذفه", 11, 0xff626970, false), space());
    }

    /** تسجيل مخرج، مع خيار خصمه من صندوق. */
    private void entryDialog(String presetCategory) {
        final List<String> known = new ArrayList<>();
        try (Cursor c = db.expenseCategories()) { while (c.moveToNext()) known.add(c.getString(0)); }

        final AutoCompleteTextView category = new AutoCompleteTextView(this);
        styleInput(category);
        category.setHint("باب المصروف — مثل: كهرباء، صيانة، رواتب");
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, known));
        category.setThreshold(1);
        if (!presetCategory.isEmpty()) category.setText(presetCategory);

        final EditText amount = new EditText(this);
        styleInput(amount);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("المبلغ بالريال اليمني");

        final EditText note = new EditText(this);
        styleInput(note);
        note.setHint("البيان (اختياري)");

        // اختيار الصندوق الذي يُدفع منه، أو بلا صندوق.
        final List<Long> boxIds = new ArrayList<>();
        final List<String> boxNames = new ArrayList<>();
        boxIds.add(0L);
        boxNames.add("بدون خصم من صندوق");
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                boxIds.add(c.getLong(0));
                boxNames.add(c.getString(1) + "  (" + money(c.getDouble(6)) + " ر.ي)");
            }
        }
        final Spinner boxPicker = new Spinner(this);
        boxPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, boxNames));
        long preferred = db.defaultCashbox();
        if (preferred > 0 && boxIds.contains(preferred)) boxPicker.setSelection(boxIds.indexOf(preferred));

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
        box.setPadding(dp(24), dp(10), dp(24), 0);
        box.addView(text("باب المصروف", 13, 0xff626970, false));
        box.addView(category);
        box.addView(text("المبلغ", 13, 0xff626970, false), space());
        box.addView(amount);
        box.addView(text("البيان", 13, 0xff626970, false), space());
        box.addView(note);
        box.addView(text("يُدفع من", 13, 0xff626970, false), space());
        box.addView(boxPicker);
        box.addView(dateButton, space());
        ScrollView form = new ScrollView(this);
        form.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مخرج جديد")
                .setView(form)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = category.getText().toString().trim();
            if (name.isEmpty()) { category.setError("اكتب باب المصروف"); return; }
            double value = Calc.number(amount.getText().toString());
            if (!(value > 0)) { amount.setError("اكتب مبلغًا أكبر من صفر"); return; }
            final long boxId = boxIds.get(boxPicker.getSelectedItemPosition());
            if (boxId > 0 && db.cashboxBalance(boxId) < value) {
                new AlertDialog.Builder(this).setTitle("رصيد غير كافٍ")
                        .setMessage("رصيد الصندوق " + money(db.cashboxBalance(boxId)) + " ر.ي وأنت تصرف " + money(value) + " ر.ي.\nهل تريد التسجيل رغم ذلك؟")
                        .setPositiveButton("سجّل", (d, w) -> {
                            db.addExpense(name, value, note.getText().toString(), date[0], boxId, 0);
                            dialog.dismiss();
                            refresh();
                        })
                        .setNegativeButton("رجوع", null).show();
                return;
            }
            try {
                db.addExpense(name, value, note.getText().toString(), date[0], boxId, 0);
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
        t.setTextSize(Math.max(12,size));
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }


    /** سجل مخاريج باب واحد. */
    private void categoryHistory(String category) {
        java.util.List<String[]> lines = new java.util.ArrayList<>();
        java.util.List<String[]> reversed = new java.util.ArrayList<>();
        try (Cursor c = db.expenses(category, 200)) {
            while (c.moveToNext()) {
                String note = c.getString(3);
                if (note == null || note.trim().isEmpty()) note = "مخرج";
                if (c.getLong(5) > 0) note = note + "  •  مُرحّلة من وردية";
                reversed.add(new String[]{c.getString(1),
                        note + "  •  " + c.getString(4),
                        "− " + money(c.getDouble(2)),
                        String.valueOf(Util.RED),
                        String.valueOf(c.getLong(0)),
                        c.getLong(5) > 0 ? "1" : "0"});
            }
        }
        for (int i = reversed.size() - 1; i >= 0; i--) lines.add(reversed.get(i));
        historyDialog("سجل " + category, lines, "لا توجد مخاريج في هذا الباب بعد.");
    }

    private void deleteEntry(long id) { db.deleteExpense(id); }

    /** زر دائري صغير يفتح سجل حركات هذا السجل وحده. */
    private View historyButton(final Runnable action) {
        ImageButton button = new ImageButton(this);
        button.setContentDescription("سجل الحركات");
        button.setTooltipText("سجل الحركات");
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setImageDrawable(new HistoryIcon());
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000),
                Util.round(Util.ACCENT_SOFT, dp(18)), null));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(36), dp(36));
        p.setMargins(dp(8), 0, 0, 0);
        button.setLayoutParams(p);
        return button;
    }

    /** أيقونة ساعة بعقارب للخلف. */
    private class HistoryIcon extends android.graphics.drawable.Drawable {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        public void draw(android.graphics.Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setColor(Util.ACCENT);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            c.drawArc(3.5f, 3.5f, 20.5f, 20.5f, 110, 300, false, paint);
            c.drawLine(12, 7.5f, 12, 12, paint);
            c.drawLine(12, 12, 15.4f, 14.1f, paint);
            c.drawLine(3.6f, 8.6f, 3.6f, 4.2f, paint);
            c.drawLine(3.6f, 8.6f, 7.9f, 8.6f, paint);
            c.restore();
        }
        public void setAlpha(int a) { paint.setAlpha(a); }
        public void setColorFilter(android.graphics.ColorFilter f) { paint.setColorFilter(f); }
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** نافذة تعرض حركات سجل واحد. الأقدم أولًا كدفتر اليومية. */
    private void historyDialog(String title, java.util.List<String[]> lines, String empty) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        if (lines.isEmpty()) {
            box.addView(text(empty, 15, 0xff626970, false));
        } else {
            for (String[] line : lines) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(9), 0, dp(9));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(text(line[0], 15, Util.NAVY, true));
                words.addView(text(line[1], 11, 0xff626970, false));
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                TextView value = text(line[2], 16, Integer.parseInt(line[3]), true);
                value.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(value);
                // الحذف بضغطة مطوّلة؛ الحركات المرحّلة من وردية محميّة.
                if (line.length >= 6) {
                    final long entryId = Long.parseLong(line[4]);
                    final boolean locked = "1".equals(line[5]);
                    row.setOnLongClickListener(v -> {
                        if (locked) {
                            new AlertDialog.Builder(this).setTitle("حركة مرتبطة بوردية")
                                    .setMessage("هذه الحركة رُحّلت تلقائيًا من وردية مُغلقة ولا تُحذف يدويًا.")
                                    .setPositiveButton("حسنًا", null).show();
                            return true;
                        }
                        new AlertDialog.Builder(this).setTitle("حذف الحركة")
                                .setMessage("سيُحذف هذا السطر نهائيًا ويتغيّر الرصيد.")
                                .setPositiveButton("حذف", (d, w) -> { try{deleteEntry(entryId); refresh();}catch(Exception e){new AlertDialog.Builder(this).setTitle("تعذّر الحذف").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();} })
                                .setNegativeButton("إلغاء", null).show();
                        return true;
                    });
                }
                box.addView(row);
                View divider = new View(this);
                divider.setBackgroundColor(0xffeef1f4);
                box.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setMessage(lines.isEmpty() ? null : "اضغط مطوّلًا على أي حركة لحذفها")
                .setPositiveButton("إغلاق", null).show();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}

