package com.alameer.invoicecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends Activity {
    Db db;
    LinearLayout listBox;
    TextView countText;
    File captureFile;
    static final int REQ_CAMERA=11,REQ_GALLERY=12,REQ_MULTI=13,REQ_BACKUP=14;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Util.installCrashReporter(this);
        db=new Db(this);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.addView(Util.brandBar(this,"سجل الفواتير","عداد أصناف البقالة من صور الفواتير الورقية — v2.0",null));
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Util.dp(this,14),Util.dp(this,8),Util.dp(this,14),Util.dp(this,16));
        LinearLayout hero=Util.panel(this,Util.NAVY);
        hero.addView(Util.text(this,"صوّر فاتورة الشراء الورقية",19,Color.WHITE,true));
        hero.addView(Util.text(this,"1) التقط صورة   2) راجع الأصناف   3) احفظ\nثم افتح التقرير لمعرفة كم اشتريت من كل صنف خلال أي فترة",13,0xffc9ccce,false));
        content.addView(hero,Util.spaced(this));
        Button cam=Util.goldButton(this,"📷  التقاط صورة فاتورة");
        cam.setOnClickListener(v->openCamera());
        content.addView(cam,Util.spaced(this));
        Button gal=Util.button(this,"🖼  فاتورة من الألبوم");
        gal.setOnClickListener(v->openGallery());
        content.addView(gal,Util.spaced(this));
        Button multi=Util.button(this,"📥  استيراد عدة فواتير دفعة واحدة");
        multi.setOnClickListener(v->openMulti());
        content.addView(multi,Util.spaced(this));
        Button rep=Util.greenButton(this,"📊  كم اشتريت من كل صنف؟");
        rep.setOnClickListener(v->startActivity(new Intent(this,ReportActivity.class)));
        content.addView(rep,Util.spaced(this));
        Button backup=Util.button(this,"💾  نسخة احتياطية (تصدير / استيراد)");
        backup.setOnClickListener(v->backupDialog());
        content.addView(backup,Util.spaced(this));
        content.addView(Util.heading(this,"الفواتير المحفوظة"),Util.spaced(this));
        listBox=new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);
        countText=Util.text(this,"",13,Util.MUTED,false);
        countText.setPadding(0,Util.dp(this,10),0,0);
        content.addView(countText);
        scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    @Override protected void onResume(){
        super.onResume();
        loadInvoices();
    }

    private void openCamera(){
        File dir=getExternalFilesDir(null);
        if(dir==null){Toast.makeText(this,"مساحة التخزين غير متاحة",Toast.LENGTH_SHORT).show();return;}
        captureFile=new File(dir,"capture.jpg");
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,AppFileProvider.uriFor(captureFile));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            startActivityForResult(i,REQ_CAMERA);
        }catch(Exception e){
            Toast.makeText(this,"لا توجد كاميرا متاحة",Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery(){
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        try{
            startActivityForResult(Intent.createChooser(i,"اختر صورة الفاتورة"),REQ_GALLERY);
        }catch(Exception e){
            Toast.makeText(this,"لا توجد مكتبة صور",Toast.LENGTH_SHORT).show();
        }
    }

    private void openMulti(){
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{
            startActivityForResult(Intent.createChooser(i,"اختر صور الفواتير (يمكن اختيار أكثر من صورة)"),REQ_MULTI);
        }catch(Exception e){
            Toast.makeText(this,"لا توجد مكتبة صور",Toast.LENGTH_SHORT).show();
        }
    }

    private void backupDialog(){
        String[] opts={"تصدير نسخة احتياطية (ملف)","استيراد نسخة احتياطية"};
        new AlertDialog.Builder(this)
            .setTitle("النسخ الاحتياطي")
            .setItems(opts,(d,w)->{
                if(w==0)exportBackup();
                else importBackup();
            })
            .setNegativeButton("إلغاء",null)
            .show();
    }

    private void exportBackup(){
        File dir=getExternalFilesDir(null);
        if(dir==null){Toast.makeText(this,"لا يوجد تخزين",Toast.LENGTH_SHORT).show();return;}
        File out=new File(dir,"backup_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm",Locale.US).format(new Date())+".json");
        try{
            StringBuilder sb=new StringBuilder();
            sb.append("{\"app\":\"invoice-counter\",\"version\":1,\"invoices\":[");
            boolean first=true;
            try(Cursor c=db.invoices()){
                while(c.moveToNext()){
                    if(!first)sb.append(",");
                    first=false;
                    sb.append("{\"date\":\"").append(json(c.getString(1))).append('"');
                    sb.append(",\"store\":\"").append(json(c.getString(2))).append('"');
                    sb.append(",\"total\":").append(c.getDouble(3));
                    sb.append(",\"items\":[");
                    boolean f2=true;
                    for(Db.Item it:db.itemsOf(c.getLong(0))){
                        if(!f2)sb.append(",");
                        f2=false;
                        sb.append("{\"name\":\"").append(json(it.name)).append('"');
                        sb.append(",\"qty\":").append(it.qty);
                        sb.append(",\"unit\":\"").append(json(it.unit)).append('"');
                        sb.append(",\"price\":").append(it.price).append("}");
                    }
                    sb.append("]}");
                }
            }
            sb.append("]}");
            Writer w=new OutputStreamWriter(new FileOutputStream(out),StandardCharsets.UTF_8);
            w.write(sb.toString());
            w.close();
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_STREAM,AppFileProvider.uriFor(out));
            send.putExtra(Intent.EXTRA_SUBJECT,"نسخة احتياطية من سجل الفواتير");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,"إرسال النسخة الاحتياطية (احفظها في مكان آمن)"));
            Toast.makeText(this,"اختر أين تريد إرسال الملف",Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,"فشل التصدير: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private void importBackup(){
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try{
            startActivityForResult(Intent.createChooser(i,"اختر ملف النسخة الاحتياطية"),REQ_BACKUP);
        }catch(Exception e){
            Toast.makeText(this,"تعذر فتح الملف",Toast.LENGTH_SHORT).show();
        }
    }

    private static String json(String s){
        if(s==null)return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
    }

    private byte[] readAll(InputStream in) throws Exception{
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        int r;
        while((r=in.read(buf))>0)bos.write(buf,0,r);
        return bos.toByteArray();
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==REQ_CAMERA&&res==RESULT_OK){
            if(captureFile!=null&&captureFile.isFile()&&captureFile.length()>0)openReview(captureFile.getAbsolutePath());
            else Toast.makeText(this,"لم تُلتقط صورة",Toast.LENGTH_SHORT).show();
        }else if(req==REQ_GALLERY&&res==RESULT_OK&&data!=null&&data.getData()!=null){
            try{
                Bitmap bmp=Util.decodeScaledUri(getContentResolver(),data.getData(),1800);
                if(bmp==null){Toast.makeText(this,"تعذّرت قراءة الصورة",Toast.LENGTH_SHORT).show();return;}
                saveAndOpen(bmp);
            }catch(Exception e){
                Toast.makeText(this,"تعذّرت قراءة الصورة",Toast.LENGTH_SHORT).show();
            }
        }else if(req==REQ_MULTI&&res==RESULT_OK&&data!=null){
            List<Uri> uris=new ArrayList<>();
            ClipData cd=data.getClipData();
            if(cd!=null){
                for(int i=0;i<cd.getItemCount();i++)uris.add(cd.getItemAt(i).getUri());
            }else if(data.getData()!=null){
                uris.add(data.getData());
            }
            ArrayList<String> paths=new ArrayList<>();
            for(Uri u:uris){
                try{
                    Bitmap bmp=Util.decodeScaledUri(getContentResolver(),u,1800);
                    if(bmp==null)continue;
                    File dir=getExternalFilesDir(null);
                    File out=new File(dir,"batch_"+System.currentTimeMillis()+"_"+paths.size()+".jpg");
                    OutputStream os=new FileOutputStream(out);
                    bmp.compress(Bitmap.CompressFormat.JPEG,85,os);
                    os.close();
                    paths.add(out.getAbsolutePath());
                }catch(Exception e){}
            }
            if(paths.isEmpty()){
                Toast.makeText(this,"تعذّرت قراءة الصور",Toast.LENGTH_SHORT).show();
                return;
            }
            if(paths.size()==1){
                openReview(paths.get(0));
            }else{
                Intent i=new Intent(this,ReviewActivity.class);
                i.putExtra("imagePath",paths.get(0));
                i.putExtra("queue",paths.toArray(new String[0]));
                i.putExtra("queueIndex",1);
                startActivity(i);
            }
        }else if(req==REQ_BACKUP&&res==RESULT_OK&&data!=null&&data.getData()!=null){
            try{
                InputStream in=getContentResolver().openInputStream(data.getData());
                String s=new String(readAll(in),"UTF-8");
                in.close();
                org.json.JSONObject root=new org.json.JSONObject(s);
                org.json.JSONArray invs=root.getJSONArray("invoices");
                new AlertDialog.Builder(this)
                    .setTitle("استيراد النسخة")
                    .setMessage("سيتم استبدال البيانات الحالية بـ "+invs.length()+" فاتورة من النسخة الاحتياطية.\n\nهل تريد المتابعة؟")
                    .setPositiveButton("استبدال",(d,w)->{
                        try{
                            db.wipeAll();
                            int count=0;
                            for(int k=0;k<invs.length();k++){
                                org.json.JSONObject o=invs.getJSONObject(k);
                                String date=o.getString("date");
                                String store=o.optString("store","");
                                double total=o.optDouble("total",0);
                                ArrayList<Db.Item> items=new ArrayList<>();
                                org.json.JSONArray its=o.optJSONArray("items");
                                if(its!=null){
                                    for(int j=0;j<its.length();j++){
                                        org.json.JSONObject io=its.getJSONObject(j);
                                        items.add(new Db.Item(io.optString("name",""),io.optDouble("qty",0),io.optString("unit",""),io.optDouble("price",0)));
                                    }
                                }
                                if(!items.isEmpty()||total>0)db.insertInvoice(date,store,total,"",items);
                                count++;
                            }
                            loadInvoices();
                            Toast.makeText(this,"تم استيراد "+count+" فاتورة ✓",Toast.LENGTH_LONG).show();
                        }catch(Exception e){
                            Toast.makeText(this,"ملف غير صالح: "+e.getMessage(),Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("إلغاء",null)
                    .show();
            }catch(Exception e){
                Toast.makeText(this,"تعذر قراءة الملف: "+e.getMessage(),Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveAndOpen(Bitmap bmp){
        try{
            File dir=getExternalFilesDir(null);
            File out=new File(dir,"picked_"+System.currentTimeMillis()+".jpg");
            OutputStream os=new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.JPEG,85,os);
            os.close();
            openReview(out.getAbsolutePath());
        }catch(Exception e){
            Toast.makeText(this,"تعذّر حفظ الصورة",Toast.LENGTH_SHORT).show();
        }
    }

    private void openReview(String path){
        Intent i=new Intent(this,ReviewActivity.class);
        i.putExtra("imagePath",path);
        startActivity(i);
    }

    private void loadInvoices(){
        listBox.removeAllViews();
        int n=0;
        try(Cursor c=db.invoices()){
            while(c.moveToNext()){
                n++;
                final long id=c.getLong(0);
                String date=Util.fmtDate(c.getString(1));
                String store=c.getString(2);
                double total=c.getDouble(3);
                int items=c.getInt(4);
                LinearLayout row=Util.panel(this,Color.WHITE);
                row.setOrientation(LinearLayout.VERTICAL);
                row.addView(Util.text(this,date+(store.isEmpty()?"":"  —  "+store),15,Util.NAVY,true));
                row.addView(Util.text(this,items+" صنف   •   "+Util.money(total)+" ج.م   •   اضغط للتعديل",13,Util.MUTED,false));
                row.setOnClickListener(v->{
                    Intent i=new Intent(this,ReviewActivity.class);
                    i.putExtra("invoiceId",id);
                    startActivity(i);
                });
                row.setOnLongClickListener(v->{
                    new AlertDialog.Builder(HomeActivity.this)
                        .setTitle("حذف الفاتورة")
                        .setMessage("حذف فاتورة "+date+(store.isEmpty()?"":" — "+store)+" نهائيًا؟")
                        .setPositiveButton("حذف",(d,w)->{
                            String[] m=db.metaOf(id);
                            if(m!=null&&m[3]!=null&&!m[3].isEmpty()){
                                File f=new File(m[3]);
                                if(f.isFile())f.delete();
                            }
                            db.deleteInvoice(id);
                            loadInvoices();
                            Toast.makeText(HomeActivity.this,"تم الحذف",Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("إلغاء",null)
                        .show();
                    return true;
                });
                listBox.addView(row,Util.spaced(this));
            }
        }
        catch(Exception e){
            countText.setText("خطأ في قراءة الفواتير: "+e.getMessage());
            return;
        }
        if(n==0){
            TextView empty=Util.text(this,"لا فواتير محفوظة بعد — صوّر أول فاتورة بالأعلى",14,Util.MUTED,false);
            empty.setPadding(0,Util.dp(this,8),0,Util.dp(this,8));
            listBox.addView(empty);
        }
        String all="";
        try(Cursor sc=db.rangeSummary("","")){
            if(sc.moveToFirst()&&sc.getInt(0)>0)all="   •   إجمالي المشتريات: "+Util.money(sc.getDouble(2))+" ج.م";
        }catch(Exception e){}
        countText.setText(n+" فاتورة محفوظة على هذا الجهاز"+all+"   (يعمل بدون إنترنت)");
    }
}
