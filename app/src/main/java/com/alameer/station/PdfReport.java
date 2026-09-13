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
        fuelLitres(shiftId);
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
        canvas.drawRect(0, 0, WIDTH, 72, paint);
        right("محطة الأمير — تقرير وردية", 18, Util.GOLD, true, 28);
        String worker = "", opened = "", closed = "", status = "", reason = "", note = "";
        try (Cursor c = db.shiftHeader(shiftId)) {
            if (c.moveToFirst()) {
                worker = c.getString(0); opened = c.getString(1); closed = c.getString(2);
                status = arabicStatus(c.getString(3)); reason = c.getString(4); note = c.getString(5);
            }
        }
        right("وردية رقم " + shiftId + "  •  " + worker + "  •  " + opened, 11, Color.WHITE, false, 54);
        y = 82;
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
        y += 6;
    }

    private void fuelLitres(long shiftId){
        java.util.LinkedHashMap<String,Double> grouped=new java.util.LinkedHashMap<>();
        grouped.put("بترول",0d);grouped.put("ديزل",0d);grouped.put("غاز",0d);
        try(Cursor c=db.shiftReadings(shiftId)){
            while(c.moveToNext()){
                String fuel=c.getString(2).trim();
                if(fuel.equals("البترول")||fuel.equals("بنزين")||fuel.equals("البنزين"))fuel="بترول";
                else if(fuel.equals("الديزل"))fuel="ديزل";
                else if(fuel.equals("الغاز"))fuel="غاز";
                if(!grouped.containsKey(fuel))grouped.put(fuel,0d);
                if(!c.isNull(4)&&c.getDouble(4)>=c.getDouble(3))
                    grouped.put(fuel,grouped.get(fuel)+c.getDouble(4)-c.getDouble(3));
            }
        }
        // Keep the two rows together when the report spans multiple pages.
        ensure(56);
        columns(grouped.keySet().toArray(new String[0]));
        String[] values=new String[grouped.size()];int index=0;
        for(double litres:grouped.values())values[index++]=money(litres)+" لتر";
        row(values);
        y+=6;
    }

    private static final String[] MOVEMENT_TYPES={"EXPENSE","COLLECTION","DEBT","CASH"};
    private static final String[] MOVEMENT_HEADERS={"المخاريج","المقبوضات","الديون","الفلوس","البيان"};

    private void movements(long shiftId){
        java.util.List<String[]> rows=new java.util.ArrayList<>();
        double[] sums=new double[4];
        // Keep rows grouped by type, with the amount in its own column only.
        try(Cursor c=db.syncMovements(shiftId)){
            while(c.moveToNext()){
                String[] values={"","","","",c.getString(1)};
                int column=-1;
                for(int i=0;i<MOVEMENT_TYPES.length;i++)
                    if(MOVEMENT_TYPES[i].equals(c.getString(0))){column=i;break;}
                if(column<0)throw new IllegalStateException("نوع حركة غير معروف: "+c.getString(0));
                values[column]=money(c.getDouble(2));
                sums[column]+=c.getDouble(2);
                rows.add(values);
            }
        }
        java.util.Collections.sort(rows,(left,right)->Integer.compare(movementColumn(left),movementColumn(right)));
        ensure(66);
        section("الحركات — المبالغ بالريال اليمني");
        movementTableRow(MOVEMENT_HEADERS,true);
        if(rows.isEmpty())movementTableRow(new String[]{"","","","","لا توجد حركات مسجلة"},false);
        for(String[] values:rows)movementTableRow(values,false);
        movementTableRow(new String[]{money(sums[0]),money(sums[1]),money(sums[2]),money(sums[3]),"الإجمالي"},true);
        y+=6;
    }

    private static int movementColumn(String[] row){
        for(int i=0;i<4;i++)if(!row[i].isEmpty())return i;
        return 4;
    }

    private void movementTableRow(String[] values,boolean highlighted){
        android.text.StaticLayout[] layouts=new android.text.StaticLayout[5];
        int height=20;
        for(int i=0;i<5;i++){
            int width=i<4?78:WIDTH-MARGIN*2-312;
            android.text.TextPaint cellPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
            cellPaint.setTextSize(10);cellPaint.setColor(Util.NAVY);cellPaint.setFakeBoldText(highlighted);
            String value=values[i]==null?"":values[i];
            layouts[i]=android.text.StaticLayout.Builder.obtain(value,0,value.length(),cellPaint,width-8)
                    .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).build();
            height=Math.max(height,layouts[i].getHeight()+8);
        }
        if(y+height>HEIGHT-MARGIN){
            newPage();
            if(values!=MOVEMENT_HEADERS)movementTableRow(MOVEMENT_HEADERS,true);
        }
        float right=WIDTH-MARGIN;
        int[] colors={0xfffbf3e8,0xffedf5ee,0xfffcf0f0,0xffedf2f8,0xffeef0f2};
        for(int i=0;i<5;i++){
            int width=i<4?78:WIDTH-MARGIN*2-312;
            if(highlighted){
                paint.setStyle(Paint.Style.FILL);paint.setColor(colors[i]);
                canvas.drawRect(right-width,y,right,y+height,paint);
            }
            canvas.save();
            canvas.translate(right-width+4,y+4);
            layouts[i].draw(canvas);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(0.5f);paint.setColor(0xffc8ccd0);
            canvas.drawRect(right-width,y,right,y+height,paint);
            right-=width;
        }
        paint.setStyle(Paint.Style.FILL);
        y+=height;
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
        ensure(30);
        paint.setColor(Util.NAVY);paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(MARGIN, y, WIDTH - MARGIN, y + 26, 6, 6, paint);
        right(name, 13, Util.GOLD, true, y + 18);
        y += 30;
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
        // Shape mixed Arabic/numeric text with an explicit RTL paragraph direction.
        android.text.TextPaint rtlPaint=new android.text.TextPaint(paint);
        rtlPaint.setTextAlign(Paint.Align.LEFT);
        int width=Math.max(1,(int)Math.ceil(rtlPaint.measureText(value))+4);
        android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(value,0,value.length(),rtlPaint,width)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).build();
        float left=align==Paint.Align.RIGHT?x-width:(align==Paint.Align.CENTER?x-width/2f:x);
        canvas.save();
        canvas.translate(left,baseline-layout.getLineBaseline(0));
        layout.draw(canvas);
        canvas.restore();
        paint.setFakeBoldText(false);
    }

    private String money(double value){return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);}
    static String arabicType(String t){return Calc.arabicType(t);}
    static String arabicStatus(String s){return Calc.arabicStatus(s);}
}
