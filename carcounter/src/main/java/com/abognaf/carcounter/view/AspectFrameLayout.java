package com.abognaf.carcounter.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * حاوية للفيديو تحافظ على نسبة أبعاد الكاميرا (3:4 طوليًا افتراضيًا)،
 * ولا تتجاوز المساحة المتاحة على الشاشة. عندما تكون نسبة الأبعاد مطابقة
 * لتدفق الكاميرا تظهر صورة كاملة دون قصّ، فتكون إحداثيات الرسم على الفيديو
 * مطابقة تمامًا للإحداثيات التي يعمل عليها الكشف.
 */
public class AspectFrameLayout extends FrameLayout {

    /** نسبة الارتفاع إلى العرض (1.3333 = 3:4). */
    private float heightRatio = 1.3333f;

    public AspectFrameLayout(Context context) {
        super(context);
    }

    public AspectFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            for (int i = 0; i < attrs.getAttributeCount(); i++) {
                if ("heightRatio".equals(attrs.getAttributeName(i))) {
                    heightRatio = attrs.getAttributeFloatValue(i, heightRatio);
                }
            }
        }
    }

    public void setHeightRatio(float ratio) {
        if (ratio > 0.1f) {
            heightRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int desiredHeight = Math.round(width * heightRatio);
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            width = Math.round(height / heightRatio);
            desiredHeight = height;
        } else if (heightMode != MeasureSpec.UNSPECIFIED && height > 0) {
            desiredHeight = Math.min(desiredHeight, height);
        }
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY));
    }
}
