package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** شاشة البداية: مطابقة العامل أو مطابقة الصناديق المحمية بكلمة سر. */
public class HomeActivity extends Activity {
    static final String MANAGER_PIN = "2216";
    private Db db;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.setPadding(dp(20), dp(28), dp(20), dp(24));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(16), dp(18), dp(16), dp(18));
        brand.setBackground(Util.round(Util.NAVY, dp(20)));
        ImageView mark = new ImageView(this);
        android.graphics.Bitmap logo = Branding.logo(this);
        if (logo != null) mark.setImageBitmap(logo); else mark.setImageResource(R.drawable.ic_wardiya_mark);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12), 0, 0, 0);
        words.addView(text(Branding.stationName(db), 19, Color.WHITE, true));
        words.addView(text("طابق ورحّل • مطابقة الورديات والصناديق", 11, 0xffCFE2FA, false));
        brand.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(brand);

        TextView welcome = text("اختر ما تريد فتحه", 20, Util.NAVY, true);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(0, dp(30), 0, dp(18));
        shell.addView(welcome);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(tile("مطابقة العامل", "الورديات والتقارير", 0, false,
                v -> startActivity(new Intent(this, ShiftActivity.class))), cell());
        row.addView(tile("حركة الصناديق", "وارد وصادر النقد", 1, true,
                v -> askPin(CashboxActivity.class)), cell());
        row.addView(tile("حركة المواد", "وارد وصادر اللترات", 2, true,
                v -> askPin(MaterialActivity.class)), cell());
        shell.addView(row);

        TextView hint = text("حركة الصناديق وحركة المواد للمدير وتفتحان بكلمة سر", 13, 0xff7c8186, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(24), 0, 0);
        shell.addView(hint);

        View filler = new View(this);
        shell.addView(filler, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView credit = text(Branding.CREDIT, 12, 0xff8b9097, false);
        credit.setGravity(Gravity.CENTER);
        shell.addView(credit);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(shell);
        setContentView(scroll);
        new AppUpdater(this).check(false);
    }

    @Override protected void onResume() {
        super.onResume();
        recreateIfBrandChanged();
    }

    private String lastBrand = null;
    private void recreateIfBrandChanged() {
        String now = Branding.stationName(db);
        if (lastBrand != null && !lastBrand.equals(now)) recreate();
        lastBrand = now;
    }

    /** بطاقة كبيرة قابلة للنقر تمثّل أحد المدخلين. */
    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(184), 1);
        p.setMargins(dp(5), 0, dp(5), 0);
        return p;
    }

    private LinearLayout tile(String title, String note, int icon, boolean locked, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(14));
        box.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), Util.round(Color.WHITE, dp(20)), null));
        box.setElevation(dp(3));
        box.setClickable(true);
        box.setOnClickListener(action);

        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Util.round(Util.ACCENT_SOFT, dp(27)));
        ImageView art = new ImageView(this);
        HomeIcon drawable = new HomeIcon(icon);
        drawable.setBounds(0, 0, dp(30), dp(30));
        art.setImageDrawable(drawable);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(30), dp(30));
        ip.gravity = Gravity.CENTER;
        disc.addView(art, ip);
        box.addView(disc, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView name = text(title, 14, Util.NAVY, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(12), 0, dp(3));
        name.setMaxLines(2);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView caption = text(note, 11, 0xff7c8186, false);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        box.addView(caption, new LinearLayout.LayoutParams(-1, -2));

        TextView badge = text(locked ? "🔒 بكلمة سر" : "مفتوح", 10, locked ? Util.NAVY : 0xff8b9097, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        if (locked) badge.setBackground(Util.round(Util.ACCENT_SOFT, dp(9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(9), 0, 0);
        box.addView(badge, lp);
        return box;
    }

    /** لا تُفتح الصناديق إلا بكلمة السر الثابتة. */
    private void askPin(final Class<?> target) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextSize(22);
        input.setGravity(Gravity.CENTER);
        input.setHint("••••");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(12), dp(28), 0);
        box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("دخول المدير")
                .setMessage("اكتب كلمة سر المدير للدخول.")
                .setView(box)
                .setPositiveButton("دخول", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!MANAGER_PIN.equals(input.getText().toString().trim())) {
                input.setError("كلمة السر غير صحيحة");
                input.setText("");
                return;
            }
            dialog.dismiss();
            startActivity(new Intent(this, target));
        }));
        dialog.show();
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

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    /** أيقونتا الشاشة: طرمبة للعامل وصندوق نقدي للمدير. */
    private static class HomeIcon extends Drawable {
        final int kind;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        HomeIcon(int kind) { this.kind = kind; }
        public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Util.NAVY);
            if (kind == 0) {
                c.drawRoundRect(4, 3, 14, 21, 1.5f, 1.5f, paint);
                c.drawLine(2, 21, 16, 21, paint);
                c.drawLine(14, 11, 17, 11, paint);
                c.drawLine(17, 11, 17, 18, paint);
                c.drawArc(17, 16, 21, 20, 0, 180, false, paint);
                c.drawLine(21, 18, 21, 7, paint);
                c.drawLine(21, 7, 18, 4, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(6, 5.5f, 12, 10, 1, 1, paint);
            } else if (kind == 2) {
                android.graphics.Path drop = new android.graphics.Path();
                drop.moveTo(12, 2.5f);
                drop.cubicTo(17.5f, 9, 20, 12.5f, 20, 15.5f);
                drop.cubicTo(20, 19.6f, 16.4f, 22, 12, 22);
                drop.cubicTo(7.6f, 22, 4, 19.6f, 4, 15.5f);
                drop.cubicTo(4, 12.5f, 6.5f, 9, 12, 2.5f);
                drop.close();
                c.drawPath(drop, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRect(8, 13.5f, 16, 15.2f, paint);
                c.drawRect(11.1f, 10.4f, 12.9f, 18.3f, paint);
            } else {
                c.drawRoundRect(2.5f, 7, 21.5f, 20, 2, 2, paint);
                c.drawLine(2.5f, 11, 21.5f, 11, paint);
                c.drawLine(7, 7, 7, 4.5f, paint);
                c.drawLine(17, 7, 17, 4.5f, paint);
                c.drawLine(7, 4.5f, 17, 4.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 15.5f, 3, paint);
            }
            c.restore();
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
