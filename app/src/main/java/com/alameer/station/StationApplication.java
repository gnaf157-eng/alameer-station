package com.alameer.station.shifts;

import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Restores only at process startup, before any screen or database helper opens. */
public class StationApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        File pending=new File(getFilesDir(),"restore-pending.db");
        if(!pending.exists())return;
        File database=getDatabasePath("alameer_station.db");
        try{
            if(!Backup.validDatabase(pending))throw new IllegalStateException("النسخة غير صالحة لهذا الإصدار");
            if(database.exists()){
                try(SQLiteDatabase old=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                    try(Cursor c=old.rawQuery("PRAGMA wal_checkpoint(FULL)",null)){
                        if(c.moveToFirst()&&c.getInt(0)!=0)throw new IllegalStateException("قاعدة البيانات مشغولة");
                    }
                }
                File safety=new File(getFilesDir(),"backup-before-restore-"+System.currentTimeMillis()+".db");
                Files.copy(database.toPath(),safety.toPath());
                if(!Backup.validDatabase(safety))throw new IllegalStateException("تعذر التحقق من نسخة الأمان");
            }
            // All handles are closed; do not carry the old WAL across database replacement.
            for(String suffix:new String[]{"-wal","-shm","-journal"}){
                File side=new File(database.getPath()+suffix);
                if(side.exists()&&!side.delete())throw new IllegalStateException("تعذر إغلاق سجل قاعدة البيانات");
            }
            database.getParentFile().mkdirs();
            Files.move(pending.toPath(),database.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            Toast.makeText(this,"استُعيدت البيانات مع الاحتفاظ بنسخة الأمان",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            // Do not keep retrying a replacement silently on every launch.
            pending.renameTo(new File(getFilesDir(),"restore-rejected-"+System.currentTimeMillis()+".db"));
            Toast.makeText(this,"لم تُستعد النسخة: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
}
