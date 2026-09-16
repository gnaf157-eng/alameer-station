package com.alameer.station.shifts;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME = "alameer_station.db";
    private static final int DB_VERSION = 12;
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
        db.execSQL(JOURNAL_SQL);
        db.execSQL(JOURNAL_LINES_SQL);
        db.execSQL(LEDGER_AUDIT_SQL);
        db.execSQL(PERIOD_LOCKS_SQL);
        db.execSQL(DIP_SQL);
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
    /** دفتر القيود: رأس القيد وأطرافه، وسجل التدقيق، وإقفال الفترات. */
    static final String JOURNAL_SQL="CREATE TABLE IF NOT EXISTS journal(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
        "memo TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,source TEXT NOT NULL DEFAULT '',source_id INTEGER NOT NULL DEFAULT 0,"+
        "total REAL NOT NULL DEFAULT 0,posted_at TEXT NOT NULL,actor TEXT NOT NULL DEFAULT '',"+
        "reversed_by INTEGER NOT NULL DEFAULT 0,reverses INTEGER NOT NULL DEFAULT 0,locked INTEGER NOT NULL DEFAULT 1)";
    static final String JOURNAL_LINES_SQL="CREATE TABLE IF NOT EXISTS journal_lines(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
        "entry_id INTEGER NOT NULL,account TEXT NOT NULL,side TEXT NOT NULL,amount REAL NOT NULL,party TEXT NOT NULL DEFAULT '')";
    static final String LEDGER_AUDIT_SQL="CREATE TABLE IF NOT EXISTS ledger_audit(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
        "created_at TEXT NOT NULL,actor TEXT NOT NULL DEFAULT '',action TEXT NOT NULL,entity TEXT NOT NULL DEFAULT '',"+
        "entity_id INTEGER NOT NULL DEFAULT 0,old_value TEXT NOT NULL DEFAULT '',new_value TEXT NOT NULL DEFAULT '',reason TEXT NOT NULL DEFAULT '')";
    static final String PERIOD_LOCKS_SQL="CREATE TABLE IF NOT EXISTS period_locks(period TEXT PRIMARY KEY,"+
        "locked_at TEXT NOT NULL,actor TEXT NOT NULL DEFAULT '',note TEXT NOT NULL DEFAULT '')";

    static final String DIP_SQL="CREATE TABLE IF NOT EXISTS dip_readings(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
        "material TEXT NOT NULL,measured REAL NOT NULL,book REAL NOT NULL,gap REAL NOT NULL,"+
        "entry_date TEXT NOT NULL,created_at TEXT NOT NULL,reason TEXT NOT NULL DEFAULT '',actor TEXT NOT NULL DEFAULT '')";

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<12){
            try{db.execSQL(DIP_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<11){
            try{db.execSQL(JOURNAL_SQL);}catch(Exception ignored){}
            try{db.execSQL(JOURNAL_LINES_SQL);}catch(Exception ignored){}
            try{db.execSQL(LEDGER_AUDIT_SQL);}catch(Exception ignored){}
            try{db.execSQL(PERIOD_LOCKS_SQL);}catch(Exception ignored){}
        }
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
    public Cursor movements(long shiftId){return getReadableDatabase().rawQuery("SELECT id,type,name,amount FROM movements WHERE shift_id=? ORDER BY id ASC",new String[]{String.valueOf(shiftId)});}
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
    /** الوردية غير المطابقة تبقى مُرسلة بانتظار المدير، ولا تُعتمد تلقائيًا. */
    public void closeUnmatched(long shiftId){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            if(!isHistorical(shiftId))db.execSQL("UPDATE pumps SET last_reading=(SELECT r.current FROM readings r WHERE r.shift_id=? AND r.pump_id=pumps.id) WHERE id IN (SELECT pump_id FROM readings WHERE shift_id=? AND current IS NOT NULL)",new Object[]{shiftId,shiftId});
            audit(db,shiftId,1,"CLOSE_UNMATCHED","أُغلقت بفارق وتنتظر مراجعة المدير");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
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
            // المقبوضات باسم مدين معروف تُقيَّد سدادًا له في سجل الديون.
            int payers=0;double paidTotal=0;
            try(Cursor c=db.rawQuery("SELECT name,SUM(amount) FROM movements WHERE shift_id=? AND type='COLLECTION' GROUP BY name",
                    new String[]{String.valueOf(shiftId)})){
                while(c.moveToNext()){
                    String name=c.getString(0).trim();
                    double amount=c.getDouble(1);
                    if(name.isEmpty()||!(amount>0))continue;
                    // الاسم الجديد يُفتح له حساب، فيصير رصيده سالبًا (له لا عليه).
                    long debtorId=findOrCreateDebtor(db,name);
                    addDebtEntry(debtorId,"PAID",amount,"سداد من وردية #"+shiftId,date,shiftId);
                    payers++;paidTotal+=amount;
                }
            }
            if(payers>0)log.append("• سُدّد ").append(Calc.money(paidTotal)).append(" ر.ي من ").append(payers).append(" حساب\n");
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
    // ==================== دفتر القيد المزدوج ====================

    /** المستخدم الحالي، يُكتب في كل قيد وكل سطر تدقيق. */
    private static String actor="النظام";
    public static void signIn(String name){actor=name==null||name.trim().isEmpty()?"النظام":name.trim();}
    public static String actor(){return actor;}

    /** سطر تدقيق كامل: من، وماذا، ومتى، والقيمة قبل وبعد، والسبب. */
    void audit(SQLiteDatabase db,String entity,long entityId,String action,String oldValue,String newValue,String reason){
        ContentValues v=new ContentValues();
        v.put("created_at",Util.now());v.put("actor",actor);v.put("action",action);
        v.put("entity",entity);v.put("entity_id",entityId);
        v.put("old_value",oldValue==null?"":oldValue);
        v.put("new_value",newValue==null?"":newValue);
        v.put("reason",reason==null?"":reason);
        db.insert("ledger_audit",null,v);
    }
    public void audit(String entity,long entityId,String action,String oldValue,String newValue,String reason){
        audit(getWritableDatabase(),entity,entityId,action,oldValue,newValue,reason);
    }
    /** 0=id,1=وقت,2=من,3=عملية,4=السجل,5=قبل,6=بعد,7=سبب */
    public Cursor auditLog(int limit){
        return getReadableDatabase().rawQuery(
            "SELECT id,created_at,COALESCE(NULLIF(actor,''),'النظام'),action,entity||' #'||entity_id,"+
            "old_value,new_value,reason FROM ledger_audit ORDER BY id DESC LIMIT ?",
            new String[]{String.valueOf(limit)});
    }

    public boolean periodLocked(String date){
        String period=Journal.periodOf(date);
        if(period.isEmpty())return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM period_locks WHERE period=?",new String[]{period})){
            return c.moveToFirst();
        }
    }
    /** يقفل فترة: لا قيد جديد فيها بعد الإقفال. */
    public void lockPeriod(String period,String note){
        if(period==null||period.trim().length()<7)throw new IllegalArgumentException("اكتب الفترة بصيغة 2026-09");
        period=period.trim();
        // لا تُقفل فترة فيها ورديات مُغلقة لم تُرحّل بعد، وإلا تعذّر ترحيلها.
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM shifts s WHERE s.status<>'OPEN' "+
                "AND substr(COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),1,7)=? "+
                "AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id)",
                new String[]{period})){
            if(c.moveToFirst()&&c.getInt(0)>0)
                throw new IllegalStateException("لا يمكن إقفال "+period+": فيها "+c.getInt(0)+" وردية لم تُرحّل بعد. رحّلها أولًا.");
        }
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ContentValues v=new ContentValues();
            v.put("period",period);v.put("locked_at",Util.now());v.put("actor",actor);v.put("note",note==null?"":note);
            db.insertWithOnConflict("period_locks",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            audit(db,"period",0,"LOCK_PERIOD","مفتوحة","مقفلة "+period,note);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void unlockPeriod(String period,String reason){
        if(reason==null||reason.trim().isEmpty())throw new IllegalArgumentException("اكتب سبب فتح الفترة");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            db.delete("period_locks","period=?",new String[]{period});
            audit(db,"period",0,"UNLOCK_PERIOD","مقفلة "+period,"مفتوحة",reason.trim());
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** 0=period,1=وقت الإقفال,2=من,3=ملاحظة */
    public Cursor periodLocks(){
        return getReadableDatabase().rawQuery("SELECT period,locked_at,actor,note FROM period_locks ORDER BY period DESC",null);
    }

    /**
     * يحفظ قيدًا مزدوجًا داخل معاملة واحدة.
     * يرفض القيد غير المتوازن أو الناقص أو الواقع في فترة مقفلة، ولا يكتب شيئًا عند الرفض.
     */
    public long postEntry(Journal.Entry entry){
        String reject=Journal.rejectReason(entry);
        if(!reject.isEmpty())throw new IllegalStateException(reject);
        if(periodLocked(entry.date))
            throw new IllegalStateException("الفترة "+Journal.periodOf(entry.date)+" مقفلة. افتحها بصلاحية المدير أولًا.");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ContentValues head=new ContentValues();
            head.put("memo",entry.memo);head.put("entry_date",entry.date);
            head.put("source",entry.source);head.put("source_id",entry.sourceId);
            head.put("total",entry.totalDebit());head.put("posted_at",Util.now());head.put("actor",actor);
            long id=db.insertOrThrow("journal",null,head);
            for(Journal.Line l:entry.lines){
                ContentValues v=new ContentValues();
                v.put("entry_id",id);v.put("account",l.account);v.put("side",l.side);
                v.put("amount",l.amount);v.put("party",l.party);
                db.insertOrThrow("journal_lines",null,v);
            }
            // تحقّق أخير من داخل قاعدة البيانات نفسها قبل الإقرار.
            double d=0,cr=0;
            try(Cursor c=db.rawQuery("SELECT side,COALESCE(SUM(amount),0) FROM journal_lines WHERE entry_id=? GROUP BY side",
                    new String[]{String.valueOf(id)})){
                while(c.moveToNext()){
                    if(Journal.DEBIT.equals(c.getString(0)))d=c.getDouble(1);else cr=c.getDouble(1);
                }
            }
            if(!Journal.balanced(d-cr))
                throw new IllegalStateException("رُفض القيد: مدين "+Calc.money(d)+" لا يساوي دائن "+Calc.money(cr));
            audit(db,"journal",id,"POST_ENTRY","",entry.memo+" — "+Calc.money(entry.totalDebit())+" ر.ي","");
            db.setTransactionSuccessful();
            return id;
        }finally{db.endTransaction();}
    }

    /**
     * التصحيح الوحيد المسموح: قيد عكسي. لا تعديل ولا حذف لقيد معتمد.
     */
    public long reverseEntry(long entryId,String reason){
        if(reason==null||reason.trim().isEmpty())throw new IllegalArgumentException("اكتب سبب التصحيح");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            String memo="",date="",source="";long sourceId=0;int reversedBy=0;
            try(Cursor c=db.rawQuery("SELECT memo,entry_date,source,source_id,reversed_by FROM journal WHERE id=?",
                    new String[]{String.valueOf(entryId)})){
                if(!c.moveToFirst())throw new IllegalStateException("القيد غير موجود");
                memo=c.getString(0);date=c.getString(1);source=c.getString(2);sourceId=c.getLong(3);reversedBy=c.getInt(4);
            }
            if(reversedBy>0)throw new IllegalStateException("سبق عكس هذا القيد بالقيد #"+reversedBy);
            if(periodLocked(date))throw new IllegalStateException("فترة القيد مقفلة. افتحها بصلاحية المدير أولًا.");

            Journal.Entry rev=new Journal.Entry("عكس القيد #"+entryId+" ("+memo+") — "+reason.trim(),date,source,sourceId);
            try(Cursor c=db.rawQuery("SELECT account,side,amount,party FROM journal_lines WHERE entry_id=? ORDER BY id",
                    new String[]{String.valueOf(entryId)})){
                while(c.moveToNext()){
                    if(Journal.DEBIT.equals(c.getString(1)))rev.credit(c.getString(0),c.getDouble(2),c.getString(3));
                    else rev.debit(c.getString(0),c.getDouble(2),c.getString(3));
                }
            }
            String reject=Journal.rejectReason(rev);
            if(!reject.isEmpty())throw new IllegalStateException(reject);

            ContentValues head=new ContentValues();
            head.put("memo",rev.memo);head.put("entry_date",rev.date);
            head.put("source",rev.source);head.put("source_id",rev.sourceId);
            head.put("total",rev.totalDebit());head.put("posted_at",Util.now());head.put("actor",actor);
            head.put("reverses",entryId);
            long newId=db.insertOrThrow("journal",null,head);
            for(Journal.Line l:rev.lines){
                ContentValues v=new ContentValues();
                v.put("entry_id",newId);v.put("account",l.account);v.put("side",l.side);
                v.put("amount",l.amount);v.put("party",l.party);
                db.insertOrThrow("journal_lines",null,v);
            }
            ContentValues mark=new ContentValues();
            mark.put("reversed_by",newId);
            db.update("journal",mark,"id=?",new String[]{String.valueOf(entryId)});
            audit(db,"journal",entryId,"REVERSE_ENTRY",memo,"مُلغى بالقيد العكسي #"+newId,reason.trim());
            db.setTransactionSuccessful();
            return newId;
        }finally{db.endTransaction();}
    }

    /** مجموع طرف واحد في الدفتر كله (القيود العكسية داخلة لأنها تلغي أثر أصلها). */
    public double journalSide(String side){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM journal_lines WHERE side=?",new String[]{side})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    public double journalDebit(){return journalSide(Journal.DEBIT);}
    public double journalCredit(){return journalSide(Journal.CREDIT);}

    /** القيود غير المتوازنة إن وُجدت: 0=id,1=بيان,2=تاريخ,3=مدين,4=دائن. */
    public Cursor unbalancedEntries(){
        return getReadableDatabase().rawQuery(
            "SELECT j.id,j.memo,j.entry_date,"+
            "COALESCE(SUM(CASE WHEN l.side='DEBIT' THEN l.amount ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN l.side='CREDIT' THEN l.amount ELSE 0 END),0) "+
            "FROM journal j LEFT JOIN journal_lines l ON l.entry_id=j.id GROUP BY j.id "+
            "HAVING ABS(COALESCE(SUM(CASE WHEN l.side='DEBIT' THEN l.amount ELSE 0 END),0)"+
            "-COALESCE(SUM(CASE WHEN l.side='CREDIT' THEN l.amount ELSE 0 END),0))>=0.005 ORDER BY j.id DESC",null);
    }

    /** ميزان المراجعة: 0=حساب,1=مدين,2=دائن,3=الرصيد. */
    public Cursor trialBalance(){
        return getReadableDatabase().rawQuery(
            "SELECT account,"+
            "COALESCE(SUM(CASE WHEN side='DEBIT' THEN amount ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN side='CREDIT' THEN amount ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END),0) "+
            "FROM journal_lines GROUP BY account ORDER BY ABS(SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END)) DESC",null);
    }

    /** الورديات المُغلقة التي لم يُسجَّل لها قيد بعد: 0=id,1=عامل,2=تاريخ,3=الفرق. */
    public Cursor unpostedShifts(){
        return getReadableDatabase().rawQuery(
            "SELECT s.id,w.name,COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),s.balance "+
            "FROM shifts s JOIN workers w ON w.id=s.worker_id "+
            "WHERE s.status<>'OPEN' AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id) "+
            "ORDER BY s.id DESC",null);
    }

    /** هل للوردية قيد مسجّل؟ */
    public boolean shiftJournalled(long shiftId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM journal WHERE source='SHIFT' AND source_id=?",
                new String[]{String.valueOf(shiftId)})){
            return c.moveToFirst();
        }
    }

    /** يسجّل قيد الوردية المُغلقة. يرفض الترحيل إذا كان هناك فرق غير مسوّى. */
    public long journalShift(long shiftId){
        if(shiftJournalled(shiftId))return 0;
        String date="",reason="";double sales=0,collections=0,cash=0,debts=0,expenses=0,balance=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(NULLIF(shift_date,''),substr(opened_at,1,10)),sales,collections,cash_delivered,"+
                "debts,expenses,balance,COALESCE(difference_reason,'') FROM shifts WHERE id=?",
                new String[]{String.valueOf(shiftId)})){
            if(!c.moveToFirst())throw new IllegalStateException("الوردية غير موجودة");
            date=c.getString(0);sales=c.getDouble(1);collections=c.getDouble(2);cash=c.getDouble(3);
            debts=c.getDouble(4);expenses=c.getDouble(5);balance=c.getDouble(6);reason=c.getString(7);
        }
        // فرق بلا تسوية أو تعليل يمنع الترحيل.
        if(Math.abs(balance)>=0.01&&reason.trim().isEmpty())
            throw new IllegalStateException("لا يمكن ترحيل وردية بفرق "+Calc.money(Math.abs(balance))+" ر.ي بلا سبب مكتوب.");
        Journal.Entry e=Journal.shiftEntry(shiftId,date,sales,collections,cash,debts,expenses,balance);
        return postEntry(e);
    }

    /** يسجّل سبب فرق وردية مُغلقة حتى يجوز ترحيلها، ويحفظ الأثر في سجل التدقيق. */
    public void settleShift(long shiftId,String reason){
        if(reason==null||reason.trim().isEmpty())throw new IllegalArgumentException("اكتب سبب الفرق");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            String before="";
            try(Cursor c=db.rawQuery("SELECT COALESCE(difference_reason,'') FROM shifts WHERE id=?",
                    new String[]{String.valueOf(shiftId)})){
                if(!c.moveToFirst())throw new IllegalStateException("الوردية غير موجودة");
                before=c.getString(0);
            }
            ContentValues v=new ContentValues();
            v.put("difference_reason",reason.trim());
            db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});
            audit(db,"shift",shiftId,"SETTLE_DIFFERENCE",before.isEmpty()?"بلا سبب":before,reason.trim(),reason.trim());
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** الورديات المُغلقة بفرق بلا سبب: 0=id,1=عامل,2=تاريخ,3=الفرق. */
    public Cursor unexplainedShifts(){
        return getReadableDatabase().rawQuery(
            "SELECT s.id,w.name,COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),s.balance "+
            "FROM shifts s JOIN workers w ON w.id=s.worker_id "+
            "WHERE s.status<>'OPEN' AND ABS(s.balance)>=0.01 AND TRIM(COALESCE(s.difference_reason,''))='' "+
            "AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id) "+
            "ORDER BY s.id",null);
    }

    /** أول فترة مقفلة تمنع ترحيل وردية مُغلقة، أو نص فارغ إذا لا مانع. */
    public String blockingPeriod(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT DISTINCT p.period FROM period_locks p JOIN shifts s "+
                "ON p.period=substr(COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),1,7) "+
                "WHERE s.status<>'OPEN' AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id) "+
                "ORDER BY p.period LIMIT 1",null)){
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /**
     * يرحّل كل وردية مُغلقة بلا قيد دفعةً واحدة.
     * يعيد سطرًا يلخّص كم نجح وكم رُفض ولماذا. كل وردية في معاملتها المستقلة،
     * فالوردية المرفوضة لا تمنع ترحيل البقية.
     */
    public String journalBacklog(){
        java.util.List<Long> ids=new java.util.ArrayList<>();
        try(Cursor c=unpostedShifts()){
            while(c.moveToNext())ids.add(c.getLong(0));
        }
        if(ids.isEmpty())return "لا توجد ورديات بانتظار الترحيل.";
        int done=0;
        StringBuilder failed=new StringBuilder();
        // الأقدم أولًا حتى تتسلسل القيود بترتيبها الزمني.
        java.util.Collections.sort(ids);
        for(long id:ids){
            try{ if(journalShift(id)>0)done++; }
            catch(Exception e){ failed.append("\n• وردية #").append(id).append(": ").append(e.getMessage()); }
        }
        StringBuilder out=new StringBuilder();
        out.append("رُحّلت ").append(done).append(" وردية إلى الدفتر.");
        if(failed.length()>0)out.append("\n\nلم تُرحّل:").append(failed);
        return out.toString();
    }

    /**
     * يفتح وردية مُغلقة للتعديل: يعكس قيدها ويلغي ترحيلها
     * حتى تُراجَع في نفس خانات الطرمبات والحركات ثم تُعتمد من جديد.
     */
    public void reopenShift(long shiftId,String reason){
        String why=reason==null||reason.trim().isEmpty()?"فتح الوردية للتعديل":reason.trim();
        String before="";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT status FROM shifts WHERE id=?",
                new String[]{String.valueOf(shiftId)})){
            if(!c.moveToFirst())throw new IllegalStateException("الوردية غير موجودة");
            before=c.getString(0);
        }
        if("OPEN".equals(before))return;

        // القيود تُعكس أولًا خارج المعاملة، فلكل عكس معاملته المستقلة.
        java.util.List<Long> entries=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id FROM journal WHERE source='SHIFT' AND source_id=? AND reversed_by=0",
                new String[]{String.valueOf(shiftId)})){
            while(c.moveToNext())entries.add(c.getLong(0));
        }
        for(long entry:entries)reverseEntry(entry,why);

        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            db.delete("cashbox_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
            db.delete("debt_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
            db.delete("expense_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
            db.delete("material_entries","note LIKE ?",new String[]{"وردية #"+shiftId+"%"});
            ContentValues v=new ContentValues();
            v.put("status","OPEN");v.put("closed_at","");v.put("sync_state","PENDING");
            db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});
            audit(db,"shift",shiftId,"REOPEN_SHIFT",before,"OPEN",why);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** هل الوردية مفتوحة للتعديل الآن؟ */
    public boolean isOpen(long shiftId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT status FROM shifts WHERE id=?",
                new String[]{String.valueOf(shiftId)})){
            return c.moveToFirst()&&("OPEN".equals(c.getString(0))||"RETURNED".equals(c.getString(0)));
        }
    }

    // ==================== كلمات السر والدخول ====================

    /** الرمز الافتراضي للمدير والعامل قبل أي تغيير. */
    public static final String DEFAULT_MANAGER_PIN = "2216";
    public static final String DEFAULT_WORKER_PIN = "6114";

    /** الجلسة الحالية في الذاكرة فقط: تُنسى بإغلاق التطبيق فتُطلب كلمة السر من جديد. */
    private static String session = "";
    public static boolean signedIn(){ return !session.isEmpty(); }
    public static boolean workerDevice(){ return "WORKER".equals(session); }
    public static boolean managerMode(){ return "MANAGER".equals(session); }
    public static void endSession(){ session = ""; }

    private String managerHash(){ return setting("pw_manager", Calc.hash(DEFAULT_MANAGER_PIN)); }
    private String workerHash(){ return setting("pw_worker", Calc.hash(DEFAULT_WORKER_PIN)); }

    /**
     * يتحقّق من كلمة السر ويفتح الجلسة بالدور الذي تخصّها.
     * يعيد MANAGER أو WORKER، أو نصًا فارغًا إذا لم تطابق شيئًا.
     */
    public String openSession(String password){
        String hash = Calc.hash(password == null ? "" : password.trim());
        String role = hash.equals(managerHash()) ? "MANAGER"
                    : hash.equals(workerHash()) ? "WORKER" : "";
        if(!role.isEmpty()){
            session = role;
            audit("device", 0, "LOGIN", "", role.equals("MANAGER") ? "دخول المدير" : "دخول العامل", "");
        }
        return role;
    }

    /** يغيّر كلمة سر أحد الدورين. متاح للمدير وحده من الضبط. */
    public void setPin(String role, String fresh){
        if(!managerMode())throw new IllegalStateException("تغيير كلمات السر للمدير وحده");
        String clean = fresh == null ? "" : fresh.trim();
        if(clean.length() < 4)throw new IllegalArgumentException("كلمة السر: 4 أرقام أو أحرف على الأقل");
        boolean manager = "MANAGER".equals(role);
        String other = manager ? workerHash() : managerHash();
        if(Calc.hash(clean).equals(other))
            throw new IllegalArgumentException("لا يمكن أن تتطابق كلمتا السر");
        setSetting(manager ? "pw_manager" : "pw_worker", Calc.hash(clean));
        audit("device", 0, "CHANGE_PIN", manager ? "المدير" : "العامل", "غُيّرت كلمة السر", "");
    }

    /** هل ما زالت كلمة السر هي الافتراضية؟ يُنبَّه المدير لتغييرها. */
    public boolean defaultPin(String role){
        return "MANAGER".equals(role)
            ? managerHash().equals(Calc.hash(DEFAULT_MANAGER_PIN))
            : workerHash().equals(Calc.hash(DEFAULT_WORKER_PIN));
    }

    // ==================== رمز الربط بين الجهازين ====================

    /** رمز الربط المحفوظ، أو نص فارغ إن لم يُضبط بعد. */
    public String linkCode(){ return setting("link_code",""); }

    /** يحفظ رمز الربط بعد توحيد شكله، ويُكتب في سجل التدقيق. */
    public void setLinkCode(String code){
        String clean=Link.normalize(code);
        if(clean.length()!=12)throw new IllegalArgumentException("رمز الربط يجب أن يكون 12 حرفًا");
        String before=linkCode();
        setSetting("link_code",clean);
        setSetting("link_since","all");
        audit("link",0,"SET_LINK",before.isEmpty()?"غير مربوط":"مربوط سابقًا",Link.pretty(clean),"ربط الجهازين");
    }

    /** ينشئ رمز ربط جديدًا لجهاز المدير. */
    public String createLinkCode(){
        String code=Link.generate(new java.util.Random());
        setLinkCode(code);
        return code;
    }

    /** عدد ورديات العامل المرسلة وما زالت تنتظر اعتماد المدير. */
    public int awaitingManager(int workerId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM shifts WHERE worker_id=? AND status='SUBMITTED' AND sync_state='SYNCED'",
                new String[]{String.valueOf(workerId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /** يعلّم الوردية بأنها أُرسلت إلى المدير. */
    public void markSent(long shiftId){
        ContentValues v=new ContentValues();
        v.put("sync_state","SYNCED");
        getWritableDatabase().update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});
        audit("shift",shiftId,"SEND_SHIFT","","أُرسلت إلى المدير","");
    }

    /** معرّف ثابت لهذا الجهاز، يميّز ورديات كل جهاز عن غيره. */
    public String deviceId(){
        String id=setting("device_id","");
        if(id.isEmpty()){
            id=Long.toHexString(System.currentTimeMillis());
            setSetting("device_id",id);
        }
        return id;
    }

    /**
     * يستورد وردية قادمة من جهاز العامل عبر المزامنة.
     * تُحفظ مُغلقة بانتظار مراجعة المدير، ولا تُرحّل ولا تُقيَّد.
     * يعيد المعرّف المحلي، أو صفرًا إذا كانت مستوردة من قبل.
     */
    public long importRemoteShift(org.json.JSONObject j){
        String device=j.optString("device","");
        long remote=j.optLong("shiftId",0);
        String date=j.optString("shiftDate",j.optString("openedAt","")).trim();
        if(date.length()>10)date=date.substring(0,10);
        String key="remote_"+device+"_"+remote+"_"+date;
        if(!setting(key,"").isEmpty())return 0;

        org.json.JSONArray readings=j.optJSONArray("readings");
        if(readings==null||readings.length()==0)
            throw new IllegalStateException("وردية #"+remote+" بلا قراءات");

        // شرط التسلسل: القراءة السابقة في الوردية الواردة تطابق عدّادك.
        StringBuilder bad=new StringBuilder();
        for(int i=0;i<readings.length();i++){
            org.json.JSONObject r=readings.optJSONObject(i);
            if(r==null)continue;
            String pump=r.optString("pump","");
            double previous=r.optDouble("previous",0);
            double mine=-1;
            try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT last_reading FROM pumps WHERE name=? AND active=1",new String[]{pump})){
                if(c.moveToFirst())mine=c.getDouble(0);
            }
            if(mine<0){bad.append(" ").append(pump).append(" غير موجودة؛");continue;}
            if(Math.abs(mine-previous)>=0.01)
                bad.append(" ").append(pump).append(": عندك ").append(Calc.money(mine))
                   .append(" والوارد ").append(Calc.money(previous)).append("؛");
        }
        if(bad.length()>0)
            throw new IllegalStateException("وردية #"+remote+" قراءاتها لا تتسلسل مع عدّاداتك:"+bad);

        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            String worker=j.optString("worker","عامل");
            int workerId=workerIdByName(db,worker);
            ContentValues head=new ContentValues();
            head.put("worker_id",workerId);head.put("opened_at",Util.now());
            head.put("shift_date",date);head.put("status","SUBMITTED");
            head.put("closed_at",Util.now());head.put("sync_state","SYNCED");
            head.put("difference_reason",j.optString("differenceReason",""));
            head.put("manager_note","واردة من جهاز العامل");
            long id=db.insertOrThrow("shifts",null,head);

            double sales=0;
            for(int i=0;i<readings.length();i++){
                org.json.JSONObject r=readings.optJSONObject(i);
                if(r==null)continue;
                long pumpId=0;
                try(Cursor c=db.rawQuery("SELECT id FROM pumps WHERE name=? AND active=1",
                        new String[]{r.optString("pump","")})){
                    if(c.moveToFirst())pumpId=c.getLong(0);
                }
                if(pumpId==0)continue;
                double previous=r.optDouble("previous",0),current=r.optDouble("current",0),price=r.optDouble("price",0);
                double amount=Math.max(0,current-previous)*Math.max(0,price);
                sales+=amount;
                ContentValues v=new ContentValues();
                v.put("shift_id",id);v.put("pump_id",pumpId);
                v.put("previous",previous);v.put("current",current);
                v.put("price",price);v.put("sales",amount);
                db.insertOrThrow("readings",null,v);
            }

            double[] totals=new double[4];
            String[] types={"COLLECTION","CASH","DEBT","EXPENSE"};
            org.json.JSONArray moves=j.optJSONArray("movements");
            if(moves!=null)for(int i=0;i<moves.length();i++){
                org.json.JSONObject m=moves.optJSONObject(i);
                if(m==null)continue;
                String type=m.optString("type","");
                double amount=m.optDouble("amount",0);
                for(int t=0;t<types.length;t++)if(types[t].equals(type))totals[t]+=amount;
                ContentValues v=new ContentValues();
                v.put("shift_id",id);v.put("type",type);v.put("name",m.optString("name",""));
                v.put("amount",amount);v.put("created_at",Util.now());
                db.insertOrThrow("movements",null,v);
            }

            ContentValues sums=new ContentValues();
            sums.put("sales",sales);sums.put("collections",totals[0]);sums.put("cash_delivered",totals[1]);
            sums.put("debts",totals[2]);sums.put("expenses",totals[3]);
            sums.put("balance",Calc.balance(sales,totals[0],totals[1],totals[2],totals[3]));
            db.update("shifts",sums,"id=?",new String[]{String.valueOf(id)});

            ContentValues mark=new ContentValues();
            mark.put("key",key);mark.put("value",String.valueOf(id));
            db.insertWithOnConflict("settings",null,mark,SQLiteDatabase.CONFLICT_REPLACE);
            audit(db,"shift",id,"RECEIVE_SHIFT","","وردية "+worker+" ("+date+") مبيعات "+Calc.money(sales),
                  "واردة من جهاز العامل");
            db.setTransactionSuccessful();
            return id;
        }finally{db.endTransaction();}
    }

    // ==================== تسليم الوردية بين الجهازين ====================

    /** يجمع الوردية في صيغة ملف التسليم. */
    public ShiftFile.Shift exportShift(long shiftId){
        ShiftFile.Shift out=new ShiftFile.Shift();
        out.station=Branding.stationName(this);
        out.number=shiftId;
        out.device=setting("device_id","");
        if(out.device.isEmpty()){
            out.device=Long.toHexString(System.currentTimeMillis());
            setSetting("device_id",out.device);
        }
        try(Cursor c=shiftHeader(shiftId)){
            if(!c.moveToFirst())throw new IllegalStateException("الوردية غير موجودة");
            out.worker=c.getString(0);out.reason=c.getString(4);out.date=c.getString(6);
        }
        try(Cursor c=shiftReadings(shiftId)){
            while(c.moveToNext()){
                if(c.isNull(4))continue;
                out.readings.add(new ShiftFile.Reading(c.getString(1),c.getString(2),
                        c.getDouble(3),c.getDouble(4),c.getDouble(5)));
            }
        }
        try(Cursor c=movements(shiftId)){
            while(c.moveToNext())out.moves.add(new ShiftFile.Move(c.getString(1),c.getString(2),c.getDouble(3)));
        }
        return out;
    }

    /** هل سبق استيراد هذا الملف؟ يمنع التكرار. */
    public boolean alreadyImported(ShiftFile.Shift s){
        String key="import_"+s.device+"_"+s.number+"_"+s.date;
        return !setting(key,"").isEmpty();
    }
    private void markImported(SQLiteDatabase db,ShiftFile.Shift s,long localId){
        ContentValues v=new ContentValues();
        v.put("key","import_"+s.device+"_"+s.number+"_"+s.date);
        v.put("value",String.valueOf(localId));
        db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * يقارن القراءات السابقة في الملف بعدّادات هذا الجهاز.
     * يعيد نصًا فارغًا عند التطابق، وإلا جدولًا بالفروقات.
     */
    public String readingMismatch(ShiftFile.Shift s){
        StringBuilder bad=new StringBuilder();
        for(ShiftFile.Reading r:s.readings){
            double mine=-1;
            try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT last_reading FROM pumps WHERE name=? AND active=1",new String[]{r.pump})){
                if(c.moveToFirst())mine=c.getDouble(0);
            }
            if(mine<0){bad.append("\n• ").append(r.pump).append(": غير موجودة عندك");continue;}
            if(Math.abs(mine-r.previous)>=0.01)
                bad.append("\n• ").append(r.pump).append(": عندك ").append(Calc.money(mine))
                   .append(" وفي الملف ").append(Calc.money(r.previous))
                   .append(" (فرق ").append(Calc.money(Math.abs(mine-r.previous))).append(")");
        }
        return bad.toString();
    }

    /**
     * يستورد وردية العامل كوردية مُغلقة بانتظار مراجعة المدير.
     * لا تُقيَّد في الدفتر ولا تُرحّل حتى يعتمدها المدير.
     */
    public long importShift(ShiftFile.Shift s){
        String mismatch=readingMismatch(s);
        if(!mismatch.isEmpty())
            throw new IllegalStateException("القراءات السابقة لا تطابق عدّاداتك:"+mismatch);
        if(alreadyImported(s))
            throw new IllegalStateException("سبق استيراد هذه الوردية.");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            int worker=workerIdByName(db,s.worker);
            ContentValues head=new ContentValues();
            head.put("worker_id",worker);head.put("opened_at",Util.now());
            head.put("shift_date",s.date);head.put("status","SUBMITTED");
            head.put("closed_at",Util.now());head.put("sync_state","PENDING");
            head.put("difference_reason",s.reason);
            head.put("manager_note","واردة من جهاز العامل");
            long id=db.insertOrThrow("shifts",null,head);
            for(ShiftFile.Reading r:s.readings){
                long pumpId=0;
                try(Cursor c=db.rawQuery("SELECT id FROM pumps WHERE name=? AND active=1",new String[]{r.pump})){
                    if(c.moveToFirst())pumpId=c.getLong(0);
                }
                if(pumpId==0)continue;
                ContentValues v=new ContentValues();
                v.put("shift_id",id);v.put("pump_id",pumpId);
                v.put("previous",r.previous);v.put("current",r.current);
                v.put("price",r.price);v.put("sales",r.amount());
                db.insertOrThrow("readings",null,v);
            }
            for(ShiftFile.Move m:s.moves){
                ContentValues v=new ContentValues();
                v.put("shift_id",id);v.put("type",m.type);v.put("name",m.name);
                v.put("amount",m.amount);v.put("created_at",Util.now());
                db.insertOrThrow("movements",null,v);
            }
            ContentValues sums=new ContentValues();
            sums.put("sales",s.sales());
            sums.put("collections",s.total("COLLECTION"));
            sums.put("cash_delivered",s.total("CASH"));
            sums.put("debts",s.total("DEBT"));
            sums.put("expenses",s.total("EXPENSE"));
            sums.put("balance",s.balance());
            db.update("shifts",sums,"id=?",new String[]{String.valueOf(id)});
            markImported(db,s,id);
            audit(db,"shift",id,"IMPORT_SHIFT","",
                  "وردية العامل "+s.worker+" ("+s.date+") مبيعات "+Calc.money(s.sales()),"استيراد من جهاز العامل");
            db.setTransactionSuccessful();
            return id;
        }finally{db.endTransaction();}
    }

    private int workerIdByName(SQLiteDatabase db,String name){
        String clean=name==null?"":name.trim();
        if(!clean.isEmpty()){
            try(Cursor c=db.rawQuery("SELECT id FROM workers WHERE name=?",new String[]{clean})){
                if(c.moveToFirst())return c.getInt(0);
            }
            ContentValues v=new ContentValues();
            v.put("name",clean);v.put("pin_hash",Calc.hash("worker-"+clean));
            v.put("role","WORKER");v.put("shift_kind","DAY");
            return (int)db.insertOrThrow("workers",null,v);
        }
        return 1;
    }

    /** الورديات الواردة من العامل بانتظار الاعتماد: 0=id,1=عامل,2=تاريخ,3=مبيعات,4=الباقي. */
    /** 0=id,1=عامل,2=تاريخ,3=مبيعات,4=الباقي,5=عدد الطرمبات */
    public Cursor incomingShifts(){
        return getReadableDatabase().rawQuery(
            "SELECT s.id,w.name,COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),s.sales,s.balance,"+
            "(SELECT COUNT(*) FROM readings r WHERE r.shift_id=s.id AND r.current IS NOT NULL) "+
            "FROM shifts s JOIN workers w ON w.id=s.worker_id "+
            "WHERE s.status='SUBMITTED' ORDER BY s.id DESC",null);
    }

    /**
     * اعتماد المدير للوردية الواردة: ترحيلها وتقييدها في الدفتر.
     * الفرق يُقيَّد على عهدة العامل، فلا يضيع ولا يُعاد إدخاله.
     */
    public String approveIncoming(long shiftId,long cashboxId){
        approve(shiftId);
        String posted=postShift(shiftId,cashboxId);
        try{ journalShift(shiftId); }catch(Exception ignored){}
        return posted;
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
    /** الديون المستحقة فقط؛ الأرصدة الدائنة لا تُطرح منها. */
    public double debtsTotal(){
        double total=0;
        try(Cursor c=debtors(true)){while(c.moveToNext()){double b=c.getDouble(7);if(b>0.009)total+=b;}}
        return total;
    }
    /** مجموع ما للزبائن علينا (الأرصدة السالبة) كقيمة موجبة. */
    public double creditsTotal(){
        double total=0;
        try(Cursor c=debtors(true)){while(c.moveToNext()){double b=c.getDouble(7);if(b<-0.009)total-=b;}}
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
    /** حدود التنبيه القابلة للضبط من الإعدادات. */
    public double lowCash(){try{return Double.parseDouble(setting("threshold_low_cash","50000"));}catch(Exception e){return 50000;}}
    public void setLowCash(double v){setSetting("threshold_low_cash",String.valueOf(v));}
    public double bigDebt(){try{return Double.parseDouble(setting("threshold_big_debt","100000"));}catch(Exception e){return 100000;}}
    public void setBigDebt(double v){setSetting("threshold_big_debt",String.valueOf(v));}
    public int staleDays(){try{return Integer.parseInt(setting("threshold_stale_days","21"));}catch(Exception e){return 21;}}
    public void setStaleDays(int v){setSetting("threshold_stale_days",String.valueOf(v));}
    public int lowStockPercent(){try{return Integer.parseInt(setting("threshold_low_stock","25"));}catch(Exception e){return 25;}}
    public void setLowStockPercent(int v){setSetting("threshold_low_stock",String.valueOf(v));}

    /** سعة خزان مادة باللترات، لرسم شريط الامتلاء في لوحة التحكم. */
    public double capacity(String material){
        try{return Double.parseDouble(setting("capacity_"+material,defaultCapacity(material)));}
        catch(NumberFormatException e){return Double.parseDouble(defaultCapacity(material));}
    }
    public void setCapacity(String material,double litres){
        setSetting("capacity_"+material,String.valueOf(litres));
    }
    private static String defaultCapacity(String material){
        return "غاز".equals(material)?"10000":"40500";
    }
    /** عدد الورديات المفتوحة الآن. */
    public int openShifts(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE status='OPEN'",null)){
            c.moveToFirst();return c.getInt(0);
        }
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
    // ==================== مطابقة العجز بالمقياس ====================

    /** يسجّل قياس خزان يدويًا ويصحّح المخزون بحركة فرق، كل ذلك في معاملة واحدة. */
    public long recordDip(String material,double measured,String reason,String date){
        if(!Double.isFinite(measured)||measured<0)throw new IllegalArgumentException("اكتب القياس باللترات");
        double book=materialSummary(material)[3];
        double gap=Dip.gap(book,measured);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            long id=0;
            if(Math.abs(gap)>=0.001){
                ContentValues v=new ContentValues();
                v.put("material",material);
                v.put("direction",Dip.correctionDirection(gap));
                v.put("litres",Math.abs(gap));
                v.put("note",Dip.note(gap,reason));
                v.put("entry_date",date);v.put("created_at",Util.now());
                id=db.insertOrThrow("material_entries",null,v);
            }
            ContentValues log=new ContentValues();
            log.put("material",material);log.put("measured",measured);log.put("book",book);
            log.put("gap",gap);log.put("entry_date",date);log.put("created_at",Util.now());
            log.put("reason",reason==null?"":reason.trim());log.put("actor",actor);
            db.insertOrThrow("dip_readings",null,log);
            audit(db,"dip",id,"DIP_RECONCILE",Calc.money(book)+" لتر دفتري",
                  Calc.money(measured)+" لتر مقاس ("+Dip.direction(gap)+" "+Calc.money(Math.abs(gap))+")",
                  reason==null?"":reason.trim());
            db.setTransactionSuccessful();
            return id;
        }finally{db.endTransaction();}
    }

    /** سجل القياسات: 0=id,1=مادة,2=مقاس,3=دفتري,4=فرق,5=تاريخ,6=سبب,7=من */
    public Cursor dipReadings(String material,int limit){
        boolean all=material==null||material.isEmpty();
        return getReadableDatabase().rawQuery(
            "SELECT id,material,measured,book,gap,entry_date,reason,actor FROM dip_readings "+
            (all?"":"WHERE material=? ")+"ORDER BY entry_date DESC,id DESC LIMIT "+Math.max(1,limit),
            all?null:new String[]{material});
    }

    /** آخر قياس لمادة، أو نص فارغ. */
    public String lastDip(String material){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT entry_date,gap FROM dip_readings WHERE material=? ORDER BY entry_date DESC,id DESC LIMIT 1",
                new String[]{material})){
            if(!c.moveToFirst())return "";
            return c.getString(0)+"  •  "+Dip.direction(c.getDouble(1))+" "+Calc.money(Math.abs(c.getDouble(1)))+" لتر";
        }
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
