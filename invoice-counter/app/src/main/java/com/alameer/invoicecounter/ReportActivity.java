package com.alameer.invoicecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReportActivity extends Activity {
    Db db;
    String from="",to="";
    String order="qty";
    final String[] orderLabels={"بالكمية","بالقيمة","أبجدي"};
    final String[] orderVals={"qty","amount","name"};
    EditText fromField,toField,searchField;
    LinearLayout resultsBox;
    TextView summaryText;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Util.installCrashReporter(this);
        db=new Db(this);
        to=Util.today();
        from=to.substring(0,8)+"01";
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.addView(Util.brandBar(this,"تقرير الأصناف","كم اشتريت من كل صنف خلال الفترة",v->finish()));
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Util.dp(this,14),Util.dp(this,8),Util.dp(this,14),Util.dp(this,16));

        LinearLayout range=Util.panel(this,Color.WHITE);
        range.addView(Util.text(this,"الفترة",16,Util.NAVY,true),Util.spaced(this));
        LinearLayout drow=new LinearLayout(this);
        drow.setGravity(Gravity.CENTER_VERTICAL);
        drow.addView(Util.text(this,"من",14,Util.MUTED,false),new LinearLayout.LayoutParams(-2,-2));
        fromField=new EditText(this);
        Util.styleInput(fromField,this);
        fromField.setText(Util.fmtDate(from));
        fromField.setTextDirection(View.TEXT_DIRECTION_LTR);
        fromField.setFocusable(false);
        fromField.setOnClickListener(v->pickDate(true));
        drow.addView(fromField,new LinearLayout.LayoutParams(0,-2,1));
        drow.addView(Util.text(this,"إلى",14,Util.MUTED,false),new LinearLayout.LayoutParams(-2,-2));
        toField=new EditText(this);
        Util.styleInput(toField,this);
        toField.setText(Util.fmtDate(to));
        toField.setTextDirection(View.TEXT_DIRECTION_LTR);
        toField.setFocusable(false);
        toField.setOnClickListener(v->pickDate(false));
        drow.addView(toField,new LinearLayout.LayoutParams(0,-2,1));
        range.addView(drow,Util.spaced(this));
        LinearLayout chips=new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        String[] chipLabels={"هذا الشهر","آخر 30 يوم","كل الفترة"};
        for(int i=0;i<3;i++){
            final int ci=i;
            Button chip=Util.button(this,chipLabels[i]);
            chip.setTextSize(12);
            chip.setMinHeight(Util.dp(this,42));
            chip.setPadding(Util.dp(this,10),0,Util.dp(this,10),0);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);
            cp.setMargins(Util.dp(this,4),0,0,0);
            chips.addView(chip,cp);
            chip.setOnClickListener(v->applyChip(ci));
        }
        range.addView(chips,Util.spaced(this));
        LinearLayout orderRow=new LinearLayout(this);
        orderRow.setGravity(Gravity.CENTER_VERTICAL);
        orderRow.addView(Util.text(this,"ترتيب:",12,Util.MUTED,false),new LinearLayout.LayoutParams(-2,-2));
        for(int i=0;i<3;i++){
            final int oi=i;
            Button chip=Util.button(this,orderLabels[i]);
            chip.setTextSize(12);
            chip.setMinHeight(Util.dp(this,42));
            chip.setPadding(Util.dp(this,10),0,Util.dp(this,10),0);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);
            cp.setMargins(Util.dp(this,4),0,0,0);
            orderRow.addView(chip,cp);
            chip.setOnClickListener(v->applyOrder(oi));
        }
        range.addView(orderRow);
        content.addView(range,Util.spaced(this));

        LinearLayout searchPanel=Util.panel(this,Color.WHITE);
        searchField=new EditText(this);
        Util.styleInput(searchField,this);
        searchField.setHint("ابحث باسم صنف (اختياري)");
        searchField.setHintTextColor(0xff9aa0a6);
        searchField.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(Editable s){load();}
        });
        searchPanel.addView(searchField,Util.spaced(this));
        Button export=Util.goldButton(this,"⬇  تصدير التقرير (Excel / CSV)");
        export.setOnClickListener(v->exportCsv());
        searchPanel.addView(export);
        content.addView(searchPanel,Util.spaced(this));

        summaryText=Util.text(this,"",14,Util.MUTED,false);
        content.addView(summaryText,Util.spaced(this));
        resultsBox=new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(resultsBox);

        scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        load();
    }

    private void applyOrder(int i){
        order=orderVals[i];
        load();
    }

    private void applyChip(int ci){
        if(ci==0)from=to.substring(0,8)+"01";
        else if(ci==1)from=addDays(to,-30);
        else from="";
        to=Util.today();
        fromField.setText(from.isEmpty()?"من البداية":Util.fmtDate(from));
        toField.setText(Util.fmtDate(to));
        load();
    }

    private void pickDate(boolean isFrom){
        String base=isFrom?(from.isEmpty()?to:from):to;
        int y,m,d;
        try{
            y=Integer.parseInt(base.substring(0,4));
            m=Integer.parseInt(base.substring(5,7))-1;
            d=Integer.parseInt(base.substring(8,10));
        }catch(Exception e){
            Calendar c=Calendar.getInstance();
            y=c.get(Calendar.YEAR);m=c.get(Calendar.MONTH);d=c.get(Calendar.DAY_OF_MONTH);
        }
        new DatePickerDialog(this,(v,yy,mm,dd)->{
            String iso=String.format(Locale.US,"%04d-%02d-%02d",yy,mm+1,dd);
            if(isFrom)from=iso;else to=iso;
            fromField.setText(from.isEmpty()?"من البداية":Util.fmtDate(from));
            toField.setText(Util.fmtDate(to));
            load();
        },y,m,d).show();
    }

    private String addDays(String iso,int delta){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        try{
            Calendar c=Calendar.getInstance();
            c.setTime(f.parse(iso));
            c.add(Calendar.DAY_OF_MONTH,delta);
            return f.format(c.getTime());
        }catch(Exception e){return iso;}
    }

    private void load(){
        String filter=searchField.getText().toString().trim();
        try(Cursor c=db.rangeSummary(from,to)){
            if(c.moveToFirst()){
                summaryText.setText(c.getInt(0)+" فاتورة   •   "+Util.qty(c.getDouble(1))+" سطر صنف   •   إجمالي "+Util.money(c.getDouble(2))+" ج.م");
            }else summaryText.setText("");
        }
        resultsBox.removeAllViews();
        int rows=0;
        try(Cursor c=db.report(from,to,filter,order)){
            while(c.moveToNext()){
                rows++;
                final String name=c.getString(0);
                final double qty=c.getDouble(1);
                final double amount=c.getDouble(2);
                final int invCount=c.getInt(3);
                String unit=db.unitOf(name,from,to);
                LinearLayout row=Util.panel(this,Color.WHITE);
                row.setOrientation(LinearLayout.VERTICAL);
                row.addView(Util.text(this,name,16,Util.NAVY,true));
                row.addView(Util.text(this,Util.qty(qty)+(unit.isEmpty()?"":" "+unit)+"   •   "+Util.money(amount)+" ج.م   •   "+invCount+" فاتورة",13,Util.MUTED,false));
                row.setOnClickListener(v->showDetail(name));
                resultsBox.addView(row,Util.spaced(this));
            }
        }
        if(rows==0){
            TextView empty=Util.text(this,"لا توجد أصناف في هذه الفترة",14,Util.MUTED,false);
            empty.setPadding(0,Util.dp(this,10),0,Util.dp(this,10));
            resultsBox.addView(empty);
        }
    }

    private void showDetail(String name){
        StringBuilder sb=new StringBuilder();
        double q=0,a=0;
        String unit="";
        try(Cursor c=db.itemDetail(name,from,to)){
            while(c.moveToNext()){
                String d=Util.fmtDate(c.getString(0));
                double qty=c.getDouble(1);
                String u=c.getString(2);
                double line=c.getDouble(3);
                q+=qty;
                a+=line;
                if(!u.isEmpty())unit=u;
                sb.append(d).append(":  ").append(Util.qty(qty)).append(u.isEmpty()?"":" "+u)
                  .append("   (").append(Util.money(line)).append(" ج.م)\n");
            }
        }
        sb.append("———————————\nالإجمالي: ").append(Util.qty(q)).append(unit.isEmpty()?"":" "+unit)
          .append("   (").append(Util.money(a)).append(" ج.م)");
        new AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(sb.toString())
            .setPositiveButton("إغلاق",null)
            .show();
    }

    private void exportCsv(){
        File dir=getExternalFilesDir(null);
        if(dir==null){Toast.makeText(this,"لا يوجد تخزين متاح",Toast.LENGTH_SHORT).show();return;}
        String fname="report_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm",Locale.US).format(new Date())+".csv";
        File out=new File(dir,fname);
        try{
            Writer w=new OutputStreamWriter(new FileOutputStream(out),StandardCharsets.UTF_8);
            w.write("\uFEFF");
            w.write("الصنف,الوحدة,إجمالي الكمية,إجمالي المبلغ (ج.م),عدد الفواتير\n");
            try(Cursor c=db.report(from,to,searchField.getText().toString().trim(),order)){
                while(c.moveToNext()){
                    String name=c.getString(0).replace("\"","\"\"");
                    w.write("\""+name+"\"");
                    w.write(","+db.unitOf(c.getString(0),from,to));
                    w.write(","+Util.qty(c.getDouble(1)));
                    w.write(","+Util.money(c.getDouble(2)));
                    w.write(","+c.getInt(3)+"\n");
                }
            }
            w.close();
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/csv");
            send.putExtra(Intent.EXTRA_STREAM,AppFileProvider.uriFor(out));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.putExtra(Intent.EXTRA_SUBJECT,"تقرير الأصناف");
            startActivity(Intent.createChooser(send,"إرسال التقرير (Excel / Drive / واتساب)"));
            Toast.makeText(this,"اختر أين تريد إرسال الملف",Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,"فشل التصدير: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }
}
