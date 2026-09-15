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

/** حركة المواد: وارد وصادر اللترات ورصيد المخزون لكل مادة. */
public class MaterialActivity extends Activity {
    private Db db;
    private LinearLayout summaryBox, listBox, entriesBox;
    private TextView totalText, entriesTitle;
    private String filterMaterial = "";

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
        words.addView(text("حركة المواد", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + " • اللترات", 11, 0xffCFE2FA, false));
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
        refreshMaterials();
        refreshEntries();
    }

    /** بطاقة الإجمالي: مجموع المخزون والوارد والمبيعات. */
    private void refreshSummary() {
        summaryBox.removeAllViews();
        double stock = 0, in = 0, sold = 0;
        for (String material : Db.MATERIALS) {
            double[] s = db.materialSummary(material);
            in += s[0];
            sold += s[2];
            stock += s[3];
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Util.round(Util.NAVY, dp(18)));
        card.addView(text("إجمالي المخزون المتاح", 13, 0xffCFE2FA, false));
        TextView grand = text(money(stock) + "  لتر", 30, Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(12));
        card.addView(grand);
        LinearLayout stats = new LinearLayout(this);
        stats.addView(stat("إجمالي الوارد", money(in)), cell());
        stats.addView(stat("المباع", money(sold)), cell());
        stats.addView(stat("المواد", String.valueOf(Db.MATERIALS.length)), cell());
        card.addView(stats);
        summaryBox.addView(card);
        totalText.setText(money(stock) + " لتر");
    }

    /** بطاقة لكل مادة: الضغط عليها يسجّل حركة. */
    private void refreshMaterials() {
        listBox.removeAllViews();
        listBox.addView(sectionTitle("المواد — اضغط على المادة لتسجيل حركة"));
        for (String material : Db.MATERIALS) {
            final String name = material;
            double[] s = db.materialSummary(material);
            final double stock = s[3];

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
            words.addView(text(material, 18, Util.NAVY, true));
            words.addView(text("اضغط لتسجيل وارد أو صادر", 11, 0xff8b9097, false));
            top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout amountBox = new LinearLayout(this);
            amountBox.setOrientation(LinearLayout.VERTICAL);
            TextView amount = text(money(stock), 22, stock < 0 ? Util.RED : Util.NAVY, true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);
            amount.setGravity(Gravity.LEFT);
            amountBox.addView(amount);
            TextView unit = text("لتر متاح", 10, 0xff8b9097, false);
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
            foot.addView(chip("وارد", money(s[0]), Util.GREEN), cell());
            foot.addView(chip("صادر", money(s[1]), Util.RED), cell());
            foot.addView(chip("مباع", money(s[2]), Util.NAVY), cell());
            card.addView(foot);

            card.setOnClickListener(v -> entryDialog(name, stock));
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(name)
                        .setItems(new String[]{"عرض حركات هذه المادة", "كل الحركات"}, (d, which) -> {
                            filterMaterial = which == 0 ? name : "";
                            refreshEntries();
                        }).show();
                return true;
            });
            listBox.addView(card, space());
        }
        listBox.addView(text("المباع يُحسب تلقائيًا من قراءات الورديات المُغلقة", 11, 0xff8b9097, false));
    }

    /** آخر الحركات مع الحذف بضغطة مطوّلة. */
    private void refreshEntries() {
        entriesBox.removeAllViews();
        entriesTitle.setText(filterMaterial.isEmpty() ? "آخر الحركات" : "حركات " + filterMaterial);
        int count = 0;
        try (Cursor c = db.materialEntries(filterMaterial, 40)) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                boolean in = "IN".equals(c.getString(2));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                String note = c.getString(4);
                words.addView(text(note.isEmpty() ? (in ? "وارد" : "صادر") : note, 16, Util.NAVY, true));
                words.addView(text(c.getString(1) + "  •  " + c.getString(5), 12, 0xff7c8186, false));
                TextView badge = text(in ? "وارد" : "صادر", 11, in ? Util.GREEN : Util.RED, true);
                badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                badge.setBackground(Util.round(in ? 0xffe7f1e7 : 0xfffbe9e9, dp(8)));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
                bp.setMargins(0, dp(6), 0, 0);
                words.addView(badge, bp);
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amount = text((in ? "+ " : "− ") + money(c.getDouble(3)) + " لتر", 16, in ? Util.GREEN : Util.RED, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(amount);
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this).setTitle("حذف الحركة")
                            .setMessage("سيُحذف هذا السطر ويتغيّر رصيد المخزون.")
                            .setPositiveButton("حذف", (d, w) -> { db.deleteMaterialEntry(id); refresh(); })
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

    /** تسجيل وارد أو صادر لمادة، مع عرض رصيدها قبل الحفظ. */
    private void entryDialog(final String material, final double stock) {
        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        balanceCard.setBackground(Util.round(Util.ACCENT_SOFT, dp(14)));
        balanceCard.addView(text("المخزون الحالي من " + material, 12, 0xff5a6672, false));
        TextView balanceText = text(money(stock) + "  لتر", 24, stock < 0 ? Util.RED : Util.NAVY, true);
        balanceText.setTextDirection(View.TEXT_DIRECTION_LTR);
        balanceCard.addView(balanceText);
        final TextView afterText = text("", 13, 0xff5a6672, true);
        balanceCard.addView(afterText);

        Spinner kind = new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"وارد (توريد للمحطة)", "صادر (خروج أو فاقد)"}));

        final EditText amount = new EditText(this);
        styleInput(amount);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("الكمية باللتر");

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
            double after = kind.getSelectedItemPosition() == 0 ? stock + value : stock - value;
            afterText.setText("المخزون بعد الحركة: " + money(after) + " لتر");
            afterText.setTextColor(after < 0 ? Util.RED : Util.GREEN);
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
        box.addView(text("الكمية", 13, 0xff7c8186, false), space());
        box.addView(amount);
        box.addView(text("البيان", 13, 0xff7c8186, false), space());
        box.addView(note);
        box.addView(dateButton, space());
        ScrollView form = new ScrollView(this);
        form.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(material)
                .setView(form)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double value = Calc.number(amount.getText().toString());
            if (!(value > 0)) { amount.setError("اكتب كمية أكبر من صفر"); return; }
            String direction = kind.getSelectedItemPosition() == 0 ? "IN" : "OUT";
            if ("OUT".equals(direction) && stock < value) {
                new AlertDialog.Builder(this).setTitle("مخزون غير كافٍ")
                        .setMessage("المتاح " + money(stock) + " لتر وأنت تُخرج " + money(value) + " لتر.\nهل تريد التسجيل رغم ذلك؟")
                        .setPositiveButton("سجّل", (d, w) -> {
                            db.addMaterialEntry(material, direction, value, note.getText().toString(), date[0]);
                            dialog.dismiss();
                            refresh();
                        })
                        .setNegativeButton("رجوع", null).show();
                return;
            }
            try {
                db.addMaterialEntry(material, direction, value, note.getText().toString(), date[0]);
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

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
