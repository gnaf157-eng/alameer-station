package com.abognaf.carcounter.core;

/**
 * أثر (Track) لسيارة واحدة يتم تتبّعها عبر الإطارات.
 * لكل أثر معرّف مستقل، ولذلك لا تُحتسب السيارة الواحدة أكثر من مرة
 * حتى لو ظهرت في عشرات الإطارات.
 */
public final class Track {

    /** معرّف فريد مستقل لكل سيارة. */
    public final int id;

    /** آخر مستطيل معروف/متوقَّع للسيارة. */
    public final Box box = new Box();

    public String label = "";
    public float score;

    /** نقطة التلامس مع الطريق (منتصف أسفل المستطيل) بعد التنعيم. */
    public float anchorX;
    public float anchorY;

    /** سرعة النقطة المرجعية بالبكسل/ثانية. */
    public float vx;
    public float vy;

    /** نقطة ظهور السيارة أول مرة (تُستخدم للتأكد من أنها متحركة فعلًا). */
    public float bornX;
    public float bornY;

    public int hits;
    public int misses;
    public long bornMs;
    public long lastSeenMs;

    /** الجهة الحالية عن خط العبور: 1 = داخل، -1 = خارج، 0 = غير محددة. */
    public int side;

    /** الجهة التي ظهرت فيها السيارة أول مرة. */
    public int bornSide;

    /** جهة العبور المرشّحة (بانتظار تأكيدها في إطار لاحق لمنع العدّ المزدوج). */
    public int pendingSide;
    public long pendingSinceMs;

    /** هل تم احتساب هذه السيارة؟ (مرة واحدة فقط طوال عمر الأثر). */
    public boolean counted;

    /** هل تأكد الأثر (شوهد في إطارين على الأقل)؟ */
    public boolean confirmed;

    /** وُجد كائن ثابت في مكانه (سيارة واقفة) — لا يُحتسب. */
    public boolean stationary;

    /** تمت مطابقة الأثر بكشف جديد في الإطار الحالي. */
    public boolean fresh;

    Track(int id, long nowMs) {
        this.id = id;
        this.bornMs = nowMs;
        this.lastSeenMs = nowMs;
    }

    public float speed() {
        return (float) Math.hypot(vx, vy);
    }

    /** المسافة التي قطعتها السيارة منذ ظهورها. */
    public float traveled() {
        return (float) Math.hypot(anchorX - bornX, anchorY - bornY);
    }

    public long ageMs(long nowMs) {
        return nowMs - bornMs;
    }

    @Override
    public String toString() {
        return "#" + id + " " + label + " side=" + side + " counted=" + counted + " " + box;
    }
}
