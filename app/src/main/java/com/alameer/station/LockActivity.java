package com.alameer.station.shifts;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/**
 * قفل التطبيق برمز. لا يُفتح شيء من التطبيق قبل تجاوز هذه الشاشة.
 */
public class LockActivity extends Activity {
    private Db db;
    private EditText pinInput;

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

        TextView hint = text("أدخل رمز الدخول", 14, 0xff7c8186, false);
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

        Button enter = big("دخول");
        enter.setOnClickListener(v -> tryPin());
        shell.addView(enter, new LinearLayout.LayoutParams(-1, -2));

        setContentView(shell);
        pinInput.setOnEditorActionListener((v, id, e) -> { tryPin(); return true; });
    }

    private void tryPin() {
        String pin = pinInput.getText().toString().trim();
        if (!db.lockPinSet() || db.checkLockPin(pin)) {
            Db.markUnlocked();
            finish();
            return;
        }
        pinInput.setText("");
        pinInput.setError("الرمز غير صحيح");
        Toast.makeText(this, "الرمز غير صحيح", Toast.LENGTH_SHORT).show();
    }

    /** زرّ الرجوع لا يتخطّى القفل: يخرج من التطبيق. */
    @Override public void onBackPressed() { finishAffinity(); }

    private Button big(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setBackground(Util.round(Util.NAVY, dp(14)));
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
