package com.alameer.station.shifts;

import android.database.Cursor;
import java.time.LocalDate;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/** A read-only snapshot of one posted account, including its brought-forward balance. */
final class LedgerStatement {
 static final class Row {
  final long id;final String date,note,code;final double increase,decrease,balance;
  Row(long id,String date,String note,String code,double increase,double decrease,double balance){this.id=id;this.date=date;this.note=note;this.code=code;this.increase=increase;this.decrease=decrease;this.balance=balance;}
 }
 final String source,key,name,station,unit,from,to,increaseLabel,decreaseLabel,legend;
 final boolean signedAccount;final List<Row> rows;
 final double opening,increase,decrease,closing;
 private LedgerStatement(Db db,String source,String key,String name,String from,String to){
  if(name==null||name.trim().isEmpty()||key==null||key.isEmpty())throw new IllegalArgumentException("افتح الحساب المطلوب أولًا");
  validateRange(from,to);this.source=source;this.key=key;this.name=name;this.from=from;this.to=to;station=Branding.stationName(db);
  String where,amount="e.amount",direction="e.direction";String[] args;double initial=0;
  switch(source){
   case "cashbox_entries":{
    long id=CashAccounts.box(key);String currency=CashAccounts.code(db,key);CashAccounts.currency(currency);
    try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM cashboxes WHERE id=?",new String[]{""+id})){if(!c.moveToFirst())throw new IllegalArgumentException("الصندوق غير موجود");}
    initial=CashAccounts.opening(db,id,currency)[0];unit=Db.currencyName(currency);where="e.box_id=? AND e.currency=?";args=new String[]{""+id,currency};
    amount="CASE WHEN e.orig_amount<>0 THEN e.orig_amount WHEN e.rate>0 THEN e.amount/e.rate ELSE NULL END";
    increaseLabel="وارد";decreaseLabel="صادر";signedAccount=true;legend="الموجب لنا • السالب علينا";break;
   }
   case "debt_entries":{
    String[] info=db.debtorInfo(Long.parseLong(key));if(info==null)throw new IllegalArgumentException("الحساب غير موجود");initial=Double.parseDouble(info[2]);
    unit="ر.ي";where="e.debtor_id=?";args=new String[]{key};increaseLabel="عليه";decreaseLabel="له / سداد";signedAccount=true;legend="الموجب لنا على الحساب • السالب مستحق للحساب علينا";break;
   }
   case "material_entries":
    if(!Arrays.asList(Db.MATERIALS).contains(key))throw new IllegalArgumentException("المادة غير موجودة");
    unit="لتر";where="e.material=?";args=new String[]{key};amount="e.litres";increaseLabel="وارد";decreaseLabel="صادر";signedAccount=false;legend="الكميات باللترات";break;
   case "supplier_entries":
    if(!Arrays.asList(Db.SUPPLIERS).contains(key))throw new IllegalArgumentException("الشركة غير موجودة");
    unit="ر.ي";where="COALESCE(e.supplier,'OIL')=? AND e.voided=0";args=new String[]{key};direction="e.kind";increaseLabel="إضافة للرصيد";decreaseLabel="خصم من الرصيد";signedAccount=true;legend="الموجب لنا لدى الشركة • السالب علينا للشركة";break;
   case "expense_entries":
    unit="ر.ي";where="e.category=?";args=new String[]{key};direction="'EXPENSE'";increaseLabel="مصروف";decreaseLabel="مردود";signedAccount=false;legend="الرصيد يمثل المصروف المتراكم";break;
   default:throw new IllegalArgumentException("اختر حسابًا واحدًا من الدفاتر الرسمية");
  }
  String code="COALESCE((SELECT s.shift_code FROM shift_links l JOIN shifts s ON s.id=l.shift_id WHERE l.entity='"+source+"' AND l.row_id=e.id LIMIT 1),"+(source.equals("supplier_entries")?"''":"(SELECT s.shift_code FROM shifts s WHERE s.id=e.source_shift)")+",'')";
  ArrayList<Row> result=new ArrayList<>();double brought=initial,plus=0,minus=0,balance=initial;
  ArrayList<String> parameters=new ArrayList<>(Arrays.asList(args));parameters.add(to);
  String details=source.equals("supplier_entries")?",e.material,e.litres":",'',0";
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT e.id,e.entry_date,e.note,"+direction+","+amount+","+code+details+" FROM "+source+" e WHERE "+where+" AND e.entry_date<=? ORDER BY e.entry_date,e.id",parameters.toArray(new String[0]))){
   while(c.moveToNext()){
    String kind=c.getString(3),date=c.getString(1);if(c.isNull(4))throw new IllegalStateException("تعذر حساب عملة حركة قديمة؛ راجع سعر صرفها");double value=c.getDouble(4);
    if(!Double.isFinite(value))throw new IllegalStateException("توجد حركة بقيمة غير صالحة في الحساب");
    double delta;
    if(source.equals("expense_entries"))delta=value;
    else if(source.equals("supplier_entries"))delta=kind.equals("PAY")?value:-value;
    else if(source.equals("debt_entries"))delta=kind.equals("DEBT")?value:-value;
    else delta=kind.equals("IN")?value:-value;
    balance+=delta;if(!Double.isFinite(balance))throw new IllegalStateException("رصيد الحساب يتجاوز المدى المسموح");
    if(!from.isEmpty()&&date.compareTo(from)<0){brought=balance;continue;}
    double add=Math.max(0,delta),subtract=Math.max(0,-delta);plus+=add;minus+=subtract;
    String note=c.getString(2);if(note==null||note.trim().isEmpty())note=delta>=0?increaseLabel:decreaseLabel;
    if(source.equals("supplier_entries")&&!c.getString(6).isEmpty())note="توريد "+c.getString(6)+" • "+number(c.getDouble(7))+" لتر\n"+note;
    result.add(new Row(c.getLong(0),date,note,c.getString(5),add,subtract,balance));
   }
  }
  opening=brought;increase=plus;decrease=minus;closing=balance;rows=Collections.unmodifiableList(result);
 }
 static LedgerStatement load(Db db,String source,String key,String name,String from,String to){
  // One transaction gives the opening, movements and totals the same database snapshot.
  android.database.sqlite.SQLiteDatabase sql=db.getReadableDatabase();sql.beginTransactionNonExclusive();
  try{return new LedgerStatement(db,source,key,name,from,to);}finally{sql.endTransaction();}
 }
 static void validateRange(String from,String to){
  try{if(from==null||to==null||to.isEmpty())throw new IllegalArgumentException();LocalDate end=LocalDate.parse(to);if(!end.toString().equals(to))throw new IllegalArgumentException();if(!from.isEmpty()){LocalDate start=LocalDate.parse(from);if(!start.toString().equals(from)||start.isAfter(end))throw new IllegalArgumentException();}}
  catch(RuntimeException e){throw new IllegalArgumentException("اختر فترة صحيحة؛ تاريخ البداية يجب ألا يتجاوز النهاية");}
 }
 String period(){return (from.isEmpty()?"من بداية الحساب":"من "+from)+" إلى "+to;}
 String balanceLabel(double value){return signedAccount?(value<0?"علينا ":"لنا ")+number(Math.abs(value)):number(value);}
 static String number(double value){if(Math.abs(value)<0.0000001)value=0;return new DecimalFormat("#,##0.###",DecimalFormatSymbols.getInstance(Locale.US)).format(value);}
}
