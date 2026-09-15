package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** كشف حساب زبون واحد بصفحة A4 عمودية، جاهز للإرسال عبر واتساب. */
public final class StatementReport {
    private static final int WIDTH = 595, HEIGHT = 842, MARGIN = 34;
    private static final int INK = 0xff153e76, MUTED = 0xff6b7580, ACCENT = 0xff4191e8;
    private static final int GREEN = 0xff12805c, RED = 0xffb42335, LINE = 0xffe3e8ef;

    private final Context context;
    private final Db db;

    public StatementReport(Context context, Db db) { this.context = context; this.db = db; }

    private static final class Line {
        String date, label, debt, paid, running;
        Line(String date, String label, String debt, String paid, String running) {
            this.date = date; this.label = label; this.debt = debt; this.paid = paid; this.running = running;
        }
    }

    public File build(long debtorId) throws Exception {
        String[] info = db.debtorInfo(debtorId);
        if (info == null) throw new IOException("الزبون غير موجود");
        String name = info[0], phone = info[1];
        double opening = Double.parseDouble(info[2]);

        List<Line> lines = new ArrayList<>();
        double running = opening, debtSum = 0, paidSum = 0;
        if (opening > 0) lines.add(new Line("—", "رصيد افتتاحي", money(opening), "", money(running)));
        try (Cursor c = db.debtLedger(debtorId)) {
            while (c.moveToNext()) {
                boolean isDebt = "DEBT".equals(c.getString(0));
                double amount = c.getDouble(1);
                String note = c.getString(2);
                if (note == null || note.trim().isEmpty()) note = isDebt ? "تعبئة على الحساب" : "سداد نقدي";
                if (isDebt) { running += amount; debtSum += amount; }
                else { running -= amount; paidSum += amount; }
                lines.add(new Line(c.getString(3), note,
                        isDebt ? money(amount) : "", isDebt ? "" : money(amount), money(running)));
            }
        }
        final double balance = running;

        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("تعذر إنشاء مجلد التقارير");
        File file = new File(dir, "statement-" + debtorId + "-" + System.currentTimeMillis() + ".pdf");

        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        int perPage = 22;
        int pages = Math.max(1, (int) Math.ceil(lines.size() / (double) perPage));
        int index = 0;
        for (int page = 1; page <= pages; page++) {
            PdfDocument.Page sheet = document.startPage(
                    new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, page).create());
            Canvas c = sheet.getCanvas();
            c.drawColor(Color.WHITE);
            float y = header(c, paint, fill, name, phone, page, pages);

            if (page == 1) y = summary(c, paint, fill, opening, debtSum, paidSum, balance, y);

            // رأس الجدول
            fill.setColor(0xffeef4fc);
            c.drawRoundRect(new RectF(MARGIN, y, WIDTH - MARGIN, y + 26), 6, 6, fill);
            paint.setColor(INK);
            paint.setTextSize(10.5f);
            paint.setFakeBoldText(true);
            columns(c, paint, y + 17.5f, "التاريخ", "البيان", "عليه", "له", "الرصيد");
            y += 30;

            paint.setFakeBoldText(false);
            int drawn = 0;
            while (index < lines.size() && drawn < perPage) {
                Line line = lines.get(index);
                if (drawn % 2 == 1) {
                    fill.setColor(0xfffafbfd);
                    c.drawRect(MARGIN, y - 3, WIDTH - MARGIN, y + 21, fill);
                }
                paint.setTextSize(10f);
                paint.setColor(MUTED);
                right(c, paint, line.date, MARGIN + 74, y + 13);
                paint.setColor(INK);
                right(c, paint, clip(line.label, 34), MARGIN + 258, y + 13);
                paint.setColor(RED);
                right(c, paint, line.debt, MARGIN + 350, y + 13);
                paint.setColor(GREEN);
                right(c, paint, line.paid, MARGIN + 440, y + 13);
                paint.setColor(INK);
                paint.setFakeBoldText(true);
                right(c, paint, line.running, WIDTH - MARGIN - 4, y + 13);
                paint.setFakeBoldText(false);
                y += 24;
                paint.setColor(LINE);
                paint.setStrokeWidth(0.6f);
                c.drawLine(MARGIN, y - 3, WIDTH - MARGIN, y - 3, paint);
                index++; drawn++;
            }
            if (lines.isEmpty()) {
                paint.setColor(MUTED);
                paint.setTextSize(12f);
                right(c, paint, "لا توجد حركات مسجّلة على هذا الحساب.", WIDTH - MARGIN - 4, y + 16);
                y += 34;
            }

            if (page == pages) closing(c, paint, fill, balance, y);
            footer(c, paint);
            document.finishPage(sheet);
        }

