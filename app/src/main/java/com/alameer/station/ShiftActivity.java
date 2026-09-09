package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;import java.util.*;

public class ShiftActivity extends Activity {
    Db db;long shiftId;int workerId;String workerName;LinearLayout readingsBox,movementsBox;TextView salesText,balanceText;final ArrayList<ReadingInput> inputs=new ArrayList<>();
    static class ReadingInput { long id; EditText current; ReadingInput(long i,EditText e){id=i;current=e;} }
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);shiftId=getIntent().getLongExtra("shiftId",0);workerId=getIntent().getIntExtra("workerId",0);workerName=getIntent().getStringExtra("workerName");build();}
    LinearLayout[] pages=new LinearLayout[5];
    Button[] tabs=new Button[4];
    int page=0;
    LinearLayout totalsBox;
    String movementFilter="";
    AutoCompleteTextView movementName;
    EditText movementAmount;
    Spinner movementType;
    final String[] movementTypes={"COLLECTION","CASH","DEBT","EXPENSE"};
    final String[] movementLabels={"مقبوضات","نقد مسلّم","ديون","مخاريج"};

    private void build(){
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        int pad=(int)(16*getResources().getDisplayMetrics().density);
        shell.setPadding(0,0,0,0);
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(20),dp(10),dp(20),dp(12));brand.setBackgroundColor(Util.NAVY);
        TextView drop=text("",32,Util.GOLD,true);drop.setBackground(new android.graphics.drawable.Drawable(){public void draw(android.graphics.Canvas c){android.graphics.Paint p=new android.graphics.Paint(3);p.setColor(Util.GOLD);android.graphics.Path path=new android.graphics.Path();float w=getBounds().width(),h=getBounds().height();path.moveTo(w*.5f,h*.12f);path.cubicTo(w*.4f,h*.35f,w*.15f,h*.5f,w*.15f,h*.64f);path.cubicTo(w*.15f,h*.98f,w*.85f,h*.98f,w*.85f,h*.64f);path.cubicTo(w*.85f,h*.5f,w*.6f,h*.3f,w*.5f,h*.12f);c.drawPath(path,p);}public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter f){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}});brand.addView(drop,new LinearLayout.LayoutParams(dp(44),dp(52)));
        LinearLayout brandWords=column();
        brandWords.addView(text("محطة الأمير",20,Color.WHITE,true));
        brandWords.addView(text("مطابقة الورديات",12,0xffd2d5d5,false));brand.addView(brandWords);
        shell.addView(brand);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        shell.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return insets;});

        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(4),dp(16),dp(16));
        for(int i=0;i<5;i++){pages[i]=new LinearLayout(this);pages[i].setOrientation(LinearLayout.VERTICAL);content.addView(pages[i]);}
        pages[0].addView(heading("ورديتي"));
        LinearLayout greeting=new LinearLayout(this);greeting.setGravity(Gravity.CENTER_VERTICAL);
        greeting.addView(text("مرحبًا، "+workerName,18,Util.NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("محفوظ على الجهاز",11,0xff6b7077,false);local.setPadding(dp(10),dp(8),dp(10),dp(8));local.setBackground(Util.round(0xffe7e8e9,dp(12)));greeting.addView(local);
        pages[0].addView(greeting,space());
        String note=db.managerNote(shiftId);
        if("RETURNED".equals(db.shiftStatus(shiftId))&&!note.isEmpty()){
            LinearLayout returned=panel(0xfffce9e8);
            returned.addView(text("↩  أرجع المدير الوردية للتصحيح",17,Util.RED,true));
            returned.addView(text(note,15,Util.NAVY,false),space());
            pages[0].addView(returned,space());
        }
        LinearLayout hero=panel(Util.NAVY);
        boolean night=db.isNightWorker(workerId);int count;try(Cursor c=db.shiftReadings(shiftId)){count=c.getCount();}
        hero.addView(text(night?"☾  وردية الليل":"☀  وردية النهار",24,Color.WHITE,true));
        hero.addView(text(night?"7 مساءً — 7 صباحًا":"7 صباحًا — 7 مساءً",18,0xffe2e3e3,false),space());
        hero.addView(text(count+" طرمبات  •  وردية رقم "+shiftId,14,Color.WHITE,false));
        pages[0].addView(hero,space());
        readingsBox=panel(Color.WHITE);pages[0].addView(readingsBox,space());loadReadings();
        Button save=action("حفظ القراءات ومتابعة الوردية",true);
        save.setOnClickListener(v->{if(saveReadings())showPage(2);});pages[0].addView(save,space());
        pages[1].addView(heading("الحركات"));
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);
        String[] filterNames={"الكل","مقبوضات","نقد مسلّم","ديون","مخاريج"};
        String[] filterTypes={"","COLLECTION","CASH","DEBT","EXPENSE"};
        for(int i=0;i<5;i++){final String type=filterTypes[i];Button chip=action(filterNames[i],i==0);chip.setTextSize(13);chip.setMinWidth(dp(72));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(44));cp.setMargins(dp(3),0,dp(3),0);chips.addView(chip,cp);chip.setOnClickListener(v->{movementFilter=type;for(int j=0;j<chips.getChildCount();j++)chips.getChildAt(j).setBackground(Util.round(chips.getChildAt(j)==v?Util.GOLD:0xffe7e8e9,dp(12)));loadMovements();});}
        filters.addView(chips);pages[1].addView(filters,space());
        movementsBox=panel(Color.WHITE);pages[1].addView(movementsBox,space());
        LinearLayout form=panel(Color.WHITE);form.addView(text("＋  إضافة حركة",21,Util.NAVY,true),space());
        form.addView(text("نوع الحركة",13,Util.NAVY,false));
        movementType=new Spinner(this);movementType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,movementLabels));
        form.addView(movementType,new LinearLayout.LayoutParams(-1,dp(48)));
        form.addView(text("الاسم",13,Util.NAVY,false));
        movementName=new AutoCompleteTextView(this);styleInput(movementName);movementName.setHint("الاسم أو البيان");movementName.setThreshold(1);form.addView(movementName,space());refreshNames();
        form.addView(text("المبلغ • ر.ي",13,Util.NAVY,false));movementAmount=new EditText(this);styleInput(movementAmount);movementAmount.setHint("0");movementAmount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);movementAmount.setTextDirection(View.TEXT_DIRECTION_LTR);form.addView(movementAmount,space());
        Button add=action("حفظ الحركة  ▣",true);add.setOnClickListener(v->{String name=movementName.getText().toString().trim();double amount=Util.number(movementAmount.getText().toString());if(name.isEmpty()){movementName.setError("أدخل الاسم");return;}if(amount<=0||Double.isNaN(amount)||Double.isInfinite(amount)){movementAmount.setError("أدخل مبلغًا صحيحًا");return;}db.addMovement(shiftId,movementTypes[movementType.getSelectedItemPosition()],name,amount);movementName.setText("");movementAmount.setText("");refreshNames();loadMovements();refreshTotals();Toast.makeText(this,"حُفظت الحركة على الجهاز",Toast.LENGTH_SHORT).show();});
        form.addView(add,space());pages[1].addView(form,space());
        Button review=action("مطابقة وتسليم الوردية",false);review.setOnClickListener(v->showPage(2));pages[1].addView(review,space());
        pages[2].addView(heading("مطابقة الوردية"));
        LinearLayout steps=new LinearLayout(this);
        String[] stepNames={"١\nالاستلام","٢\nالحركات","٣\nالتسليم"};
        for(int i=0;i<3;i++){
            final int dest=i==1?1:i==2?2:0;
            TextView step=text(stepNames[i],14,i==2?Util.NAVY:0xff777d84,i==2);
            step.setGravity(Gravity.CENTER);step.setPadding(0,dp(8),0,dp(8));
            step.setBackground(Util.round(i==2?0xffffedaa:0xffe7e8e9,dp(14)));
            step.setOnClickListener(v->showPage(dest));
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);
            sp.setMargins(dp(4),0,dp(4),0);steps.addView(step,sp);
        }
        pages[2].addView(steps,space());
        balanceText=text("",27,Util.GREEN,true);balanceText.setGravity(Gravity.CENTER);balanceText.setPadding(dp(16),dp(24),dp(16),dp(24));pages[2].addView(balanceText,space());
        totalsBox=panel(Color.WHITE);pages[2].addView(totalsBox,space());
        Button details=action("مراجعة التفاصيل  ▤",false);details.setOnClickListener(v->showPage(0));pages[2].addView(details,space());
        TextView pending=text("تُحفظ محليًا بانتظار المزامنة",12,0xff747a80,false);pending.setGravity(Gravity.CENTER);pages[2].addView(pending,space());
        Button submit=action("إرسال للمدير  ➤",true);submit.setOnClickListener(v->submit());pages[2].addView(submit,space());
        scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pages[3].addView(Util.label(this,"أرشيف وردياتي"));
        pages[4].addView(Util.label(this,"حسابي"));
        pages[4].addView(Util.card(this,workerName+"\nمحطة الأمير • مطابقة الورديات"),Util.spaced());
        Button update=Util.button(this,"فحص تحديث التطبيق");
        update.setOnClickListener(v->new AppUpdater(this).check(true));
        pages[4].addView(update,Util.spaced());
        Button logout=Util.button(this,"تسجيل الخروج");
        logout.setOnClickListener(v->{Intent i=new Intent(this,LoginActivity.class);i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);startActivity(i);finish();});
        pages[4].addView(logout,Util.spaced());
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(6),dp(8),dp(6),dp(8));
        nav.setBackground(Util.round(Color.WHITE,dp(22)));
        nav.setElevation(dp(3));
        String[] names={"ورديتي","الحركات","الأرشيف","حسابي"};
        int[] destinations={0,1,3,4};
        for(int i=0;i<4;i++){
            final int n=destinations[i];
            Button tab=new Button(this);tabs[i]=tab;
            tab.setText(names[i]);tab.setAllCaps(false);tab.setTextSize(12);
            tab.setGravity(Gravity.CENTER);tab.setPadding(dp(2),dp(8),dp(2),dp(6));
            tab.setMinWidth(0);tab.setMinimumWidth(0);tab.setMinHeight(0);tab.setMinimumHeight(0);
            tab.setStateListAnimator(null);
            NavIcon icon=new NavIcon(i);icon.setBounds(0,0,dp(25),dp(25));
            tab.setCompoundDrawables(null,icon,null,null);tab.setCompoundDrawablePadding(dp(4));
            tab.setContentDescription(names[i]);
            tab.setOnClickListener(v->{if(page==0&&n!=0&&!saveReadings())return;showPage(n);scroll.smoothScrollTo(0,0);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(70),1);
            lp.setMargins(dp(3),0,dp(3),0);nav.addView(tab,lp);
        }
        shell.addView(nav);setContentView(shell);showPage(0);loadMovements();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void showPage(int selected){
        page=selected;
        for(int i=0;i<5;i++)pages[i].setVisibility(i==selected?View.VISIBLE:View.GONE);
        int active=selected==2?0:selected==3?2:selected==4?3:selected;
        for(int i=0;i<4;i++){
            tabs[i].setBackgroundTintList(null);
            tabs[i].setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),Util.round(i==active?Util.GOLD:Color.WHITE,dp(17)),null));
            tabs[i].setTextColor(i==active?Util.NAVY:Color.rgb(98,104,109));
            tabs[i].setTypeface(android.graphics.Typeface.DEFAULT,i==active?1:0);
            tabs[i].setSelected(i==active);
        }
        if(selected==3)loadArchive();
        refreshTotals();
    }
    private void loadArchive(){
        pages[3].removeAllViews();pages[3].addView(Util.label(this,"أرشيف وردياتي"));
        try(Cursor c=db.archive(workerId,false)){
            if(c.getCount()==0)pages[3].addView(Util.card(this,"لا توجد ورديات محفوظة بعد."),Util.spaced());
            while(c.moveToNext()){
                String state=c.getString(3);
                String label="APPROVED".equals(state)?"معتمدة":"SUBMITTED".equals(state)?"بانتظار الاعتماد":"RETURNED".equals(state)?"مُرجعة للتصحيح":"مفتوحة";
                pages[3].addView(Util.card(this,"وردية #"+c.getLong(0)+"  •  "+label+"\n"+c.getString(2)+"\nالمبيعات: "+money(c.getDouble(4))+" ريال\nالباقي: "+money(c.getDouble(5))+" ريال"),Util.spaced());
            }
        }
    }
    private class NavIcon extends android.graphics.drawable.Drawable {
        final int kind;final android.graphics.Paint paint=new android.graphics.Paint(3);
        NavIcon(int kind){this.kind=kind;}
        public void draw(android.graphics.Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
            paint.setColor(Util.NAVY);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            if(kind==0){c.drawRoundRect(4,3,14,21,1,1,paint);c.drawRect(6,5,12,10,paint);c.drawLine(2,21,16,21,paint);c.drawLine(14,11,17,11,paint);c.drawLine(17,11,17,18,paint);c.drawArc(17,16,21,20,0,180,false,paint);c.drawLine(21,18,21,7,paint);c.drawLine(21,7,18,4,paint);}
            else if(kind==1){c.drawLine(4,7,20,7,paint);c.drawLine(16,3,20,7,paint);c.drawLine(16,11,20,7,paint);c.drawLine(20,17,4,17,paint);c.drawLine(8,13,4,17,paint);c.drawLine(8,21,4,17,paint);}
            else if(kind==2){android.graphics.Path p=new android.graphics.Path();p.moveTo(3,20);p.lineTo(3,5);p.lineTo(9,5);p.lineTo(12,8);p.lineTo(21,8);p.lineTo(21,20);p.close();c.drawPath(p,paint);}
            else {c.drawCircle(12,7,4,paint);c.drawRoundRect(5,14,19,22,4,4,paint);}
            c.restore();
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);}
        public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
        public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private LinearLayout panel(int color){LinearLayout box=column();box.setPadding(dp(14),dp(16),dp(14),dp(16));box.setBackground(Util.round(color,dp(16)));return box;}
    private LinearLayout.LayoutParams space(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(7));return p;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTextDirection(View.TEXT_DIRECTION_RTL);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,1);return t;}
    private TextView heading(String name){TextView t=text(name,26,0xff141922,true);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(20),0,dp(20));return t;}
    private Button action(String name,boolean primary){Button b=new Button(this);b.setText(name);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(Util.NAVY);b.setTypeface(android.graphics.Typeface.DEFAULT,primary?1:0);b.setMinHeight(dp(50));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setStateListAnimator(null);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),Util.round(primary?Util.GOLD:0xffe7e8e9,dp(12)),null));return b;}
    private void styleInput(EditText e){e.setTextSize(18);e.setTextColor(Util.NAVY);e.setSingleLine(true);e.setPadding(dp(12),dp(10),dp(12),dp(10));android.graphics.drawable.GradientDrawable bg=Util.round(Color.WHITE,dp(10));bg.setStroke(dp(1),0xffdedfe2);e.setBackground(bg);e.setMinHeight(dp(48));}
    private void refreshNames(){ArrayList<String> names=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT DISTINCT name FROM remembered_names ORDER BY name",null)){while(c.moveToNext())names.add(c.getString(0));}movementName.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,names));}
    private void loadReadings(){
        inputs.clear();readingsBox.removeAllViews();readingsBox.addView(text("قراءات الطرمبات",20,Util.NAVY,true),space());
        try(Cursor c=db.shiftReadings(shiftId)){while(c.moveToNext()){
            LinearLayout row=column();row.setPadding(0,dp(8),0,dp(12));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(c.getString(1),16,Util.NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
            top.addView(text("السابقة  "+money(c.getDouble(3)),15,Util.NAVY,true));row.addView(top);
            boolean stopped=c.getInt(7)==0;
            row.addView(text(c.getString(2)+"  •  سعر اللتر "+money(c.getDouble(5))+(stopped?"  •  أوقفها المدير":""),12,stopped?Util.RED:0xff7c8186,false),space());
            EditText current=new EditText(this);styleInput(current);current.setHint("القراءة الحالية");current.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);current.setTextDirection(View.TEXT_DIRECTION_LTR);
            if(!c.isNull(4))current.setText(fmt(c.getDouble(4)));
            if(stopped){current.setEnabled(false);current.setAlpha(0.6f);}
            row.addView(current);
            readingsBox.addView(row);View divider=new View(this);divider.setBackgroundColor(0xffeceef0);readingsBox.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            inputs.add(new ReadingInput(c.getLong(0),current));
        }}
    }
    private boolean saveReadings(){boolean ok=true;for(ReadingInput r:inputs)if(r.current.isEnabled()&&!r.current.getText().toString().trim().isEmpty())if(!db.saveReading(r.id,Util.number(r.current.getText().toString())))ok=false;Toast.makeText(this,ok?"تم الحفظ داخل الهاتف":"رفضت قراءة أقل من السابقة",Toast.LENGTH_SHORT).show();return ok;}
    private void movementDialog(String type,String label){LinearLayout box=new LinearLayout(this);box.setPadding(30,10,30,0);box.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("الاسم أو البيان");EditText amount=new EditText(this);amount.setHint("المبلغ");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(name);box.addView(amount);new AlertDialog.Builder(this).setTitle("إضافة "+label).setView(box).setPositiveButton("حفظ",(d,w)->{if(name.getText().toString().trim().isEmpty()||Util.number(amount.getText().toString())<=0){Toast.makeText(this,"أدخل الاسم والمبلغ",Toast.LENGTH_SHORT).show();return;}db.addMovement(shiftId,type,name.getText().toString(),Util.number(amount.getText().toString()));loadMovements();refreshTotals();}).setNegativeButton("إلغاء",null).show();}
    private void loadMovements(){
        movementsBox.removeAllViews();int count=0;
        try(Cursor c=db.movements(shiftId)){while(c.moveToNext()){
            String type=c.getString(1);if(!movementFilter.isEmpty()&&!movementFilter.equals(type))continue;count++;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(12));
            LinearLayout words=column();words.addView(text(c.getString(2),17,Util.NAVY,true));
            int color="COLLECTION".equals(type)?Util.GREEN:"EXPENSE".equals(type)?0xffa85a1a:Util.RED;
            TextView badge=text(arabicType(type),12,color,false);badge.setPadding(dp(8),dp(4),dp(8),dp(4));badge.setBackground(Util.round("COLLECTION".equals(type)?0xffe7f1e7:0xfffbebdf,dp(8)));words.addView(badge,space());
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView amount=text(money(c.getDouble(3))+" ر.ي",17,0xff141922,true);amount.setTextDirection(View.TEXT_DIRECTION_LTR);row.addView(amount);
            final long movementId=c.getLong(0);final String movementLabel=c.getString(2);
            boolean locked=!"OPEN".equals(db.shiftStatus(shiftId))&&!"RETURNED".equals(db.shiftStatus(shiftId));
            if(!locked){
                TextView remove=text("✕",18,Util.RED,true);remove.setPadding(dp(14),dp(4),dp(6),dp(4));
                remove.setContentDescription("حذف الحركة");
                remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("حذف الحركة").setMessage("سيُحذف \""+movementLabel+"\" نهائيًا من هذه الوردية.").setPositiveButton("حذف",(d,w)->{db.deleteMovement(movementId);loadMovements();refreshTotals();Toast.makeText(this,"حُذفت الحركة",Toast.LENGTH_SHORT).show();}).setNegativeButton("إلغاء",null).show());
                row.addView(remove);
            }
            movementsBox.addView(row);View line=new View(this);line.setBackgroundColor(0xffeceef0);movementsBox.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        }}
        if(count==0)movementsBox.addView(text("لا توجد حركات في هذه القائمة",15,0xff777d84,false));
    }
    private void refreshTotals(){
        double[] values={db.sales(shiftId),db.total(shiftId,"COLLECTION"),db.total(shiftId,"CASH"),db.total(shiftId,"DEBT"),db.total(shiftId,"EXPENSE")};
        double bal=db.balance(shiftId);String issue=db.validateShift(shiftId);
        if(balanceText!=null){
            boolean matched=issue.isEmpty()&&Math.abs(bal)<0.01;
            String nl=System.lineSeparator();
            balanceText.setText((!issue.isEmpty()?"الوردية غير مكتملة":matched?"✓ الوردية مطابقة":"يوجد فرق في الوردية")+nl+nl+"الباقي"+nl+money(bal)+" ر.ي"+(!issue.isEmpty()?nl+issue:""));
            balanceText.setTextColor(matched?0xff3f7542:!issue.isEmpty()?Util.NAVY:Util.RED);
            balanceText.setBackground(Util.round(matched?0xffe3efe3:!issue.isEmpty()?0xfffff4ce:0xfffce9e8,dp(16)));
        }
        if(totalsBox!=null){totalsBox.removeAllViews();String[] labels={"المبيعات","المقبوضات","النقد المسلّم","الديون","المخاريج"};
            for(int i=0;i<5;i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(13),0,dp(13));row.addView(text(labels[i],17,Util.NAVY,false),new LinearLayout.LayoutParams(0,-2,1));TextView amount=text((i==0?"":i==1?"+ ":"− ")+money(values[i]),18,i==0?Util.NAVY:i==1?Util.GREEN:Util.RED,true);amount.setTextDirection(View.TEXT_DIRECTION_LTR);row.addView(amount);totalsBox.addView(row);if(i<4){View line=new View(this);line.setBackgroundColor(0xffeceef0);totalsBox.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}}
        }
    }
    private String money(double value){return String.format(Locale.US,value==Math.rint(value)?"%,.0f":"%,.2f",value);}
    private void submit(){if(!saveReadings())return;String issue=db.validateShift(shiftId);if(!issue.isEmpty()){new AlertDialog.Builder(this).setTitle("لا يمكن إرسال الوردية").setMessage(issue).setPositiveButton("حسنًا",null).show();return;}double bal=db.balance(shiftId);if(Math.abs(bal)<0.01){confirmSubmit("");return;}EditText reason=new EditText(this);reason.setHint("سبب العجز أو الزيادة (إجباري)");AlertDialog dialog=new AlertDialog.Builder(this).setTitle("الباقي "+fmt(bal)+" ريال").setMessage("توجد زيادة أو عجز. اكتب السبب قبل الإرسال.").setView(reason).setPositiveButton("إرسال",null).setNegativeButton("رجوع",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String r=reason.getText().toString().trim();if(r.isEmpty()){reason.setError("السبب مطلوب");return;}dialog.dismiss();confirmSubmit(r);}));dialog.show();}
    private void confirmSubmit(String reason){new AlertDialog.Builder(this).setTitle("تأكيد الإرسال").setMessage("ستُرسل الوردية للمدير ويمكن تعديلها حتى يعتمدها.").setPositiveButton("تأكيد",(d,w)->{db.submit(shiftId,workerId,reason);Toast.makeText(this,"حُفظت الوردية وهي بانتظار المزامنة",Toast.LENGTH_LONG).show();new Sync(this).run(false);finish();}).setNegativeButton("إلغاء",null).show();}
    private String arabicType(String t){if("COLLECTION".equals(t))return "مقبوضات";if("CASH".equals(t))return "نقد مسلّم";if("DEBT".equals(t))return "ديون";return "مخاريج";}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
}
