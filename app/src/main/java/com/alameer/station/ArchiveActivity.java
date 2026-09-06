package com.alameer.station.shifts;

import android.app.*;import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;import android.widget.*;import androidx.core.content.FileProvider;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;

public class ArchiveActivity extends Activity {
    private Db db;private int workerId;private boolean admin;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);db=new Db(this);
        workerId=getIntent().getIntExtra("workerId",0);admin=getIntent().getBooleanExtra("admin",false);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        root.setPadding(18,24,18,40);root.setBackgroundColor(Util.BG);
        root.addView(Util.title(this,admin?"أرشيف جميع الورديات":"أرشيف وردياتي"),Util.spaced());
        Button export=Util.goldButton(this,"تصدير الأرشيف (Excel / CSV)");
        export.setOnClickListener(v->export());
        root.addView(export,Util.spaced());
        try(Cursor c=db.archive(workerId,admin)){
            if(c.getCount()==0)root.addView(Util.card(this,"لا توجد ورديات محفوظة بعد."),Util.spaced());
            while(c.moveToNext()){
                double bal=c.getDouble(5);String note=c.getString(7);
                TextView card=Util.card(this,"وردية #"+c.getLong(0)+" — "+c.getString(1)+"\n"+c.getString(2)+"\nالحالة: "+status(c.getString(3))+" | المبيعات: "+fmt(c.getDouble(4))+"\nالباقي: "+fmt(bal)+" | المزامنة: "+sync(c.getString(6))+(note==null||note.isEmpty()?"":"\nملاحظة المدير: "+note));
                card.setTextColor(Math.abs(bal)<0.01?Util.GREEN:Util.RED);
                final long shiftId=c.getLong(0);
                card.setClickable(true);
                card.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("وردية #"+shiftId).setMessage("أخرج تقرير الوردية بصيغة PDF جاهزة للطباعة أو المشاركة.").setPositiveButton("تقرير PDF",(d,w)->exportPdf(shiftId)).setNegativeButton("إغلاق",null).show());
                root.addView(card,Util.spaced());
            }
        }
        scroll.addView(root);setContentView(scroll);
    }
    private void export(){
        try{
            StringBuilder sb=new StringBuilder("\ufeff");
            sb.append("رقم الوردية,العامل,وقت الفتح,وقت الإغلاق,الحالة,المبيعات,المقبوضات,النقد المسلّم,الديون,المخاريج,الباقي,سبب الفرق,ملاحظة المدير,المزامنة\n");
            int rows=0;
            try(Cursor c=db.exportShifts(workerId,admin)){
                while(c.moveToNext()){
                    rows++;
                    sb.append(cell(String.valueOf(c.getLong(0)))).append(',').append(cell(c.getString(1))).append(',')
                      .append(cell(c.getString(2))).append(',').append(cell(c.getString(3))).append(',')
                      .append(cell(status(c.getString(4)))).append(',');
                    for(int i=5;i<=10;i++)sb.append(cell(fmt(c.getDouble(i)))).append(',');
                    sb.append(cell(c.getString(11))).append(',').append(cell(c.getString(12))).append(',').append(cell(sync(c.getString(13)))).append('\n');
                }
            }
            if(rows==0){Toast.makeText(this,"لا توجد ورديات لتصديرها.",Toast.LENGTH_LONG).show();return;}
            File dir=new File(getCacheDir(),"exports");dir.mkdirs();
            File file=new File(dir,"alameer-shifts-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".csv");
            try(OutputStream out=new FileOutputStream(file)){out.write(sb.toString().getBytes(StandardCharsets.UTF_8));}
            share(file,"text/csv","أرشيف ورديات محطة الأمير","تصدير الأرشيف");
        }catch(Exception e){Toast.makeText(this,"تعذر تصدير الأرشيف",Toast.LENGTH_LONG).show();}
    }
    private void exportPdf(long shiftId){
        try{
            File file=new PdfReport(this,db).build(shiftId);
            share(file,"application/pdf","تقرير وردية #"+shiftId,"مشاركة تقرير الوردية");
        }catch(Exception e){Toast.makeText(this,"تعذر إنشاء تقرير PDF",Toast.LENGTH_LONG).show();}
    }
    private void share(File file,String mime,String subject,String chooser){
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
        Intent intent=new Intent(Intent.ACTION_SEND);intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM,uri);intent.putExtra(Intent.EXTRA_SUBJECT,subject);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent,chooser));
    }
    private String cell(String value){if(value==null)value="";return "\""+value.replace("\"","\"\"").replace("\n"," ")+"\"";}
    private String status(String s){return PdfReport.arabicStatus(s);}
    private String sync(String s){return "LOCAL".equals(s)?"محلي":"PENDING".equals(s)?"بانتظار الإنترنت":"تمت";}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
}
