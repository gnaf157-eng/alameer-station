package com.alameer.station.shifts;

/**
 * حماية الدخول: تقييد المحاولات الخاطئة وتقوية تجزئة كلمة السر.
 * منطق خالص بلا اعتماد على أندرويد حتى يُختبر على JVM.
 */
public final class Guard {
    private Guard(){}

    /** بعد هذا العدد من الأخطاء يُقفل الدخول مؤقتًا. */
    public static final int MAX_TRIES = 5;
    /** مدة القفل بالثواني، تتضاعف مع تكرار القفل. */
    public static final int BASE_LOCK_SECONDS = 60;
    public static final int MAX_LOCK_SECONDS = 15 * 60;

    /** عدد المحاولات المتبقية قبل القفل. */
    public static int remaining(int failures) {
        int left = MAX_TRIES - failures;
        return left < 0 ? 0 : left;
    }

    /**
     * مدة القفل بالثواني بعد عدد من الأخطاء.
     * تبدأ بدقيقة وتتضاعف حتى خمس عشرة دقيقة، فالتخمين الآلي يصير مستحيلًا عمليًا.
     */
    public static int lockSeconds(int failures) {
        if (failures < MAX_TRIES) return 0;
        int steps = (failures - MAX_TRIES) / MAX_TRIES;
        long seconds = (long) BASE_LOCK_SECONDS << Math.min(steps, 10);
        return (int) Math.min(seconds, MAX_LOCK_SECONDS);
    }

    /** الثواني المتبقية من القفل، أو صفرًا إن انتهى. */
    public static int lockLeft(int failures, long lockedAtMillis, long nowMillis) {
        int total = lockSeconds(failures);
        if (total == 0 || lockedAtMillis <= 0) return 0;
        long passed = (nowMillis - lockedAtMillis) / 1000;
        long left = total - passed;
        return left <= 0 ? 0 : (int) left;
    }

    public static boolean locked(int failures, long lockedAtMillis, long nowMillis) {
        return lockLeft(failures, lockedAtMillis, nowMillis) > 0;
    }

    /** رسالة عربية تصف حالة القفل أو ما تبقّى من محاولات. */
    public static String message(int failures, long lockedAtMillis, long nowMillis) {
        int left = lockLeft(failures, lockedAtMillis, nowMillis);
        if (left > 0) {
            if (left >= 60) {
                int minutes = (left + 59) / 60;
                return "الدخول مقفل مؤقتًا. حاول بعد " + minutes + " دقيقة.";
            }
            return "الدخول مقفل مؤقتًا. حاول بعد " + left + " ثانية.";
        }
        int tries = remaining(failures % MAX_TRIES == 0 && failures > 0 ? 0 : failures);
        if (tries <= 2) return "كلمة السر غير صحيحة. بقيت " + tries + " محاولة قبل القفل.";
        return "كلمة السر غير صحيحة.";
    }

    /**
     * تجزئة كلمة السر بملح خاص بكل جهاز وتكرار كثيف،
     * حتى لا تُستخرج من الشيفرة المنشورة ولا تُكسر بجدول جاهز.
     */
    public static String hash(String password, String salt) {
        String value = "tabiq::" + (salt == null ? "" : salt) + "::" + (password == null ? "" : password.trim());
        for (int i = 0; i < 12000; i++) value = Calc.hash(value);
        return value;
    }

    /** مقارنة ثابتة الزمن: لا تكشف طول التطابق. */
    public static boolean same(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    /** قوة كلمة السر: نصّ فارغ إن قُبلت، وإلا سبب الرفض. */
    public static String weakness(String password) {
        String p = password == null ? "" : password.trim();
        if (p.length() < 4) return "كلمة السر: 4 رموز على الأقل";
        if (p.equals("2216") || p.equals("6114"))
            return "لا تستخدم الرمز الافتراضي المنشور";
        boolean same = true;
        for (int i = 1; i < p.length(); i++) if (p.charAt(i) != p.charAt(0)) { same = false; break; }
        if (same) return "لا تستخدم رمزًا مكرّرًا مثل 1111";
        if ("123456789".contains(p) || "987654321".contains(p))
            return "لا تستخدم أرقامًا متتابعة";
        return "";
    }

    /** ملح عشوائي جديد لهذا الجهاز. */
    public static String newSalt(java.util.Random random) {
        StringBuilder b = new StringBuilder();
        String set = "abcdef0123456789";
        for (int i = 0; i < 32; i++) b.append(set.charAt(random.nextInt(set.length())));
        return b.toString();
    }
}
