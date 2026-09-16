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
        // شاشات المدير وحده: لا تُفتح في جلسة العامل.
        if (!Db.managerMode()) { finish(); return; }
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
        words.addView(text("سجل الورديات المنتظرة", 19, Color.WHITE, true));
        words.addView(text("اضغط الوردية لمراجعتها ثم ترحيلها إلى الدفاتر", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));

        Button pick = new Button(this);
        pick.setText("⟳  تحديث السجل");
        pick.setAllCaps(false);
        pick.setTextSize(16);
        pick.setTextColor(Color.WHITE);
        pick.setBackground(Util.round(Util.NAVY, dp(14)));
        pick.setPadding(dp(16), dp(14), dp(16), dp(14));
        pick.setOnClickListener(v -> {
            Relay relay = new Relay(this);
            if (!relay.linked()) {
                new AlertDialog.Builder(this).setTitle("الجهاز غير مربوط")
                        .setMessage("أنشئ رمز الربط من الإعدادات، واكتبه في جهاز العامل مرة واحدة.")
                        .setPositiveButton("حسنًا", null).show();
                return;
            }
            relay.receive(true, this::refresh);
        });
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

    @Override protected void onResume() {
        super.onResume();
        if (!Db.managerMode()) { finish(); return; }
        if (isFinishing() || listBox == null) return;
        refresh();
        // جلب صامت عند الفتح، فلا ينتظر المدير ضغط زر.
        Relay relay = new Relay(this);
        if (relay.linked()) relay.receive(false, this::refresh);
    }

    private void refresh() {
        listBox.removeAllViews();
        int count = 0;
        try (Cursor c = db.incomingShifts()) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                final String who = c.getString(1);
                String date = c.getString(2);
                double balance = c.getDouble(4);
                int pumps = c.getInt(5);
                boolean matched = Math.abs(balance) < 0.01;

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(16), dp(15), dp(16), dp(15));
                card.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x18000000),
                        Util.round(Color.WHITE, dp(16)), null));
                card.setElevation(dp(2));
                card.setClickable(true);
                card.setOnClickListener(v -> review(id));

                // اسم العامل أولًا، فهو عنوان الوردية.
                card.addView(text(who, 19, Util.NAVY, true));
                card.addView(text(pumps + " طرمبة  •  " + date, 14, 0xff667078, false));
                card.addView(text(matched ? "مطابقة — جاهزة للترحيل"
                                : "فرق " + money(Math.abs(balance)) + " ر.ي "
                                  + (balance > 0 ? "(عجز على العامل)" : "(زيادة)"),
                        13, matched ? Util.GREEN : Util.RED, true));

                TextView go = text("اضغط للمراجعة والترحيل  ‹", 12, Util.ACCENT, true);
                go.setPadding(0, dp(8), 0, 0);
                card.addView(go);

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.setMargins(0, dp(6), 0, dp(6));
                listBox.addView(card, cp);
            }
        }
        if (count == 0)
            listBox.addView(text("لا توجد ورديات منتظرة.\nالورديات الواردة من العامل تظهر هنا.",
                    14, 0xff777d84, false));
    }

    /** يفتح الوردية في خانات المطابقة ليراجعها المدير قبل الترحيل. */
    private void review(long id) {
        try {
            db.reopenShift(id, "مراجعة المدير");
            Intent open = new Intent(this, ShiftActivity.class);
            open.putExtra("openShift", id);
            open.putExtra("reviewing", true);
            startActivity(open);
        } catch (Exception e) {
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
        }
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
