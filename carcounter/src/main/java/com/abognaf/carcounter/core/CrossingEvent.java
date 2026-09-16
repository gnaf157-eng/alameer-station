package com.abognaf.carcounter.core;

/** حدث عبور سيارة لخط العدّ (يُستخدم للعدّ وللرسم على الشاشة). */
public final class CrossingEvent {

    public final int trackId;
    /** true = عبور باتجاه "داخل"، false = باتجاه "خارج". */
    public final boolean toInside;
    public final float x;
    public final float y;
    public final long timeMs;
    public final String label;
    public final float score;

    public CrossingEvent(int trackId, boolean toInside, float x, float y, long timeMs,
                         String label, float score) {
        this.trackId = trackId;
        this.toInside = toInside;
        this.x = x;
        this.y = y;
        this.timeMs = timeMs;
        this.label = label;
        this.score = score;
    }
}
