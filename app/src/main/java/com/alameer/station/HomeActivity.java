package com.alameer.station.shifts;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** شاشة البداية: مطابقة العامل أو مطابقة الصناديق المحمية بكلمة سر. */
public class HomeActivity extends Activity {
    private Db db;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        Db.signIn(Branding.stationName(db));

        // كل فتح: كلمة السر وحدها تحدّد الواجهة، ولا يُحفظ دور.
        if (!Db.signedIn()) { askPassword(); return; }
        // العامل لا يرى إلا شاشة الوردية.
        if (Db.workerDevice()) {
            Intent shift = new Intent(this, ShiftActivity.class);
            shift.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(shift);
            finish();
            return;
        }
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.setPadding(dp(14), dp(18), dp(14), dp(12));

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
        words.addView(text("واجهة المدير", 12, 0xffCFE2FA, true));
        brand.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        // الترس انتقل إلى هنا؛ يفتح صفحة الإعدادات مباشرة بدل أن يزحم شاشة العامل.
        ImageButton gear = new ImageButton(this);
        gear.setContentDescription("الضبط");
        gear.setTooltipText("الضبط");
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        gear.setImageDrawable(new SettingsGear());
        gear.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(0x22FFFFFF, dp(23)), Util.round(Color.WHITE, dp(23))));
        gear.setOnClickListener(v -> {
            Intent intent = new Intent(this, ShiftActivity.class);
            intent.putExtra("openSettings", true);
            startActivity(intent);
        });
        brand.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(46)));

        // تسجيل خروج: يُنهي الجلسة فتعود شاشة كلمة السر ليدخل العامل برمزه.
        ImageButton exit = new ImageButton(this);
        exit.setContentDescription("تسجيل خروج");
        exit.setTooltipText("تسجيل خروج");
        exit.setPadding(dp(11), dp(11), dp(11), dp(11));
        exit.setImageDrawable(new LockIcon());
        exit.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(0x22FFFFFF, dp(23)), Util.round(Color.WHITE, dp(23))));
        exit.setOnClickListener(v -> logout());
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(46), dp(46));
        ep.setMargins(dp(8), 0, 0, 0);
        brand.addView(exit, ep);
        shell.addView(brand);

        TextView welcome = text("اختر ما تريد فتحه", 20, Util.NAVY, true);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(0, dp(14), 0, dp(10));
        shell.addView(welcome);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        row1.addView(tile("فحص الوردية", "مراجعة القراءات والحركات", 0,
                v -> startActivity(new Intent(this, ShiftActivity.class))), cell());
        row1.addView(tile("لوحة التحكم", "ملخّص الصناديق والمواد والديون", 2,
                v -> startActivity(new Intent(this, ControlPanelActivity.class))), cell());
        shell.addView(row1, rowWeight(false));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        row2.addView(tile("حركة الصناديق", "وارد وصادر النقد", 3,
                v -> startActivity(new Intent(this, CashboxActivity.class))), cell());
        row2.addView(tile("حركة الديون", "ديون وسداد المدينين", 4,
                v -> startActivity(new Intent(this, DebtActivity.class))), cell());
        shell.addView(row2, rowWeight(true));

        LinearLayout row3 = new LinearLayout(this);
        row3.setGravity(Gravity.CENTER);
        row3.addView(tile("حركة المخاريج", "مصروفات المحطة", 5,
                v -> startActivity(new Intent(this, ExpenseActivity.class))), cell());
        row3.addView(tile("حركة المواد", "وارد وصادر اللترات", 6,
                v -> startActivity(new Intent(this, MaterialActivity.class))), cell());
        shell.addView(row3, rowWeight(true));

        // سجل الانتظار والدفاتر: مرحلتا الرحلة الأخيرتان.
        int waiting = db.pendingCount();
        LinearLayout row4 = new LinearLayout(this);
        row4.setGravity(Gravity.CENTER);
        row4.addView(tile("سجل الورديات المنتظرة", waiting == 0 ? "لا ورديات منتظرة"
                        : waiting + " وردية بانتظار اعتمادك", 7,
                v -> startActivity(new Intent(this, IncomingActivity.class))), cell());
        row4.addView(tile("الدفاتر الرسمية", "تقارير الورديات المرحّلة", 8,
                v -> startActivity(new Intent(this, ArchiveActivity.class))), cell());
        shell.addView(row4, rowWeight(true));

        TextView credit = text(Branding.CREDIT, 12, 0xff8b9097, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(12), 0, dp(2));
        shell.addView(credit);

        setContentView(shell);
        new AppUpdater(this).check(false);
    }

    /** غلاف موحّد لشاشات الدخول. */
    private LinearLayout loginShell(String title, String note) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(dp(28), dp(24), dp(28), dp(24));

        ImageView mark = new ImageView(this);
        android.graphics.Bitmap logo = Branding.logo(this);
        if (logo != null) mark.setImageBitmap(logo); else mark.setImageResource(R.drawable.ic_wardiya_mark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(74));
        lp.gravity = Gravity.CENTER;
        lp.bottomMargin = dp(16);
        shell.addView(mark, lp);

        TextView name = text(Branding.stationName(db), 22, Util.NAVY, true);
        name.setGravity(Gravity.CENTER);
        shell.addView(name);

        TextView head = text(title, 17, Util.NAVY, true);
        head.setGravity(Gravity.CENTER);
        head.setPadding(0, dp(18), 0, dp(4));
        shell.addView(head);

        TextView hint = text(note, 13, 0xff7c8186, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(18));
        shell.addView(hint);
        return shell;
    }

    private EditText passwordField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(18);
        field.setTextColor(Util.NAVY);
        field.setSingleLine(true);
        field.setGravity(Gravity.CENTER);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(dp(14), dp(13), dp(14), dp(13));
        android.graphics.drawable.GradientDrawable bg = Util.round(Color.WHITE, dp(12));
        bg.setStroke(dp(1), 0xffdedfe2);
        field.setBackground(bg);
        return field;
    }

    private Button bigButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setBackground(Util.round(Util.NAVY, dp(14)));
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setStateListAnimator(null);
        return b;
    }

    /** ينهي الجلسة ويعود إلى شاشة كلمة السر. */
    private void logout() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("تسجيل خروج")
                .setMessage("ستعود شاشة كلمة السر.\nأدخل 6114 لواجهة العامل، أو كلمة سر المدير للعودة إلى هنا.")
                .setPositiveButton("خروج", (d, w) -> {
                    Db.endSession();
                    Intent home = new Intent(this, HomeActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(home);
                    finish();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** قفل مرسوم: جسم القفل وقوسه. */
    private class LockIcon extends android.graphics.drawable.Drawable {
        final android.graphics.Paint ink = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        public void draw(android.graphics.Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            ink.setColor(Color.WHITE);
            ink.setStyle(android.graphics.Paint.Style.STROKE);
            ink.setStrokeWidth(2.2f);
            ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            // القوس مفتوح جهة اليمين ليدل على الخروج.
            android.graphics.RectF arc = new android.graphics.RectF(7.5f, 3.5f, 16.5f, 12.5f);
            c.drawArc(arc, 180, 150, false, ink);
            ink.setStyle(android.graphics.Paint.Style.FILL);
            c.drawRoundRect(5.5f, 10.5f, 18.5f, 20.5f, 2.4f, 2.4f, ink);
            ink.setColor(Util.NAVY);
            c.drawCircle(12, 15.5f, 1.7f, ink);
            ink.setStyle(android.graphics.Paint.Style.STROKE);
            ink.setStrokeWidth(2f);
            c.drawLine(12, 15.5f, 12, 18, ink);
            c.restore();
        }
        public void setAlpha(int a) {}
        public void setColorFilter(android.graphics.ColorFilter f) {}
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** شاشة الدخول: كلمة السر هي التي تفتح واجهة المدير أو واجهة العامل. */
    private void askPassword() {
        LinearLayout shell = loginShell("أدخل كلمة السر",
                "كلمة سر المدير تفتح الواجهة كاملة،\nوكلمة سر العامل تفتح شاشة الوردية.");
        final EditText field = passwordField("كلمة السر");
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.bottomMargin = dp(14);
        shell.addView(field, fp);

        Button enter = bigButton("دخول");
        enter.setOnClickListener(v -> {
            String role = db.openSession(field.getText().toString());
            if (role.isEmpty()) {
                field.setText("");
                field.setError("كلمة السر غير صحيحة");
                Toast.makeText(this, "كلمة السر غير صحيحة", Toast.LENGTH_SHORT).show();
                return;
            }
            recreate();
        });
        shell.addView(enter, new LinearLayout.LayoutParams(-1, -2));

        field.setOnEditorActionListener((v, id, event) -> { enter.performClick(); return true; });
        setContentView(shell);
    }

    @Override public void onBackPressed() {
        // من شاشة الدخول: الخروج من التطبيق لا الدوران فيه.
        if (!Db.signedIn()) { finishAffinity(); return; }
        super.onBackPressed();
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
    /** البطاقة تملأ ارتفاع صفّها كاملًا حتى تبقى الشاشة بلا تمرير. */
    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(5), 0, dp(5), 0);
        return p;
    }

    /** الصفوف الثلاثة تتقاسم ما تبقّى من الشاشة بالتساوي. */
    private LinearLayout.LayoutParams rowWeight(boolean gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 0, 1);
        p.setMargins(0, gap ? dp(10) : 0, 0, 0);
        return p;
    }

    private LinearLayout tile(String title, String note, int icon, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        box.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), Util.round(Color.WHITE, dp(20)), null));
        box.setElevation(dp(3));
        box.setClickable(true);
        box.setOnClickListener(action);

        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Util.round(Util.ACCENT_SOFT, dp(28)));
        ImageView art = new ImageView(this);
        HomeIcon drawable = new HomeIcon(icon);
        drawable.setBounds(0, 0, dp(32), dp(32));
        art.setImageDrawable(drawable);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(32), dp(32));
        ip.gravity = Gravity.CENTER;
        disc.addView(art, ip);
        box.addView(disc, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView name = text(title, 15, Util.NAVY, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(8), 0, dp(2));
        name.setMaxLines(1);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView caption = text(note, 11, 0xff7c8186, false);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(caption, new LinearLayout.LayoutParams(-1, -2));

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

    /** ترس الضبط الأبيض في ترويسة الشاشة. */
    private static class SettingsGear extends Drawable {
        final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            ink.setColor(Color.WHITE);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(2.1f);
            ink.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(12, 12, 6.6f, ink);
            c.drawCircle(12, 12, 2.7f, ink);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                c.drawLine(12 + (float) Math.cos(a) * 6.6f, 12 + (float) Math.sin(a) * 6.6f,
                        12 + (float) Math.cos(a) * 9.4f, 12 + (float) Math.sin(a) * 9.4f, ink);
            }
            c.restore();
        }
        public void setAlpha(int a) { ink.setAlpha(a); }
        public void setColorFilter(android.graphics.ColorFilter f) { ink.setColorFilter(f); }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
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
                // لوحة تحكم: إطار وأعمدة بيانية.
                c.drawRoundRect(2.5f, 3.5f, 21.5f, 20.5f, 2f, 2f, paint);
                c.drawLine(2.5f, 7.6f, 21.5f, 7.6f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(6, 13.5f, 8.4f, 17.8f, 0.8f, 0.8f, paint);
                c.drawRoundRect(10.8f, 10.5f, 13.2f, 17.8f, 0.8f, 0.8f, paint);
                c.drawRoundRect(15.6f, 12f, 18, 17.8f, 0.8f, 0.8f, paint);
            } else if (kind == 3) {
                // صندوق نقدي.
                c.drawRoundRect(2.5f, 7, 21.5f, 20, 2, 2, paint);
                c.drawLine(2.5f, 11, 21.5f, 11, paint);
                c.drawLine(7, 7, 7, 4.5f, paint);
                c.drawLine(17, 7, 17, 4.5f, paint);
                c.drawLine(7, 4.5f, 17, 4.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 15.5f, 3, paint);
            } else if (kind == 4) {
                // ورقة نقدية للديون.
                c.drawRoundRect(2.5f, 6, 21.5f, 18, 1.8f, 1.8f, paint);
                c.drawLine(5.6f, 6, 5.6f, 18, paint);
                c.drawLine(18.4f, 6, 18.4f, 18, paint);
                c.drawCircle(12, 12, 3.1f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 12, 1.5f, paint);
            } else if (kind == 5) {
                // فاتورة المخاريج.
                android.graphics.Path receipt = new android.graphics.Path();
                receipt.moveTo(4.5f, 2.5f);
                receipt.lineTo(19.5f, 2.5f);
                receipt.lineTo(19.5f, 21.5f);
                receipt.lineTo(17, 19.6f);
                receipt.lineTo(14.5f, 21.5f);
                receipt.lineTo(12, 19.6f);
                receipt.lineTo(9.5f, 21.5f);
                receipt.lineTo(7, 19.6f);
                receipt.lineTo(4.5f, 21.5f);
                receipt.close();
                c.drawPath(receipt, paint);
                paint.setColor(Util.ACCENT);
                c.drawLine(8, 8, 16, 8, paint);
                c.drawLine(8, 12, 16, 12, paint);
                c.drawLine(8, 15.6f, 13, 15.6f, paint);
            } else if (kind == 6) {
                // خرطوم وقود للمواد.
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
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(7, 6.5f, 12.5f, 11.5f, 0.8f, 0.8f, paint);
            } else if (kind == 7) {
                // مستند وارد مع سهم نازل: ورديات العامل.
                c.drawRoundRect(4.5f, 2.5f, 19.5f, 21.5f, 2f, 2f, paint);
                paint.setColor(Util.ACCENT);
                c.drawLine(12, 7.5f, 12, 15, paint);
                c.drawLine(12, 15, 9, 12, paint);
                c.drawLine(12, 15, 15, 12, paint);
                c.drawLine(8, 18, 16, 18, paint);
            } else if (kind == 8) {
                // دفتر مفتوح: الدفاتر الرسمية.
                c.drawLine(12, 6.5f, 12, 20, paint);
                android.graphics.Path left = new android.graphics.Path();
                left.moveTo(12, 6.5f);
                left.cubicTo(9.5f, 4.2f, 6, 4.2f, 3, 5.5f);
                left.lineTo(3, 18.5f);
                left.cubicTo(6, 17.2f, 9.5f, 17.2f, 12, 20);
                c.drawPath(left, paint);
                android.graphics.Path right = new android.graphics.Path();
                right.moveTo(12, 6.5f);
                right.cubicTo(14.5f, 4.2f, 18, 4.2f, 21, 5.5f);
                right.lineTo(21, 18.5f);
                right.cubicTo(18, 17.2f, 14.5f, 17.2f, 12, 20);
                c.drawPath(right, paint);
                paint.setColor(Util.ACCENT);
                c.drawLine(5.5f, 9, 9.5f, 9, paint);
                c.drawLine(14.5f, 9, 18.5f, 9, paint);
                c.drawLine(5.5f, 12.5f, 9.5f, 12.5f, paint);
                c.drawLine(14.5f, 12.5f, 18.5f, 12.5f, paint);
            } else if (kind == 9) {
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
                android.graphics.Path shield = new android.graphics.Path();
                shield.moveTo(12, 2.2f);
                shield.lineTo(20.5f, 5.6f);
                shield.lineTo(20.5f, 12.4f);
                shield.cubicTo(20.5f, 17.4f, 16.9f, 20.6f, 12, 22.3f);
                shield.cubicTo(7.1f, 20.6f, 3.5f, 17.4f, 3.5f, 12.4f);
                shield.lineTo(3.5f, 5.6f);
                shield.close();
                c.drawPath(shield, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(8, 11.4f, 16, 17, 1.2f, 1.2f, paint);
                android.graphics.Path arc = new android.graphics.Path();
                arc.addArc(9.6f, 7.4f, 14.4f, 13.4f, 180, 180);
                c.drawPath(arc, paint);
            }
            c.restore();
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
