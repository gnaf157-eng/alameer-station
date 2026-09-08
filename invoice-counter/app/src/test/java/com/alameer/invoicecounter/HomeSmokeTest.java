package com.alameer.invoicecounter;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * اختبار دخان: يجب أن تفتح الشاشة الرئيسية دون خطأ وتعرض عنوانها.
 * لو حدث أي استثناء أثناء التشغيل سينفشل البناء هنا قبل وصول التطبيق للمستخدم.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HomeSmokeTest {
    @Test
    public void homeActivityLaunchesAndShowsTitle() {
        ActivityController<HomeActivity> controller = Robolectric.buildActivity(HomeActivity.class).setup();
        controller.performLayout();
        Activity a = controller.get();
        View decor = a.getWindow().getDecorView();
        assertNotNull(decor);
        TextView title = findText(decor, "سجل الفواتير");
        assertNotNull("العنوان يجب أن يكون موجودًا على الشاشة", title);
        assertTrue("العنوان يجب أن يكون مرئيًا", title.getVisibility() == View.VISIBLE);
        assertTrue("العنوان يجب أن له عرضًا بعد التخطيط", title.getWidth() > 0);
    }

    private static TextView findText(View v, String s) {
        if (v instanceof TextView && s.equals(((TextView) v).getText().toString())) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = findText(g.getChildAt(i), s);
                if (t != null) return t;
            }
        }
        return null;
    }
}
