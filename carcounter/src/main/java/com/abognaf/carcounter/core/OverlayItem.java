package com.abognaf.carcounter.core;

/** عنصر رسومي جاهز للعرض (نسخة ثابتة من بيانات الأثر لتفادي مشاكل الخيوط). */
public final class OverlayItem {

    public final float x1;
    public final float y1;
    public final float x2;
    public final float y2;
    public final String label;
    public final float score;
    public final int trackId;
    public final boolean counted;
    public final boolean stationary;
    public final boolean confirmed;
    public final int side;

    public OverlayItem(float x1, float y1, float x2, float y2, String label, float score,
                       int trackId, boolean counted, boolean stationary, boolean confirmed, int side) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.label = label;
        this.score = score;
        this.trackId = trackId;
        this.counted = counted;
        this.stationary = stationary;
        this.confirmed = confirmed;
        this.side = side;
    }
}
