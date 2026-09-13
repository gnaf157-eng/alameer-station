package com.alameer.station.shifts;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import java.io.*;

public final class PdfReport {
    private final Context context;private final Db db;
    private static final int WIDTH=842,HEIGHT=595,MARGIN=24,COLUMN=(WIDTH-2*MARGIN)/5;
    public PdfReport(Context context,Db db){this.context=context;this.db=db;}
    public File build(long id)throws Exception{
        ReportTable table=new ReportTable(db,id);
        File dir=new File(context.getCacheDir(),"exports");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("تعذر إنشاء مجلد التقرير");
        File file=new File(dir,"alameer-shift-"+id+"-"+System.currentTimeMillis()+".pdf");
        try(PdfDocument document=new PdfDocument()){
            PdfDocument.Page page=null;Canvas canvas=null;int y=MARGIN,pageNo=0;
            for(int i=0;i<table.rows.size();i++){
                ReportTable.Row row=table.rows.get(i);
                StaticLayout[] layouts=layouts(row);int height=height(layouts);
                int needed=height;
                if(i==table.totalsStart-1){
                    for(int j=i+1;j<table.rows.size();j++)needed+=height(layouts(table.rows.get(j)));
                }
                if(i==table.movementHeader&&i+1<table.rows.size())needed+=height(layouts(table.rows.get(i+1)));
                if(page==null||y+needed>HEIGHT-MARGIN){
                    if(page!=null)document.finishPage(page);
                    page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++pageNo).create());
                    canvas=page.getCanvas();y=MARGIN;
                    if(i>table.movementHeader){
                        ReportTable.Row heading=table.rows.get(table.movementHeader);
                        StaticLayout[] h=layouts(heading);draw(canvas,h,heading.heading,y);y+=height(h);
                    }
                    Paint footer=new Paint(Paint.ANTI_ALIAS_FLAG);footer.setTextSize(8);footer.setColor(0xff666666);
                    canvas.drawText("#"+id+"   "+pageNo,MARGIN,HEIGHT-10,footer);
                }
                draw(canvas,layouts,row.heading,y);y+=height;
            }
            if(page!=null)document.finishPage(page);
            try(FileOutputStream out=new FileOutputStream(file)){document.writeTo(out);}
        }
        return file;
    }
    private static StaticLayout[] layouts(ReportTable.Row row){
        StaticLayout[] result=new StaticLayout[5];
        for(int i=0;i<5;i++){
            Object value=i<row.cells.length?row.cells[i]:null;
            if(value instanceof XlsxWorkbook.Formula)value=((XlsxWorkbook.Formula)value).value;
            String text=value==null?"":value instanceof Number?ReportTable.format(((Number)value).doubleValue()):value.toString();
            TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(10);
            paint.setTypeface(Typeface.create("sans-serif",row.heading?Typeface.BOLD:Typeface.NORMAL));
            paint.setColor(0xff252a2e);
            result[i]=StaticLayout.Builder.obtain(text,0,text.length(),paint,COLUMN-8)
                .setTextDirection(value instanceof Number?TextDirectionHeuristics.LTR:TextDirectionHeuristics.RTL)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
        }
        return result;
    }
    private static int height(StaticLayout[] cells){
        int h=18;for(StaticLayout c:cells)h=Math.max(h,c.getHeight()+6);return h;
    }
    private static void draw(Canvas canvas,StaticLayout[] cells,boolean heading,int y){
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int h=height(cells);
        for(int i=0;i<5;i++){
            int right=WIDTH-MARGIN-i*COLUMN,left=right-COLUMN;
            if(heading){paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffe99a);canvas.drawRect(left,y,right,y+h,paint);}
            canvas.save();canvas.translate(left+4,y+(h-cells[i].getHeight())/2f);cells[i].draw(canvas);canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(0.5f);paint.setColor(0xffcccccc);canvas.drawRect(left,y,right,y+h,paint);
        }
    }
    static String arabicType(String t){return Calc.arabicType(t);}
    static String arabicStatus(String s){return Calc.arabicStatus(s);}
}
