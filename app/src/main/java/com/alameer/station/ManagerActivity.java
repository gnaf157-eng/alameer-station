package com.alameer.station.shifts;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.Locale;

/** واجهة المدير: حركة الصناديق وحركة المواد بعد تجاوز كلمة السر. */
public class ManagerActivity extends Activity {
    private Db db;
    private TextView cashTotal, stockTotal, debtTotal;

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
        words.addView(text("واجهة المدير", 20, Color.WHITE, true));
        words.addView(text(Branding.stationName(db), 14, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView lock = text("🔒", 18, 0xffCFE2FA, false);
        header.addView(lock);
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(20));

        TextView welcome = text("اختر ما تريد فتحه", 18, Util.NAVY, true);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(0, dp(22), 0, dp(16));
        content.addView(welcome);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout cashTile = tile("حركة الصناديق", "وارد وصادر النقد", 0,
                v -> startActivity(new Intent(this, CashboxActivity.class)));
        cashTotal = (TextView) cashTile.getTag();
        row.addView(cashTile, cell());
        LinearLayout stockTile = tile("حركة المواد", "وارد وصادر اللترات", 1,
                v -> startActivity(new Intent(this, MaterialActivity.class)));
        stockTotal = (TextView) stockTile.getTag();
        row.addView(stockTile, cell());
        LinearLayout debtTile = tile("حركة الديون", "ديون وسداد المدينين", 2,
                v -> startActivity(new Intent(this, DebtActivity.class)));
        debtTotal = (TextView) debtTile.getTag();
        row.addView(debtTile, cell());
        content.addView(row);

        TextView hint = text("الأرقام تحت كل أيقونة محدّثة الآن", 12, 0xff8b9097, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(20), 0, 0);
        content.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(6), 0, 0);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshTotals();
    }

    /** ملخّص سريع يظهر داخل كل بطاقة. */
    private void refreshTotals() {
        cashTotal.setText(money(db.cashboxesTotal()) + " ر.ي");
        double stock = 0;
        for (String material : Db.MATERIALS) stock += db.materialSummary(material)[3];
        stockTotal.setText(money(stock) + " لتر");
        debtTotal.setText(money(db.debtsTotal()) + " ر.ي");
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(184), 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private LinearLayout tile(String title, String note, int icon, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(14), dp(6), dp(14));
        box.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), Util.round(Color.WHITE, dp(20)), null));
        box.setElevation(dp(3));
        box.setClickable(true);
        box.setOnClickListener(action);

        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Util.round(Util.ACCENT_SOFT, dp(26)));
        ImageView art = new ImageView(this);
        ManagerIcon drawable = new ManagerIcon(icon);
        drawable.setBounds(0, 0, dp(29), dp(29));
        art.setImageDrawable(drawable);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(29), dp(29));
        ip.gravity = Gravity.CENTER;
        disc.addView(art, ip);
        box.addView(disc, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView name = text(title, 14, Util.NAVY, true);
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(12), 0, dp(3));
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView caption = text(note, 10, 0xff7c8186, false);
        caption.setMaxLines(2);
        caption.setGravity(Gravity.CENTER);
        box.addView(caption, new LinearLayout.LayoutParams(-1, -2));

        TextView total = text("", 13, Util.NAVY, true);
        total.setGravity(Gravity.CENTER);
        total.setTextDirection(View.TEXT_DIRECTION_LTR);
        total.setPadding(dp(10), dp(5), dp(10), dp(5));
        total.setBackground(Util.round(Util.ACCENT_SOFT, dp(9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(10), 0, 0);
        box.addView(total, lp);
        box.setTag(total);
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    /** صندوق نقدي وقطرة وقود. */
    private static class ManagerIcon extends android.graphics.drawable.Drawable {
        final int kind;
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ManagerIcon(int kind) { this.kind = kind; }
        public void draw(android.graphics.Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            paint.setColor(Util.NAVY);
            if (kind == 0) {
                c.drawRoundRect(2.5f, 7, 21.5f, 20, 2, 2, paint);
                c.drawLine(2.5f, 11, 21.5f, 11, paint);
                c.drawLine(7, 7, 7, 4.5f, paint);
                c.drawLine(17, 7, 17, 4.5f, paint);
                c.drawLine(7, 4.5f, 17, 4.5f, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 15.5f, 3, paint);
            } else if (kind == 1) {
                // خرطوم وقود: مقبض ومسدس وأنبوب منحنٍ.
                c.drawRoundRect(5, 4, 14.5f, 21, 1.6f, 1.6f, paint);
                c.drawLine(3, 21, 16.5f, 21, paint);
                c.drawLine(14.5f, 9.5f, 17, 9.5f, paint);
                c.drawLine(17, 9.5f, 17, 16.5f, paint);
                android.graphics.Path hose = new android.graphics.Path();
                hose.moveTo(17, 16.5f);
                hose.cubicTo(17, 19.4f, 21, 19.4f, 21, 16.5f);
                hose.lineTo(21, 6.5f);
                hose.lineTo(18.4f, 3.6f);
                c.drawPath(hose, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(7, 6.5f, 12.5f, 11.5f, 0.8f, 0.8f, paint);
            } else {
                // ورقة نقدية: إطار وعملة في الوسط وحافتان.
                c.drawRoundRect(2.5f, 6, 21.5f, 18, 1.8f, 1.8f, paint);
                c.drawLine(5.6f, 6, 5.6f, 18, paint);
                c.drawLine(18.4f, 6, 18.4f, 18, paint);
                c.drawCircle(12, 12, 3.1f, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 12, 1.5f, paint);
            }
            c.restore();
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
