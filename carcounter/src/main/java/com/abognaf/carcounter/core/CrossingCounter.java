package com.abognaf.carcounter.core;

import java.util.ArrayList;
import java.util.List;

/**
 * محرّك العدّ الكامل: تتبّع + احتساب عبور خط العدّ باتجاهين (داخل/خارج).
 *
 * <p>قواعد أساسية مضمونة هنا:
 * <ul>
 *   <li>لا تُحتسب السيارة إلا عند عبورها خطّ العبور فعليًا.</li>
 *   <li>السيارة الواقفة (أو الاهتزاز حول الخط) لا تُحتسب إطلاقًا.</li>
 *   <li>كل سيارة تُحتسب مرة واحدة فقط — لكل أثر معرّف مستقل وعلامة "تم العدّ".</li>
 *   <li>تأكيد العبور يتم في إطارين متتاليين لتفادي القفزات المفاجئة.</li>
 * </ul>
 * لا يعتمد هذا الصنف على أي واجهة أندرويد، لذا يُختبر بالكامل على الحاسوب.
 */
public final class CrossingCounter {

    /** هامش الحياد حول الخط (نسبة من ارتفاع الإطار) لمنع الاهتزاز. */
    public static final float HYSTERESIS_RATIO = 0.015f;
    /** أقل سرعة تُعتبر حركة حقيقية (نسبة من ارتفاع الإطار في الثانية). */
    public static final float MOVING_SPEED_RATIO = 0.05f;
    /** أقل مسافة تقطعها السيارة منذ ظهورها قبل السماح بالعدّ. */
    public static final float MIN_TRAVEL_RATIO = 0.04f;
    /** نصف قطر حماية من إعادة عدّ السيارة نفسها بعد فقدان أثرها. */
    public static final float GUARD_RADIUS_RATIO = 0.10f;
    /** المدة التي تبقى فيها حماية إعادة العدّ فعّالة. */
    public static final long GUARD_WINDOW_MS = 1500L;
    /** مهلة تأكيد العبور قبل إلغائه. */
    public static final long PENDING_TIMEOUT_MS = 450L;

    private final VehicleTracker tracker;
    private final CountingLine line;
    private final List<CrossingEvent> recentEvents = new ArrayList<>();
    private final List<CrossingEvent> frameEvents = new ArrayList<>();
    private final List<OverlayItem> items = new ArrayList<>();

    private float frameW;
    private float frameH;
    private int inCount;
    private int outCount;
    private int total;

    public CrossingCounter(float frameW, float frameH) {
        this.frameW = Math.max(2f, frameW);
        this.frameH = Math.max(2f, frameH);
        this.tracker = new VehicleTracker(this.frameW, this.frameH);
        this.line = new CountingLine();
    }

    public CountingLine line() {
        return line;
    }

    public int inCount() {
        return inCount;
    }

    public int outCount() {
        return outCount;
    }

    public int total() {
        return total;
    }

    public float frameW() {
        return frameW;
    }

    public float frameH() {
        return frameH;
    }

    public List<Track> tracks() {
        return tracker.tracks();
    }

    /** تغيير دقة التحليل: تُلغى الآثار القديمة (إحداثياتها لم تعد صحيحة) مع حفظ العدادات. */
    public void setFrameSize(float w, float h) {
        if (w < 2f || h < 2f) {
            return;
        }
        if (Math.abs(w - frameW) > 0.5f || Math.abs(h - frameH) > 0.5f) {
            frameW = w;
            frameH = h;
            tracker.setFrameSize(w, h);
            recentEvents.clear();
            frameEvents.clear();
        }
    }

    /** تحديث خط العبور (يُستدعى من الواجهة عند سحب الخط). */
    public void setLine(float x1, float y1, float x2, float y2) {
        setLine(x1, y1, x2, y2, line.isFlipped());
    }

    /** تحديث خط العبور مع تحديد اتجاه "داخل" في خطوة واحدة. */
    public void setLine(float x1, float y1, float x2, float y2, boolean flipped) {
        line.setNormalized(x1, y1, x2, y2);
        line.setFlipped(flipped);
        onLineChanged();
    }

    /** عكس معنى الاتجاهين: ما كان "داخل" يصبح "خارج". */
    public void flipDirection() {
        line.flip();
        onLineChanged();
    }

