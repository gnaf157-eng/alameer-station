package com.alameer.station.shifts;

/**
 * قفل التطبيق: بصمة الجهاز أو رمز احتياطي.
 * منطق خالص بلا اعتماد على أندرويد حتى يُختبر على JVM.
 */
public final class Lock {
    private Lock(){}

    /** أقل طول مقبول للرمز الاحتياطي. */
    public static final int MIN_PIN = 4;

    /** يُقفل التطبيق بعد هذه المدة في الخلفية، فلا يُطلب القفل بين كل شاشتين. */
    public static final long GRACE_MILLIS = 60_000L;

    /** تجزئة الرمز بملح الجهاز، فلا يُخزَّن كنص ولا يُكسر بجدول جاهز. */
    public static String hash(String pin, String salt) {
        return Calc.hash("lock::" + (salt == null ? "" : salt) + "::" + (pin == null ? "" : pin.trim()));
    }

    /** مقارنة ثابتة الزمن لا تكشف طول التطابق. */
    public static boolean same(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    /** سبب رفض الرمز، أو نص فارغ إن قُبل. */
    public static String reject(String pin, String again) {
        String p = pin == null ? "" : pin.trim();
        if (p.length() < MIN_PIN) return "الرمز: " + MIN_PIN + " رموز على الأقل";
        boolean uniform = true;
        for (int i = 1; i < p.length(); i++) if (p.charAt(i) != p.charAt(0)) { uniform = false; break; }
        if (uniform) return "لا تستخدم رمزًا مكرّرًا مثل 1111";
        if ("0123456789".contains(p) || "9876543210".contains(p)) return "لا تستخدم أرقامًا متتابعة";
        if (again != null && !p.equals(again.trim())) return "الرمزان غير متطابقين";
        return "";
    }

    /**
     * هل يجب طلب القفل الآن؟
     * يُطلب عند أول فتح، وبعد مغادرة التطبيق مدةً تتجاوز المهلة.
     */
    public static boolean shouldAsk(boolean enabled, boolean unlockedInSession,
                                    long leftAtMillis, long nowMillis) {
        if (!enabled) return false;
        if (!unlockedInSession) return true;
        if (leftAtMillis <= 0) return false;
        return nowMillis - leftAtMillis > GRACE_MILLIS;
    }

    /** ملح عشوائي جديد لهذا الجهاز. */
    public static String newSalt(java.util.Random random) {
        StringBuilder b = new StringBuilder();
        String set = "abcdef0123456789";
        for (int i = 0; i < 24; i++) b.append(set.charAt(random.nextInt(set.length())));
        return b.toString();
    }
}
