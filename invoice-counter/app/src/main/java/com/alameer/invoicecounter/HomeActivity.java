package com.alameer.invoicecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class HomeActivity extends Activity {
    Db db;
    LinearLayout listBox;
    TextView countText;
    File captureFile;
    static final int REQ_CAMERA=11,REQ_GALLERY=12;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Util.installCrashReporter(this);
        db=new Db(this);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);
        shell.addView(Util.brandBar(this,"سجل الفواتير","عداد أصناف البقالة من صور الفواتير الورقية",null));
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
        Button rep=Util.greenButton(this,"📊  كم اشتريت من كل صنف؟");
        rep.setOnClickListener(v->startActivity(new Intent(this,ReportActivity.class)));
        content.addView(rep,Util.spaced(this));
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

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==REQ_CAMERA&&res==RESULT_OK){
            if(captureFile!=null&&captureFile.isFile()&&captureFile.length()>0)openReview(captureFile.getAbsolutePath());
            else Toast.makeText(this,"لم تُلتقط صورة",Toast.LENGTH_SHORT).show();
        }else if(req==REQ_GALLERY&&res==RESULT_OK&&data!=null&&data.getData()!=null){
            try{
                Bitmap bmp=Util.decodeScaledUri(getContentResolver(),data.getData(),1800);
                if(bmp==null){Toast.makeText(this,"تعذّرت قراءة الصورة",Toast.LENGTH_SHORT).show();return;}
                File dir=getExternalFilesDir(null);
                File out=new File(dir,"picked_"+System.currentTimeMillis()+".jpg");
                OutputStream os=new FileOutputStream(out);
                bmp.compress(Bitmap.CompressFormat.JPEG,85,os);
                os.close();
                openReview(out.getAbsolutePath());
            }catch(Exception e){
                Toast.makeText(this,"تعذّرت قراءة الصورة",Toast.LENGTH_SHORT).show();
            }
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
        countText.setText(n+" فاتورة محفوظة على هذا الجهاز (يعمل بدون إنترنت)");
    }
}
