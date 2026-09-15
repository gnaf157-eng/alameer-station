package com.alameer.station.shifts;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME = "alameer_station.db";
    private static final int DB_VERSION = 10;
    public Db(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE workers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,pin_hash TEXT NOT NULL UNIQUE,role TEXT NOT NULL,shift_kind TEXT NOT NULL DEFAULT 'DAY',active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE pumps(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,fuel TEXT NOT NULL,price REAL NOT NULL DEFAULT 0,last_reading REAL NOT NULL DEFAULT 0,worker_id INTEGER,active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE shifts(id INTEGER PRIMARY KEY AUTOINCREMENT,worker_id INTEGER NOT NULL,opened_at TEXT NOT NULL,shift_date TEXT NOT NULL DEFAULT '',historical INTEGER NOT NULL DEFAULT 0,closed_at TEXT,status TEXT NOT NULL DEFAULT 'OPEN',sales REAL NOT NULL DEFAULT 0,collections REAL NOT NULL DEFAULT 0,cash_delivered REAL NOT NULL DEFAULT 0,debts REAL NOT NULL DEFAULT 0,expenses REAL NOT NULL DEFAULT 0,balance REAL NOT NULL DEFAULT 0,difference_reason TEXT DEFAULT '',manager_note TEXT DEFAULT '',sync_state TEXT NOT NULL DEFAULT 'LOCAL',revision INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id INTEGER NOT NULL,pump_id INTEGER NOT NULL,previous REAL NOT NULL,current REAL,price REAL NOT NULL,sales REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id INTEGER NOT NULL,type TEXT NOT NULL,name TEXT NOT NULL,amount REAL NOT NULL,created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE remembered_names(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL,name TEXT NOT NULL,UNIQUE(type,name))");
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL(CASHBOXES_SQL);
        db.execSQL(CASHBOX_ENTRIES_SQL);
        db.execSQL(MATERIAL_ENTRIES_SQL);
        db.execSQL(EXPENSE_ENTRIES_SQL);
        db.execSQL(DEBTORS_SQL);
        db.execSQL(DEBT_ENTRIES_SQL);
        db.execSQL("CREATE TABLE audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id INTEGER,worker_id INTEGER,action TEXT NOT NULL,details TEXT,created_at TEXT NOT NULL)");
        seed(db);
    }

    private void seed(SQLiteDatabase db) {
        seedWorker(db,"المدير","0000","ADMIN","DAY");
        seedWorker(db,"عامل الديزل","1111","WORKER","DAY");
        seedWorker(db,"عامل البترول","2222","WORKER","DAY");
        seedWorker(db,"عامل الغاز","3333","WORKER","DAY");
        seedWorker(db,"عامل الليل","4444","WORKER","NIGHT");
        for (int i=1;i<=4;i++) db.execSQL("INSERT INTO pumps(name,fuel,worker_id) VALUES('ديزل "+i+"','ديزل',2)");
        for (int i=1;i<=3;i++) db.execSQL("INSERT INTO pumps(name,fuel,worker_id) VALUES('بترول "+i+"','بترول',3)");
        db.execSQL("INSERT INTO pumps(name,fuel,worker_id) VALUES('غاز 1','غاز',4)");
    }

    private void seedWorker(SQLiteDatabase db,String name,String pin,String role,String kind){
        db.execSQL("INSERT INTO workers(name,pin_hash,role,shift_kind) VALUES(?,?,?,?)",new Object[]{name,hash(pin),role,kind});
    }

    public static String hash(String pin){ return Calc.hash(pin); }

    static final String CASHBOXES_SQL="CREATE TABLE IF NOT EXISTS cashboxes(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,opening REAL NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL DEFAULT '')";
    static final String CASHBOX_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS cashbox_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,box_id INTEGER NOT NULL,direction TEXT NOT NULL,amount REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0)";
    static final String MATERIAL_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS material_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,material TEXT NOT NULL,direction TEXT NOT NULL,litres REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL)";
    static final String DEBTORS_SQL="CREATE TABLE IF NOT EXISTS debtors(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,phone TEXT NOT NULL DEFAULT '',opening REAL NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL DEFAULT '')";
    static final String DEBT_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS debt_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,debtor_id INTEGER NOT NULL,direction TEXT NOT NULL,amount REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0)";
    static final String EXPENSE_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS expense_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,category TEXT NOT NULL,amount REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0,box_id INTEGER NOT NULL DEFAULT 0)";
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<10){
            try{db.execSQL(EXPENSE_ENTRIES_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<9){
            try{db.execSQL("ALTER TABLE cashbox_entries ADD COLUMN source_shift INTEGER NOT NULL DEFAULT 0");}catch(Exception ignored){}
            try{db.execSQL("ALTER TABLE debt_entries ADD COLUMN source_shift INTEGER NOT NULL DEFAULT 0");}catch(Exception ignored){}
            try{db.execSQL(MATERIAL_ENTRIES_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<8){
            try{db.execSQL(DEBTORS_SQL);db.execSQL(DEBT_ENTRIES_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<7){
            try{db.execSQL(MATERIAL_ENTRIES_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<6){
            try{db.execSQL(CASHBOXES_SQL);db.execSQL(CASHBOX_ENTRIES_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<5){
            db.execSQL("ALTER TABLE shifts ADD COLUMN shift_date TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE shifts ADD COLUMN historical INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE shifts SET shift_date=substr(opened_at,1,10)");
        }
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE shifts ADD COLUMN manager_note TEXT DEFAULT ''"); } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)"); } catch (Exception ignored) {}
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE workers ADD COLUMN pin_hash TEXT");
                try (Cursor c = db.rawQuery("SELECT id,pin FROM workers", null)) {
                    while (c.moveToNext())
                        db.execSQL("UPDATE workers SET pin_hash=? WHERE id=?", new Object[]{hash(c.getString(1)), c.getLong(0)});
                }
            } catch (Exception ignored) {}
        }
    }

    /** حساب المشغّل الوحيد في وضع الجهاز المستقل (بلا دخول). */
    public int soloWorkerId(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM workers WHERE role='WORKER' AND active=1 ORDER BY id LIMIT 1",null)){
            if(c.moveToFirst())return c.getInt(0);
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM workers ORDER BY id LIMIT 1",null)){
            return c.moveToFirst()?c.getInt(0):1;
        }
    }
    public String workerName(int id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM workers WHERE id=?",new String[]{String.valueOf(id)})){
            return c.moveToFirst()?c.getString(0):"المشغّل";
        }
    }
    /** تغيير اسم المشغّل وحده، دون المساس بالرمز أو نوع الوردية. */
    public void renameWorker(int id,String name){
        if(name==null||name.trim().isEmpty())return;
        ContentValues v=new ContentValues();v.put("name",name.trim());
        getWritableDatabase().update("workers",v,"id=?",new String[]{String.valueOf(id)});
    }
    /** كل الطرمبات النشطة تُسند للمشغّل الوحيد، فيرى الجميع في ورديته. */
    public long openSoloShift(int workerId){
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT id FROM shifts WHERE worker_id=? AND status IN ('OPEN','RETURNED') ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(workerId)})){
            if(c.moveToFirst())return c.getLong(0);
        }
        ContentValues s=new ContentValues();s.put("worker_id",workerId);s.put("opened_at",Util.now());s.put("shift_date",ShiftDates.today());
        long shiftId=db.insertOrThrow("shifts",null,s);
        try(Cursor c=db.rawQuery("SELECT id,last_reading,price FROM pumps WHERE active=1 ORDER BY id",null)){
            while(c.moveToNext()){
                ContentValues r=new ContentValues();
                r.put("shift_id",shiftId);r.put("pump_id",c.getLong(0));
                r.put("previous",c.getDouble(1));r.put("price",c.getDouble(2));
                db.insertOrThrow("readings",null,r);
            }
        }
        audit(db,shiftId,workerId,"OPEN_SHIFT","فتح وردية على الجهاز");
        return shiftId;
    }
    /** يسحب أسعار الطرمبات المحدّثة إلى وردية مفتوحة ويعيد حساب مبيعاتها. */
    public void refreshShiftPrices(long shiftId){
        if(isHistorical(shiftId))return;
        SQLiteDatabase db=getWritableDatabase();
        db.execSQL("UPDATE readings SET price=(SELECT p.price FROM pumps p WHERE p.id=readings.pump_id) WHERE shift_id=?",new Object[]{shiftId});
        db.execSQL("UPDATE readings SET sales=(current-previous)*price WHERE shift_id=? AND current IS NOT NULL",new Object[]{shiftId});
    }

    /**
     * ينقل عدّاد الطرمبة المضبوط من الإعدادات إلى الوردية المفتوحة.
     * لا نلمس طرمبة أدخل لها العامل قراءة حالية، حتى لا تضيع مبيعاته.
     */
    public void refreshShiftPrevious(long shiftId){
        if(isHistorical(shiftId))return;
        getWritableDatabase().execSQL(
            "UPDATE readings SET previous=(SELECT p.last_reading FROM pumps p WHERE p.id=readings.pump_id) "+
            "WHERE shift_id=? AND current IS NULL",new Object[]{shiftId});
    }

    /** يزامن الوردية المفتوحة مع أي تغيير في الإعدادات: طرمبات جديدة، أسعار، وعدّادات. */
    public void syncShiftWithSettings(long shiftId){
        if(isHistorical(shiftId))return;
        syncShiftPumps(shiftId);
        refreshShiftPrevious(shiftId);
        refreshShiftPrices(shiftId);
    }
    /** يضمّ أي طرمبة نشطة أُضيفت بعد فتح الوردية. */
    public int syncShiftPumps(long shiftId){
        if(isHistorical(shiftId))return 0;
        SQLiteDatabase db=getWritableDatabase();int added=0;
        try(Cursor c=db.rawQuery("SELECT id,last_reading,price FROM pumps WHERE active=1 AND id NOT IN (SELECT pump_id FROM readings WHERE shift_id=?) ORDER BY id",new String[]{String.valueOf(shiftId)})){
            while(c.moveToNext()){
                ContentValues r=new ContentValues();
                r.put("shift_id",shiftId);r.put("pump_id",c.getLong(0));
                r.put("previous",c.getDouble(1));r.put("price",c.getDouble(2));
                db.insertOrThrow("readings",null,r);added++;
            }
        }
        return added;
    }
    /** إعداد عام مخزّن في قاعدة البيانات. */
    public String setting(String key,String fallback){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?",new String[]{key})){
            return c.moveToFirst()?c.getString(0):fallback;
        }catch(Exception e){return fallback;}
    }
    public void setSetting(String key,String value){
        ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);
        getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    /** وضع التجربة: الدخول باختيار الاسم دون رمز، قبل ربط العمال بالمنظومة. */
    public boolean openAccess(){return "1".equals(setting("open_access","0"));}
    public void setOpenAccess(boolean on){setSetting("open_access",on?"1":"0");}
    /** الحسابات النشطة للاختيار منها في وضع التجربة. */
    public Cursor activeAccounts(){return getReadableDatabase().rawQuery("SELECT id,name,role FROM workers WHERE active=1 ORDER BY CASE role WHEN 'ADMIN' THEN 0 ELSE 1 END,id",null);}
    public Cursor login(String pin) {
        return getReadableDatabase().rawQuery("SELECT id,name,role FROM workers WHERE pin_hash=? AND active=1",new String[]{hash(pin)});
    }
    public boolean isNightWorker(int workerId){try(Cursor c=getReadableDatabase().rawQuery("SELECT shift_kind FROM workers WHERE id=?",new String[]{String.valueOf(workerId)})){return c.moveToFirst()&&"NIGHT".equals(c.getString(0));}}
    public ArrayList<String> workerNames() {
        ArrayList<String> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM workers WHERE active=1 ORDER BY id",null)){while(c.moveToNext())out.add(c.getString(0));}
        return out;
    }
    public Cursor workers(){return getReadableDatabase().rawQuery("SELECT id,name,'',role,shift_kind,active FROM workers ORDER BY id",null);}
    public Cursor pumps(){return getReadableDatabase().rawQuery("SELECT p.id,p.name,p.fuel,p.price,p.last_reading,p.worker_id,COALESCE(w.name,'بدون عامل'),p.active FROM pumps p LEFT JOIN workers w ON w.id=p.worker_id ORDER BY "+FUEL_ORDER+",p.id",null);}
    public void updateWorker(long id,String name,String pin,String kind){ContentValues v=new ContentValues();v.put("name",name.trim());if(!pin.trim().isEmpty())v.put("pin_hash",hash(pin));v.put("shift_kind",kind);getWritableDatabase().update("workers",v,"id=?",new String[]{String.valueOf(id)});}
    public void updatePump(long id,String name,String fuel,double price,double reading,long workerId){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("fuel",fuel.trim());v.put("price",price);v.put("last_reading",reading);v.put("worker_id",workerId);getWritableDatabase().update("pumps",v,"id=?",new String[]{String.valueOf(id)});}
    public void addWorker(String name,String pin,String kind){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("pin_hash",hash(pin));v.put("role","WORKER");v.put("shift_kind",kind);v.put("active",1);getWritableDatabase().insertOrThrow("workers",null,v);}
    public void addPump(String name,String fuel,double price,double reading,long workerId){
        if(price<=0)price=priceFor(fuel);
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("fuel",fuel.trim());v.put("price",price);v.put("last_reading",reading);v.put("worker_id",workerId);v.put("active",1);getWritableDatabase().insertOrThrow("pumps",null,v);}
    /** لا نحذف نهائيًا حفاظًا على الأرشيف، بل نوقف الحساب/الطرمبة. */
    public void setWorkerActive(long id,boolean active){ContentValues v=new ContentValues();v.put("active",active?1:0);getWritableDatabase().update("workers",v,"id=?",new String[]{String.valueOf(id)});}
    public void setPumpActive(long id,boolean active){ContentValues v=new ContentValues();v.put("active",active?1:0);getWritableDatabase().update("pumps",v,"id=?",new String[]{String.valueOf(id)});}
    /** هل الطرمبة داخل وردية مفتوحة لم يُدخل لها قراءة بعد؟ */
    public boolean pumpInOpenShift(long pumpId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM readings r JOIN shifts s ON s.id=r.shift_id WHERE r.pump_id=? AND r.current IS NULL AND s.status IN ('OPEN','RETURNED')",new String[]{String.valueOf(pumpId)})){c.moveToFirst();return c.getInt(0)>0;}}
    public boolean workerHasOpenShift(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE worker_id=? AND status IN ('OPEN','SUBMITTED','RETURNED')",new String[]{String.valueOf(id)})){c.moveToFirst();return c.getInt(0)>0;}}
    public void deleteMovement(long movementId){getWritableDatabase().delete("movements","id=?",new String[]{String.valueOf(movementId)});}
    public ArrayList<Integer> workerIds(){ArrayList<Integer> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM workers WHERE role='WORKER' AND active=1 ORDER BY id",null)){while(c.moveToNext())out.add(c.getInt(0));}return out;}
    public long openShift(int workerId, boolean night) {
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT id FROM shifts WHERE worker_id=? AND status IN ('OPEN','SUBMITTED','RETURNED') ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(workerId)})){if(c.moveToFirst())return c.getLong(0);}
        ContentValues s=new ContentValues(); s.put("worker_id",workerId); s.put("opened_at",Util.now());s.put("shift_date",ShiftDates.today());
        long shiftId=db.insertOrThrow("shifts",null,s);
        String sql=night ? "SELECT id,last_reading,price FROM pumps WHERE active=1" : "SELECT id,last_reading,price FROM pumps WHERE active=1 AND worker_id=?";
        try(Cursor c=db.rawQuery(sql,night?null:new String[]{String.valueOf(workerId)})){
            while(c.moveToNext()){ContentValues r=new ContentValues();r.put("shift_id",shiftId);r.put("pump_id",c.getLong(0));r.put("previous",c.getDouble(1));r.put("price",c.getDouble(2));db.insertOrThrow("readings",null,r);}
        }
        audit(db,shiftId,workerId,"OPEN_SHIFT","فتح الوردية"); return shiftId;
    }
    /**
     * الطرمبة الموقوفة تختفي من الوردية ما لم يكن العامل قد أدخل قراءتها فعلًا،
     * فلا تضيع مبيعات سُجّلت قبل الإيقاف.
     */
    /** ترتيب موحّد للمواد: بترول ثم ديزل ثم غاز. */
    static final String FUEL_ORDER="CASE TRIM(p.fuel) WHEN 'بترول' THEN 0 WHEN 'البترول' THEN 0 WHEN 'بنزين' THEN 0 WHEN 'البنزين' THEN 0 WHEN 'ديزل' THEN 1 WHEN 'الديزل' THEN 1 WHEN 'غاز' THEN 2 WHEN 'الغاز' THEN 2 ELSE 3 END";
    private static final String LIVE_PUMP = "(p.active=1 OR r.current IS NOT NULL)";
    public Cursor shiftReadings(long shiftId){return getReadableDatabase().rawQuery("SELECT r.id,p.name,p.fuel,r.previous,r.current,r.price,r.sales,p.active FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP+" ORDER BY CASE TRIM(p.fuel) WHEN 'بترول' THEN 0 WHEN 'البترول' THEN 0 WHEN 'بنزين' THEN 0 WHEN 'البنزين' THEN 0 WHEN 'ديزل' THEN 1 WHEN 'الديزل' THEN 1 WHEN 'غاز' THEN 2 WHEN 'الغاز' THEN 2 ELSE 3 END,p.id",new String[]{String.valueOf(shiftId)});}
    public boolean saveReading(long readingId,double current){SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT previous,price FROM readings WHERE id=?",new String[]{String.valueOf(readingId)})){if(c.moveToFirst()){double previous=c.getDouble(0),price=c.getDouble(1);if(current<previous)return false;ContentValues v=new ContentValues();v.put("current",current);v.put("sales",Calc.pumpSales(previous,current,price));db.update("readings",v,"id=?",new String[]{String.valueOf(readingId)});return true;}}return false;}
    /** تعديل القراءة السابقة يدويًا، مع إعادة حساب المبيعات إن كانت الحالية مُدخلة. */
    public boolean savePrevious(long readingId,double previous){
        if(previous<0)return false;
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT current,price FROM readings WHERE id=?",new String[]{String.valueOf(readingId)})){
            if(!c.moveToFirst())return false;
            boolean hasCurrent=!c.isNull(0);
            double current=c.getDouble(0),price=c.getDouble(1);
            if(hasCurrent&&current<previous)return false;
            ContentValues v=new ContentValues();
            v.put("previous",previous);
            if(hasCurrent)v.put("sales",Calc.pumpSales(previous,current,price));
            db.update("readings",v,"id=?",new String[]{String.valueOf(readingId)});
            // نُبقي عدّاد الطرمبة متوافقًا مع أحدث بداية أدخلها العامل.
            db.execSQL("UPDATE pumps SET last_reading=? WHERE id=(SELECT r.pump_id FROM readings r JOIN shifts s ON s.id=r.shift_id WHERE r.id=? AND s.historical=0)",new Object[]{previous,readingId});
            return true;
        }
    }
    public String validateShift(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(CASE WHEN r.current IS NULL THEN 1 ELSE 0 END),SUM(CASE WHEN r.price<=0 THEN 1 ELSE 0 END),SUM(CASE WHEN r.current<r.previous THEN 1 ELSE 0 END) FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP,new String[]{String.valueOf(shiftId)})){if(!c.moveToFirst()||c.getInt(0)==0)return "لا توجد طرمبات مسندة لهذا العامل";if(c.getInt(1)>0)return "أدخل القراءة الحالية لجميع الطرمبات";if(c.getInt(2)>0)return "سعر الوقود غير مضبوط. اطلب من المدير إدخال الأسعار";if(c.getInt(3)>0)return "إحدى القراءات الحالية أقل من القراءة السابقة";}return "";}
    public void addMovement(long shiftId,String type,String name,double amount){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("shift_id",shiftId);v.put("type",type);v.put("name",name.trim());v.put("amount",amount);v.put("created_at",Util.now());db.insertOrThrow("movements",null,v);ContentValues n=new ContentValues();n.put("type",type);n.put("name",name.trim());db.insertWithOnConflict("remembered_names",null,n,SQLiteDatabase.CONFLICT_IGNORE);}
    public Cursor movements(long shiftId){return getReadableDatabase().rawQuery("SELECT id,type,name,amount FROM movements WHERE shift_id=? ORDER BY id DESC",new String[]{String.valueOf(shiftId)});}
    public double total(long shiftId,String type){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM movements WHERE shift_id=? AND type=?",new String[]{String.valueOf(shiftId),type})){c.moveToFirst();return c.getDouble(0);}}
    /** Clearing a field removes its prior contribution only when the worker saves it. */
    public boolean clearReading(long readingId,long shiftId){
        ContentValues value=new ContentValues();value.putNull("current");value.put("sales",0);
        return getWritableDatabase().update("readings",value,"id=? AND shift_id=? AND EXISTS(SELECT 1 FROM shifts WHERE id=? AND status IN ('OPEN','RETURNED'))",new String[]{String.valueOf(readingId),String.valueOf(shiftId),String.valueOf(shiftId)})==1;
    }
    public double sales(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN r.current IS NOT NULL AND r.current>=r.previous AND r.price>0 THEN (r.current-r.previous)*r.price ELSE 0 END),0) FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP,new String[]{String.valueOf(shiftId)})){c.moveToFirst();return c.getDouble(0);}}
    public double balance(long shiftId){return Calc.balance(sales(shiftId),total(shiftId,"COLLECTION"),total(shiftId,"CASH"),total(shiftId,"DEBT"),total(shiftId,"EXPENSE"));}
    public void submit(long shiftId,int workerId,String reason){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("sales",sales(shiftId));v.put("collections",total(shiftId,"COLLECTION"));v.put("cash_delivered",total(shiftId,"CASH"));v.put("debts",total(shiftId,"DEBT"));v.put("expenses",total(shiftId,"EXPENSE"));v.put("balance",balance(shiftId));v.put("difference_reason",reason);v.put("manager_note","");v.put("status","SUBMITTED");v.put("closed_at",Util.now());v.put("sync_state","PENDING");db.execSQL("UPDATE shifts SET revision=revision+1 WHERE id=?",new Object[]{shiftId});db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,workerId,"SUBMIT","إرسال/تعديل الوردية؛ السبب: "+reason);}
    public Cursor archive(int workerId,boolean admin){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.status,s.sales,s.balance,s.sync_state,COALESCE(s.manager_note,''),COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) FROM shifts s JOIN workers w ON w.id=s.worker_id "+(admin?"":"WHERE s.worker_id=? ")+"ORDER BY COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) DESC,s.id DESC",admin?null:new String[]{String.valueOf(workerId)});}
    public Cursor submitted(){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.sales,s.balance,s.difference_reason FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.status='SUBMITTED' ORDER BY s.id",null);}
    public int pendingCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE status='SUBMITTED'",null)){c.moveToFirst();return c.getInt(0);}}
    public int pendingSyncCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE sync_state='PENDING'",null)){c.moveToFirst();return c.getInt(0);}}
    public Cursor pendingSync(){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.closed_at,s.status,s.sales,s.collections,s.cash_delivered,s.debts,s.expenses,s.balance,s.difference_reason,s.revision,COALESCE(s.manager_note,'') FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.sync_state='PENDING' ORDER BY s.id",null);}
    public void markSynced(long shiftId){ContentValues v=new ContentValues();v.put("sync_state","SYNCED");getWritableDatabase().update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});}
    public Cursor syncReadings(long shiftId){return getReadableDatabase().rawQuery("SELECT p.name,p.fuel,r.previous,r.current,r.price,r.sales FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP+" ORDER BY p.id",new String[]{String.valueOf(shiftId)});}
    public Cursor syncMovements(long shiftId){return getReadableDatabase().rawQuery("SELECT type,name,amount FROM movements WHERE shift_id=? ORDER BY id",new String[]{String.valueOf(shiftId)});}
    public void approve(long shiftId){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(!isHistorical(shiftId))db.execSQL("UPDATE pumps SET last_reading=(SELECT r.current FROM readings r WHERE r.shift_id=? AND r.pump_id=pumps.id) WHERE id IN (SELECT pump_id FROM readings WHERE shift_id=? AND current IS NOT NULL)",new Object[]{shiftId,shiftId});ContentValues v=new ContentValues();v.put("status","APPROVED");v.put("sync_state","PENDING");db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,1,"APPROVE","اعتماد المدير");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void returnToWorker(long shiftId,String note){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("status","RETURNED");v.put("manager_note",note);v.put("sync_state","PENDING");db.execSQL("UPDATE shifts SET revision=revision+1 WHERE id=?",new Object[]{shiftId});db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,1,"RETURN","إرجاع للعامل: "+note);}
    public String shiftStatus(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT status FROM shifts WHERE id=?",new String[]{String.valueOf(shiftId)})){return c.moveToFirst()?c.getString(0):"OPEN";}}
    public String managerNote(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(manager_note,'') FROM shifts WHERE id=?",new String[]{String.valueOf(shiftId)})){return c.moveToFirst()?c.getString(0):"";}}
    /** سعر اللتر السائد لنوع وقود، أو صفر إن لم يوجد. */
    public double priceFor(String fuel){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(price) FROM pumps WHERE fuel=?",new String[]{fuel.trim()})){return c.moveToFirst()?c.getDouble(0):0;}}
    /** أنواع الوقود المستخدمة فعليًا مع سعر كل نوع وعدد طرمباته. */
    public Cursor fuelPrices(){return getReadableDatabase().rawQuery(
        "SELECT fuel,MIN(price),MAX(price),COUNT(*) FROM pumps p WHERE active=1 GROUP BY fuel ORDER BY "+FUEL_ORDER+",fuel",null);}
    /** يضبط سعر اللتر لكل طرمبات نوع وقود واحد دفعة واحدة، ويرجع عدد ما تغيّر. */
    public int setFuelPrice(String fuel,double price){
        if(price<=0)return 0;
        ContentValues v=new ContentValues();v.put("price",price);
        return getWritableDatabase().update("pumps",v,"fuel=? AND price<>?",new String[]{fuel,String.valueOf(price)});}
    /** يحدّث سعر اللتر لكل طرمبة حسب نوع الوقود، ويرجع عدد ما تغيّر فعلًا. */
    public int applyPrices(org.json.JSONObject prices){
        int changed=0;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            java.util.Iterator<String> keys=prices.keys();
            while(keys.hasNext()){
                String fuel=keys.next();double price=prices.optDouble(fuel,-1);
                if(price<=0)continue;
                ContentValues v=new ContentValues();v.put("price",price);
                changed+=db.update("pumps",v,"fuel=? AND price<>?",new String[]{fuel,String.valueOf(price)});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return changed;}
    /** قائمة الأشهر التي فيها ورديات، الأحدث أولًا. */
    public ArrayList<String> months(){ArrayList<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT substr(COALESCE(NULLIF(shift_date,''),opened_at),1,7) FROM shifts ORDER BY 1 DESC",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
    /** تجميع شهري لكل عامل: عدد الورديات والمبيعات والفروقات. */
    public Cursor monthlyByWorker(String month){return getReadableDatabase().rawQuery(
        "SELECT w.name,COUNT(*),COALESCE(SUM(s.sales),0),COALESCE(SUM(s.collections),0),COALESCE(SUM(s.cash_delivered),0),"+
        "COALESCE(SUM(s.debts),0),COALESCE(SUM(s.expenses),0),COALESCE(SUM(s.balance),0),"+
        "SUM(CASE WHEN ABS(s.balance)>=0.01 THEN 1 ELSE 0 END) "+
        "FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE substr(COALESCE(NULLIF(s.shift_date,''),s.opened_at),1,7)=? AND s.status IN ('SUBMITTED','APPROVED','RETURNED') "+
        "GROUP BY w.id,w.name ORDER BY 3 DESC",new String[]{month});}
    public Cursor shiftHeader(long shiftId){return getReadableDatabase().rawQuery("SELECT w.name,s.opened_at,COALESCE(s.closed_at,''),s.status,COALESCE(s.difference_reason,''),COALESCE(s.manager_note,''),COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.id=?",new String[]{String.valueOf(shiftId)});}
    public Cursor exportShifts(int workerId,boolean admin){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,COALESCE(s.closed_at,''),s.status,s.sales,s.collections,s.cash_delivered,s.debts,s.expenses,s.balance,COALESCE(s.difference_reason,''),COALESCE(s.manager_note,''),s.sync_state FROM shifts s JOIN workers w ON w.id=s.worker_id "+(admin?"":"WHERE s.worker_id=? ")+"ORDER BY COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) DESC,s.id DESC",admin?null:new String[]{String.valueOf(workerId)});}
    public String shiftDate(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(NULLIF(shift_date,''),substr(opened_at,1,10)) FROM shifts WHERE id=?",new String[]{String.valueOf(id)})){
            return c.moveToFirst()?c.getString(0):ShiftDates.today();
        }
    }
    public boolean isHistorical(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT historical FROM shifts WHERE id=?",new String[]{String.valueOf(id)})){
            return c.moveToFirst()&&c.getInt(0)==1;
        }
    }
    public void setShiftDate(long id,String date){
        ShiftDates.validate(date);
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        try(Cursor c=database.rawQuery("SELECT opened_at,historical,status FROM shifts WHERE id=?",new String[]{String.valueOf(id)})){
            if(!c.moveToFirst()||!("OPEN".equals(c.getString(2))||"RETURNED".equals(c.getString(2))))throw new IllegalStateException("الوردية مغلقة");
            boolean historical=c.getInt(1)==1||date.compareTo(c.getString(0).substring(0,10))<0;
            ContentValues values=new ContentValues();values.put("shift_date",date);values.put("historical",historical?1:0);
            database.update("shifts",values,"id=?",new String[]{String.valueOf(id)});
            audit(database,id,0,"SHIFT_DATE",date);
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
    }
    public boolean saveHistoricalBaseline(long shiftId,long readingId,double previous,double price){
        if(!isHistorical(shiftId)||!Double.isFinite(previous)||!Double.isFinite(price)||previous<0||price<=0)return false;
        SQLiteDatabase database=getWritableDatabase();
        try(Cursor c=database.rawQuery("SELECT r.current FROM readings r JOIN shifts s ON s.id=r.shift_id WHERE r.id=? AND r.shift_id=? AND s.status IN ('OPEN','RETURNED')",new String[]{String.valueOf(readingId),String.valueOf(shiftId)})){
            if(!c.moveToFirst()||(!c.isNull(0)&&c.getDouble(0)<previous))return false;
            ContentValues values=new ContentValues();values.put("previous",previous);values.put("price",price);
            values.put("sales",c.isNull(0)?0:(c.getDouble(0)-previous)*price);
            return database.update("readings",values,"id=? AND shift_id=?",new String[]{String.valueOf(readingId),String.valueOf(shiftId)})==1;
        }
    }
    // ==================== الصناديق ====================
    public long addCashbox(String name,double opening){
        String clean=name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب اسم الصندوق");
        if(!Double.isFinite(opening))throw new IllegalArgumentException("الرصيد الافتتاحي غير صالح");
        ContentValues v=new ContentValues();v.put("name",clean);v.put("opening",opening);v.put("created_at",Util.now());
        long id=getWritableDatabase().insertWithOnConflict("cashboxes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1)throw new IllegalArgumentException("يوجد صندوق بهذا الاسم");
        return id;
    }
    public void renameCashbox(long id,String name){
        String clean=name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب اسم الصندوق");
        ContentValues v=new ContentValues();v.put("name",clean);
        if(getWritableDatabase().updateWithOnConflict("cashboxes",v,"id=?",new String[]{String.valueOf(id)},SQLiteDatabase.CONFLICT_IGNORE)!=1)
            throw new IllegalArgumentException("يوجد صندوق بهذا الاسم");
    }
    public void setCashboxOpening(long id,double opening){
        if(!Double.isFinite(opening))throw new IllegalArgumentException("الرصيد الافتتاحي غير صالح");
        ContentValues v=new ContentValues();v.put("opening",opening);
        getWritableDatabase().update("cashboxes",v,"id=?",new String[]{String.valueOf(id)});
    }
    public void setCashboxActive(long id,boolean active){
        ContentValues v=new ContentValues();v.put("active",active?1:0);
        getWritableDatabase().update("cashboxes",v,"id=?",new String[]{String.valueOf(id)});
    }
    /** يُحذف الصندوق فقط إذا لم تُسجَّل عليه أي حركة، حفاظًا على سلامة الأرشيف. */
    public boolean deleteCashbox(long id){
        if(cashboxEntryCount(id)>0)return false;
        return getWritableDatabase().delete("cashboxes","id=?",new String[]{String.valueOf(id)})==1;
    }
    public int cashboxEntryCount(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cashbox_entries WHERE box_id=?",new String[]{String.valueOf(id)})){
            c.moveToFirst();return c.getInt(0);
        }
    }
    /** id,name,opening,active,in,out,balance */
    public Cursor cashboxes(boolean onlyActive){
        return getReadableDatabase().rawQuery(
            "SELECT b.id,b.name,b.opening,b.active,"+
            "COALESCE((SELECT SUM(e.amount) FROM cashbox_entries e WHERE e.box_id=b.id AND e.direction='IN'),0),"+
            "COALESCE((SELECT SUM(e.amount) FROM cashbox_entries e WHERE e.box_id=b.id AND e.direction='OUT'),0),"+
            "b.opening+COALESCE((SELECT SUM(e.amount) FROM cashbox_entries e WHERE e.box_id=b.id AND e.direction='IN'),0)"+
            "-COALESCE((SELECT SUM(e.amount) FROM cashbox_entries e WHERE e.box_id=b.id AND e.direction='OUT'),0) "+
            "FROM cashboxes b "+(onlyActive?"WHERE b.active=1 ":"")+"ORDER BY b.active DESC,b.id",null);
    }
    public double cashboxBalance(long id){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT b.opening+COALESCE((SELECT SUM(e.amount) FROM cashbox_entries e WHERE e.box_id=b.id AND e.direction='IN'),0)"+
            "-COALESCE((SELECT SUM(e.amount) FROM cashbox_entries e WHERE e.box_id=b.id AND e.direction='OUT'),0) FROM cashboxes b WHERE b.id=?",
            new String[]{String.valueOf(id)})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    /** إجمالي أرصدة الصناديق النشطة. */
    public double cashboxesTotal(){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COALESCE(SUM(b.opening),0)+COALESCE((SELECT SUM(CASE WHEN e.direction='IN' THEN e.amount ELSE -e.amount END) FROM cashbox_entries e JOIN cashboxes x ON x.id=e.box_id WHERE x.active=1),0) FROM cashboxes b WHERE b.active=1",null)){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    public long addCashboxEntry(long boxId,String direction,double amount,String note,String date){
        return addCashboxEntry(boxId,direction,amount,note,date,0);
    }
    public long addCashboxEntry(long boxId,String direction,double amount,String note,String date,long sourceShift){
        if(!"IN".equals(direction)&&!"OUT".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        ContentValues v=new ContentValues();
        v.put("box_id",boxId);v.put("direction",direction);v.put("amount",amount);
        v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());v.put("source_shift",sourceShift);
        return getWritableDatabase().insertOrThrow("cashbox_entries",null,v);
    }
    public boolean deleteCashboxEntry(long id){
        return getWritableDatabase().delete("cashbox_entries","id=?",new String[]{String.valueOf(id)})==1;
    }
    /** id,direction,amount,note,entry_date,box_name */
    public Cursor cashboxEntries(long boxId,int limit){
        String where=boxId>0?"WHERE e.box_id=? ":"";
        return getReadableDatabase().rawQuery(
            "SELECT e.id,e.direction,e.amount,e.note,e.entry_date,b.name,e.source_shift FROM cashbox_entries e JOIN cashboxes b ON b.id=e.box_id "+
            where+"ORDER BY e.entry_date DESC,e.id DESC LIMIT "+Math.max(1,limit),
            boxId>0?new String[]{String.valueOf(boxId)}:null);
    }
    // ==================== حركة المخاريج ====================
    public long addExpense(String category,double amount,String note,String date,long boxId,long sourceShift){
        String clean=category.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب باب المصروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        long id;
        try{
            ContentValues v=new ContentValues();
            v.put("category",clean);v.put("amount",amount);v.put("note",note.trim());
            v.put("entry_date",date);v.put("created_at",Util.now());v.put("source_shift",sourceShift);v.put("box_id",boxId);
            id=db.insertOrThrow("expense_entries",null,v);
            // المصروف المدفوع من صندوق يخرج منه فعليًا.
            if(boxId>0)addCashboxEntry(boxId,"OUT",amount,"مخاريج: "+clean+(note.trim().isEmpty()?"":" — "+note.trim()),date,sourceShift);
            ContentValues n=new ContentValues();n.put("type","EXPENSE");n.put("name",clean);
            db.insertWithOnConflict("remembered_names",null,n,SQLiteDatabase.CONFLICT_IGNORE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return id;
    }
    public boolean deleteExpense(long id){
        return getWritableDatabase().delete("expense_entries","id=?",new String[]{String.valueOf(id)})==1;
    }
    /** category,total,count — أبواب المصروف مرتّبة بالأكبر. */
    public Cursor expenseCategories(){
        return getReadableDatabase().rawQuery(
            "SELECT category,SUM(amount),COUNT(*) FROM expense_entries GROUP BY category ORDER BY SUM(amount) DESC",null);
    }
    /** id,category,amount,note,entry_date,source_shift */
    public Cursor expenses(String category,int limit){
        boolean all=category==null||category.isEmpty();
        return getReadableDatabase().rawQuery(
            "SELECT id,category,amount,note,entry_date,source_shift FROM expense_entries "+
            (all?"":"WHERE category=? ")+"ORDER BY entry_date DESC,id DESC LIMIT "+Math.max(1,limit),
            all?null:new String[]{category});
    }
    public double expensesTotal(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM expense_entries",null)){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    /** إجمالي مصروف شهر بصيغة yyyy-MM. */
    public double expensesInMonth(String month){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM expense_entries WHERE substr(entry_date,1,7)=?",new String[]{month})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    // ==================== ربط الوردية بالصناديق والديون والمواد ====================
    /** الصندوق الافتراضي الذي يستقبل نقد الورديات، أو 0 إن لم يُختر. */
    public long defaultCashbox(){
        long id=0;
        try{id=Long.parseLong(setting("default_cashbox","0"));}catch(NumberFormatException ignored){}
        if(id<=0)return 0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cashboxes WHERE id=? AND active=1",new String[]{String.valueOf(id)})){
            c.moveToFirst();return c.getInt(0)==1?id:0;
        }
    }
    public void setDefaultCashbox(long id){setSetting("default_cashbox",String.valueOf(id));}
    public boolean shiftPosted(long shiftId){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT (SELECT COUNT(*) FROM cashbox_entries WHERE source_shift=?)+(SELECT COUNT(*) FROM debt_entries WHERE source_shift=?)"+
            "+(SELECT COUNT(*) FROM expense_entries WHERE source_shift=?)",
            new String[]{String.valueOf(shiftId),String.valueOf(shiftId),String.valueOf(shiftId)})){
            c.moveToFirst();return c.getInt(0)>0;
        }
    }
    /** يلغي ترحيل وردية (عند حذفها أو إعادة ترحيلها). */
    public void unpostShift(long shiftId){
        SQLiteDatabase db=getWritableDatabase();
        db.delete("cashbox_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
        db.delete("debt_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
        db.delete("expense_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
        db.delete("material_entries","note LIKE ?",new String[]{"وردية #"+shiftId+"%"});
    }
    /**
     * يرحّل وردية مُغلقة إلى بقية السجلات في معاملة واحدة:
     * النقد المسلّم إلى الصندوق، وديون الوردية على المدينين بأسمائهم،
     * واللترات المباعة تُخصم من المخزون.
     * يعيد سطر ملخّص لما جرى.
     */
    public String postShift(long shiftId,long cashboxId){
        if(shiftPosted(shiftId))return "";
        SQLiteDatabase db=getWritableDatabase();
        String date=shiftDate(shiftId);
        StringBuilder log=new StringBuilder();
        db.beginTransaction();
        try{
            double cash=total(shiftId,"CASH");
            if(cashboxId>0&&cash>0){
                addCashboxEntry(cashboxId,"IN",cash,"نقد مسلّم من وردية #"+shiftId,date,shiftId);
                log.append("• دخل الصندوق ").append(Calc.money(cash)).append(" ر.ي\n");
            }
            int debtors=0;double debtTotal=0;
            try(Cursor c=db.rawQuery("SELECT name,SUM(amount) FROM movements WHERE shift_id=? AND type='DEBT' GROUP BY name",
                    new String[]{String.valueOf(shiftId)})){
                while(c.moveToNext()){
                    String name=c.getString(0).trim();
                    double amount=c.getDouble(1);
                    if(name.isEmpty()||!(amount>0))continue;
                    long debtorId=findOrCreateDebtor(db,name);
                    addDebtEntry(debtorId,"DEBT",amount,"دين من وردية #"+shiftId,date,shiftId);
                    debtors++;debtTotal+=amount;
                }
            }
            int expenses=0;double expenseTotal=0;
            try(Cursor c=db.rawQuery("SELECT name,SUM(amount) FROM movements WHERE shift_id=? AND type='EXPENSE' GROUP BY name",
                    new String[]{String.valueOf(shiftId)})){
                while(c.moveToNext()){
                    String name=c.getString(0).trim();
                    double amount=c.getDouble(1);
                    if(name.isEmpty()||!(amount>0))continue;
                    ContentValues v=new ContentValues();
                    v.put("category",name);v.put("amount",amount);v.put("note","وردية #"+shiftId);
                    v.put("entry_date",date);v.put("created_at",Util.now());v.put("source_shift",shiftId);v.put("box_id",0);
                    db.insertOrThrow("expense_entries",null,v);
                    expenses++;expenseTotal+=amount;
                }
            }
            if(expenses>0)log.append("• سُجّل ").append(Calc.money(expenseTotal)).append(" ر.ي مخاريج\n");
            if(debtors>0)log.append("• قُيّد ").append(Calc.money(debtTotal)).append(" ر.ي على ").append(debtors).append(" مدين\n");
            int materials=0;
            try(Cursor c=db.rawQuery(
                "SELECT TRIM(p.fuel),SUM(r.current-r.previous) FROM readings r JOIN pumps p ON p.id=r.pump_id "+
                "WHERE r.shift_id=? AND r.current IS NOT NULL AND r.current>=r.previous GROUP BY TRIM(p.fuel)",
                new String[]{String.valueOf(shiftId)})){
                while(c.moveToNext()){
                    String fuel=normalizeFuel(c.getString(0));
                    double litres=c.getDouble(1);
                    if(!(litres>0))continue;
                    ContentValues v=new ContentValues();
                    v.put("material",fuel);v.put("direction","OUT");v.put("litres",litres);
                    v.put("note","وردية #"+shiftId+" — مبيعات");v.put("entry_date",date);v.put("created_at",Util.now());
                    db.insertOrThrow("material_entries",null,v);
                    materials++;
                }
            }
            if(materials>0)log.append("• خُصمت لترات المبيعات من المخزون\n");
            audit(db,shiftId,0,"POST_SHIFT","ترحيل الوردية إلى الصناديق والديون والمواد");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return log.toString().trim();
    }
    static String normalizeFuel(String fuel){
        String f=fuel==null?"":fuel.trim();
        if(f.equals("البترول")||f.equals("بنزين")||f.equals("البنزين"))return "بترول";
        if(f.equals("الديزل"))return "ديزل";
        if(f.equals("الغاز"))return "غاز";
        return f;
    }
    private long findOrCreateDebtor(SQLiteDatabase db,String name){
        try(Cursor c=db.rawQuery("SELECT id FROM debtors WHERE name=?",new String[]{name})){
            if(c.moveToFirst())return c.getLong(0);
        }
        ContentValues v=new ContentValues();
        v.put("name",name);v.put("phone","");v.put("opening",0);v.put("created_at",Util.now());
        return db.insertOrThrow("debtors",null,v);
    }
    // ==================== حركة الديون ====================
    public long addDebtor(String name,String phone,double opening){
        String clean=name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب اسم المدين");
        if(!Double.isFinite(opening)||opening<0)throw new IllegalArgumentException("الدين الافتتاحي غير صالح");
        ContentValues v=new ContentValues();
        v.put("name",clean);v.put("phone",phone.trim());v.put("opening",opening);v.put("created_at",Util.now());
        long id=getWritableDatabase().insertWithOnConflict("debtors",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1)throw new IllegalArgumentException("يوجد مدين بهذا الاسم");
        return id;
    }
    public void updateDebtor(long id,String name,String phone,double opening){
        String clean=name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب اسم المدين");
        if(!Double.isFinite(opening)||opening<0)throw new IllegalArgumentException("الدين الافتتاحي غير صالح");
        ContentValues v=new ContentValues();v.put("name",clean);v.put("phone",phone.trim());v.put("opening",opening);
        if(getWritableDatabase().updateWithOnConflict("debtors",v,"id=?",new String[]{String.valueOf(id)},SQLiteDatabase.CONFLICT_IGNORE)!=1)
            throw new IllegalArgumentException("يوجد مدين بهذا الاسم");
    }
    public void setDebtorActive(long id,boolean active){
        ContentValues v=new ContentValues();v.put("active",active?1:0);
        getWritableDatabase().update("debtors",v,"id=?",new String[]{String.valueOf(id)});
    }
    public int debtEntryCount(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM debt_entries WHERE debtor_id=?",new String[]{String.valueOf(id)})){
            c.moveToFirst();return c.getInt(0);
        }
    }
    public boolean deleteDebtor(long id){
        if(debtEntryCount(id)>0)return false;
        return getWritableDatabase().delete("debtors","id=?",new String[]{String.valueOf(id)})==1;
    }
    /** id,name,phone,opening,active,debt,paid,balance */
    public Cursor debtors(boolean onlyActive){
        return getReadableDatabase().rawQuery(
            "SELECT d.id,d.name,d.phone,d.opening,d.active,"+
            "COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='DEBT'),0),"+
            "COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='PAID'),0),"+
            "d.opening+COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='DEBT'),0)"+
            "-COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='PAID'),0) "+
            "FROM debtors d "+(onlyActive?"WHERE d.active=1 ":"")+"ORDER BY d.active DESC,d.id",null);
    }
    public double debtorBalance(long id){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT d.opening+COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='DEBT'),0)"+
            "-COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='PAID'),0) FROM debtors d WHERE d.id=?",
            new String[]{String.valueOf(id)})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    /** إجمالي الديون غير المسدّدة على المدينين النشطين. */
    public double debtsTotal(){
        double total=0;
        try(Cursor c=debtors(true)){while(c.moveToNext())total+=c.getDouble(7);}
        return total;
    }
    public long addDebtEntry(long debtorId,String direction,double amount,String note,String date){
        return addDebtEntry(debtorId,direction,amount,note,date,0);
    }
    public long addDebtEntry(long debtorId,String direction,double amount,String note,String date,long sourceShift){
        if(!"DEBT".equals(direction)&&!"PAID".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        ContentValues v=new ContentValues();
        v.put("debtor_id",debtorId);v.put("direction",direction);v.put("amount",amount);
        v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());v.put("source_shift",sourceShift);
        return getWritableDatabase().insertOrThrow("debt_entries",null,v);
    }
    public boolean deleteDebtEntry(long id){
        return getWritableDatabase().delete("debt_entries","id=?",new String[]{String.valueOf(id)})==1;
    }
    /** id,name,phone,opening — بيانات مدين واحد. */
    public String[] debtorInfo(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name,COALESCE(phone,''),opening FROM debtors WHERE id=?",
                new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())return null;
            return new String[]{c.getString(0),c.getString(1),String.valueOf(c.getDouble(2))};
        }
    }
    /** كل حركات المدين بالترتيب الزمني الصاعد لكشف الحساب: direction,amount,note,entry_date */
    public Cursor debtLedger(long debtorId){
        return getReadableDatabase().rawQuery(
            "SELECT direction,amount,note,entry_date FROM debt_entries WHERE debtor_id=? ORDER BY entry_date,id",
            new String[]{String.valueOf(debtorId)});
    }
    /** آخر تاريخ حركة للمدين، أو فراغ. */
    public String debtorLastActivity(long debtorId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT MAX(entry_date) FROM debt_entries WHERE debtor_id=?",new String[]{String.valueOf(debtorId)})){
            return c.moveToFirst()&&c.getString(0)!=null?c.getString(0):"";
        }
    }
    /** id,direction,amount,note,entry_date,debtor_name */
    public Cursor debtEntries(long debtorId,int limit){
        String where=debtorId>0?"WHERE e.debtor_id=? ":"";
        return getReadableDatabase().rawQuery(
            "SELECT e.id,e.direction,e.amount,e.note,e.entry_date,d.name,e.source_shift FROM debt_entries e JOIN debtors d ON d.id=e.debtor_id "+
            where+"ORDER BY e.entry_date DESC,e.id DESC LIMIT "+Math.max(1,limit),
            debtorId>0?new String[]{String.valueOf(debtorId)}:null);
    }
    // ==================== حركة المواد ====================
    public static final String[] MATERIALS={"بترول","ديزل","غاز"};
    public long addMaterialEntry(String material,String direction,double litres,String note,String date){
        if(!"IN".equals(direction)&&!"OUT".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(litres)||litres<=0)throw new IllegalArgumentException("اكتب كمية أكبر من صفر");
        ContentValues v=new ContentValues();
        v.put("material",material);v.put("direction",direction);v.put("litres",litres);
        v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());
        return getWritableDatabase().insertOrThrow("material_entries",null,v);
    }
    public boolean deleteMaterialEntry(long id){
        return getWritableDatabase().delete("material_entries","id=?",new String[]{String.valueOf(id)})==1;
    }
    /** الوارد والصادر والرصيد المخزني لمادة واحدة. المبيعات المحفوظة تُخصم كصادر. */
    public double[] materialSummary(String material){
        double in=0,out=0;
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN litres ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN direction='OUT' THEN litres ELSE 0 END),0) FROM material_entries WHERE material=?",
            new String[]{material})){
            if(c.moveToFirst()){in=c.getDouble(0);out=c.getDouble(1);}
        }
        // المبيعات تدخل كحركات صادرة عند ترحيل الوردية، فلا تُخصم مرتين.
        double sold=postedSales(material);
        return new double[]{in,out,sold,in-out};
    }
    /** اللترات المرحّلة من الورديات ضمن الصادر. */
    public double postedSales(String material){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COALESCE(SUM(litres),0) FROM material_entries WHERE material=? AND direction='OUT' AND note LIKE 'وردية #%'",
            new String[]{material})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    /** اللترات المباعة فعليًا من الورديات المغلقة لهذه المادة. */
    public double soldLitres(String material){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN r.current IS NOT NULL AND r.current>=r.previous THEN r.current-r.previous ELSE 0 END),0) "+
            "FROM readings r JOIN pumps p ON p.id=r.pump_id JOIN shifts s ON s.id=r.shift_id "+
            "WHERE s.status<>'OPEN' AND TRIM(p.fuel) IN (?,?)",
            new String[]{material,"ال"+material})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    /** id,material,direction,litres,note,entry_date */
    public Cursor materialEntries(String material,int limit){
        boolean all=material==null||material.isEmpty();
        return getReadableDatabase().rawQuery(
            "SELECT id,material,direction,litres,note,entry_date FROM material_entries "+
            (all?"":"WHERE material=? ")+"ORDER BY entry_date DESC,id DESC LIMIT "+Math.max(1,limit),
            all?null:new String[]{material});
    }
    private void audit(SQLiteDatabase db,long shiftId,int workerId,String action,String details){ContentValues v=new ContentValues();v.put("shift_id",shiftId);v.put("worker_id",workerId);v.put("action",action);v.put("details",details);v.put("created_at",Util.now());db.insert("audit_log",null,v);}
}
