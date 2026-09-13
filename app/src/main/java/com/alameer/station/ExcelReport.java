package com.alameer.station.shifts;
import android.content.Context;
import java.io.File;
public final class ExcelReport {
    private final Context context;private final Db db;
    public ExcelReport(Context context,Db db){this.context=context;this.db=db;}
    public File build(long id)throws Exception{
        ReportTable table=new ReportTable(db,id);
        XlsxWorkbook book=new XlsxWorkbook();book.mergeTitle();
        for(ReportTable.Row row:table.rows)book.row(row.heading,row.cells);
        File dir=new File(context.getCacheDir(),"exports");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("تعذر إنشاء مجلد التقرير");
        File file=new File(dir,"alameer-shift-"+id+"-"+System.currentTimeMillis()+".xlsx");
        book.write(file);return file;
    }
}
