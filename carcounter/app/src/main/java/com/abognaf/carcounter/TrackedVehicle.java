package com.abognaf.carcounter;

import android.graphics.RectF;

/** سيارة واحدة متتبَّعة بمعرّف مستقل. */
public class TrackedVehicle {
    public final int id;
    public RectF box;
    public float cx, cy;          // مركز الصندوق الحالي
    public float firstCx, firstCy; // مركز أول ظهور
    public int missedFrames = 0;
    public int hits = 1;
    public boolean counted = false;
    /** الجهة التي كانت فيها السيارة بالنسبة للخط في آخر إطار (>0 أو <0) */
    public int lastSide = 0;

    TrackedVehicle(int id, RectF box) {
        this.id = id;
        update(box);
        firstCx = cx;
        firstCy = cy;
    }

    void update(RectF b) {
        box = b;
        cx = b.centerX();
        cy = b.centerY();
    }
}
