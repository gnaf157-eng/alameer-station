package com.abognaf.carcounter.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.abognaf.carcounter.core.CrossingEvent;
import com.abognaf.carcounter.core.OverlayFrame;
import com.abognaf.carcounter.core.OverlayItem;

import java.util.ArrayList;
import java.util.List;

/**
 * طبقة الرسم فوق الفيديو: خط العبور القابل للسحب، مستطيلات السيارات المكتشفة،
 * وتنبيهات العبور (+1) مع تمييز الجهتين "داخل" و"خارج".
 */
public class OverlayView extends View {

    /** إشعار بتغيير خط العبور (يُطبَّق على محرّك العدّ). */
    public interface LineChangeListener {
        void onLineChanged(float x1, float y1, float x2, float y2);

        void onLineEditFinished();
    }

    private static final long FLASH_MS = 900L;

    private float frameW = 480f;
    private float frameH = 640f;
    private float scale = 1f;
    private float offX = 0f;
    private float offY = 0f;

    private float lx1 = 0.06f;
    private float ly1 = 0.55f;
    private float lx2 = 0.94f;
    private float ly2 = 0.55f;
    private boolean flipped = false;

    private boolean showBoxes = true;
    private boolean showStats = false;
    private boolean editMode = false;

    private String statsText = "";
    private OverlayFrame frame = OverlayFrame.empty();
    private final List<Flash> flashes = new ArrayList<>();
    private final List<Flash> deadFlashes = new ArrayList<>();

    @Nullable
    private LineChangeListener listener;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private int dragTarget;
    private float dragLastX;
    private float dragLastY;

    private static final class Flash {
        final float x;
        final float y;
        final boolean inside;
        final long timeMs;

        Flash(float x, float y, boolean inside, long timeMs) {
            this.x = x;
            this.y = y;
            this.inside = inside;
            this.timeMs = timeMs;
        }
    }

