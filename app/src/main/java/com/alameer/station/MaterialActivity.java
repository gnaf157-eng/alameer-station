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

        // ترس الضبط: أسعار اللتر وسعات الخزانات.
        ImageButton gear = new ImageButton(this);
        gear.setContentDescription("ضبط المواد");
        gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setImageDrawable(new GearIcon());
        gear.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(0x22FFFFFF, dp(21)), Util.round(Color.WHITE, dp(21))));
        gear.setOnClickListener(v -> settingsDialog());
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
        setContentView(shell);
        refresh();
    }

    /** ضبط المواد: سعر اللتر وسعة الخزان لكل مادة. */
    private void settingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);

        for (final String material : Db.MATERIALS) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));

            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text(material, 17, Util.NAVY, true));

            double sell = db.priceFor(material);
            double buy = db.buyPrice(material);
            double freight = db.freightPrice(material);
            double cost = db.unitCost(material);
            double margin = db.unitMargin(material);

            words.addView(text("بيع " + (sell > 0 ? money(sell) : "—")
                    + "  •  شراء " + (buy > 0 ? money(buy) : "—")
                    + "  •  توصيل " + (freight > 0 ? money(freight) : "—"),
                    12, 0xff7c8186, false));
            words.addView(text("التكلفة " + (cost > 0 ? money(cost) + " ريال/لتر" : "غير مضبوطة")
                    + (margin != 0 ? "  •  الربح " + money(margin) : ""),
                    12, cost <= 0 ? Util.RED : margin > 0 ? Util.GREEN : Util.RED, true));
            words.addView(text("قيمة المخزون " + money(db.stockValue(material)) + " ر.ي"
                    + "  •  السعة " + money(db.capacity(material)) + " لتر",
                    11, 0xff8b9097, false));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(row);

            // صفّ الأزرار: أربعة تروس لكل مادة.
            LinearLayout buttons = new LinearLayout(this);
            buttons.setPadding(0, 0, 0, dp(6));

            Button sellBtn = action("بيع", false);
            sellBtn.setTextSize(12);
            sellBtn.setOnClickListener(v -> numberDialog("سعر بيع لتر " + material,
                    db.priceFor(material), value -> { db.setFuelPrice(material, value); reopenSettings(); }));
            buttons.addView(sellBtn, tabCell());

            Button buyBtn = action("شراء", false);
            buyBtn.setTextSize(12);
            buyBtn.setOnClickListener(v -> numberDialog("سعر شراء لتر " + material,
                    db.buyPrice(material), value -> { db.setBuyPrice(material, value); reopenSettings(); }));
            buttons.addView(buyBtn, tabCell());

            Button freightBtn = action("توصيل", false);
            freightBtn.setTextSize(12);
            freightBtn.setOnClickListener(v -> freightDialog(material));
            buttons.addView(freightBtn, tabCell());

            Button capBtn = action("السعة", false);
            capBtn.setTextSize(12);
            capBtn.setOnClickListener(v -> numberDialog("سعة خزان " + material,
                    db.capacity(material), value -> { db.setCapacity(material, value); reopenSettings(); }));
            buttons.addView(capBtn, tabCell());
            box.addView(buttons);

            View line = new View(this);
            line.setBackgroundColor(0xffeceef0);
            box.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }

        ScrollView form = new ScrollView(this);
        form.addView(box);
        new AlertDialog.Builder(this).setTitle("ضبط المواد")
                .setMessage("سعر اللتر يُطبَّق على كل طرمبات المادة، والسعة تُستعمل في نسبة الامتلاء.")
                .setView(form)
                .setPositiveButton("تم", null)
                .show();
    }

    private LinearLayout.LayoutParams tabCell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    /** يعيد فتح نافذة الضبط بعد أي تغيير لتظهر الأرقام الجديدة. */
    private void reopenSettings() {
        refresh();
        settingsDialog();
    }

    /**
     * أجرة التوصيل: تُدخل لكل لتر مباشرة، أو كمبلغ شحنة يُقسَّم على لتراتها.
     */
    private void freightDialog(final String material) {
        final EditText perLitre = new EditText(this);
        styleInput(perLitre);
        perLitre.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        perLitre.setHint("أجرة اللتر");
        double current = db.freightPrice(material);
        if (current > 0) perLitre.setText(fmt(current));

        final EditText total = new EditText(this);
        styleInput(total);
        total.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        total.setHint("أجرة الشحنة كاملة");

        final EditText litres = new EditText(this);
        styleInput(litres);
        litres.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        litres.setHint("لترات الشحنة");

        final TextView result = text("", 13, Util.GREEN, true);

        // القسمة تحسب أجرة اللتر تلقائيًا وتملأ الخانة الأولى.
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            public void onTextChanged(CharSequence t, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                double sum = Calc.number(total.getText().toString());
                double qty = Calc.number(litres.getText().toString());
                if (sum > 0 && qty > 0) {
                    double each = sum / qty;
                    result.setText("أجرة اللتر = " + money(each) + " ريال");
                    perLitre.setText(fmt(each));
                } else result.setText("");
            }
        };
        total.addTextChangedListener(watcher);
        litres.addTextChangedListener(watcher);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(text("أجرة التوصيل للتر", 13, 0xff7c8186, false));
        box.addView(perLitre);
        box.addView(text("أو احسبها من شحنة كاملة", 13, 0xff7c8186, false), space());
        box.addView(total);
        box.addView(litres);
        box.addView(result);

        ScrollView form = new ScrollView(this);
        form.addView(box);
        new AlertDialog.Builder(this).setTitle("أجرة توصيل " + material)
                .setMessage("تُضاف إلى سعر الشراء فتكون تكلفة اللتر الحقيقية.")
                .setView(form)
                .setPositiveButton("حفظ", (d, w) -> {
                    double value = Calc.number(perLitre.getText().toString());
                    if (!(value > 0)) {
                        Toast.makeText(this, "اكتب أجرة أكبر من صفر", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.setFreightPrice(material, value);
                    reopenSettings();
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private interface ValueSink { void accept(double value); }

    private void numberDialog(String title, double current, final ValueSink sink) {
        final EditText input = new EditText(this);
        styleInput(input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(current > 0 ? fmt(current) : "");
        input.setSelectAllOnFocus(true);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(8), dp(24), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle(title).setView(wrap)
                .setPositiveButton("حفظ", (d, w) -> {
                    double value = Calc.number(input.getText().toString());
                    if (!(value > 0)) {
                        Toast.makeText(this, "اكتب قيمة أكبر من صفر", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sink.accept(value);
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private String fmt(double n) {
        return n == Math.rint(n) ? String.format(Locale.US, "%.0f", n) : String.format(Locale.US, "%.2f", n);
    }

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

    private void refresh() {
        refreshSummary();
        refreshMaterials();
        refreshEntries();
    }

    /** بطاقة الإجمالي: مجموع المخزون والوارد والمبيعات. */
    private void refreshSummary() {
        summaryBox.removeAllViews();
        double stock = 0;
        for (String material : Db.MATERIALS) stock += db.materialSummary(material)[3];
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Util.round(Util.NAVY, dp(18)));
        // الرأس: قيمة كل المواد بسعر التكلفة بدل عدد اللترات.
        double value = db.stockValueTotal();
        card.addView(text("إجمالي قيمة المواد بسعر التكلفة", 13, 0xffCFE2FA, false));
        TextView grand = text((value > 0 ? money(value) : "—") + "  ر.ي", 30, Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(4));
        card.addView(grand);
        card.addView(text(money(stock) + " لتر  •  الشراء زائد أجرة التوصيل", 11, 0xffCFE2FA, false));

        // الخانات الثلاث: قيمة كل مادة بسعر التكلفة.
        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, dp(12), 0, 0);
        for (String material : Db.MATERIALS) {
            double each = db.stockValue(material);
            stats.addView(stat(material, each > 0 ? money(each) : "—"), cell());
        }
        card.addView(stats);
        if (value <= 0) {
            TextView hint = text("اضبط سعر الشراء وأجرة التوصيل من الترس", 11, 0xffFFD79A, true);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(8), 0, 0);
            card.addView(hint);
        }
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
            top.addView(historyButton(() -> materialHistory(name)));
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
            card.setOnLongClickListener(v -> { materialHistory(name); return true; });
            listBox.addView(card, space());
        }
        listBox.addView(text("المباع يُرحَّل تلقائيًا ضمن الصادر عند إغلاق كل وردية", 11, 0xff8b9097, false));
    }

    /** آخر الحركات مع الحذف بضغطة مطوّلة. */
    private void refreshEntries() {
        entriesBox.removeAllViews();
        if (entriesTitle != null) entriesTitle.setText("");
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
                final boolean auto = c.getString(4).startsWith("وردية #");
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
                    new AlertDialog.Builder(this).setTitle("حذف الحركة")
                            .setMessage("سيُحذف هذا السطر ويتغيّر رصيد المخزون.")
                            .setPositiveButton("حذف", (d, w) -> { try{db.deleteMaterialEntry(id); refresh();}catch(Exception e){new AlertDialog.Builder(this).setTitle("تعذّر الحذف").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();} })
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


    /** سجل حركات مادة واحدة باللترات. */
    private void materialHistory(String material) {
        java.util.List<String[]> lines = new java.util.ArrayList<>();
        java.util.List<String[]> reversed = new java.util.ArrayList<>();
        try (Cursor c = db.materialEntries(material, 200)) {
            while (c.moveToNext()) {
                boolean in = "IN".equals(c.getString(2));
                String note = c.getString(4);
                if (note == null || note.trim().isEmpty()) note = in ? "وارد" : "صادر";
                reversed.add(new String[]{in ? "وارد" : "صادر",
                        note + "  •  " + c.getString(5),
                        (in ? "+ " : "− ") + money(c.getDouble(3)) + " لتر",
                        String.valueOf(in ? Util.GREEN : Util.RED),
                        String.valueOf(c.getLong(0)),
                        note.startsWith("وردية #") ? "1" : "0"});
            }
        }
        for (int i = reversed.size() - 1; i >= 0; i--) lines.add(reversed.get(i));
        historyDialog("سجل " + material, lines, "لا توجد حركات على هذه المادة بعد.");
    }

    private void deleteEntry(long id) { db.deleteMaterialEntry(id); }

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
            box.addView(text(empty, 15, 0xff8b9097, false));
        } else {
            for (String[] line : lines) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(9), 0, dp(9));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(text(line[0], 15, Util.NAVY, true));
                words.addView(text(line[1], 11, 0xff8b9097, false));
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
                                .setPositiveButton("حذف", (d, w) -> { deleteEntry(entryId); refresh(); })
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

