package com.abognaf.carcounter.core;

/**
 * خطّ العبور القابل للتحريك.
 *
 * <p>يُخزَّن الخط بإحداثيات نسبية (0..1) حتى يبقى صحيحًا مع أي دقة فيديو،
 * ويُحسب البعد الاتجاهي (signed distance) بالبكسل. الإشارة الموجبة تعني
 * الجهة المعتبرة "داخل"، ويمكن عكس الاتجاهين بزر واحد.
 */
public final class CountingLine {

    public static final float DEFAULT_X1 = 0.06f;
    public static final float DEFAULT_Y1 = 0.55f;
    public static final float DEFAULT_X2 = 0.94f;
    public static final float DEFAULT_Y2 = 0.55f;

    private float nx1 = DEFAULT_X1;
    private float ny1 = DEFAULT_Y1;
    private float nx2 = DEFAULT_X2;
    private float ny2 = DEFAULT_Y2;
    private boolean flipped = false;

    public void reset() {
        nx1 = DEFAULT_X1;
        ny1 = DEFAULT_Y1;
        nx2 = DEFAULT_X2;
        ny2 = DEFAULT_Y2;
        flipped = false;
    }

    public float x1() {
        return nx1;
    }

    public float y1() {
        return ny1;
    }

    public float x2() {
        return nx2;
    }

    public float y2() {
        return ny2;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setFlipped(boolean value) {
        flipped = value;
    }

    public void flip() {
        flipped = !flipped;
    }

    public void setNormalized(float x1, float y1, float x2, float y2) {
        nx1 = clamp01(x1);
        ny1 = clamp01(y1);
        nx2 = clamp01(x2);
        ny2 = clamp01(y2);
    }

    /** إزاحة الخط كاملًا مع بقاء أطرافه داخل الصورة. */
    public void translateNormalized(float dx, float dy) {
        float minX = Math.min(nx1, nx2);
        float maxX = Math.max(nx1, nx2);
        float minY = Math.min(ny1, ny2);
        float maxY = Math.max(ny1, ny2);
        dx = Math.max(-minX, Math.min(dx, 1f - maxX));
        dy = Math.max(-minY, Math.min(dy, 1f - maxY));
        nx1 += dx;
        nx2 += dx;
        ny1 += dy;
        ny2 += dy;
    }

    public float px1(float frameW) {
        return nx1 * frameW;
    }

    public float py1(float frameH) {
        return ny1 * frameH;
    }

    public float px2(float frameW) {
        return nx2 * frameW;
    }

    public float py2(float frameH) {
        return ny2 * frameH;
    }

    public float lengthPx(float frameW, float frameH) {
        return (float) Math.hypot(px2(frameW) - px1(frameW), py2(frameH) - py1(frameH));
    }

    /**
     * البعد الاتجاهي لنقطة عن الخط بالبكسل.
     * القيمة الموجبة = الجهة "داخل" (بعد تطبيق عكس الاتجاه إن كان مطلوبًا).
     */
    public float signedDistance(float px, float py, float frameW, float frameH) {
        float ax = px1(frameW);
        float ay = py1(frameH);
        float bx = px2(frameW);
        float by = py2(frameH);
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            return 0f;
        }
        // المتجه العمودي على اتجاه الخط
        float ntx = -dy / len;
        float nty = dx / len;
        float d = (px - ax) * ntx + (py - ay) * nty;
        return flipped ? -d : d;
    }

    /** المتجه العمودي (باتجاه "داخل") بالبكسل — يُستخدم للرسم على الشاشة. */
    public void normal(float frameW, float frameH, float[] out) {
        float dx = px2(frameW) - px1(frameW);
        float dy = py2(frameH) - py1(frameH);
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f || out == null || out.length < 2) {
            return;
        }
        float ntx = -dy / len;
        float nty = dx / len;
        out[0] = flipped ? -ntx : ntx;
        out[1] = flipped ? -nty : nty;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }
}