    public OverlayView(Context context) {
        this(context, null);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        strokePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void setLineChangeListener(@Nullable LineChangeListener value) {
        listener = value;
    }

    public void setFrameSize(float width, float height) {
        if (width > 1f && height > 1f) {
            frameW = width;
            frameH = height;
            invalidate();
        }
    }

    public void setLine(float x1, float y1, float x2, float y2) {
        lx1 = x1;
        ly1 = y1;
        lx2 = x2;
        ly2 = y2;
        invalidate();
    }

    public float lineX1() {
        return lx1;
    }

    public float lineY1() {
        return ly1;
    }

    public float lineX2() {
        return lx2;
    }

    public float lineY2() {
        return ly2;
    }

    public void setFlipped(boolean value) {
        flipped = value;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setShowBoxes(boolean value) {
        showBoxes = value;
        invalidate();
    }

    public void setShowStats(boolean value) {
        showStats = value;
        invalidate();
    }

    public void setEditMode(boolean value) {
        editMode = value;
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setStatsText(String text) {
        statsText = text == null ? "" : text;
        if (showStats) {
            invalidate();
        }
    }

    /** استقبال نتائج إطار جديد من خيط المعالجة. */
    public void submit(OverlayFrame newFrame) {
        if (newFrame == null) {
            return;
        }
        frame = newFrame;
        List<CrossingEvent> events = newFrame.events;
        for (int i = 0; i < events.size(); i++) {
            CrossingEvent event = events.get(i);
            flashes.add(new Flash(event.x, event.y, event.toInside, event.timeMs));
        }
        invalidate();
    }

    public void clearTracks() {
        frame = OverlayFrame.empty();
        flashes.clear();
        invalidate();
    }

    public void clearFlashes() {
        flashes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        computeTransform(viewWidth, viewHeight);
        drawInsideHalfPlane(canvas);
        if (showBoxes) {
            drawDetections(canvas);
        }
        drawCountLine(canvas);
        drawFlashes(canvas, System.currentTimeMillis());
        if (showStats) {
            drawStats(canvas);
        }
        if (editMode) {
            drawHandles(canvas);
        }
        // إبقاء الرسم متحركًا أثناء ظهور تنبيهات العبور
        if (!flashes.isEmpty()) {
            postInvalidateDelayed(40L);
        }
    }

    // ---------------------------------------------------------------- الرسم

    private void computeTransform(int viewWidth, int viewHeight) {
        // نفس منطق FILL_CENTER في PreviewView: ملء المساحة مع البقاء في المنتصف
        scale = Math.max(viewWidth / frameW, viewHeight / frameH);
        offX = (viewWidth - frameW * scale) * 0.5f;
        offY = (viewHeight - frameH * scale) * 0.5f;
    }

    private float tx(float frameX) {
        return frameX * scale + offX;
    }

    private float ty(float frameY) {
        return frameY * scale + offY;
    }

    private float invX(float viewX) {
        return (viewX - offX) / scale;
    }

    private float invY(float viewY) {
        return (viewY - offY) / scale;
    }

    private void drawInsideHalfPlane(Canvas canvas) {
        float x1 = tx(lx1 * frameW);
        float y1 = ty(ly1 * frameH);
        float x2 = tx(lx2 * frameW);
        float y2 = ty(ly2 * frameH);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) {
            return;
        }
        float ux = dx / len;
        float uy = dy / len;
        float nx = flipped ? uy : -uy;
        float ny = flipped ? -ux : ux;
        float big = Math.max(getWidth(), getHeight()) * 2f;
        path.reset();
        path.moveTo(x1 - ux * big, y1 - uy * big);
        path.lineTo(x2 + ux * big, y2 + uy * big);
        path.lineTo(x2 + ux * big + nx * big, y2 + uy * big + ny * big);
        path.lineTo(x1 - ux * big + nx * big, y1 - uy * big + ny * big);
        path.close();
        fillPaint.setColor(Color.argb(18, 46, 204, 113));
        canvas.drawPath(path, fillPaint);
    }

    private void drawCountLine(Canvas canvas) {
        float x1 = tx(lx1 * frameW);
        float y1 = ty(ly1 * frameH);
        float x2 = tx(lx2 * frameW);
        float y2 = ty(ly2 * frameH);

        // هالة خفيفة ثم الخط نفسه
        strokePaint.setColor(Color.argb(70, 0, 0, 0));
        strokePaint.setStrokeWidth(density * 8f);
        canvas.drawLine(x1, y1, x2, y2, strokePaint);
        strokePaint.setColor(editMode ? Color.parseColor("#FF00E5FF") : Color.WHITE);
        strokePaint.setStrokeWidth(density * (editMode ? 4f : 3f));
        canvas.drawLine(x1, y1, x2, y2, strokePaint);
        strokePaint.setStrokeWidth(density * 1.5f);
        strokePaint.setColor(Color.argb(90, 0, 0, 0));
        canvas.drawLine(x1, y1, x2, y2, strokePaint);

        // سهم يوضح جهة "داخل"
        float mx = (x1 + x2) * 0.5f;
        float my = (y1 + y2) * 0.5f;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) {
            return;
        }
        float nx = flipped ? dy / len : -dy / len;
        float ny = flipped ? -dx / len : dx / len;
        float arrowLen = density * 22f;
        float arrowW = density * 11f;
        float tipX = mx + nx * arrowLen;
        float tipY = my + ny * arrowLen;
        path.reset();
        path.moveTo(tipX, tipY);
        path.lineTo(mx + (-ny) * arrowW * 0.5f, my + (nx) * arrowW * 0.5f);
        path.lineTo(mx - (-ny) * arrowW * 0.5f, my - (nx) * arrowW * 0.5f);
        path.close();
        fillPaint.setColor(Color.parseColor("#FF2ECC71"));
        canvas.drawPath(path, fillPaint);

        // كلمتا "داخل" و"خارج" على جانبي الخط
        drawSideLabel(canvas, x1, y1, x2, y2, nx, ny, true);
        drawSideLabel(canvas, x1, y1, x2, y2, nx, ny, false);
    }

    private void drawSideLabel(Canvas canvas, float x1, float y1, float x2, float y2,
                               float nx, float ny, boolean insideSide) {
        float t = 0.22f;
        float labelX = x1 + (x2 - x1) * t;
        float labelY = y1 + (y2 - y1) * t;
        float sign = insideSide ? 1f : -1f;
        float distance = density * 30f;
        float px = labelX + nx * distance * sign;
        float py = labelY + ny * distance * sign;
        String text = insideSide ? "داخل" : "خارج";
        textPaint.setColor(insideSide ? Color.parseColor("#FF7BF1A8") : Color.parseColor("#FFFFC46B"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        float textWidth = textPaint.measureText(text);
        float textHeight = textPaint.getTextSize();
        rect.set(px - textWidth * 0.55f - density * 5f, py - textHeight * 0.85f - density * 2f,
                px + textWidth * 0.55f + density * 5f, py + textHeight * 0.35f + density * 2f);
        fillPaint.setColor(Color.argb(150, 0, 0, 0));
        canvas.drawRoundRect(rect, density * 6f, density * 6f, fillPaint);
        canvas.drawText(text, px, py, textPaint);
        textPaint.setFakeBoldText(false);
    }

    private void drawDetections(Canvas canvas) {
        List<OverlayItem> items = frame.items;
        for (int i = 0; i < items.size(); i++) {
            OverlayItem item = items.get(i);
            float left = tx(item.x1);
            float top = ty(item.y1);
            float right = tx(item.x2);
            float bottom = ty(item.y2);
            if (right < 0 || left > getWidth() || bottom < 0 || top > getHeight()) {
                continue;
            }
            int color;
            if (item.counted) {
                color = Color.parseColor("#FF4FC3F7");       // تم احتسابها
            } else if (item.stationary) {
                color = Color.parseColor("#FF9E9E9E");       // واقفة: لا تُحتسب
            } else if (!item.confirmed) {
                color = Color.parseColor("#FFFFB74D");       // لا تزال قيد التأكيد
            } else {
                color = Color.parseColor("#FF3DDC84");       // سيارة متحركة
            }
            strokePaint.setColor(color);
            strokePaint.setStrokeWidth(density * 2.5f);
            rect.set(left, top, right, bottom);
            canvas.drawRect(rect, strokePaint);

            // النقطة المرجعية المستخدمة في قياس العبور (أسفل منتصف المستطيل)
            float anchorX = tx((item.x1 + item.x2) * 0.5f);
            float anchorY = ty(item.y2);
            fillPaint.setColor(color);
            canvas.drawCircle(anchorX, anchorY, density * 2.5f, fillPaint);

            // شريحة الاسم مع درجة الثقة
            String text = "\u200F" + item.label + " " + Math.round(item.score * 100f) + "%";
            drawChip(canvas, text, left, top - density * 2f, Color.argb(200, 0, 0, 0), color);
        }
    }

    private void drawChip(Canvas canvas, String text, float x, float baselineY, int bgColor, int textColor) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(textColor);
        float textWidth = textPaint.measureText(text);
        float textHeight = textPaint.getTextSize();
        float pad = density * 4f;
        float y = Math.max(baselineY, textHeight + pad * 2f);
        rect.set(x, y - textHeight - pad * 2f, x + textWidth + pad * 2f, y);
        fillPaint.setColor(bgColor);
        canvas.drawRoundRect(rect, density * 4f, density * 4f, fillPaint);
        canvas.drawText(text, x + pad, y - pad - textHeight * 0.2f, textPaint);
    }

    private void drawFlashes(Canvas canvas, long nowMs) {
        deadFlashes.clear();
        fillPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < flashes.size(); i++) {
            Flash flash = flashes.get(i);
            long age = nowMs - flash.timeMs;
            if (age < 0L) {
                age = 0L;
            }
            if (age > FLASH_MS) {
                deadFlashes.add(flash);
                continue;
            }
            float progress = age / (float) FLASH_MS;
            int alpha = (int) (255 * (1f - progress));
            float px = tx(flash.x);
            float py = ty(flash.y) - progress * density * 40f;
            int color = flash.inside ? Color.parseColor("#FF2ECC71") : Color.parseColor("#FFFFA726");
            strokePaint.setColor(Color.argb(Math.max(0, alpha / 2), Color.red(color), Color.green(color), Color.blue(color)));
            strokePaint.setStrokeWidth(density * 3f);
            canvas.drawCircle(px, py, density * (14f + progress * 22f), strokePaint);
            textPaint.setColor(Color.argb(Math.max(0, alpha), Color.red(color), Color.green(color), Color.blue(color)));
            textPaint.setFakeBoldText(true);
            textPaint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(flash.inside ? "+1 داخل" : "+1 خارج", px, py + density * 6f, textPaint);
            textPaint.setFakeBoldText(false);
            textPaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        }
        if (!deadFlashes.isEmpty()) {
            flashes.removeAll(deadFlashes);
            deadFlashes.clear();
        }
    }

    private void drawStats(Canvas canvas) {
        if (statsText == null || statsText.isEmpty()) {
            return;
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.parseColor("#FFE0E0E0"));
        float textWidth = textPaint.measureText(statsText);
        float textHeight = textPaint.getTextSize();
        float pad = density * 5f;
        rect.set(density * 8f, density * 8f, density * 8f + textWidth + pad * 2f,
                density * 8f + textHeight + pad * 2f);
        fillPaint.setColor(Color.argb(150, 0, 0, 0));
        canvas.drawRoundRect(rect, density * 5f, density * 5f, fillPaint);
        canvas.drawText(statsText, density * 8f + pad, density * 8f + pad + textHeight * 0.85f, textPaint);
    }

    private void drawHandles(Canvas canvas) {
        float x1 = tx(lx1 * frameW);
        float y1 = ty(ly1 * frameH);
        float x2 = tx(lx2 * frameW);
        float y2 = ty(ly2 * frameH);
        drawHandle(canvas, x1, y1);
        drawHandle(canvas, x2, y2);
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        fillPaint.setColor(Color.argb(90, 0, 229, 255));
        canvas.drawCircle(x, y, density * 20f, fillPaint);
        fillPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, density * 9f, fillPaint);
        strokePaint.setColor(Color.parseColor("#FF00E5FF"));
        strokePaint.setStrokeWidth(density * 3f);
        canvas.drawCircle(x, y, density * 9f, strokePaint);
    }

