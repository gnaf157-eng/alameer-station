package com.alameer.station.shifts;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME = "alameer_station.db";
    private static final int DB_VERSION = 26;
    public Db(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    static final String SETTLEMENT_SQL="CREATE TABLE IF NOT EXISTS settlement_links(entry_id INTEGER PRIMARY KEY,debt_entry INTEGER NOT NULL DEFAULT 0,cashbox_entry INTEGER NOT NULL DEFAULT 0,expense_entry INTEGER NOT NULL DEFAULT 0)";
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL(SETTLEMENT_SQL);
        db.execSQL("CREATE TABLE workers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,pin_hash TEXT NOT NULL UNIQUE,role TEXT NOT NULL,shift_kind TEXT NOT NULL DEFAULT 'DAY',active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE pumps(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,fuel TEXT NOT NULL,price REAL NOT NULL DEFAULT 0,last_reading REAL NOT NULL DEFAULT 0,worker_id INTEGER,active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE shifts(id INTEGER PRIMARY KEY AUTOINCREMENT,worker_id INTEGER NOT NULL,opened_at TEXT NOT NULL,shift_date TEXT NOT NULL DEFAULT '',historical INTEGER NOT NULL DEFAULT 0,closed_at TEXT,status TEXT NOT NULL DEFAULT 'OPEN',sales REAL NOT NULL DEFAULT 0,collections REAL NOT NULL DEFAULT 0,cash_delivered REAL NOT NULL DEFAULT 0,debts REAL NOT NULL DEFAULT 0,expenses REAL NOT NULL DEFAULT 0,balance REAL NOT NULL DEFAULT 0,difference_reason TEXT DEFAULT '',manager_note TEXT DEFAULT '',sync_state TEXT NOT NULL DEFAULT 'LOCAL',revision INTEGER NOT NULL DEFAULT 0,shift_code TEXT NOT NULL DEFAULT '')");
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
        db.execSQL(POSTED_SQL);
        db.execSQL(SUPPLIER_SQL);
        ShiftWorkspace.create(db);
        Capital.create(db);
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
    static final String CASHBOX_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS cashbox_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,box_id INTEGER NOT NULL,direction TEXT NOT NULL,amount REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0,currency TEXT NOT NULL DEFAULT 'YER',orig_amount REAL NOT NULL DEFAULT 0,rate REAL NOT NULL DEFAULT 1,managed INTEGER NOT NULL DEFAULT 0)";
    static final String MATERIAL_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS material_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,material TEXT NOT NULL,direction TEXT NOT NULL,litres REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0)";
    static final String DEBTORS_SQL="CREATE TABLE IF NOT EXISTS debtors(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,phone TEXT NOT NULL DEFAULT '',opening REAL NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL DEFAULT '',telegram TEXT NOT NULL DEFAULT '')";
    static final String DEBT_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS debt_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,debtor_id INTEGER NOT NULL,direction TEXT NOT NULL,amount REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0,managed INTEGER NOT NULL DEFAULT 0)";
    static final String EXPENSE_ENTRIES_SQL="CREATE TABLE IF NOT EXISTS expense_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,category TEXT NOT NULL,amount REAL NOT NULL,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,created_at TEXT NOT NULL,source_shift INTEGER NOT NULL DEFAULT 0,box_id INTEGER NOT NULL DEFAULT 0,cashbox_entry INTEGER NOT NULL DEFAULT 0)";
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

    /** سجل الترحيل: كود الوردية مفتاح فريد، فلا تُرحّل وردية مرتين أبدًا. */
    static final String POSTED_SQL="CREATE TABLE IF NOT EXISTS posted_shifts("+
        "shift_code TEXT PRIMARY KEY,shift_id INTEGER NOT NULL,posted_at TEXT NOT NULL)";

    /** حساب شركة النفط: مشتريات المواد وما وُرِّد من الصناديق. */
    static final String SUPPLIER_SQL="CREATE TABLE IF NOT EXISTS supplier_entries("+
        "id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,material TEXT NOT NULL DEFAULT '',"+
        "litres REAL NOT NULL DEFAULT 0,unit_cost REAL NOT NULL DEFAULT 0,amount REAL NOT NULL,"+
        "box_id INTEGER NOT NULL DEFAULT 0,note TEXT NOT NULL DEFAULT '',entry_date TEXT NOT NULL,"+
        "created_at TEXT NOT NULL,material_entry INTEGER NOT NULL DEFAULT 0,"+
        "cashbox_entry INTEGER NOT NULL DEFAULT 0,voided INTEGER NOT NULL DEFAULT 0,supplier TEXT NOT NULL DEFAULT 'OIL',debt_entry INTEGER NOT NULL DEFAULT 0)";

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<26)Capital.create(db);
        if(oldVersion<25&&newVersion>=21){ShiftWorkspace.create(db);db.execSQL("UPDATE shift_workspace SET strict_counts=0,reviewed=0 WHERE shift_id IN (SELECT id FROM shifts WHERE status='OPEN')");}
        if(oldVersion<25&&newVersion>=25)db.execSQL("DELETE FROM shift_counts WHERE shift_id IN (SELECT id FROM shifts WHERE status='OPEN')");
        if(oldVersion<19){
            try{db.execSQL("ALTER TABLE supplier_entries ADD COLUMN supplier TEXT NOT NULL DEFAULT 'OIL'");}catch(Exception ignored){}
            // الحركات القديمة: الغاز لشركة الغاز وما عداه لشركة النفط.
            try{db.execSQL("UPDATE supplier_entries SET supplier='GAS' WHERE material='غاز'");}catch(Exception ignored){}
        }
        if(oldVersion<18){
            for(String col:new String[]{"currency TEXT NOT NULL DEFAULT 'YER'",
                    "orig_amount REAL NOT NULL DEFAULT 0","rate REAL NOT NULL DEFAULT 1"})
                try{db.execSQL("ALTER TABLE cashbox_entries ADD COLUMN "+col);}catch(Exception ignored){}
            // الحركات القديمة كلها بالريال اليمني.
            try{db.execSQL("UPDATE cashbox_entries SET orig_amount=amount WHERE orig_amount=0");}catch(Exception ignored){}
        }
        if(oldVersion<17){
            try{db.execSQL("ALTER TABLE debtors ADD COLUMN telegram TEXT NOT NULL DEFAULT ''");}catch(Exception ignored){}
        }
        if(oldVersion<16){
            try{db.execSQL(SUPPLIER_SQL);}catch(Exception ignored){}
        }
        if(oldVersion<15){
            try{db.execSQL(POSTED_SQL);}catch(Exception ignored){}
            // تسجيل ما رُحّل فعلًا حتى لا يُعاد ترحيله بعد الترقية.
            try{db.execSQL("INSERT OR IGNORE INTO posted_shifts(shift_code,shift_id,posted_at) "+
                "SELECT s.shift_code,s.id,COALESCE(s.closed_at,s.opened_at) FROM shifts s "+
                "WHERE s.shift_code<>'' AND ("+
                "EXISTS(SELECT 1 FROM cashbox_entries e WHERE e.source_shift=s.id) OR "+
                "EXISTS(SELECT 1 FROM debt_entries e WHERE e.source_shift=s.id) OR "+
                "EXISTS(SELECT 1 FROM expense_entries e WHERE e.source_shift=s.id) OR "+
                "EXISTS(SELECT 1 FROM material_entries e WHERE e.source_shift=s.id))");}catch(Exception ignored){}
        }
        if(oldVersion<14){
            try{db.execSQL("ALTER TABLE material_entries ADD COLUMN source_shift INTEGER NOT NULL DEFAULT 0");}catch(Exception ignored){}
            // ربط الحركات القديمة بورديّاتها المستخرجة من البيان.
            try{db.execSQL("UPDATE material_entries SET source_shift=CAST(REPLACE(SUBSTR(note,INSTR(note,'#')+1),' — مبيعات','') AS INTEGER) "+
                "WHERE source_shift=0 AND note LIKE 'وردية #%'");}catch(Exception ignored){}
        }
        if(oldVersion<13){
            try{db.execSQL("ALTER TABLE shifts ADD COLUMN shift_code TEXT NOT NULL DEFAULT ''");}catch(Exception ignored){}
            try{db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_shift_code ON shifts(shift_code) WHERE shift_code<>''");}catch(Exception ignored){}
        }
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
        if(oldVersion<20){
            db.execSQL(SETTLEMENT_SQL);
            ensureColumn(db,"cashbox_entries","managed","INTEGER NOT NULL DEFAULT 0");
            ensureColumn(db,"debt_entries","managed","INTEGER NOT NULL DEFAULT 0");
            ensureColumn(db,"expense_entries","cashbox_entry","INTEGER NOT NULL DEFAULT 0");
            ensureColumn(db,"supplier_entries","debt_entry","INTEGER NOT NULL DEFAULT 0");
        }
    }

    private static void ensureColumn(SQLiteDatabase db,String table,String column,String definition){
        try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){
            while(c.moveToNext())if(column.equals(c.getString(1)))return;
        }
        db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
    }
    private interface Write<T>{ T run(); }
    private final ThreadLocal<java.util.List<Runnable>> pendingNotifications=new ThreadLocal<>();
    private <T> T atomic(Write<T> action){
        SQLiteDatabase db=getWritableDatabase();boolean outer=!db.inTransaction();
        if(outer)pendingNotifications.set(new java.util.ArrayList<>());
        db.beginTransaction();boolean success=false;
        try{T result=action.run();db.setTransactionSuccessful();success=true;return result;}
        finally{
            db.endTransaction();
            if(outer){java.util.List<Runnable> callbacks=pendingNotifications.get();pendingNotifications.remove();
                if(success&&callbacks!=null)for(Runnable callback:callbacks)try{callback.run();}catch(Exception ignored){}
            }
        }
    }
    private void afterCommit(Runnable callback){
        java.util.List<Runnable> callbacks=pendingNotifications.get();
        if(callbacks==null)callback.run();else callbacks.add(callback);
    }
    private void requireDate(String date){
        try{java.time.LocalDate.parse(date);}catch(Exception e){throw new IllegalArgumentException("اكتب تاريخًا صحيحًا بصيغة YYYY-MM-DD");}
        if(periodLocked(date))throw new IllegalStateException("الفترة مقفلة؛ لم تُحفظ أي تغييرات.");
    }
    private void requireEntity(String table,long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM "+table+" WHERE id=?",new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())throw new IllegalStateException("الحساب غير موجود");
        }
    }
    private void requireStandalone(String table,long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT entry_date,source_shift"+
                ((table.equals("cashbox_entries")||table.equals("debt_entries"))?",managed":",0")+
                " FROM "+table+" WHERE id=?",new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())throw new IllegalStateException("الحركة غير موجودة");
            requireDate(c.getString(0));
            if(c.getLong(1)>0||c.getInt(2)>0)throw new IllegalStateException("هذه الحركة مرتبطة بعملية أصلية؛ صحّحها من مصدرها.");
        }
        String settlementColumn=table.equals("cashbox_entries")?"cashbox_entry":table.equals("debt_entries")?"debt_entry":table.equals("expense_entries")?"expense_entry":"";
        if(!settlementColumn.isEmpty())try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM settlement_links WHERE "+settlementColumn+"=?",new String[]{String.valueOf(id)})){
            if(c.moveToFirst())throw new IllegalStateException("الحركة مرتبطة بقيد آخر؛ صحّح العملية من مصدرها.");
        }
        String link=table.equals("cashbox_entries")?"cashbox_entry":table.equals("material_entries")?"material_entry":table.equals("debt_entries")?"debt_entry":"";
        if(!link.isEmpty())try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM supplier_entries WHERE voided=0 AND "+link+"=?",new String[]{String.valueOf(id)})){
            if(c.moveToFirst())throw new IllegalStateException("هذه الحركة مرتبطة بالمورّد؛ صحّحها من حساب المورّد.");
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
        if(isHistorical(shiftId)||!isOpen(shiftId))return;
        SQLiteDatabase db=getWritableDatabase();
        db.execSQL("UPDATE readings SET price=(SELECT p.price FROM pumps p WHERE p.id=readings.pump_id) WHERE shift_id=?",new Object[]{shiftId});
        db.execSQL("UPDATE readings SET sales=(current-previous)*price WHERE shift_id=? AND current IS NOT NULL",new Object[]{shiftId});
    }

    /**
     * ينقل عدّاد الطرمبة المضبوط من الإعدادات إلى الوردية المفتوحة.
     * لا نلمس طرمبة أدخل لها العامل قراءة حالية، حتى لا تضيع مبيعاته.
     */
    public void refreshShiftPrevious(long shiftId){
        if(isHistorical(shiftId)||!isOpen(shiftId))return;
        getWritableDatabase().execSQL(
            "UPDATE readings SET previous=(SELECT p.last_reading FROM pumps p WHERE p.id=readings.pump_id) "+
            "WHERE shift_id=? AND current IS NULL",new Object[]{shiftId});
    }

    /** يزامن الوردية المفتوحة مع أي تغيير في الإعدادات: طرمبات جديدة، أسعار، وعدّادات. */
    public void syncShiftWithSettings(long shiftId){
        if(isHistorical(shiftId)||!isOpen(shiftId))return;
        syncShiftPumps(shiftId);
        refreshShiftPrevious(shiftId);
        refreshShiftPrices(shiftId);
    }
    /** يضمّ أي طرمبة نشطة أُضيفت بعد فتح الوردية. */
    public int syncShiftPumps(long shiftId){
        if(isHistorical(shiftId)||!isOpen(shiftId))return 0;
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
    private void requireEditableMovement(long movementId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT shift_id FROM movements WHERE id=?",new String[]{String.valueOf(movementId)})){
            if(!c.moveToFirst()||!isOpen(c.getLong(0)))throw new IllegalStateException("الوردية مغلقة؛ افتحها للتصحيح أولًا");
            requireDate(shiftDate(c.getLong(0)));
        }
    }
    private void validateMovement(String type,String name,double amount){
        if(!java.util.Arrays.asList("CASH","DEBT","COLLECTION","EXPENSE").contains(type))throw new IllegalArgumentException("نوع حركة غير صحيح");
        if(name==null||name.trim().isEmpty()||!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("أدخل اسمًا ومبلغًا صحيحًا أكبر من صفر");
    }
    public void deleteMovement(long movementId){atomic(()->{requireEditableMovement(movementId);getWritableDatabase().delete("movements","id=?",new String[]{String.valueOf(movementId)});return null;});}
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
    private static final String LIVE_PUMP = "(r.current IS NOT NULL OR (p.active=1 AND EXISTS(SELECT 1 FROM shifts live WHERE live.id=r.shift_id AND live.status IN ('OPEN','RETURNED'))))";
    public Cursor shiftReadings(long shiftId){return getReadableDatabase().rawQuery("SELECT r.id,p.name,p.fuel,r.previous,r.current,r.price,r.sales,p.active FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP+" ORDER BY CASE TRIM(p.fuel) WHEN 'بترول' THEN 0 WHEN 'البترول' THEN 0 WHEN 'بنزين' THEN 0 WHEN 'البنزين' THEN 0 WHEN 'ديزل' THEN 1 WHEN 'الديزل' THEN 1 WHEN 'غاز' THEN 2 WHEN 'الغاز' THEN 2 ELSE 3 END,p.id",new String[]{String.valueOf(shiftId)});}
    public boolean saveReading(long readingId,double current){SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT previous,price FROM readings WHERE id=? AND EXISTS(SELECT 1 FROM shifts s WHERE s.id=readings.shift_id AND s.status IN ('OPEN','RETURNED'))",new String[]{String.valueOf(readingId)})){if(c.moveToFirst()){double previous=c.getDouble(0),price=c.getDouble(1);if(!Double.isFinite(current)||!Double.isFinite(previous)||!Double.isFinite(price)||price<=0||current<previous||!Double.isFinite(Calc.pumpSales(previous,current,price)))return false;ContentValues v=new ContentValues();v.put("current",current);v.put("sales",Calc.pumpSales(previous,current,price));db.update("readings",v,"id=?",new String[]{String.valueOf(readingId)});return true;}}return false;}
    /** تعديل القراءة السابقة يدويًا، مع إعادة حساب المبيعات إن كانت الحالية مُدخلة. */
    public boolean savePrevious(long readingId,double previous){
        if(!Double.isFinite(previous)||previous<0)return false;
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT current,price FROM readings WHERE id=? AND EXISTS(SELECT 1 FROM shifts s WHERE s.id=readings.shift_id AND s.status IN ('OPEN','RETURNED'))",new String[]{String.valueOf(readingId)})){
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
    public void addMovement(long shiftId,String type,String name,double amount){validateMovement(type,name,amount);if(!isOpen(shiftId))throw new IllegalStateException("الوردية مغلقة؛ افتحها للتصحيح أولًا");requireDate(shiftDate(shiftId));SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("shift_id",shiftId);v.put("type",type);v.put("name",name.trim());v.put("amount",amount);v.put("created_at",Util.now());db.insertOrThrow("movements",null,v);ContentValues n=new ContentValues();n.put("type",type);n.put("name",name.trim());db.insertWithOnConflict("remembered_names",null,n,SQLiteDatabase.CONFLICT_IGNORE);}
    /** بيانات حركة واحدة: 0=type,1=name,2=amount. */
    public Cursor movement(long movementId){
        return getReadableDatabase().rawQuery(
            "SELECT type,name,amount FROM movements WHERE id=?",
            new String[]{String.valueOf(movementId)});
    }

    /**
     * يعدّل حركة مسجّلة في وردية مفتوحة.
     * يُسجَّل التعديل في سجل التدقيق بقيمته قبل وبعد.
     */
    public void updateMovement(long movementId,String type,String name,double amount){
        validateMovement(type,name,amount);requireEditableMovement(movementId);
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        String clean=name==null?"":name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب الاسم");
        String before="";long shiftId=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT shift_id,type,name,amount FROM movements WHERE id=?",
                new String[]{String.valueOf(movementId)})){
            if(!c.moveToFirst())throw new IllegalStateException("الحركة غير موجودة");
            shiftId=c.getLong(0);
            before=Calc.arabicType(c.getString(1))+" — "+c.getString(2)+" — "+Calc.money(c.getDouble(3));
        }
        // الوردية المُغلقة لا تُعدَّل حركاتها؛ تُفتح أولًا.
        if(!"OPEN".equals(shiftStatus(shiftId))&&!"RETURNED".equals(shiftStatus(shiftId)))
            throw new IllegalStateException("الوردية مُغلقة. افتحها للتعديل أولًا.");

        SQLiteDatabase db=getWritableDatabase();
        ContentValues v=new ContentValues();
        v.put("type",type);v.put("name",clean);v.put("amount",amount);
        db.update("movements",v,"id=?",new String[]{String.valueOf(movementId)});
        ContentValues n=new ContentValues();
        n.put("type",type);n.put("name",clean);
        db.insertWithOnConflict("remembered_names",null,n,SQLiteDatabase.CONFLICT_IGNORE);
        audit("movement",movementId,"EDIT_MOVEMENT",before,
                Calc.arabicType(type)+" — "+clean+" — "+Calc.money(amount),"تعديل حركة وردية");
    }

    public Cursor movements(long shiftId){return getReadableDatabase().rawQuery("SELECT id,type,name,amount FROM movements WHERE shift_id=? ORDER BY id ASC",new String[]{String.valueOf(shiftId)});}
    public double total(long shiftId,String type){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM movements WHERE shift_id=? AND type=?",new String[]{String.valueOf(shiftId),type})){c.moveToFirst();return c.getDouble(0);}}
    /** Clearing a field removes its prior contribution only when the worker saves it. */
    public boolean clearReading(long readingId,long shiftId){
        ContentValues value=new ContentValues();value.putNull("current");value.put("sales",0);
        return getWritableDatabase().update("readings",value,"id=? AND shift_id=? AND EXISTS(SELECT 1 FROM shifts WHERE id=? AND status IN ('OPEN','RETURNED'))",new String[]{String.valueOf(readingId),String.valueOf(shiftId),String.valueOf(shiftId)})==1;
    }
    public double sales(long shiftId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN r.current IS NOT NULL AND r.current>=r.previous AND r.price>0 THEN (r.current-r.previous)*r.price ELSE 0 END),0) FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP,new String[]{String.valueOf(shiftId)})){c.moveToFirst();return c.getDouble(0);}}
    public double balance(long shiftId){return Calc.balance(sales(shiftId),total(shiftId,"COLLECTION"),total(shiftId,"CASH"),total(shiftId,"DEBT"),total(shiftId,"EXPENSE"));}
    /** Only floating-point arithmetic noise is tolerated; monetary differences are not settled. */
    private void requireMatchedShift(long shiftId){
        if(ShiftWorkspace.exists(this,shiftId)&&!closingWorkspace&& !isOpen(shiftId))throw new IllegalStateException("الوردية مرحّلة وغير قابلة للتعديل");
        double difference=balance(shiftId);
        if(!Double.isFinite(difference)||Math.abs(difference)>0.0000001)
            throw new IllegalStateException("لا يمكن إقفال أو ترحيل الوردية حتى يصبح الفارق صفرًا. الباقي: "+Double.toString(difference)+" ر.ي. البيانات محفوظة؛ صحح القراءات أو الحركات.");
    }
    /** Status, pump handover, ledgers and journal commit together. */
    private boolean closingWorkspace=false;
    public String closeAndPostShift(long shiftId,int workerId,String reason,long cashboxId){return closeAndPostShift(shiftId,workerId,reason,cashboxId,false);}
    String closeAndPostShift(long shiftId,int workerId,String reason,long cashboxId,boolean capitalOverride){
        return atomic(()->{
            if(!isOpen(shiftId))throw new IllegalStateException("الوردية مغلقة بالفعل");
            requireDate(shiftDate(shiftId));
            String error=validateShift(shiftId);
            if(!error.isEmpty())throw new IllegalStateException(error);
            double difference=balance(shiftId);
            if(!Double.isFinite(difference))throw new IllegalStateException("توجد قيمة غير صالحة في الوردية");
            String why=reason==null?"":reason.trim();
            requireMatchedShift(shiftId);
            ShiftWorkspace.ready(this,shiftId);
            boolean unified=ShiftWorkspace.exists(this,shiftId);
            Map<String,Long> before=unified?ShiftWorkspace.before(this):null;
            Capital.Plan capitalPlan=Capital.plan(this,shiftId);
            closingWorkspace=true;
            try{
                submit(shiftId,workerId,why);
                approve(shiftId);
                String result=postShift(shiftId,unified?ShiftWorkspace.box(this,shiftId):cashboxId);
                journalShift(shiftId);
                if(unified){ShiftWorkspace.convertWorkerCash(this,shiftId,before.get("cashbox_entries"));ShiftWorkspace.post(this,shiftId);ShiftWorkspace.link(this,shiftId,before);}
                Capital.finish(this,shiftId,capitalPlan,reason,capitalOverride);
                return result;
            }finally{closingWorkspace=false;}
        });
    }
    public void submit(long shiftId,int workerId,String reason){if(ShiftWorkspace.exists(this,shiftId)&&!closingWorkspace)throw new IllegalStateException("استخدم إغلاق الوردية وترحيل الكل");requireMatchedShift(shiftId);SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("sales",sales(shiftId));v.put("collections",total(shiftId,"COLLECTION"));v.put("cash_delivered",total(shiftId,"CASH"));v.put("debts",total(shiftId,"DEBT"));v.put("expenses",total(shiftId,"EXPENSE"));v.put("balance",balance(shiftId));v.put("difference_reason",reason);v.put("manager_note","");v.put("status","SUBMITTED");v.put("closed_at",Util.now());v.put("sync_state","PENDING");db.execSQL("UPDATE shifts SET revision=revision+1 WHERE id=?",new Object[]{shiftId});db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,workerId,"SUBMIT","إرسال/تعديل الوردية؛ السبب: "+reason);}
    public Cursor archive(int workerId,boolean admin){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.status,s.sales,s.balance,s.sync_state,COALESCE(s.manager_note,''),COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) FROM shifts s JOIN workers w ON w.id=s.worker_id "+(admin?"":"WHERE s.worker_id=? ")+"ORDER BY COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) DESC,s.id DESC",admin?null:new String[]{String.valueOf(workerId)});}
    public Cursor submitted(){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.sales,s.balance,s.difference_reason FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.status='SUBMITTED' ORDER BY s.id",null);}
    public int pendingCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE status='SUBMITTED'",null)){c.moveToFirst();return c.getInt(0);}}
    public int pendingSyncCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shifts WHERE sync_state='PENDING'",null)){c.moveToFirst();return c.getInt(0);}}
    public Cursor pendingSync(){return getReadableDatabase().rawQuery("SELECT s.id,w.name,s.opened_at,s.closed_at,s.status,s.sales,s.collections,s.cash_delivered,s.debts,s.expenses,s.balance,s.difference_reason,s.revision,COALESCE(s.manager_note,'') FROM shifts s JOIN workers w ON w.id=s.worker_id WHERE s.sync_state='PENDING' AND s.status NOT IN ('OPEN','RETURNED') ORDER BY s.id",null);}
    public boolean markSynced(long shiftId,int revision){ContentValues v=new ContentValues();v.put("sync_state","SYNCED");return getWritableDatabase().update("shifts",v,"id=? AND revision=? AND status NOT IN ('OPEN','RETURNED')",new String[]{String.valueOf(shiftId),String.valueOf(revision)})==1;}
    public Cursor syncReadings(long shiftId){return getReadableDatabase().rawQuery("SELECT p.name,p.fuel,r.previous,r.current,r.price,r.sales FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND "+LIVE_PUMP+" ORDER BY p.id",new String[]{String.valueOf(shiftId)});}
    public Cursor syncMovements(long shiftId){return getReadableDatabase().rawQuery("SELECT type,name,amount FROM movements WHERE shift_id=? ORDER BY id",new String[]{String.valueOf(shiftId)});}
    /** Legacy entry point cannot close an unmatched shift. */
    public void closeUnmatched(long shiftId){
        throw new IllegalStateException("إقفال وردية غير مطابقة غير مسموح؛ صحح الفارق أولًا.");
    }
    public void approve(long shiftId){if(ShiftWorkspace.exists(this,shiftId)&&!closingWorkspace)throw new IllegalStateException("استخدم إغلاق الوردية وترحيل الكل");requireMatchedShift(shiftId);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(!isHistorical(shiftId))db.execSQL("UPDATE pumps SET last_reading=(SELECT r.current FROM readings r WHERE r.shift_id=? AND r.pump_id=pumps.id) WHERE id IN (SELECT pump_id FROM readings WHERE shift_id=? AND current IS NOT NULL)",new Object[]{shiftId,shiftId});ContentValues v=new ContentValues();v.put("status","APPROVED");v.put("sync_state","PENDING");db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,1,"APPROVE","اعتماد المدير");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void returnToWorker(long shiftId,String note){if(ShiftWorkspace.exists(this,shiftId))throw new IllegalStateException("الدفاتر المرحّلة للعرض فقط");SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("status","RETURNED");v.put("manager_note",note);v.put("sync_state","PENDING");db.execSQL("UPDATE shifts SET revision=revision+1 WHERE id=?",new Object[]{shiftId});db.update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});audit(db,shiftId,1,"RETURN","إرجاع للعامل: "+note);}
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
        return atomic(()->addCashboxAtomic(name,opening));
    }
    private long addCashboxAtomic(String name,double opening){
        if(opening!=0&&openingPosted())throw new IllegalStateException("الأرصدة الافتتاحية معتمدة؛ افتح الحساب بصفر ثم سجّل حركته.");
        String clean=name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب اسم الصندوق");
        if(!Double.isFinite(opening))throw new IllegalArgumentException("الرصيد الافتتاحي غير صالح");
        ContentValues v=new ContentValues();v.put("name",clean);v.put("opening",opening);v.put("created_at",Util.now());
        long id=getWritableDatabase().insertWithOnConflict("cashboxes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1)throw new IllegalArgumentException("يوجد صندوق بهذا الاسم");
        CashAccounts.seed(getWritableDatabase(),id,opening);return id;
    }
    public void renameCashbox(long id,String name){
        String clean=name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("اكتب اسم الصندوق");
        ContentValues v=new ContentValues();v.put("name",clean);
        if(getWritableDatabase().updateWithOnConflict("cashboxes",v,"id=?",new String[]{String.valueOf(id)},SQLiteDatabase.CONFLICT_IGNORE)!=1)
            throw new IllegalArgumentException("يوجد صندوق بهذا الاسم");
    }
    public void setCashboxOpening(long id,double opening){
        atomic(()->{setCashboxOpeningAtomic(id,opening);return null;});
    }
    private void setCashboxOpeningAtomic(long id,double opening){
        if(openingPosted())throw new IllegalStateException("الأرصدة الافتتاحية معتمدة؛ سجّل حركة تصحيح بدل تغيير الأصل.");
        if(!Double.isFinite(opening))throw new IllegalArgumentException("الرصيد الافتتاحي غير صالح");
        CashAccounts.setOpening(this,id,ShiftWorkspace.boxCurrency(this,id),opening/ShiftWorkspace.openingRate(this,id));
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
            "SELECT COALESCE(SUM(b.opening),0)+COALESCE((SELECT SUM(CASE WHEN e.direction='IN' THEN e.amount ELSE -e.amount END) FROM cashbox_entries e JOIN cashboxes x ON x.id=e.box_id),0) FROM cashboxes b",null)){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }
    // ==================== تعديل الحركات ====================

    /** بيانات حركة صندوق: 0=direction,1=amount,2=note,3=entry_date,4=source_shift,5=currency,6=orig_amount */
    public Cursor cashboxEntry(long id){
        return getReadableDatabase().rawQuery(
            "SELECT direction,amount,note,entry_date,source_shift,"+
            "COALESCE(currency,'YER'),COALESCE(NULLIF(orig_amount,0),amount) "+
            "FROM cashbox_entries WHERE id=?",new String[]{String.valueOf(id)});
    }

    /**
     * يعدّل حركة صندوق: يعكس قيدها القديم ويكتب الجديد،
     * ويسجّل القيمة قبل وبعد في سجل التدقيق.
     */
    public void updateCashboxEntry(long id,String direction,double amount,String note,
                                   String date,String currency){
        atomic(()->{updateCashboxEntryAtomic(id,direction,amount,note,date,currency);return null;});
    }
    private void updateCashboxEntryAtomic(long id,String direction,double amount,String note,
                                   String date,String currency){
        requireDate(date);requireStandalone("cashbox_entries",id);
        if(explicitCashEntry(id))throw new IllegalStateException("ألغِ الحركة ثم أعد تسجيلها لتصحيح طرفي القيد معًا.");
        if(!"IN".equals(direction)&&!"OUT".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        String before="";long boxId=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT box_id,direction,amount,note,source_shift FROM cashbox_entries WHERE id=?",
                new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())throw new IllegalStateException("الحركة غير موجودة");
            if(c.getLong(4)>0)throw new IllegalStateException("حركة مرحّلة من وردية ولا تُعدَّل يدويًا");
            boxId=c.getLong(0);
            before=("IN".equals(c.getString(1))?"وارد ":"صادر ")+Calc.money(c.getDouble(2))+" — "+c.getString(3);
        }
        String code=currency==null||currency.trim().isEmpty()?"YER":currency.trim();
        double rate=rate(code);
        try(Cursor old=getReadableDatabase().rawQuery("SELECT currency,rate FROM cashbox_entries WHERE id=?",new String[]{String.valueOf(id)})){
            if(old.moveToFirst()&&code.equals(old.getString(0)))rate=old.getDouble(1);
        }
        double yer=amount*rate;
        if(!Double.isFinite(yer)||!Double.isFinite(rate)||rate<=0)throw new IllegalArgumentException("سعر الصرف أو المبلغ غير صالح");
        String label=note==null?"":note.trim();
        if(!"YER".equals(code))
            label=(label.isEmpty()?"":label+" — ")+Calc.money(amount)+" "+currencyName(code)
                    +" × "+Calc.money(rate);

        reverseManual("CASHBOX",id);
        ContentValues v=new ContentValues();
        v.put("direction",direction);v.put("amount",yer);v.put("note",label);
        v.put("entry_date",date);v.put("currency",code);v.put("orig_amount",amount);v.put("rate",rate);
        getWritableDatabase().update("cashbox_entries",v,"id=?",new String[]{String.valueOf(id)});
        journalManual("cashbox",id,date,label,yer,
                "IN".equals(direction)?Journal.CASH:Journal.SUSPENSE,
                "IN".equals(direction)?Journal.SUSPENSE:Journal.CASH);
        audit("cashbox",id,"EDIT_ENTRY",before,
                ("IN".equals(direction)?"وارد ":"صادر ")+Calc.money(yer)+" — "+label,"تعديل حركة صندوق");
    }

    /** بيانات حركة دين: 0=direction,1=amount,2=note,3=entry_date,4=source_shift,5=debtor_id */
    public Cursor debtEntry(long id){
        return getReadableDatabase().rawQuery(
            "SELECT direction,amount,note,entry_date,source_shift,debtor_id "+
            "FROM debt_entries WHERE id=?",new String[]{String.valueOf(id)});
    }

    /** يعدّل حركة دين بنفس القاعدة: عكس القيد القديم وكتابة الجديد. */
    public void updateDebtEntry(long id,String direction,double amount,String note,String date){
        atomic(()->{updateDebtEntryAtomic(id,direction,amount,note,date);return null;});
    }
    private void updateDebtEntryAtomic(long id,String direction,double amount,String note,String date){
        requireDate(date);requireStandalone("debt_entries",id);
        if(explicitCustomerEntry(id))throw new IllegalStateException("للحفاظ على طرفي القيد، ألغِ الحركة وأعد تسجيلها بالقيم الصحيحة.");
        if(!"DEBT".equals(direction)&&!"PAID".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        String before="";long debtorId=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT debtor_id,direction,amount,note,source_shift FROM debt_entries WHERE id=?",
                new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())throw new IllegalStateException("الحركة غير موجودة");
            if(c.getLong(4)>0)throw new IllegalStateException("حركة مرحّلة من وردية ولا تُعدَّل يدويًا");
            debtorId=c.getLong(0);
            before=("DEBT".equals(c.getString(1))?"دين ":"سداد ")+Calc.money(c.getDouble(2))+" — "+c.getString(3);
        }
        String clean=note==null?"":note.trim();
        reverseManual("DEBT",id);
        ContentValues v=new ContentValues();
        v.put("direction",direction);v.put("amount",amount);v.put("note",clean);v.put("entry_date",date);
        getWritableDatabase().update("debt_entries",v,"id=?",new String[]{String.valueOf(id)});
        String who=debtorName(debtorId);
        journalManual("debt",id,date,("DEBT".equals(direction)?"دين على ":"سداد من ")+who,amount,
                "DEBT".equals(direction)?Journal.RECEIVABLE:Journal.SUSPENSE,
                "DEBT".equals(direction)?Journal.SUSPENSE:Journal.RECEIVABLE);
        audit("debt",id,"EDIT_ENTRY",before,
                ("DEBT".equals(direction)?"دين ":"سداد ")+Calc.money(amount)+" — "+clean,"تعديل حركة دين");
    }

    // ==================== العملات وأسعار الصرف ====================

    /** العملات المتاحة في إدخال حركة الصندوق. */
    public static final String[] CURRENCIES={"YER","SAR","USD"};
    public static final String[] CURRENCY_NAMES={"يمني","سعودي","دولار"};

    public static String currencyName(String code){
        for(int i=0;i<CURRENCIES.length;i++)if(CURRENCIES[i].equals(code))return CURRENCY_NAMES[i];
        return code==null?"يمني":code;
    }

    /** سعر صرف العملة إلى الريال اليمني. اليمني دائمًا واحد. */
    public double rate(String code){
        if(code==null||"YER".equals(code))return 1;
        if(!java.util.Arrays.asList(CURRENCIES).contains(code))throw new IllegalArgumentException("عملة غير معروفة");
        try{
            double v=Double.parseDouble(setting("rate_"+code,defaultRate(code)));
            return Double.isFinite(v)&&v>0?v:Double.parseDouble(defaultRate(code));
        }catch(Exception e){return Double.parseDouble(defaultRate(code));}
    }

    public void setRate(String code,double value){
        if("YER".equals(code))throw new IllegalArgumentException("الريال اليمني هو عملة الأساس");
        if(!java.util.Arrays.asList(CURRENCIES).contains(code)||!Double.isFinite(value)||!(value>0))throw new IllegalArgumentException("سعر الصرف أو العملة غير صالح");
        setSetting("rate_"+code,String.valueOf(value));
        audit("device",0,"SET_RATE",code,Calc.money(value)+" ريال يمني","سعر الصرف");
    }

    private static String defaultRate(String code){
        if("SAR".equals(code))return "670";
        if("USD".equals(code))return "2500";
        return "1";
    }

    /** يحوّل مبلغًا من عملته إلى الريال اليمني. */
    public double toYer(double amount,String code){
        return amount*rate(code);
    }

    /**
     * حركة صندوق بعملة محدّدة: يُحفظ المبلغ الأصلي وسعر الصرف،
     * ويُخزَّن الرصيد بالريال اليمني حتى تبقى الأرصدة موحّدة.
     */
    public long addCashboxEntry(long boxId,String direction,double amount,String note,
                                String date,String currency){
        return atomic(()->addCashboxEntryAtomic(boxId,direction,amount,note,date,currency));
    }
    private long addCashboxEntryAtomic(long boxId,String direction,double amount,String note,
                                String date,String currency){
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        String code=currency==null||currency.trim().isEmpty()?"YER":currency.trim();
        double rate=rate(code);
        double yer=amount*rate;
        String label=note==null?"":note.trim();
        // العملة الأجنبية تُذكر في البيان ليُعرف أصل المبلغ.
        if(!"YER".equals(code))
            label=(label.isEmpty()?"":label+" — ")+Calc.money(amount)+" "+currencyName(code)
                    +" × "+Calc.money(rate);
        long id=addCashboxEntry(boxId,direction,yer,label,date,0);
        ContentValues v=new ContentValues();
        v.put("currency",code);v.put("orig_amount",amount);v.put("rate",rate);
        getWritableDatabase().update("cashbox_entries",v,"id=?",new String[]{String.valueOf(id)});
        return id;
    }

    public long addCashboxEntry(long boxId,String direction,double amount,String note,String date){
        return atomic(()->addCashboxEntryAtomic(boxId,direction,amount,note,date));
    }
    private long addCashboxEntryAtomic(long boxId,String direction,double amount,String note,String date){
        return addCashboxEntry(boxId,direction,amount,note,date,0);
    }
    public long addCashboxEntry(long boxId,String direction,double amount,String note,String date,long sourceShift){
        return atomic(()->addCashboxEntryAtomic(boxId,direction,amount,note,date,sourceShift));
    }
    private long addCashboxEntryAtomic(long boxId,String direction,double amount,String note,String date,long sourceShift){
        requireDate(date);requireEntity("cashboxes",boxId);
        if(!"IN".equals(direction)&&!"OUT".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        ContentValues v=new ContentValues();
        v.put("box_id",boxId);v.put("direction",direction);v.put("amount",amount);
        v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());v.put("source_shift",sourceShift);v.put("managed",suppressJournal?1:0);
        long id=getWritableDatabase().insertOrThrow("cashbox_entries",null,v);
        // الحركة اليدوية تُقيَّد مزدوجة فورًا؛ حركات الورديات تُقيَّد مع قيد الوردية.
        if(sourceShift==0)journalManual("cashbox",id,date,note.trim(),amount,
                "IN".equals(direction)?Journal.CASH:Journal.SUSPENSE,
                "IN".equals(direction)?Journal.SUSPENSE:Journal.CASH);
        return id;
    }
    public boolean deleteCashboxEntry(long id){
        return atomic(()->deleteCashboxEntryAtomic(id));
    }
    private boolean deleteCashboxEntryAtomic(long id){
        requireStandalone("cashbox_entries",id);
        reverseManual("CASHBOX",id);
        reverseManual("CASH_EXPLICIT",id);
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
    /**
     * حركات الصناديق ضمن مدى تاريخي، مرتّبة بالتاريخ ثم بالتسلسل.
     * 0=تاريخ,1=اتجاه,2=مبلغ,3=بيان,4=اسم الصندوق,5=وردية المصدر
     */
    public Cursor cashboxRange(String from,String to,long boxId){
        StringBuilder where=new StringBuilder("WHERE e.entry_date BETWEEN ? AND ? ");
        java.util.List<String> args=new java.util.ArrayList<>();
        args.add(from);args.add(to);
        if(boxId>0){where.append("AND e.box_id=? ");args.add(String.valueOf(boxId));}
        return getReadableDatabase().rawQuery(
            "SELECT e.entry_date,e.direction,e.amount,e.note,b.name,e.source_shift "+
            "FROM cashbox_entries e JOIN cashboxes b ON b.id=e.box_id "+where+
            "ORDER BY e.entry_date,e.id",args.toArray(new String[0]));
    }

    /** أقدم تاريخ حركة في الصناديق، أو تاريخ اليوم إن لم توجد حركات. */
    public String firstCashboxDate(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT MIN(entry_date) FROM cashbox_entries",null)){
            if(c.moveToFirst()&&c.getString(0)!=null&&!c.getString(0).isEmpty())return c.getString(0);
        }
        return ShiftDates.today();
    }

    // ==================== حركة المخاريج ====================
    public long addExpense(String category,double amount,String note,String date,long boxId,long sourceShift){
        return atomic(()->addExpenseAtomic(category,amount,note,date,boxId,sourceShift));
    }
    private long addExpenseAtomic(String category,double amount,String note,String date,long boxId,long sourceShift){
        requireDate(date);if(boxId>0)requireEntity("cashboxes",boxId);
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
            if(boxId>0){
                suppressJournal=true;
                try{
                    long cashId=addCashboxEntry(boxId,"OUT",amount,"مخاريج: "+clean+(note.trim().isEmpty()?"":" — "+note.trim()),date,sourceShift);
                    db.execSQL("UPDATE expense_entries SET cashbox_entry=? WHERE id=?",new Object[]{cashId,id});
                }finally{suppressJournal=false;}
            }
            ContentValues n=new ContentValues();n.put("type","EXPENSE");n.put("name",clean);
            db.insertWithOnConflict("remembered_names",null,n,SQLiteDatabase.CONFLICT_IGNORE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        // المخاريج مدينة، والدائن هو الصندوق إن دُفعت منه وإلا حساب وسيط.
        if(sourceShift==0)journalManual("expense",id,date,"مخاريج: "+clean,amount,
                Journal.EXPENSE,boxId>0?Journal.CASH:Journal.SUSPENSE);
        return id;
    }
    public boolean deleteExpense(long id){
        return atomic(()->deleteExpenseAtomic(id));
    }
    private boolean deleteExpenseAtomic(long id){
        requireStandalone("expense_entries",id);
        long cashId=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT box_id,cashbox_entry FROM expense_entries WHERE id=?",new String[]{String.valueOf(id)})){
            if(c.moveToFirst()){cashId=c.getLong(1);if(c.getLong(0)>0&&cashId==0)throw new IllegalStateException("مصروف قديم مرتبط بصندوق؛ يلزم مراجعة الربط قبل الإلغاء.");}
        }
        reverseManual("EXPENSE",id);
        if(cashId>0)getWritableDatabase().delete("cashbox_entries","id=?",new String[]{String.valueOf(cashId)});
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
        String id=String.valueOf(shiftId);
        // سجل الترحيل هو المرجع الأول.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM posted_shifts WHERE shift_id=?",
                new String[]{id})){
            if(c.moveToFirst())return true;
        }
        // لا بدّ من فحص المخزون أيضًا: وردية بمبيعات بلا نقد ولا دين ولا مخاريج
        // كانت تُعدّ «غير مرحّلة» فتُخصم لتراتها مرة بعد مرة.
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT (SELECT COUNT(*) FROM cashbox_entries WHERE source_shift=?)+(SELECT COUNT(*) FROM debt_entries WHERE source_shift=?)"+
            "+(SELECT COUNT(*) FROM expense_entries WHERE source_shift=?)"+
            "+(SELECT COUNT(*) FROM material_entries WHERE source_shift=? OR note LIKE ?)"+
            "",
            new String[]{id,id,id,id,"وردية #"+shiftId+" —%"})){
            c.moveToFirst();return c.getInt(0)>0;
        }
    }
    /** الورديات التي رُحّلت أكثر من مرة: 0=id,1=عامل,2=تاريخ,3=عدد النسخ. */
    public Cursor doublePosted(){
        return getReadableDatabase().rawQuery(
            "SELECT s.id,w.name,COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),"+
            "(SELECT COUNT(*) FROM material_entries m WHERE m.source_shift=s.id AND m.direction='OUT') "+
            "FROM shifts s JOIN workers w ON w.id=s.worker_id "+
            "WHERE (SELECT COUNT(*) FROM material_entries m WHERE m.source_shift=s.id AND m.direction='OUT') > "+
            "(SELECT COUNT(DISTINCT TRIM(p.fuel)) FROM readings r JOIN pumps p ON p.id=r.pump_id "+
            " WHERE r.shift_id=s.id AND r.current IS NOT NULL AND r.current>=r.previous) "+
            "ORDER BY s.id DESC",null);
    }

    /**
     * يصلح وردية رُحّلت مرتين: يلغي كل أثرها ثم يرحّلها مرة واحدة.
     * القيود المزدوجة تُعكس ولا تُحذف، فيبقى الأثر في سجل التدقيق.
     */
    public String repostShift(long shiftId){
        return atomic(()->repostShiftAtomic(shiftId));
    }
    private String repostShiftAtomic(long shiftId){
        java.util.List<Long> entries=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id FROM journal WHERE source='SHIFT' AND source_id=? AND reversed_by=0 AND reverses=0",
                new String[]{String.valueOf(shiftId)})){
            while(c.moveToNext())entries.add(c.getLong(0));
        }
        for(long entry:entries)reverseEntry(entry,"إصلاح ترحيل مكرّر");
        unpostShift(shiftId);
        audit("shift",shiftId,"REPAIR_DOUBLE_POST","ترحيل مكرّر","أُعيد الترحيل مرة واحدة","إصلاح");
        String posted=postShift(shiftId,defaultCashbox());
        journalShift(shiftId);
        return posted;
    }

    /** يلغي ترحيل وردية (عند حذفها أو إعادة ترحيلها). */
    public void unpostShift(long shiftId){
        atomic(()->{unpostShiftAtomic(shiftId);return null;});
    }
    private void unpostShiftAtomic(long shiftId){
        if(ShiftWorkspace.exists(this,shiftId))throw new IllegalStateException("الدفاتر المرحّلة للعرض فقط");
        SQLiteDatabase db=getWritableDatabase();
        // يُحرَّر حجز الكود ليجوز إعادة الترحيل بعد المراجعة.
        db.delete("posted_shifts","shift_id=?",new String[]{String.valueOf(shiftId)});
        db.delete("cashbox_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
        db.delete("debt_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
        db.delete("expense_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
        db.delete("material_entries","source_shift=? OR note LIKE ?",
                new String[]{String.valueOf(shiftId),"وردية #"+shiftId+" —%"});
    }
    /**
     * يرحّل وردية مُغلقة إلى بقية السجلات في معاملة واحدة:
     * النقد المسلّم إلى الصندوق، وديون الوردية على المدينين بأسمائهم،
     * واللترات المباعة تُخصم من المخزون.
     * يعيد سطر ملخّص لما جرى.
     */
    public String postShift(long shiftId,long cashboxId){
        return atomic(()->postShiftAtomic(shiftId,cashboxId));
    }
    private String postShiftAtomic(long shiftId,long cashboxId){
        requireMatchedShift(shiftId);
        requireDate(shiftDate(shiftId));if(isOpen(shiftId))throw new IllegalStateException("أغلق الوردية قبل الترحيل");if(total(shiftId,"CASH")>0)requireEntity("cashboxes",cashboxId);
        if(shiftPosted(shiftId))return "";
        // كود الوردية هو الحارس: يُحجز داخل المعاملة نفسها، فإن كان محجوزًا
        // فالوردية مُرحّلة سلفًا ويُلغى كل شيء. لا اعتماد على عدّ السجلات.
        final String code=shiftCode(shiftId);
        // كل سطر يحمل كود الوردية، فيُتتبَّع أي رصيد إلى مصدره.
        final String tag=code.isEmpty()?("#"+shiftId):code;
        SQLiteDatabase db=getWritableDatabase();
        String date=shiftDate(shiftId);
        StringBuilder log=new StringBuilder();
        db.beginTransaction();
        try{
            if(!code.isEmpty()){
                ContentValues claim=new ContentValues();
                claim.put("shift_code",code);
                claim.put("shift_id",shiftId);
                claim.put("posted_at",Util.now());
                long row=db.insertWithOnConflict("posted_shifts",null,claim,SQLiteDatabase.CONFLICT_IGNORE);
                if(row==-1)return ""; // محجوز: رُحّلت من قبل
            }
            double cash=total(shiftId,"CASH");
            if(cashboxId>0&&cash>0){
                addCashboxEntry(cashboxId,"IN",cash,"نقد مسلّم من وردية "+tag,date,shiftId);
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
                    addDebtEntry(debtorId,"DEBT",amount,"دين من وردية "+tag,date,shiftId);
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
                    addDebtEntry(debtorId,"PAID",amount,"سداد من وردية "+tag,date,shiftId);
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
                    v.put("category",name);v.put("amount",amount);v.put("note","وردية "+tag);
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
                    v.put("note","وردية "+tag+" — مبيعات");v.put("entry_date",date);v.put("created_at",Util.now());
                    v.put("source_shift",shiftId);
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
                "AND NOT ("+ZERO_WORKER_POSTED+") AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id AND j.reversed_by=0 AND j.reverses=0)",
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
        // Keep legacy reversals, but never create a new suspense posting.
        for(Journal.Line line:entry.lines){
            if(Journal.SUSPENSE.equals(line.account))
                throw new IllegalStateException("حدد الحساب المقابل للحركة قبل الترحيل. يمكنك حفظها كمسودة ثم استكمالها؛ الحساب الوسيط غير مسموح.");
        }
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
        ShiftWorkspace.immutable(this,"journal",entryId);
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
            try(Cursor original=db.rawQuery("SELECT reverses FROM journal WHERE id=?",new String[]{String.valueOf(entryId)})){if(original.moveToFirst()&&original.getLong(0)>0)throw new IllegalStateException("لا يُعكس قيد عكسي؛ راجع العملية الأصلية.");}
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
            try(Cursor linked=db.rawQuery("SELECT debt_entry,cashbox_entry,expense_entry FROM settlement_links WHERE entry_id=?",new String[]{String.valueOf(entryId)})){
                if(linked.moveToFirst()){
                    String[] tables={"debt_entries","cashbox_entries","expense_entries"};
                    for(int i=0;i<3;i++)if(linked.getLong(i)>0)db.delete(tables[i],"id=?",new String[]{String.valueOf(linked.getLong(i))});
                }
            }
            db.delete("settlement_links","entry_id=?",new String[]{String.valueOf(entryId)});
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

    // ==================== تصفية الحساب الوسيط ====================

    /** رصيد الحساب الوسيط: موجب مدين وسالب دائن. */
    public double suspenseBalance(){
        // يُحسب من نفس الحركات المعروضة بالضبط: المعلّقة فقط.
        // القيد المعكوس وقيده العكسي يُستبعدان معًا فيلغي أحدهما الآخر.
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN l.side='DEBIT' THEN l.amount ELSE -l.amount END),0) "+
                "FROM journal_lines l JOIN journal j ON j.id=l.entry_id "+
                "WHERE l.account=? AND j.reversed_by=0 AND j.reverses=0",
                new String[]{Journal.SUSPENSE})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }

    /** عدد الحركات المعلّقة فعلًا في الوسيط. */
    public int suspenseCount(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM journal j JOIN journal_lines l ON l.entry_id=j.id "+
                "WHERE l.account=? AND j.reversed_by=0 AND j.reverses=0",
                new String[]{Journal.SUSPENSE})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /**
     * الحركات المعلّقة في الحساب الوسيط.
     * 0=entryId,1=بيان,2=تاريخ,3=الجهة,4=المبلغ,5=الطرف الآخر,6=المصدر,
     * 7=رقم السجل,8=اسم السجل,9=بيان الحركة الأصلية,10=من سجّلها
     */
    public Cursor suspenseEntries(){
        return getReadableDatabase().rawQuery(
            "SELECT j.id,j.memo,j.entry_date,l.side,l.amount,"+
            "COALESCE((SELECT o.account FROM journal_lines o WHERE o.entry_id=j.id AND o.account<>? LIMIT 1),''),"+
            "j.source,j.source_id,"+
            // تفصيل المصدر: اسم الصندوق أو المدين أو باب المصروف، وبيان الحركة الأصلية.
            "COALESCE((SELECT b.name FROM cashbox_entries e JOIN cashboxes b ON b.id=e.box_id "+
            " WHERE j.source='CASHBOX' AND e.id=j.source_id),"+
            "(SELECT d.name FROM debt_entries e JOIN debtors d ON d.id=e.debtor_id "+
            " WHERE j.source='DEBT' AND e.id=j.source_id),"+
            "(SELECT e.category FROM expense_entries e WHERE j.source='EXPENSE' AND e.id=j.source_id),''),"+
            "COALESCE((SELECT e.note FROM cashbox_entries e WHERE j.source='CASHBOX' AND e.id=j.source_id),"+
            "(SELECT e.note FROM debt_entries e WHERE j.source='DEBT' AND e.id=j.source_id),"+
            "(SELECT e.note FROM expense_entries e WHERE j.source='EXPENSE' AND e.id=j.source_id),''),"+
            "COALESCE(j.actor,'') "+
            "FROM journal j JOIN journal_lines l ON l.entry_id=j.id "+
            // القيد العكسي يلغي أصله، فلا يُعرض ولا يُصرَّف من جديد.
            "WHERE l.account=? AND j.reversed_by=0 AND j.reverses=0 "+
            "ORDER BY j.entry_date DESC,j.id DESC",
            new String[]{Journal.SUSPENSE,Journal.SUSPENSE});
    }

    /** الحسابات التي يجوز تصريف الوسيط إليها. */
    public static final String[] SUSPENSE_TARGETS={
        Journal.RECEIVABLE, Journal.SALES, Journal.EXPENSE, Journal.CASH, Journal.EQUITY, Journal.WORKER
    };

    /**
     * يصرّف حركة معلّقة إلى حسابها الصحيح: يعكس القيد القديم ويكتب قيدًا جديدًا
     * بنفس المبلغ والتاريخ مع الحساب المختار. لا حذف، والأثر محفوظ.
     * وإن كان الحساب «ذمم المدينين» واسم المدين معروف، يُخصم من رصيده فعليًا.
     */
    public void settleSuspense(long entryId,String targetAccount,String party,String reason){
        atomic(()->{settleSuspenseAtomic(entryId,targetAccount,party,reason);return null;});
    }
    private void settleSuspenseAtomic(long entryId,String targetAccount,String party,String reason){
        if(targetAccount==null||targetAccount.trim().isEmpty())
            throw new IllegalArgumentException("اختر الحساب الصحيح");
        String memo="",date="",source="";long sourceId=0;
        double amount=0;boolean suspenseIsDebit=false;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT j.memo,j.entry_date,j.source,j.source_id,l.side,l.amount,j.reverses,j.reversed_by "+
                "FROM journal j JOIN journal_lines l ON l.entry_id=j.id "+
                "WHERE j.id=? AND l.account=?",
                new String[]{String.valueOf(entryId),Journal.SUSPENSE})){
            if(!c.moveToFirst())throw new IllegalStateException("الحركة غير موجودة في الحساب الوسيط");
            // القيد العكسي لا يُصرَّف: هو إلغاء لأصله وقد سُوّي معه.
            if(c.getLong(6)>0)throw new IllegalStateException("هذا قيد عكسي ولا يحتاج تصريفًا");
            if(c.getLong(7)>0)throw new IllegalStateException("سبق تصريف هذه الحركة");
            memo=c.getString(0);date=c.getString(1);source=c.getString(2);sourceId=c.getLong(3);
            suspenseIsDebit="DEBIT".equals(c.getString(4));
            amount=c.getDouble(5);
        }
        // الطرف الآخر يبقى كما هو، والوسيط يُستبدل بالحساب الصحيح.
        String other="";
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT account FROM journal_lines WHERE entry_id=? AND account<>? LIMIT 1",
                new String[]{String.valueOf(entryId),Journal.SUSPENSE})){
            if(c.moveToFirst())other=c.getString(0);
        }
        if(other.isEmpty())throw new IllegalStateException("القيد ناقص الطرف الآخر");
        if(!java.util.Arrays.asList(SUSPENSE_TARGETS).contains(targetAccount)||targetAccount.equals(other))throw new IllegalArgumentException("اختر حسابًا مقابلًا مختلفًا وصحيحًا");
        if(Journal.RECEIVABLE.equals(targetAccount)&&(party==null||findDebtor(party.trim())<=0))throw new IllegalArgumentException("اختر اسم عميل مسجّل");
        if(Journal.EXPENSE.equals(targetAccount)&&!suspenseIsDebit)throw new IllegalArgumentException("ردّ المصروف يُصحّح من حركته الأصلية");
        if(Journal.CASH.equals(targetAccount))requireEntity("cashboxes",defaultCashbox());

        String why=reason==null||reason.trim().isEmpty()?"تصريف الحساب الوسيط":reason.trim();
        reverseEntry(entryId,why);
        if(memo.length()>60)memo=memo.substring(0,60);
        Journal.Entry fresh=suspenseIsDebit
            ? Journal.simple(memo,date,source,sourceId,targetAccount,other,amount,party)
            : Journal.simple(memo,date,source,sourceId,other,targetAccount,amount,party);
        long created=postEntry(fresh);
        audit("journal",created,"SETTLE_SUSPENSE",Journal.SUSPENSE,targetAccount,why);

        ContentValues links=new ContentValues();links.put("entry_id",created);
        boolean previousSuppression=suppressJournal;suppressJournal=true;
        try{
            if(Journal.RECEIVABLE.equals(targetAccount)){
                long id=addDebtEntry(findDebtor(party.trim()),suspenseIsDebit?"DEBT":"PAID",amount,"تصريف حركة #"+entryId,date);
                links.put("debt_entry",id);
            }else if(Journal.CASH.equals(targetAccount)){
                long id=addCashboxEntry(defaultCashbox(),suspenseIsDebit?"IN":"OUT",amount,"تصريف حركة #"+entryId,date);
                links.put("cashbox_entry",id);
            }else if(Journal.EXPENSE.equals(targetAccount)){
                long id=addExpense(party==null||party.trim().isEmpty()?"مصروف من تصريف الوسيط":party.trim(),amount,"تصريف حركة #"+entryId,date,0,0);
                links.put("expense_entry",id);
            }
            getWritableDatabase().insertOrThrow("settlement_links",null,links);
        }finally{suppressJournal=previousSuppression;}
    }

    /**
     * ينظّف فوضى التصريف المتكرّر: يعكس كل قيد وسيط بقي معلّقًا ومعه قيد مقابل
     * لنفس المصدر والمبلغ، ويحذف قيود الديون المكرّرة الناتجة عن التصريف المتكرّر.
     * يعيد عدد ما نُظّف.
     */
    public int cleanSuspenseMess(){
        throw new IllegalStateException("لا يُحذف دين ولا يُصفّر الحساب الوسيط آليًا. راجع الحركة الأصلية وحدّد سبب التصحيح.");
    }

    /** الرصيد الحقيقي للوسيط في الدفتر كله، شاملًا القيود العكسية. */
    public double suspenseResidual(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END),0) "+
                "FROM journal_lines WHERE account=?",new String[]{Journal.SUSPENSE})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }

    /** يبحث عن مدين بالاسم، ويعيد صفرًا إن لم يوجد. */
    public long findDebtor(String name){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM debtors WHERE name=?",
                new String[]{name==null?"":name.trim()})){
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    /** ينشئ مدينًا جديدًا إن لم يكن موجودًا ويعيد معرّفه. */
    public long ensureDebtor(String name){
        long id=findDebtor(name);
        if(id>0)return id;
        ContentValues v=new ContentValues();
        v.put("name",name.trim());v.put("phone","");v.put("opening",0);v.put("created_at",Util.now());
        return getWritableDatabase().insertOrThrow("debtors",null,v);
    }

    /** الورديات المُغلقة التي لم يُسجَّل لها قيد بعد: 0=id,1=عامل,2=تاريخ,3=الفرق. */
    // A fully posted workspace can have cash/material operations with no worker money.
    // Its posted_shifts record is the completion evidence; no zero journal is invented.
    private static final String ZERO_WORKER_POSTED="s.sales=0 AND s.collections=0 AND s.cash_delivered=0 AND s.debts=0 AND s.expenses=0 AND s.balance=0 AND EXISTS(SELECT 1 FROM posted_shifts ps JOIN shift_workspace sw ON sw.shift_id=ps.shift_id WHERE ps.shift_id=s.id)";
    public Cursor unpostedShifts(){
        return getReadableDatabase().rawQuery(
            "SELECT s.id,w.name,COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),s.balance "+
            "FROM shifts s JOIN workers w ON w.id=s.worker_id "+
            "WHERE s.status<>'OPEN' AND NOT ("+ZERO_WORKER_POSTED+") AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id AND j.reversed_by=0 AND j.reverses=0) "+
            "ORDER BY s.id DESC",null);
    }

    /** هل للوردية قيد مسجّل؟ */
    public boolean shiftJournalled(long shiftId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM shifts s WHERE s.id=? AND ("+ZERO_WORKER_POSTED+")",new String[]{String.valueOf(shiftId)})){if(c.moveToFirst())return true;}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM journal WHERE source='SHIFT' AND source_id=? AND reversed_by=0 AND reverses=0",
                new String[]{String.valueOf(shiftId)})){
            return c.moveToFirst();
        }
    }

    /** يسجّل قيد الوردية المُغلقة. يرفض الترحيل إذا كان هناك فرق غير مسوّى. */
    public long journalShift(long shiftId){
        return atomic(()->journalShiftAtomic(shiftId));
    }
    private long journalShiftAtomic(long shiftId){
        requireMatchedShift(shiftId);
        requireDate(shiftDate(shiftId));if(isOpen(shiftId))throw new IllegalStateException("أغلق الوردية قبل الترحيل");
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
        if(!Double.isFinite(balance)||Math.abs(balance)>0.0000001)
            throw new IllegalStateException("لا يمكن ترحيل وردية بفرق "+Calc.money(Math.abs(balance))+" ر.ي بلا سبب مكتوب.");
        Journal.Entry e=Journal.shiftEntry(shiftId,shiftCode(shiftId),date,sales,collections,cash,debts,expenses,balance);
        return postEntry(e);
    }

    /** يسجّل سبب فرق وردية مُغلقة حتى يجوز ترحيلها، ويحفظ الأثر في سجل التدقيق. */
    public void settleShift(long shiftId,String reason){
        if(ShiftWorkspace.exists(this,shiftId))throw new IllegalStateException("لا يمكن تعديل الوردية المرحّلة");
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
            "AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id AND j.reversed_by=0 AND j.reverses=0) "+
            "ORDER BY s.id",null);
    }

    /** أول فترة مقفلة تمنع ترحيل وردية مُغلقة، أو نص فارغ إذا لا مانع. */
    public String blockingPeriod(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT DISTINCT p.period FROM period_locks p JOIN shifts s "+
                "ON p.period=substr(COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),1,7) "+
                "WHERE s.status<>'OPEN' AND NOT ("+ZERO_WORKER_POSTED+") AND NOT EXISTS(SELECT 1 FROM journal j WHERE j.source='SHIFT' AND j.source_id=s.id AND j.reversed_by=0 AND j.reverses=0) "+
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
        atomic(()->{reopenShiftAtomic(shiftId,reason);return null;});
    }
    private void reopenShiftAtomic(long shiftId,String reason){
        if(ShiftWorkspace.exists(this,shiftId))throw new IllegalStateException("الوردية المرحّلة للعرض فقط");
        requireDate(shiftDate(shiftId));
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
                "SELECT id FROM journal WHERE source='SHIFT' AND source_id=? AND reversed_by=0 AND reverses=0",
                new String[]{String.valueOf(shiftId)})){
            while(c.moveToNext())entries.add(c.getLong(0));
        }
        for(long entry:entries)reverseEntry(entry,why);

        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            db.delete("posted_shifts","shift_id=?",new String[]{String.valueOf(shiftId)});
            db.delete("cashbox_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
            db.delete("debt_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
            db.delete("expense_entries","source_shift=?",new String[]{String.valueOf(shiftId)});
            db.delete("material_entries","source_shift=? OR note LIKE ?",
                new String[]{String.valueOf(shiftId),"وردية #"+shiftId+" —%"});
            ContentValues v=new ContentValues();
            v.put("historical",1);v.put("status","OPEN");v.put("closed_at","");v.put("sync_state","PENDING");
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

    /** الورديات المرحّلة إلى الأرشيف: 0=id,1=عامل,2=تاريخ,3=مبيعات,4=الباقي,5=طرمبات */
    public Cursor postedShifts(){
        return getReadableDatabase().rawQuery(
            "SELECT s.id,w.name,COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)),s.sales,s.balance,"+
            "(SELECT COUNT(*) FROM readings r WHERE r.shift_id=s.id AND r.current IS NOT NULL) "+
            "FROM shifts s JOIN workers w ON w.id=s.worker_id "+
            "WHERE s.status<>'OPEN' "+
            "ORDER BY COALESCE(NULLIF(s.shift_date,''),substr(s.opened_at,1,10)) DESC,s.id DESC",null);
    }

    /**
     * كل الأسماء المحفوظة في التطبيق مهما كان مصدرها: حركات الورديات،
     * والمخاريج، والصناديق، والمدينون. الاسم الواحد يظهر مرة واحدة.
     */
    public java.util.ArrayList<String> allNames(){
        java.util.LinkedHashSet<String> set=new java.util.LinkedHashSet<>();
        String[] queries={
            "SELECT DISTINCT name FROM remembered_names WHERE TRIM(name)<>''",
            "SELECT DISTINCT name FROM debtors WHERE TRIM(name)<>''",
            "SELECT DISTINCT category FROM expense_entries WHERE TRIM(category)<>''",
            "SELECT DISTINCT name FROM cashboxes WHERE TRIM(name)<>''"
        };
        for(String q:queries){
            try(Cursor c=getReadableDatabase().rawQuery(q,null)){
                while(c.moveToNext()){
                    String v=c.getString(0);
                    if(v!=null&&!v.trim().isEmpty())set.add(v.trim());
                }
            }catch(Exception ignored){}
        }
        java.util.ArrayList<String> names=new java.util.ArrayList<>(set);
        java.util.Collections.sort(names);
        return names;
    }

    /** يحفظ اسمًا في ذاكرة الأسماء ليُقترح لاحقًا في كل الشاشات. */
    public void rememberName(String type,String name){
        if(name==null||name.trim().isEmpty())return;
        ContentValues v=new ContentValues();
        v.put("type",type==null?"CASH":type);
        v.put("name",name.trim());
        getWritableDatabase().insertWithOnConflict("remembered_names",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** كود الوردية الفريد، يُولَّد ويُثبَّت عند أول طلب. */
    public String shiftCode(long shiftId){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(shift_code,''),COALESCE(NULLIF(shift_date,''),substr(opened_at,1,10)) "+
                "FROM shifts WHERE id=?",new String[]{String.valueOf(shiftId)})){
            if(!c.moveToFirst())return "";
            if(!c.getString(0).isEmpty())return c.getString(0);
            String code=Calc.shiftCode(deviceId(),c.getString(1),shiftId);
            ContentValues v=new ContentValues();
            v.put("shift_code",code);
            getWritableDatabase().update("shifts",v,"id=?",new String[]{String.valueOf(shiftId)});
            return code;
        }
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
        return atomic(()->addDebtorAtomic(name,phone,opening));
    }
    private long addDebtorAtomic(String name,String phone,double opening){
        if(opening!=0&&openingPosted())throw new IllegalStateException("الأرصدة الافتتاحية معتمدة؛ افتح الحساب بصفر ثم سجّل حركته.");
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
        atomic(()->{updateDebtorAtomic(id,name,phone,opening);return null;});
    }
    private void updateDebtorAtomic(long id,String name,String phone,double opening){
        if(openingPosted())try(Cursor c=getReadableDatabase().rawQuery("SELECT opening FROM debtors WHERE id=?",new String[]{String.valueOf(id)})){
            if(c.moveToFirst()&&Math.abs(c.getDouble(0)-opening)>0.000001)throw new IllegalStateException("الرصيد الافتتاحي معتمد؛ سجّل حركة تصحيح.");
        }
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
    // ==================== قفل التطبيق ====================

    /** الجلسة الحالية: فُتح القفل أم لا، ومتى غادر التطبيق. */
    private static boolean unlocked=false;
    private static long leftAt=0;
    public static void markUnlocked(){ unlocked=true; leftAt=0; }
    public static void markLeft(){ leftAt=System.currentTimeMillis(); }
    public static void forgetUnlock(){ unlocked=false; leftAt=0; }

    public boolean lockOn(){ return "1".equals(setting("lock_on","0")); }
    public void setLockOn(boolean on){
        setSetting("lock_on",on?"1":"0");
        audit("device",0,"SET_LOCK",on?"مغلق":"مفتوح",on?"مفعّل":"معطّل","قفل التطبيق");
    }

    /** ملح خاص بالجهاز لتجزئة رمز القفل. */
    private String lockSalt(){
        String v=setting("lock_salt","");
        if(v.isEmpty()){
            v=Lock.newSalt(new java.security.SecureRandom());
            setSetting("lock_salt",v);
        }
        return v;
    }

    public boolean lockPinSet(){ return !setting("lock_pin","").isEmpty(); }

    public void setLockPin(String pin){
        setSetting("lock_pin",Lock.hash(pin,lockSalt()));
        audit("device",0,"SET_LOCK_PIN","","رمز جديد","قفل التطبيق");
    }

    public boolean checkLockPin(String pin){
        return Lock.same(Lock.hash(pin,lockSalt()),setting("lock_pin",""));
    }

    /** هل يُطلب الرمز الآن؟ */
    public boolean shouldAskLock(){
        return Lock.shouldAsk(lockOn(),unlocked,leftAt,System.currentTimeMillis());
    }

    // ==================== إشعار تلغرام ====================

    public String telegramToken(){ return setting("telegram_token",""); }
    public void setTelegramToken(String token){
        setSetting("telegram_token",token==null?"":token.trim());
        audit("device",0,"SET_TELEGRAM","","رمز البوت مضبوط","إشعارات العملاء");
    }
    public boolean telegramOn(){ return "1".equals(setting("telegram_on","1")); }
    public void setTelegramOn(boolean on){ setSetting("telegram_on",on?"1":"0"); }

    /** معرّف محادثة العميل، أو نص فارغ. */
    public String debtorTelegram(long debtorId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(telegram,'') FROM debtors WHERE id=?",
                new String[]{String.valueOf(debtorId)})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public void setDebtorTelegram(long debtorId,String chatId){
        ContentValues v=new ContentValues();
        v.put("telegram",chatId==null?"":chatId.trim());
        getWritableDatabase().update("debtors",v,"id=?",new String[]{String.valueOf(debtorId)});
        audit("debtor",debtorId,"SET_TELEGRAM","",chatId==null?"":chatId.trim(),"ربط تلغرام العميل");
    }

    /** عدد العملاء المربوطين بتلغرام. */
    public int telegramLinkedCount(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM debtors WHERE TRIM(COALESCE(telegram,''))<>''",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

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
        try(Cursor c=debtors(false)){while(c.moveToNext()){double b=c.getDouble(7);if(b>0.009)total+=b;}}
        return total;
    }
    /** مجموع ما للزبائن علينا (الأرصدة السالبة) كقيمة موجبة. */
    public double creditsTotal(){
        double total=0;
        try(Cursor c=debtors(false)){while(c.moveToNext()){double b=c.getDouble(7);if(b<-0.009)total-=b;}}
        return total;
    }
    /** Cash movement with a selected real counterpart, atomically linked for cancellation. */
    public long addCashTransaction(long boxId,String direction,double amount,String note,String date,String currency,String counterpart,long targetId){
        return atomic(()->{
            requireDate(date);requireEntity("cashboxes",boxId);
            boolean incoming="IN".equals(direction);
            if(!incoming&&!"OUT".equals(direction))throw new IllegalArgumentException("نوع الحركة غير صحيح");
            if(!java.util.Arrays.asList("SALE","EXPENSE","CUSTOMER","TRANSFER").contains(counterpart))throw new IllegalArgumentException("اختر الحساب المقابل");
            if("SALE".equals(counterpart)&&!incoming)throw new IllegalArgumentException("المبيعات النقدية حركة واردة؛ اختر العملية الصحيحة");
            if("EXPENSE".equals(counterpart)&&incoming)throw new IllegalArgumentException("المصروف حركة صادرة؛ اختر العملية الصحيحة");
            if("CUSTOMER".equals(counterpart))requireEntity("debtors",targetId);
            if("TRANSFER".equals(counterpart)){
                requireEntity("cashboxes",targetId);
                if(targetId==boxId)throw new IllegalArgumentException("اختر صندوقًا آخر");
            }
            if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("المبلغ غير صحيح");
            String code=currency==null?"YER":currency;
            double yer=toYer(amount,code);
            if(!Double.isFinite(yer)||yer<=0)throw new IllegalArgumentException("سعر الصرف غير صحيح");
            String label=note==null?"":note.trim();
            if(label.isEmpty())throw new IllegalArgumentException("اكتب البيان أو بند المصروف");
            long cashId,linkedCash=0,debtId=0,expenseId=0;
            boolean prior=suppressJournal;suppressJournal=true;
            try{
                cashId=addCashboxEntry(boxId,direction,amount,label,date,code);
                ContentValues primary=new ContentValues();primary.put("managed",0);
                getWritableDatabase().update("cashbox_entries",primary,"id=?",new String[]{String.valueOf(cashId)});
                if("CUSTOMER".equals(counterpart))debtId=addDebtEntry(targetId,incoming?"PAID":"DEBT",yer,label,date);
                if("TRANSFER".equals(counterpart))linkedCash=addCashboxEntry(targetId,incoming?"OUT":"IN",amount,label,date,code);
                if("EXPENSE".equals(counterpart))expenseId=addExpense(label,yer,label,date,0,0);
            }finally{suppressJournal=prior;}
            String other="SALE".equals(counterpart)?Journal.SALES:"EXPENSE".equals(counterpart)?Journal.EXPENSE:"CUSTOMER".equals(counterpart)?Journal.RECEIVABLE:Journal.CASH;
            String fromName="",targetName=label;
            try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM cashboxes WHERE id=?",new String[]{String.valueOf(boxId)})){if(c.moveToFirst())fromName=c.getString(0);}
            if("CUSTOMER".equals(counterpart))targetName=debtorName(targetId);
            if("TRANSFER".equals(counterpart))try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM cashboxes WHERE id=?",new String[]{String.valueOf(targetId)})){if(c.moveToFirst())targetName=c.getString(0);}
            Journal.Entry entry=new Journal.Entry(label,date,"CASH_EXPLICIT",cashId);
            if(incoming){entry.debit(Journal.CASH,yer,fromName);entry.credit(other,yer,targetName);}
            else{entry.debit(other,yer,targetName);entry.credit(Journal.CASH,yer,fromName);}
            long journalId=postEntry(entry);
            if(linkedCash>0||debtId>0||expenseId>0){
                ContentValues link=new ContentValues();link.put("entry_id",journalId);link.put("cashbox_entry",linkedCash);link.put("debt_entry",debtId);link.put("expense_entry",expenseId);
                getWritableDatabase().insertOrThrow("settlement_links",null,link);
            }
            return cashId;
        });
    }
    private boolean explicitCashEntry(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM journal WHERE source='CASH_EXPLICIT' AND source_id=? AND reversed_by=0 AND reverses=0",new String[]{String.valueOf(id)})){return c.moveToFirst();}
    }

    /** Explicit customer transaction: no suspense account and both ledgers commit together. */
    public long addCustomerTransaction(long debtorId,String operation,long boxId,double amount,String note,String date){
        return atomic(()->{
            requireDate(date);requireEntity("debtors",debtorId);
            if(!java.util.Arrays.asList("CREDIT_SALE","COLLECTION","CASH_LOAN").contains(operation))
                throw new IllegalArgumentException("اختر نوع العملية");
            if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا صحيحًا أكبر من صفر");
            boolean collection="COLLECTION".equals(operation);
            boolean usesCash=!"CREDIT_SALE".equals(operation);
            if(usesCash)requireEntity("cashboxes",boxId);
            String direction=collection?"PAID":"DEBT";
            String label=("CREDIT_SALE".equals(operation)?"بيع آجل خارج الورديات":collection?"تحصيل دين":"سلفة نقدية")+" — "+debtorName(debtorId);
            if(note!=null&&!note.trim().isEmpty())label+=" — "+note.trim();
            long debtId,cashId=0;
            boolean prior=suppressJournal;suppressJournal=true;
            try{
                debtId=addDebtEntry(debtorId,direction,amount,label,date);
                ContentValues primary=new ContentValues();primary.put("managed",0);
                getWritableDatabase().update("debt_entries",primary,"id=?",new String[]{String.valueOf(debtId)});
                if(usesCash)cashId=addCashboxEntry(boxId,collection?"IN":"OUT",amount,label,date);
            }finally{suppressJournal=prior;}
            String debit=collection?Journal.CASH:Journal.RECEIVABLE;
            String credit=collection?Journal.RECEIVABLE:usesCash?Journal.CASH:Journal.SALES;
            long entry=postEntry(Journal.simple(label,date,"DEBT_EXPLICIT",debtId,debit,credit,amount,debtorName(debtorId)));
            if(cashId>0){
                ContentValues link=new ContentValues();link.put("entry_id",entry);link.put("cashbox_entry",cashId);
                getWritableDatabase().insertOrThrow("settlement_links",null,link);
            }
            return debtId;
        });
    }
    private boolean explicitCustomerEntry(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM journal WHERE source='DEBT_EXPLICIT' AND source_id=? AND reversed_by=0 AND reverses=0",new String[]{String.valueOf(id)})){return c.moveToFirst();}
    }

    public long addDebtEntry(long debtorId,String direction,double amount,String note,String date){
        return atomic(()->addDebtEntryAtomic(debtorId,direction,amount,note,date));
    }
    private long addDebtEntryAtomic(long debtorId,String direction,double amount,String note,String date){
        return addDebtEntry(debtorId,direction,amount,note,date,0);
    }
    public long addDebtEntry(long debtorId,String direction,double amount,String note,String date,long sourceShift){
        return atomic(()->addDebtEntryAtomic(debtorId,direction,amount,note,date,sourceShift));
    }
    private long addDebtEntryAtomic(long debtorId,String direction,double amount,String note,String date,long sourceShift){
        requireDate(date);requireEntity("debtors",debtorId);
        if(!"DEBT".equals(direction)&&!"PAID".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        ContentValues v=new ContentValues();
        v.put("debtor_id",debtorId);v.put("direction",direction);v.put("amount",amount);
        v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());v.put("source_shift",sourceShift);v.put("managed",suppressJournal?1:0);
        long id=getWritableDatabase().insertOrThrow("debt_entries",null,v);
        afterCommit(()->notifyDebtor(debtorId,"DEBT".equals(direction),amount,note,date));
        if(sourceShift==0){
            String who=debtorName(debtorId);
            // الدين يزيد ذمة المدين، والسداد ينقصها.
            journalManual("debt",id,date,("DEBT".equals(direction)?"دين على ":"سداد من ")+who,amount,
                    "DEBT".equals(direction)?Journal.RECEIVABLE:Journal.SUSPENSE,
                    "DEBT".equals(direction)?Journal.SUSPENSE:Journal.RECEIVABLE);
        }
        return id;
    }

    /**
     * يرسل إشعار تلغرام للعميل بعد تسجيل الحركة.
     * الإرسال في خيط منفصل، وأي فشل لا يمسّ الحركة المحفوظة.
     */
    void notifyDebtor(final long debtorId,final boolean debt,final double amount,
                      final String note,final String date){
        try{
            if(!telegramOn())return;
            final String token=telegramToken();
            final String chat=debtorTelegram(debtorId);
            if(token.isEmpty()||chat.trim().isEmpty())return;
            final String name=debtorName(debtorId);
            final double balance=debtorBalance(debtorId);
            final String station=Branding.stationName(this);
            final String when=date==null||date.trim().isEmpty()?ShiftDates.today():date;
            new Thread(()->{
                Telegram.send(token,chat,
                        Telegram.message(station,name,debt,amount,balance,note,when));
            },"telegram").start();
        }catch(Exception ignored){}
    }

    /** اسم المدين، أو نص فارغ. */
    public String debtorName(long debtorId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM debtors WHERE id=?",
                new String[]{String.valueOf(debtorId)})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /**
     * يقيّد حركة يدوية في الدفتر المزدوج.
     * الطرف المقابل «حساب وسيط» حتى يُحدَّد سببه، فيبقى الدفتر متوازنًا دائمًا.
     */
    /**
     * يقيّد الحركات اليدوية القديمة التي سُجّلت قبل تفعيل القيد المزدوج.
     * يعيد عدد ما قُيّد.
     */
    public int journalManualBacklog(){
        throw new IllegalStateException("الحركات القديمة تحتاج مراجعة مصادرها قبل الترحيل؛ لن تُنشأ قيود تقديرية تلقائيًا.");
    }

    /** يعكس قيد حركة يدوية عند حذفها، فلا يبقى أثر بلا مقابل. */
    void reverseManual(String source,long id){
        atomic(()->{reverseManualAtomic(source,id);return null;});
    }
    private void reverseManualAtomic(String source,long id){
        java.util.List<Long> entries=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id FROM journal WHERE source=? AND source_id=? AND reversed_by=0 AND reverses=0",
                new String[]{source,String.valueOf(id)})){
            while(c.moveToNext())entries.add(c.getLong(0));
        }
        for(long entry:entries)reverseEntry(entry,"تصحيح الحركة");
    }
    private boolean suppressJournal=false;
    void journalManual(String source,long id,String date,String memo,double amount,
                       String debitAccount,String creditAccount){
        if(suppressJournal)return;
        requireDate(date);
        String label=memo==null||memo.trim().isEmpty()?source:memo.trim();
        postEntry(Journal.simple(label,date,source.toUpperCase(java.util.Locale.US),id,
                debitAccount,creditAccount,amount,label));
    }
    public boolean deleteDebtEntry(long id){
        return atomic(()->deleteDebtEntryAtomic(id));
    }
    private boolean deleteDebtEntryAtomic(long id){
        requireStandalone("debt_entries",id);
        reverseManual("DEBT",id);
        reverseManual("DEBT_EXPLICIT",id);
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

    // ==================== بداية جديدة ====================

    /**
     * يفرّغ كل الحركات ويبقي البنية والإعدادات:
     * الطرمبات وأسعارها، والصناديق وأسماؤها، والعملاء، والمواد وتكلفتها،
     * والحدود والسعات، والأسماء المحفوظة.
     * الأرصدة الافتتاحية تُصفَّر ليُعاد إدخالها، ولا تُمسّ كلمات السر.
     */
    public String freshStart(boolean keepOpenings){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            // الحركات كلها.
            for(String t:new String[]{"movements","readings","shifts","cashbox_entries",
                    "debt_entries","expense_entries","material_entries","dip_readings",
                    "supplier_entries","journal_lines","journal","posted_shifts",
                    "period_locks","ledger_audit","audit_log","shift_workspace","shift_operations","shift_counts","shift_links","settlement_links"}){
                try{db.delete(t,null,null);}catch(Exception ignored){}
            }
            if(!keepOpenings){
                db.execSQL("UPDATE cashbox_openings SET native_amount=0,yer_amount=0");
                ContentValues zero=new ContentValues();
                zero.put("opening",0);
                try{db.update("cashboxes",zero,null,null);}catch(Exception ignored){}
                try{db.update("debtors",zero,null,null);}catch(Exception ignored){}
            }
            // العدّادات تبدأ من قراءتها الحالية، فلا تُحتسب مبيعات وهمية.
            try{db.execSQL("DELETE FROM sqlite_sequence WHERE name IN "+
                "('movements','readings','shifts','cashbox_entries','debt_entries',"+
                "'expense_entries','material_entries','dip_readings','supplier_entries',"+
                "'journal','journal_lines','ledger_audit','audit_log')");}catch(Exception ignored){}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        setSetting("fresh_start_at",Util.now());
        audit("device",0,"FRESH_START","بيانات سابقة",
                keepOpenings?"بداية جديدة مع الاحتفاظ بالافتتاحيات":"بداية جديدة بأرصدة صفرية",
                "تفريغ الحركات");
        return "أُفرغت الحركات. الإعدادات والأسماء والطرمبات كما هي.";
    }

    /** ملخّص ما سيُحذف وما سيبقى، يُعرض قبل التنفيذ. */
    public String freshStartPreview(){
        int shifts=count("shifts"),cash=count("cashbox_entries"),debt=count("debt_entries"),
            exp=count("expense_entries"),mat=count("material_entries"),sup=count("supplier_entries"),
            entries=count("journal");
        int boxes=count("cashboxes"),debtors=count("debtors"),pumps=count("pumps"),names=count("remembered_names");
        return "سيُحذف:\n"
            +"• "+shifts+" وردية\n"
            +"• "+cash+" حركة صندوق\n"
            +"• "+debt+" حركة دين\n"
            +"• "+exp+" حركة مخاريج\n"
            +"• "+mat+" حركة مواد\n"
            +"• "+sup+" حركة مع شركة النفط\n"
            +"• "+entries+" قيدًا محاسبيًا\n\n"
            +"سيبقى:\n"
            +"• "+pumps+" طرمبة بأسعارها وعدّاداتها\n"
            +"• "+boxes+" صندوقًا بأسمائها\n"
            +"• "+debtors+" عميلًا بأسمائهم\n"
            +"• "+names+" اسمًا محفوظًا\n"
            +"• أسعار الشراء والبيع والتوصيل والسعات والحدود";
    }

    private int count(String table){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){
            return c.moveToFirst()?c.getInt(0):0;
        }catch(Exception e){return 0;}
    }

    /**
     * يقيّد الأرصدة الافتتاحية في الدفتر المزدوج دفعةً واحدة.
     * الصناديق والمدينون والمخزون مدينة، ورأس المال دائن.
     */
    public String postOpeningBalances(String date){
        return atomic(()->postOpeningBalancesAtomic(date));
    }
    private String postOpeningBalancesAtomic(String date){
        requireDate(date);
        if(openingPosted())throw new IllegalStateException("سبق تقييد الأرصدة الافتتاحية");
        Journal.Entry e=new Journal.Entry("أرصدة افتتاحية للصناديق والعملاء",date,"OPENING",0);
        double net=0;
        try(Cursor c=cashboxes(false)){while(c.moveToNext()){
            double v=c.getDouble(2);net+=v;
            if(v>0)e.debit(Journal.CASH,v,c.getString(1));else if(v<0)e.credit(Journal.CASH,-v,c.getString(1));
        }}
        try(Cursor c=debtors(false)){while(c.moveToNext()){
            double v=c.getDouble(3);net+=v;
            if(v>0)e.debit(Journal.RECEIVABLE,v,c.getString(1));else if(v<0)e.credit(Journal.RECEIVABLE,-v,c.getString(1));
        }}
        if(net>0)e.credit(Journal.EQUITY,net,"");else if(net<0)e.debit(Journal.EQUITY,-net,"");
        if(e.lines.isEmpty())throw new IllegalStateException("لا توجد أرصدة افتتاحية للصناديق أو العملاء");
        postEntry(e);return "قُيّدت الأرصدة الافتتاحية للصناديق والعملاء فقط: "+Calc.money(net)+" ر.ي";
    }

    /** هل سبق تقييد أرصدة افتتاحية؟ */
    public boolean openingPosted(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT 1 FROM journal WHERE source='OPENING' AND reversed_by=0 AND reverses=0 LIMIT 1",null)){
            return c.moveToFirst();
        }
    }

    // ==================== حسابات الموردين ====================

    /** الموردان: شركة النفط للبترول والديزل، وشركة الغاز للغاز. */
    public static final String[] SUPPLIERS={"OIL","GAS"};
    public static final String[] SUPPLIER_NAMES={"شركة النفط","شركة الغاز"};

    public static String supplierName(String code){
        return "GAS".equals(code)?"شركة الغاز":"شركة النفط";
    }

    /** المادة تحدّد موردها: الغاز لشركة الغاز وما عداه لشركة النفط. */
    public static String supplierOf(String material){
        return "غاز".equals(material)?"GAS":"OIL";
    }

    /** المواد التي يوردها كل مورّد. */
    public static String[] materialsOf(String supplier){
        return "GAS".equals(supplier)?new String[]{"غاز"}:new String[]{"بترول","ديزل"};
    }

    static String supplierAccount(String code){
        return "GAS".equals(code)?Journal.SUPPLIER_GAS:Journal.SUPPLIER;
    }

    /**
     * رصيد مورّد بقاعدة التطبيق الموحّدة: الموجب لنا والسالب علينا.
     * ما وُرِّد يزيد رصيدنا، والمشتريات تنقصه.
     */
    public double supplierBalance(String supplier){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN kind='PAY' THEN amount ELSE -amount END),0) "+
                "FROM supplier_entries WHERE voided=0 AND COALESCE(supplier,'OIL')=?",
                new String[]{supplier})){
            return (c.moveToFirst()?c.getDouble(0):0)+Capital.scalar(this,"SELECT COALESCE(SUM(balance),0) FROM supplier_openings WHERE supplier=?",supplier);
        }
    }

    /** رصيد الموردين جميعًا: الموجب لنا والسالب علينا. */
    public double supplierBalance(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN kind='PAY' THEN amount ELSE -amount END),0) "+
                "FROM supplier_entries WHERE voided=0",null)){
            return (c.moveToFirst()?c.getDouble(0):0)+Capital.scalar(this,"SELECT COALESCE(SUM(balance),0) FROM supplier_openings");
        }
    }

    /** ما علينا للموردين فقط (موجب)، لحساب المطلوبات. */
    public double supplierOwed(){
        return Math.max(0,-supplierBalance("OIL"))+Math.max(0,-supplierBalance("GAS"));
    }

    public double supplierBought(String supplier){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM supplier_entries "+
                "WHERE voided=0 AND kind='BUY' AND COALESCE(supplier,'OIL')=?",
                new String[]{supplier})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }

    public double supplierPaid(String supplier){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM supplier_entries "+
                "WHERE voided=0 AND kind='PAY' AND COALESCE(supplier,'OIL')=?",
                new String[]{supplier})){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }

    public double supplierBought(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM supplier_entries WHERE voided=0 AND kind='BUY'",null)){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }

    public double supplierPaid(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM supplier_entries WHERE voided=0 AND kind='PAY'",null)){
            return c.moveToFirst()?c.getDouble(0):0;
        }
    }

    /** 0=id,1=نوع,2=مادة,3=لترات,4=سعر اللتر,5=مبلغ,6=صندوق,7=بيان,8=تاريخ */
    public Cursor supplierEntries(String supplier,int limit){
        return getReadableDatabase().rawQuery(
            "SELECT e.id,e.kind,e.material,e.litres,e.unit_cost,e.amount,"+
            "COALESCE((SELECT b.name FROM cashboxes b WHERE b.id=e.box_id),''),e.note,e.entry_date "+
            "FROM supplier_entries e WHERE e.voided=0 AND COALESCE(e.supplier,'OIL')=? "+
            "ORDER BY e.entry_date DESC,e.id DESC LIMIT "+Math.max(1,limit),
            new String[]{supplier});
    }

    /**
     * شراء مواد من شركة النفط: تدخل المخزون كوارد، وقيمتها تصير دَينًا علينا.
     * القيد: مخزون الوقود مدين، وشركة النفط دائنة.
     */
    public long buyFromSupplier(String material,double litres,double unitCost,String note,String date){
        return atomic(()->buyFromSupplierAtomic(material,litres,unitCost,note,date));
    }
    private long buyFromSupplierAtomic(String material,double litres,double unitCost,String note,String date){
        requireDate(date);
        if(!Double.isFinite(litres)||litres<=0)throw new IllegalArgumentException("اكتب كمية أكبر من صفر");
        if(!Double.isFinite(unitCost)||unitCost<=0)throw new IllegalArgumentException("اكتب سعر اللتر");
        double amount=litres*unitCost;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        long id;
        try{
            // الوارد يُسجَّل في حركة المواد بلا قيد مستقل، فالقيد هنا يشمله.
            ContentValues m=new ContentValues();
            m.put("material",material);m.put("direction","IN");m.put("litres",litres);
            m.put("note","شراء من "+supplierName(supplierOf(material))+(note.trim().isEmpty()?"":" — "+note.trim()));
            m.put("entry_date",date);m.put("created_at",Util.now());
            long materialEntry=db.insertOrThrow("material_entries",null,m);

            ContentValues v=new ContentValues();
            v.put("kind","BUY");v.put("material",material);v.put("litres",litres);
            v.put("unit_cost",unitCost);v.put("amount",amount);v.put("note",note.trim());
            v.put("entry_date",date);v.put("created_at",Util.now());
            v.put("material_entry",materialEntry);
            v.put("supplier",supplierOf(material));
            id=db.insertOrThrow("supplier_entries",null,v);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        journalManual("supplier",id,date,
                "شراء "+Calc.money(litres)+" لتر "+material+" من "+supplierName(supplierOf(material)),amount,
                Journal.INVENTORY,supplierAccount(supplierOf(material)));
        audit("supplier",id,"BUY_FUEL","",Calc.money(litres)+" لتر "+material+" بـ "+Calc.money(amount),note);
        return id;
    }

    /**
     * توريد مبلغ لشركة النفط من أحد الصناديق.
     * القيد: شركة النفط مدينة، والصندوق دائن.
     */
    /**
     * ينقل رصيد حساب من الديون إلى حساب مورّد.
     * يُستعمل حين يكون الحساب مورّدًا سُجّل خطأً في الديون.
     * القيد: حساب المورّد مدين، وذمم المدينين دائنة.
     */
    public long moveDebtorToSupplier(long debtorId,String supplier,String note,String date){
        return atomic(()->moveDebtorToSupplierAtomic(debtorId,supplier,note,date));
    }
    private long moveDebtorToSupplierAtomic(long debtorId,String supplier,String note,String date){
        requireDate(date);
        double balance=debtorBalance(debtorId);
        double amount=Math.abs(balance);
        if(amount<0.01)throw new IllegalStateException("رصيد الحساب صفر، لا شيء يُنقل");
        final String who=debtorName(debtorId);
        final String to=supplierName(supplier);
        // الرصيد الدائن يعني أنّ الحساب دائن لنا: يصير دَينًا على المورّد.
        final boolean credit=balance<0;
        String label=(note==null||note.trim().isEmpty())
                ?"نقل رصيد "+who+" إلى "+to:note.trim();

        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        long id;long debtId;
        try{
            // قيد معاكس في الديون يصفّر الحساب هناك بلا حذف.
            suppressJournal=true;
            try{
                debtId=addDebtEntry(debtorId,credit?"DEBT":"PAID",amount,
                        "نقل الرصيد إلى حساب "+to,date);
            }finally{suppressJournal=false;}

            ContentValues v=new ContentValues();
            v.put("kind",credit?"BUY":"PAY");v.put("debt_entry",debtId);
            v.put("amount",amount);v.put("note",label);
            v.put("entry_date",date);v.put("created_at",Util.now());
            v.put("supplier",supplier);
            id=db.insertOrThrow("supplier_entries",null,v);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}

        journalManual("supplier",id,date,label,amount,
                credit?Journal.RECEIVABLE:supplierAccount(supplier),
                credit?supplierAccount(supplier):Journal.RECEIVABLE);
        audit("supplier",id,"MOVE_TO_SUPPLIER",who+" — "+Calc.money(balance),
                to+" — "+Calc.money(amount),"نقل رصيد من الديون إلى الموردين");
        return id;
    }

    /**
     * سداد عيني للمورّد: تُردّ لترات بدل النقد.
     * تُخصم من المخزون ومن دَين المورّد بقيمتها.
     * القيد: المورّد مدين، ومخزون الوقود دائن.
     */
    public long paySupplierInKind(String supplier,String material,double litres,
                                  double unitCost,String note,String date){
        return atomic(()->paySupplierInKindAtomic(supplier,material,litres,unitCost,note,date));
    }
    private long paySupplierInKindAtomic(String supplier,String material,double litres,
                                  double unitCost,String note,String date){
        if(!Double.isFinite(litres)||litres<=0)throw new IllegalArgumentException("اكتب كمية أكبر من صفر");
        if(!Double.isFinite(unitCost)||unitCost<=0)throw new IllegalArgumentException("اكتب سعر اللتر");
        double amount=litres*unitCost;
        final String who=supplierName(supplier);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        long id;
        try{
            // اللترات تخرج من المخزون بلا قيد مستقل؛ القيد أدناه يشملها.
            ContentValues m=new ContentValues();
            m.put("material",material);m.put("direction","OUT");m.put("litres",litres);
            m.put("note","سداد عيني لـ"+who+(note.trim().isEmpty()?"":" — "+note.trim()));
            m.put("entry_date",date);m.put("created_at",Util.now());
            long materialEntry=db.insertOrThrow("material_entries",null,m);

            ContentValues v=new ContentValues();
            v.put("kind","PAY");v.put("material",material);v.put("litres",litres);
            v.put("unit_cost",unitCost);v.put("amount",amount);v.put("note",note.trim());
            v.put("entry_date",date);v.put("created_at",Util.now());
            v.put("material_entry",materialEntry);
            v.put("supplier",supplier);
            id=db.insertOrThrow("supplier_entries",null,v);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}

        journalManual("supplier",id,date,
                "سداد "+Calc.money(litres)+" لتر "+material+" لـ"+who,amount,
                supplierAccount(supplier),Journal.INVENTORY);
        audit("supplier",id,"PAY_IN_KIND",who,
                Calc.money(litres)+" لتر "+material+" بـ "+Calc.money(amount),note);
        return id;
    }

    /** حسابات الديون التي لها رصيد، لاختيار ما يُنقل إلى المورّدين. */
    public Cursor debtorsWithBalance(){
        return getReadableDatabase().rawQuery(
            "SELECT d.id,d.name,"+
            "d.opening+COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='DEBT'),0)"+
            "-COALESCE((SELECT SUM(e.amount) FROM debt_entries e WHERE e.debtor_id=d.id AND e.direction='PAID'),0) AS bal "+
            "FROM debtors d WHERE ABS(bal)>=0.01 ORDER BY d.name",null);
    }

    public long paySupplier(String supplier,long boxId,double amount,String note,String date){
        return atomic(()->paySupplierAtomic(supplier,boxId,amount,note,date));
    }
    private long paySupplierAtomic(String supplier,long boxId,double amount,String note,String date){
        requireDate(date);
        if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("اكتب مبلغًا أكبر من صفر");
        if(boxId<=0)throw new IllegalArgumentException("اختر الصندوق");
        final String who=supplierName(supplier);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        long id;
        try{
            suppressJournal=true;
            long cashEntry;
            try{
                cashEntry=addCashboxEntry(boxId,"OUT",amount,
                        "توريد لـ"+who+(note.trim().isEmpty()?"":" — "+note.trim()),date);
            }finally{suppressJournal=false;}

            ContentValues v=new ContentValues();
            v.put("kind","PAY");v.put("amount",amount);v.put("box_id",boxId);
            v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());
            v.put("cashbox_entry",cashEntry);
            v.put("supplier",supplier);
            id=db.insertOrThrow("supplier_entries",null,v);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        journalManual("supplier",id,date,"توريد لـ"+who,amount,
                supplierAccount(supplier),Journal.CASH);
        audit("supplier",id,"PAY_SUPPLIER",who,Calc.money(amount)+" ر.ي",note);
        return id;
    }

    /** يلغي حركة مورّد بعكس قيدها وإزالة أثرها، ويبقى السجل محفوظًا. */
    public void voidSupplierEntry(long id){
        atomic(()->{voidSupplierEntryAtomic(id);return null;});
    }
    private void voidSupplierEntryAtomic(long id){
        ShiftWorkspace.immutable(this,"supplier_entries",id);
        String kind="";long materialEntry=0,cashEntry=0,debtEntry=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT kind,material_entry,cashbox_entry,debt_entry,entry_date FROM supplier_entries WHERE id=? AND voided=0",
                new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())throw new IllegalStateException("الحركة غير موجودة");
            kind=c.getString(0);materialEntry=c.getLong(1);cashEntry=c.getLong(2);debtEntry=c.getLong(3);requireDate(c.getString(4));
            if(materialEntry==0&&cashEntry==0&&debtEntry==0)throw new IllegalStateException("حركة مورّد قديمة غير مربوطة بمصدرها؛ يلزم مراجعتها قبل الإلغاء.");
        }
        reverseManual("SUPPLIER",id);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            if(debtEntry>0)db.delete("debt_entries","id=?",new String[]{String.valueOf(debtEntry)});
            if(materialEntry>0)db.delete("material_entries","id=?",new String[]{String.valueOf(materialEntry)});
            if(cashEntry>0)db.delete("cashbox_entries","id=?",new String[]{String.valueOf(cashEntry)});
            ContentValues v=new ContentValues();v.put("voided",1);
            db.update("supplier_entries",v,"id=?",new String[]{String.valueOf(id)});
            audit(db,"supplier",id,"VOID_SUPPLIER",kind,"مُلغاة","إلغاء حركة مورّد");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    // ==================== تكلفة اللتر ====================

    /** سعر شراء اللتر من المورّد. */
    public double buyPrice(String material){
        try{return Double.parseDouble(setting("buy_"+material,"0"));}catch(Exception e){return 0;}
    }
    public void setBuyPrice(String material,double value){
        setSetting("buy_"+material,String.valueOf(value));
        audit("material",0,"SET_BUY_PRICE",material,Calc.money(value)+" ريال/لتر","سعر الشراء");
    }

    /** أجرة التوصيل لكل لتر. */
    public double freightPrice(String material){
        try{return Double.parseDouble(setting("freight_"+material,"0"));}catch(Exception e){return 0;}
    }
    public void setFreightPrice(String material,double value){
        setSetting("freight_"+material,String.valueOf(value));
        audit("material",0,"SET_FREIGHT",material,Calc.money(value)+" ريال/لتر","أجرة التوصيل");
    }

    /** تكلفة اللتر الكاملة: الشراء زائد التوصيل. */
    public double unitCost(String material){
        return buyPrice(material)+freightPrice(material);
    }

    /** قيمة مخزون مادة بسعر التكلفة. */
    public double stockValue(String material){
        return Capital.enabled(this)?materialSummary(material)[3]*Capital.cost(this,material):Math.max(0,materialSummary(material)[3])*unitCost(material);
    }

    /** قيمة كل المخزون بسعر التكلفة. */
    public double stockValueTotal(){
        double total=Capital.external(this);
        for(String m:MATERIALS)total+=stockValue(m);
        return total;
    }

    /** ربح اللتر المتوقّع: سعر البيع ناقص التكلفة. */
    public double unitMargin(String material){
        double sell=priceFor(material);
        double cost=unitCost(material);
        return sell>0&&cost>0?sell-cost:0;
    }

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
        return atomic(()->addMaterialEntryAtomic(material,direction,litres,note,date));
    }
    private long addMaterialEntryAtomic(String material,String direction,double litres,String note,String date){
        requireDate(date);
        if(!"IN".equals(direction)&&!"OUT".equals(direction))throw new IllegalArgumentException("نوع الحركة غير معروف");
        if(!Double.isFinite(litres)||litres<=0)throw new IllegalArgumentException("اكتب كمية أكبر من صفر");
        ContentValues v=new ContentValues();
        v.put("material",material);v.put("direction",direction);v.put("litres",litres);
        v.put("note",note.trim());v.put("entry_date",date);v.put("created_at",Util.now());
        return getWritableDatabase().insertOrThrow("material_entries",null,v);
    }
    public boolean deleteMaterialEntry(long id){
        return atomic(()->deleteMaterialEntryAtomic(id));
    }
    private boolean deleteMaterialEntryAtomic(long id){
        requireStandalone("material_entries",id);
        return getWritableDatabase().delete("material_entries","id=?",new String[]{String.valueOf(id)})==1;
    }
    // ==================== مطابقة العجز بالمقياس ====================

    /** يسجّل قياس خزان يدويًا ويصحّح المخزون بحركة فرق، كل ذلك في معاملة واحدة. */
    public long recordDip(String material,double measured,String reason,String date){
        return atomic(()->recordDipAtomic(material,measured,reason,date));
    }
    private long recordDipAtomic(String material,double measured,String reason,String date){
        requireDate(date);
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


