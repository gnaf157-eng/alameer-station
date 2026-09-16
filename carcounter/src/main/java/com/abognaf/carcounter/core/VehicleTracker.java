package com.abognaf.carcounter.core;

import java.util.ArrayList;
import java.util.List;

/**
 * متتبّع خفيف للسيارات (بدون إنترنت وبأقل استهلاك ممكن).
 *
 * <p>الفكرة: مطابقة كشوف الإطار الحالي مع آثار الإطار السابق باستخدام
 * التراكب (IoU) والمسافة بين النقاط المرجعية، مع توقّع حركة بسيط
 * (سرعة مُنعَّمة). كل كشف جديد غير مطابق يُنشئ أثرًا جديدًا بمعرّف جديد،
 * والأثر يبقى حيًّا لعدة إطارات حتى لو فُقد مؤقتًا، فلا تنقسم السيارة
 * الواحدة إلى عدّة معرّفات.
 */
public final class VehicleTracker {

    /** الحد الأدنى للتراكب لقبول المطابقة. */
    public static final float IOU_GATE = 0.10f;
    /** زمن بقاء الأثر بدون كشف قبل حذفه (ملي ثانية). */
    public static final long MAX_MISS_MS = 700L;
    /** مهلة الأثر غير المؤكد (كشف عابر) قبل حذفه. */
    public static final long TENTATIVE_TTL_MS = 900L;
    /** عدد الإطارات المطلوب لتأكيد الأثر. */
    public static final int HITS_TO_CONFIRM = 2;
    /** تراكب يُعتبر تكرارًا لنفس الكشف. */
    public static final float DEDUPE_IOU = 0.65f;
    /** أصغر مساحة مقبولة للمستطيل نسبةً لمساحة الإطار. */
    public static final float MIN_AREA_RATIO = 0.0004f;
    /** أكبر مساحة مقبولة للمستطيل نسبةً لمساحة الإطار. */
    public static final float MAX_AREA_RATIO = 0.85f;

    private final List<Track> tracks = new ArrayList<>();
    private final List<DetectedBox> accepted = new ArrayList<>();
    private float frameW;
    private float frameH;
    private long lastMs = -1L;
    private int nextId = 1;

    public VehicleTracker(float frameW, float frameH) {
        this.frameW = Math.max(1f, frameW);
        this.frameH = Math.max(1f, frameH);
    }

    public List<Track> tracks() {
        return tracks;
    }

    public int nextId() {
        return nextId;
    }

    /** تغيير دقة الفيديو يلغي الآثار القديمة لأن إحداثياتها لم تعد صحيحة. */
    public void setFrameSize(float w, float h) {
        if (w < 2f || h < 2f) {
            return;
        }
        if (Math.abs(w - frameW) > 0.5f || Math.abs(h - frameH) > 0.5f) {
            frameW = w;
            frameH = h;
            reset();
        }
    }

    public void reset() {
        tracks.clear();
        accepted.clear();
        lastMs = -1L;
        nextId = 1;
    }

    /** خطوة توقّع فقط (تُستخدم عند تخطي الكشف في بعض الإطارات). */
    public void predict(long nowMs) {
        advance(nowMs);
        prune(nowMs);
    }

    /** الخطوة الكاملة: تحديث الآثار بالكشوف الجديدة ثم إنشاء/حذف آثار. */
    public void update(List<DetectedBox> detections, long nowMs) {
        float dt = advance(nowMs);
        filterDetections(detections);

        final int trackCount = tracks.size();
        final int detCount = accepted.size();
        boolean[] trackUsed = new boolean[trackCount];
        boolean[] detUsed = new boolean[detCount];

        if (trackCount > 0 && detCount > 0) {
            List<float[]> pairs = new ArrayList<>(trackCount * detCount);
            for (int i = 0; i < trackCount; i++) {
                Track track = tracks.get(i);
                for (int j = 0; j < detCount; j++) {
                    DetectedBox det = accepted.get(j);
                    float iou = track.box.iou(det.box);
                    float dist = (float) Math.hypot(track.anchorX - det.box.centerX(),
                            track.anchorY - det.box.bottomY());
                    float reach = Math.max(track.box.diagonal(), det.box.diagonal()) * 0.75f + 12f;
                    if (iou < IOU_GATE && dist > reach) {
                        continue;
                    }
                    float cost = (1f - iou) + (dist / (frameH * 0.5f)) * 0.25f;
                    if (!track.label.isEmpty() && track.label.equalsIgnoreCase(det.label)) {
                        cost -= 0.08f;
                    }
                    pairs.add(new float[]{i, j, cost});
                }
            }
            pairs.sort((a, b) -> Float.compare(a[2], b[2]));
            for (int k = 0; k < pairs.size(); k++) {
                float[] pair = pairs.get(k);
                int i = (int) pair[0];
                int j = (int) pair[1];
                if (trackUsed[i] || detUsed[j]) {
                    continue;
                }
                trackUsed[i] = true;
                detUsed[j] = true;
                applyMatch(tracks.get(i), accepted.get(j), dt, nowMs);
            }
        }

        for (int j = 0; j < detCount; j++) {
            if (!detUsed[j]) {
                spawn(accepted.get(j), nowMs);
            }
        }

        prune(nowMs);
    }