        FileOutputStream out = new FileOutputStream(file);
        try { document.writeTo(out); } finally { out.close(); document.close(); }
        return file;
    }

    /** ترويسة زرقاء تحمل اسم المحطة واسم الزبون. */
    private float header(Canvas c, Paint paint, Paint fill, String name, String phone, int page, int pages) {
        fill.setColor(INK);
        c.drawRect(0, 0, WIDTH, 104, fill);
        Bitmap logo = Branding.logo(context);
        if (logo != null) c.drawBitmap(logo, null, new RectF(MARGIN, 26, MARGIN + 52, 78), null);

        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(19f);
        right(c, paint, Branding.stationName(db), WIDTH - MARGIN, 44);
        paint.setFakeBoldText(false);
        paint.setTextSize(11f);
        paint.setColor(0xffCFE2FA);
        right(c, paint, "كشف حساب زبون  •  " + ShiftDates.today()
                + (pages > 1 ? "  •  صفحة " + page + " من " + pages : ""), WIDTH - MARGIN, 64);

        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(14f);
        right(c, paint, name + (phone.isEmpty() ? "" : "  •  " + phone), WIDTH - MARGIN, 88);
        paint.setFakeBoldText(false);
        return 128;
    }

    /** أربع خانات: الافتتاحي، عليه، له، المستحق. */
    private float summary(Canvas c, Paint paint, Paint fill, double opening,
                          double debt, double paid, double balance, float y) {
        float width = (WIDTH - 2 * MARGIN - 18) / 4f;
        String[] labels = {"رصيد افتتاحي", "إجمالي عليه", "إجمالي له", "المستحق الآن"};
        double[] values = {opening, debt, paid, balance};
        int[] colors = {MUTED, RED, GREEN, balance > 0 ? RED : GREEN};
        for (int i = 0; i < 4; i++) {
            float left = WIDTH - MARGIN - (i + 1) * width - i * 6;
            boolean last = i == 3;
            fill.setColor(last ? (balance > 0 ? 0xfffdeef0 : 0xffe9f6f1) : 0xfff5f8fc);
            c.drawRoundRect(new RectF(left, y, left + width, y + 62), 10, 10, fill);
            paint.setColor(MUTED);
            paint.setTextSize(9.5f);
            center(c, paint, labels[i], left + width / 2, y + 20);
            paint.setColor(colors[i]);
            paint.setFakeBoldText(true);
            paint.setTextSize(last ? 16f : 14f);
            center(c, paint, money(values[i]), left + width / 2, y + 44);
            paint.setFakeBoldText(false);
        }
        return y + 80;
    }

    /** سطر الختام بالمستحق وعبارة الشكر. */
    private void closing(Canvas c, Paint paint, Paint fill, double balance, float y) {
        if (y > HEIGHT - 130) y = HEIGHT - 130;
        boolean owes = balance > 0.009;
        fill.setColor(owes ? 0xfffdeef0 : 0xffe9f6f1);
        c.drawRoundRect(new RectF(MARGIN, y + 10, WIDTH - MARGIN, y + 74), 12, 12, fill);
        paint.setColor(owes ? RED : GREEN);
        paint.setFakeBoldText(true);
        paint.setTextSize(15f);
        right(c, paint, owes ? "المبلغ المستحق عليكم: " + money(balance) + " ريال يمني"
                : "الحساب مسدّد بالكامل — شكرًا لكم", WIDTH - MARGIN - 10, y + 38);
        paint.setFakeBoldText(false);
        paint.setColor(MUTED);
        paint.setTextSize(10f);
        right(c, paint, "نشكر لكم ثقتكم بنا، ونسعد بخدمتكم دائمًا.", WIDTH - MARGIN - 10, y + 60);
    }

    private void footer(Canvas c, Paint paint) {
        paint.setColor(LINE);
        paint.setStrokeWidth(0.8f);
        c.drawLine(MARGIN, HEIGHT - 42, WIDTH - MARGIN, HEIGHT - 42, paint);
        paint.setColor(MUTED);
        paint.setTextSize(9f);
        right(c, paint, "كشف صادر من تطبيق طابق ورحّل  •  " + Util.now(), WIDTH - MARGIN, HEIGHT - 26);
    }

    private void columns(Canvas c, Paint paint, float y, String a, String b, String d, String e, String f) {
        right(c, paint, a, MARGIN + 74, y);
        right(c, paint, b, MARGIN + 258, y);
        right(c, paint, d, MARGIN + 350, y);
        right(c, paint, e, MARGIN + 440, y);
        right(c, paint, f, WIDTH - MARGIN - 4, y);
    }

    private void right(Canvas c, Paint paint, String text, float x, float y) {
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(text == null ? "" : text, x, y, paint);
    }

    private void center(Canvas c, Paint paint, String text, float x, float y) {
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(text == null ? "" : text, x, y, paint);
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        value = value.replace('\n', ' ').trim();
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    static String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    /** نص واتساب مختصر يرافق الملف. */
    public String message(long debtorId) {
        String[] info = db.debtorInfo(debtorId);
        if (info == null) return "";
        double balance = db.debtorBalance(debtorId);
        return "السلام عليكم " + info[0] + "\n"
                + Branding.stationName(db) + " — كشف حسابكم حتى " + ShiftDates.today() + "\n"
                + (balance > 0.009 ? "المستحق عليكم: " + money(balance) + " ريال يمني"
                                   : "حسابكم مسدّد بالكامل، شكرًا لكم")
                + "\nالكشف التفصيلي في الملف المرفق.";
    }
}
