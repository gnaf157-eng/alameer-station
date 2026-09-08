package com.alameer.invoicecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReviewActivity extends Activity {
    Db db;
    long invoiceId=0;
    String imagePath="";
    String dateIso=Util.today();
    String[] queue=null;
    int queueIndex=0;
    LinearLayout itemsBox;
    ImageView preview;
    EditText dateField,storeField;
    TextView totalField,statusText;
    ProgressBar progressBar;
    Bitmap bmp=null;
    final ArrayList<Row> rows=new ArrayList<>();
    final ExecutorService pool=Executors.newSingleThreadExecutor();

    static class Row{
        AutoCompleteTextView name;
        EditText qty,unit,price;
        LinearLayout root;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Util.installCrashReporter(this);
        db=new Db(this);
        invoiceId=getIntent().getLongExtra("invoiceId",0);
        queue=getIntent().getStringArrayExtra("queue");
        if(queue!=null)queueIndex=getIntent().getIntExtra("queueIndex",1);
        String path=getIntent().getStringExtra("imagePath");
        if(path!=null){
            bmp=Util.decodeScaledFile(new File(path),1200);
            if(bmp!=null)imagePath=path;
        }
        build();
        if(invoiceId>0){
            String[] m=db.metaOf(invoiceId);
            if(m==null){
                Toast.makeText(this,"الفاتورة غير موجودة",Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            dateIso=m[0];
            storeField.setText(m[1]);
            imagePath=m[3];
            if(bmp==null&&imagePath!=null&&!imagePath.isEmpty())bmp=Util.decodeScaledFile(new File(imagePath),1200);
            showPreview();
            statusText.setText("تعديل فاتورة محفوظة — عدّل ثم احفظ");
            for(Db.Item it:db.itemsOf(invoiceId))addRow(it.name,it.qty,it.unit,it.price);
            refreshTotal();
        }else if(bmp!=null){
            showPreview();
            startOcr(new File(imagePath));
        }else{
            statusText.setText("لا توجد صورة — أضف الأصناف يدويًا");
            addRow("",0,"",0);
        }
    }

    @Override protected void onDestroy(){
        pool.shutdownNow();
        super.onDestroy();
    }

    private void build(){
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        String sub=queue!=null?("الفاتورة "+queueIndex+" من "+queue.length):invoiceId>0?"تعديل":"بعد التقاط الصورة";
        shell.addView(Util.brandBar(this,"مراجعة الفاتورة",sub,v->{
            if(queue!=null&&queueIndex<queue.length){
                new AlertDialog.Builder(ReviewActivity.this)
                    .setTitle("تخطي الفاتورة؟")
                    .setMessage("لن تُحفظ هذه الفاتورة. الانتقال إلى الفاتورة التالية؟")
                    .setPositiveButton("التالي",(d,w)->launchNext())
                    .setNegativeButton("إنهاء الكل",null)
                    .show();
            }else finish();
        }));
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Util.dp(this,14),Util.dp(this,6),Util.dp(this,14),Util.dp(this,16));

        preview=new ImageView(this);
        preview.setBackgroundColor(0xffe8eaec);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(View.GONE);
        content.addView(preview,new LinearLayout.LayoutParams(-1,Util.dp(this,110)));

        LinearLayout meta=Util.panel(this,Color.WHITE);
        meta.addView(Util.text(this,"تاريخ الفاتورة",13,Util.MUTED,false));
        dateField=new EditText(this);
        Util.styleInput(dateField,this);
        dateField.setText(Util.fmtDate(dateIso));
        dateField.setTextDirection(View.TEXT_DIRECTION_LTR);
        dateField.setFocusable(false);
        dateField.setOnClickListener(v->pickDate());
        meta.addView(dateField,Util.spaced(this));
        meta.addView(Util.text(this,"اسم المحل / المورد (اختياري)",13,Util.MUTED,false));
        storeField=new EditText(this);
        Util.styleInput(storeField,this);
        storeField.setHint("مثال: بقالة النور");
        storeField.setHintTextColor(0xff9aa0a6);
        meta.addView(storeField,Util.spaced(this));
        LinearLayout totalRow=new LinearLayout(this);
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.addView(Util.text(this,"الإجمالي المحسوب (عدد × سعر)",13,Util.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        totalField=Util.text(this,"0.00 ج.م",16,Util.GREEN,true);
        totalRow.addView(totalField);
        meta.addView(totalRow);
        content.addView(meta,Util.spaced(this));

        LinearLayout itemsPanel=Util.panel(this,Color.WHITE);
        itemsPanel.addView(Util.text(this,"الأصناف",18,Util.NAVY,true));
        itemsPanel.addView(Util.text(this,"عدّل الاسم أو العدد أو الوحدة أو السعر — واحذف ما لا ينفع",12,Util.MUTED,false),Util.spaced(this));
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Util.text(this,"الصنف",12,Util.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(Util.text(this,"عدد",12,Util.MUTED,false),new LinearLayout.LayoutParams(Util.dp(this,62),-2));
        header.addView(Util.text(this,"وحدة",12,Util.MUTED,false),new LinearLayout.LayoutParams(Util.dp(this,58),-2));
        header.addView(Util.text(this,"سعر",12,Util.MUTED,false),new LinearLayout.LayoutParams(Util.dp(this,70),-2));
        header.addView(new View(this),new LinearLayout.LayoutParams(Util.dp(this,40),-2));
        itemsPanel.addView(header,Util.spaced(this));
        itemsBox=new LinearLayout(this);
        itemsBox.setOrientation(LinearLayout.VERTICAL);
        itemsPanel.addView(itemsBox);
        Button addRowBtn=Util.button(this,"＋  إضافة صنف يدويًا");
        addRowBtn.setOnClickListener(v->addRow("",0,"",0));
        itemsPanel.addView(addRowBtn,Util.spaced(this));
        content.addView(itemsPanel,Util.spaced(this));

        progressBar=new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        content.addView(progressBar,new LinearLayout.LayoutParams(-1,-2));
        statusText=Util.text(this,"",14,Util.NAVY,false);
        statusText.setPadding(Util.dp(this,4),Util.dp(this,8),Util.dp(this,4),Util.dp(this,4));
        content.addView(statusText,Util.spaced(this));

        Button save=Util.goldButton(this,"✓  حفظ الفاتورة");
        save.setOnClickListener(v->save());
        content.addView(save);

        scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showPreview(){
        if(bmp!=null){
            preview.setImageBitmap(bmp);
            preview.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Util.dp(this,110));
            p.setMargins(0,0,0,Util.dp(this,10));
            preview.setLayoutParams(p);
        }
    }

    private void pickDate(){
        int y,m,d;
        if(dateIso!=null&&dateIso.length()>=10){
            try{
                y=Integer.parseInt(dateIso.substring(0,4));
                m=Integer.parseInt(dateIso.substring(5,7))-1;
                d=Integer.parseInt(dateIso.substring(8,10));
            }catch(Exception e){y=2026;m=0;d=1;}
        }else{
            Calendar c=Calendar.getInstance();
            y=c.get(Calendar.YEAR);m=c.get(Calendar.MONTH);d=c.get(Calendar.DAY_OF_MONTH);
        }
        new DatePickerDialog(this,(v,yy,mm,dd)->{
            dateIso=String.format(Locale.US,"%04d-%02d-%02d",yy,mm+1,dd);
            dateField.setText(Util.fmtDate(dateIso));
        },y,m,d).show();
    }

    private void addRow(String n,double q,String u,double p){
        Row r=new Row();
        r.root=new LinearLayout(this);
        r.root.setOrientation(LinearLayout.HORIZONTAL);
        r.root.setGravity(Gravity.CENTER_VERTICAL);
        r.name=new AutoCompleteTextView(this);
        Util.styleInput(r.name,this);
        r.name.setHint("اسم الصنف");
        r.name.setHintTextColor(0xff9aa0a6);
        r.name.setThreshold(1);
        r.name.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,db.itemNames()));
        r.qty=new EditText(this);
        Util.styleInput(r.qty,this);
        r.qty.setHint("عدد");
        r.qty.setHintTextColor(0xff9aa0a6);
        r.qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        r.qty.setTextDirection(View.TEXT_DIRECTION_LTR);
        r.unit=new EditText(this);
        Util.styleInput(r.unit,this);
        r.unit.setHint("وحدة");
        r.unit.setHintTextColor(0xff9aa0a6);
        r.price=new EditText(this);
        Util.styleInput(r.price,this);
        r.price.setHint("سعر");
        r.price.setHintTextColor(0xff9aa0a6);
        r.price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        r.price.setTextDirection(View.TEXT_DIRECTION_LTR);
        Button del=new Button(this);
        del.setText("✕");
        del.setAllCaps(false);
        del.setTextSize(14);
        del.setTextColor(Color.WHITE);
        del.setMinHeight(0);
        del.setMinimumHeight(0);
        del.setMinWidth(0);
        del.setMinimumWidth(0);
        del.setPadding(0,0,0,0);
        del.setBackgroundTintList(ColorStateList.valueOf(Util.RED));
        del.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),Util.round(Util.RED,12),null));
        final Row fr=r;
        del.setOnClickListener(v->{
            fr.root.setVisibility(View.GONE);
            rows.remove(fr);
            refreshTotal();
        });
        r.root.addView(r.name,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams f1=new LinearLayout.LayoutParams(Util.dp(this,62),Util.dp(this,46));
        f1.setMargins(Util.dp(this,4),0,0,0);
        r.root.addView(r.qty,f1);
        LinearLayout.LayoutParams f2=new LinearLayout.LayoutParams(Util.dp(this,58),Util.dp(this,46));
        f2.setMargins(Util.dp(this,4),0,0,0);
        r.root.addView(r.unit,f2);
        LinearLayout.LayoutParams f3=new LinearLayout.LayoutParams(Util.dp(this,70),Util.dp(this,46));
        f3.setMargins(Util.dp(this,4),0,0,0);
        r.root.addView(r.price,f3);
        LinearLayout.LayoutParams f4=new LinearLayout.LayoutParams(Util.dp(this,40),Util.dp(this,46));
        f4.setMargins(Util.dp(this,4),0,0,0);
        r.root.addView(del,f4);
        TextWatcher tw=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(Editable s){refreshTotal();}
        };
        r.qty.addTextChangedListener(tw);
        r.price.addTextChangedListener(tw);
        itemsBox.addView(r.root,Util.spaced(this));
        rows.add(r);
        r.name.setText(n==null?"":n);
        r.qty.setText(q<=0?"":Util.qty(q));
        r.unit.setText(u==null?"":u);
        r.price.setText(p<=0?"":Util.money(p));
    }

    private void refreshTotal(){
        double t=0;
        for(Row r:rows){
            t+=Util.number(r.qty.getText().toString())*Util.number(r.price.getText().toString());
        }
        totalField.setText(Util.money(t)+" ج.م");
    }

    private void startOcr(File img){
        int gs=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if(gs!=0){
            statusText.setText("⚠ التعرّف على النصوص يتطلب خدمة Google Play — أضف الأصناف يدويًا");
            addRow("",0,"",0);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("جاري تجهيز نموذج القراءة وتحليل الفاتورة... (أول مرة ينزّل النموذج ≈15 م.ب)");
        pool.execute(()->{
            final List<OcrParser.Parsed> items;
            final String store;
            final String raw;
            try{
                Bitmap b=Util.decodeScaledFile(img,1800);
                if(b==null)throw new Exception("تعذّرت قراءة الصورة");
                ensureModel();
                com.google.mlkit.vision.text.Text vt=runOcrWithRetries(b);
                items=OcrParser.parse(vt);
                store=OcrParser.guessStore(vt);
                raw=vt.getText();
            }catch(Exception e){
                final String msg=(e.getMessage()==null||e.getMessage().isEmpty())?"فشل التحليل — أضف الأصناف يدويًا":e.getMessage();
                runOnUiThread(()->onOcrDone(null,msg,null,null));
                return;
            }
            runOnUiThread(()->onOcrDone(items,null,store,raw));
        });
    }

    /** يضمن بدء تنزيل نموذج القراءة قبل التحليل (أول مرة فقط، ≈15 م.ب) */
    private void ensureModel() throws Exception{
        TextRecognizer probe=null;
        try{
            probe=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            com.google.android.gms.common.api.OptionalModuleApi api=(com.google.android.gms.common.api.OptionalModuleApi)probe;
            com.google.android.gms.common.moduleinstall.ModuleInstallClient client=com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(this);
            com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse avail=null;
            try{
                avail=client.areModulesAvailable(api).getResult(30, java.util.concurrent.TimeUnit.SECONDS);
            }catch(Exception e){}
            if(avail!=null&&avail.areModulesAvailable())return;
            com.google.android.gms.common.moduleinstall.ModuleInstallRequest req=com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder().addApi(api).build();
            client.installModules(req).getResult(30, java.util.concurrent.TimeUnit.SECONDS);
            // تم طلب التنزيل — runOcrWithRetries ستنتظر اكتماله
        }catch(Exception e){
            // إن لم تتوفر واجهة التثبيت الصريحة، سيُجرى التحليل مباشرة وقد يبدأ التنزيل تلقائيًا
        }finally{
            if(probe!=null){
                try{probe.close();}catch(Exception e){}
            }
        }
    }

    /** يحاول التحليل عدة مرات بانتظار اكتمال تنزيل النموذج */
    private com.google.mlkit.vision.text.Text runOcrWithRetries(Bitmap b) throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<12;attempt++){
            TextRecognizer rec=null;
            try{
                rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                InputImage in=InputImage.fromBitmap(b,0);
                return rec.process(in).getResult(25, java.util.concurrent.TimeUnit.SECONDS);
            }catch(Exception e){
                last=e;
            }finally{
                if(rec!=null){
                    try{rec.close();}catch(Exception e){}
                }
            }
            Thread.sleep(5000);
        }
        throw new Exception("لم يكتمل التحليل خلال الانتظار — تأكد من الاتصال بالإنترنت (أول مرة ينزّل النموذج) ثم أعد المحاولة");
    }

    private void onOcrDone(List<OcrParser.Parsed> items,String err,String store,String rawText){
        progressBar.setVisibility(View.GONE);
        if(err!=null){
            statusText.setText("⚠ "+err);
            new AlertDialog.Builder(this)
                .setTitle("لم يكتمل التحليل")
                .setMessage(err+"\n\n💡 أول مرة يحتاج إنترنت لتنزيل نموذج القراءة (≈15 م.ب) — بعد تنزيله يعمل بدون إنترنت.\n\nيمكنك إضافة الأصناف يدويًا الآن.")
                .setPositiveButton("حسنًا",null)
                .show();
            addRow("",0,"",0);
            return;
        }
        if(items==null||items.isEmpty()){
            if(rawText==null||rawText.trim().isEmpty()){
                statusText.setText("لم يتعرف على أي نص في الصورة");
                new AlertDialog.Builder(this)
                    .setTitle("لم يُقرأ أي نص من الصورة")
                    .setMessage("تأكد من:\n• الصورة واضحة وغير مغبشة (ثبّت الجوال)\n• الفاتورة تملأ أغلب الصورة (اقترب من الكاميرا)\n• الإضاءة جيدة والفاتورة مسطحة\n• الفاتورة مطبوعة وليست مكتوبة بخط اليد\n\nثم أعد المحاولة، أو أضف الأصناف يدويًا.")
                    .setPositiveButton("إضافة يدويًا",null)
                    .show();
            }else{
                statusText.setText("تم قراءة النص لكن لم يُفرز أصناف — انظر ما قرأه التطبيق");
                String show=rawText.trim();
                if(show.length()>600)show=show.substring(0,600)+"…";
                new AlertDialog.Builder(this)
                    .setTitle("النص الذي قرأه التطبيق")
                    .setMessage(show)
                    .setPositiveButton("إغلاق",null)
                    .show();
            }
            addRow("",0,"",0);
            return;
        }
        if(store!=null&&storeField.getText().toString().trim().isEmpty())storeField.setText(store);
        statusText.setText("✓ تم استخراج "+items.size()+" سطرًا — راجع وعدّل ثم احفظ");
        for(OcrParser.Parsed p:items)addRow(p.name,p.qty,p.unit,p.price);
        refreshTotal();
    }

    private void save(){
        if(dateIso==null||dateIso.length()<10){
            dateField.setError("أدخل التاريخ");
            return;
        }
        ArrayList<Db.Item> items=new ArrayList<>();
        for(Row r:rows){
            String n=r.name.getText().toString().trim();
            if(n.isEmpty())continue;
            double q=Util.number(r.qty.getText().toString());
            if(q<=0)q=1;
            String u=r.unit.getText().toString().trim();
            double p=Util.number(r.price.getText().toString());
            items.add(new Db.Item(n,q,u,p));
        }
        if(items.isEmpty()){
            new AlertDialog.Builder(this)
                .setTitle("لا توجد أصناف")
                .setMessage("اكتب اسم صنف واحد على الأقل ثم احفظ.")
                .setPositiveButton("حسنًا",null)
                .show();
            return;
        }
        double total=0;
        for(Db.Item it:items)total+=it.qty*it.price;
        String store=storeField.getText().toString().trim();
        long savedId;
        if(invoiceId>0){
            db.updateInvoice(invoiceId,dateIso,store,total,null,items);
            savedId=invoiceId;
        }else{
            savedId=db.insertInvoice(dateIso,store,total,"",items);
            invoiceId=savedId;
        }
        if(bmp!=null){
            File dir=getExternalFilesDir(null);
            if(dir!=null){
                File out=new File(dir,"inv_"+savedId+".jpg");
                try{
                    OutputStream os=new FileOutputStream(out);
                    if(bmp.compress(Bitmap.CompressFormat.JPEG,70,os))db.setImage(savedId,out.getAbsolutePath());
                    os.close();
                }catch(Exception e){}
            }
        }
        boolean more=(queue!=null&&queueIndex<queue.length);
        new AlertDialog.Builder(this)
            .setTitle("تم الحفظ ✓")
            .setMessage("حُفظت الفاتورة مع "+items.size()+" صنفًا على الجهاز.\nالإجمالي: "+Util.money(total)+" ج.م"+(more?("\n\nالمتبقي في القائمة: "+(queue.length-queueIndex)+" فواتير"):""))
            .setPositiveButton(more?("التالي ("+(queueIndex+1)+" من "+queue.length+")"):"العودة",(d,w)->{if(more)launchNext();else finish();})
            .setCancelable(false)
            .show();
    }

    private void launchNext(){
        Intent i=new Intent(this,ReviewActivity.class);
        i.putExtra("imagePath",queue[queueIndex]);
        i.putExtra("queue",queue);
        i.putExtra("queueIndex",queueIndex+1);
        startActivity(i);
        finish();
    }
}
