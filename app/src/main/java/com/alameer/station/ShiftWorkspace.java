package com.alameer.station.shifts;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Durable staging area. Only Db.closeAndPostShift may publish its entries. */
public final class ShiftWorkspace {
    static void create(SQLiteDatabase s){
        s.execSQL("CREATE TABLE IF NOT EXISTS shift_workspace(shift_id INTEGER PRIMARY KEY,cashbox_id INTEGER NOT NULL DEFAULT 0,reviewed INTEGER NOT NULL DEFAULT 0)");
        s.execSQL("CREATE TABLE IF NOT EXISTS shift_operations(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id INTEGER NOT NULL,section INTEGER NOT NULL,kind TEXT NOT NULL,box_id INTEGER NOT NULL DEFAULT 0,target_id INTEGER NOT NULL DEFAULT 0,material TEXT NOT NULL DEFAULT '',quantity REAL NOT NULL DEFAULT 0,amount REAL NOT NULL,note TEXT NOT NULL,posted INTEGER NOT NULL DEFAULT 0)");
        s.execSQL("CREATE TABLE IF NOT EXISTS shift_links(shift_id INTEGER NOT NULL,entity TEXT NOT NULL,row_id INTEGER NOT NULL,PRIMARY KEY(entity,row_id))");
        extend(s);
        // Worker edits invalidate all dependent reviews; cash edits also invalidate materials.
        for(String table:new String[]{"readings","movements","shift_operations"}){
            for(String event:new String[]{"INSERT","UPDATE","DELETE"}){
                String ref=event.equals("DELETE")?"OLD":"NEW";
                String when=table.equals("readings")&&event.equals("UPDATE")?" WHEN OLD.current IS NOT NEW.current OR OLD.previous IS NOT NEW.previous OR OLD.price IS NOT NEW.price":"";
                if(table.equals("shift_operations")&&event.equals("UPDATE"))when=" WHEN OLD.kind IS NOT NEW.kind OR OLD.box_id IS NOT NEW.box_id OR OLD.target_id IS NOT NEW.target_id OR OLD.material IS NOT NEW.material OR OLD.quantity IS NOT NEW.quantity OR OLD.amount IS NOT NEW.amount OR OLD.note IS NOT NEW.note OR OLD.freight IS NOT NEW.freight OR OLD.rate IS NOT NEW.rate OR OLD.target_rate IS NOT NEW.target_rate OR OLD.driver_name IS NOT NEW.driver_name";
                String mask=table.equals("shift_operations")?"6":"7";
                s.execSQL("DROP TRIGGER IF EXISTS review_"+table+"_"+event);
                s.execSQL("CREATE TRIGGER review_"+table+"_"+event+" AFTER "+event+" ON "+table+when+" BEGIN UPDATE shift_workspace SET reviewed=reviewed & ~"+mask+" WHERE shift_id="+ref+".shift_id; END");
            }
        }
    }
    static boolean exists(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM shift_workspace WHERE shift_id=?",new String[]{""+id})){return c.moveToFirst();}}
    static void ensure(Db db,long id){if(!db.isOpen(id))return;ContentValues v=new ContentValues();v.put("shift_id",id);v.put("cashbox_id",db.defaultCashbox());v.put("strict_counts",1);v.put("cashbox_rate",db.rate(boxCurrency(db,db.defaultCashbox())));db.getWritableDatabase().insertWithOnConflict("shift_workspace",null,v,SQLiteDatabase.CONFLICT_IGNORE);db.shiftCode(id);}
    static void openOnly(Db db,long id){if(!db.isOpen(id)||!exists(db,id))throw new IllegalStateException("افتح وردية جديدة أولًا؛ الدفاتر للعرض فقط");}
    static long box(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_id FROM shift_workspace WHERE shift_id=?",new String[]{""+id})){return c.moveToFirst()?c.getLong(0):db.defaultCashbox();}}
    static int reviewed(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT reviewed FROM shift_workspace WHERE shift_id=?",new String[]{""+id})){return c.moveToFirst()?c.getInt(0):0;}}
    static void selectBox(Db db,long id,long box){openOnly(db,id);db.getWritableDatabase().execSQL("UPDATE shift_workspace SET cashbox_id=?,cashbox_rate=?,reviewed=reviewed & ~6 WHERE shift_id=?",new Object[]{box,db.rate(boxCurrency(db,box)),id});}
    static void review(Db db,long id,int section){openOnly(db,id);if(section<0||section>2)throw new IllegalArgumentException();
        if(section==0){String issue=db.validateShift(id);if(!issue.isEmpty()||Math.abs(db.balance(id))>0.0000001)throw new IllegalStateException(issue.isEmpty()?"يجب تصفير فرق العامل":issue);}
        if(section==1&&(reviewed(db,id)&1)==0)throw new IllegalStateException("أكمل مطابقة العامل أولًا");
        if(section==2&&(reviewed(db,id)&3)!=3)throw new IllegalStateException("أكمل مطابقة الصناديق أولًا");
        if(section>0)validateCounts(db,id,section);db.getWritableDatabase().execSQL("UPDATE shift_workspace SET reviewed=reviewed|? WHERE shift_id=?",new Object[]{1<<section,id});}
    static void ready(Db db,long id){if(exists(db,id)){validateCounts(db,id,1);validateCounts(db,id,2);}if(exists(db,id)&&reviewed(db,id)!=7)throw new IllegalStateException("راجع وأكد التبويبات الثلاثة: مطابقة العامل، الصناديق، المواد. أكد عدم وجود حركة إن كان التبويب فارغًا.");}
    static Cursor operations(Db db,long id,int section){return db.getReadableDatabase().rawQuery("SELECT id,kind,box_id,target_id,material,quantity,amount,note,posted FROM shift_operations WHERE shift_id=? AND section=? ORDER BY id",new String[]{""+id,""+section});}
    static long add(Db db,long id,int section,String kind,long box,long target,String material,double quantity,double amount,String note){
        openOnly(db,id);
        if(!Double.isFinite(amount)||amount<=0||note==null||note.trim().isEmpty())throw new IllegalArgumentException("أدخل البيان والمبلغ الصحيح");
        if(section==1){
            if(!Arrays.asList("EXPENSE","COLLECTION","LOAN","TRANSFER","SUPPLIER").contains(kind)||box<=0)throw new IllegalArgumentException("اختر الصندوق ونوع الحركة");
            if(Arrays.asList("COLLECTION","LOAN","TRANSFER").contains(kind)&&target<=0)throw new IllegalArgumentException("اختر الحساب المقابل");
            if(kind.equals("TRANSFER")&&box==target)throw new IllegalArgumentException("اختر صندوقًا آخر");
        }else if(section==2){
            if(!Arrays.asList(Db.MATERIALS).contains(material)||!Double.isFinite(quantity)||quantity<=0||!Arrays.asList("BUY_CREDIT","BUY_CASH").contains(kind))throw new IllegalArgumentException("أدخل المادة والكمية ونوع الحركة");
            if(kind.equals("BUY_CASH")&&box<=0)throw new IllegalArgumentException("اختر صندوق الدفع");
        }else throw new IllegalArgumentException("التبويب غير صحيح");
        ContentValues v=new ContentValues();v.put("shift_id",id);v.put("section",section);v.put("kind",kind);v.put("box_id",box);v.put("target_id",target);v.put("material",material);v.put("quantity",quantity);v.put("amount",amount);v.put("note",note.trim());
        return db.getWritableDatabase().insertOrThrow("shift_operations",null,v);
    }
    static void delete(Db db,long shift,long id){openOnly(db,shift);db.getWritableDatabase().delete("shift_operations","id=? AND shift_id=? AND posted=0",new String[]{""+id,""+shift});}
    static String label(String k){switch(k){case "EXPENSE":return "مصروف";case "COLLECTION":return "تحصيل عميل";case "LOAN":return "سلفة عميل";case "TRANSFER":return "تحويل بين صندوقين";case "SUPPLIER":return "سداد مورد";case "BUY_CREDIT":return "توريد آجل";case "BUY_CASH":return "توريد نقدي";case "LOSS":return "حركة سابقة للمواد";case "FUEL_SUPPLY":return "توريد مواد من الشركة";case "COMPANY_PAYMENT":return "توريد مبلغ للشركة";default:return k;}}
    static final String[] LEDGERS={"cashbox_entries","debt_entries","expense_entries","material_entries","supplier_entries","journal"};
    static Map<String,Long> before(Db db){Map<String,Long> m=new HashMap<>();for(String t:LEDGERS)try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(MAX(id),0) FROM "+t,null)){c.moveToFirst();m.put(t,c.getLong(0));}return m;}
    static void link(Db db,long id,Map<String,Long> before){
        for(String t:LEDGERS){
            db.getWritableDatabase().execSQL("INSERT INTO shift_links(shift_id,entity,row_id) SELECT ?,?,id FROM "+t+" WHERE id>?",new Object[]{id,t,before.get(t)});
            if(!t.equals("journal")&&!t.equals("supplier_entries"))db.getWritableDatabase().execSQL("UPDATE "+t+" SET source_shift=? WHERE id>?",new Object[]{id,before.get(t)});
        }
    }
    static void immutable(Db db,String table,long row){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM shift_links WHERE entity=? AND row_id=?",new String[]{table,""+row})){if(c.moveToFirst())throw new IllegalStateException("حركة مرحّلة من وردية؛ الدفاتر للعرض فقط ولا يمكن تعديلها أو حذفها");}}
    static void post(Db db,long id){
        String date=db.shiftDate(id),code=db.shiftCode(id);
        for(int section=1;section<=2;section++)try(Cursor c=operations(db,id,section)){
            while(c.moveToNext()){
                if(c.getInt(8)!=0)throw new IllegalStateException("هذه الحركة مرحّلة مسبقًا");
                String k=c.getString(1),mat=c.getString(4),note=code+" — "+c.getString(7);long box=c.getLong(2),target=c.getLong(3);double q=c.getDouble(5),a=c.getDouble(6);
                long watermark=0;try(Cursor cash=db.getReadableDatabase().rawQuery("SELECT COALESCE(MAX(id),0) FROM cashbox_entries",null)){cash.moveToFirst();watermark=cash.getLong(0);}
                if(k.equals("COMPANY_PAYMENT")||k.equals("SUPPLIER"))db.paySupplier(target==1?"GAS":"OIL",box,a,note,date);
                else if(k.equals("FUEL_SUPPLY")){
                    db.buyFromSupplier(mat,q,a/q,note,date);
                    try(Cursor detail=db.getReadableDatabase().rawQuery("SELECT driver_name,freight FROM shift_operations WHERE id=?",new String[]{""+c.getLong(0)})){
                        detail.moveToFirst();double freight=detail.getDouble(1);
                        if(freight>0){long driver=customer(db,detail.getString(0));db.addDebtEntry(driver,"PAID",freight,"أجرة نقل — "+note,date,id);
                            db.postEntry(Journal.simple("أجرة نقل — "+note,date,"SHIFT_FREIGHT",id,Journal.INVENTORY,Journal.RECEIVABLE,freight,detail.getString(0)));}
                    }
                }else if(section==1){
                    db.addCashTransaction(box,k.equals("COLLECTION")?"IN":"OUT",a,note,date,"YER",k.equals("COLLECTION")||k.equals("LOAN")?"CUSTOMER":k,target);
                }else if(k.equals("BUY_CREDIT"))db.buyFromSupplier(mat,q,a/q,note,date);
                else {
                    db.addMaterialEntry(mat,k.equals("LOSS")?"OUT":"IN",q,note,date);
                    if(k.equals("BUY_CASH"))db.addCashboxEntry(box,"OUT",a,note,date,id);
                    db.postEntry(Journal.simple(note,date,"SHIFT_MATERIAL",id,k.equals("LOSS")?Journal.EXPENSE:Journal.INVENTORY,k.equals("LOSS")?Journal.INVENTORY:Journal.CASH,a,mat));
                }
                try(Cursor fx=db.getReadableDatabase().rawQuery("SELECT rate,target_rate FROM shift_operations WHERE id=?",new String[]{""+c.getLong(0)})){fx.moveToFirst();convertCashMetadata(db,watermark,box,fx.getDouble(0));if(k.equals("TRANSFER"))convertCashMetadata(db,watermark,target,fx.getDouble(1));}
                db.getWritableDatabase().execSQL("UPDATE shift_operations SET posted=1 WHERE id=?",new Object[]{c.getLong(0)});
            }
        }
    }

    private static void column(SQLiteDatabase s,String table,String name,String declaration){
        boolean found=false;try(Cursor c=s.rawQuery("PRAGMA table_info("+table+")",null)){while(c.moveToNext())found|=name.equals(c.getString(1));}
        if(!found)s.execSQL("ALTER TABLE "+table+" ADD COLUMN "+name+" "+declaration);
    }
    static void extend(SQLiteDatabase s){
        column(s,"shift_workspace","strict_counts","INTEGER NOT NULL DEFAULT 0");
        column(s,"shift_workspace","cashbox_rate","REAL NOT NULL DEFAULT 1");
        column(s,"shift_operations","driver_name","TEXT NOT NULL DEFAULT ''");
        column(s,"shift_operations","freight","REAL NOT NULL DEFAULT 0");
        column(s,"shift_operations","rate","REAL NOT NULL DEFAULT 1");
        column(s,"shift_operations","target_rate","REAL NOT NULL DEFAULT 1");
        s.execSQL("CREATE TABLE IF NOT EXISTS cashbox_currency(box_id INTEGER PRIMARY KEY,code TEXT NOT NULL,opening_rate REAL NOT NULL)");
        s.execSQL("CREATE TABLE IF NOT EXISTS shift_counts(shift_id INTEGER NOT NULL,section INTEGER NOT NULL,account TEXT NOT NULL,actual REAL NOT NULL,expected REAL NOT NULL,PRIMARY KEY(shift_id,section,account))");
    }
    static long addBox(Db db,String name,String code,double nativeOpening){
        if(!Arrays.asList(Db.CURRENCIES).contains(code)||!Double.isFinite(nativeOpening)||nativeOpening<0)throw new IllegalArgumentException("راجع العملة والرصيد");
        SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{double rate=db.rate(code);long id=db.addCashbox(name,nativeOpening*rate);s.execSQL("INSERT INTO cashbox_currency(box_id,code,opening_rate) VALUES(?,?,?)",new Object[]{id,code,rate});if(db.defaultCashbox()==0)db.setDefaultCashbox(id);s.execSQL("UPDATE shift_workspace SET reviewed=reviewed & ~6 WHERE shift_id IN (SELECT id FROM shifts WHERE status='OPEN')");s.setTransactionSuccessful();return id;}finally{s.endTransaction();}
    }
    static String boxCurrency(Db db,long box){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT code FROM cashbox_currency WHERE box_id=?",new String[]{""+box})){return c.moveToFirst()?c.getString(0):"YER";}}
    static double openingRate(Db db,long box){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT opening_rate FROM cashbox_currency WHERE box_id=?",new String[]{""+box})){return c.moveToFirst()?c.getDouble(0):1;}}
    static double nativeCash(Db db,long box){
        double value=0,rate=openingRate(db,box);String code=boxCurrency(db,box);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT opening FROM cashboxes WHERE id=?",new String[]{""+box})){if(c.moveToFirst())value=c.getDouble(0)/rate;}
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT direction,amount,currency,orig_amount FROM cashbox_entries WHERE box_id=?",new String[]{""+box})){while(c.moveToNext()){double amount=code.equals(c.getString(2))&&c.getDouble(3)!=0?c.getDouble(3):c.getDouble(1)/rate;value+=("IN".equals(c.getString(0))?1:-1)*amount;}}return value;
    }
    static double expectedCash(Db db,long shift,long box){
        double n=nativeCash(db,box);if(box==box(db,shift)){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_rate FROM shift_workspace WHERE shift_id=?",new String[]{""+shift})){c.moveToFirst();n+=db.total(shift,"CASH")/c.getDouble(0);}}
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT kind,box_id,target_id,amount,rate,target_rate FROM shift_operations WHERE shift_id=? AND posted=0",new String[]{""+shift})){while(c.moveToNext()){String k=c.getString(0);boolean money=Arrays.asList("EXPENSE","COLLECTION","LOAN","TRANSFER","SUPPLIER","COMPANY_PAYMENT","BUY_CASH").contains(k);if(money&&c.getLong(1)==box)n+=(k.equals("COLLECTION")?1:-1)*c.getDouble(3)/c.getDouble(4);if(k.equals("TRANSFER")&&c.getLong(2)==box)n+=c.getDouble(3)/c.getDouble(5);}}return n;
    }
    static double expectedMaterial(Db db,long shift,String mat){
        double n=db.materialSummary(mat)[3];
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(r.current-r.previous),0) FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND p.fuel=? AND r.current IS NOT NULL",new String[]{""+shift,mat})){c.moveToFirst();n-=c.getDouble(0);}
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT kind,quantity FROM shift_operations WHERE shift_id=? AND material=? AND posted=0",new String[]{""+shift,mat})){while(c.moveToNext())n+=(c.getString(0).equals("LOSS")?-1:1)*c.getDouble(1);}return n;
    }
    static double expectedCompany(Db db,long shift,String company){double n=db.supplierBalance(company);try(Cursor c=db.getReadableDatabase().rawQuery("SELECT kind,target_id,material,amount FROM shift_operations WHERE shift_id=? AND posted=0",new String[]{""+shift})){while(c.moveToNext()){String k=c.getString(0);if((k.equals("COMPANY_PAYMENT")||k.equals("SUPPLIER"))&&company.equals(c.getLong(1)==1?"GAS":"OIL"))n+=c.getDouble(3);if((k.equals("FUEL_SUPPLY")||k.equals("BUY_CREDIT"))&&company.equals(Db.supplierOf(c.getString(2))))n-=c.getDouble(3);}}return n;}
    static Double counted(Db db,long shift,int section,String account){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT actual FROM shift_counts WHERE shift_id=? AND section=? AND account=?",new String[]{""+shift,""+section,account})){return c.moveToFirst()?c.getDouble(0):null;}}
    static void count(Db db,long shift,int section,String account,double actual){openOnly(db,shift);if((section!=1&&section!=2)||!Double.isFinite(actual)||actual<0)throw new IllegalArgumentException("أدخل الرصيد الفعلي الصحيح");double expected=section==1?expectedCash(db,shift,Long.parseLong(account)):expectedMaterial(db,shift,account);ContentValues v=new ContentValues();v.put("shift_id",shift);v.put("section",section);v.put("account",account);v.put("actual",actual);v.put("expected",expected);db.getWritableDatabase().insertWithOnConflict("shift_counts",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.getWritableDatabase().execSQL("UPDATE shift_workspace SET reviewed=reviewed & ~? WHERE shift_id=?",new Object[]{section==1?6:4,shift});}
    static void validateCounts(Db db,long shift,int section){
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT strict_counts FROM shift_workspace WHERE shift_id=?",new String[]{""+shift})){if(!c.moveToFirst()||c.getInt(0)==0)return;}
        if(section==1){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM cashboxes WHERE active=1",null)){while(c.moveToNext())assertCount(db,shift,section,""+c.getLong(0),c.getString(1),expectedCash(db,shift,c.getLong(0)));}}
        else for(String m:Db.MATERIALS)assertCount(db,shift,section,m,m,expectedMaterial(db,shift,m));
    }
    private static void assertCount(Db db,long shift,int section,String account,String name,double expected){Double actual=counted(db,shift,section,account);if(actual==null)throw new IllegalStateException("أدخل الرصيد الفعلي: "+name);if(!Double.isFinite(expected)||Math.abs(actual-expected)>0.0000001)throw new IllegalStateException("فرق "+name+": "+(actual-expected)+" — يجب تصحيح الفرق قبل الترحيل");}
    static long customer(Db db,String name){String n=name==null?"":name.trim();if(n.isEmpty())throw new IllegalArgumentException("اكتب الاسم");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM debtors WHERE name=?",new String[]{n})){if(c.moveToFirst())return c.getLong(0);}return db.addDebtor(n,"",0);}
    static long addSupply(Db db,long shift,String mat,double q,String driver,String note){
        if(!Arrays.asList(Db.MATERIALS).contains(mat)||driver==null||driver.trim().isEmpty())throw new IllegalArgumentException("اختر المادة واكتب اسم السائق");double price=db.buyPrice(mat),freight=db.freightPrice(mat)*q;
        if(!Double.isFinite(price)||price<=0||!Double.isFinite(freight)||freight<0)throw new IllegalArgumentException("راجع أجرة النقل في الإعدادات");
        SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{long id=add(db,shift,2,"BUY_CREDIT",0,0,mat,q,q*price,driver+(note==null||note.trim().isEmpty()?"":" — "+note));s.execSQL("UPDATE shift_operations SET kind='FUEL_SUPPLY',driver_name=?,freight=? WHERE id=?",new Object[]{driver.trim(),freight,id});s.setTransactionSuccessful();return id;}finally{s.endTransaction();}
    }
    static long addCash(Db db,long shift,int section,String kind,long box,long target,double nativeAmount,String note){
        if((section==2&&!kind.equals("COMPANY_PAYMENT"))||(section==1&&!Arrays.asList("EXPENSE","COLLECTION","LOAN","TRANSFER").contains(kind))||(section!=1&&section!=2))throw new IllegalArgumentException("نوع الحركة غير صحيح");
        if(kind.equals("COMPANY_PAYMENT")&&target!=0&&target!=1)throw new IllegalArgumentException("اختر الشركة");
        double rate=db.rate(boxCurrency(db,box)),other=db.rate(boxCurrency(db,target));SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{
        long id=add(db,shift,1,kind.equals("COMPANY_PAYMENT")?"SUPPLIER":kind,box,target,"",0,nativeAmount*rate,note);
        s.execSQL("UPDATE shift_operations SET section=?,kind=?,rate=?,target_rate=? WHERE id=?",new Object[]{section,kind,rate,other,id});s.setTransactionSuccessful();return id;}finally{s.endTransaction();}
    }
    static void convertCashMetadata(Db db,long from,long box,double rate){if(box<=0)return;db.getWritableDatabase().execSQL("UPDATE cashbox_entries SET currency=?,orig_amount=amount/?,rate=? WHERE id>? AND box_id=?",new Object[]{boxCurrency(db,box),rate,rate,from,box});}
    static void convertWorkerCash(Db db,long shift,long from){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_id,cashbox_rate FROM shift_workspace WHERE shift_id=?",new String[]{""+shift})){if(c.moveToFirst())convertCashMetadata(db,from,c.getLong(0),c.getDouble(1));}}
}