    private float advance(long nowMs) {
        if (lastMs < 0L) {
            lastMs = nowMs;
            for (int i = 0; i < tracks.size(); i++) {
                tracks.get(i).fresh = false;
            }
            return 0f;
        }
        if (nowMs < lastMs) {
            lastMs = nowMs;
        }
        float dt = (nowMs - lastMs) / 1000f;
        if (dt > 0.5f) {
            dt = 0.5f;
        }
        lastMs = nowMs;
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            track.fresh = false;
            if (dt > 0f) {
                track.anchorX += track.vx * dt;
                track.anchorY += track.vy * dt;
                track.box.translate(track.vx * dt, track.vy * dt);
            }
            track.misses++;
        }
        return dt;
    }

    /** ترشيح الكشوف: حذف المكرر والكشوف غير المنطقية (ضجيج). */
    private void filterDetections(List<DetectedBox> input) {
        accepted.clear();
        if (input == null || input.isEmpty()) {
            return;
        }
        float frameArea = frameW * frameH;
        float minArea = MIN_AREA_RATIO * frameArea;
        float maxArea = MAX_AREA_RATIO * frameArea;
        for (int i = 0; i < input.size(); i++) {
            DetectedBox det = input.get(i);
            if (det == null) {
                continue;
            }
            float area = det.box.area();
            if (area < minArea || area > maxArea) {
                continue;
            }
            if (det.box.height() > frameH * 0.98f) {
                continue;
            }
            boolean replaced = false;
            for (int j = 0; j < accepted.size(); j++) {
                DetectedBox kept = accepted.get(j);
                if (kept.box.iou(det.box) > DEDUPE_IOU) {
                    // نفس السيارة مكتشفة مرتين: نُبقي الأعلى ثقة
                    if (det.score > kept.score) {
                        accepted.set(j, det);
                    }
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                accepted.add(det);
            }
        }
    }

    private void applyMatch(Track track, DetectedBox det, float dt, long nowMs) {
        float newAnchorX = det.box.centerX();
        float newAnchorY = det.box.bottomY();
        if (track.hits == 0) {
            track.anchorX = newAnchorX;
            track.anchorY = newAnchorY;
        } else {
            float oldX = track.anchorX;
            float oldY = track.anchorY;
            float k = 0.5f;
            track.anchorX += k * (newAnchorX - oldX);
            track.anchorY += k * (newAnchorY - oldY);
            if (dt > 0.001f) {
                float instVx = (track.anchorX - oldX) / dt;
                float instVy = (track.anchorY - oldY) / dt;
                float kv = 0.5f;
                track.vx += kv * (instVx - track.vx);
                track.vy += kv * (instVy - track.vy);
            }
        }
        track.box.set(det.box);
        track.label = det.label;
        track.score = det.score;
        track.hits++;
        track.misses = 0;
        track.lastSeenMs = nowMs;
        track.fresh = true;
        if (!track.confirmed && track.hits >= HITS_TO_CONFIRM) {
            track.confirmed = true;
        }
        track.stationary = track.speed() < MOVING_SPEED_RATIO_DEFAULT * frameH;
    }

    /** نسبة سرعة الحركة الدنيا (تُستخدم أيضًا في منطق العدّ). */
    private static final float MOVING_SPEED_RATIO_DEFAULT = 0.05f;

    private void spawn(DetectedBox det, long nowMs) {
        Track track = new Track(nextId++, nowMs);
        track.box.set(det.box);
        track.label = det.label;
        track.score = det.score;
        track.anchorX = det.box.centerX();
        track.anchorY = det.box.bottomY();
        track.bornX = track.anchorX;
        track.bornY = track.anchorY;
        track.hits = 1;
        track.misses = 0;
        track.lastSeenMs = nowMs;
        track.fresh = true;
        track.stationary = false;
        tracks.add(track);
    }

    private void prune(long nowMs) {
        for (int i = tracks.size() - 1; i >= 0; i--) {
            Track track = tracks.get(i);
            long sinceSeen = nowMs - track.lastSeenMs;
            boolean dead = sinceSeen > MAX_MISS_MS
                    || (!track.confirmed && (nowMs - track.bornMs) > TENTATIVE_TTL_MS);
            if (dead) {
                tracks.remove(i);
            }
        }
    }

    public int size() {
        return tracks.size();
    }
}
