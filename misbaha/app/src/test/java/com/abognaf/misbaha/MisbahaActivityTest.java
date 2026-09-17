package com.abognaf.misbaha;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * اختبارات حقيقية (Robolectric) تحقّق من:
 * 1) ظهور الشريط الثابت "أبوقناف للأتمتة • 777808020" دائمًا أسفل الشاشة.
 * 2) عدم تكرار العنوان.
 * 3) منطق العدّ: +1 والانتقال بين الأذكار دون تصفير، والعودة من "والله أكبر" إلى "أستغفر الله".
 * 4) الحفظ المحلي واستمرار العد بعد إعادة الفتح، والتصفير بزر التصفير فقط.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MisbahaActivityTest {

    private static final String BAR_TEXT = "أبوقناف للأتمتة • 777808020";

    private Activity launch() {
        return Robolectric.buildActivity(MisbahaActivity.class).setup().get();
    }

    @Test
    public void bottomBarIsVisibleWithExactTextAndPinnedToBottom() {
        Activity activity = launch();

        View bar = activity.findViewById(R.id.developer_bar);
        assertNotNull("شريط المطور موجود في التخطيط", bar);
        assertEquals("شريط المطور ظاهر دائمًا (VISIBLE)", View.VISIBLE, bar.getVisibility());
        assertEquals("نص الشريط مطابق حرفيًا", BAR_TEXT, bar.getText().toString());

        // الشريط يجب أن يكون العنصر الأخير في جذر التخطيط (مؤشّرًا إلى أسفل الشاشة)
        ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content).getChildAt(0);
        assertSame("الشريط مثبت أسفل الشاشة (آخر عنصر في التخطيط)", bar,
                root.getChildAt(root.getChildCount() - 1));
    }

    @Test
    public void titleAppearsOnceAtTop() {
        Activity activity = launch();

        TextView title = (TextView) activity.findViewById(R.id.app_title);
        assertNotNull("عنوان التطبيق موجود", title);
        assertEquals("المسبحة الإلكترونية", title.getText().toString());

        ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content).getChildAt(0);
        // العنوان هو أول عنصر في التخطيط (أعلى الشاشة) ولا يوجد عنوان آخر.
        assertSame("العنوان أعلى الشاشة", title, root.getChildAt(0));
    }

    @Test
    public void tapIncrementsAndRotatesDhikrWithoutResetting() {
        Activity activity = launch();
        View button = activity.findViewById(R.id.tasbih_button);
        TextView dhikr = (TextView) activity.findViewById(R.id.dhikr_text);
        TextView counter = (TextView) activity.findViewById(R.id.counter_display);

        assertEquals("أستغفر الله", dhikr.getText().toString());
        assertEquals("0", counter.getText().toString());

        button.performClick();
        assertEquals("العداد يزيد بمقدار 1", "1", counter.getText().toString());
        assertEquals("ينتقل إلى الذكر الثاني", "سبحان الله", dhikr.getText().toString());

        button.performClick();
        button.performClick();
        assertEquals("3", counter.getText().toString());
        assertEquals("والله أكبر", dhikr.getText().toString());

        // بعد «والله أكبر» يعود إلى «أستغفر الله» ويبقى العدد
        button.performClick();
        assertEquals("العدد لم يُصفَّر عند الانتقال", "4", counter.getText().toString());
        assertEquals("عاد إلى الذكر الأول", "أستغفر الله", dhikr.getText().toString());
    }

    @Test
    public void countSurvivesRestartAndOnlyResetClearsIt() {
        Activity first = launch();
        View button = first.findViewById(R.id.tasbih_button);
        for (int i = 0; i < 7; i++) {
            button.performClick();
        }
        first.destroy();

        // إعادة فتح التطبيق: العدد مستمر من نفس القيمة
        Activity second = Robolectric.buildActivity(MisbahaActivity.class).setup().get();
        TextView counter = (TextView) second.findViewById(R.id.counter_display);
        assertEquals("العدد محفوظ محليًا ويستمر", "7", counter.getText().toString());

        second.findViewById(R.id.reset_button).performClick();
        assertEquals("زر التصفير يعيد العدد إلى صفر", "0", counter.getText().toString());
        second.destroy();

        Activity third = Robolectric.buildActivity(MisbahaActivity.class).setup().get();
        assertEquals("التصفير محفوظ بشكل دائم", "0",
                ((TextView) third.findViewById(R.id.counter_display)).getText().toString());
    }

    @Test
    public void tappingBottomBarShowsAboutDialogContent() {
        Activity activity = launch();

        // الضغط على الشريط يفتح نافذة "حول التطبيق" (لا يجب أن يسبب خطأ)
        activity.findViewById(R.id.developer_bar).performClick();

        // محتوى نافذة "حول التطبيق"
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_about, null);
        assertTrue("النافذة تعرض اسم التطبيق", containsText(dialogView, "المسبحة الإلكترونية"));
        assertTrue("النافذة تعرض 'تطوير:'", containsText(dialogView, "تطوير:"));
        assertTrue("النافذة تعرض المطوّر", containsText(dialogView, "أبوقناف للأتمتة"));
        assertTrue("النافذة تعرض 'للتواصل:'", containsText(dialogView, "للتواصل:"));
        assertTrue("النافذة تعرض رقم التواصل", containsText(dialogView, "777808020"));
    }

    private static boolean containsText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) {
            return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), text)) {
                    return true;
                }
            }
        }
        return false;
    }
}
