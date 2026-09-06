package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;import java.util.*;

public class ShiftActivity extends Activity {
    Db db;long shiftId;int workerId;String workerName;LinearLayout readingsBox,movementsBox;TextView salesText,balanceText;final ArrayList<ReadingInput> inputs=new ArrayList<>();
    static class ReadingInput { long id; EditText current; ReadingInput(long i,EditText e){id=i;current=e;} }
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);shiftId=getIntent().getLongExtra("shiftId",0);workerId=getIntent().getIntExtra("workerId",0);workerName=getIntent().getStringExtra("workerName");build();}
    LinearLayout[] pages=new LinearLayout[3];
    Button[] tabs=new Button[3];
    int page=0;
    private void build(){
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        int pad=(int)(16*getResources().getDisplayMetrics().density);
        shell.setPadding(pad,pad,pad,pad);
        shell.addView(Util.title(this,"محطة الأمير"),Util.spaced());
        shell.addView(Util.label(this,workerName+"  •  وردية رقم "+shiftId));
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<3;i++){pages[i]=new LinearLayout(this);pages[i].setOrientation(LinearLayout.VERTICAL);content.addView(pages[i]);}
        pages[0].addView(Util.label(this,"قراءات الطرمبات"));
        pages[0].addView(Util.card(this,"أدخل قراءة نهاية الوردية لكل طرمبة، ثم احفظ لحساب المبيعات."),Util.spaced());
        readingsBox=new LinearLayout(this);readingsBox.setOrientation(LinearLayout.VERTICAL);
        pages[0].addView(readingsBox);loadReadings();
        Button save=Util.goldButton(this,"حفظ القراءات وحساب المبيعات");
        save.setOnClickListener(v->{saveReadings();refreshTotals();});
        pages[0].addView(save,Util.spaced());
        pages[1].addView(Util.label(this,"الحركات المالية"));
        String[] labels={"مقبوضات","نقد مسلّم","ديون","مخاريج"};
        String[] types={"COLLECTION","CASH","DEBT","EXPENSE"};
        for(int i=0;i<4;i++){
            final String type=types[i],label=labels[i];
            Button add=Util.button(this,"＋ "+label);
            add.setOnClickListener(v->movementDialog(type,label));
            pages[1].addView(add,Util.spaced());
        }
        pages[1].addView(Util.label(this,"الحركات المسجلة"));
        movementsBox=new LinearLayout(this);movementsBox.setOrientation(LinearLayout.VERTICAL);
        pages[1].addView(movementsBox);
        pages[2].addView(Util.label(this,"مراجعة وتسليم الوردية"));
        salesText=Util.card(this,"");salesText.setTextSize(23);
        pages[2].addView(salesText,Util.spaced());
        balanceText=Util.card(this,"");balanceText.setTextSize(20);balanceText.setLineSpacing(12,1.1f);
        pages[2].addView(balanceText,Util.spaced());
        Button submit=Util.goldButton(this,"إرسال الوردية للمدير");
        submit.setOnClickListener(v->submit());pages[2].addView(submit,Util.spaced());
        scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);
        String[] names={"القراءات","الحركات","المطابقة"};
        for(int i=0;i<3;i++){final int n=i;tabs[i]=Util.button(this,names[i]);tabs[i].setTextSize(14);tabs[i].setOnClickListener(v->{if(page==0&&n!=0&&!saveReadings())return;showPage(n);scroll.smoothScrollTo(0,0);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(3,8,3,0);nav.addView(tabs[i],lp);}
        shell.addView(nav);setContentView(shell);showPage(0);loadMovements();
    }
    private void showPage(int selected){
        page=selected;
        for(int i=0;i<3;i++){pages[i].setVisibility(i==selected?View.VISIBLE:View.GONE);tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(i==selected?Util.GOLD:Color.WHITE));tabs[i].setTextColor(Util.NAVY);}
        refreshTotals();
    }
    private void loadReadings(){inputs.clear();readingsBox.removeAllViews();try(Cursor c=db.shiftReadings(shiftId)){while(c.moveToNext()){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);TextView name=Util.label(this,c.getString(1)+" — "+c.getString(2)+"\nالسابقة: "+fmt(c.getDouble(3))+" | السعر: "+fmt(c.getDouble(5)));EditText current=new EditText(this);current.setHint("القراءة الحالية");current.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(!c.isNull(4))current.setText(fmt(c.getDouble(4)));row.addView(name);row.addView(current);row.setPadding(14,8,14,16);row.setElevation(2);row.setBackground(Util.round(Color.WHITE,18));readingsBox.addView(row,Util.spaced());inputs.add(new ReadingInput(c.getLong(0),current));}}}
    private boolean saveReadings(){boolean ok=true;for(ReadingInput r:inputs)if(!r.current.getText().toString().trim().isEmpty())if(!db.saveReading(r.id,Util.number(r.current.getText().toString())))ok=false;Toast.makeText(this,ok?"تم الحفظ داخل الهاتف":"رفضت قراءة أقل من السابقة",Toast.LENGTH_SHORT).show();return ok;}
    private void movementDialog(String type,String label){LinearLayout box=new LinearLayout(this);box.setPadding(30,10,30,0);box.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("الاسم أو البيان");EditText amount=new EditText(this);amount.setHint("المبلغ");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(name);box.addView(amount);new AlertDialog.Builder(this).setTitle("إضافة "+label).setView(box).setPositiveButton("حفظ",(d,w)->{if(name.getText().toString().trim().isEmpty()||Util.number(amount.getText().toString())<=0){Toast.makeText(this,"أدخل الاسم والمبلغ",Toast.LENGTH_SHORT).show();return;}db.addMovement(shiftId,type,name.getText().toString(),Util.number(amount.getText().toString()));loadMovements();refreshTotals();}).setNegativeButton("إلغاء",null).show();}
    private void loadMovements(){movementsBox.removeAllViews();try(Cursor c=db.movements(shiftId)){while(c.moveToNext()){String type=arabicType(c.getString(1));movementsBox.addView(Util.card(this,c.getString(2)+"\n"+type+"  •  "+money(c.getDouble(3))+" ريال"),Util.spaced());}}}
    private void refreshTotals(){
        double s=db.sales(shiftId),c=db.total(shiftId,"COLLECTION"),cash=db.total(shiftId,"CASH"),debt=db.total(shiftId,"DEBT"),exp=db.total(shiftId,"EXPENSE"),bal=db.balance(shiftId);
        String issue=db.validateShift(shiftId);
        if(salesText!=null)salesText.setText("إجمالي مبيعات الوقود\n"+money(s)+" ريال");
        if(balanceText!=null){
            balanceText.setText("المبيعات     "+money(s)+"\n+ المقبوضات     "+money(c)+"\n− النقد المسلّم     "+money(cash)+"\n− الديون     "+money(debt)+"\n− المخاريج     "+money(exp)+"\n\nالباقي     "+money(bal)+" ريال\n"+(!issue.isEmpty()?"غير مكتملة: "+issue:Math.abs(bal)<0.01?"✓ الوردية مطابقة":"يوجد فرق — يلزم كتابة السبب"));
            balanceText.setTextColor(!issue.isEmpty()?Util.NAVY:Math.abs(bal)<0.01?Util.GREEN:Util.RED);
        }
    }
    private String money(double value){return String.format(Locale.US,"%,.2f",value);}
    private void submit(){if(!saveReadings())return;String issue=db.validateShift(shiftId);if(!issue.isEmpty()){new AlertDialog.Builder(this).setTitle("لا يمكن إرسال الوردية").setMessage(issue).setPositiveButton("حسنًا",null).show();return;}double bal=db.balance(shiftId);if(Math.abs(bal)<0.01){confirmSubmit("");return;}EditText reason=new EditText(this);reason.setHint("سبب العجز أو الزيادة (إجباري)");AlertDialog dialog=new AlertDialog.Builder(this).setTitle("الباقي "+fmt(bal)+" ريال").setMessage("توجد زيادة أو عجز. اكتب السبب قبل الإرسال.").setView(reason).setPositiveButton("إرسال",null).setNegativeButton("رجوع",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String r=reason.getText().toString().trim();if(r.isEmpty()){reason.setError("السبب مطلوب");return;}dialog.dismiss();confirmSubmit(r);}));dialog.show();}
    private void confirmSubmit(String reason){new AlertDialog.Builder(this).setTitle("تأكيد الإرسال").setMessage("ستُرسل الوردية للمدير ويمكن تعديلها حتى يعتمدها.").setPositiveButton("تأكيد",(d,w)->{db.submit(shiftId,workerId,reason);Toast.makeText(this,"حُفظت الوردية وهي بانتظار المزامنة",Toast.LENGTH_LONG).show();finish();}).setNegativeButton("إلغاء",null).show();}
    private String arabicType(String t){if("COLLECTION".equals(t))return "مقبوضات";if("CASH".equals(t))return "نقد مسلّم";if("DEBT".equals(t))return "ديون";return "مخاريج";}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
}