    private void onLineChanged() {
        // تصفير جهات الآثار حتى لا يُنشئ تغيير الخط عبورًا وهميًا
        List<Track> tracks = tracker.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            track.side = 0;
            track.bornSide = 0;
            track.pendingSide = 0;
        }
    }

    /** تصفير العدادات وإلغاء كل الآثار. */
    public void reset() {
        inCount = 0;
        outCount = 0;
        total = 0;
        resetTracks();
    }

    /** إلغاء الآثار الحالية فقط (بدون تصفير الأرقام). */
    public void resetTracks() {
        tracker.reset();
        recentEvents.clear();
        frameEvents.clear();
    }

    /**
     * المعالجة الكاملة لإطار واحد: تتبّع الكشوف ثم احتساب العبور.
     *
     * @param detections الكشوف الحالية (قد تكون فارغة)
     * @param nowMs      زمن المعالجة (SystemClock.elapsedRealtime)
     */
    public OverlayFrame process(List<DetectedBox> detections, long nowMs, long inferenceMs,
                               float fps, int rawDetections) {
        tracker.update(detections, nowMs);

        float hysteresis = HYSTERESIS_RATIO * frameH;
        float minSpeed = MOVING_SPEED_RATIO * frameH;
        float minTravel = MIN_TRAVEL_RATIO * frameH;
        float guardRadius = GUARD_RADIUS_RATIO * (float) Math.hypot(frameW, frameH);
        frameEvents.clear();

        List<Track> tracks = tracker.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            if (!track.confirmed) {
                continue;
            }
            float distance = line.signedDistance(track.anchorX, track.anchorY, frameW, frameH);
            int side = distance > hysteresis ? 1 : (distance < -hysteresis ? -1 : 0);
            if (side != 0 && track.bornSide == 0) {
                track.bornSide = side;
            }
            if (side == 0) {
                if (track.pendingSide != 0 && (nowMs - track.pendingSinceMs) > PENDING_TIMEOUT_MS) {
                    track.pendingSide = 0;
                }
                continue;
            }
            if (track.side == 0) {
                track.side = side;
                continue;
            }
            if (side != track.side) {
                // تغيّر الجهة: تسجيل عبور مرشّح بانتظار تأكيده في إطار لاحق
                track.side = side;
                track.pendingSide = side;
                track.pendingSinceMs = nowMs;
                continue;
            }
            boolean canCount = track.pendingSide == side
                    && track.fresh
                    && !track.counted
                    && track.hits >= 2
                    && track.speed() >= minSpeed
                    && track.traveled() >= minTravel
                    && !recentlyCountedNearby(track, side, nowMs, guardRadius);
            if (canCount) {
                track.counted = true;
                track.pendingSide = 0;
                boolean toInside = side > 0;
                if (toInside) {
                    inCount++;
                } else {
                    outCount++;
                }
                total = inCount + outCount;
                CrossingEvent event = new CrossingEvent(track.id, toInside, track.anchorX, track.anchorY,
                        nowMs, Labels.arabicName(track.label), track.score);
                frameEvents.add(event);
                recentEvents.add(event);
            }
        }

        pruneRecentEvents(nowMs);
        return buildFrame(nowMs, inferenceMs, fps, rawDetections);
    }

    /**
     * حماية من عدّ السيارة نفسها مرتين إذا فُقد أثرها وأنشأ النظام أثرًا جديدًا:
     * إن كانت السيارة الجديدة قد ظهرت أصلًا في الجهة التي نُقل إليها، وقريبًا
     * من مكان وزمن عبور سيارة أخرى في الاتجاه نفسه — تُعتبر نفس السيارة ولا تُعدّ.
     */
    private boolean recentlyCountedNearby(Track track, int side, long nowMs, float radius) {
        if (track.bornSide != side) {
            return false;
        }
        if ((nowMs - track.bornMs) > GUARD_WINDOW_MS + 700L) {
            return false;
        }
        for (int i = recentEvents.size() - 1; i >= 0; i--) {
            CrossingEvent event = recentEvents.get(i);
            if ((nowMs - event.timeMs) > GUARD_WINDOW_MS) {
                continue;
            }
            if (event.toInside != (side > 0)) {
                continue;
            }
            float dist = (float) Math.hypot(event.x - track.anchorX, event.y - track.anchorY);
            if (dist <= radius) {
                return true;
            }
        }
        return false;
    }

    private void pruneRecentEvents(long nowMs) {
        for (int i = recentEvents.size() - 1; i >= 0; i--) {
            if ((nowMs - recentEvents.get(i).timeMs) > GUARD_WINDOW_MS) {
                recentEvents.remove(i);
            }
        }
    }

    private OverlayFrame buildFrame(long nowMs, long inferenceMs, float fps, int rawDetections) {
        items.clear();
        List<Track> tracks = tracker.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            items.add(new OverlayItem(track.box.x1, track.box.y1, track.box.x2, track.box.y2,
                    Labels.arabicName(track.label), track.score, track.id, track.counted,
                    track.stationary, track.confirmed, track.side));
        }
        return new OverlayFrame(new ArrayList<>(items), new ArrayList<>(frameEvents),
                total, inCount, outCount, tracks.size(), nowMs, fps, inferenceMs, rawDetections);
    }
}
