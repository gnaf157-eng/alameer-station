package com.alameer.station.shifts;

import java.nio.charset.StandardCharsets;

/**
 * الربط بين جهاز العامل وجهاز المدير برمز واحد يُكتب مرة واحدة.
 *
 * الرمز يحمل شيئين: اسم القناة السرّية على وسيط الرسائل، ومفتاح التعمية.
 * منطق خالص بلا اعتماد على أندرويد حتى يُختبر على JVM.
 */
public final class Link {
    private Link(){}

    /** وسيط الرسائل المجاني، بلا حساب ولا تسجيل. */
    public static final String HOST = "https://ntfy.sh";

    /** حروف الرمز: بلا ما يلتبس على العين مثل الصفر والحرف O. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** يولّد رمز ربط جديدًا من اثني عشر حرفًا في ثلاث مجموعات. */
    public static String generate(java.util.Random random) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }

    /** يوحّد شكل الرمز: حروف كبيرة بلا فراغات ولا شرطات. */
    public static String normalize(String code) {
        if (code == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : code.toUpperCase(java.util.Locale.US).toCharArray())
            if (ALPHABET.indexOf(c) >= 0) b.append(c);
        return b.toString();
    }

    /** يتحقّق من صلاحية الرمز قبل حفظه. */
    public static boolean valid(String code) {
        return normalize(code).length() == 12;
    }

    /** يعيد الرمز بشكله المقروء مع الشرطات. */
    public static String pretty(String code) {
        String clean = normalize(code);
        if (clean.length() != 12) return clean;
        return clean.substring(0, 4) + "-" + clean.substring(4, 8) + "-" + clean.substring(8);
    }

    /** اسم القناة المشتقّ من الرمز: لا يكشف الرمز نفسه. */
    public static String topic(String code) {
        String clean = normalize(code);
        if (clean.isEmpty()) return "";
        return "tabiq-" + Calc.hash("topic::" + clean).substring(0, 24);
    }

    /** مفتاح التعمية المشتقّ من الرمز، مختلف عن اسم القناة. */
    public static String secret(String code) {
        return Calc.hash("secret::" + normalize(code));
    }

    /** عنوان النشر والسحب للقناة. */
    public static String url(String code) {
        return HOST + "/" + topic(code);
    }

    /** عنوان سحب الرسائل المخزَّنة منذ مدة، بصيغة JSON سطرًا بسطر. */
    public static String pullUrl(String code, String since) {
        return HOST + "/" + topic(code) + "/json?poll=1&since=" + (since == null || since.isEmpty() ? "all" : since);
    }

    /**
     * تعمية بسيطة متماثلة: يُخلط النص مع سلسلة مشتقّة من المفتاح.
     * الغرض ألّا يُقرأ المحتوى لو خُمّن اسم القناة؛ والتوقيع يمنع العبث.
     */
    public static String cipher(String text, String key) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        byte[] pad = keyStream(key, data.length);
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ pad[i]);
        return base64(out);
    }

    public static String decipher(String encoded, String key) {
        byte[] data = unbase64(encoded);
        byte[] pad = keyStream(key, data.length);
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ pad[i]);
        return new String(out, StandardCharsets.UTF_8);
    }

    private static byte[] keyStream(String key, int length) {
        byte[] stream = new byte[length];
        int filled = 0;
        String block = key == null ? "" : key;
        int counter = 0;
        while (filled < length) {
            String hash = Calc.hash(block + "#" + counter++);
            byte[] chunk = hash.getBytes(StandardCharsets.UTF_8);
            int take = Math.min(chunk.length, length - filled);
            System.arraycopy(chunk, 0, stream, filled, take);
            filled += take;
        }
        return stream;
    }

    private static final String B64 =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    static String base64(byte[] data) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < data.length; i += 3) {
            int n = (data[i] & 0xff) << 16;
            if (i + 1 < data.length) n |= (data[i + 1] & 0xff) << 8;
            if (i + 2 < data.length) n |= (data[i + 2] & 0xff);
            b.append(B64.charAt((n >> 18) & 63)).append(B64.charAt((n >> 12) & 63));
            b.append(i + 1 < data.length ? B64.charAt((n >> 6) & 63) : '=');
            b.append(i + 2 < data.length ? B64.charAt(n & 63) : '=');
        }
        return b.toString();
    }

    static byte[] unbase64(String text) {
        StringBuilder clean = new StringBuilder();
        for (char c : text.toCharArray()) if (B64.indexOf(c) >= 0) clean.append(c);
        int length = clean.length() * 3 / 4;
        byte[] out = new byte[length];
        int pos = 0;
        for (int i = 0; i + 1 < clean.length(); i += 4) {
            int n = B64.indexOf(clean.charAt(i)) << 18 | B64.indexOf(clean.charAt(i + 1)) << 12;
            if (i + 2 < clean.length()) n |= B64.indexOf(clean.charAt(i + 2)) << 6;
            if (i + 3 < clean.length()) n |= B64.indexOf(clean.charAt(i + 3));
            if (pos < length) out[pos++] = (byte) ((n >> 16) & 0xff);
            if (pos < length) out[pos++] = (byte) ((n >> 8) & 0xff);
            if (pos < length) out[pos++] = (byte) (n & 0xff);
        }
        return out;
    }
}
