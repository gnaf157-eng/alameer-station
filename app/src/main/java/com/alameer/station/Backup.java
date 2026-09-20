package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * نسخ احتياطي واستعادة لقاعدة البيانات كاملة.
 * النسخة ملف SQLite واحد يمكن حفظه في درايف أو إرساله عبر واتساب.
 */
public final class Backup {
    public static final int REQUEST_RESTORE = 4201;
    private final Activity activity;

    public Backup(Activity activity){ this.activity = activity; }

    /** ينشئ نسخة ويفتح قائمة المشاركة لحفظها خارج الجهاز. */
    public void export(){
        try {
            File source = activity.getDatabasePath("alameer_station.db");
            if (!source.exists()) { toast("لا توجد بيانات لحفظها بعد."); return; }

            File dir = new File(activity.getCacheDir(), "exports");
            dir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
            File target = new File(dir, "alameer-backup-" + stamp + ".db");
            try(Db helper=new Db(activity)){snapshot(helper,target);}

            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", target);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية — محطة الأمير " + stamp);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "حفظ النسخة الاحتياطية"));
        } catch (Exception e) {
            toast("تعذر إنشاء النسخة الاحتياطية.");
        }
    }

    /** يفتح منتقي الملفات لاختيار نسخة سابقة. */
    public void pickForRestore(){
        new AlertDialog.Builder(activity)
            .setTitle("استعادة نسخة احتياطية")
            .setMessage("سيُستبدل محتوى التطبيق بالنسخة المختارة عند إعادة فتحه. ستُحفظ نسخة أمان من البيانات الحالية.\n\nاحفظ نسخة خارج الجهاز أيضًا.")
            .setPositiveButton("اختيار الملف", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                activity.startActivityForResult(Intent.createChooser(intent, "اختر ملف النسخة"), REQUEST_RESTORE);
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    /** يستعيد قاعدة البيانات من الملف المختار بعد التحقق من صلاحيته. */
    public void restoreFrom(Uri uri){
        File staging=new File(activity.getFilesDir(),"restore-staging.db");
        try{
            try(InputStream in=activity.getContentResolver().openInputStream(uri);
                OutputStream out=new FileOutputStream(staging)){
                if(in==null)throw new java.io.IOException("تعذر فتح الملف");
                byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            }
            if(!validDatabase(staging))throw new java.io.IOException("النسخة تالفة أو من إصدار أحدث");
            File pending=new File(activity.getFilesDir(),"restore-pending.db");
            java.nio.file.Files.move(staging.toPath(),pending.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            new AlertDialog.Builder(activity).setTitle("النسخة جاهزة للاستعادة")
                .setMessage("أغلق التطبيق الآن ثم افتحه. ستُستعاد النسخة قبل فتح أي سجل، مع الاحتفاظ بنسخة أمان.")
                .setCancelable(false).setPositiveButton("إغلاق",(d,w)->{activity.finishAffinity();System.exit(0);}).show();
        }catch(Exception e){staging.delete();toast("لم تتغير بياناتك. "+e.getMessage());}
    }

    /** A consistent SQLite snapshot, including committed WAL rows; no copying a live database file. */
    static void snapshot(Db helper,File target)throws Exception{
        android.database.sqlite.SQLiteDatabase source=helper.getWritableDatabase();
        if(target.exists()&&!target.delete())throw new java.io.IOException("تعذر إنشاء نسخة جديدة");
        android.database.sqlite.SQLiteDatabase dest=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(target,null);
        boolean success=false;
        source.beginTransaction();dest.beginTransaction();
        try{
            java.util.List<String> tables=new java.util.ArrayList<>();
            try(android.database.Cursor c=source.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata' ORDER BY name",null)){
                while(c.moveToNext()){dest.execSQL(c.getString(1));tables.add(c.getString(0));}
            }
            for(String table:tables)copyRows(source,dest,table);
            try(android.database.Cursor c=source.rawQuery("SELECT 1 FROM sqlite_master WHERE name='sqlite_sequence'",null)){
                if(c.moveToFirst()){dest.delete("sqlite_sequence",null,null);copyRows(source,dest,"sqlite_sequence");}
            }
            try(android.database.Cursor c=source.rawQuery("SELECT sql FROM sqlite_master WHERE type IN ('index','trigger','view') AND sql IS NOT NULL",null)){
                while(c.moveToNext())dest.execSQL(c.getString(0));
            }
            dest.setVersion(source.getVersion());
            dest.setTransactionSuccessful();source.setTransactionSuccessful();success=true;
        }finally{
            try{dest.endTransaction();}finally{dest.close();source.endTransaction();if(!success)target.delete();}
        }
        if(!validDatabase(target))throw new java.io.IOException("فشل التحقق من النسخة الاحتياطية");
    }
    private static void copyRows(android.database.sqlite.SQLiteDatabase source,android.database.sqlite.SQLiteDatabase dest,String table){
        String quoted="\""+table.replace("\"","\"\"")+"\"";
        try(android.database.Cursor c=source.rawQuery("SELECT * FROM "+quoted,null)){
            while(c.moveToNext()){
                android.content.ContentValues row=new android.content.ContentValues();
                for(int i=0;i<c.getColumnCount();i++){
                    String key=c.getColumnName(i);
                    switch(c.getType(i)){
                        case android.database.Cursor.FIELD_TYPE_NULL:row.putNull(key);break;
                        case android.database.Cursor.FIELD_TYPE_INTEGER:row.put(key,c.getLong(i));break;
                        case android.database.Cursor.FIELD_TYPE_FLOAT:row.put(key,c.getDouble(i));break;
                        case android.database.Cursor.FIELD_TYPE_BLOB:row.put(key,c.getBlob(i));break;
                        default:row.put(key,c.getString(i));
                    }
                }
                dest.insertOrThrow(quoted,null,row);
            }
        }
    }
    static boolean validDatabase(File file){
        try(android.database.sqlite.SQLiteDatabase probe=android.database.sqlite.SQLiteDatabase.openDatabase(file.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
            if(probe.getVersion()<1||probe.getVersion()>20)return false;
            try(android.database.Cursor c=probe.rawQuery("PRAGMA integrity_check",null)){
                if(!c.moveToFirst()||!"ok".equals(c.getString(0)))return false;
            }
            try(android.database.Cursor c=probe.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('workers','pumps','shifts','readings','movements')",null)){
                return c.moveToFirst()&&c.getInt(0)==5;
            }
        }catch(Exception e){return false;}
    }

    private void toast(String message){ Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
}

