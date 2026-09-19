package com.alameer.station.shifts;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/**
 * قفل التطبيق: تُطلب البصمة أولًا، والرمز بديل دائم عنها.
 * لا يُفتح شيء من التطبيق قبل تجاوز هذه الشاشة.
 * تستعمل واجهة البصمة الأصلية في أندرويد فلا تحتاج مكتبة خارجية.
 */
public class LockActivity extends Activity {
    private Db db;
    private EditText pinInput;
    private TextView hint;
    private android.os.CancellationSignal cancel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(dp(28), dp(24), dp(28), dp(24));

        ImageView mark = new ImageView(this);
        android.graphics.Bitmap logo = Branding.logo(this);
        if (logo != null) mark.setImageBitmap(logo);
        else mark.setImageResource(R.drawable.ic_wardiya_mark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
        lp.gravity = Gravity.CENTER;
        lp.bottomMargin = dp(14);
        shell.addView(mark, lp);

        TextView name = text(Branding.stationName(db), 21, Util.NAVY, true);
        name.setGravity(Gravity.CENTER);
        shell.addView(name);

        hint = text("التطبيق مقفل", 14, 0xff7c8186, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(20));
        shell.addView(hint);

        pinInput = new EditText(this);
        pinInput.setHint("الرمز");
        pinInput.setTextSize(20);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setTextColor(Util.NAVY);
        pinInput.setSingleLine(true);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setPadding(dp(14), dp(13), dp(14), dp(13));
        android.graphics.drawable.GradientDrawable bg = Util.round(Color.WHITE, dp(12));
        bg.setStroke(dp(1), 0xffdedfe2);
        pinInput.setBackground(bg);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.bottomMargin = dp(12);
        shell.addView(pinInput, pp);

        Button enter = big("دخول", true);
        enter.setOnClickListener(v -> tryPin());
        shell.addView(enter, new LinearLayout.LayoutParams(-1, -2));

        if (fingerprintReady()) {
            Button finger = big("استخدام البصمة", false);
            finger.setOnClickListener(v -> askFingerprint());
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
            fp.topMargin = dp(10);
            shell.addView(finger, fp);
        }

        setContentView(shell);
        pinInput.setOnEditorActionListener((v, id, e) -> { tryPin(); return true; });
        if (fingerprintReady()) askFingerprint();
    }

    /** هل الجهاز يملك بصمة مسجّلة؟ */
    private boolean fingerprintReady() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        try {
            android.hardware.biometrics.BiometricManager manager =
                    getSystemService(android.hardware.biometrics.BiometricManager.class);
            if (manager == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                return manager.canAuthenticate()
                        == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void askFingerprint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            if (cancel != null) cancel.cancel();
            cancel = new android.os.CancellationSignal();
            new android.hardware.biometrics.BiometricPrompt.Builder(this)
                    .setTitle(Branding.stationName(db))
                    .setSubtitle("افتح التطبيق بالبصمة")
                    .setNegativeButton("استخدام الرمز", getMainExecutor(),
                            (d, w) -> hint.setText("أدخل الرمز للدخول"))
                    .build()
                    .authenticate(cancel, getMainExecutor(),
                            new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                                @Override public void onAuthenticationSucceeded(
                                        android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                                    unlock();
                                }
                                @Override public void onAuthenticationError(int code, CharSequence message) {
                                    // الإلغاء ليس خطأً: الرمز يبقى متاحًا.
                                    if (hint != null) hint.setText("أدخل الرمز، أو أعد المحاولة بالبصمة");
                                }
                            });
        } catch (Exception e) {
            hint.setText("البصمة غير متاحة — أدخل الرمز");
        }
    }

    private void tryPin() {
        String pin = pinInput.getText().toString().trim();
        if (!db.lockPinSet()) { unlock(); return; }
        if (db.checkLockPin(pin)) { unlock(); return; }
        pinInput.setText("");
        pinInput.setError("الرمز غير صحيح");
        Toast.makeText(this, "الرمز غير صحيح", Toast.LENGTH_SHORT).show();
    }

    private void unlock() {
        Db.markUnlocked();
        if (cancel != null) try { cancel.cancel(); } catch (Exception ignored) {}
        finish();
    }

    /** زرّ الرجوع لا يتخطّى القفل: يخرج من التطبيق. */
    @Override public void onBackPressed() { finishAffinity(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (cancel != null) try { cancel.cancel(); } catch (Exception ignored) {}
    }

    private Button big(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(primary ? Color.WHITE : Util.NAVY);
        b.setStateListAnimator(null);
        b.setBackground(Util.round(primary ? Util.NAVY : Util.ACCENT_SOFT, dp(14)));
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        return b;
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
}
