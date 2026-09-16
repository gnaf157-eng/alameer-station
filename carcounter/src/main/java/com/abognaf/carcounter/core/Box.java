package com.abognaf.carcounter.core;

/**
 * مستطيل إحاطة (Bounding box) بإحداثيات البكسل داخل إطار الصورة
 * بعد تدويرها إلى الوضع الرأسي المطابق للعرض على الشاشة.
 *
 * <p>هذا الصنف لا يعتمد على أي واجهة أندرويد، لذا يمكن اختبار منطق العدّ
 * بالكامل على الحاسوب (unit tests) دون هاتف.
 */
public final class Box {

    public float x1;
    public float y1;
    public float x2;
    public float y2;

    public Box() {
    }

    public Box(float x1, float y1, float x2, float y2) {
        set(x1, y1, x2, y2);
    }

    /** يرتّب الإحداثيات ويضمن أن x1&lt;=x2 و y1&lt;=y2. */
    public void set(float x1, float y1, float x2, float y2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
    }

    public void set(Box other) {
        set(other.x1, other.y1, other.x2, other.y2);
    }

    public void translate(float dx, float dy) {
        x1 += dx;
        x2 += dx;
        y1 += dy;
        y2 += dy;
    }

    public float width() {
        return x2 - x1;
    }

    public float height() {
        return y2 - y1;
    }

    public float area() {
        float w = width();
        float h = height();
        return (w > 0f && h > 0f) ? w * h : 0f;
    }

    public float centerX() {
        return (x1 + x2) * 0.5f;
    }

    public float centerY() {
        return (y1 + y2) * 0.5f;
    }

    /** أسفل المستطيل: نقطة تلامس السيارة مع الطريق (أفضل نقطة لقياس عبور الخط). */
    public float bottomY() {
        return y2;
    }

    public float diagonal() {
        return (float) Math.hypot(width(), height());
    }

    /** نسبة التراكب بين مستطيلين (Intersection over Union). */
    public float iou(Box other) {
        float ix1 = Math.max(x1, other.x1);
        float iy1 = Math.max(y1, other.y1);
        float ix2 = Math.min(x2, other.x2);
        float iy2 = Math.min(y2, other.y2);
        float iw = ix2 - ix1;
        float ih = iy2 - iy1;
        if (iw <= 0f || ih <= 0f) {
            return 0f;
        }
        float intersection = iw * ih;
        float union = area() + other.area() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    @Override
    public String toString() {
        return "Box[" + x1 + "," + y1 + " -> " + x2 + "," + y2 + "]";
    }
}
