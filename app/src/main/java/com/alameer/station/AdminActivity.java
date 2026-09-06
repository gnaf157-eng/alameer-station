package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;import java.util.*;

public class AdminActivity extends Activity {
    Db db;LinearLayout content;
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);if("approvals".equals(getIntent().getStringExtra("screen")))approvals();else home();}
    private void shell(String title){ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(18,24,18,50);content.setBackgroundColor(Util.BG);content.addView(Util.title(this,title),Util.spaced());scroll.addView(content);setContentView(scroll);}
    private void home(){shell("إعدادات المدير");Button workers=Util.button(this,"العمال ورموز الدخول");workers.setOnClickListener(v->workers());content.addView(workers,Util.spaced());Button pumps=Util.button(this,"الطرمبات والأسعار والقراءات");pumps.setOnClickListener(v->pumps());content.addView(pumps,Util.spaced());int n=db.pendingCount();Button approvals=Util.goldButton(this,"الورديات بانتظار الاعتماد"+(n>0?" ("+n+")":""));approvals.setOnClickListener(v->approvals());content.addView(approvals,Util.spaced());Button sync=Util.button(this,"مزامنة الآن مع Google Sheets");sync.setOnClickListener(v->new Sync(this).run(true));content.addView(sync,Util.spaced());content.addView(Util.card(this,"ابدأ بإدخال الأسعار والقراءات الافتتاحية قبل تشغيل أول وردية حقيقية."),Util.spaced());}
    private void back(){Button b=Util.button(this,"رجوع");b.setOnClickListener(v->home());content.addView(b,Util.spaced());}
    private void workers(){shell("إعداد العمال");
        Button add=Util.goldButton(this,"＋ إضافة عامل جديد");add.setOnClickListener(v->addWorkerDialog());content.addView(add,Util.spaced());
        try(Cursor c=db.workers()){while(c.moveToNext()){long id=c.getLong(0);String name=c.getString(1),role=c.getString(3),kind=c.getString(4);boolean active=c.getInt(5)==1;
            Button b=Util.button(this,name+" — "+("NIGHT".equals(kind)?"ليل":"نهار")+("ADMIN".equals(role)?" — مدير":"")+(active?"":"  (موقوف)"));
            if(!active)b.setAlpha(0.55f);
            b.setOnClickListener(v->workerDialog(id,name,kind,"ADMIN".equals(role),active));content.addView(b);}}
        back();}
    private void addWorkerDialog(){LinearLayout box=form();EditText name=input("اسم العامل","",false),pin=input("رمز PIN (4 أرقام فأكثر)","",true);Spinner kind=new Spinner(this);kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"نهار","ليل"}));box.addView(name);box.addView(pin);box.addView(Util.label(this,"نوع الوردية"));box.addView(kind);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("عامل جديد").setView(box).setPositiveButton("إضافة",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()){name.setError("الاسم مطلوب");return;}
            if(pin.getText().toString().trim().length()<4){pin.setError("الرمز 4 أرقام على الأقل");return;}
            try{db.addWorker(name.getText().toString(),pin.getText().toString(),kind.getSelectedItemPosition()==1?"NIGHT":"DAY");d.dismiss();workers();}
            catch(Exception e){pin.setError("هذا الرمز مستخدم لحساب آخر");}}));
        d.show();}
    private void workerDialog(long id,String oldName,String oldKind,boolean admin,boolean active){LinearLayout box=form();EditText name=input("اسم العامل",oldName,false),pin=input("رمز PIN جديد (اتركه فارغًا للإبقاء)","",true);Spinner kind=new Spinner(this);kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"نهار","ليل"}));kind.setSelection("NIGHT".equals(oldKind)?1:0);box.addView(name);box.addView(pin);if(!admin)box.addView(kind);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("تعديل الحساب").setView(box)
            .setPositiveButton("حفظ",(d,w)->{if(name.getText().toString().trim().isEmpty()){Toast.makeText(this,"الاسم مطلوب",Toast.LENGTH_LONG).show();return;}
                String p=pin.getText().toString().trim();
                if(!p.isEmpty()&&p.length()<4){Toast.makeText(this,"الرمز 4 أرقام على الأقل",Toast.LENGTH_LONG).show();return;}
                try{db.updateWorker(id,name.getText().toString(),p,admin?"DAY":(kind.getSelectedItemPosition()==1?"NIGHT":"DAY"));workers();}
                catch(Exception e){Toast.makeText(this,"رمز PIN مستخدم لحساب آخر",Toast.LENGTH_LONG).show();}})
            .setNegativeButton("إلغاء",null);
        if(!admin)builder.setNeutralButton(active?"إيقاف الحساب":"إعادة تفعيله",(d,w)->{
            if(active&&db.workerHasOpenShift(id)){Toast.makeText(this,"لا يمكن إيقاف عامل لديه وردية غير معتمدة",Toast.LENGTH_LONG).show();return;}
            db.setWorkerActive(id,!active);workers();});
        builder.show();}
    private void pumps(){shell("الطرمبات والأسعار");
        Button add=Util.goldButton(this,"＋ إضافة طرمبة جديدة");add.setOnClickListener(v->pumpDialog(0,"","",0,0,0,true));content.addView(add,Util.spaced());
        try(Cursor c=db.pumps()){while(c.moveToNext()){long id=c.getLong(0);String name=c.getString(1),fuel=c.getString(2),worker=c.getString(6);double price=c.getDouble(3),reading=c.getDouble(4);long workerId=c.getLong(5);boolean active=c.getInt(7)==1;
            Button b=Util.button(this,name+" — "+fuel+(active?"":"  (موقوفة)")+"\nالسعر: "+fmt(price)+" | القراءة: "+fmt(reading)+" | "+worker);
            if(!active)b.setAlpha(0.55f);
            b.setOnClickListener(v->pumpDialog(id,name,fuel,price,reading,workerId,active));content.addView(b);}}
        back();}
    private void pumpDialog(long id,String oldName,String oldFuel,double oldPrice,double oldReading,long oldWorkerId,boolean active){
        boolean creating=id==0;
        LinearLayout box=form();EditText name=input("اسم الطرمبة",oldName,false),fuel=input("نوع الوقود",oldFuel,false),price=input("سعر اللتر",creating?"":fmt(oldPrice),true),reading=input("القراءة الافتتاحية / الحالية",creating?"":fmt(oldReading),true);
        ArrayList<String> names=new ArrayList<>();ArrayList<Integer> ids=db.workerIds();
        try(Cursor c=db.workers()){while(c.moveToNext())if("WORKER".equals(c.getString(3))&&c.getInt(5)==1)names.add(c.getString(1));}
        Spinner worker=new Spinner(this);worker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        worker.setSelection(Math.max(0,ids.indexOf((int)oldWorkerId)));
        box.addView(name);box.addView(fuel);box.addView(price);box.addView(reading);box.addView(Util.label(this,"العامل المسؤول في النهار"));box.addView(worker);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(creating?"طرمبة جديدة":"تعديل الطرمبة").setView(box)
            .setPositiveButton(creating?"إضافة":"حفظ",(d,w)->{
                if(names.isEmpty()){Toast.makeText(this,"أضف عاملًا نشطًا أولًا",Toast.LENGTH_LONG).show();return;}
                if(name.getText().toString().trim().isEmpty()||fuel.getText().toString().trim().isEmpty()){Toast.makeText(this,"الاسم ونوع الوقود مطلوبان",Toast.LENGTH_LONG).show();return;}
                long chosen=ids.get(worker.getSelectedItemPosition());
                if(creating)db.addPump(name.getText().toString(),fuel.getText().toString(),Util.number(price.getText().toString()),Util.number(reading.getText().toString()),chosen);
                else db.updatePump(id,name.getText().toString(),fuel.getText().toString(),Util.number(price.getText().toString()),Util.number(reading.getText().toString()),chosen);
                pumps();})
            .setNegativeButton("إلغاء",null);
        if(!creating)builder.setNeutralButton(active?"إيقاف الطرمبة":"إعادة تفعيلها",(d,w)->{db.setPumpActive(id,!active);pumps();});
        builder.show();}
    private void approvals(){shell("بانتظار اعتماد المدير");try(Cursor c=db.submitted()){if(c.getCount()==0)content.addView(Util.label(this,"لا توجد ورديات بانتظار الاعتماد."));while(c.moveToNext()){long id=c.getLong(0);String worker=c.getString(1),date=c.getString(2),reason=c.getString(5);double sales=c.getDouble(3),balance=c.getDouble(4);Button b=Util.button(this,"وردية #"+id+" — "+worker+"\n"+date+" | المبيعات: "+fmt(sales)+" | الباقي: "+fmt(balance)+(reason.isEmpty()?"":"\nالسبب: "+reason));b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("وردية #"+id).setMessage("اعتمدها لتصبح قراءات الإغلاق بداية الوردية التالية، أو أرجعها للعامل للتصحيح.").setPositiveButton("اعتماد",(d,w)->{db.approve(id);new Sync(this).run(false);approvals();}).setNeutralButton("إرجاع للعامل",(d,w)->returnDialog(id)).setNegativeButton("إلغاء",null).show());content.addView(b);}}back();}
    private void returnDialog(long id){EditText note=input("سبب الإرجاع وما يجب تصحيحه","",false);LinearLayout box=form();box.addView(note);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("إرجاع الوردية #"+id).setView(box).setPositiveButton("إرجاع",null).setNegativeButton("إلغاء",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=note.getText().toString().trim();if(text.isEmpty()){note.setError("اكتب سبب الإرجاع");return;}dialog.dismiss();db.returnToWorker(id,text);new Sync(this).run(false);approvals();}));dialog.show();}

    private LinearLayout form(){LinearLayout b=new LinearLayout(this);b.setPadding(28,8,28,0);b.setOrientation(LinearLayout.VERTICAL);return b;}
    private EditText input(String hint,String value,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setText(value);if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
    @Override public void onBackPressed(){finish();}
}
