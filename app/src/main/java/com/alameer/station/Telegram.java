package com.alameer.station.shifts;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * إشعار تلغرام للعميل بعد كل حركة في حسابه.
 * الإرسال في خيط منفصل ولا يعطّل الحفظ أبدًا: الحركة تُسجَّل أولًا ثم يُرسل الإشعار.
 */
public final class Telegram {
    private Telegram(){}

    /** نص الرسالة التي تصل العميل. */
    public static String message(String station, String name, boolean debt,
                                 double amount, double balance, String note, String date) {
        StringBuilder b = new StringBuilder();
        b.append(station).append('\n');
        b.append(debt ? "قيد عليكم" : "سداد مستلم").append('\n');
        b.append("الأخ/ ").append(name).append('\n');
        b.append("المبلغ: ").append(Calc.money(amount)).append(" ر.ي\n");
        if (note != null && !note.trim().isEmpty())
            b.append("البيان: ").append(note.trim()).append('\n');
        b.append("التاريخ: ").append(date).append('\n');
        // الرصيد بلغة مفهومة بلا إشارة سالبة.
        if (balance > 0.009) b.append("الرصيد المستحق: ").append(Calc.money(balance)).append(" ر.ي");
        else if (balance < -0.009) b.append("لكم رصيد: ").append(Calc.money(-balance)).append(" ر.ي");
        else b.append("الحساب مسدّد بالكامل");
        return b.toString();
    }

    /** يتحقّق من صيغة معرّف المحادثة قبل الحفظ. */
    public static boolean validChat(String chatId) {
        String v = chatId == null ? "" : chatId.trim();
        if (v.isEmpty()) return false;
        if (v.startsWith("@")) return v.length() > 3;
        if (v.startsWith("-")) v = v.substring(1);
        for (int i = 0; i < v.length(); i++) if (!Character.isDigit(v.charAt(i))) return false;
        return v.length() > 4;
    }

    /**
     * يرسل الرسالة عبر واجهة بوت تلغرام.
     * يعيد نصًا فارغًا عند النجاح، أو سبب الفشل.
     */
    public static String send(String token, String chatId, String text) {
        if (token == null || token.trim().isEmpty()) return "رمز البوت غير مضبوط";
        if (!validChat(chatId)) return "معرّف المحادثة غير صالح";
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.telegram.org/bot" + token.trim() + "/sendMessage");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            String body = "chat_id=" + enc(chatId.trim())
                    + "&text=" + enc(text)
                    + "&disable_web_page_preview=true";
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = c.getResponseCode();
            if (status >= 200 && status < 300) return "";
            if (status == 401) return "رمز البوت غير صحيح";
            if (status == 400) return "معرّف المحادثة خاطئ، أو لم يبدأ العميل محادثة البوت";
            if (status == 403) return "العميل لم يبدأ محادثة البوت بعد";
            return "تعذر الإرسال (" + status + ")";
        } catch (Exception e) {
            return "تعذر الاتصال بتلغرام";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String enc(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }
}
