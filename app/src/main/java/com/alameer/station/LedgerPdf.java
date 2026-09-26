package com.alameer.station.shifts;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.CancellationSignal;
import android.print.*;
import android.text.*;
import java.io.*;
import java.util.*;

/** One vector PDF layout for sharing and Android printing, with complete wrapped notes. */
final class LedgerPdf {
 static final int WIDTH=595,HEIGHT=842,MARGIN=32,BOTTOM=790;
 static final int INK=0xff232323,MUTED=0xff666666,GOLD=0xffFFCD11;
 static final int[] WIDTHS={70,215,70,70,106};
 final boolean cash;final int[] widths;
 final LedgerStatement statement;final Bitmap logo;final ArrayList<ArrayList<Block>> pages=new ArrayList<>();
 final StaticLayout station,account,period,legend;final int summaryTop,tableTop,bodyTop;
 final String printedAt=Util.now();
 static final class Block {
  final StaticLayout[] cells;final int height,kind;final double balance;
  Block(String[] text,int kind,double balance){this(text,kind,balance,WIDTHS);}
  Block(String[] text,int kind,double balance,int[] widths){this.kind=kind;this.balance=balance;cells=new StaticLayout[text.length];int h=30;
   for(int i=0;i<text.length;i++){cells[i]=layout(text[i],widths[i]-10,10,kind!=0,INK,widths[0]==75?i<3:i!=1);h=Math.max(h,cells[i].getHeight()+14);}height=h;
  }
 }
 LedgerPdf(Context context,LedgerStatement statement){
  this.statement=statement;cash=statement.source.equals("cashbox_entries");widths=cash?new int[]{75,75,75,180,126}:WIDTHS;logo=Branding.logo(context);station=layout(statement.station,WIDTH-2*MARGIN-48,13,true,INK,false);
  account=layout(statement.name,WIDTH-2*MARGIN,16,true,INK,false);period=layout(statement.period(),WIDTH-2*MARGIN,10,false,MUTED,false);legend=layout(statement.legend+" • الوحدة: "+statement.unit,WIDTH-2*MARGIN,9,false,MUTED,false);
  summaryTop=83+account.getHeight()+period.getHeight()+legend.getHeight()+15;tableTop=summaryTop+66;bodyTop=tableTop+30;
  if(bodyTop>BOTTOM-160)throw new IllegalArgumentException("اسم الحساب طويل جدًا للطباعة؛ اختصر اسمه من الضبط");
  ArrayList<Block> blocks=new ArrayList<>();blocks.add(balanceBlock("رصيد بداية الفترة",statement.opening,1));
  for(LedgerStatement.Row row:statement.rows){
   if(cash){cashBlocks(blocks,row);continue;}
   String text=row.note+(row.code.isEmpty()||row.note.contains(row.code)?"":"\n"+row.code);StaticLayout wrapped=layout(text,WIDTHS[1]-12,10,false,INK,false);
   int line=0;boolean first=true;int maxLines=Math.max(1,Math.min(20,(BOTTOM-bodyTop-85)/14));
   while(line<wrapped.getLineCount()){
    int end=Math.min(wrapped.getLineCount(),line+maxLines);String part=text.substring(wrapped.getLineStart(line),wrapped.getLineEnd(end-1)).trim();
    blocks.add(new Block(new String[]{first?row.date:"",first?part:"تابع البيان: "+part,first&&row.increase!=0?LedgerStatement.number(row.increase):"",first&&row.decrease!=0?LedgerStatement.number(row.decrease):"",first?LedgerStatement.number(row.balance):""},0,row.balance));first=false;line=end;
   }
  }
  if(statement.rows.isEmpty())blocks.add(cash?new Block(new String[]{"","","","لا توجد حركات خلال الفترة المختارة",""},0,statement.opening,widths):new Block(new String[]{"","لا توجد حركات خلال الفترة المختارة","","",""},0,statement.opening));
  blocks.add(cash?new Block(new String[]{LedgerStatement.number(statement.expenses()),LedgerStatement.number(statement.increase),LedgerStatement.number(statement.decrease-statement.expenses()),"إجمالي الفترة",""},2,statement.closing,widths):new Block(new String[]{"","إجمالي الفترة",LedgerStatement.number(statement.increase),LedgerStatement.number(statement.decrease),LedgerStatement.number(statement.closing)},2,statement.closing));
  ArrayList<Block> page=new ArrayList<>();int y=bodyTop;double carried=statement.opening;
  for(Block block:blocks){if(y+block.height>BOTTOM){pages.add(page);page=new ArrayList<>();Block carry=balanceBlock("رصيد منقول",carried,1);page.add(carry);y=bodyTop+carry.height;}
   if(y+block.height>BOTTOM)throw new IllegalArgumentException("تعذر تنسيق إحدى الحركات للطباعة");page.add(block);y+=block.height;carried=block.balance;
  }pages.add(page);
 }
 Block balanceBlock(String label,double value,int kind){return cash?new Block(new String[]{"","","",label,LedgerStatement.number(value)},kind,value,widths):new Block(new String[]{"",label,"","",LedgerStatement.number(value)},kind,value);}
 String[] headings(){return cash?new String[]{"المخاريج","وارد","صادر","البيان","الجهة"}:new String[]{"التاريخ","البيان",statement.source.equals("supplier_entries")?"إضافة":statement.increaseLabel,statement.source.equals("supplier_entries")?"خصم":statement.decreaseLabel,"الرصيد"};}
 void cashBlocks(ArrayList<Block> blocks,LedgerStatement.Row row){
  String[] values={row.expense()==0?"":LedgerStatement.number(row.expense()),row.increase==0?"":LedgerStatement.number(row.increase),row.outgoing()==0?"":LedgerStatement.number(row.outgoing()),row.cash.person+"\n"+(row.expense()>0?"صادر — مخاريج":row.increase>0?"وارد":"صادر"),row.cash.box};
  cashTextBlocks(blocks,values,row.balance);
 }
 void cashTextBlocks(ArrayList<Block> blocks,String[] values,double balance){
  StaticLayout[] wrapped=new StaticLayout[values.length];int lines=1;
  for(int i=0;i<values.length;i++){wrapped[i]=layout(values[i],widths[i]-10,10,false,INK,i<3);lines=Math.max(lines,wrapped[i].getLineCount());}
  int maxLines=Math.max(1,Math.min(20,(BOTTOM-bodyTop-85)/14));
  for(int line=0;line<lines;line+=maxLines){String[] part=new String[values.length];
   for(int i=0;i<values.length;i++){StaticLayout w=wrapped[i];part[i]=line>=w.getLineCount()?"":values[i].substring(w.getLineStart(line),w.getLineEnd(Math.min(w.getLineCount(),line+maxLines)-1)).trim();}
   blocks.add(new Block(part,0,balance,widths));
  }
 }
 static TextPaint paint(int size,boolean bold,int color){TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return p;}
 static StaticLayout layout(String text,int width,int size,boolean bold,int color,boolean numeric){String s=text==null?"":text;return StaticLayout.Builder.obtain(s,0,s.length(),paint(size,bold,color),width).setTextDirection(numeric?TextDirectionHeuristics.LTR:TextDirectionHeuristics.RTL).setAlignment(numeric?Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(2,1).build();}
 static void draw(Canvas c,StaticLayout text,float x,float y){c.save();c.translate(x,y);text.draw(c);c.restore();}
 int pageCount(){return pages.size();}
 void drawPage(Canvas c,int index){
  c.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(GOLD);c.drawRect(MARGIN,20,WIDTH-MARGIN,24,p);
  draw(c,station,MARGIN+48,34);if(logo!=null)c.drawBitmap(logo,null,new RectF(MARGIN,31,MARGIN+34,65),p);
  draw(c,layout("كشف حركة حساب",WIDTH-2*MARGIN,20,true,INK,false),MARGIN,55);
  int y=83;draw(c,account,MARGIN,y);y+=account.getHeight()+5;draw(c,period,MARGIN,y);y+=period.getHeight()+5;draw(c,legend,MARGIN,y);
  String[] labels={"رصيد بداية الفترة",statement.increaseLabel,cash?"الصادر والمخاريج":statement.decreaseLabel,"رصيد نهاية الفترة"};
  String[] values={statement.balanceLabel(statement.opening),LedgerStatement.number(statement.increase),LedgerStatement.number(statement.decrease),statement.balanceLabel(statement.closing)};
  for(int i=0;i<4;i++){int left=WIDTH-MARGIN-(i+1)*134+5;p.setColor(i==3?0xffFFF5CC:0xffF1F1F1);c.drawRoundRect(left,summaryTop,left+128,summaryTop+56,6,6,p);draw(c,layout(labels[i],120,9,false,MUTED,false),left+4,summaryTop+7);draw(c,layout(values[i],120,12,true,INK,true),left+4,summaryTop+25);}
  p.setColor(0xffE9E9E9);c.drawRect(MARGIN,tableTop,WIDTH-MARGIN,bodyTop,p);
  String[] headings=headings();int right=WIDTH-MARGIN;
  for(int i=0;i<widths.length;i++){right-=widths[i];draw(c,layout(headings[i],widths[i]-10,9,true,INK,false),right+6,tableTop+7);}
  y=bodyTop;int stripe=0;
  for(Block block:pages.get(index)){
   if(block.kind!=0||stripe%2==1){p.setColor(block.kind==2?0xffFFF5CC:block.kind==1?0xffF0F0F0:0xffFAFAFA);c.drawRect(MARGIN,y,WIDTH-MARGIN,y+block.height,p);}
   right=WIDTH-MARGIN;for(int i=0;i<widths.length;i++){right-=widths[i];draw(c,block.cells[i],right+6,y+7);}
   y+=block.height;p.setColor(0xffD8D8D8);p.setStrokeWidth(0.5f);c.drawLine(MARGIN,y,WIDTH-MARGIN,y,p);stripe++;
  }
  p.setColor(0xffD0D0D0);c.drawLine(MARGIN,803,WIDTH-MARGIN,803,p);
  draw(c,layout("صفحة "+(index+1)+" / "+pages.size()+" • "+statement.rows.size()+" حركة • طبع في "+printedAt,WIDTH-2*MARGIN,8,false,MUTED,false),MARGIN,809);
  draw(c,layout(Branding.CREDIT,WIDTH-2*MARGIN,8,false,MUTED,false),MARGIN,823);
 }
 File build(Context context,CancellationSignal cancellation)throws IOException{
  File dir=new File(context.getCacheDir(),"exports");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("تعذر إنشاء مجلد التقارير");File file=File.createTempFile("ledger-statement-",".pdf",dir);
  try(FileOutputStream out=new FileOutputStream(file)){write(out,new PageRange[]{PageRange.ALL_PAGES},cancellation,null);return file;}catch(IOException|RuntimeException e){file.delete();throw e;}
 }
 PageRange[] write(OutputStream out,PageRange[] requested,CancellationSignal cancellation,PrintAttributes attributes)throws IOException{
  PdfDocument doc=new PdfDocument();ArrayList<PageRange> written=new ArrayList<>();
  try{
   for(int i=0;i<pages.size();i++){if(!includes(requested,i))continue;cancellation.throwIfCanceled();
    int width=WIDTH,height=HEIGHT,left=0,top=0,right=0,bottom=0;
    if(attributes!=null&&attributes.getMediaSize()!=null){width=Math.max(1,Math.round(attributes.getMediaSize().getWidthMils()*72f/1000));height=Math.max(1,Math.round(attributes.getMediaSize().getHeightMils()*72f/1000));PrintAttributes.Margins margins=attributes.getMinMargins();if(margins!=null){left=Math.round(margins.getLeftMils()*72f/1000);top=Math.round(margins.getTopMils()*72f/1000);right=Math.round(margins.getRightMils()*72f/1000);bottom=Math.round(margins.getBottomMils()*72f/1000);}}
    if(width<=left+right||height<=top+bottom)throw new IOException("هوامش الطباعة لا تترك مساحة للكشف");
    PdfDocument.Page sheet=doc.startPage(new PdfDocument.PageInfo.Builder(width,height,i+1).create());Canvas canvas=sheet.getCanvas();
    float scale=Math.min((width-left-right)/(float)WIDTH,(height-top-bottom)/(float)HEIGHT);canvas.save();canvas.translate(left+(width-left-right-WIDTH*scale)/2,top+(height-top-bottom-HEIGHT*scale)/2);canvas.scale(scale,scale);
    try{drawPage(canvas,i);}finally{canvas.restore();doc.finishPage(sheet);}written.add(new PageRange(i,i));
   }
   cancellation.throwIfCanceled();if(written.isEmpty())throw new IOException("اختر صفحة من الكشف للطباعة");doc.writeTo(out);cancellation.throwIfCanceled();return written.toArray(new PageRange[0]);
  }finally{doc.close();}
 }
 static boolean includes(PageRange[] ranges,int page){for(PageRange range:ranges)if(range.getStart()<=page&&range.getEnd()>=page)return true;return false;}
}
