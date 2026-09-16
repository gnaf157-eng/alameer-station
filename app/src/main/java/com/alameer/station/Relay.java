package com.alameer.station.shifts;

import android.app.Activity;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * نقل الورديات بين جهاز العامل وجهاز المدير عبر قناة سرّية على الإنترنت،
 * بلا حساب ولا تسجيل: رمز ربط واحد يُكتب مرة واحدة على كل جهاز.
 */
public class Relay {
    private final Activity activity;
    private final Db db;

    public Relay(Activity a) { activity = a; db = new Db(a); }

    private void toast(String text) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed())
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
        });
    }

    public boolean linked() { return Link.valid(db.linkCode()); }

    /** يرفع وردية مُغلقة إلى القناة ليجدها المدير. */
    public void send(final long shiftId, final boolean loud) {
        final String code = db.linkCode();
        if (!Link.valid(code)) { toast("لم يُربط الجهاز بعد. أدخل رمز الربط في الإعدادات."); return; }
        if (loud) toast("جاري إرسال الوردية...");
        new Thread(() -> {
            try {
                ShiftFile.Shift data = db.exportShift(shiftId);
                String body = ShiftFile.write(data);
                String sealed = Link.cipher(body, Link.secret(code));
                int status = post(Link.url(code), sealed);
                if (status >= 200 && status < 300) {
                    db.markSent(shiftId);
                    toast("أُرسلت الوردية #" + shiftId + " إلى المدير.");
                } else {
                    toast("تعذر الإرسال (" + status + "). ستبقى محفوظة وأعد المحاولة لاحقًا.");
                }
            } catch (Exception e) {
                toast("تعذر الإرسال: تحقق من الإنترنت ثم أعد المحاولة.");
            }
        }).start();
    }

    /** يسحب ورديات العامل من القناة ويحفظها في الأرشيف بانتظار المراجعة. */
    public void receive(final boolean loud, final Runnable done) {
        final String code = db.linkCode();
        if (!Link.valid(code)) { toast("لم يُربط الجهاز بعد. أنشئ رمز الربط من الإعدادات."); return; }
        if (loud) toast("جاري جلب ورديات العامل...");
        new Thread(() -> {
            int added = 0, skipped = 0;
            StringBuilder problems = new StringBuilder();
            try {
                String payload = get(Link.pullUrl(code, db.setting("link_since", "all")));
                for (String line : payload.split("\n")) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        JSONObject envelope = new JSONObject(line);
                        if (!"message".equals(envelope.optString("event", "message"))) continue;
                        String message = envelope.optString("message", "");
                        if (message.isEmpty()) continue;
                        String body = Link.decipher(message, Link.secret(code));
                        ShiftFile.Shift shift = ShiftFile.read(body);
                        if (db.importShift(shift) > 0) added++; else skipped++;
                    } catch (Exception e) {
                        skipped++;
                        String why = String.valueOf(e.getMessage());
                        if (problems.length() < 400 && !why.contains("سبق استيراد"))
                            problems.append("\n• ").append(why);
                    }
                }
                db.setSetting("link_since", String.valueOf(System.currentTimeMillis() / 1000));
            } catch (Exception e) {
                toast("تعذر الاتصال. تحقق من الإنترنت ثم أعد المحاولة.");
                activity.runOnUiThread(done);
                return;
            }
            final int fAdded = added, fSkipped = skipped;
            final String detail = problems.toString();
            activity.runOnUiThread(() -> {
                if (fAdded > 0) toast("وصلت " + fAdded + " وردية إلى الأرشيف للمراجعة.");
                else if (loud) toast(detail.isEmpty()
                        ? "لا توجد ورديات جديدة."
                        : "لم تُقبل " + fSkipped + " وردية:" + detail);
                done.run();
            });
        }).start();
    }

    private int post(String address, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        c.setRequestProperty("Title", "wardiya");
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = c.getResponseCode();
        c.disconnect();
        return status;
    }

    private String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 4 * 1024 * 1024) break;
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }
}
