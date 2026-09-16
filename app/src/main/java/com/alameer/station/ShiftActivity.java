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
        // فتح وردية بعينها قادمًا من شاشة ورديات العامل.
        long requested=getIntent().getLongExtra("openShift",0);
        if(requested>0)shiftId=requested;
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
            if(db.isOpen(shiftId))db.syncShiftWithSettings(shiftId);
            loadReadings();loadMovements();refreshTotals();
        }
    }
    LinearLayout[] pages=new LinearLayout[5];
    Button[] tabs=new Button[3];
    LinearLayout navBar;
    int settingsReturnPage=0;
    boolean settingsOnly=false;
    ScrollView screenScroll;
    int page=0;
    TextView headerBalance,stationTitle;
    ImageView stationLogo;
    static final int PICK_STATION_LOGO=7301;
    Button shiftDateButton;
    LinearLayout fuelLitresBox, reconciliationLitresBox, pinnedSummaries, movementSummary;
    boolean askNameOnFirstRun=false;
    LinearLayout totalsBox;

    LinearLayout typePicker;
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
        stationLogo=new ImageView(this);stationLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stationLogo.setContentDescription("شعار المحطة");
        brand.addView(stationLogo,new LinearLayout.LayoutParams(dp(44),dp(52)));
        LinearLayout brandWords=column();
        stationTitle=text("",20,Color.WHITE,true);stationTitle.setMaxLines(2);
        stationTitle.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        brandWords.addView(stationTitle,new LinearLayout.LayoutParams(-1,dp(48)));
        refreshStationBrand();
        brandWords.addView(text("طابق ورحّل • مطابقة الورديات",11,0xffCFE2FA,false));
        brand.addView(brandWords,new LinearLayout.LayoutParams(0,-2,1));
        headerBalance=text("",15,0xffCFE2FA,true);
        headerBalance.setGravity(Gravity.CENTER);headerBalance.setPadding(dp(6),dp(6),dp(6),dp(6));
        headerBalance.setMaxLines(3);
        headerBalance.setAutoSizeTextTypeUniformWithConfiguration(11,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        brand.addView(headerBalance,new LinearLayout.LayoutParams(0,dp(66),1));
        // ترس في ترويسة الوردية: جهاز العامل لا يرى الواجهة الرئيسية، فهذا طريقه الوحيد للإعدادات.
        ImageButton gear=new ImageButton(this);
        gear.setContentDescription("الضبط");
        gear.setPadding(dp(9),dp(9),dp(9),dp(9));
        gear.setImageDrawable(new SettingsGear());
        gear.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(0x22FFFFFF,dp(21)),Util.round(Color.WHITE,dp(21))));
        gear.setOnClickListener(v->{settingsReturnPage=page==4?0:page;showPage(4);
            if(screenScroll!=null)screenScroll.smoothScrollTo(0,0);});
        brand.addView(gear,new LinearLayout.LayoutParams(dp(42),dp(42)));
        shell.addView(brand);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        shell.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return insets;});

        pinnedSummaries=column();
        pinnedSummaries.setPadding(dp(16),dp(4),dp(16),dp(4));
        shell.addView(pinnedSummaries);
        movementSummary=panel(Color.WHITE);
        movementSummary.setVisibility(View.GONE);
        pinnedSummaries.addView(movementSummary);
        ScrollView scroll=new ScrollView(this);screenScroll=scroll;
        // هامش علوي داخل التمرير حتى لا يلتصق المحتوى بالترويسة الثابتة.
        scroll.setClipToPadding(false);
        scroll.setPadding(0,dp(10),0,0);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(4),dp(16),dp(16));
        for(int i=0;i<pages.length;i++){pages[i]=new LinearLayout(this);pages[i].setOrientation(LinearLayout.VERTICAL);content.addView(pages[i]);}
        pages[0].addView(heading("ورديتي"));
        shiftDateButton=action("",false);
        shiftDateButton.setOnClickListener(v->chooseShiftDate());
        pages[0].addView(shiftDateButton,space());
        refreshShiftDate();
        LinearLayout greeting=new LinearLayout(this);greeting.setGravity(Gravity.CENTER_VERTICAL);
        greetingText=text("مرحبًا، "+workerName,18,Util.NAVY,true);greeting.addView(greetingText,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("محفوظ على الجهاز",11,0xff6b7077,false);local.setPadding(dp(10),dp(8),dp(10),dp(8));local.setBackground(Util.round(0xffe7e8e9,dp(12)));greeting.addView(local);
        pages[0].addView(greeting,space());
        fuelLitresBox=panel(Color.WHITE);pinnedSummaries.addView(fuelLitresBox);
        readingsBox=panel(Color.WHITE);pages[0].addView(readingsBox,space());loadReadings();
        Button save=action("حفظ القراءات ومتابعة الوردية",true);
        save.setOnClickListener(v->{if(saveReadings()){showPage(1);if(screenScroll!=null)screenScroll.smoothScrollTo(0,0);}});pages[0].addView(save,space());
        pages[1].addView(heading("الحركات"));
        movementsBox=panel(Color.WHITE);
        // أربع أيقونات ظاهرة بدل القائمة المنسدلة؛ كل واحدة تفتح نافذة إدخال سريعة.
        LinearLayout picker=panel(Color.WHITE);
        picker.addView(text("＋  إضافة حركة",21,Util.NAVY,true),space());
        picker.addView(text("اختر نوع الحركة ثم سجّل الاسم والمبلغ",12,0xff777d84,false),space());
        typePicker=column();
        picker.addView(typePicker);
        buildTypeTiles();
        pages[1].addView(picker,space());
        pages[1].addView(text("الحركات المسجّلة",18,Util.NAVY,true),space());

        pages[1].addView(movementsBox,space());
        Button review=action("مطابقة وتسليم الوردية",false);review.setOnClickListener(v->showPage(2));pages[1].addView(review,space());
        pages[2].addView(heading("مطابقة الوردية"));
        LinearLayout steps=new LinearLayout(this);
        String[] stepNames={"١\nالاستلام","٢\nالحركات","٣\nالتسليم"};
        for(int i=0;i<3;i++){
            final int dest=i==1?1:i==2?2:0;
            TextView step=text(stepNames[i],14,i==2?Util.NAVY:0xff777d84,i==2);
            step.setGravity(Gravity.CENTER);step.setPadding(0,dp(8),0,dp(8));
            step.setBackground(Util.round(i==2?Util.ACCENT_SOFT:0xffedf0f4,dp(14)));
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
        Button excel=action("مشاركة Excel",true);excel.setOnClickListener(v->exportExcel());pages[2].addView(excel,space());
        Button close=action("إغلاق الوردية وبدء وردية جديدة",false);close.setOnClickListener(v->closeShift());pages[2].addView(close,space());
        scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pages[3].addView(Util.label(this,"أرشيف وردياتي"));
        buildSettingsPage();
        LinearLayout nav=new LinearLayout(this);
        navBar=nav;
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(6),dp(8),dp(6),dp(8));
        nav.setBackground(Util.round(Color.WHITE,dp(22)));
        nav.setElevation(dp(3));
        String[] names={"ورديتي","الحركات","الأرشيف"};
        int[] destinations={0,1,3};
        for(int i=0;i<tabs.length;i++){
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
        shell.addView(nav);setContentView(shell);
        boolean openSettings=getIntent().getBooleanExtra("openSettings",false);
        // الدخول من ترس الواجهة الرئيسية: الرجوع يخرج إليها مباشرة لا إلى الوردية.
        settingsOnly=openSettings;
        showPage(openSettings?4:0);loadMovements();
    }
    /** تبويب الإعدادات: الأسعار والطرمبات قبل بدء المطابقة. */
    private void buildSettingsPage(){
        pages[4].removeAllViews();
        pages[4].addView(heading("الإعدادات"));
        // التحديث أولًا: أهم ما يحتاجه جهاز العامل ولا ينبغي أن يبحث عنه.
        Button updateTop=action("فحص تحديث التطبيق  ⟳",true);
        updateTop.setOnClickListener(v->new AppUpdater(this).check(true));
        pages[4].addView(updateTop,space());
        TextView version=text("النسخة الحالية "+BuildConfig.VERSION_NAME,12,0xff7c8186,false);
        version.setGravity(Gravity.CENTER);
        pages[4].addView(version,space());
        Button back=action("رجوع إلى الوردية",false);
        back.setOnClickListener(v->{showPage(settingsReturnPage);
            if(screenScroll!=null)screenScroll.smoothScrollTo(0,0);});
        pages[4].addView(back,space());
        buildStationSettings();

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

        buildLinkSettings();
        buildTankSettings();
        buildThresholdSettings();

        Button backupBtn=action("نسخة احتياطية",false);
        backupBtn.setOnClickListener(v->new Backup(this).export());
        pages[4].addView(backupBtn,space());
        pages[4].addView(sectionTitle("حول التطبيق"));
        LinearLayout about=panel(Color.WHITE);
        about.addView(text("طابق ورحّل  •  "+BuildConfig.VERSION_NAME,19,Util.NAVY,true));
        about.addView(text("تطوير: أبوقناف للأتمتة",16,Util.NAVY,true),space());
        TextView contact=text("للتواصل: 777808020",16,Util.NAVY,false);contact.setTextIsSelectable(true);about.addView(contact);
        Button call=action("تواصل مع المطوّر",false);
        call.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:777808020")));}catch(ActivityNotFoundException e){Toast.makeText(this,"رقم التواصل: 777808020",Toast.LENGTH_LONG).show();}});
        about.addView(call,space());pages[4].addView(about,space());
    }

    /** تغيير كلمة مرور المستخدم الحالي بعد التحقّق من الحالية. */
    private void changePasswordDialog(final String role){
        final EditText current=new EditText(this);styleInput(current);
        current.setHint("كلمة المرور الحالية");
        current.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText fresh=new EditText(this);styleInput(fresh);
        fresh.setHint("كلمة المرور الجديدة");
        fresh.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);
        form.addView(current);form.addView(fresh);
        new AlertDialog.Builder(this).setTitle("تغيير كلمة المرور").setView(form)
            .setPositiveButton("حفظ",(d,w)->{
                try{
                    db.changePassword(role,current.getText().toString(),fresh.getText().toString());
                    Toast.makeText(this,"غُيّرت كلمة المرور",Toast.LENGTH_LONG).show();
                }catch(Exception e){Toast.makeText(this,String.valueOf(e.getMessage()),Toast.LENGTH_LONG).show();}
            })
            .setNegativeButton("إلغاء",null).show();
    }

    /** ربط جهاز العامل بجهاز المدير برمز واحد يُكتب مرة واحدة. */
    private void buildLinkSettings(){
        pages[4].addView(sectionTitle("المستخدم الحالي"));
        LinearLayout roleBox=panel(Color.WHITE);
        final boolean worker=db.workerDevice();
        roleBox.addView(text(worker?"العامل":"المدير",19,Util.NAVY,true));
        roleBox.addView(text(worker?"شاشة الوردية وحدها. للدخول كمدير اخرج ثم أدخل كلمة مروره."
                                   :"الواجهة كاملة: مراجعة واعتماد وتقارير.",13,0xff7c8186,false));
        Button out=action("خروج وتبديل المستخدم",false);
        out.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("خروج")
            .setMessage("ستُطلب كلمة المرور عند الفتح القادم.")
            .setPositiveButton("خروج",(d,w)->{
                db.signOut();
                Intent home=new Intent(this,HomeActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);finish();
            })
            .setNegativeButton("إلغاء",null).show());
        roleBox.addView(out,space());
        Button pw=action("تغيير كلمة مروري",false);
        pw.setOnClickListener(v->changePasswordDialog(worker?"WORKER":"MANAGER"));
        roleBox.addView(pw,space());
        pages[4].addView(roleBox,space());

        pages[4].addView(sectionTitle("الربط بين الجهازين"));
        LinearLayout box=panel(Color.WHITE);
        String code=db.linkCode();
        boolean linked=Link.valid(code);
        box.addView(text(linked?Link.pretty(code):"غير مربوط",linked?20:17,linked?Util.NAVY:Util.RED,true));
        box.addView(text(linked
                ? "الجهازان مربوطان. إرسال الوردية واستلامها يتم تلقائيًا."
                : "أنشئ رمزًا في جهاز المدير، واكتب نفس الرمز في جهاز العامل مرة واحدة.",
                13,0xff7c8186,false));

        Button create=action("إنشاء رمز ربط جديد",true);
        create.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("إنشاء رمز ربط")
                .setMessage("سيُنشأ رمز جديد لهذا الجهاز.\nاكتبه في جهاز العامل مرة واحدة.\n\n"
                        +"تنبيه: الرمز القديم يتوقف عن العمل.")
                .setPositiveButton("إنشاء",(d,w)->{
                    String fresh=db.createLinkCode();
                    buildSettingsPage();
                    new AlertDialog.Builder(this).setTitle("رمز الربط")
                        .setMessage(Link.pretty(fresh)+"\n\nاكتب هذا الرمز في جهاز العامل:\nالإعدادات ← الربط بين الجهازين ← إدخال رمز الربط.")
                        .setPositiveButton("نسخ",(a,b)->{
                            android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("رمز الربط",Link.pretty(fresh)));
                            Toast.makeText(this,"نُسخ الرمز",Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("حسنًا",null).show();
                })
                .setNegativeButton("إلغاء",null).show());
        box.addView(create,space());

        Button enter=action("إدخال رمز الربط",false);
        enter.setOnClickListener(v->{
            EditText input=new EditText(this);styleInput(input);
            input.setHint("مثال: ABCD-2345-KLMN");
            input.setText(linked?Link.pretty(code):"");
            LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);form.addView(input);
            new AlertDialog.Builder(this).setTitle("رمز الربط")
                .setMessage("اكتب الرمز الذي أنشأه جهاز المدير. مرة واحدة فقط.")
                .setView(form)
                .setPositiveButton("حفظ",(d,w)->{
                    try{ db.setLinkCode(input.getText().toString());
                        Toast.makeText(this,"تم الربط",Toast.LENGTH_LONG).show();
                        buildSettingsPage();
                    }catch(Exception e){Toast.makeText(this,String.valueOf(e.getMessage()),Toast.LENGTH_LONG).show();}
                })
                .setNegativeButton("إلغاء",null).show();
        });
        box.addView(enter,space());
        pages[4].addView(box,space());
    }

    /** سعات الخزانات ومطابقة العجز بالمقياس اليدوي. */
    private void buildTankSettings(){
        pages[4].addView(sectionTitle("الخزانات ومطابقة العجز"));
        LinearLayout box=panel(Color.WHITE);
        for(final String material:Db.MATERIALS){
            final double cap=db.capacity(material);
            final double book=db.materialSummary(material)[3];
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));
            LinearLayout words=column();
            words.addView(text(material,17,Util.NAVY,true));
            words.addView(text("السعة "+money(cap)+" لتر  •  الدفتري "+money(book)+" لتر",13,0xff7c8186,false));
            String last=db.lastDip(material);
            if(!last.isEmpty())words.addView(text("آخر قياس: "+last,12,0xff8b9097,false));
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            Button capBtn=action("السعة",false);capBtn.setTextSize(13);
            capBtn.setOnClickListener(v->capacityDialog(material,cap));
            row.addView(capBtn);
            Button dipBtn=action("قياس",false);dipBtn.setTextSize(13);
            dipBtn.setOnClickListener(v->dipDialog(material));
            row.addView(dipBtn);
            box.addView(row);
            View line=new View(this);line.setBackgroundColor(0xffeceef0);
            box.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        pages[4].addView(box,space());
        Button history=action("سجل المطابقات",false);
        history.setOnClickListener(v->dipHistory());
        pages[4].addView(history,space());
    }

    private void capacityDialog(final String material,double current){
        final EditText input=new EditText(this);styleInput(input);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(fmt(current));input.setSelectAllOnFocus(true);
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);form.addView(input);
        new AlertDialog.Builder(this).setTitle("سعة خزان "+material)
            .setMessage("تُستخدم لرسم شريط الامتلاء وحساب نسبة المخزون.")
            .setView(form)
            .setPositiveButton("حفظ",(d,w)->{
                double value=Calc.number(input.getText().toString());
                if(value<=0){Toast.makeText(this,"اكتب سعة أكبر من صفر",Toast.LENGTH_SHORT).show();return;}
                db.setCapacity(material,value);buildSettingsPage();
            }).setNegativeButton("إلغاء",null).show();
    }

    /** يقارن القياس اليدوي بالرصيد الدفتري ويصحّح الفرق بحركة مخزون. */
    private void dipDialog(final String material){
        final double book=db.materialSummary(material)[3];
        final double price=db.priceFor(material);
        final EditText measured=new EditText(this);styleInput(measured);
        measured.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measured.setHint("القياس الفعلي باللترات");
        final EditText why=new EditText(this);styleInput(why);why.setHint("سبب الفرق (اختياري)");
        final TextView preview=text("أدخل القياس لعرض الفرق",14,0xff7c8186,false);
        preview.setPadding(0,dp(10),0,0);
        measured.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence c,int a,int b,int d){}
            public void onTextChanged(CharSequence c,int a,int b,int d){}
            public void afterTextChanged(android.text.Editable e){
                String raw=e.toString().trim();
                if(raw.isEmpty()){preview.setText("أدخل القياس لعرض الفرق");preview.setTextColor(0xff7c8186);return;}
                double value=Calc.number(raw);
                double gap=Dip.gap(book,value);
                int level=Dip.level(book,value);
                preview.setText(Dip.summary(book,value,price)+"\n"+Dip.verdict(book,value)
                        +"  (الحد المسموح "+money(Dip.tolerance(book))+" لتر)");
                preview.setTextColor(level==Dip.OK?Util.GREEN:level==Dip.WATCH?0xffB86A00:Util.RED);
            }
        });
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);
        form.addView(text("الرصيد الدفتري "+money(book)+" لتر",15,Util.NAVY,true));
        form.addView(measured);form.addView(why);form.addView(preview);

        new AlertDialog.Builder(this).setTitle("مطابقة خزان "+material)
            .setView(form)
            .setPositiveButton("حفظ المطابقة",(d,w)->{
                String raw=measured.getText().toString().trim();
                if(raw.isEmpty()){Toast.makeText(this,"اكتب القياس أولًا",Toast.LENGTH_SHORT).show();return;}
                try{
                    db.recordDip(material,Calc.number(raw),why.getText().toString(),ShiftDates.today());
                    Toast.makeText(this,"سُجّلت المطابقة وصُحّح المخزون",Toast.LENGTH_LONG).show();
                    buildSettingsPage();
                }catch(Exception ex){Toast.makeText(this,String.valueOf(ex.getMessage()),Toast.LENGTH_LONG).show();}
            })
            .setNegativeButton("إلغاء",null).show();
    }

    private void dipHistory(){
        StringBuilder sb=new StringBuilder();
        try(Cursor c=db.dipReadings(null,40)){
            if(c.getCount()==0)sb.append("لا قياسات مسجّلة بعد.");
            while(c.moveToNext()){
                double gap=c.getDouble(4);
                sb.append("• ").append(c.getString(1)).append("  —  ").append(c.getString(5))
                  .append("\n   المقاس ").append(money(c.getDouble(2)))
                  .append(" • الدفتري ").append(money(c.getDouble(3)))
                  .append("\n   ").append(Dip.direction(gap)).append(" ").append(money(Math.abs(gap))).append(" لتر");
                String reason=c.getString(6);
                if(reason!=null&&!reason.isEmpty())sb.append("\n   السبب: ").append(reason);
                sb.append("\n\n");
            }
        }
        new AlertDialog.Builder(this).setTitle("سجل مطابقة الخزانات")
            .setMessage(sb.toString()).setPositiveButton("حسنًا",null).show();
    }

    /** حدود التنبيه في لوحة التحكم. */
    private void buildThresholdSettings(){
        pages[4].addView(sectionTitle("حدود التنبيه"));
        LinearLayout box=panel(Color.WHITE);
        box.addView(thresholdRow("الصندوق المنخفض",money(db.lowCash())+" ريال",
                "يُنبَّه على أي صندوق رصيده أقل من هذا الحد.",
                v->numberDialog("حد الصندوق المنخفض",db.lowCash(),false,value->db.setLowCash(value))));
        box.addView(thresholdRow("الدين الكبير",money(db.bigDebt())+" ريال",
                "يُميَّز المدين الذي يتجاوز دينه هذا الحد.",
                v->numberDialog("حد الدين الكبير",db.bigDebt(),false,value->db.setBigDebt(value))));
        box.addView(thresholdRow("الحساب الراكد",db.staleDays()+" يومًا",
                "يُعلَّم المدين الذي لم تُسجَّل له حركة منذ هذه المدة.",
                v->numberDialog("أيام الركود",db.staleDays(),true,value->db.setStaleDays((int)value))));
        box.addView(thresholdRow("المخزون المنخفض",db.lowStockPercent()+"٪",
                "تُنبَّه المادة التي ينزل مخزونها تحت هذه النسبة من السعة.",
                v->numberDialog("نسبة المخزون المنخفض",db.lowStockPercent(),true,value->db.setLowStockPercent((int)value))));
        pages[4].addView(box,space());
    }

    private View thresholdRow(String title,String value,String note,View.OnClickListener tap){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));
        LinearLayout words=column();
        words.addView(text(title+"  —  "+value,17,Util.NAVY,true));
        words.addView(text(note,12,0xff7c8186,false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=action("تغيير",false);edit.setTextSize(14);
        edit.setOnClickListener(tap);
        row.addView(edit);
        return row;
    }

    private interface NumberSink{ void accept(double value); }

    private void numberDialog(String title,double current,boolean integer,final NumberSink sink){
        final EditText input=new EditText(this);styleInput(input);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                |(integer?0:android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));
        input.setText(fmt(current));input.setSelectAllOnFocus(true);
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);form.addView(input);
        new AlertDialog.Builder(this).setTitle(title).setView(form)
            .setPositiveButton("حفظ",(d,w)->{
                double value=Calc.number(input.getText().toString());
                if(value<=0){Toast.makeText(this,"اكتب قيمة أكبر من صفر",Toast.LENGTH_SHORT).show();return;}
                sink.accept(value);buildSettingsPage();
            }).setNegativeButton("إلغاء",null).show();
    }

    private void refreshStationBrand(){
        if(stationTitle!=null)stationTitle.setText(Branding.stationName(db));
        if(stationLogo!=null){
            android.graphics.Bitmap logo=Branding.logo(this);
            if(logo==null)stationLogo.setImageResource(R.drawable.ic_wardiya_mark);else stationLogo.setImageBitmap(logo);
        }
    }
    private void buildStationSettings(){
        pages[4].addView(sectionTitle("المحطة"));
        LinearLayout box=panel(Color.WHITE);
        box.addView(text(Branding.stationName(db),20,Util.NAVY,true));
        box.addView(text("اسم المحطة وشعارها يظهران في الواجهة والتقرير",13,0xff7c8186,false));
        Button rename=action("تغيير اسم المحطة",false);
        rename.setOnClickListener(v->{
            EditText input=new EditText(this);styleInput(input);input.setSingleLine(true);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
            input.setText(Branding.stationName(db));input.setSelectAllOnFocus(true);
            LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);form.addView(input);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("اسم المحطة").setView(form).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
            dialog.setOnShowListener(a->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()){input.setError("أدخل اسم المحطة");return;}
                db.setSetting("station_name",name);refreshStationBrand();buildSettingsPage();dialog.dismiss();
            }));dialog.show();
        });box.addView(rename,space());
        Button logo=action("اختيار شعار المحطة",false);
        logo.setOnClickListener(v->{try{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,PICK_STATION_LOGO);
        }catch(ActivityNotFoundException e){Toast.makeText(this,"لا يوجد تطبيق لاختيار الصور",Toast.LENGTH_LONG).show();}});
        box.addView(logo,space());
        Button remove=action("استخدام شعار وردية",false);
        remove.setOnClickListener(v->{Branding.removeLogo(this);refreshStationBrand();Toast.makeText(this,"تم استخدام شعار وردية",Toast.LENGTH_SHORT).show();});
        box.addView(remove,space());pages[4].addView(box,space());
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_STATION_LOGO||result!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();
        new Thread(()->{
            try{Branding.importLogo(getApplicationContext(),uri);
                runOnUiThread(()->{if(!isDestroyed()){refreshStationBrand();Toast.makeText(this,"حُفظ شعار المحطة",Toast.LENGTH_SHORT).show();}});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"تعذر حفظ الشعار. اختر صورة أخرى.",Toast.LENGTH_LONG).show();});}
        },"station-logo").start();
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
        pinnedSummaries.setVisibility(selected==0||selected==1?View.VISIBLE:View.GONE);
        fuelLitresBox.setVisibility(selected==0?View.VISIBLE:View.GONE);
        movementSummary.setVisibility(selected==1?View.VISIBLE:View.GONE);
        for(int i=0;i<pages.length;i++)pages[i].setVisibility(i==selected?View.VISIBLE:View.GONE);
        // شاشة الإعدادات لا تحتاج شريط التنقّل السفلي.
        if(navBar!=null)navBar.setVisibility(selected==4?View.GONE:View.VISIBLE);
        int active=selected==2?0:selected==3?2:selected==4?-1:selected;
        for(int i=0;i<tabs.length;i++){
            tabs[i].setBackgroundTintList(null);
            tabs[i].setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),Util.round(i==active?Util.ACCENT:Color.WHITE,dp(17)),null));
            tabs[i].setTextColor(i==active?Util.NAVY:Color.rgb(98,104,109));
            tabs[i].setTypeface(android.graphics.Typeface.DEFAULT,i==active?1:0);
            tabs[i].setSelected(i==active);
        }
        if(selected==3)loadArchive();
        refreshTotals();
    }
    private void refreshShiftDate(){
        if(shiftDateButton==null)return;
        String date=db.shiftDate(shiftId);
        shiftDateButton.setText(ShiftDates.day(date)+"  "+date+"  •  تغيير التاريخ"+
            (db.isHistorical(shiftId)?"\nوردية للأرشيف — لا تغيّر عدادات الطرمبات":""));
    }
    private void chooseShiftDate(){
        java.time.LocalDate date=java.time.LocalDate.parse(db.shiftDate(shiftId));
        DatePickerDialog picker=new DatePickerDialog(this,(view,year,month,day)->{
            String value=java.time.LocalDate.of(year,month+1,day).toString();
            try{
                db.setShiftDate(shiftId,value);loadReadings();refreshTotals();
                if(db.isHistorical(shiftId))new AlertDialog.Builder(this).setTitle("وردية قديمة")
                    .setMessage("ستُحفظ للأرشفة فقط. اضغط على القراءة السابقة لتعديل قراءة وسعر كل طرمبة لهذه الوردية، دون تغيير العدادات الحالية.")
                    .setPositiveButton("حسنًا",null).show();
            }catch(Exception e){Toast.makeText(this,"تعذر تغيير تاريخ الوردية",Toast.LENGTH_LONG).show();}
        },date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
    }
    private void historicalBaseline(long readingId,double previous,double price){
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);
        EditText prev=new EditText(this),rate=new EditText(this);
        styleInput(prev);styleInput(rate);
        prev.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rate.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        prev.setText(fmt(previous));rate.setText(fmt(price));
        box.addView(text("القراءة السابقة للوردية القديمة",14,Util.NAVY,true));box.addView(prev);
        box.addView(text("سعر اللتر وقت الوردية",14,Util.NAVY,true));box.addView(rate);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("تعديل هذه الوردية فقط").setView(box)
            .setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            if(prev.getText().toString().trim().isEmpty()||rate.getText().toString().trim().isEmpty()||
                !db.saveHistoricalBaseline(shiftId,readingId,Util.number(prev.getText().toString()),Util.number(rate.getText().toString()))){
                Toast.makeText(this,"راجع القراءة والسعر؛ السابقة لا تتجاوز الحالية المحفوظة",Toast.LENGTH_LONG).show();return;
            }
            dialog.dismiss();loadReadings();refreshTotals();
        }));
        dialog.show();
    }
    private void loadArchive(){
        pages[3].removeAllViews();pages[3].addView(Util.label(this,"أرشيف وردياتي"));
        try(Cursor c=db.archive(workerId,false)){
            if(c.getCount()==0)pages[3].addView(Util.card(this,"لا توجد ورديات محفوظة بعد."),Util.spaced());
            while(c.moveToNext()){
                String state=c.getString(3);
                String label="OPEN".equals(state)?"جارية الآن":"مُغلقة";
                final long archivedId=c.getLong(0);
                LinearLayout card=panel(Color.WHITE);
                card.addView(text(ShiftDates.day(c.getString(8)),21,0xff16733c,true),space());
                card.addView(text("وردية #"+archivedId+"  •  "+label,16,Util.NAVY,true));
                card.addView(text("تاريخ الوردية: "+c.getString(8),15,Util.NAVY,true));
                card.addView(text("تاريخ الإدخال: "+c.getString(2),13,0xff667078,false));
                card.addView(text("المبيعات: "+money(c.getDouble(4))+" ريال  •  الباقي: "+money(c.getDouble(5)),14,Util.NAVY,false),space());
                card.addView(text(Math.abs(c.getDouble(5))<0.01?"اضغط لفتحها ومراجعتها":"غير مطابقة — اضغط لفتحها ومراجعتها",12,Math.abs(c.getDouble(5))<0.01?0xff667078:0xffb3261e,false));
                final boolean live="OPEN".equals(state)||"RETURNED".equals(state);
                card.setClickable(true);
                card.setOnClickListener(v->openArchived(archivedId,live));
                card.setOnLongClickListener(v->{chooseReport(archivedId);return true;});
                pages[3].addView(card,Util.spaced());
            }
        }
    }
    @Override public void onBackPressed(){
        // مغادرة الإعدادات تُنزل الأسعار والطرمبات على الوردية المفتوحة.
        if(page==4){db.syncShiftWithSettings(shiftId);loadReadings();refreshTotals();}
        // جهاز العامل بلا واجهة رئيسية: الرجوع من الإعدادات يعود للوردية دائمًا.
        if(page==4&&(!settingsOnly||db.workerDevice())){
            showPage(settingsReturnPage);
            if(screenScroll!=null)screenScroll.smoothScrollTo(0,0);
        }else super.onBackPressed();
    }
    private class SettingsGear extends android.graphics.drawable.Drawable{
        final android.graphics.Paint ink=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        public void draw(android.graphics.Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);
            c.scale(getBounds().width()/24f,getBounds().height()/24f);
            ink.setColor(Color.WHITE);ink.setStyle(android.graphics.Paint.Style.STROKE);
            ink.setStrokeWidth(2.3f);ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            c.drawCircle(12,12,7,ink);c.drawCircle(12,12,2.8f,ink);
            for(int i=0;i<8;i++){
                double a=Math.PI*i/4;
                c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,
                    12+(float)Math.cos(a)*9.5f,12+(float)Math.sin(a)*9.5f,ink);
            }
            c.restore();
        }
        public void setAlpha(int a){ink.setAlpha(a);}
        public void setColorFilter(android.graphics.ColorFilter f){ink.setColorFilter(f);}
        public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
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
    private Button action(String name,boolean primary){Button b=new Button(this);b.setText(name);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:Util.NAVY);b.setTypeface(android.graphics.Typeface.DEFAULT,primary?1:0);b.setMinHeight(dp(50));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setStateListAnimator(null);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),Util.round(primary?Util.ACCENT:Util.ACCENT_SOFT,dp(12)),null));return b;}
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
    /**
     * كل الأسماء المحفوظة مهما كان نوع الحركة، فالاسم الواحد قد يتكرر
     * بين المقبوضات والديون والمخاريج. أسماء النوع الحالي تُقترح أولًا.
     */
    private ArrayList<String> rememberedNames(String type){
        ArrayList<String> names=new ArrayList<>();
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT name,MAX(CASE WHEN type=? THEN 1 ELSE 0 END) AS same FROM remembered_names "+
                "GROUP BY name ORDER BY same DESC,name",new String[]{type})){
            while(c.moveToNext())names.add(c.getString(0));
        }
        return names;
    }
    private void loadReadings(){
        refreshShiftDate();
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
            if(db.isHistorical(shiftId)){
                final long historicalReading=c.getLong(0);
                final double historicalPrevious=c.getDouble(3),historicalPrice=c.getDouble(5);
                previous.setContentDescription("تعديل قراءة وسعر هذه الوردية القديمة فقط");
                previous.setOnClickListener(v->historicalBaseline(historicalReading,historicalPrevious,historicalPrice));
            }
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
                refreshTotals();
            }
        });
    }
    private boolean saveReadings(){
        boolean ok=true;
        for(ReadingInput r:inputs){
            if(!r.current.isEnabled())continue;
            String curr=r.current.getText().toString().trim();
            if(curr.isEmpty()){
                if(db.clearReading(r.id,shiftId))readingDrafts().edit().remove(draftKey(r.id,"current")).apply();
                else ok=false;
            }else{
                if(db.saveReading(r.id,Util.number(curr)))
                    readingDrafts().edit().remove(draftKey(r.id,"current")).apply();
                else ok=false;
            }
        }
        Toast.makeText(this,ok?"تم الحفظ داخل الهاتف":"رفضت قراءة حالية أقل من السابقة",Toast.LENGTH_SHORT).show();
        loadReadings();refreshTotals();
        return ok;}
    /** يعيد رسم البطاقات الأربع لتحديث مجاميعها. */
    private void buildTypeTiles(){
        if(typePicker==null)return;
        typePicker.removeAllViews();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER);
        top.addView(typeTile(0),tileCell());
        top.addView(typeTile(1),tileCell());
        typePicker.addView(top);
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2);gap.setMargins(0,dp(10),0,0);
        bottom.addView(typeTile(2),tileCell());
        bottom.addView(typeTile(3),tileCell());
        typePicker.addView(bottom,gap);
    }

    private LinearLayout.LayoutParams tileCell(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(108),1);
        p.setMargins(dp(5),0,dp(5),0);
        return p;
    }

    /** بطاقة نوع حركة: أيقونة ملوّنة واسم ومجموع النوع في هذه الوردية. */
    private LinearLayout typeTile(final int index){
        final String type=movementTypes[index];
        int tint=typeColor(type);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6),dp(10),dp(6),dp(10));
        box.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x22000000),
            Util.round(typeSoft(type),dp(16)),null));
        box.setClickable(true);
        FrameLayout disc=new FrameLayout(this);
        disc.setBackground(Util.round(Color.WHITE,dp(19)));
        ImageView art=new ImageView(this);
        MoveIcon icon=new MoveIcon(index,tint);
        icon.setBounds(0,0,dp(22),dp(22));
        art.setImageDrawable(icon);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(22),dp(22));
        ip.gravity=Gravity.CENTER;
        disc.addView(art,ip);
        box.addView(disc,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView name=text(movementLabels[index],14,tint,true);
        name.setGravity(Gravity.CENTER);name.setPadding(0,dp(7),0,dp(2));
        box.addView(name,new LinearLayout.LayoutParams(-1,-2));
        TextView sum=text(Calc.money(db.total(shiftId,type)),12,tint,false);
        sum.setGravity(Gravity.CENTER);sum.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(sum,new LinearLayout.LayoutParams(-1,-2));
        box.setOnClickListener(v->quickEntry(index));
        return box;
    }

    private int typeColor(String type){
        if("COLLECTION".equals(type))return Util.GREEN;
        if("CASH".equals(type))return Util.NAVY;
        if("DEBT".equals(type))return Util.RED;
        return 0xffB86A00;
    }

    private int typeSoft(String type){
        int c=typeColor(type);
        return Color.argb(26,Color.red(c),Color.green(c),Color.blue(c));
    }

    /**
     * نافذة إدخال تبقى مفتوحة: الاسم ← التالي ← المبلغ ← التالي فتُحفظ الحركة
     * ويعود المؤشر للاسم لتسجيل حركة أخرى، ولا تُغلق إلا بزر إلغاء.
     */
    private void quickEntry(final int index){
        final String type=movementTypes[index];
        final String label=movementLabels[index];
        final int tint=typeColor(type);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22),dp(6),dp(22),0);

        final TextView running=text("",13,tint,true);
        running.setPadding(dp(12),dp(9),dp(12),dp(9));
        running.setBackground(Util.round(typeSoft(type),dp(11)));
        box.addView(running,space());

        box.addView(text("الاسم أو البيان",12,0xff7c8186,false));
        final AutoCompleteTextView name=new AutoCompleteTextView(this);
        styleInput(name);
        name.setHint("اكتب الاسم ثم التالي");
        name.setThreshold(1);
        name.setSingleLine(true);
        name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        name.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,rememberedNames(type)));
        box.addView(name,space());

        box.addView(text("المبلغ • ر.ي",12,0xff7c8186,false));
        final EditText amount=new EditText(this);
        styleInput(amount);
        amount.setHint("0");
        amount.setSingleLine(true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        amount.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        box.addView(amount,space());

        final TextView saved=text("",12,Util.GREEN,true);
        saved.setPadding(0,dp(4),0,0);
        box.addView(saved);

        ScrollView form=new ScrollView(this);form.addView(box);
        final AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("إضافة "+label)
            .setView(form)
            .setPositiveButton("التالي",null)
            .setNegativeButton("إلغاء",null)
            .create();

        final Runnable refreshRunning=()->running.setText(
            "مجموع "+label+" في هذه الوردية: "+Calc.money(db.total(shiftId,type))+" ر.ي");
        refreshRunning.run();

        // خطوة واحدة: إن كان المؤشر على الاسم ينتقل للمبلغ، وإلا يحفظ ويعيد الكرّة.
        final Runnable step=()->{
            String value=name.getText().toString().trim();
            if(value.isEmpty()){name.setError("أدخل الاسم");name.requestFocus();return;}
            if(!amount.hasFocus()&&amount.getText().toString().trim().isEmpty()){
                amount.requestFocus();return;
            }
            double money=Util.number(amount.getText().toString());
            if(money<=0||Double.isNaN(money)||Double.isInfinite(money)){
                amount.setError("أدخل مبلغًا صحيحًا");amount.requestFocus();return;
            }
            db.addMovement(shiftId,type,value,money);
            loadMovements();refreshTotals();
            saved.setText("✓ سُجّلت: "+value+"  •  "+Calc.money(money)+" ر.ي");
            refreshRunning.run();
            name.setText("");amount.setText("");
            name.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,rememberedNames(type)));
            name.requestFocus();
        };

        name.setOnEditorActionListener((v,actionId,event)->{amount.requestFocus();return true;});
        amount.setOnEditorActionListener((v,actionId,event)->{step.run();return true;});
        dialog.setOnShowListener(x->{
            Button next=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            next.setOnClickListener(v->step.run());
            name.requestFocus();
        });
        dialog.setOnDismissListener(x->{loadMovements();refreshTotals();showPage(1);});
        dialog.show();
    }

    /** أيقونات أنواع الحركات الأربعة. */
    private class MoveIcon extends android.graphics.drawable.Drawable{
        final int kind,tint;final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        MoveIcon(int kind,int tint){this.kind=kind;this.tint=tint;}
        public void draw(android.graphics.Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);
            c.scale(getBounds().width()/24f,getBounds().height()/24f);
            paint.setColor(tint);paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            if(kind==0){
                // مقبوضات: كف تستقبل عملة.
                c.drawCircle(12,8,4.2f,paint);
                c.drawLine(12,5.6f,12,10.4f,paint);
                c.drawArc(4.5f,14,19.5f,22,200,140,false,paint);
            }else if(kind==1){
                // نقد مسلّم: ورقة نقدية وسهم للأعلى.
                c.drawRoundRect(3,10.5f,21,19.5f,1.8f,1.8f,paint);
                c.drawCircle(12,15,2.4f,paint);
                c.drawLine(12,8.5f,12,2.8f,paint);
                c.drawLine(9,5.6f,12,2.8f,paint);
                c.drawLine(15,5.6f,12,2.8f,paint);
            }else if(kind==2){
                // ديون: دفتر حساب وقلم.
                c.drawRoundRect(4,3,17,21,1.8f,1.8f,paint);
                c.drawLine(7.5f,8,13.5f,8,paint);
                c.drawLine(7.5f,12,13.5f,12,paint);
                c.drawLine(7.5f,16,11,16,paint);
                c.drawLine(19,6,21.5f,8.5f,paint);
                c.drawLine(19,6,14.5f,10.5f,paint);
                c.drawLine(21.5f,8.5f,17,13,paint);
            }else{
                // مخاريج: فاتورة بحافة مسنّنة.
                android.graphics.Path r=new android.graphics.Path();
                r.moveTo(5,2.5f);r.lineTo(19,2.5f);r.lineTo(19,21.5f);
                r.lineTo(16.5f,19.6f);r.lineTo(14,21.5f);r.lineTo(11.5f,19.6f);
                r.lineTo(9,21.5f);r.lineTo(6.5f,19.6f);r.lineTo(5,21.5f);r.close();
                c.drawPath(r,paint);
                c.drawLine(8.5f,8,15.5f,8,paint);
                c.drawLine(8.5f,12,15.5f,12,paint);
                c.drawLine(8.5f,15.6f,13,15.6f,paint);
            }
            c.restore();
        }
        public void setAlpha(int a){paint.setAlpha(a);}
        public void setColorFilter(android.graphics.ColorFilter f){paint.setColorFilter(f);}
        public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }

    private void movementDialog(String type,String label){LinearLayout box=new LinearLayout(this);box.setPadding(30,10,30,0);box.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("الاسم أو البيان");EditText amount=new EditText(this);amount.setHint("المبلغ");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(name);box.addView(amount);new AlertDialog.Builder(this).setTitle("إضافة "+label).setView(box).setPositiveButton("حفظ",(d,w)->{if(name.getText().toString().trim().isEmpty()||Util.number(amount.getText().toString())<=0){Toast.makeText(this,"أدخل الاسم والمبلغ",Toast.LENGTH_SHORT).show();return;}db.addMovement(shiftId,type,name.getText().toString(),Util.number(amount.getText().toString()));loadMovements();refreshTotals();}).setNegativeButton("إلغاء",null).show();}
    private void loadMovements(){
        movementsBox.removeAllViews();int count=0;
        try(Cursor c=db.movements(shiftId)){while(c.moveToNext()){
            String type=c.getString(1);count++;
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
    private Double visibleCurrent(Cursor c){
        String key=draftKey(c.getLong(0),"current");
        // A stopped meter displays only its stored reading.
        if(c.getInt(7)!=0&&readingDrafts().contains(key)){
            String draft=readingDrafts().getString(key,"").trim();
            if(draft.isEmpty())return null;
            double value=Util.number(draft);
            return Double.isNaN(value)||Double.isInfinite(value)?null:value;
        }
        return c.isNull(4)?null:c.getDouble(4);
    }
    private boolean hasReadingDrafts(){
        try(Cursor c=db.shiftReadings(shiftId)){while(c.moveToNext())if(c.getInt(7)!=0&&readingDrafts().contains(draftKey(c.getLong(0),"current")))return true;}
        return false;
    }
    private double visibleSales(){
        double total=0;
        try(Cursor c=db.shiftReadings(shiftId)){
            while(c.moveToNext()){
                Double current=visibleCurrent(c);
                if(current!=null&&current>=c.getDouble(3)&&c.getDouble(5)>0)total+=(current-c.getDouble(3))*c.getDouble(5);
            }
        }
        return total;
    }
    private void refreshTotals(){
        refreshMovementSummary();
        buildTypeTiles();
        double[] values={visibleSales(),db.total(shiftId,"COLLECTION"),db.total(shiftId,"CASH"),db.total(shiftId,"DEBT"),db.total(shiftId,"EXPENSE")};
        double bal=Calc.balance(values[0],values[1],values[2],values[3],values[4]);String issue=db.validateShift(shiftId);
        if(hasReadingDrafts())issue="مسودة قراءات — احفظ لتأكيد الحساب";
        if(headerBalance!=null){
            headerBalance.setText("الباقي"+System.lineSeparator()+money(bal)+" ر.ي"+(issue.isEmpty()?"":System.lineSeparator()+"غير مكتملة"));
            headerBalance.setTextColor(!issue.isEmpty()?0xffCFE2FA:Math.abs(bal)<0.01?0xffb9e5bd:0xffffb8b8);
            headerBalance.setContentDescription("باقي الوردية الحالية "+money(bal)+" ريال");
        }
        refreshFuelLitres();
        if(balanceText!=null){
            boolean matched=issue.isEmpty()&&Math.abs(bal)<0.01;
            String nl=System.lineSeparator();
            balanceText.setText((!issue.isEmpty()?"الوردية غير مكتملة":matched?"✓ الوردية مطابقة":"يوجد فرق في الوردية")+nl+nl+"الباقي"+nl+money(bal)+" ر.ي"+(!issue.isEmpty()?nl+issue:""));
            balanceText.setTextColor(matched?0xff3f7542:!issue.isEmpty()?Util.NAVY:Util.RED);
            balanceText.setBackground(Util.round(matched?0xffe3efe3:!issue.isEmpty()?Util.ACCENT_SOFT:0xfffce9e8,dp(16)));
        }
        if(totalsBox!=null){totalsBox.removeAllViews();String[] labels={"المبيعات","المقبوضات","النقد المسلّم","الديون","المخاريج"};
            for(int i=0;i<5;i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(13),0,dp(13));row.addView(text(labels[i],17,Util.NAVY,false),new LinearLayout.LayoutParams(0,-2,1));TextView amount=text((i==0?"":i==1?"+ ":"− ")+money(values[i]),18,i==0?Util.NAVY:i==1?Util.GREEN:Util.RED,true);amount.setTextDirection(View.TEXT_DIRECTION_LTR);row.addView(amount);totalsBox.addView(row);if(i<4){View line=new View(this);line.setBackgroundColor(0xffeceef0);totalsBox.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}}
        }
    }
    /** Uses the same visible reading rows as sales; missing counters stay incomplete. */
    private void refreshMovementSummary(){
        if(movementSummary==null)return;
        movementSummary.removeAllViews();
        LinearLayout headers=new LinearLayout(this),values=new LinearLayout(this);
        headers.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        values.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        headers.setBackgroundColor(Util.ACCENT_SOFT);
        String[] names={"المخاريج","المقبوضات","الديون","الفلوس"};
        String[] types={"EXPENSE","COLLECTION","DEBT","CASH"};
        for(int i=0;i<types.length;i++){
            TextView name=text(names[i],13,Util.NAVY,true);
            name.setGravity(Gravity.CENTER);name.setPadding(dp(2),dp(5),dp(2),dp(5));
            TextView amount=text(money(db.total(shiftId,types[i])),16,Util.NAVY,true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);amount.setGravity(Gravity.CENTER);
            amount.setPadding(dp(2),dp(6),dp(2),dp(6));amount.setMaxLines(1);
            amount.setAutoSizeTextTypeUniformWithConfiguration(10,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            headers.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            values.addView(amount,new LinearLayout.LayoutParams(0,dp(36),1));
        }
        movementSummary.addView(headers);movementSummary.addView(values);
    }
    private void refreshFuelLitres(){
        LinkedHashMap<String,double[]> totals=new LinkedHashMap<>();
        try(Cursor c=db.shiftReadings(shiftId)){
            while(c.moveToNext()){
                String fuel=c.getString(2).trim();
                double[] total=totals.get(fuel);
                if(total==null){total=new double[2];totals.put(fuel,total);}
                Double current=visibleCurrent(c);
                if(current==null||current<c.getDouble(3)){total[1]++;continue;}
                total[0]+=current-c.getDouble(3);
            }
        }
        for(LinearLayout box:new LinearLayout[]{fuelLitresBox,reconciliationLitresBox}){
            if(box==null)continue;
            box.removeAllViews();
            if(box==fuelLitresBox){
                LinearLayout headers=new LinearLayout(this),numbers=new LinearLayout(this);
                headers.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                numbers.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                headers.setBackgroundColor(Util.ACCENT_SOFT);
                for(Map.Entry<String,double[]> entry:totals.entrySet()){
                    TextView label=text(entry.getKey(),16,Util.NAVY,true);
                    label.setGravity(Gravity.CENTER);label.setPadding(dp(4),dp(5),dp(4),dp(5));
                    TextView value=text(money(entry.getValue()[0]),18,Util.NAVY,true);
                    value.setTextDirection(View.TEXT_DIRECTION_LTR);value.setGravity(Gravity.CENTER);
                    value.setPadding(dp(4),dp(6),dp(4),dp(6));
                    headers.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                    numbers.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                }
                box.addView(headers);box.addView(numbers);
                continue;
            }
            box.addView(text("إجمالي اللترات حسب النوع",18,Util.NAVY,true),space());
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
            box.addView(text("من فرق القراءات الظاهرة لهذه الوردية",11,0xff777d84,false),space());
        }
    }
    private String money(double value){return String.format(Locale.US,value==Math.rint(value)?"%,.0f":"%,.2f",value);}
    /** يحفظ تقرير الوردية PDF ويفتح قائمة المشاركة. */
    private void exportPdf(){
        if(!saveReadings())return;
        if(!reportAllowed(shiftId))return;
        try{
            sharePdfOrThrow(shiftId);
        }catch(Exception e){Toast.makeText(this,"تعذر إنشاء ملف PDF",Toast.LENGTH_LONG).show();}
    }
    private void exportExcel(){
        if(!saveReadings())return;
        if(!reportAllowed(shiftId))return;
        shareExcel(shiftId);
    }
    /** لا يُصدَّر تقرير إلا لوردية مطابقة تمامًا (الباقي = صفر). */
    private boolean reportAllowed(long id){
        String issue=db.validateShift(id);
        if(!issue.isEmpty()){
            new AlertDialog.Builder(this).setTitle("لا يمكن إخراج التقرير")
                .setMessage(issue).setPositiveButton("حسنًا",null).show();
            return false;
        }
        double bal=db.balance(id);
        if(!Calc.matched(bal)){
            new AlertDialog.Builder(this).setTitle("لا يمكن إخراج التقرير")
                .setMessage("الوردية غير مطابقة. الباقي "+money(bal)+" ريال.\nيجب أن يكون الباقي صفرًا قبل حفظ أو مشاركة PDF أو Excel.")
                .setPositiveButton("حسنًا",null).show();
            return false;
        }
        return true;
    }
    /**
     * يفتح وردية من الأرشيف في نفس خانات الطرمبات والحركات.
     * المُغلقة تُفتح للتعديل بعد تأكيد، فيُعكس قيدها ويُلغى ترحيلها.
     */
    private void openArchived(final long id,boolean live){
        if(live){ switchTo(id); return; }
        new AlertDialog.Builder(this).setTitle("وردية #"+id)
            .setMessage("تفتح الوردية بقراءاتها وحركاتها كما سُجّلت، وتصير قابلة للتعديل.\n\n"
                + "سيُعكس قيدها المحاسبي ويُلغى ترحيلها حتى تعتمدها من جديد، ويُحفظ ذلك في سجل التدقيق.")
            .setPositiveButton("فتح للتعديل",(d,w)->{
                try{
                    db.reopenShift(id,"مراجعة المدير");
                    switchTo(id);
                    Toast.makeText(this,"فُتحت الوردية #"+id+" للتعديل",Toast.LENGTH_LONG).show();
                }catch(Exception e){
                    Toast.makeText(this,String.valueOf(e.getMessage()),Toast.LENGTH_LONG).show();
                }
            })
            .setNeutralButton("تقرير",(d,w)->chooseReport(id))
            .setNegativeButton("إلغاء",null).show();
    }

    /** ينقل الشاشة كلها إلى وردية أخرى ويعرض قراءاتها وحركاتها. */
    private void switchTo(long id){
        shiftId=id;
        loadReadings();loadMovements();refreshTotals();
        showPage(0);
        if(screenScroll!=null)screenScroll.smoothScrollTo(0,0);
    }

    private void chooseReport(long id){
        if(id==shiftId&&!saveReadings())return;
        new AlertDialog.Builder(this).setTitle("مشاركة تقرير الوردية")
            .setItems(new String[]{"PDF","Excel (.xlsx)","إرسال للمدير"},(d,which)->{
                if(id==shiftId&&!saveReadings())return;
                // الإرسال للمدير متاح دائمًا؛ التقارير تحتاج وردية مطابقة.
                if(which==2){sendShift(id);return;}
                if(!reportAllowed(id))return;
                if(which==0)sharePdf(id);else shareExcel(id);
            }).show();
    }
    private void shareExcel(long id){
        if(!reportAllowed(id))return;
        Toast.makeText(this,"جارٍ تجهيز ملف Excel",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                java.io.File file=new ExcelReport(this,db).build(id);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    try{
                        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
                        Intent intent=new Intent(Intent.ACTION_SEND);
                        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        intent.putExtra(Intent.EXTRA_STREAM,uri);
                        intent.putExtra(Intent.EXTRA_SUBJECT,"وردية "+Branding.stationName(db)+" #"+id);
                        intent.setClipData(android.content.ClipData.newRawUri("تقرير الوردية",uri));
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent,"حفظ أو مشاركة Excel"));
                    }catch(Exception e){Toast.makeText(this,"تعذرت مشاركة ملف Excel",Toast.LENGTH_LONG).show();}
                });
            }catch(Exception e){
                runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"تعذر إنشاء ملف Excel",Toast.LENGTH_LONG).show();});
            }
        }).start();
    }
    private void sharePdf(long id){
        try{ sharePdfOrThrow(id); }
        catch(Exception e){Toast.makeText(this,"تعذر إنشاء ملف PDF",Toast.LENGTH_LONG).show();}
    }
    private void sharePdfOrThrow(long id)throws Exception{
        if(!reportAllowed(id))return;
        java.io.File file=new PdfReport(this,db).build(id);
        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.putExtra(Intent.EXTRA_SUBJECT,"وردية "+Branding.stationName(db)+" #"+id);
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
                .setMessage(db.isHistorical(shiftId)?"ستُحفظ الوردية القديمة في الأرشيف دون تغيير قراءات الطرمبات الحالية.":"الوردية مطابقة. ستُحفظ في الأرشيف وتبدأ وردية جديدة بقراءات الإغلاق.")
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
        final boolean historical=db.isHistorical(closed);
        final boolean matched=reason.isEmpty();
        db.submit(closed,workerId,reason);
        // المطابقة وحدها تُعتمد؛ غير المطابقة تبقى «مُرسلة» بانتظار المدير.
        if(matched)db.approve(closed);else db.closeUnmatched(closed);
        String posted=db.postShift(closed,db.defaultCashbox());
        // قيد الوردية المزدوج: يُرفض إن كان فيه فرق بلا تعليل، ولا يُكتب إلا متوازنًا.
        String journalNote="";
        try{ db.journalShift(closed); }
        catch(Exception e){ journalNote="\n\n⚠ لم يُسجَّل القيد المحاسبي: "+e.getMessage(); }
        shiftId=db.openSoloShift(workerId);
        loadReadings();loadMovements();refreshTotals();showPage(0);
        String base=historical?"حُفظت الوردية القديمة دون تغيير قراءات الطرمبات الحالية.":"بدأت وردية جديدة بقراءات الإغلاق.";
        if(!posted.isEmpty())base=base+"\n\nرُحّلت الوردية:\n"+posted;
        base=base+journalNote;
        AlertDialog.Builder done=new AlertDialog.Builder(this).setTitle("حُفظت الوردية #"+closed);
        if(reason.isEmpty()){
            done.setMessage(base+"\nأرسل الوردية للمدير، أو احفظ تقريرها.")
                .setPositiveButton("إرسال للمدير",(d,w)->sendShift(closed))
                .setNeutralButton("حفظ PDF",(d,w)->sharePdf(closed))
                .setNegativeButton("لاحقًا",null);
        }else{
            done.setMessage(base+"\nالوردية غير مطابقة: أرسلها للمدير ليراجعها ويعتمدها.")
                .setPositiveButton("إرسال للمدير",(d,w)->sendShift(closed))
                .setNegativeButton("لاحقًا",null);
        }
        done.show();
    }
    /** يرفع الوردية إلى المدير عبر قناة الربط، بلا ملفات ولا واتساب. */
    void sendShift(long id){
        Relay relay=new Relay(this);
        if(!relay.linked()){
            new AlertDialog.Builder(this).setTitle("الجهاز غير مربوط")
                .setMessage("اطلب رمز الربط من المدير، ثم أدخله مرة واحدة من الإعدادات.")
                .setPositiveButton("فتح الإعدادات",(d,w)->{settingsReturnPage=page;showPage(4);})
                .setNegativeButton("لاحقًا",null).show();
            return;
        }
        relay.send(id,true);
    }

    /** نسخة الملف محفوظة للطوارئ حين لا يتوفر إنترنت. */
    void sendShiftAsFile(long id){
        try{
            ShiftFile.Shift data=db.exportShift(id);
            String text=ShiftFile.write(data);
            java.io.File dir=new java.io.File(getCacheDir(),"exports");
            if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("تعذر إنشاء مجلد التصدير");
            java.io.File file=new java.io.File(dir,ShiftFile.fileName(data));
            try(java.io.OutputStream out=new java.io.FileOutputStream(file)){
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM,uri);
            intent.putExtra(Intent.EXTRA_SUBJECT,"وردية "+data.worker+" — "+data.date);
            intent.setClipData(android.content.ClipData.newRawUri("وردية",uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,"إرسال الوردية للمدير"));
        }catch(Exception e){
            Toast.makeText(this,"تعذر تجهيز ملف الوردية: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private String arabicType(String t){if("COLLECTION".equals(t))return "مقبوضات";if("CASH".equals(t))return "نقد مسلّم";if("DEBT".equals(t))return "ديون";return "مخاريج";}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
}
