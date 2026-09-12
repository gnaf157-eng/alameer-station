package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;import java.util.*;

public class ShiftActivity extends Activity {
    Db db;long shiftId;int workerId;String workerName;LinearLayout readingsBox,movementsBox;TextView salesText,balanceText,greetingText;final ArrayList<ReadingInput> inputs=new ArrayList<>();
    static class ReadingInput { long id; EditText current; EditText previous; ReadingInput(long i,EditText e,EditText p){id=i;current=e;previous=p;} }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);db=new Db(this);
        shiftId=getIntent().getLongExtra("shiftId",0);
        workerId=getIntent().getIntExtra("workerId",0);
        workerName=getIntent().getStringExtra("workerName");
        if(shiftId==0){ // فُتح التطبيق مباشرة بلا شاشة دخول
            workerId=db.soloWorkerId();
            workerName=db.workerName(workerId);
            shiftId=db.openSoloShift(workerId);
            askNameOnFirstRun=db.setting("name_set","0").equals("0");
        }
        build();
        new AppUpdater(this).check(false);
        if(askNameOnFirstRun){
            db.setSetting("name_set","1");
            showPage(4);
            nameDialog();
        }
    }
    @Override protected void onResume(){
        super.onResume();
        if(readingsBox!=null){ // قد تكون الأسعار أو العدّادات تغيّرت من الإعدادات
            db.syncShiftWithSettings(shiftId);
            loadReadings();refreshTotals();
        }
    }
    LinearLayout[] pages=new LinearLayout[5];
    Button[] tabs=new Button[4];
    int page=0;
    TextView headerBalance;
    LinearLayout fuelLitresBox, reconciliationLitresBox;
    boolean askNameOnFirstRun=false;
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
        brandWords.addView(text("مطابقة الورديات",12,0xffd2d5d5,false));
        brand.addView(brandWords,new LinearLayout.LayoutParams(0,-2,1));
        headerBalance=text("",15,Util.GOLD,true);
        headerBalance.setGravity(Gravity.CENTER);headerBalance.setPadding(dp(6),dp(6),dp(6),dp(6));
        headerBalance.setMaxLines(3);
        headerBalance.setAutoSizeTextTypeUniformWithConfiguration(11,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        brand.addView(headerBalance,new LinearLayout.LayoutParams(0,dp(66),1));
        shell.addView(brand);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        shell.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return insets;});

        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(4),dp(16),dp(16));
        for(int i=0;i<5;i++){pages[i]=new LinearLayout(this);pages[i].setOrientation(LinearLayout.VERTICAL);content.addView(pages[i]);}
        pages[0].addView(heading("ورديتي"));
        LinearLayout greeting=new LinearLayout(this);greeting.setGravity(Gravity.CENTER_VERTICAL);
        greetingText=text("مرحبًا، "+workerName,18,Util.NAVY,true);greeting.addView(greetingText,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("محفوظ على الجهاز",11,0xff6b7077,false);local.setPadding(dp(10),dp(8),dp(10),dp(8));local.setBackground(Util.round(0xffe7e8e9,dp(12)));greeting.addView(local);
        pages[0].addView(greeting,space());
        LinearLayout hero=panel(Util.NAVY);
        boolean night=db.isNightWorker(workerId);int count;try(Cursor c=db.shiftReadings(shiftId)){count=c.getCount();}
        hero.addView(text(night?"☾  وردية الليل":"☀  وردية النهار",24,Color.WHITE,true));
        hero.addView(text(night?"7 مساءً — 7 صباحًا":"7 صباحًا — 7 مساءً",18,0xffe2e3e3,false),space());
        hero.addView(text(count+" طرمبات  •  وردية رقم "+shiftId,14,Color.WHITE,false));
        pages[0].addView(hero,space());
        fuelLitresBox=panel(Color.WHITE);pages[0].addView(fuelLitresBox,space());
        readingsBox=panel(Color.WHITE);pages[0].addView(readingsBox,space());loadReadings();
        Button save=action("حفظ القراءات ومتابعة الوردية",true);
        save.setOnClickListener(v->{if(saveReadings())showPage(2);});pages[0].addView(save,space());
        pages[1].addView(heading("الحركات"));
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);
        String[] filterNames={"الكل","مقبوضات","نقد مسلّم","ديون","مخاريج"};
        String[] filterTypes={"","COLLECTION","CASH","DEBT","EXPENSE"};
        for(int i=0;i<5;i++){final String type=filterTypes[i];Button chip=action(filterNames[i],i==0);chip.setTextSize(13);chip.setMinWidth(dp(72));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(44));cp.setMargins(dp(3),0,dp(3),0);chips.addView(chip,cp);chip.setOnClickListener(v->{movementFilter=type;for(int j=0;j<chips.getChildCount();j++)chips.getChildAt(j).setBackground(Util.round(chips.getChildAt(j)==v?Util.GOLD:0xffe7e8e9,dp(12)));loadMovements();});}
        filters.addView(chips);
        movementsBox=panel(Color.WHITE);
        LinearLayout form=panel(Color.WHITE);form.addView(text("＋  إضافة حركة",21,Util.NAVY,true),space());
        form.addView(text("نوع الحركة",13,Util.NAVY,false));
        movementType=new Spinner(this);movementType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,movementLabels));
        form.addView(movementType,new LinearLayout.LayoutParams(-1,dp(48)));
        form.addView(text("الاسم",13,Util.NAVY,false));
        movementName=new AutoCompleteTextView(this);styleInput(movementName);movementName.setHint("الاسم أو البيان");movementName.setThreshold(1);form.addView(movementName,space());refreshNames();
        form.addView(text("المبلغ • ر.ي",13,Util.NAVY,false));movementAmount=new EditText(this);styleInput(movementAmount);movementAmount.setHint("0");movementAmount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);movementAmount.setTextDirection(View.TEXT_DIRECTION_LTR);form.addView(movementAmount,space());
        Button add=action("حفظ الحركة  ▣",true);add.setOnClickListener(v->{String name=movementName.getText().toString().trim();double amount=Util.number(movementAmount.getText().toString());if(name.isEmpty()){movementName.setError("أدخل الاسم");return;}if(amount<=0||Double.isNaN(amount)||Double.isInfinite(amount)){movementAmount.setError("أدخل مبلغًا صحيحًا");return;}db.addMovement(shiftId,movementTypes[movementType.getSelectedItemPosition()],name,amount);movementName.setText("");movementAmount.setText("");refreshNames();loadMovements();refreshTotals();Toast.makeText(this,"حُفظت الحركة على الجهاز",Toast.LENGTH_SHORT).show();});
        form.addView(add,space());pages[1].addView(form,space());
        pages[1].addView(text("الحركات المسجّلة",18,Util.NAVY,true),space());
        pages[1].addView(filters,space());
        pages[1].addView(movementsBox,space());
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
        reconciliationLitresBox=panel(Color.WHITE);pages[2].addView(reconciliationLitresBox,space());
        Button details=action("مراجعة التفاصيل  ▤",false);details.setOnClickListener(v->showPage(0));pages[2].addView(details,space());
        TextView pending=text("تُحفظ محليًا على الجهاز",12,0xff747a80,false);pending.setGravity(Gravity.CENTER);pages[2].addView(pending,space());
        Button pdf=action("حفظ الوردية PDF  ▤",true);pdf.setOnClickListener(v->exportPdf());pages[2].addView(pdf,space());
        Button close=action("إغلاق الوردية وبدء وردية جديدة",false);close.setOnClickListener(v->closeShift());pages[2].addView(close,space());
        scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pages[3].addView(Util.label(this,"أرشيف وردياتي"));
        buildSettingsPage();
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(6),dp(8),dp(6),dp(8));
        nav.setBackground(Util.round(Color.WHITE,dp(22)));
        nav.setElevation(dp(3));
        String[] names={"ورديتي","الحركات","الأرشيف","الضبط"};
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
    /** تبويب الإعدادات: الأسعار والطرمبات قبل بدء المطابقة. */
    private void buildSettingsPage(){
        pages[4].removeAllViews();
        pages[4].addView(heading("الإعدادات"));

        pages[4].addView(sectionTitle("اسم العامل"));
        LinearLayout nameBox=panel(Color.WHITE);
        LinearLayout nameRow=new LinearLayout(this);nameRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nameWords=column();
        nameWords.addView(text(workerName,18,Util.NAVY,true));
        nameWords.addView(text("يظهر في الوردية وفي تقرير PDF",13,0xff7c8186,false));
        nameRow.addView(nameWords,new LinearLayout.LayoutParams(0,-2,1));
        Button editName=action("تغيير",false);editName.setTextSize(14);
        editName.setOnClickListener(v->nameDialog());
        nameRow.addView(editName);
        nameBox.addView(nameRow);
        pages[4].addView(nameBox,space());

        pages[4].addView(sectionTitle("أسعار اللتر"));
        LinearLayout priceBox=panel(Color.WHITE);
        int types=0;
        try(Cursor c=db.fuelPrices()){
            while(c.moveToNext()){
                types++;
                final String fuel=c.getString(0);
                double min=c.getDouble(1),max=c.getDouble(2);int count=c.getInt(3);
                boolean mixed=Math.abs(max-min)>=0.01;
                boolean missing=min<=0;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));
                LinearLayout words=column();
                words.addView(text(fuel,17,Util.NAVY,true));
                words.addView(text(missing?"لم يُحدَّد بعد":mixed?"مختلف ("+money(min)+" — "+money(max)+")":money(min)+" ريال/لتر  •  "+count+" طرمبة",
                        13,(missing||mixed)?Util.RED:0xff7c8186,false));
                row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                Button edit=action("تغيير",false);edit.setTextSize(14);
                edit.setOnClickListener(v->fuelPriceDialog(fuel,mixed?0:min));
                row.addView(edit);
                priceBox.addView(row);
            }
        }
        if(types==0)priceBox.addView(text("لا توجد طرمبات نشطة بعد.",15,0xff777d84,false));
        pages[4].addView(priceBox,space());

        pages[4].addView(sectionTitle("الطرمبات"));
        LinearLayout pumpBox=panel(Color.WHITE);
        try(Cursor c=db.pumps()){
            while(c.moveToNext()){
                final long id=c.getLong(0);
                final String name=c.getString(1),fuel=c.getString(2);
                final double price=c.getDouble(3),reading=c.getDouble(4);
                final boolean active=c.getInt(7)==1;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));
                LinearLayout words=column();
                words.addView(text(name+(active?"":"  (موقوفة)"),17,active?Util.NAVY:Util.RED,true));
                words.addView(text(fuel+"  •  العداد "+money(reading),13,0xff7c8186,false));
                row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                Button toggle=action(active?"إيقاف":"تفعيل",false);toggle.setTextSize(14);
                toggle.setOnClickListener(v->{
                    if(active&&db.pumpInOpenShift(id)){
                        new AlertDialog.Builder(this).setTitle("إيقاف "+name)
                            .setMessage("ستختفي من وردية اليوم ولن تُحتسب ضمن مبيعاتها. إن كنت قد بعت منها فأدخل قراءتها أولًا.")
                            .setPositiveButton("إيقافها",(d,w)->{db.setPumpActive(id,false);refreshAll();})
                            .setNegativeButton("تراجع",null).show();
                        return;
                    }
                    db.setPumpActive(id,!active);refreshAll();});
                row.addView(toggle);
                Button edit=action("تعديل",false);edit.setTextSize(14);
                edit.setOnClickListener(v->pumpDialog(id,name,fuel,price,reading));
                row.addView(edit);
                pumpBox.addView(row);
                View line=new View(this);line.setBackgroundColor(0xffeceef0);
                pumpBox.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
            }
        }
        pages[4].addView(pumpBox,space());
        Button addPump=action("＋  إضافة طرمبة",true);
        addPump.setOnClickListener(v->pumpDialog(0,"","",0,0));
        pages[4].addView(addPump,space());

        Button startShift=action("ابدأ المطابقة  ➤",true);
        startShift.setOnClickListener(v->{db.syncShiftWithSettings(shiftId);loadReadings();refreshTotals();showPage(0);});
        pages[4].addView(startShift,space());

        Button update=action("فحص تحديث التطبيق",false);
        update.setOnClickListener(v->new AppUpdater(this).check(true));
        pages[4].addView(update,space());
        Button backupBtn=action("نسخة احتياطية",false);
        backupBtn.setOnClickListener(v->new Backup(this).export());
        pages[4].addView(backupBtn,space());
    }
    private void refreshGreeting(){if(greetingText!=null)greetingText.setText("مرحبًا، "+workerName);}
    private void nameDialog(){
        EditText input=new EditText(this);styleInput(input);
        input.setHint("اسم العامل");input.setText(workerName);
        input.setSelection(input.getText().length());
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);box.addView(input);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("اسم العامل").setView(box)
            .setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if(value.isEmpty()){input.setError("أدخل الاسم");return;}
            db.renameWorker(workerId,value);
            workerName=value;
            d.dismiss();
            Toast.makeText(this,"حُفظ الاسم.",Toast.LENGTH_SHORT).show();
            buildSettingsPage();refreshGreeting();}));
        d.show();
    }
    private TextView sectionTitle(String name){TextView t=text(name,19,Util.NAVY,true);t.setPadding(dp(4),dp(14),dp(4),dp(6));return t;}
    private void refreshAll(){db.syncShiftWithSettings(shiftId);buildSettingsPage();loadReadings();refreshTotals();}
    private void fuelPriceDialog(String fuel,double current){
        EditText price=new EditText(this);styleInput(price);
        price.setHint("سعر اللتر بالريال");
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setTextDirection(View.TEXT_DIRECTION_LTR);
        if(current>0)price.setText(fmt(current));
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);box.addView(price);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("سعر "+fuel).setView(box)
            .setPositiveButton("تطبيق على كل الطرمبات",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            double value=Util.number(price.getText().toString());
            if(value<=0){price.setError("أدخل سعرًا أكبر من صفر");return;}
            int changed=db.setFuelPrice(fuel,value);
            d.dismiss();
            Toast.makeText(this,changed==0?"السعر كما هو.":"حُدِّث سعر "+changed+" طرمبة.",Toast.LENGTH_LONG).show();
            refreshAll();}));
        d.show();
    }
    private void pumpDialog(long id,String oldName,String oldFuel,double oldPrice,double oldReading){
        boolean creating=id==0;
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);
        EditText name=dialogInput("اسم الطرمبة",oldName,false);
        EditText fuel=dialogInput("نوع الوقود (ديزل / بترول / غاز)",oldFuel,false);
        EditText price=dialogInput(creating?"سعر اللتر (فارغ = سعر النوع)":"سعر اللتر",creating?"":fmt(oldPrice),true);
        EditText reading=dialogInput("القراءة السابقة للوردية",creating?"":fmt(oldReading),true);
        box.addView(name);box.addView(fuel);box.addView(price);box.addView(reading);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(creating?"طرمبة جديدة":"تعديل الطرمبة").setView(box)
            .setPositiveButton(creating?"إضافة":"حفظ",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()){name.setError("الاسم مطلوب");return;}
            if(fuel.getText().toString().trim().isEmpty()){fuel.setError("نوع الوقود مطلوب");return;}
            double opening=Util.number(reading.getText().toString());
            if(Double.isNaN(opening)||Double.isInfinite(opening)||opening<0){reading.setError("أدخل قراءة صحيحة");return;}
            if(!creating){
                try(Cursor recorded=db.getReadableDatabase().rawQuery("SELECT current FROM readings WHERE shift_id=? AND pump_id=? AND current IS NOT NULL",new String[]{String.valueOf(shiftId),String.valueOf(id)})){
                    if(recorded.moveToFirst()&&opening>recorded.getDouble(0)){reading.setError("السابقة أكبر من الحالية المحفوظة. صحّح الحالية أولًا.");return;}
                }
            }
            if(creating)db.addPump(name.getText().toString(),fuel.getText().toString(),Util.number(price.getText().toString()),Util.number(reading.getText().toString()),workerId);
            else db.updatePump(id,name.getText().toString(),fuel.getText().toString(),Util.number(price.getText().toString()),Util.number(reading.getText().toString()),workerId);
            if(!creating){
                db.getWritableDatabase().execSQL("UPDATE readings SET previous=?,sales=CASE WHEN current IS NULL THEN 0 ELSE (current-?)*price END WHERE shift_id=? AND pump_id=? AND EXISTS(SELECT 1 FROM shifts WHERE id=? AND status='OPEN')",new Object[]{opening,opening,shiftId,id,shiftId});
            }
            d.dismiss();refreshAll();}));
        d.show();
    }
    private EditText dialogInput(String hint,String value,boolean number){
        EditText e=new EditText(this);styleInput(e);e.setHint(hint);e.setText(value);
        if(number){e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setTextDirection(View.TEXT_DIRECTION_LTR);}
        return e;
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
                String label="OPEN".equals(state)?"جارية الآن":"مُغلقة";
                final long archivedId=c.getLong(0);
                TextView card=Util.card(this,"وردية #"+archivedId+"  •  "+label+"\n"+c.getString(2)+"\nالمبيعات: "+money(c.getDouble(4))+" ريال\nالباقي: "+money(c.getDouble(5))+" ريال\n\nاضغط لحفظ PDF");
                card.setClickable(true);
                card.setOnClickListener(v->sharePdf(archivedId));
                pages[3].addView(card,Util.spaced());
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
    private void styleInput(EditText e){e.setTextSize(18);e.setTextColor(Util.NAVY);e.setSingleLine(true);e.setPadding(dp(12),dp(10),dp(12),dp(10));android.graphics.drawable.GradientDrawable bg=Util.round(Color.WHITE,dp(10));bg.setStroke(dp(1),0xffdedfe2);e.setBackground(bg);e.setMinHeight(dp(48));configureNext(e);}
    private void configureNext(EditText input){
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        input.setOnEditorActionListener((v,action,event)->{
            boolean enter=event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER;
            if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_NEXT&&!enter)return false;
            if(enter&&event.getAction()!=KeyEvent.ACTION_DOWN)return true;
            ArrayList<EditText> fields=new ArrayList<>();
            collectEditable(input.getRootView(),fields);
            int current=fields.indexOf(input);
            for(int offset=1;offset<fields.size();offset++){
                EditText target=fields.get((current+offset)%fields.size());
                if(target.getText().toString().trim().isEmpty()){
                    target.requestFocus();
                    target.post(()->target.requestRectangleOnScreen(new android.graphics.Rect(0,0,target.getWidth(),target.getHeight()),false));
                    return true;
                }
            }
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            input.clearFocus();return true;
        });
    }
    private void collectEditable(View view,ArrayList<EditText> fields){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof EditText){
            EditText edit=(EditText)view;
            if(edit.isEnabled()&&edit.isFocusable()&&edit.getKeyListener()!=null)fields.add(edit);
        }else if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)collectEditable(group.getChildAt(i),fields);
        }
    }
    private void refreshNames(){ArrayList<String> names=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT DISTINCT name FROM remembered_names ORDER BY name",null)){while(c.moveToNext())names.add(c.getString(0));}movementName.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,names));}
    private void loadReadings(){
        inputs.clear();readingsBox.removeAllViews();readingsBox.addView(text("قراءات الطرمبات",20,Util.NAVY,true),space());
        readingsBox.addView(text("تُحفظ الكتابة تلقائيًا. اضغط حفظ القراءات لتحديث الحساب.",12,0xff777d84,false),space());
        try(Cursor c=db.shiftReadings(shiftId)){while(c.moveToNext()){
            LinearLayout row=column();row.setPadding(0,dp(8),0,dp(12));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(c.getString(1),16,Util.NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(top);
            boolean stopped=c.getInt(7)==0;
            row.addView(text(c.getString(2)+"  •  سعر اللتر "+money(c.getDouble(5))+(stopped?"  •  أوقفها المدير":""),12,stopped?Util.RED:0xff7c8186,false),space());
            LinearLayout pair=new LinearLayout(this);pair.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout prevBox=column();prevBox.addView(text("القراءة السابقة",12,0xff7c8186,false));
            EditText previous=new EditText(this);styleInput(previous);previous.setHint("السابقة");previous.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);previous.setTextDirection(View.TEXT_DIRECTION_LTR);
            previous.setText(fmt(c.getDouble(3)));
            previous.setKeyListener(null);previous.setFocusable(false);previous.setFocusableInTouchMode(false);
            previous.setCursorVisible(false);previous.setLongClickable(false);
            previous.setBackground(Util.round(0xffeceef0,dp(10)));
            previous.setContentDescription("القراءة السابقة، تُعدّل من الضبط ثم الطرمبات");
            prevBox.addView(previous);
            LinearLayout currBox=column();currBox.addView(text("القراءة الحالية",12,0xff7c8186,false));
            EditText current=new EditText(this);styleInput(current);current.setHint("الحالية");current.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);current.setTextDirection(View.TEXT_DIRECTION_LTR);
            if(!c.isNull(4))current.setText(fmt(c.getDouble(4)));currBox.addView(current);
            if(!stopped){
                readingDrafts().edit().remove(draftKey(c.getLong(0),"previous")).apply();
                restoreAndWatchDraft(current,c.getLong(0),"current");
            }
            LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMargins(dp(3),0,dp(3),0);
            pair.addView(prevBox,half);pair.addView(currBox,new LinearLayout.LayoutParams(half));
            if(stopped){current.setEnabled(false);current.setAlpha(0.6f);previous.setEnabled(false);previous.setAlpha(0.6f);}
            row.addView(pair);
            readingsBox.addView(row);View divider=new View(this);divider.setBackgroundColor(0xffeceef0);readingsBox.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            inputs.add(new ReadingInput(c.getLong(0),current,previous));
        }}
    }
    private android.content.SharedPreferences readingDrafts(){
        return getSharedPreferences("reading_drafts",MODE_PRIVATE);
    }
    private String draftKey(long readingId,String field){
        return "shift_"+shiftId+"_reading_"+readingId+"_"+field;
    }
    private void restoreAndWatchDraft(EditText input,long readingId,String field){
        final String key=draftKey(readingId,field);
        final android.content.SharedPreferences prefs=readingDrafts();
        if(prefs.contains(key))input.setText(prefs.getString(key,""));
        input.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(android.text.Editable value){
                // Persist exact input, including a cleared field, separately from validated sales.
                prefs.edit().putString(key,value.toString()).apply();
            }
        });
    }
    private boolean saveReadings(){
        boolean ok=true;
        for(ReadingInput r:inputs){
            if(!r.current.isEnabled())continue;
            String curr=r.current.getText().toString().trim();
            if(!curr.isEmpty()){
                if(db.saveReading(r.id,Util.number(curr)))
                    readingDrafts().edit().remove(draftKey(r.id,"current")).apply();
                else ok=false;
            }
        }
        Toast.makeText(this,ok?"تم الحفظ داخل الهاتف":"رفضت قراءة حالية أقل من السابقة",Toast.LENGTH_SHORT).show();
        loadReadings();refreshTotals();
        return ok;}
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
            boolean locked=!"OPEN".equals(db.shiftStatus(shiftId));
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
        if(headerBalance!=null){
            headerBalance.setText("الباقي"+System.lineSeparator()+money(bal)+" ر.ي"+(issue.isEmpty()?"":System.lineSeparator()+"غير مكتملة"));
            headerBalance.setTextColor(!issue.isEmpty()?Util.GOLD:Math.abs(bal)<0.01?0xffb9e5bd:0xffffb8b8);
            headerBalance.setContentDescription("باقي الوردية الحالية "+money(bal)+" ريال");
        }
        refreshFuelLitres();
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
    /** Uses the same visible reading rows as sales; missing counters stay incomplete. */
    private void refreshFuelLitres(){
        LinkedHashMap<String,double[]> totals=new LinkedHashMap<>();
        try(Cursor c=db.shiftReadings(shiftId)){
            while(c.moveToNext()){
                String fuel=c.getString(2).trim();
                double[] total=totals.get(fuel);
                if(total==null){total=new double[2];totals.put(fuel,total);}
                if(c.isNull(4)||c.getDouble(4)<c.getDouble(3)){total[1]++;continue;}
                total[0]+=c.getDouble(4)-c.getDouble(3);
            }
        }
        for(LinearLayout box:new LinearLayout[]{fuelLitresBox,reconciliationLitresBox}){
            if(box==null)continue;
            box.removeAllViews();box.addView(text("إجمالي اللترات حسب النوع",18,Util.NAVY,true),space());
            if(totals.isEmpty())box.addView(text("لا توجد طرمبات في الوردية",14,0xff777d84,false));
            for(Map.Entry<String,double[]> entry:totals.entrySet()){
                double[] total=entry.getValue();
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(text(entry.getKey(),16,Util.NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
                TextView amount=text(money(total[0])+" لتر",18,Util.NAVY,true);
                amount.setTextDirection(View.TEXT_DIRECTION_LTR);row.addView(amount);
                box.addView(row,space());
                if(total[1]>0)box.addView(text("غير مكتمل — "+(int)total[1]+" قراءة متبقية",12,0xff8a6200,false));
            }
            box.addView(text("من فرق القراءات المحفوظة لهذه الوردية",11,0xff777d84,false),space());
        }
    }
    private String money(double value){return String.format(Locale.US,value==Math.rint(value)?"%,.0f":"%,.2f",value);}
    /** يحفظ تقرير الوردية PDF ويفتح قائمة المشاركة. */
    private void exportPdf(){
        if(!saveReadings())return;
        try{
            sharePdfOrThrow(shiftId);
        }catch(Exception e){Toast.makeText(this,"تعذر إنشاء ملف PDF",Toast.LENGTH_LONG).show();}
    }
    private void sharePdf(long id){
        try{ sharePdfOrThrow(id); }
        catch(Exception e){Toast.makeText(this,"تعذر إنشاء ملف PDF",Toast.LENGTH_LONG).show();}
    }
    private void sharePdfOrThrow(long id)throws Exception{
        java.io.File file=new PdfReport(this,db).build(id);
        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.putExtra(Intent.EXTRA_SUBJECT,"وردية محطة الأمير #"+id);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent,"حفظ أو مشاركة الوردية"));
    }
    /** يقفل الوردية الحالية بعد حفظها ويبدأ وردية جديدة بعدادات الإغلاق. */
    private void closeShift(){
        if(!saveReadings())return;
        String issue=db.validateShift(shiftId);
        if(!issue.isEmpty()){new AlertDialog.Builder(this).setTitle("لا يمكن إغلاق الوردية").setMessage(issue).setPositiveButton("حسنًا",null).show();return;}
        double bal=db.balance(shiftId);
        if(Math.abs(bal)<0.01){
            new AlertDialog.Builder(this).setTitle("إغلاق الوردية")
                .setMessage("الوردية مطابقة. ستُحفظ في الأرشيف وتبدأ وردية جديدة بقراءات الإغلاق.")
                .setPositiveButton("إغلاق",(d,w)->finishShift(""))
                .setNegativeButton("إلغاء",null).show();
            return;
        }
        EditText reason=new EditText(this);styleInput(reason);
        reason.setHint("سبب العجز أو الزيادة");
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);box.addView(reason);
        AlertDialog d=new AlertDialog.Builder(this)
            .setTitle("الباقي "+money(bal)+" ريال")
            .setMessage("توجد زيادة أو عجز. اكتب السبب ليُحفظ في التقرير.")
            .setView(box).setPositiveButton("إغلاق",null).setNegativeButton("رجوع",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String text=reason.getText().toString().trim();
            if(text.isEmpty()){reason.setError("السبب مطلوب");return;}
            d.dismiss();finishShift(text);}));
        d.show();
    }
    /** يؤرشف الوردية الحالية ويبدأ واحدة جديدة، ثم يعرض حفظ التقرير. */
    private void finishShift(String reason){
        final long closed=shiftId;
        db.submit(closed,workerId,reason);
        db.approve(closed);
        shiftId=db.openSoloShift(workerId);
        loadReadings();loadMovements();refreshTotals();showPage(0);
        new AlertDialog.Builder(this).setTitle("حُفظت الوردية #"+closed)
            .setMessage("بدأت وردية جديدة بقراءات الإغلاق. تستطيع حفظ تقرير الوردية المُغلقة الآن أو لاحقًا من الأرشيف.")
            .setPositiveButton("حفظ PDF",(d,w)->sharePdf(closed))
            .setNegativeButton("لاحقًا",null).show();
    }
    private String arabicType(String t){if("COLLECTION".equals(t))return "مقبوضات";if("CASH".equals(t))return "نقد مسلّم";if("DEBT".equals(t))return "ديون";return "مخاريج";}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
}
