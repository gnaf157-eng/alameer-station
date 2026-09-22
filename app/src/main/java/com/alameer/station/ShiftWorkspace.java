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
        // Edits to a reviewed draft invalidate all three confirmations, including meter autosaves.
        for(String table:new String[]{"readings","movements","shift_operations"}){
            for(String event:new String[]{"INSERT","UPDATE","DELETE"}){
                String ref=event.equals("DELETE")?"OLD":"NEW";
                String when=table.equals("readings")&&event.equals("UPDATE")?" WHEN OLD.current IS NOT NEW.current OR OLD.previous IS NOT NEW.previous OR OLD.price IS NOT NEW.price":"";
                s.execSQL("CREATE TRIGGER IF NOT EXISTS review_"+table+"_"+event+" AFTER "+event+" ON "+table+when+" BEGIN UPDATE shift_workspace SET reviewed=0 WHERE shift_id="+ref+".shift_id; END");
            }
        }
    }
    static boolean exists(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM shift_workspace WHERE shift_id=?",new String[]{""+id})){return c.moveToFirst();}}
    static void ensure(Db db,long id){if(!db.isOpen(id))return;ContentValues v=new ContentValues();v.put("shift_id",id);v.put("cashbox_id",db.defaultCashbox());db.getWritableDatabase().insertWithOnConflict("shift_workspace",null,v,SQLiteDatabase.CONFLICT_IGNORE);db.shiftCode(id);}
    static void openOnly(Db db,long id){if(!db.isOpen(id)||!exists(db,id))throw new IllegalStateException("افتح وردية جديدة أولًا؛ الدفاتر للعرض فقط");}
    static long box(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_id FROM shift_workspace WHERE shift_id=?",new String[]{""+id})){return c.moveToFirst()?c.getLong(0):db.defaultCashbox();}}
    static int reviewed(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT reviewed FROM shift_workspace WHERE shift_id=?",new String[]{""+id})){return c.moveToFirst()?c.getInt(0):0;}}
    static void selectBox(Db db,long id,long box){openOnly(db,id);db.getWritableDatabase().execSQL("UPDATE shift_workspace SET cashbox_id=?,reviewed=0 WHERE shift_id=?",new Object[]{box,id});}
    static void review(Db db,long id,int section){openOnly(db,id);if(section<0||section>2)throw new IllegalArgumentException();db.getWritableDatabase().execSQL("UPDATE shift_workspace SET reviewed=reviewed|? WHERE shift_id=?",new Object[]{1<<section,id});}
    static void ready(Db db,long id){if(exists(db,id)&&reviewed(db,id)!=7)throw new IllegalStateException("راجع وأكد التبويبات الثلاثة: مطابقة العامل، الصناديق، المواد. أكد عدم وجود حركة إن كان التبويب فارغًا.");}
    static Cursor operations(Db db,long id,int section){return db.getReadableDatabase().rawQuery("SELECT id,kind,box_id,target_id,material,quantity,amount,note,posted FROM shift_operations WHERE shift_id=? AND section=? ORDER BY id",new String[]{""+id,""+section});}
    static long add(Db db,long id,int section,String kind,long box,long target,String material,double quantity,double amount,String note){
        openOnly(db,id);
        if(!Double.isFinite(amount)||amount<=0||note==null||note.trim().isEmpty())throw new IllegalArgumentException("أدخل البيان والمبلغ الصحيح");
        if(section==1){
            if(!Arrays.asList("EXPENSE","COLLECTION","LOAN","TRANSFER","SUPPLIER").contains(kind)||box<=0)throw new IllegalArgumentException("اختر الصندوق ونوع الحركة");
            if(Arrays.asList("COLLECTION","LOAN","TRANSFER").contains(kind)&&target<=0)throw new IllegalArgumentException("اختر الحساب المقابل");
            if(kind.equals("TRANSFER")&&box==target)throw new IllegalArgumentException("اختر صندوقًا آخر");
        }else if(section==2){
            if(!Arrays.asList(Db.MATERIALS).contains(material)||!Double.isFinite(quantity)||quantity<=0||!Arrays.asList("BUY_CREDIT","BUY_CASH","LOSS").contains(kind))throw new IllegalArgumentException("أدخل المادة والكمية ونوع الحركة");
            if(kind.equals("BUY_CASH")&&box<=0)throw new IllegalArgumentException("اختر صندوق الدفع");
        }else throw new IllegalArgumentException("التبويب غير صحيح");
        ContentValues v=new ContentValues();v.put("shift_id",id);v.put("section",section);v.put("kind",kind);v.put("box_id",box);v.put("target_id",target);v.put("material",material);v.put("quantity",quantity);v.put("amount",amount);v.put("note",note.trim());
        return db.getWritableDatabase().insertOrThrow("shift_operations",null,v);
    }
    static void delete(Db db,long shift,long id){openOnly(db,shift);db.getWritableDatabase().delete("shift_operations","id=? AND shift_id=? AND posted=0",new String[]{""+id,""+shift});}
    static String label(String k){switch(k){case "EXPENSE":return "مصروف";case "COLLECTION":return "تحصيل عميل";case "LOAN":return "سلفة عميل";case "TRANSFER":return "تحويل بين صندوقين";case "SUPPLIER":return "سداد مورد";case "BUY_CREDIT":return "توريد آجل";case "BUY_CASH":return "توريد نقدي";case "LOSS":return "فاقد مواد";default:return k;}}
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
                if(section==1){
                    if(k.equals("SUPPLIER"))db.paySupplier(target==1?"GAS":"OIL",box,a,note,date);
                    else db.addCashTransaction(box,k.equals("COLLECTION")?"IN":"OUT",a,note,date,"YER",k.equals("COLLECTION")||k.equals("LOAN")?"CUSTOMER":k,target);
                }else if(k.equals("BUY_CREDIT"))db.buyFromSupplier(mat,q,a/q,note,date);
                else {
                    db.addMaterialEntry(mat,k.equals("LOSS")?"OUT":"IN",q,note,date);
                    if(k.equals("BUY_CASH"))db.addCashboxEntry(box,"OUT",a,note,date,id);
                    db.postEntry(Journal.simple(note,date,"SHIFT_MATERIAL",id,k.equals("LOSS")?Journal.EXPENSE:Journal.INVENTORY,k.equals("LOSS")?Journal.INVENTORY:Journal.CASH,a,mat));
                }
                db.getWritableDatabase().execSQL("UPDATE shift_operations SET posted=1 WHERE id=?",new Object[]{c.getLong(0)});
            }
        }
    }
}
