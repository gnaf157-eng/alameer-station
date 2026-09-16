package com.alameer.station.shifts;

/**
 * مطابقة العجز: مقارنة رصيد الخزان الدفتري بالقياس اليدوي (المقياس/العصا).
 * منطق خالص بلا اعتماد على أندرويد حتى يُختبر على JVM.
 *
 * الفرق = المقاس − الدفتري.
 *   سالب  → عجز (فقد أو تسرّب أو بيع غير مسجّل).
 *   موجب  → زيادة (خطأ قياس أو توريد لم يُسجَّل).
 */
public final class Dip {
    private Dip(){}

    /** حدّ التفاوت الطبيعي: نصف بالمئة من المخزون، فالتبخّر ودقّة القياس واردان. */
    public static final double NORMAL_RATE = 0.005;
    /** أدنى حدّ مقبول باللترات مهما صغر المخزون. */
    public static final double MIN_TOLERANCE = 20;

    public static final int OK = 0;
    public static final int WATCH = 1;
    public static final int ALARM = 2;

    /** الفرق باللترات: موجب زيادة وسالب عجز. */
    public static double gap(double bookLitres, double measuredLitres) {
        return measuredLitres - bookLitres;
    }

    /** الحدّ المسموح لهذا المخزون. */
    public static double tolerance(double bookLitres) {
        return Math.max(MIN_TOLERANCE, Math.abs(bookLitres) * NORMAL_RATE);
    }

    /** نسبة الفرق إلى المخزون الدفتري مئويًا. */
    public static double percent(double bookLitres, double measuredLitres) {
        if (Math.abs(bookLitres) < 0.0001) return 0;
        return gap(bookLitres, measuredLitres) * 100 / bookLitres;
    }

    /** درجة الخطورة: ضمن الحد، أو ضعف الحد، أو أكثر. */
    public static int level(double bookLitres, double measuredLitres) {
        double g = Math.abs(gap(bookLitres, measuredLitres));
        double t = tolerance(bookLitres);
        if (g <= t) return OK;
        return g <= t * 2 ? WATCH : ALARM;
    }

    public static String verdict(double bookLitres, double measuredLitres) {
        switch (level(bookLitres, measuredLitres)) {
            case OK: return "ضمن الحد الطبيعي";
            case WATCH: return "فرق يحتاج متابعة";
            default: return "فرق كبير يستوجب مراجعة";
        }
    }

    public static String direction(double g) {
        if (Math.abs(g) < 0.0001) return "مطابق تمامًا";
        return g < 0 ? "عجز" : "زيادة";
    }

    /** قيمة الفرق بالريال حسب سعر اللتر. */
    public static double value(double gapLitres, double pricePerLitre) {
        if (!(pricePerLitre > 0)) return 0;
        return Math.abs(gapLitres) * pricePerLitre;
    }

    /** وصف مختصر للعرض والتقارير. */
    public static String summary(double bookLitres, double measuredLitres, double price) {
        double g = gap(bookLitres, measuredLitres);
        if (Math.abs(g) < 0.0001) return "مطابق تمامًا";
        String s = direction(g) + " " + Calc.money(Math.abs(g)) + " لتر";
        if (price > 0) s += "  •  " + Calc.money(value(g, price)) + " ر.ي";
        return s;
    }

    /** ملاحظة الحركة التصحيحية التي تُسجَّل في المخزون. */
    public static String note(double gapLitres, String reason) {
        String base = "مطابقة مقياس — " + direction(gapLitres);
        return reason == null || reason.trim().isEmpty() ? base : base + ": " + reason.trim();
    }

    /** اتجاه الحركة التصحيحية: العجز صادر والزيادة وارد. */
    public static String correctionDirection(double gapLitres) {
        return gapLitres < 0 ? "OUT" : "IN";
    }
}