    // ---------------------------------------------------------------- اللمس

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editMode) {
            return super.onTouchEvent(event);
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dragTarget = pickTarget(x, y);
                dragLastX = x;
                dragLastY = y;
                if (dragTarget != 0 && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return dragTarget != 0;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragTarget == 0) {
                    return false;
                }
                float dx = x - dragLastX;
                float dy = y - dragLastY;
                dragLastX = x;
                dragLastY = y;
                if (dragTarget == 3) {
                    translateLine(dx, dy);
                } else {
                    moveEndpoint(dragTarget, x, y);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragTarget == 0) {
                    return false;
                }
                dragTarget = 0;
                snapLine();
                notifyLineChanged(true);
                invalidate();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private int pickTarget(float x, float y) {
        float x1 = tx(lx1 * frameW);
        float y1 = ty(ly1 * frameH);
        float x2 = tx(lx2 * frameW);
        float y2 = ty(ly2 * frameH);
        float handleRadius = density * 34f;
        if (Math.hypot(x - x1, y - y1) <= handleRadius) {
            return 1;
        }
        if (Math.hypot(x - x2, y - y2) <= handleRadius) {
            return 2;
        }
        if (distanceToSegment(x, y, x1, y1, x2, y2) <= density * 26f) {
            return 3;
        }
        return 0;
    }

    private void moveEndpoint(int target, float viewX, float viewY) {
        float nx = clamp01(invX(viewX) / frameW);
        float ny = clamp01(invY(viewY) / frameH);
        if (target == 1) {
            lx1 = nx;
            ly1 = ny;
        } else {
            lx2 = nx;
            ly2 = ny;
        }
        notifyLineChanged(false);
    }

    private void translateLine(float dxPx, float dyPx) {
        float ndx = dxPx / (scale * frameW);
        float ndy = dyPx / (scale * frameH);
        float minX = Math.min(lx1, lx2);
        float maxX = Math.max(lx1, lx2);
        float minY = Math.min(ly1, ly2);
        float maxY = Math.max(ly1, ly2);
        ndx = Math.max(-minX, Math.min(ndx, 1f - maxX));
        ndy = Math.max(-minY, Math.min(ndy, 1f - maxY));
        lx1 += ndx;
        lx2 += ndx;
        ly1 += ndy;
        ly2 += ndy;
        notifyLineChanged(false);
    }

    /** تثبيت الخط أفقيًا أو عموديًا عندما يكون قريبًا من ذلك. */
    private void snapLine() {
        float dx = (lx2 - lx1) * frameW;
        float dy = (ly2 - ly1) * frameH;
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) {
            return;
        }
        double angle = Math.atan2(Math.abs(dy), Math.abs(dx));
        double snap = Math.toRadians(8.0);
        if (angle < snap) {
            float average = (ly1 + ly2) * 0.5f;
            ly1 = average;
            ly2 = average;
        } else if (angle > (Math.PI / 2.0 - snap)) {
            float average = (lx1 + lx2) * 0.5f;
            lx1 = average;
            lx2 = average;
        }
    }

    private void notifyLineChanged(boolean finished) {
        if (listener == null) {
            return;
        }
        listener.onLineChanged(lx1, ly1, lx2, ly2);
        if (finished) {
            listener.onLineEditFinished();
        }
    }

    private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-6f) {
            return (float) Math.hypot(px - x1, py - y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        return (float) Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
