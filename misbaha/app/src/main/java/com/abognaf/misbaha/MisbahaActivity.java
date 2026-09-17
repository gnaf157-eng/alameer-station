package com.abognaf.misbaha;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.widget.TextView;

/**
 * المسبحة الإلكترونية — شاشة واحدة بسيطة:
 * - عنوان واحد أعلى الشاشة.
 * - الذكر الحالي بخط كبير في المنتصف.
 * - عداد كبير بشكل شاشة المسبحة الإلكترونية.
 * - زر دائري كبير للعد وزر صغير لإعادة التصفير.
 * - شريط ثابت أسفل الشاشة باسم المطور (لا يُغطى ولا يختفي).
 * - حفظ محلي للعدّاد في SharedPreferences: يستمر بعد إغلاق التطبيق،
 *   وبوحد زر التصفير يعيد العدد إلى صفر.
 */
public class MisbahaActivity extends Activity {

    private static final String PREFS_NAME = "misbaha_prefs";
    private static final String KEY_COUNT = "count";
    private static final String KEY_DHIKR_INDEX = "dhikr_index";

    /** الأذكار بالترتيب المطلوب؛ بعد الأخير يعود الأول. */
    private static final String[] DHIKR = {
            "أستغفر الله",
            "سبحان الله",
            "الحمد لله",
            "والله أكبر"
    };

    private TextView dhikrText;
    private TextView counterDisplay;
    private TextView developerBar;
    private View tasbihButton;
    private View resetButton;

    private SharedPreferences prefs;
    private Vibrator vibrator;

    private int count;
    private int dhikrIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dhikrText = (TextView) findViewById(R.id.dhikr_text);
        counterDisplay = (TextView) findViewById(R.id.counter_display);
        developerBar = (TextView) findViewById(R.id.developer_bar);
        tasbihButton = findViewById(R.id.tasbih_button);
        resetButton = findViewById(R.id.reset_button);

        // استرجاع الحالة المحفوظة محليًا
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        count = prefs.getInt(KEY_COUNT, 0);
        dhikrIndex = prefs.getInt(KEY_DHIKR_INDEX, 0);
        if (dhikrIndex < 0 || dhikrIndex >= DHIKR.length) {
            dhikrIndex = 0;
        }

        vibrator = getVibrator();

        // زر التسبيح: يزيد العداد بمقدار 1 وينقل الذكر إلى التالي (بدون تصفير العدد)
        tasbihButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTasbihPressed();
            }
        });

        // زر التصفير: الوحيد الذي يعيد العدد إلى صفر
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onResetPressed();
            }
        });

        // شريط المطور: يفتح نافذة "حول التطبيق"
        developerBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAboutDialog();
            }
        });

        updateUi();
    }

    private void onTasbihPressed() {
        count = count + 1;
        dhikrIndex = (dhikrIndex + 1) % DHIKR.length;
        save();
        updateUi();
        vibrate();
    }

    private void onResetPressed() {
        count = 0;
        dhikrIndex = 0;
        save();
        updateUi();
    }

    private void save() {
        prefs.edit()
                .putInt(KEY_COUNT, count)
                .putInt(KEY_DHIKR_INDEX, dhikrIndex)
                .apply();
    }

    private void updateUi() {
        dhikrText.setText(DHIKR[dhikrIndex]);
        counterDisplay.setText(String.valueOf(count));
    }

    /** اهتزاز خفيف قصير جدًا عند كل ضغطة. */
    private void vibrate() {
        if (vibrator == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager =
                    (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void showAboutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setView(dialogView)
                .setPositiveButton(R.string.about_close, null)
                .show();
    }
}
