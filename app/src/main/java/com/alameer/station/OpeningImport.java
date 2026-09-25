package com.alameer.station.shifts;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Explicit private-file import. The APK and repository contain no customer balances. */
final class OpeningImport {
 static final int REQUEST=4202;
 static void pick(Activity a){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);a.startActivityForResult(i,REQUEST);}
 static void restoreSafety(Activity a){
  File[] files=a.getFilesDir().listFiles((dir,n)->n.startsWith("backup-before-opening-")&&n.endsWith(".db"));
  if(files==null||files.length==0){error(a,new Exception("لا توجد نسخة أمان قبل الاستيراد"));return;}
  Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());File last=files[0];
  new AlertDialog.Builder(a).setTitle("الرجوع إلى ما قبل الاستيراد")
   .setMessage("ستُستعاد البيانات السابقة للنقل وتُستبدل البيانات الحالية. ستُحفظ نسخة أمان أخرى من البيانات الحالية عند إعادة تشغيل التطبيق.")
   .setNegativeButton("إلغاء",null).setPositiveButton("استعادة",(d,w)->ManagerAccess.run(a,new Db(a),()->new Backup(a).restoreFrom(Uri.fromFile(last)))).show();
 }
 static String digest(String text)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
 static double number(JSONObject o,String key)throws JSONException{double n=o.getDouble(key);if(!Double.isFinite(n)||Math.abs(n)>1e12)throw new IllegalArgumentException("قيمة غير صالحة: "+key);return n;}
 static String name(JSONObject o,String key)throws JSONException{String n=o.getString(key).trim();if(n.isEmpty()||n.length()>500)throw new IllegalArgumentException("اسم غير صالح");return n;}
 static double nonnegative(JSONObject o,String key)throws JSONException{double n=number(o,key);if(n<0)throw new IllegalArgumentException("قيمة سالبة غير مسموحة: "+key);return n;}
 static void validate(JSONObject p)throws JSONException{
  if(!"alameer-opening-v1".equals(p.getString("format")))throw new IllegalArgumentException("اختر ملف أرصدة المحطة المعتمد");
  LocalDate.parse(p.getString("date"));number(p,"capital");nonnegative(p,"gasOpeningProfit");
  for(String key:new String[]{"cashboxes","debtors","materials","pumps","workers","offsite","suppliers"})if(p.getJSONArray(key).length()>1000)throw new IllegalArgumentException("الملف أكبر من الحد المسموح");
  if(p.getJSONArray("cashboxes").length()==0||p.getJSONArray("pumps").length()==0||p.getJSONArray("materials").length()!=3||p.getJSONArray("suppliers").length()!=2)throw new IllegalArgumentException("ملف افتتاح غير مكتمل");
 }
 static void from(Activity a,Uri uri){
  try(InputStream in=a.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(in==null)throw new IOException("تعذر قراءة الملف");byte[] buffer=new byte[8192];int n;
   while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2*1024*1024)throw new IOException("ملف الاستيراد كبير جدًا");}
   String raw=out.toString("UTF-8");JSONObject p=new JSONObject(raw);validate(p);
   new AlertDialog.Builder(a).setTitle("استيراد الأرصدة الافتتاحية")
    .setMessage("تاريخ الإقفال: "+p.getString("date")+"\nرأس المال: "+Calc.money(number(p,"capital"))+" ر.ي\nالصناديق: "+p.getJSONArray("cashboxes").length()+" • الحسابات: "+p.getJSONArray("debtors").length()+"\n\nسيستبدل هذا كل الحركات والأرصدة التجريبية، مع حفظ نسخة أمان والإبقاء على رمز المدير وإعدادات الجهاز. حسابات العمال خارج رأس المال. لا تُستورد مخاريج قديمة.")
    .setNegativeButton("إلغاء",null).setPositiveButton("استيراد",(d,w)->{
     Db db=new Db(a);ManagerAccess.run(a,db,()->{try{
      if(!db.setting("opening_import_hash","").isEmpty())throw new IllegalStateException("تم استيراد الأرصدة مسبقًا؛ لا يمكن تكرارها أو مسح العمل الفعلي");
      File safety=new File(a.getFilesDir(),"backup-before-opening-"+System.currentTimeMillis()+".db");Backup.snapshot(db,safety);
      apply(db,p,digest(raw));
      new AlertDialog.Builder(a).setTitle("اكتمل الاستيراد — مطابق")
       .setMessage("رأس المال: "+Calc.money(Capital.actual(db))+" ر.ي\nحُفظت نسخة أمان قبل النقل. الأرصدة والعدادات جاهزة لوردية جديدة.")
       .setPositiveButton("الرئيسية",(x,y)->{a.startActivity(new Intent(a,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));a.finish();}).show();
     }catch(Exception e){error(a,e);}});
    }).show();
  }catch(Exception e){error(a,e);}
 }
 static void error(Activity a,Exception e){new AlertDialog.Builder(a).setTitle("لم تُستورد البيانات").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();}
 static void line(Journal.Entry e,String account,double value,String party){if(value>0)e.debit(account,value,party);else if(value<0)e.credit(account,-value,party);}
 static void apply(Db db,JSONObject p,String hash)throws Exception{
  validate(p);SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{
   if(!db.setting("opening_import_hash","").isEmpty())throw new IllegalStateException("سبق استيراد الأرصدة؛ لن تتكرر");
   String date=p.getString("date"),now=Util.now();
   for(String t:new String[]{"settlement_links","shift_links","shift_counts","shift_operations","shift_cost","shift_workspace","readings","movements","shifts","posted_shifts","cashbox_entries","cashbox_openings","cashbox_currency","cashboxes","material_entries","expense_entries","debt_entries","debtors","supplier_entries","supplier_openings","journal_lines","journal","period_locks","dip_readings","ledger_audit","audit_log","remembered_names","pumps","opening_stock","worker_accounts","stock_cost","capital_checks"})s.delete(t,null,null);
   JSONObject rates=p.getJSONObject("rates");for(String code:new String[]{"SAR","USD"})db.setRate(code,number(rates,code));
   Journal.Entry entry=new Journal.Entry("أرصدة افتتاحية مستوردة — "+date,date,"OPENING",0);
   JSONArray boxes=p.getJSONArray("cashboxes");for(int i=0;i<boxes.length();i++){
    JSONObject b=boxes.getJSONObject(i);String label=name(b,"name");long id=db.addCashbox(label,0);if(i==0)db.setDefaultCashbox(id);
    JSONObject balances=b.getJSONObject("balances");for(Iterator<String> keys=balances.keys();keys.hasNext();){String code=keys.next();CashAccounts.currency(code);CashAccounts.setOpening(db,id,code,number(balances,code));}
    line(entry,Journal.CASH,db.cashboxBalance(id),label);
   }
   JSONArray debts=p.getJSONArray("debtors");for(int i=0;i<debts.length();i++){
    JSONObject b=debts.getJSONObject(i);String label=name(b,"name");double value=number(b,"balance");
    s.execSQL("INSERT INTO debtors(name,opening,created_at) VALUES(?,?,?)",new Object[]{label,value,now});
    s.execSQL("INSERT OR IGNORE INTO remembered_names(type,name) VALUES('DEBT',?)",new Object[]{label});line(entry,Journal.RECEIVABLE,value,label);
   }
   Set<String> fuels=new HashSet<>();JSONArray materials=p.getJSONArray("materials");for(int i=0;i<materials.length();i++){
    JSONObject b=materials.getJSONObject(i);String m=name(b,"name");if(!Arrays.asList(Db.MATERIALS).contains(m)||!fuels.add(m))throw new IllegalArgumentException("مادة مكررة أو غير صالحة");
    double quantity=nonnegative(b,"quantity"),buy=nonnegative(b,"buy"),freight=nonnegative(b,"freight"),sell=nonnegative(b,"sell"),cost=buy+freight;
    db.setBuyPrice(m,buy);db.setFreightPrice(m,freight);db.setSetting("sell_"+m,""+sell);
    s.execSQL("INSERT INTO stock_cost VALUES(?,?)",new Object[]{m,cost});
    if(quantity!=0)s.execSQL("INSERT INTO material_entries(material,direction,litres,note,entry_date,created_at) VALUES(?,'IN',?,'رصيد افتتاحي مستورد',?,?)",new Object[]{m,quantity,date,now});
    line(entry,Journal.INVENTORY,quantity*cost,m);
   }
   JSONArray stock=p.getJSONArray("offsite");for(int i=0;i<stock.length();i++){
    JSONObject b=stock.getJSONObject(i);String m=name(b,"material"),location=name(b,"location");if(!fuels.contains(m))throw new IllegalArgumentException("مادة مخزون غير صالحة");
    double quantity=nonnegative(b,"quantity"),cost=nonnegative(b,"cost");boolean owned=b.getBoolean("owned");
    s.execSQL("INSERT INTO opening_stock(material,location,quantity,unit_cost,owned) VALUES(?,?,?,?,?)",new Object[]{m,location,quantity,cost,owned?1:0});if(owned)line(entry,Journal.INVENTORY,quantity*cost,location);
   }
   Set<String> companies=new HashSet<>();JSONArray suppliers=p.getJSONArray("suppliers");for(int i=0;i<suppliers.length();i++){
    JSONObject b=suppliers.getJSONObject(i);String code=name(b,"code");if(!Arrays.asList(Db.SUPPLIERS).contains(code)||!companies.add(code))throw new IllegalArgumentException("شركة غير صالحة");double value=number(b,"balance");
    s.execSQL("INSERT INTO supplier_openings VALUES(?,?)",new Object[]{code,value});line(entry,Db.supplierAccount(code),value,Db.supplierName(code));
   }
   JSONArray workers=p.getJSONArray("workers");for(int i=0;i<workers.length();i++){JSONObject b=workers.getJSONObject(i);String label=name(b,"name");s.execSQL("INSERT INTO worker_accounts VALUES(?,?)",new Object[]{label,number(b,"balance")});s.execSQL("INSERT OR IGNORE INTO remembered_names(type,name) VALUES('WORKER',?)",new Object[]{label});}
   int worker=db.soloWorkerId();Set<String> pumpNames=new HashSet<>();JSONArray pumps=p.getJSONArray("pumps");for(int i=0;i<pumps.length();i++){
    JSONObject b=pumps.getJSONObject(i);String label=name(b,"name"),fuel=name(b,"fuel");if(!fuels.contains(fuel)||!pumpNames.add(label))throw new IllegalArgumentException("طرمبة مكررة أو مادة غير صالحة");
    double price=Double.parseDouble(db.setting("sell_"+fuel,"0"));s.execSQL("INSERT INTO pumps(name,fuel,price,last_reading,worker_id) VALUES(?,?,?,?,?)",new Object[]{label,fuel,price,nonnegative(b,"reading"),worker});
   }
   db.setSetting("capital_enabled","1");double actual=Capital.actual(db),reference=number(p,"capital");
   if(Capital.money(actual-reference)!=0)throw new IllegalStateException("فشل فحص الافتتاح؛ فرق رأس المال: "+Calc.money(actual-reference)+" ر.ي. لم تتغير البيانات السابقة.");
   double gasProfit=nonnegative(p,"gasOpeningProfit");line(entry,"أرباح تسوية افتتاح الغاز",-gasProfit,"ضمن رأس المال الافتتاحي؛ لا تُضاف مرة ثانية");line(entry,Journal.EQUITY,-actual+gasProfit,"");db.postEntry(entry);
   db.setSetting("capital_opening",""+actual);db.setSetting("opening_date",date);db.setSetting("opening_gas_profit",""+gasProfit);db.setSetting("opening_import_hash",hash);db.setSetting("opening_import_document",p.toString());
   db.audit("device",0,"IMPORT_OPENING","",Calc.money(actual),"نقل افتتاحي مع استبعاد أرصدة العمال والعهدة من رأس المال");s.setTransactionSuccessful();
  }finally{s.endTransaction();}
 }
}
