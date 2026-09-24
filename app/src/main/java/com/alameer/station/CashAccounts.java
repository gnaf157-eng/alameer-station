package com.alameer.station.shifts;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** A physical balance for each currency; the legacy cashbox total remains in YER. */
final class CashAccounts {
 static void create(SQLiteDatabase s){
  s.execSQL("CREATE TABLE IF NOT EXISTS cashbox_openings(box_id INTEGER NOT NULL,currency TEXT NOT NULL,native_amount REAL NOT NULL,rate REAL NOT NULL,yer_amount REAL NOT NULL,PRIMARY KEY(box_id,currency))");
  ShiftWorkspace.column(s,"shift_operations","currency","TEXT NOT NULL DEFAULT ''");
  ShiftWorkspace.column(s,"shift_operations","target_currency","TEXT NOT NULL DEFAULT ''");
  ShiftWorkspace.column(s,"shift_workspace","receipt_currency","TEXT NOT NULL DEFAULT ''");
  s.execSQL("INSERT OR IGNORE INTO cashbox_openings SELECT b.id,COALESCE(c.code,'YER'),b.opening/COALESCE(c.opening_rate,1),COALESCE(c.opening_rate,1),b.opening FROM cashboxes b LEFT JOIN cashbox_currency c ON c.box_id=b.id");
  s.execSQL("UPDATE shift_operations SET currency=COALESCE((SELECT code FROM cashbox_currency WHERE box_id=shift_operations.box_id),'YER') WHERE currency=''");
  s.execSQL("UPDATE shift_operations SET target_currency=COALESCE((SELECT code FROM cashbox_currency WHERE box_id=shift_operations.target_id),'YER') WHERE target_currency=''");
  s.execSQL("UPDATE shift_workspace SET receipt_currency=COALESCE((SELECT code FROM cashbox_currency WHERE box_id=shift_workspace.cashbox_id),'YER') WHERE receipt_currency=''");
  // Existing count records retain their numbers and become explicitly currency-qualified.
  s.execSQL("UPDATE shift_counts SET account=account||':'||COALESCE((SELECT code FROM cashbox_currency WHERE box_id=CAST(shift_counts.account AS INTEGER)),'YER') WHERE section=1 AND instr(account,':')=0");
 }
 static void currency(String code){if(!Arrays.asList(Db.CURRENCIES).contains(code))throw new IllegalArgumentException("اختر العملة");}
 static void requireBox(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM cashboxes WHERE id=? AND active=1",new String[]{""+id})){if(!c.moveToFirst())throw new IllegalArgumentException("اختر الصندوق");}}
 static String key(long box,String code){currency(code);return box+":"+code;}
 static long box(String key){return Long.parseLong(key.split(":",2)[0]);}
 static String code(Db db,String key){return key.contains(":")?key.split(":",2)[1]:ShiftWorkspace.boxCurrency(db,box(key));}
 static String normalize(Db db,String key){return key(box(key),code(db,key));}
 static String name(Db db,long box){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT name FROM cashboxes WHERE id=?",new String[]{""+box})){return c.moveToFirst()?c.getString(0):"غير محدد";}}
 static double[] opening(Db db,long box,String code){
  currency(code);try(Cursor c=db.getReadableDatabase().rawQuery("SELECT native_amount,rate,yer_amount FROM cashbox_openings WHERE box_id=? AND currency=?",new String[]{""+box,code})){if(c.moveToFirst())return new double[]{c.getDouble(0),c.getDouble(1),c.getDouble(2)};}
  return new double[]{0,db.rate(code),0};
 }
 static void seed(SQLiteDatabase s,long box,double yer){s.execSQL("INSERT INTO cashbox_openings(box_id,currency,native_amount,rate,yer_amount) VALUES(?,'YER',?,1,?)",new Object[]{box,yer,yer});}
 static void setOpening(Db db,long box,String code,double nativeAmount){
  currency(code);if(!Double.isFinite(nativeAmount))throw new IllegalArgumentException("الرصيد غير صحيح");
  SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{
   requireBox(db,box);double[] old=opening(db,box,code);double yer=nativeAmount*old[1],delta=yer-old[2];if(!Double.isFinite(yer))throw new IllegalArgumentException("الرصيد غير صحيح");
   if(db.openingPosted()&&Math.abs(delta)>0.0000001){
    String label="تعديل افتتاحي — "+name(db,box)+" • "+Db.currencyName(code);
    db.postEntry(Journal.simple(label,ShiftDates.today(),"CASH_OPENING_ADJUSTMENT",box,delta>0?Journal.CASH:Journal.EQUITY,delta>0?Journal.EQUITY:Journal.CASH,Math.abs(delta),name(db,box)));
   }
   ContentValues v=new ContentValues();v.put("box_id",box);v.put("currency",code);v.put("native_amount",nativeAmount);v.put("rate",old[1]);v.put("yer_amount",yer);s.insertWithOnConflict("cashbox_openings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
   s.execSQL("UPDATE cashboxes SET opening=(SELECT COALESCE(SUM(yer_amount),0) FROM cashbox_openings WHERE box_id=?) WHERE id=?",new Object[]{box,box});
   db.audit("cashbox",box,"EDIT_CURRENCY_OPENING",code+" "+old[0],code+" "+nativeAmount,"تعديل الرصيد الافتتاحي من الضبط؛ سعر التحويل "+old[1]);
   s.execSQL("UPDATE shift_workspace SET reviewed=reviewed & ~6 WHERE shift_id IN (SELECT id FROM shifts WHERE status='OPEN')");
   s.setTransactionSuccessful();
  }finally{s.endTransaction();}
 }
 static double posted(Db db,long box,String code){
  currency(code);double total=opening(db,box,code)[0];
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT direction,amount,orig_amount,rate FROM cashbox_entries WHERE box_id=? AND currency=?",new String[]{""+box,code})){while(c.moveToNext())total+=("IN".equals(c.getString(0))?1:-1)*(c.getDouble(2)!=0?c.getDouble(2):c.getDouble(1)/c.getDouble(3));}
  return total;
 }
 static String receiptCurrency(Db db,long shift){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT receipt_currency FROM shift_workspace WHERE shift_id=?",new String[]{""+shift})){return c.moveToFirst()&&!c.getString(0).isEmpty()?c.getString(0):ShiftWorkspace.boxCurrency(db,ShiftWorkspace.box(db,shift));}}
 static double expected(Db db,long shift,long box,String code){
  double total=posted(db,box,code);
  if(db.isOpen(shift)&&box==ShiftWorkspace.box(db,shift)&&code.equals(receiptCurrency(db,shift)))try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_rate FROM shift_workspace WHERE shift_id=?",new String[]{""+shift})){if(c.moveToFirst())total+=db.total(shift,"CASH")/c.getDouble(0);}
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT kind,box_id,target_id,amount,rate,target_rate,currency,target_currency FROM shift_operations WHERE shift_id=? AND posted=0",new String[]{""+shift})){
   while(c.moveToNext()){String kind=c.getString(0);if(!Arrays.asList("EXPENSE","COLLECTION","LOAN","TRANSFER","SUPPLIER","COMPANY_PAYMENT","BUY_CASH").contains(kind))continue;
    if(c.getLong(1)==box&&code.equals(c.getString(6)))total+=(kind.equals("COLLECTION")?1:-1)*c.getDouble(3)/c.getDouble(4);
    if(kind.equals("TRANSFER")&&c.getLong(2)==box&&code.equals(c.getString(7)))total+=c.getDouble(3)/c.getDouble(5);
   }
  }return total;
 }
 static List<String> currencies(Db db,long box,long shift){
  Set<String> used=new HashSet<>();used.add(ShiftWorkspace.boxCurrency(db,box));
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT currency FROM cashbox_openings WHERE box_id=? UNION SELECT currency FROM cashbox_entries WHERE box_id=? UNION SELECT currency FROM shift_operations WHERE shift_id=? AND box_id=? UNION SELECT target_currency FROM shift_operations WHERE shift_id=? AND target_id=? AND kind='TRANSFER'",new String[]{""+box,""+box,""+shift,""+box,""+shift,""+box})){while(c.moveToNext())used.add(c.getString(0));}
  if(box==ShiftWorkspace.box(db,shift))used.add(receiptCurrency(db,shift));List<String> ordered=new ArrayList<>();for(String code:Db.CURRENCIES)if(used.contains(code))ordered.add(code);return ordered;
 }
}
