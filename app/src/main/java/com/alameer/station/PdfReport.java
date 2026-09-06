package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/** يبني تقرير وردية بصيغة PDF بحجم A4 وباتجاه من اليمين لليسار. */
public final class PdfReport {
    private static final int WIDTH = 595, HEIGHT = 842, MARGIN = 40;
    private final Context context;private final Db db;
    private PdfDocument document;private PdfDocument.Page page;private Canvas canvas;private int y;private int pageNumber;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PdfReport(Context context, Db db){this.context=context;this.db=db;}

    public File build(long shiftId) throws Exception {
        document = new PdfDocument();
        newPage();
        header(shiftId);
        readings(shiftId);
        movements(shiftId);
        totals(shiftId);
        signatures();
        document.finishPage(page);
        File dir = new File(context.getCacheDir(), "exports");
        dir.mkdirs();
        File file = new File(dir, "alameer-shift-" + shiftId + ".pdf");
        try (FileOutputStream out = new FileOutputStream(file)) { document.writeTo(out); }
        document.close();
        return file;
    }

    private void newPage(){
        if (page != null) document.finishPage(page);
        pageNumber++;
        page = document.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNumber).create());
        canvas = page.getCanvas();
        y = MARGIN;
    }

    private void ensure(int needed){ if (y + needed > HEIGHT - MARGIN) newPage(); }

    private void header(long shiftId){
        paint.setColor(Util.NAVY);paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, WIDTH, 92, paint);
        right("محطة الأمير — تقرير وردية", 22, Util.GOLD, true, 34);
        String worker = "", opened = "", closed = "", status = "", reason = "", note = "";
        try (Cursor c = db.shiftHeader(shiftId)) {
            if (c.moveToFirst()) {
                worker = c.getString(0); opened = c.getString(1); closed = c.getString(2);
                status = arabicStatus(c.getString(3)); reason = c.getString(4); note = c.getString(5);
            }
        }
        right("وردية رقم " + shiftId + "  •  " + worker, 13, Color.WHITE, false, 60);
        y = 118;
        line("العامل", worker);
        line("وقت الفتح", opened);
        line("وقت الإغلاق", closed.isEmpty() ? "—" : closed);
        line("الحالة", status);
        if (!reason.isEmpty()) line("سبب الفرق", reason);
        if (!note.isEmpty()) line("ملاحظة المدير", note);
        y += 10;
    }

    private void readings(long shiftId){
        section("قراءات الطرمبات");
        columns(new String[]{"الطرمبة", "الوقود", "السابقة", "الحالية", "السعر", "المبيعات"});
        try (Cursor c = db.syncReadings(shiftId)) {
            while (c.moveToNext()) {
                row(new String[]{c.getString(0), c.getString(1), money(c.getDouble(2)),
                        money(c.getDouble(3)), money(c.getDouble(4)), money(c.getDouble(5))});
            }
        }
        y += 12;
    }

    private void movements(long shiftId){
        section("الحركات");
        columns(new String[]{"النوع", "الاسم", "المبلغ", "", "", ""});
        int count = 0;
        try (Cursor c = db.syncMovements(shiftId)) {
            while (c.moveToNext()) {
                count++;
                row(new String[]{arabicType(c.getString(0)), c.getString(1), money(c.getDouble(2)), "", "", ""});
            }
        }
        if (count == 0) row(new String[]{"لا توجد حركات مسجلة", "", "", "", "", ""});
        y += 12;
    }

    private void totals(long shiftId){
        section("المطابقة");
        double balance = db.balance(shiftId);
        line("المبيعات", money(db.sales(shiftId)) + " ر.ي");
        line("المقبوضات", "+ " + money(db.total(shiftId, "COLLECTION")) + " ر.ي");
        line("النقد المسلّم", "− " + money(db.total(shiftId, "CASH")) + " ر.ي");
        line("الديون", "− " + money(db.total(shiftId, "DEBT")) + " ر.ي");
        line("المخاريج", "− " + money(db.total(shiftId, "EXPENSE")) + " ر.ي");
        ensure(56);
        boolean matched = Math.abs(balance) < 0.01;
        paint.setColor(matched ? 0xffe3efe3 : 0xfffce9e8);paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(MARGIN, y, WIDTH - MARGIN, y + 46, 10, 10, paint);
        y += 30;
        right((matched ? "الوردية مطابقة" : "يوجد فرق") + "  —  الباقي " + money(balance) + " ر.ي",
                15, matched ? Util.GREEN : Util.RED, true, y);
        y += 34;
    }

    private void signatures(){
        ensure(80);
        y += 24;
        paint.setColor(0xff9aa0a6);paint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, y, MARGIN + 180, y, paint);
        canvas.drawLine(WIDTH - MARGIN - 180, y, WIDTH - MARGIN, y, paint);
        y += 18;
        text("توقيع المدير", 11, 0xff5f6469, false, MARGIN, y, Paint.Align.LEFT);
        text("توقيع العامل", 11, 0xff5f6469, false, WIDTH - MARGIN, y, Paint.Align.RIGHT);
        y += 22;
        right("صدر من تطبيق محطة الأمير بتاريخ " + Util.now(), 9, 0xff8b9096, false, y);
    }

    private void section(String name){
        ensure(40);
        paint.setColor(Util.NAVY);paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(MARGIN, y, WIDTH - MARGIN, y + 26, 6, 6, paint);
        right(name, 13, Util.GOLD, true, y + 18);
        y += 38;
    }

    private void columns(String[] headers){
        ensure(26);
        paint.setColor(0xffeceef0);paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN, y - 2, WIDTH - MARGIN, y + 18, paint);
        cells(headers, 11, Util.NAVY, true);
    }

    private void row(String[] values){
        ensure(24);
        cells(values, 11, 0xff2b2f33, false);
        paint.setColor(0xffe4e6e8);paint.setStrokeWidth(0.6f);
        canvas.drawLine(MARGIN, y + 4, WIDTH - MARGIN, y + 4, paint);
    }

    private void cells(String[] values, int size, int color, boolean bold){
        float usable = WIDTH - MARGIN * 2f;
        float column = usable / values.length;
        for (int i = 0; i < values.length; i++) {
            float rightEdge = WIDTH - MARGIN - column * i - 6;
            text(values[i], size, color, bold, rightEdge, y + 13, Paint.Align.RIGHT);
        }
        y += 22;
    }

    private void line(String label, String value){
        ensure(22);
        text(label, 11, 0xff6b7075, false, WIDTH - MARGIN, y + 12, Paint.Align.RIGHT);
        text(value, 12, Util.NAVY, true, WIDTH - MARGIN - 110, y + 12, Paint.Align.RIGHT);
        y += 22;
    }

    private void right(String value, int size, int color, boolean bold, int baseline){
        text(value, size, color, bold, WIDTH - MARGIN, baseline, Paint.Align.RIGHT);
    }

    private void text(String value, int size, int color, boolean bold, float x, float baseline, Paint.Align align){
        if (value == null) value = "";
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setFakeBoldText(bold);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText(value, x, baseline, paint);
        paint.setFakeBoldText(false);
    }

    private String money(double value){return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);}
    static String arabicType(String t){if("COLLECTION".equals(t))return "مقبوضات";if("CASH".equals(t))return "نقد مسلّم";if("DEBT".equals(t))return "ديون";return "مخاريج";}
    static String arabicStatus(String s){if("OPEN".equals(s))return "مفتوحة";if("SUBMITTED".equals(s))return "مرسلة للمدير";if("RETURNED".equals(s))return "مُرجعة للتصحيح";if("APPROVED".equals(s))return "معتمدة";return s;}
}
