package com.alameer.station.shifts;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME = "alameer_station.db";
    private static final int DB_VERSION = 3;
    public Db(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE workers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,pin_hash TEXT NOT NULL UNIQUE,role TEXT NOT NULL,shift_kind TEXT NOT NULL DEFAULT 'DAY',active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE pumps(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,fuel TEXT NOT NULL,price REAL NOT NULL DEFAULT 0,last_reading REAL NOT NULL DEFAULT 0,worker_id INTEGER,active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE shifts(id INTEGER PRIMARY KEY AUTOINCREMENT,worker_id INTEGER NOT NULL,opened_at TEXT NOT NULL,closed_at TEXT,status TEXT NOT NULL DEFAULT 'OPEN',sales REAL NOT NULL DEFAULT 0,collections REAL NOT NULL DEFAULT 0,cash_delivered REAL NOT NULL DEFAULT 0,debts REAL NOT NULL DEFAULT 0,expenses REAL NOT NULL DEFAULT 0,balance REAL NOT NULL DEFAULT 0,difference_reason TEXT DEFAULT '',manager_note TEXT DEFAULT '',sync_state TEXT NOT NULL DEFAULT 'LOCAL',revision INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id INTEGER NOT NULL,pump_id INTEGER NOT NULL,previous REAL NOT NULL,current REAL,price REAL NOT NULL,sales REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id INTEGER NOT NULL,type TEXT NOT NULL,name TEXT NOT NULL,amount REAL NOT NULL,created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE remembered_names(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL,name TEXT NOT NULL,UNIQUE(type,name))");
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

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE shifts ADD COLUMN manager_note TEXT DEFAULT ''"); } catch (Exception ignored) {}
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
    public Cursor pumps(){return getReadableDatabase().rawQuery("SELECT p.id,p.name,p.fuel,p.price,p.last_reading,p.worker_id,COALESCE(w.name,'بدون عامل'),p.active FROM pumps p LEFT JOIN workers w ON w.id=p.worker_id ORDER BY p.id",null);}
    public void updateWorker(long id,String name,String pin,String kind){ContentValues v=new ContentValues();v.put("name",name.trim());if(!pin.trim().isEmpty())v.put("pin_hash",hash(pin));v.put("shift_kind",kind);getWritableDatabase().update("workers",v,"id=?",new String[]{String.valueOf(id)});}
    public void updatePump(long id,String name,String fuel,double price,double reading,long workerId){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("fuel",fuel.trim());v.put("price",price);v.put("last_reading",reading);v.put("worker_id",workerId);getWritableDatabase().update("pumps",v,"id=?",new String[]{String.valueOf(id)});}
    public void addWorker(String name,String pin,String kind){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("pin_hash",hash(pin));v.put("role","WORKER");v.put("shift_kind",kind);v.put("active",1);getWritableDatabase().insertOrThrow("workers",null,v);}
    public void addPump(String name,String fuel,double price,double reading,long workerId){
        if(price<=0)price=priceFor(fuel);
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("fuel",fuel.trim());v.put("price",price);v.put("last_reading",reading);v.put("worker_id",workerId);v.put("active",1);getWritableDatabase().insertOrThrow("pumps",null,v);}
    /** لا نحذف نهائيًا حفاظًا على الأرشيف، بل نوقف الحساب/الطرمبة. */
    public void setWorkerActive(long id,boolean active){ContentValues v=new ContentValues();v.put("active",active?1:0);getWritableDatabase().update("workers",v,"id=?",new String[]{String.valueOf(id)});}
    public void setPumpActive(long id,boolean active){ContentValues v=new ContentValues();v.put("active",active?1:0);getWritableDatabase().update("pumps",v,"id=?",new String[]{String.valueOf(id)});}
    public boolean workerHasOpenShift(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE worker_id=? AND status IN ('OPEN','SUBMITTED','RETURNED')",new String[]{String.valueOf(id)})){c.moveToFirst();return c.getInt(0)>0;}}
    public void deleteMovement(long movementId){getWritableDatabase().delete("movements","id=?",new String[]{String.valueOf(movementId)});}
    public ArrayList<Integer> workerIds(){ArrayList<Integer> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM workers WHERE role='WORKER' AND active=1 ORDER BY id",null)){while(c.moveToNext())out.add(c.getInt(0));}return out;}
    public long openShift(int workerId, boolean night) {
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT id FROM shifts WHERE worker_id=? AND status IN ('OPEN','SUBMITTED','RETURNED') ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(workerId)})){if(c.moveToFirst())return c.getLong(0);}
        ContentValues s=new ContentValues(); s.put("worker_id",workerId); s.put("opened_at",Util.now());
        long shiftId=db.insertOrThrow("shifts",null,s);
        String sql=night ? "SELECT id,last_reading,price FROM pumps WHERE active=1" : "SELECT id,last_reading,price FROM pumps WHERE active=1 AND worker_id=?";
        try(Cursor c=db.rawQuery(sql,night?null:new String[]{String.valueOf(workerId)})){
            while(c.moveToNext()){ContentValues r=new ContentValues();r.put("shift_id",shiftId);r.put("pump_id",c.getLong(0));r.put("previous",c.getDouble(1));r.put("price",c.getDouble(2));db.insertOrThrow("readings",null,r);}
        }
        audit(db,shiftId,workerId,"OPEN_SHIFT","فتح الوردية"); return shiftId;
    }
    public Cursor shiftReadings(long shiftId){return getReadableDatabase().rawQuery("SELECT r.id,p.name,p.fuel,r.previous,r.current,r.price,r.sales FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? ORDER BY p.id",new String[]{String.valueOf(shiftId)});}
    public boolean saveReading(long readingId,double current){SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT previous,price FROM readings WHERE id=?",new String[]{String.valueOf(readingId)})){if(c.moveToFirst()){double previous=c.getDouble(0),price=c.getDouble(1);if(current<previous)return false;ContentValues v=new ContentValues();v.put("current",current);v.put("sales",Calc.pumpSales(previous,current,price));db.update("readings",v,"id=?",new String[]{String.valueOf(readingId)});return true;}}return false;}
    public String validateShift(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(CASE WHEN current IS NULL THEN 1 ELSE 0 END),SUM(CASE WHEN price<=0 THEN 1 ELSE 0 END),SUM(CASE WHEN current<previous THEN 1 ELSE 0 END) FROM readings WHERE shift_id=?",new String[]{String.valueOf(shiftId)})){if(!c.moveToFirst()||c.getInt(0)==0)return "لا توجد طرمبات مسندة لهذا العامل";if(c.getInt(1)>0)return "أدخل القراءة الحالية لجميع الطرمبات";if(c.getInt(2)>0)return "سعر الوقود غير مضبوط. اطلب من المدير إدخال الأسعار";if(c.getInt(3)>0)return "إحدى القراءات الحالية أقل من القراءة السابقة";}return "";}
    public void addMovement(long shiftId,String type,String name,double amount){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("shift_id",shiftId);v.put("type",type);v.put("name",name.trim());v.put("amount",amount);v.put("created_at",Util.now());db.insertOrThrow("movements",null,v);ContentValues n=new ContentValues();n.put("type",type);n.put("name",name.trim());db.insertWithOnConflict("remembered_names",null,n,SQLiteDatabase.CONFLICT_IGNORE);}
    public Cursor movements(long shiftId){return getReadableDatabase().rawQuery("SELECT id,type,name,amount FROM movements WHERE shift_id=? ORDER BY id DESC",new String[]{String.valueOf(shiftId)});}
    public double total(long shiftId,String type){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM movements WHERE shift_id=? AND type=?",new String[]{String.valueOf(shiftId),type})){c.moveToFirst();return c.getDouble(0);}}
    public double sales(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(sales),0) FROM readings WHERE shift_id=?",new String[]{String.valueOf(shiftId)})){c.moveToFirst();return c.getDouble(0);}}
    public double balance(long shiftId){return Calc.balance(sales(shiftId),total(shiftId,"COLLECTION"),total(shiftId,"CASH"),total(shiftId,"DEBT"),total(shiftId,"EXPENSE"));}
    public void submit(long shiftId,int workerId,String reason){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("sales",sales(shiftId));v.put("collections",total(shiftId,"COLLECTION"));v.put("cash_delivered",total(shiftId,"CASH"));v.put("debts",total(shiftId,"DEBT"));v.put("expenses",total(shiftId,"EXPENSE"));v.put("balance",balance(shiftId));v.put("difference_reason",reason);v.put("manager_note","");v.put("status","SUBMITTED");v.put("closed_at",Util.now());v.put("sync_state","PENDING");db.execSQL("UPDATE shifts SET revision=revision+1 WHERE id=?",new Object[]{shiftId});db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,workerId,"SUBMIT","إرسال/تعديل الوردية؛ السبب: "+reason);}
    public Cursor archive(int workerId,boolean admin){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.status,s.sales,s.balance,s.sync_state,COALESCE(s.manager_note,'') FROM shifts s JOIN workers w ON w.id=s.worker_id "+(admin?"":"WHERE s.worker_id=? ")+"ORDER BY s.id DESC",admin?null:new String[]{String.valueOf(workerId)});}
    public Cursor submitted(){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.sales,s.balance,s.difference_reason FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.status='SUBMITTED' ORDER BY s.id",null);}
    public int pendingCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE status='SUBMITTED'",null)){c.moveToFirst();return c.getInt(0);}}
    public int pendingSyncCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE sync_state='PENDING'",null)){c.moveToFirst();return c.getInt(0);}}
    public Cursor pendingSync(){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.closed_at,s.status,s.sales,s.collections,s.cash_delivered,s.debts,s.expenses,s.balance,s.difference_reason,s.revision,COALESCE(s.manager_note,'') FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.sync_state='PENDING' ORDER BY s.id",null);}
    public void markSynced(long shiftId){ContentValues v=new ContentValues();v.put("sync_state","SYNCED");getWritableDatabase().update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});}
    public Cursor syncReadings(long shiftId){return getReadableDatabase().rawQuery("SELECT p.name,p.fuel,r.previous,r.current,r.price,r.sales FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? ORDER BY p.id",new String[]{String.valueOf(shiftId)});}
    public Cursor syncMovements(long shiftId){return getReadableDatabase().rawQuery("SELECT type,name,amount FROM movements WHERE shift_id=? ORDER BY id",new String[]{String.valueOf(shiftId)});}
    public void approve(long shiftId){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.execSQL("UPDATE pumps SET last_reading=(SELECT r.current FROM readings r WHERE r.shift_id=? AND r.pump_id=pumps.id) WHERE id IN (SELECT pump_id FROM readings WHERE shift_id=? AND current IS NOT NULL)",new Object[]{shiftId,shiftId});ContentValues v=new ContentValues();v.put("status","APPROVED");v.put("sync_state","PENDING");db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,1,"APPROVE","اعتماد المدير");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void returnToWorker(long shiftId,String note){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("status","RETURNED");v.put("manager_note",note);v.put("sync_state","PENDING");db.execSQL("UPDATE shifts SET revision=revision+1 WHERE id=?",new Object[]{shiftId});db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,1,"RETURN","إرجاع للعامل: "+note);}
    public String shiftStatus(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT status FROM shifts WHERE id=?",new String[]{String.valueOf(shiftId)})){return c.moveToFirst()?c.getString(0):"OPEN";}}
    public String managerNote(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(manager_note,'') FROM shifts WHERE id=?",new String[]{String.valueOf(shiftId)})){return c.moveToFirst()?c.getString(0):"";}}
    /** سعر اللتر السائد لنوع وقود، أو صفر إن لم يوجد. */
    public double priceFor(String fuel){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(price) FROM pumps WHERE fuel=?",new String[]{fuel.trim()})){return c.moveToFirst()?c.getDouble(0):0;}}
    /** أنواع الوقود المستخدمة فعليًا مع سعر كل نوع وعدد طرمباته. */
    public Cursor fuelPrices(){return getReadableDatabase().rawQuery(
        "SELECT fuel,MIN(price),MAX(price),COUNT(*) FROM pumps WHERE active=1 GROUP BY fuel ORDER BY fuel",null);}
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
    public ArrayList<String> months(){ArrayList<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT substr(opened_at,1,7) FROM shifts ORDER BY 1 DESC",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
    /** تجميع شهري لكل عامل: عدد الورديات والمبيعات والفروقات. */
    public Cursor monthlyByWorker(String month){return getReadableDatabase().rawQuery(
        "SELECT w.name,COUNT(*),COALESCE(SUM(s.sales),0),COALESCE(SUM(s.collections),0),COALESCE(SUM(s.cash_delivered),0),"+
        "COALESCE(SUM(s.debts),0),COALESCE(SUM(s.expenses),0),COALESCE(SUM(s.balance),0),"+
        "SUM(CASE WHEN ABS(s.balance)>=0.01 THEN 1 ELSE 0 END) "+
        "FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE substr(s.opened_at,1,7)=? AND s.status IN ('SUBMITTED','APPROVED','RETURNED') "+
        "GROUP BY w.id,w.name ORDER BY 3 DESC",new String[]{month});}
    public Cursor shiftHeader(long shiftId){return getReadableDatabase().rawQuery("SELECT w.name,s.opened_at,COALESCE(s.closed_at,''),s.status,COALESCE(s.difference_reason,''),COALESCE(s.manager_note,'') FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.id=?",new String[]{String.valueOf(shiftId)});}
    public Cursor exportShifts(int workerId,boolean admin){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,COALESCE(s.closed_at,''),s.status,s.sales,s.collections,s.cash_delivered,s.debts,s.expenses,s.balance,COALESCE(s.difference_reason,''),COALESCE(s.manager_note,''),s.sync_state FROM shifts s JOIN workers w ON w.id=s.worker_id "+(admin?"":"WHERE s.worker_id=? ")+"ORDER BY s.id DESC",admin?null:new String[]{String.valueOf(workerId)});}
    private void audit(SQLiteDatabase db,long shiftId,int workerId,String action,String details){ContentValues v=new ContentValues();v.put("shift_id",shiftId);v.put("worker_id",workerId);v.put("action",action);v.put("details",details);v.put("created_at",Util.now());db.insert("audit_log",null,v);}
}
