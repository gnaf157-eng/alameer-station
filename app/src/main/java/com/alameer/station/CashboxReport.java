package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;

import java.io.File;

/**
 * تقرير حركة الصناديق في ملف Excel واحد حسب المدى التاريخي.
 * الأعمدة من اليمين إلى اليسار: المخاريج، وارد، صادر، البيان، الجهة.
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

        // خمسة أعمدة فقط؛ معلومات الفترة أعلى الجدول والتفاصيل أسفله.
        book.row(true, "المخاريج", "وارد", "صادر", "البيان", "الجهة");
        book.row(false,"", "", "", "المبالغ بالريال اليمني", "");

        int first = book.nextRow();
        int rows = 0;
        double totalIn = 0, totalOut = 0, totalExpense=0;


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

                CashReportDetails detail=CashReportDetails.load(db,c.getLong(6));
                boolean expense=!in&&detail.expense;
                book.row(false,expense?amount:null,in?amount:null,!in&&!expense?amount:null,
                        detail.person+"\n"+(expense?"صادر — مخاريج":in?"وارد":"صادر"),box);
                rows++;
                if(in)totalIn+=amount;else if(expense)totalExpense+=amount;else totalOut+=amount;
            }
        }

        if (rows == 0) throw new IllegalStateException("لا توجد حركات في هذا المدى.");

        int last = book.nextRow() - 1;
        book.row(true,new XlsxWorkbook.Formula("SUM(A"+first+":A"+last+")",totalExpense),
                new XlsxWorkbook.Formula("SUM(B"+first+":B"+last+")",totalIn),
                new XlsxWorkbook.Formula("SUM(C"+first+":C"+last+")",totalOut),"الإجمالي",rows+" حركة");
        book.row(true,"","","","صافي الحركة",new XlsxWorkbook.Formula("B"+(last+1)+"-C"+(last+1)+"-A"+(last+1),totalIn-totalOut-totalExpense));
        book.row(false, "", "", "", Branding.CREDIT, "");

        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.isDirectory() && !dir.mkdirs())
            throw new java.io.IOException("تعذر إنشاء مجلد التقرير");
        File file = new File(dir, "cashboxes-" + from + "_" + to + ".xlsx");
        book.write(file);
        return file;
    }
}
