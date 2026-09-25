package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;

import java.io.File;

/**
 * تقرير حركة الصناديق في ملف Excel واحد حسب المدى التاريخي.
 * الأعمدة من اليمين إلى اليسار كما تُقرأ: وارد، صادر، البيان، اسم الصندوق.
 */
public final class CashboxReport {
    private final Context context;
    private final Db db;

    public CashboxReport(Context context, Db db) { this.context = context; this.db = db; }

    /** يبني الملف ويعيده. يرمي استثناءً إن لم توجد حركات في المدى. */
    public File build(String from, String to, long boxId, String boxName) throws Exception {
        XlsxWorkbook book = new XlsxWorkbook();
        book.mergeTitle();

        book.row(true, Branding.stationName(db) + " — حركة الصناديق", "", "", "", "");
        book.row(false, "من تاريخ", from, "إلى تاريخ", to, "");
        book.row(false, "الصندوق", boxId > 0 ? boxName : "كل الصناديق",
                "تاريخ الطباعة", ShiftDates.today(), "");

        // ترويسة الجدول: التاريخ ثم الأعمدة الأربعة المطلوبة.
        book.row(true, "المخاريج", "وارد", "صادر", "البيان", "الجهة", "التاريخ", "نوع الحركة", "ملاحظات", "المبلغ الأصلي", "العملة");
        book.row(false,"المبالغ في الأعمدة الثلاثة بالريال اليمني", "", "", "", "");

        int first = book.nextRow();
        int rows = 0;
        double totalIn = 0, totalOut = 0, totalExpense=0;
        String lastDate = "";

        try (Cursor c = db.cashboxRange(from, to, boxId)) {
            while (c.moveToNext()) {
                String date = c.getString(0);
                boolean in = "IN".equals(c.getString(1));
                double amount = c.getDouble(2);
                String note = c.getString(3);
                String box = c.getString(4);
                long shift = c.getLong(5);

                if (note == null) note = "";
                if (note.trim().isEmpty()) note = in ? "إيداع" : "صرف";
                if (shift > 0) {
                    String code = db.shiftCode(shift);
                    if (!code.isEmpty() && !note.contains(code)) note = note + "  [" + code + "]";
                }

                // التاريخ يُكتب مرة واحدة لكل يوم فيسهل تتبّع الأيام.
                String shown = date.equals(lastDate) ? "" : date;
                lastDate = date;

                CashReportDetails detail=CashReportDetails.load(db,c.getLong(6));
                boolean expense=!in&&detail.expense;
                double original=c.getDouble(8)!=0?c.getDouble(8):amount/(c.getDouble(9)>0?c.getDouble(9):1);
                book.row(false,expense?amount:null,in?amount:null,!in&&!expense?amount:null,
                        detail.person,box,shown,in?"وارد":expense?"صادر — مخاريج":"صادر",note,original,Db.currencyName(c.getString(7)));
                rows++;
                if(in)totalIn+=amount;else if(expense)totalExpense+=amount;else totalOut+=amount;
            }
        }

        if (rows == 0) throw new IllegalStateException("لا توجد حركات في هذا المدى.");

        int last = book.nextRow() - 1;
        book.row(true,new XlsxWorkbook.Formula("SUM(A"+first+":A"+last+")",totalExpense),
                new XlsxWorkbook.Formula("SUM(B"+first+":B"+last+")",totalIn),
                new XlsxWorkbook.Formula("SUM(C"+first+":C"+last+")",totalOut),"الإجمالي",rows+" حركة");
        book.row(true,"الصافي",new XlsxWorkbook.Formula("B"+(last+1)+"-C"+(last+1)+"-A"+(last+1),totalIn-totalOut-totalExpense),"","وارد ناقص صادر ومخاريج","");
        book.row(false, Branding.CREDIT, "", "", "", "");

        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.isDirectory() && !dir.mkdirs())
            throw new java.io.IOException("تعذر إنشاء مجلد التقرير");
        File file = new File(dir, "cashboxes-" + from + "_" + to + ".xlsx");
        book.write(file);
        return file;
    }
}
