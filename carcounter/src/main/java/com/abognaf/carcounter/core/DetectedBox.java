package com.abognaf.carcounter.core;

/** كائن مكتشَف من نموذج الذكاء الاصطناعي: اسم الصنف + درجة الثقة + المستطيل. */
public final class DetectedBox {

    public final String label;
    public final float score;
    public final Box box;

    public DetectedBox(String label, float score, float x1, float y1, float x2, float y2) {
        this.label = label == null ? "" : label;
        this.score = score;
        this.box = new Box(x1, y1, x2, y2);
    }

    @Override
    public String toString() {
        return label + "(" + score + ") " + box;
    }
}
