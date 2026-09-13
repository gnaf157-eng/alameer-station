package com.alameer.station.shifts;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import java.io.*;

/** Print-friendly report with restrained dividers, separate from the Excel styling. */
public final class PdfReport {
    private final Context context;private final Db db;
    private static final int WIDTH=842,HEIGHT=595,MARGIN=28,COLUMN=(WIDTH-2*MARGIN)/5;
    private static final int INK=0xff252a2e,MUTED=0xff667078,GOLD=0xffe6b900;
    public PdfReport(Context context,Db db){this.context=context;this.db=db;}
    private static final class Block{
        final ReportTable.Row row;final StaticLayout[] cells;final int kind,height;
        Block(ReportTable.Row row,int kind){
            this.row=row;this.kind=kind;this.cells=layouts(row,kind);
            int h=kind==1?56:kind==2?46:kind==3?30:22;
            for(StaticLayout c:cells)h=Math.max(h,c.getHeight()+(kind==1?22:10));
            height=h;
        }
    }
    public File build(long id)throws Exception{
        ReportTable table=new ReportTable(db,id);
        java.util.List<Block> blocks=new java.util.ArrayList<>();
        for(int i=0;i<table.rows.size();i++){
            ReportTable.Row row=table.rows.get(i);
            int kind=i==0?1:"الباقي".equals(row.cells[0])?2:row.heading?3:0;
            blocks.add(new Block(row,kind));
        }
        File dir=new File(context.getCacheDir(),"exports");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("تعذر إنشاء مجلد التقرير");
        File file=new File(dir,"alameer-shift-"+id+"-"+System.currentTimeMillis()+".pdf");
        PdfDocument document=new PdfDocument();
        try{
            PdfDocument.Page page=null;Canvas canvas=null;int y=MARGIN,pageNo=0;
            for(int i=0;i<blocks.size();i++){
                Block block=blocks.get(i);int needed=block.height;
                if(i==table.totalsStart-1){
                    for(int j=i+1;j<blocks.size();j++)needed+=blocks.get(j).height;
                }else if(block.kind==3&&i+1<blocks.size()){
                    needed+=blocks.get(i+1).height;
                }
                if(page==null||y+needed>HEIGHT-MARGIN){
                    if(page!=null)document.finishPage(page);
                    page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++pageNo).create());
                    canvas=page.getCanvas();y=MARGIN;
                    if(pageNo>1){
                        TextPaint label=paint(10,true,MUTED);
                        canvas.drawText("ALAMEER  /  #"+id,MARGIN,y+10,label);y+=22;
                    }
                    if(i>table.movementHeader&&i<table.totalsStart){
                        Block heading=blocks.get(table.movementHeader);draw(canvas,heading,y);y+=heading.height;
                    }
                    TextPaint footer=paint(8,false,MUTED);
                    canvas.drawText("#"+id+"   /   "+pageNo,MARGIN,HEIGHT-12,footer);
                }
                draw(canvas,block,y);y+=block.height;
            }
            if(page!=null)document.finishPage(page);
            try(FileOutputStream out=new FileOutputStream(file)){document.writeTo(out);}
        }finally{document.close();}
        return file;
    }
    private static TextPaint paint(int size,boolean bold,int color){
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);p.setColor(color);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return p;
    }
    private static StaticLayout[] layouts(ReportTable.Row row,int kind){
        StaticLayout[] result=new StaticLayout[kind==1?1:5];
        for(int i=0;i<result.length;i++){
            Object value=i<row.cells.length?row.cells[i]:null;
            if(value instanceof XlsxWorkbook.Formula)value=((XlsxWorkbook.Formula)value).value;
            String text=value==null?"":value instanceof Number?ReportTable.format(((Number)value).doubleValue()):value.toString();
            int size=kind==1?21:kind==2&&i==1?20:kind==2?12:10;
            int color=kind==1?Color.WHITE:kind==2?balanceColor(row):kind==3?INK:value instanceof Number?INK:MUTED;
            TextPaint p=paint(size,kind!=0,color);
            result[i]=StaticLayout.Builder.obtain(text,0,text.length(),p,(kind==1?COLUMN*5:COLUMN)-20)
                .setTextDirection(value instanceof Number?TextDirectionHeuristics.LTR:TextDirectionHeuristics.RTL)
                .setAlignment(kind==1?Layout.Alignment.ALIGN_NORMAL:Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false).build();
        }
        return result;
    }
    private static int balanceColor(ReportTable.Row row){
        Object v=row.cells[1];
        double n=v instanceof XlsxWorkbook.Formula?((XlsxWorkbook.Formula)v).value:v instanceof Number?((Number)v).doubleValue():0;
        return Math.abs(n)<0.01?0xff286342:0xffa34132;
    }
    private static void draw(Canvas canvas,Block b,int y){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        int left=WIDTH-MARGIN-COLUMN*5,right=WIDTH-MARGIN;
        if(b.kind==1){
            p.setColor(INK);canvas.drawRoundRect(left,y,right,y+b.height-6,9,9,p);
            p.setColor(GOLD);canvas.drawRoundRect(right-7,y+10,right-3,y+b.height-16,2,2,p);
        }else if(b.kind==2){
            p.setColor(balanceColor(b.row)==0xff286342?0xffedf5ef:0xfffcf0ed);
            canvas.drawRoundRect(left,y+4,right,y+b.height-2,8,8,p);
        }else if(b.kind==3){
            p.setColor(0xfff3f4f4);
            canvas.drawRoundRect(left,y+4,right,y+b.height-2,5,5,p);
            p.setColor(GOLD);canvas.drawRect(right-3,y+8,right,y+b.height-6,p);
        }
        for(int i=0;i<b.cells.length;i++){
            int width=b.kind==1?COLUMN*5:COLUMN;
            int cellLeft=right-i*width-width;
            canvas.save();canvas.translate(cellLeft+10,y+(b.height-b.cells[i].getHeight())/2f);
            b.cells[i].draw(canvas);canvas.restore();
        }
        if(b.kind==0){
            p.setColor(0xffe9ecee);p.setStrokeWidth(0.35f);
            canvas.drawLine(left+8,y+b.height,right-8,y+b.height,p);
        }
    }
    static String arabicType(String t){return Calc.arabicType(t);}
    static String arabicStatus(String s){return Calc.arabicStatus(s);}
}
