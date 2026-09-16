package com.abognaf.carcounter.core;

import java.util.Locale;

/**
 * تحويل أسماء الأصناف القادمة من النموذج (COCO) إلى أسماء عربية،
 * وتحديد ما إذا كان الصنف مركبة تُعدّ أم لا.
 */
public final class Labels {

    /** أسماء تدل على مركبة في نماذج COCO (إنجليزي أو عربي). */
    private static final String[] VEHICLE_HINTS = {
            "car", "truck", "bus", "motorcycle", "motorbike", "bicycle", "bike",
            "vehicle", "van", "pickup", "tractor", "taxi",
            "سيارة", "شاحنة", "حافلة", "باص", "بص", "دراجة", "مركبة", "عربة", "تراكتور"
    };

    /** أسماء تدل على دراجة (نارية أو هوائية). */
    private static final String[] TWO_WHEEL_HINTS = {
            "motorcycle", "motorbike", "bicycle", "bike", "scooter",
            "سيارة دراجة", "دراجة", "موتوسيكل", "سيكل"
    };

    private static final String[] MOTORCYCLE_HINTS = {
            "motorcycle", "motorbike", "scooter", "موتوسيكل", "دراجة نارية"
    };

    private Labels() {
    }

    /** هل الصنف سيارة/مركبة تستحق العدّ؟ */
    public static boolean isVehicle(String label, boolean countMotorcycles, boolean countBicycles) {
        if (label == null) {
            return false;
        }
        String lower = label.toLowerCase(Locale.ROOT).trim();
        if (lower.isEmpty()) {
            return false;
        }
        if (!countBicycles && isBicycle(lower)) {
            return false;
        }
        if (!countMotorcycles && isMotorcycle(lower)) {
            return false;
        }
        if (!countMotorcycles && !countBicycles && isTwoWheeler(lower)) {
            return false;
        }
        for (String hint : VEHICLE_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTwoWheeler(String lower) {
        for (String hint : TWO_WHEEL_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMotorcycle(String lower) {
        for (String hint : MOTORCYCLE_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBicycle(String lower) {
        return lower.contains("bicycle") || lower.contains("bike") || lower.contains("سيكل")
                || lower.contains("هوائية");
    }

    /** اسم عربي واضح للعرض على الشاشة. */
    public static String arabicName(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "مركبة";
        }
        String lower = label.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("motorcycle") || lower.contains("motorbike") || lower.contains("scooter")
                || lower.contains("موتوسيكل")) {
            return "دراجة نارية";
        }
        if (isBicycle(lower)) {
            return "دراجة";
        }
        if (lower.contains("truck")) {
            return "شاحنة";
        }
        if (lower.contains("bus")) {
            return "حافلة";
        }
        if (lower.contains("car") || lower.contains("taxi") || lower.contains("سيارة")) {
            return "سيارة";
        }
        if (lower.contains("van") || lower.contains("pickup")) {
            return "شاحنة صغيرة";
        }
        if (lower.contains("person")) {
            return "شخص";
        }
        return label.trim();
    }
}
