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

    private void refresh() {
        refreshSummary();
        refreshDebtors();
        refreshEntries();
    }

    /** بطاقة إجمالي الديون غير المسدّدة. */
    private void refreshSummary() {
        summaryBox.removeAllViews();
        double total = 0, credit = 0, paid = 0;
        int count = 0, settled = 0;
        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                count++;
                double balance = c.getDouble(7);
                if (balance > 0.009) total += balance;
                else if (balance < -0.009) credit -= balance;
                else settled++;
                paid += c.getDouble(6);
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
        stats.addView(stat("لهم عندنا", money(credit)), cell());
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
        boolean creditHeaderShown = false;
        // جولتان: 0 للمستحق عليهم والمقفلة حساباتهم، 1 لأصحاب الرصيد الدائن مجموعين.
        for (int pass = 0; pass < 2; pass++) {
        try (Cursor c = db.debtors(false)) {
            while (c.moveToNext()) {
                boolean isCredit = c.getDouble(7) < -0.009;
                if (isCredit != (pass == 1)) continue;
                if (isCredit && !creditHeaderShown) {
                    creditHeaderShown = true;
                    listBox.addView(sectionTitle("أرصدة لهم عندنا"));
                }
                count++;
                final long id = c.getLong(0);
                final String name = c.getString(1);
                final String phone = c.getString(2);
                final double opening = c.getDouble(3);
                final boolean active = c.getInt(4) == 1;
                final double balance = c.getDouble(7);
                final boolean clear = Math.abs(balance) < 0.01;
                // سالب = له عندنا رصيد (أخضر)، موجب = عليه دين (أحمر)، صفر = رمادي.
                final boolean credit = balance < -0.009;
                final int balanceTint = clear ? 0xff7c8186 : credit ? Util.GREEN : Util.RED;

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
                        : clear ? "الحساب مقفل — لا دين ولا رصيد"
                        : credit ? "دفع أكثر مما عليه" : phone.isEmpty() ? "اضغط لتسجيل دين أو سداد" : phone;
                words.addView(text(note, 11, active && (clear || credit) ? Util.GREEN : 0xff8b9097, false));
                top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                LinearLayout amountBox = new LinearLayout(this);
                amountBox.setOrientation(LinearLayout.VERTICAL);
                TextView amount = text(money(balance), 22, balanceTint, true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                amount.setGravity(Gravity.LEFT);
                amountBox.addView(amount);
                TextView unit = text(clear ? "لا يوجد دين" : credit ? "ريال له" : "ريال عليه", 10, 0xff8b9097, false);
                unit.setGravity(Gravity.LEFT);
                amountBox.addView(unit);
                top.addView(amountBox);
                top.addView(historyButton(() -> debtorHistory(id, name)));
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
        if (entriesTitle != null) entriesTitle.setText("");
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
        boolean linked = !db.debtorTelegram(id).trim().isEmpty();
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"📄  كشف حساب وإرساله", "سجل حركاته",
                        linked ? "✓ تلغرام مربوط — تعديل" : "ربط تلغرام",
                        "تعديل البيانات", "إيقاف المدين", "تفعيل المدين", "حذف المدين"},
                        (d, which) -> {
                            if (which == 0) sendStatement(id, phone);
                            else if (which == 1) debtorHistory(id, name);
                            else if (which == 2) telegramDialog(id, name);
                            else if (which == 3) debtorDialog(id, name, phone, opening);
                            else if (which == 4) { db.setDebtorActive(id, false); refresh(); }
                            else if (which == 5) { db.setDebtorActive(id, true); refresh(); }
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

    /** يولّد كشف الحساب PDF ثم يعرض خيار الإرسال عبر واتساب أو أي تطبيق. */
    private void sendStatement(long debtorId, String phone) {
        final java.io.File file;
        try {
            file = new StatementReport(this, db).build(debtorId);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("تعذر إنشاء الكشف")
                    .setMessage(String.valueOf(e.getMessage())).setPositiveButton("حسنًا", null).show();
            return;
        }
        final android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".files", file);
        final String body = new StatementReport(this, db).message(debtorId);
        final String clean = phone == null ? "" : phone.replaceAll("[^0-9]", "");

        String[] choices = clean.isEmpty()
                ? new String[]{"مشاركة الكشف", "فتح الكشف"}
                : new String[]{"إرسال واتساب إلى " + phone, "مشاركة الكشف", "فتح الكشف"};
        new AlertDialog.Builder(this).setTitle("كشف الحساب جاهز")
                .setItems(choices, (d, which) -> {
                    int pick = clean.isEmpty() ? which + 1 : which;
                    if (pick == 0) whatsapp(clean, body, uri);
                    else if (pick == 1) share(uri, body);
                    else open(uri);
                }).show();
    }

    /** ربط العميل بمحادثة تلغرام ليصله إشعار بعد كل حركة. */
    private void telegramDialog(final long id, final String name) {
        final EditText input = new EditText(this);
        styleInput(input);
        input.setHint("معرّف المحادثة، مثل 123456789");
        input.setText(db.debtorTelegram(id));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(text("يصل " + name + " إشعار فور تسجيل دين أو سداد.", 13, 0xff7c8186, false));
        box.addView(input);
        box.addView(text("كيف تحصل عليه: اطلب من العميل مراسلة البوت، ثم افتح"
                + " @userinfobot في تلغرام ليعطيه رقمه.", 12, 0xff8b9097, false));

        new AlertDialog.Builder(this).setTitle("تلغرام " + name)
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    String chat = input.getText().toString().trim();
                    if (!chat.isEmpty() && !Telegram.validChat(chat)) {
                        Toast.makeText(this, "معرّف المحادثة غير صالح", Toast.LENGTH_LONG).show();
                        return;
                    }
                    db.setDebtorTelegram(id, chat);
                    Toast.makeText(this, chat.isEmpty() ? "أُلغي الربط" : "رُبط بتلغرام", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("رسالة تجريبية", (d, w) -> testTelegram(id, name))
                .setNegativeButton("إلغاء", null).show();
    }

    /** يرسل رسالة تجربة للتأكد من صحة الرمز والمعرّف. */
    private void testTelegram(final long id, final String name) {
        final String token = db.telegramToken();
        final String chat = db.debtorTelegram(id);
        if (token.isEmpty()) {
            Toast.makeText(this, "اضبط رمز البوت من الإعدادات أولًا", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "جارٍ الإرسال...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String error = Telegram.send(token, chat,
                    Branding.stationName(db) + "\nرسالة تجريبية للأخ/ " + name
                            + "\nسيصلك إشعار بعد كل حركة في حسابك.");
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle(error.isEmpty() ? "وصلت الرسالة" : "تعذر الإرسال")
                        .setMessage(error.isEmpty() ? "تحقّق من هاتف العميل." : error)
                        .setPositiveButton("حسنًا", null).show();
            });
        }, "telegram-test").start();
    }

    /** يفتح محادثة واتساب مع الرقم ويرفق الملف؛ يسقط إلى المشاركة عند غياب واتساب. */
    private void whatsapp(String digits, String body, android.net.Uri uri) {
        String number = digits.startsWith("00") ? digits.substring(2) : digits;
        if (number.length() == 9 && number.startsWith("7")) number = "967" + number;
        else if (number.startsWith("0")) number = "967" + number.substring(1);
        try {
            android.content.Intent chat = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://wa.me/" + number + "?text="
                            + android.net.Uri.encode(body)));
            startActivity(chat);
            // الملف يُرسل بخطوة ثانية لأن واتساب لا يقبل نصًا ومرفقًا لرقم محدد معًا.
            Toast.makeText(this, "أرسل الرسالة ثم أرفق الكشف من نافذة المشاركة", Toast.LENGTH_LONG).show();
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("application/pdf");
            send.setPackage("com.whatsapp");
            send.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            send.putExtra(android.content.Intent.EXTRA_TEXT, body);
            send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(send);
        } catch (Exception e) {
            share(uri, body);
        }
    }

    private void share(android.net.Uri uri, String body) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, body);
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "كشف حساب");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(intent, "إرسال كشف الحساب"));
    }

    private void open(android.net.Uri uri) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق لفتح PDF", Toast.LENGTH_SHORT).show(); }
    }

    /** تسجيل دين أو سداد، مع عرض رصيد المدين قبل الحفظ. */
    private void entryDialog(final long debtorId, String debtorName) {
        final double current = db.debtorBalance(debtorId);

        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        balanceCard.setBackground(Util.round(Util.ACCENT_SOFT, dp(14)));
        balanceCard.addView(text((current < -0.009 ? "رصيد له عند " : "الدين الحالي على ") + debtorName, 12, 0xff5a6672, false));
        TextView balanceText = text(money(current) + "  ر.ي", 24,
                Math.abs(current) < 0.01 ? 0xff7c8186 : current < 0 ? Util.GREEN : Util.RED, true);
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
            afterText.setTextColor(after > 0.01 ? Util.RED : after < -0.01 ? Util.GREEN : 0xff7c8186);
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


    /** سجل حركات مدين واحد بالترتيب الزمني. */
    private void debtorHistory(long debtorId, String name) {
        java.util.List<String[]> lines = new java.util.ArrayList<>();
        java.util.List<String[]> reversed = new java.util.ArrayList<>();
        try (Cursor c = db.debtEntries(debtorId, 200)) {
            while (c.moveToNext()) {
                boolean isDebt = "DEBT".equals(c.getString(1));
                String note = c.getString(3);
                if (note == null || note.trim().isEmpty()) note = isDebt ? "دين" : "سداد";
                reversed.add(new String[]{isDebt ? "دين" : "سداد",
                        note + "  •  " + c.getString(4),
                        (isDebt ? "+ " : "− ") + money(c.getDouble(2)),
                        String.valueOf(isDebt ? Util.RED : Util.GREEN),
                        String.valueOf(c.getLong(0)),
                        c.getLong(6) > 0 ? "1" : "0"});
            }
        }
        for (int i = reversed.size() - 1; i >= 0; i--) lines.add(reversed.get(i));
        historyDialog("سجل " + name, lines, "لا توجد حركات على هذا المدين بعد.");
    }

    private void deleteEntry(long id) { db.deleteDebtEntry(id); }

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
