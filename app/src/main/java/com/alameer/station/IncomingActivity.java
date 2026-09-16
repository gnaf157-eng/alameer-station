package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.Locale;

/** ورديات العامل الواردة: استيراد الملف ثم مراجعتها واعتمادها. */
public class IncomingActivity extends Activity {
    private static final int PICK_FILE = 71;
    private Db db;
    private LinearLayout listBox;

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
        words.addView(text("ورديات العامل", 19, Color.WHITE, true));
        words.addView(text("استورد ملف الوردية ثم راجعها واعتمدها", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));

        Button pick = new Button(this);
        pick.setText("＋  استيراد ملف وردية");
        pick.setAllCaps(false);
        pick.setTextSize(16);
        pick.setTextColor(Color.WHITE);
        pick.setBackground(Util.round(Util.NAVY, dp(14)));
        pick.setPadding(dp(16), dp(14), dp(16), dp(14));
        pick.setOnClickListener(v -> pickFile());
        content.addView(pick, new LinearLayout.LayoutParams(-1, -2));

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

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void pickFile() {
        try {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick, PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق لاختيار الملفات", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILE || result != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData());
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 1024 * 1024) throw new java.io.IOException("الملف كبير جدًا");
                out.write(buffer, 0, n);
            }
            String body = out.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            ShiftFile.Shift shift = ShiftFile.read(body);
            confirmImport(shift);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("تعذر قراءة الملف")
                    .setMessage(String.valueOf(e.getMessage()))
                    .setPositiveButton("حسنًا", null).show();
        }
    }

    /** يعرض ملخّص الوردية قبل الاستيراد، ويمنعه إن لم تطابق القراءات السابقة. */
    private void confirmImport(final ShiftFile.Shift s) {
        String mismatch = db.readingMismatch(s);
        StringBuilder sb = new StringBuilder();
        sb.append("العامل: ").append(s.worker).append("\n")
          .append("التاريخ: ").append(s.date).append("\n")
          .append("الطرمبات: ").append(s.readings.size())
          .append("  •  الحركات: ").append(s.moves.size()).append("\n\n")
          .append("المبيعات ").append(money(s.sales())).append(" ر.ي\n")
          .append("المقبوضات ").append(money(s.total("COLLECTION"))).append(" ر.ي\n")
          .append("النقد المسلّم ").append(money(s.total("CASH"))).append(" ر.ي\n")
          .append("الديون ").append(money(s.total("DEBT"))).append(" ر.ي\n")
          .append("المخاريج ").append(money(s.total("EXPENSE"))).append(" ر.ي\n")
          .append("الباقي ").append(money(s.balance())).append(" ر.ي");

        if (!mismatch.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("القراءات لا تطابق عدّاداتك")
                    .setMessage("لا يمكن الاستيراد لأن القراءة السابقة تختلف:" + mismatch
                            + "\n\nتأكد أن العامل يرسل وردية متسلسلة مع آخر وردية اعتمدتها.")
                    .setPositiveButton("حسنًا", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("استيراد وردية " + s.worker)
                .setMessage(sb.toString())
                .setPositiveButton("استيراد للمراجعة", (d, w) -> {
                    try {
                        long id = db.importShift(s);
                        Toast.makeText(this, "استُوردت كوردية #" + id, Toast.LENGTH_LONG).show();
                        refresh();
                    } catch (Exception e) {
                        new AlertDialog.Builder(this).setTitle("تعذر الاستيراد")
                                .setMessage(String.valueOf(e.getMessage()))
                                .setPositiveButton("حسنًا", null).show();
                    }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void refresh() {
        listBox.removeAllViews();
        listBox.addView(text("بانتظار الاعتماد", 16, Util.NAVY, true));
        int count = 0;
        try (Cursor c = db.incomingShifts()) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                final String who = c.getString(1);
                String date = c.getString(2);
                double sales = c.getDouble(3), balance = c.getDouble(4);
                boolean matched = Math.abs(balance) < 0.01;

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(15), dp(14), dp(15), dp(14));
                card.setBackground(Util.round(Color.WHITE, dp(16)));
                card.setElevation(dp(2));
                card.addView(text("وردية #" + id + " — " + who, 17, Util.NAVY, true));
                card.addView(text(date + "  •  مبيعات " + money(sales) + " ر.ي", 13, 0xff7c8186, false));
                card.addView(text(matched ? "مطابقة" : "فرق " + money(Math.abs(balance)) + " ر.ي "
                                + (balance > 0 ? "(عجز على العامل)" : "(زيادة)"),
                        14, matched ? Util.GREEN : Util.RED, true));

                Button approve = new Button(this);
                approve.setText("اعتماد وترحيل");
                approve.setAllCaps(false);
                approve.setTextColor(Color.WHITE);
                approve.setBackground(Util.round(Util.GREEN, dp(12)));
                approve.setOnClickListener(v -> approve(id, who, balance));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
                bp.setMargins(0, dp(10), 0, 0);
                card.addView(approve, bp);

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.setMargins(0, dp(8), 0, dp(4));
                listBox.addView(card, cp);
            }
        }
        if (count == 0) listBox.addView(text("لا توجد ورديات واردة. استورد ملفًا من العامل.", 14, 0xff777d84, false));
    }

    /** الاعتماد يرحّل الوردية ويقيّدها؛ الفرق يبقى محسوبًا على العامل. */
    private void approve(final long id, String who, double balance) {
        boolean matched = Math.abs(balance) < 0.01;
        String message = matched
                ? "ستُرحَّل الوردية إلى الصناديق والديون والمخزون، ويُسجَّل قيدها المحاسبي."
                : "الفرق " + money(Math.abs(balance)) + " ر.ي سيُقيَّد على عهدة " + who
                  + " ويظهر في حسابه، ثم تُرحَّل الوردية ويُسجَّل قيدها.";
        new AlertDialog.Builder(this).setTitle("اعتماد وردية #" + id)
                .setMessage(message)
                .setPositiveButton("اعتماد", (d, w) -> {
                    try {
                        if (!matched) db.settleShift(id, "فرق محسوب على العامل");
                        String posted = db.approveIncoming(id, db.defaultCashbox());
                        new AlertDialog.Builder(this).setTitle("اعتُمدت الوردية #" + id)
                                .setMessage(posted.isEmpty() ? "تمّ الاعتماد والترحيل." : "رُحّلت:\n" + posted)
                                .setPositiveButton("حسنًا", null).show();
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null).show();
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
