package com.alameer.station.shifts;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AppUpdater {
    private final Activity activity;
    private static final java.util.concurrent.atomic.AtomicBoolean downloading=new java.util.concurrent.atomic.AtomicBoolean(false);
    public AppUpdater(Activity a){activity=a;}
    private void ui(Runnable action){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())action.run();});}
    private void message(String text){ui(()->new AlertDialog.Builder(activity).setTitle("تحديث التطبيق").setMessage(text).setPositiveButton("حسنًا",null).show());}
    public void check(boolean requested){
        String address=BuildConfig.UPDATE_MANIFEST_URL;
        if(address==null||address.trim().isEmpty()){if(requested)message("عنوان التحديث غير مضبوط في هذه النسخة.");return;}
        if(requested)Toast.makeText(activity,"جاري فحص التحديث...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                JSONObject manifest=new JSONObject(readUrl(address));
                if(manifest.getLong("versionCode")>BuildConfig.VERSION_CODE)ui(()->new AlertDialog.Builder(activity)
                    .setTitle("تحديث متوفر — "+manifest.optString("versionName"))
                    .setMessage(manifest.optString("notes","تحسينات وإصلاحات"))
                    .setPositiveButton("تنزيل وتثبيت",(d,w)->download(manifest))
                    .setNegativeButton("لاحقًا",null).show());
                else if(requested)ui(()->Toast.makeText(activity,"لديك أحدث نسخة",Toast.LENGTH_LONG).show());
            }catch(Exception e){if(requested)message("تعذر فحص التحديث. تحقق من الإنترنت ثم حاول مجددًا.");}
        }).start();
    }
    private String readUrl(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setUseCaches(false);
        c.setRequestProperty("Accept","application/json");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Invalid manifest");out.write(b,0,n);}
            return out.toString(StandardCharsets.UTF_8.name());
        }finally{c.disconnect();}
    }
    private void download(JSONObject manifest){
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("السماح بتثبيت التحديث")
                .setMessage("اسمح لهذا التطبيق بتثبيت التحديثات، ثم ارجع واضغط فحص التحديث.")
                .setPositiveButton("فتح الإعدادات",(d,w)->activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()))))
                .setNegativeButton("إلغاء",null).show();return;
        }
        Uri address=Uri.parse(manifest.optString("apkUrl",""));
        if(!"https".equalsIgnoreCase(address.getScheme())||address.getHost()==null){message("رابط التحديث غير صالح.");return;}
        // Use Drive's download endpoint, not its viewer or HTML confirmation page.
        if("drive.google.com".equalsIgnoreCase(address.getHost())){
            String id=address.getQueryParameter("id");
            if(id!=null)address=Uri.parse("https://drive.usercontent.google.com/download").buildUpon()
                .appendQueryParameter("id",id).appendQueryParameter("export","download").appendQueryParameter("confirm","t").build();
        }
        if(!downloading.compareAndSet(false,true)){message("يوجد تحديث قيد التنزيل. انتظر اكتماله.");return;}
        DownloadManager manager=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        final long[] downloadId={-1};
        final Context app=activity.getApplicationContext();
        BroadcastReceiver receiver=new BroadcastReceiver(){
            public void onReceive(Context context,Intent intent){
                if(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-2)!=downloadId[0])return;
                try{app.unregisterReceiver(this);}catch(IllegalArgumentException ignored){}
                new Thread(()->{
                    try{
                        try(Cursor c=manager.query(new DownloadManager.Query().setFilterById(downloadId[0]))){
                            if(c==null||!c.moveToFirst()||c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))!=DownloadManager.STATUS_SUCCESSFUL)
                                throw new IOException("لم يكتمل تنزيل التحديث. أعد المحاولة عند استقرار الإنترنت.");
                        }
                        Uri downloaded=manager.getUriForDownloadedFile(downloadId[0]);
                        if(downloaded==null)throw new IOException("تعذر الوصول إلى ملف التحديث.");
                        File dir=new File(activity.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("لا توجد مساحة لحفظ التحديث.");
                        File target=new File(dir,"station-update-"+manifest.getLong("versionCode")+".apk");
                        target.delete();
                        try{
                            try(InputStream in=app.getContentResolver().openInputStream(downloaded);OutputStream out=new FileOutputStream(target)){
                                if(in==null)throw new IOException("تعذر قراءة التنزيل.");
                                byte[] bytes=new byte[32768];int n;long total=0;
                                while((n=in.read(bytes))!=-1){total+=n;if(total>100L*1024*1024)throw new IOException("حجم ملف التحديث غير متوقع.");out.write(bytes,0,n);}
                            }
                            UpdateFileCheck.verify(target,manifest.optLong("sizeBytes",0),manifest.optString("sha256",""));
                            verifyPackage(target,manifest.getLong("versionCode"));
                            ui(()->install(target));
                        }catch(Exception e){target.delete();throw e;}
                    }catch(Exception e){message(e.getMessage()==null?"تعذر تجهيز التحديث. أعد المحاولة.":e.getMessage());}
                    finally{manager.remove(downloadId[0]);downloading.set(false);}
                }).start();
            }
        };
        try{
            IntentFilter filter=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if(Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else app.registerReceiver(receiver,filter);
            DownloadManager.Request request=new DownloadManager.Request(address);
            request.setTitle("تحديث محطة الأمير");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,"station-"+System.currentTimeMillis()+".apk");
            downloadId[0]=manager.enqueue(request);
            Toast.makeText(activity,"جاري التنزيل. سيُفحص الملف قبل التثبيت.",Toast.LENGTH_LONG).show();
        }catch(Exception e){try{app.unregisterReceiver(receiver);}catch(Exception ignored){}downloading.set(false);message("تعذر بدء التنزيل. تحقق من المساحة والإنترنت.");}
    }
    @SuppressWarnings("deprecation")
    private void verifyPackage(File file,long expected)throws Exception{
        PackageManager pm=activity.getPackageManager();
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if(candidate==null)throw new IOException("الملف المنزّل ليس حزمة أندرويد قابلة للقراءة. لم يبدأ التثبيت.");
        if(!activity.getPackageName().equals(candidate.packageName))throw new IOException("ملف التحديث لا يخص محطة الأمير.");
        if(candidate.versionCode!=expected||candidate.versionCode<=BuildConfig.VERSION_CODE)throw new IOException("نسخة الملف لا تطابق التحديث المطلوب. أعد المحاولة.");
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
        if(candidate.signatures==null||installed.signatures==null||
            !new HashSet<Signature>(Arrays.asList(candidate.signatures)).equals(new HashSet<Signature>(Arrays.asList(installed.signatures))))
            throw new IOException("توقيع التحديث مختلف عن النسخة المثبتة. لا تحذف التطبيق؛ أرسل صورة هذه الرسالة لمعالجة توافق النسخ.");
    }
    private void install(File file){
        try{
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.setClipData(ClipData.newRawUri("update",uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        }catch(Exception e){message("تعذر فتح مثبّت أندرويد. تحقق من السماح بتثبيت التطبيقات لهذا البرنامج.");}
    }
}
