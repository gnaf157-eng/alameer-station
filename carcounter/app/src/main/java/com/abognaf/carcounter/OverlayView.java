package com.abognaf.carcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * طبقة فوق الكاميرا: ترسم خط العد القابل للسحب وصناديق السيارات المتتبَّعة.
 * جميع الإحداثيات المخزنة نسبية (0..1) حتى تتطابق مع إحداثيات الصورة.
 */
public class OverlayView extends View {

    public interface LineListener { void onLineChanged(PointF a, PointF b); }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final PointF a = new PointF(0.1f, 0.55f);
    private final PointF b = new PointF(0.9f, 0.55f);
    private volatile List<TrackedVehicle> tracks = new ArrayList<>();
    private LineListener listener;
    private boolean swapped = false;
    /** نسبة عرض/ارتفاع صورة التحليل المستقيمة؛ تُستخدم لمطابقة الإحداثيات مع FILL_CENTER */
    private float imageAspect = 3f / 4f;
    private float scaleX = 1f, scaleY = 1f, offX = 0f, offY = 0f;

    // حالة السحب: 0 لا شيء، 1 طرف A، 2 طرف B، 3 الخط كاملًا
    private int drag = 0;
    private float lastX, lastY;

    public OverlayView(Context c, AttributeSet at) {
        super(c, at);
        linePaint.setColor(0xFFFACC15);
        linePaint.setStrokeWidth(6f);
        handlePaint.setColor(0xFFFACC15);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setColor(0xFF22C55E);
        countedPaint.setStyle(Paint.Style.STROKE);
        countedPaint.setStrokeWidth(4f);
        countedPaint.setColor(0xFF94A3B8);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setFakeBoldText(true);
        labelPaint.setColor(0xFFFACC15);
        labelPaint.setTextSize(34f);
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(4f, 0, 0, Color.BLACK);
    }

    public void setLineListener(LineListener l) { listener = l; }

    /** الخط بإحداثيات الصورة النسبية (لتمريره إلى المتتبِّع) */
    public PointF getA() { return viewToImage(a); }
    public PointF getB() { return viewToImage(b); }

    public void setImageAspect(float aspect) {
        if (aspect > 0 && Math.abs(aspect - imageAspect) > 0.001f) {
            post(() -> {
                imageAspect = aspect;
                computeMapping();
                if (listener != null) listener.onLineChanged(getA(), getB());
            });
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeMapping();
        if (listener != null) listener.onLineChanged(getA(), getB());
    }

    /** حساب تحويل الصورة إلى الشاشة بنفس منطق PreviewView.FILL_CENTER (تكبير مع قص) */
    private void computeMapping() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float viewAspect = w / h;
        if (viewAspect > imageAspect) {
            // الشاشة أعرض: الصورة تملأ العرض ويُقص من الأعلى/الأسفل
            scaleX = 1f;
            scaleY = (w / imageAspect) / h;
            offX = 0f;
            offY = (1f - scaleY) / 2f;
        } else {
            scaleY = 1f;
            scaleX = (h * imageAspect) / w;
            offY = 0f;
            offX = (1f - scaleX) / 2f;
        }
    }

    private PointF viewToImage(PointF v) {
        return new PointF((v.x - offX) / scaleX, (v.y - offY) / scaleY);
    }

    private float imgX(float x) { return (x * scaleX + offX) * getWidth(); }
    private float imgY(float y) { return (y * scaleY + offY) * getHeight(); }
    public void setSwapped(boolean s) { swapped = s; invalidate(); }

    public void setTracks(List<TrackedVehicle> t) {
        tracks = t;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();

        // صناديق السيارات
        for (TrackedVehicle t : tracks) {
            RectF r = new RectF(imgX(t.box.left), imgY(t.box.top), imgX(t.box.right), imgY(t.box.bottom));
            c.drawRoundRect(r, 8, 8, t.counted ? countedPaint : boxPaint);
            c.drawText("#" + t.id, r.left + 6, r.top - 8, textPaint);
        }

        // خط العد
        float ax = a.x * w, ay = a.y * h, bx = b.x * w, by = b.y * h;
        c.drawLine(ax, ay, bx, by, linePaint);
        c.drawCircle(ax, ay, 22f, handlePaint);
        c.drawCircle(bx, by, 22f, handlePaint);

        // تسمية الاتجاهين حول منتصف الخط (عمودي على الخط)
        float mx = (ax + bx) / 2f, my = (ay + by) / 2f;
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.hypot(dx, dy);
        if (len > 1) {
            float nx = -dy / len, ny = dx / len; // العمودي (جهة الـ cross الموجبة)
            String posLabel = swapped ? "خارج ↓" : "داخل ↓";
            String negLabel = swapped ? "داخل ↑" : "خارج ↑";
            float off = 60f;
            c.drawText(posLabel, mx + nx * off - 40, my + ny * off + 12, labelPaint);
            c.drawText(negLabel, mx - nx * off - 40, my - ny * off + 12, labelPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float grab = 70f;
                if (Math.hypot(x - a.x * w, y - a.y * h) < grab) drag = 1;
                else if (Math.hypot(x - b.x * w, y - b.y * h) < grab) drag = 2;
                else if (distToSegment(x, y, a.x * w, a.y * h, b.x * w, b.y * h) < 60f) drag = 3;
                else drag = 0;
                lastX = x; lastY = y;
                if (drag != 0) getParent().requestDisallowInterceptTouchEvent(true);
                return drag != 0;
            case MotionEvent.ACTION_MOVE:
                if (drag == 0) return false;
                float ddx = (x - lastX) / w, ddy = (y - lastY) / h;
                if (drag == 1) { a.x = clamp(x / w); a.y = clamp(y / h); }
                else if (drag == 2) { b.x = clamp(x / w); b.y = clamp(y / h); }
                else {
                    a.x = clamp(a.x + ddx); a.y = clamp(a.y + ddy);
                    b.x = clamp(b.x + ddx); b.y = clamp(b.y + ddy);
                }
                lastX = x; lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag != 0 && listener != null) listener.onLineChanged(getA(), getB());
                drag = 0;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float distToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        if (l2 == 0) return (float) Math.hypot(px - x1, py - y1);
        float t = ((px - x1) * dx + (py - y1) * dy) / l2;
        t = Math.max(0, Math.min(1, t));
        return (float) Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }
}
