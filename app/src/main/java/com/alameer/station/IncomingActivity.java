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
        words.addView(text("تصل من جهاز العامل — راجعها ثم اعتمدها", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));

        Button pick = new Button(this);
        pick.setText("⟳  جلب ورديات العامل");
        pick.setAllCaps(false);
        pick.setTextSize(16);
        pick.setTextColor(Color.WHITE);
        pick.setBackground(Util.round(Util.NAVY, dp(14)));
        pick.setPadding(dp(16), dp(14), dp(16), dp(14));
        pick.setOnClickListener(v -> new Sync(this).pullShifts(true));
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

                Button review = new Button(this);
                review.setText("فتح للمراجعة والتعديل");
                review.setAllCaps(false);
                review.setTextColor(Color.WHITE);
                review.setBackground(Util.round(Util.NAVY, dp(12)));
                review.setOnClickListener(v -> {
                    try {
                        db.reopenShift(id, "مراجعة المدير");
                        Intent open = new Intent(this, ShiftActivity.class);
                        open.putExtra("openShift", id);
                        startActivity(open);
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                });
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.setMargins(0, dp(10), 0, 0);
                card.addView(review, rp);

                Button approve = new Button(this);
                approve.setText("اعتماد وترحيل");
                approve.setAllCaps(false);
                approve.setTextColor(Color.WHITE);
                approve.setBackground(Util.round(Util.GREEN, dp(12)));
                approve.setOnClickListener(v -> approve(id, who, balance));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
                bp.setMargins(0, dp(8), 0, 0);
                card.addView(approve, bp);

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.setMargins(0, dp(8), 0, dp(4));
                listBox.addView(card, cp);
            }
        }
        if (count == 0) listBox.addView(text("لا توجد ورديات واردة. اضغط «جلب ورديات العامل».", 14, 0xff777d84, false));
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
