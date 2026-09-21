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
    private TextView totalText, entriesTitle;
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
        words.addView(text("حركة الصناديق", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + " • الريال اليمني", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        totalText = text("", 17, 0xffCFE2FA, true);
        totalText.setTextDirection(View.TEXT_DIRECTION_RTL);
        header.addView(totalText);

        // ترس أسعار الصرف.
        ImageButton gear = new ImageButton(this);
        gear.setContentDescription("أسعار الصرف");
        gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setImageDrawable(new GearIcon());
        gear.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(0x22FFFFFF, dp(21)), Util.round(Color.WHITE, dp(21))));
        gear.setOnClickListener(v -> ratesDialog());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(42), dp(42));
        gp.setMargins(dp(10), 0, 0, 0);
        header.addView(gear, gp);
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

        Button report = action("⤓  تقرير Excel لحركة الصناديق", true);
        report.setOnClickListener(v -> reportDialog());
        content.addView(report, space());

        Button newBox = action("＋  إضافة صندوق جديد", false);
        newBox.setOnClickListener(v -> boxDialog(0, "", 0));
        content.addView(newBox, space());

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
        refreshBoxes();
        refreshEntries();
    }

    /** بطاقة الإجمالي العام أعلى الشاشة. */
    /** تعديل الحركة أو حذفها. */
    private void entryOptions(final long id) {
        new AlertDialog.Builder(this).setTitle("الحركة")
                .setItems(new String[]{"تعديل الحركة", "حذف الحركة"}, (d, which) -> {
                    if (which == 0) editEntryDialog(id);
                    else new AlertDialog.Builder(this).setTitle("حذف الحركة")
                            .setMessage("سيُحذف هذا السطر ويتغيّر رصيد الصندوق.")
                            .setPositiveButton("حذف", (a, b) -> { try{db.deleteCashboxEntry(id); refresh();}catch(Exception e){new AlertDialog.Builder(this).setTitle("تعذّر الحذف").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();} })
                            .setNegativeButton("إلغاء", null).show();
                }).show();
    }

    /** نافذة تعديل حركة صندوق مسجّلة. */
    private void editEntryDialog(final long id) {
        String dir = "IN", note = "", when = ShiftDates.today(), code = "YER";
        double orig = 0;
        try (Cursor c = db.cashboxEntry(id)) {
            if (!c.moveToFirst()) { Toast.makeText(this, "الحركة غير موجودة", Toast.LENGTH_SHORT).show(); return; }
            dir = c.getString(0); note = c.getString(2); when = c.getString(3);
            code = c.getString(5); orig = c.getDouble(6);
            if (c.getLong(4) > 0) {
                new AlertDialog.Builder(this).setTitle("حركة مرتبطة بوردية")
                        .setMessage("رُحّلت تلقائيًا من وردية ولا تُعدَّل يدويًا.")
                        .setPositiveButton("حسنًا", null).show();
                return;
            }
        }

        final Spinner kind = new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"وارد (دخول نقد)", "صادر (خروج نقد)"}));
        kind.setSelection("IN".equals(dir) ? 0 : 1);

        final Spinner currency = new Spinner(this);
        currency.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Db.CURRENCY_NAMES));
        for (int i = 0; i < Db.CURRENCIES.length; i++)
            if (Db.CURRENCIES[i].equals(code)) currency.setSelection(i);

        // البيان يُنظَّف من لاحقة التحويل حتى لا تتكرّر عند الحفظ.
        String clean = note == null ? "" : note;
        int cut = clean.indexOf(" — ");
        if (cut > 0 && !"YER".equals(code)) clean = clean.substring(0, cut);
        final AutoCompleteTextView noteInput = new AutoCompleteTextView(this);
        styleInput(noteInput);
        noteInput.setHint("الاسم أو البيان");
        noteInput.setText(clean);
        noteInput.setThreshold(1);
        noteInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, db.allNames()));

        final EditText amount = new EditText(this);
        styleInput(amount);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(money(orig).replace(",", ""));
        amount.setSelectAllOnFocus(true);

        final String[] date = {when};
        final Button dateButton = action("التاريخ: " + date[0], false);
        dateButton.setOnClickListener(v -> {
            String[] parts = date[0].split("-");
            new android.app.DatePickerDialog(this, (picker, y, m, d) -> {
                date[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                dateButton.setText("التاريخ: " + date[0]);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])).show();
        });

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(text("نوع الحركة", 13, 0xff626970, false));
        box.addView(kind);
        box.addView(text("العملة", 13, 0xff626970, false));
        box.addView(currency);
        box.addView(text("الاسم", 13, 0xff626970, false));
        box.addView(noteInput);
        box.addView(text("المبلغ", 13, 0xff626970, false));
        box.addView(amount);
        box.addView(dateButton);
        ScrollView form = new ScrollView(this);
        form.addView(box);

        new AlertDialog.Builder(this).setTitle("تعديل الحركة")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        db.updateCashboxEntry(id,
                                kind.getSelectedItemPosition() == 0 ? "IN" : "OUT",
                                Calc.number(amount.getText().toString()),
                                noteInput.getText().toString(), date[0],
                                Db.CURRENCIES[currency.getSelectedItemPosition()]);
                        db.rememberName("CASH", noteInput.getText().toString());
                        refresh();
                        Toast.makeText(this, "عُدّلت الحركة", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    /** ضبط سعر صرف كل عملة أجنبية إلى الريال اليمني. */
    private void ratesDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(10), dp(22), 0);
        box.addView(text("كم ريالًا يمنيًا يساوي الواحد من كل عملة؟", 13, 0xff626970, false));

        for (int i = 0; i < Db.CURRENCIES.length; i++) {
            final String code = Db.CURRENCIES[i];
            if ("YER".equals(code)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text("الريال " + Db.currencyName(code), 17, Util.NAVY, true));
            words.addView(text("١ " + Db.currencyName(code) + " = " + money(db.rate(code)) + " ر.ي",
                    12, 0xff626970, false));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

            Button edit = action("تغيير", false);
            edit.setTextSize(13);
            edit.setOnClickListener(v -> rateDialog(code));
            row.addView(edit);
            box.addView(row);

            View line = new View(this);
            line.setBackgroundColor(0xffeceef0);
            box.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        box.addView(text("الأرصدة تُحفظ بالريال اليمني، فتغيير السعر لا يغيّر الحركات السابقة.",
                12, 0xff626970, false));

        ScrollView form = new ScrollView(this);
        form.addView(box);
        new AlertDialog.Builder(this).setTitle("أسعار الصرف")
                .setView(form).setPositiveButton("تم", null).show();
    }

    private void rateDialog(final String code) {
        final EditText input = new EditText(this);
        styleInput(input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(money(db.rate(code)).replace(",", ""));
        input.setSelectAllOnFocus(true);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(8), dp(24), 0);
        wrap.addView(wrapText("١ " + Db.currencyName(code) + " = كم ريالًا يمنيًا؟"));
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("سعر صرف الريال " + Db.currencyName(code))
                .setView(wrap)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        db.setRate(code, Calc.number(input.getText().toString()));
                        refresh();
                        ratesDialog();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private TextView wrapText(String value) { return text(value, 13, 0xff626970, false); }

    /** ترس مرسوم بلا ملف صورة. */
    private class GearIcon extends android.graphics.drawable.Drawable {
        final android.graphics.Paint ink = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        public void draw(android.graphics.Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            ink.setColor(Color.WHITE);
            ink.setStyle(android.graphics.Paint.Style.STROKE);
            ink.setStrokeWidth(2.3f);
            ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            c.drawCircle(12, 12, 7, ink);
            c.drawCircle(12, 12, 2.8f, ink);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                c.drawLine((float) (12 + Math.cos(a) * 7), (float) (12 + Math.sin(a) * 7),
                        (float) (12 + Math.cos(a) * 10), (float) (12 + Math.sin(a) * 10), ink);
            }
            c.restore();
        }
        public void setAlpha(int a) {}
        public void setColorFilter(android.graphics.ColorFilter f) {}
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void refreshSummary() {
        summaryBox.removeAllViews();
        // الأرصدة الموجبة نقد متوفّر، والسالبة عجز. والصافي فرقهما.
        double positive = 0, negative = 0;
        int count = 0;
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                count++;
                double balance = c.getDouble(6);
                if (balance > 0.009) positive += balance;
                else if (balance < -0.009) negative -= balance;
            }
        }
        double net = positive - negative;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Util.round(Util.NAVY, dp(18)));
        // الرصيد يظهر كما هو: موجبًا كان أو سالبًا.
        card.addView(text(net < -0.009 ? "صافي الصناديق — عجز" : "صافي الصناديق — متوفّر",
                13, 0xffCFE2FA, false));
        TextView grand = text(money(net) + "  ر.ي", 30,
                net < -0.009 ? 0xffFFB3BC : Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(12));
        card.addView(grand);

        LinearLayout stats = new LinearLayout(this);
        stats.addView(stat("نقد متوفّر", money(positive)), statCell());
        stats.addView(stat("عجز", money(negative)), statCell());
        stats.addView(stat("صافي الصناديق", money(net)), statCell());
        card.addView(stats);
        summaryBox.addView(card);
        totalText.setText(count == 0 ? "" : money(net) + " ر.ي");
    }

    private LinearLayout.LayoutParams statCell() {
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

    /** بطاقة لكل صندوق: الضغط عليها يسجّل حركة، والضغط المطوّل يفتح خياراته. */
    private void refreshBoxes() {
        listBox.removeAllViews();
        listBox.addView(sectionTitle("الصناديق — اضغط على الصندوق لتسجيل حركة"));
        int count = 0;
        try (Cursor c = db.cashboxes(false)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                final String name = c.getString(1);
                final double opening = c.getDouble(2);
                final boolean active = c.getInt(3) == 1;
                final double balance = c.getDouble(6);

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
                words.addView(text(active ? "اضغط لتسجيل وارد أو صادر" : "موقوف — لا تُسجَّل عليه حركات", 11, 0xff626970, false));
                top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                LinearLayout amountBox = new LinearLayout(this);
                amountBox.setOrientation(LinearLayout.VERTICAL);
                TextView amount = text(money(balance), 22, balance < 0 ? Util.RED : Util.NAVY, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                amount.setGravity(Gravity.LEFT);
                amountBox.addView(amount);
                TextView unit = text("ريال يمني", 10, 0xff626970, false);
                unit.setGravity(Gravity.LEFT);
                amountBox.addView(unit);
                top.addView(amountBox);
                top.addView(historyButton(() -> boxHistory(id, name)));
                card.addView(top);

                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
                lp.setMargins(0, dp(12), 0, dp(10));
                card.addView(line, lp);

                LinearLayout foot = new LinearLayout(this);
                foot.addView(chip("افتتاحي", money(opening), Util.NAVY), statCell());
                foot.addView(chip("وارد", money(c.getDouble(4)), Util.GREEN), statCell());
                foot.addView(chip("صادر", money(c.getDouble(5)), Util.RED), statCell());
                card.addView(foot);

                card.setOnClickListener(v -> {
                    if (!active) { Toast.makeText(this, "الصندوق موقوف. فعّله أولًا.", Toast.LENGTH_SHORT).show(); return; }
                    entryDialog(id, name);
                });
                if (id == db.defaultCashbox()) {
                    TextView tag = text("★  صندوق الورديات — يستقبل النقد المسلّم تلقائيًا", 11, Util.ACCENT, true);
                    tag.setPadding(0, dp(9), 0, 0);
                    card.addView(tag);
                }
                card.setOnLongClickListener(v -> { boxOptions(id, name, opening); return true; });
                listBox.addView(card, space());
            }
        }
        if (count == 0) {
            LinearLayout empty = panel();
            empty.addView(text("لم تُضف صناديق بعد. أضف صندوقًا لتبدأ تسجيل الوارد والصادر.", 15, 0xff777d84, false));
            listBox.addView(empty, space());
        } else {
            listBox.addView(text(db.defaultCashbox() == 0
                    ? "⚠ لم تختر صندوق الورديات بعد. اضغط مطوّلًا على صندوق واختر «اجعله صندوق الورديات» ليستقبل النقد المسلّم تلقائيًا."
                    : "اضغط مطوّلًا على الصندوق لتعديله أو إيقافه", 11,
                    db.defaultCashbox() == 0 ? 0xffa8610a : 0xff626970, false));
        }
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
        TextView l = text(label, 10, 0xff626970, false);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    /** آخر الحركات مع إمكانية الحذف بضغطة مطوّلة. */
    private void refreshEntries() {
        entriesBox.removeAllViews();
        if (entriesTitle != null) entriesTitle.setText("");
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
                words.addView(text(c.getString(5) + "  •  " + c.getString(4), 12, 0xff626970, false));
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
                final boolean auto = c.getLong(6) > 0;
                if (auto) {
                    TextView src = text("مُرحّلة تلقائيًا من وردية", 10, Util.ACCENT, false);
                    src.setPadding(0, dp(4), 0, 0);
                    words.addView(src);
                }
                row.setOnLongClickListener(v -> {
                    if (auto) {
                        new AlertDialog.Builder(this).setTitle("حركة مرتبطة بوردية")
                                .setMessage("هذه الحركة رُحّلت تلقائيًا من وردية مُغلقة ولا تُحذف يدويًا، حتى لا تختلف الأرقام عن الأرشيف.")
                                .setPositiveButton("حسنًا", null).show();
                        return true;
                    }
                    entryOptions(id);
                    return true;
                });
                entriesBox.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffeceef0);
                entriesBox.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (count == 0) entriesBox.addView(text("لا توجد حركات مسجّلة بعد.", 15, 0xff777d84, false));
        else entriesBox.addView(text("اضغط مطوّلًا على أي حركة لتعديلها أو حذفها", 11, 0xff626970, false), space());
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
        box.addView(text("اسم الصندوق", 13, 0xff626970, false));
        box.addView(nameInput);
        box.addView(text("الرصيد الافتتاحي (ريال يمني)", 13, 0xff626970, false), space());
        box.addView(openingInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "صندوق جديد" : "تعديل الصندوق")
                .setView(box)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
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
        });
        dialog.show();
    }

    /** إيقاف الصندوق أو حذفه إن كان بلا حركات. */
    private void boxOptions(long id, String name, double opening) {
        int entries = db.cashboxEntryCount(id);
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"تعديل الاسم والرصيد الافتتاحي", "اجعله صندوق الورديات", "سجل حركاته", "إيقاف الصندوق", "تفعيل الصندوق", "حذف الصندوق"},
                        (d, which) -> {
                            if (which == 0) { boxDialog(id, name, opening); }
                            else if (which == 1) {
                                db.setDefaultCashbox(db.defaultCashbox() == id ? 0 : id);
                                Toast.makeText(this, db.defaultCashbox() == id ? "سيستقبل " + name + " النقد المسلّم من الورديات" : "أُلغي ربط الورديات بهذا الصندوق", Toast.LENGTH_LONG).show();
                                refresh();
                            }
                            else if (which == 2) boxHistory(id, name);
                            else if (which == 4) { db.setCashboxActive(id, false); refresh(); }
                            else if (which == 5) { db.setCashboxActive(id, true); refresh(); }
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

    /** تسجيل وارد أو صادر على صندوق محدّد، مع عرض رصيده قبل الحفظ. */
    private void entryDialog(final long boxId, String boxName) {
        final double opening = db.cashboxBalance(boxId);

        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        balanceCard.setBackground(Util.round(Util.ACCENT_SOFT, dp(14)));
        balanceCard.addView(text("الرصيد الحالي لصندوق " + boxName, 12, 0xff5a6672, false));
        TextView balanceText = text(money(opening) + "  ر.ي", 24, opening < 0 ? Util.RED : Util.NAVY, true);
        balanceText.setTextDirection(View.TEXT_DIRECTION_LTR);
        balanceCard.addView(balanceText);
        final TextView afterText = text("", 13, 0xff5a6672, true);
        balanceCard.addView(afterText);

        Spinner kind = new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"وارد (دخول نقد)", "صادر (خروج نقد)"}));

        // العملة بجانب نوع الحركة؛ الرصيد يبقى بالريال اليمني دائمًا.
        final Spinner currency = new Spinner(this);
        currency.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Db.CURRENCY_NAMES));

        // الاسم أولًا، ويقترح كل الأسماء المحفوظة في التطبيق.
        final AutoCompleteTextView note = new AutoCompleteTextView(this);
        styleInput(note);
        note.setHint("الاسم أو البيان");
        note.setThreshold(1);
        note.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, db.allNames()));
        note.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP) note.showDropDown();
            return false;
        });

        final EditText amount = new EditText(this);
        styleInput(amount);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("المبلغ بالريال اليمني");

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

        // الرصيد المتوقّع يتحدّث مع الكتابة، ويبيّن التحويل إن كانت العملة أجنبية.
        final Runnable preview = () -> {
            double entered = Calc.number(amount.getText().toString());
            if (!(entered > 0)) { afterText.setText(""); return; }
            String code = Db.CURRENCIES[currency.getSelectedItemPosition()];
            double value = db.toYer(entered, code);
            double after = kind.getSelectedItemPosition() == 0 ? opening + value : opening - value;
            String line = "YER".equals(code) ? ""
                    : money(entered) + " " + Db.currencyName(code) + " = " + money(value) + " ر.ي\n";
            afterText.setText(line + "الرصيد بعد الحركة: " + money(after) + " ر.ي");
            afterText.setTextColor(after < 0 ? Util.RED : Util.GREEN);
        };
        amount.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            public void onTextChanged(CharSequence t, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) { preview.run(); }
        });
        AdapterView.OnItemSelectedListener redraw = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                amount.setHint("YER".equals(Db.CURRENCIES[currency.getSelectedItemPosition()])
                        ? "المبلغ بالريال اليمني"
                        : "المبلغ بالـ" + Db.currencyName(Db.CURRENCIES[currency.getSelectedItemPosition()]));
                preview.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        kind.setOnItemSelectedListener(redraw);
        currency.setOnItemSelectedListener(redraw);

        final java.util.ArrayList<String> counterpartNames=new java.util.ArrayList<>();
        final java.util.ArrayList<String> counterpartTypes=new java.util.ArrayList<>();
        final java.util.ArrayList<Long> counterpartIds=new java.util.ArrayList<>();
        counterpartNames.add("اختر الحساب المقابل");counterpartTypes.add("");counterpartIds.add(0L);
        counterpartNames.add("مبيعات نقدية خارج الورديات");counterpartTypes.add("SALE");counterpartIds.add(0L);
        counterpartNames.add("مصروف نقدي — اكتب البند في البيان");counterpartTypes.add("EXPENSE");counterpartIds.add(0L);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM debtors ORDER BY name",null)){
            while(c.moveToNext()){counterpartNames.add("عميل: "+c.getString(1));counterpartTypes.add("CUSTOMER");counterpartIds.add(c.getLong(0));}
        }
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM cashboxes WHERE id<>? ORDER BY name",new String[]{String.valueOf(boxId)})){
            while(c.moveToNext()){counterpartNames.add("تحويل صندوق: "+c.getString(1));counterpartTypes.add("TRANSFER");counterpartIds.add(c.getLong(0));}
        }
        final Spinner counterpartPicker=new Spinner(this);
        counterpartPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,counterpartNames));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(10), dp(24), 0);
        box.addView(balanceCard);
        LinearLayout kinds = new LinearLayout(this);
        kinds.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout kindCol = new LinearLayout(this);
        kindCol.setOrientation(LinearLayout.VERTICAL);
        kindCol.addView(text("نوع الحركة", 13, 0xff626970, false));
        kindCol.addView(kind);
        kinds.addView(kindCol, new LinearLayout.LayoutParams(0, -2, 2));
        LinearLayout curCol = new LinearLayout(this);
        curCol.setOrientation(LinearLayout.VERTICAL);
        curCol.addView(text("العملة", 13, 0xff626970, false));
        curCol.addView(currency);
        LinearLayout.LayoutParams cw = new LinearLayout.LayoutParams(0, -2, 1);
        cw.setMargins(dp(10), 0, 0, 0);
        kinds.addView(curCol, cw);
        box.addView(kinds, space());
        box.addView(text("الحساب المقابل",14,Util.NAVY,true),space());
        box.addView(counterpartPicker);
        box.addView(text("وارد من العميل = تحصيل، صادر إليه = سلفة. لا تكرر نقد الورديات هنا.",13,0xff626970,false),space());
        box.addView(text("الاسم", 13, 0xff626970, false), space());
        box.addView(note);
        box.addView(text("المبلغ", 13, 0xff626970, false), space());
        box.addView(amount);
        box.addView(dateButton, space());
        ScrollView form = new ScrollView(this);
        form.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(boxName)
                .setView(form)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double entered = Calc.number(amount.getText().toString());
            if (!(entered > 0)) { amount.setError("اكتب مبلغًا أكبر من صفر"); return; }
            final String code = Db.CURRENCIES[currency.getSelectedItemPosition()];
            // المبلغ يُحوَّل إلى الريال اليمني قبل المقارنة والحفظ.
            final double value = db.toYer(entered, code);
            String direction = kind.getSelectedItemPosition() == 0 ? "IN" : "OUT";
            if ("OUT".equals(direction) && opening < value) {
                new AlertDialog.Builder(this).setTitle("رصيد غير كافٍ")
                        .setMessage("رصيد الصندوق " + money(opening) + " ر.ي وأنت تصرف " + money(value) + " ر.ي.\nهل تريد التسجيل رغم ذلك؟")
                        .setPositiveButton("سجّل", (d, w) -> {
                            try{db.addCashTransaction(boxId, direction, entered, note.getText().toString(), date[0], code, counterpartTypes.get(counterpartPicker.getSelectedItemPosition()), counterpartIds.get(counterpartPicker.getSelectedItemPosition()));}catch(RuntimeException e){amount.setError(e.getMessage());return;}
                            db.rememberName("CASH", note.getText().toString());
                            dialog.dismiss();
                            refresh();
                        })
                        .setNegativeButton("رجوع", null).show();
                return;
            }
            try {
                db.addCashTransaction(boxId, direction, entered, note.getText().toString(), date[0], code, counterpartTypes.get(counterpartPicker.getSelectedItemPosition()), counterpartIds.get(counterpartPicker.getSelectedItemPosition()));
                db.rememberName("CASH", note.getText().toString());
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

    /** يختار المدى التاريخي والصندوق ثم يشارك التقرير. */
    private void reportDialog() {
        final String[] from = { ShiftDates.today().substring(0, 8) + "01" };
        final String[] to = { ShiftDates.today() };
        final long[] box = { 0 };
        final String[] boxName = { "كل الصناديق" };

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.setPadding(dp(22), dp(10), dp(22), 0);

        final Button fromBtn = action("من: " + from[0], false);
        fromBtn.setOnClickListener(v -> pickDate(from[0], picked -> {
            from[0] = picked; fromBtn.setText("من: " + picked);
        }));
        form.addView(fromBtn);

        final Button toBtn = action("إلى: " + to[0], false);
        toBtn.setOnClickListener(v -> pickDate(to[0], picked -> {
            to[0] = picked; toBtn.setText("إلى: " + picked);
        }));
        form.addView(toBtn);

        final Button boxBtn = action("الصندوق: " + boxName[0], false);
        boxBtn.setOnClickListener(v -> {
            final List<Long> ids = new ArrayList<>();
            final List<String> names = new ArrayList<>();
            ids.add(0L); names.add("كل الصناديق");
            try (Cursor c = db.cashboxes(false)) {
                while (c.moveToNext()) { ids.add(c.getLong(0)); names.add(c.getString(1)); }
            }
            new AlertDialog.Builder(this).setTitle("اختر الصندوق")
                    .setItems(names.toArray(new String[0]), (d, which) -> {
                        box[0] = ids.get(which);
                        boxName[0] = names.get(which);
                        boxBtn.setText("الصندوق: " + boxName[0]);
                    }).show();
        });
        form.addView(boxBtn);

        // اختصارات سريعة للمدى الشائع.
        LinearLayout quick = new LinearLayout(this);
        quick.setGravity(Gravity.CENTER);
        String[] labels = { "اليوم", "هذا الشهر", "كل الحركات" };
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            Button q = action(labels[i], false);
            q.setTextSize(13);
            q.setOnClickListener(v -> {
                String today = ShiftDates.today();
                if (which == 0) { from[0] = today; to[0] = today; }
                else if (which == 1) { from[0] = today.substring(0, 8) + "01"; to[0] = today; }
                else { from[0] = db.firstCashboxDate(); to[0] = today; }
                fromBtn.setText("من: " + from[0]);
                toBtn.setText("إلى: " + to[0]);
            });
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(0, -2, 1);
            qp.setMargins(dp(3), dp(6), dp(3), 0);
            quick.addView(q, qp);
        }
        form.addView(quick);

        new AlertDialog.Builder(this).setTitle("تقرير حركة الصناديق")
                .setView(form)
                .setPositiveButton("إنشاء ومشاركة", (d, w) -> shareReport(from[0], to[0], box[0], boxName[0]))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private interface DateSink { void accept(String date); }

    private void pickDate(String current, final DateSink sink) {
        java.time.LocalDate start;
        try { start = java.time.LocalDate.parse(current); }
        catch (Exception e) { start = java.time.LocalDate.now(); }
        new android.app.DatePickerDialog(this, (picker, y, m, d) ->
                sink.accept(java.time.LocalDate.of(y, m + 1, d).toString()),
                start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth()).show();
    }

    private void shareReport(final String from, final String to, final long boxId, final String boxName) {
        if (from.compareTo(to) > 0) {
            Toast.makeText(this, "تاريخ البداية بعد النهاية", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "جارٍ تجهيز التقرير...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final java.io.File file = new CashboxReport(this, db).build(from, to, boxId, boxName);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                this, getPackageName() + ".files", file);
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                        intent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                                "حركة الصناديق " + from + " إلى " + to);
                        intent.setClipData(android.content.ClipData.newRawUri("تقرير الصناديق", uri));
                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(android.content.Intent.createChooser(intent, "مشاركة التقرير"));
                    } catch (Exception e) {
                        Toast.makeText(this, "تعذرت مشاركة الملف", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                final String why = String.valueOf(e.getMessage());
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, why, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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

    private String trim(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%.0f" : "%.2f", value);
    }


    /** سجل حركات صندوق واحد. */
    private void boxHistory(long boxId, String name) {
        java.util.List<String[]> lines = new java.util.ArrayList<>();
        java.util.List<String[]> reversed = new java.util.ArrayList<>();
        try (Cursor c = db.cashboxEntries(boxId, 200)) {
            while (c.moveToNext()) {
                boolean in = "IN".equals(c.getString(1));
                String note = c.getString(3);
                if (note == null || note.trim().isEmpty()) note = in ? "وارد" : "صادر";
                reversed.add(new String[]{in ? "وارد" : "صادر",
                        note + "  •  " + c.getString(4),
                        (in ? "+ " : "− ") + money(c.getDouble(2)),
                        String.valueOf(in ? Util.GREEN : Util.RED),
                        String.valueOf(c.getLong(0)),
                        c.getLong(6) > 0 ? "1" : "0"});
            }
        }
        for (int i = reversed.size() - 1; i >= 0; i--) lines.add(reversed.get(i));
        historyDialog("سجل " + name, lines, "لا توجد حركات على هذا الصندوق بعد.");
    }

    private void deleteEntry(long id) { db.deleteCashboxEntry(id); }

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
                        entryOptions(entryId);
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
                .setMessage(lines.isEmpty() ? null : "اضغط مطوّلًا على أي حركة لتعديلها أو حذفها")
                .setPositiveButton("إغلاق", null).show();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}

