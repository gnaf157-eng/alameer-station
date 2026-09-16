package com.abognaf.carcounter;

import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * متتبِّع خفيف يعتمد على تطابق الصناديق (IoU + المسافة) بين الإطارات المتتالية.
 * - كل سيارة تأخذ معرّفًا واحدًا طوال ظهورها، فلا تُحسب أكثر من مرة.
 * - العد يتم فقط عند عبور مركز السيارة لخط العد، ومرة واحدة لكل معرّف.
 * - السيارة المتوقفة لا تعبر الخط، وبالتالي لا تُحسب ولا يتكرر عدها.
 */
public class VehicleTracker {

    public interface Listener {
        /** يُستدعى عند عبور سيارة للخط. inDirection=true تعني "داخل" */
        void onCrossed(TrackedVehicle v, boolean inDirection);
    }

    private static final int MAX_MISSED = 12;      // إطارات الغياب قبل حذف المسار
    private static final int MIN_HITS_TO_COUNT = 2; // تأكيدات قبل السماح بالعد
    private static final float MIN_MOVE_RATIO = 0.15f; // حد الحركة الدنيا نسبةً لحجم الصندوق

    private final List<TrackedVehicle> tracks = new ArrayList<>();
    private int nextId = 1;
    private final Listener listener;

    // خط العد بإحداثيات الصورة (نسبية 0..1)
    private PointF lineA = new PointF(0f, 0.5f);
    private PointF lineB = new PointF(1f, 0.5f);
    private boolean swapDirection = false;

    public VehicleTracker(Listener l) { listener = l; }

    public synchronized void setLine(PointF a, PointF b) {
        lineA = a; lineB = b;
        // إعادة تهيئة الجهة الحالية لكل مسار حتى لا يُحتسب عبور وهمي عند تحريك الخط
        for (TrackedVehicle t : tracks) t.lastSide = side(t.cx, t.cy);
    }

    public synchronized void setSwapDirection(boolean s) { swapDirection = s; }
    public boolean isSwapDirection() { return swapDirection; }

    public synchronized List<TrackedVehicle> getTracks() { return new ArrayList<>(tracks); }

    public synchronized void reset() {
        tracks.clear();
        nextId = 1;
    }

    /**
     * @param detections صناديق بإحداثيات نسبية (0..1) للإطار المستقيم
     */
    public synchronized void update(List<RectF> detections) {
        boolean[] used = new boolean[detections.size()];

        // 1) مطابقة المسارات الحالية مع الاكتشافات الجديدة (أفضل تطابق أولاً)
        for (TrackedVehicle t : tracks) {
            int best = -1;
            float bestScore = 0f;
            for (int i = 0; i < detections.size(); i++) {
                if (used[i]) continue;
                float s = matchScore(t, detections.get(i));
                if (s > bestScore) { bestScore = s; best = i; }
            }
            if (best >= 0 && bestScore > 0.2f) {
                used[best] = true;
                t.update(detections.get(best));
                t.missedFrames = 0;
                t.hits++;
                checkCrossing(t);
            } else {
                t.missedFrames++;
            }
        }

        // 2) إنشاء مسارات جديدة للاكتشافات غير المطابقة
        for (int i = 0; i < detections.size(); i++) {
            if (!used[i]) {
                TrackedVehicle t = new TrackedVehicle(nextId++, detections.get(i));
                t.lastSide = side(t.cx, t.cy);
                tracks.add(t);
            }
        }

        // 3) حذف المسارات المفقودة
        Iterator<TrackedVehicle> it = tracks.iterator();
        while (it.hasNext()) {
            if (it.next().missedFrames > MAX_MISSED) it.remove();
        }
    }

    private float matchScore(TrackedVehicle t, RectF d) {
        float iou = iou(t.box, d);
        if (iou > 0f) return iou + 0.5f;
        // إن لم تتقاطع الصناديق، اعتمد على قرب المركز نسبةً لحجم الصندوق
        float dx = t.cx - d.centerX(), dy = t.cy - d.centerY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float size = Math.max(t.box.width(), t.box.height());
        if (size <= 0) return 0f;
        float ratio = dist / size;
        return ratio < 1.0f ? (1.0f - ratio) * 0.5f : 0f;
    }

    private static float iou(RectF a, RectF b) {
        float l = Math.max(a.left, b.left), t = Math.max(a.top, b.top);
        float r = Math.min(a.right, b.right), bt = Math.min(a.bottom, b.bottom);
        if (r <= l || bt <= t) return 0f;
        float inter = (r - l) * (bt - t);
        float union = a.width() * a.height() + b.width() * b.height() - inter;
        return union <= 0 ? 0f : inter / union;
    }

    /** جهة النقطة بالنسبة للخط: موجب/سالب/صفر */
    private int side(float x, float y) {
        float cross = (lineB.x - lineA.x) * (y - lineA.y) - (lineB.y - lineA.y) * (x - lineA.x);
        return cross > 0 ? 1 : (cross < 0 ? -1 : 0);
    }

    private void checkCrossing(TrackedVehicle t) {
        int now = side(t.cx, t.cy);
        if (now == 0) return;
        if (t.lastSide == 0) { t.lastSide = now; return; }
        if (now != t.lastSide) {
            // تحقّق أن السيارة تحركت فعلاً (ليست اهتزاز صندوق لسيارة واقفة)
            float moved = (float) Math.hypot(t.cx - t.firstCx, t.cy - t.firstCy);
            float size = Math.max(t.box.width(), t.box.height());
            boolean reallyMoved = moved > size * MIN_MOVE_RATIO;
            if (!t.counted && t.hits >= MIN_HITS_TO_COUNT && reallyMoved) {
                t.counted = true;
                boolean in = (now > 0) != swapDirection;
                if (listener != null) listener.onCrossed(t, in);
            }
            t.lastSide = now;
        }
    }
}
