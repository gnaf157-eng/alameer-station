package com.alameer.station.shifts;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Independent roll-forward of capital, enabled after a validated opening import. */
final class Capital {
 static void create(SQLiteDatabase s){
  s.execSQL("CREATE TABLE IF NOT EXISTS supplier_openings(supplier TEXT PRIMARY KEY,balance REAL NOT NULL)");
  s.execSQL("CREATE TABLE IF NOT EXISTS opening_stock(id INTEGER PRIMARY KEY AUTOINCREMENT,material TEXT NOT NULL,location TEXT NOT NULL,quantity REAL NOT NULL,unit_cost REAL NOT NULL,owned INTEGER NOT NULL)");
  s.execSQL("CREATE TABLE IF NOT EXISTS worker_accounts(name TEXT PRIMARY KEY,opening REAL NOT NULL)");
  s.execSQL("CREATE TABLE IF NOT EXISTS stock_cost(material TEXT PRIMARY KEY,unit_cost REAL NOT NULL)");
  s.execSQL("CREATE TABLE IF NOT EXISTS shift_cost(shift_id INTEGER NOT NULL,material TEXT NOT NULL,unit_cost REAL NOT NULL,PRIMARY KEY(shift_id,material))");
  s.execSQL("CREATE TABLE IF NOT EXISTS capital_checks(shift_id INTEGER PRIMARY KEY,entry_date TEXT NOT NULL,previous REAL NOT NULL,profit REAL NOT NULL,expenses REAL NOT NULL,expected REAL NOT NULL,actual REAL NOT NULL,gap REAL NOT NULL,reason TEXT NOT NULL,created_at TEXT NOT NULL)");
 }
 static boolean enabled(Db db){return "1".equals(db.setting("capital_enabled","0"));}
 static double money(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("قيمة مالية غير صالحة");return BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP).doubleValue();}
 static double scalar(Db db,String sql,String... args){try(Cursor c=db.getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getDouble(0):0;}}
 static double external(Db db){return scalar(db,"SELECT COALESCE(SUM(quantity*unit_cost),0) FROM opening_stock WHERE owned=1");}
 static double cost(Db db,String material){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT unit_cost FROM stock_cost WHERE material=?",new String[]{material})){return c.moveToFirst()?c.getDouble(0):db.unitCost(material);}}
 static double stock(Db db){double v=external(db);for(String m:Db.MATERIALS)v+=db.materialSummary(m)[3]*cost(db,m);return v;}
 static double debts(Db db){double sum=0;try(Cursor c=db.debtors(false)){while(c.moveToNext())sum+=c.getDouble(7);}return sum;}
 static double actual(Db db){return db.cashboxesTotal()+debts(db)+stock(db)+db.supplierBalance();}
 static double previous(Db db){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT actual FROM capital_checks ORDER BY shift_id DESC LIMIT 1",null)){if(c.moveToFirst())return c.getDouble(0);}return Double.parseDouble(db.setting("capital_opening","0"));}
 static void capture(Db db,long shift){if(!enabled(db))return;for(String m:Db.MATERIALS)db.getWritableDatabase().execSQL("INSERT OR IGNORE INTO shift_cost VALUES(?,?,?)",new Object[]{shift,m,cost(db,m)});}
 static final class Check extends IllegalStateException {
  final double gap;
  Check(double gap){super("فرق رأس المال: "+Calc.money(gap)+" ر.ي. لم يُرحّل شيء. راجع الحركات أو اعتمد الفرق برمز المدير وسبب واضح.");this.gap=gap;}
 }
 static final class Plan {
  double previous,profit,expenses,expected,costOfSales;
  final Map<String,Double> nextCosts=new LinkedHashMap<>();
 }
 static Plan plan(Db db,long shift){
  if(!enabled(db))return null;
  if(db.shiftDate(shift).compareTo(db.setting("opening_date",""))<=0)throw new IllegalStateException("هذه الأرصدة بعد إقفال الافتتاح؛ اختر تاريخ وردية لاحقًا له");
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT MAX(entry_date) FROM capital_checks",null)){if(c.moveToFirst()&&!c.isNull(0)&&db.shiftDate(shift).compareTo(c.getString(0))<0)throw new IllegalStateException("لا يمكن ترحيل وردية قبل آخر إقفال لرأس المال");}
  capture(db,shift);Plan p=new Plan();p.previous=previous(db);double cogs=0;
  for(String m:Db.MATERIALS){
   double oldCost=scalar(db,"SELECT unit_cost FROM shift_cost WHERE shift_id=? AND material=?",""+shift,m);
   double sold=scalar(db,"SELECT COALESCE(SUM(r.current-r.previous),0) FROM readings r JOIN pumps p ON p.id=r.pump_id WHERE r.shift_id=? AND p.fuel=? AND r.current IS NOT NULL",""+shift,m);
   double bought=0,value=0;
   try(Cursor c=db.getReadableDatabase().rawQuery("SELECT kind,quantity,amount,freight FROM shift_operations WHERE shift_id=? AND material=? AND posted=0",new String[]{""+shift,m})){while(c.moveToNext()){
    String k=c.getString(0);if(Arrays.asList("FUEL_SUPPLY","BUY_CREDIT","BUY_CASH").contains(k)){bought+=c.getDouble(1);value+=c.getDouble(2)+(k.equals("FUEL_SUPPLY")?c.getDouble(3):0);}
   }}
   double before=db.materialSummary(m)[3],after=before-sold+bought;
   if(after < -0.000001)throw new IllegalStateException("مخزون "+m+" غير كافٍ؛ راجع التوريد والقراءات");
   cogs+=sold*oldCost;double remaining=before*cost(db,m)-sold*oldCost+value;
   if(Math.abs(after)<0.000001&&Math.abs(remaining)>0.005)throw new IllegalStateException("راجع تكلفة مخزون "+m);
   p.nextCosts.put(m,after>0.000001?remaining/after:oldCost);
  }
  p.costOfSales=cogs;p.profit=db.sales(shift)-cogs;
  p.expenses=db.total(shift,"EXPENSE")+scalar(db,"SELECT COALESCE(SUM(amount),0) FROM shift_operations WHERE shift_id=? AND kind='EXPENSE' AND posted=0",""+shift);
  p.expected=p.previous+p.profit-p.expenses;return p;
 }
 static void finish(Db db,long shift,Plan p,String reason,boolean override){
  if(p==null)return;
  for(Map.Entry<String,Double> x:p.nextCosts.entrySet())db.getWritableDatabase().execSQL("INSERT OR REPLACE INTO stock_cost VALUES(?,?)",new Object[]{x.getKey(),x.getValue()});
  double actual=actual(db),gap=money(actual-p.expected);String why=reason==null?"":reason.trim();
  if(gap!=0&&(!override||why.length()<5))throw new Check(gap);
  if(p.costOfSales>0)db.postEntry(Journal.simple("تكلفة الوقود المباع — "+db.shiftCode(shift),db.shiftDate(shift),"SHIFT_COGS",shift,"تكلفة الوقود المباع",Journal.INVENTORY,p.costOfSales,""));
  db.getWritableDatabase().execSQL("INSERT INTO capital_checks VALUES(?,?,?,?,?,?,?,?,?,?)",new Object[]{shift,db.shiftDate(shift),p.previous,p.profit,p.expenses,p.expected,actual,gap,gap==0?"":why,Util.now()});
  if(gap!=0)db.audit("capital",shift,"UNMATCHED_APPROVED","",Double.toString(gap),why);
 }
 static String preview(Db db,long shift){
  if(!enabled(db))return "";
  try{Plan p=plan(db,shift);double projected=projected(db,shift,p),gap=money(projected-p.expected);return "السابق: "+Calc.money(p.previous)+" ر.ي\nربح المبيعات: "+Calc.money(p.profit)+" • المخاريج: "+Calc.money(p.expenses)+"\nالمتوقع: "+Calc.money(p.expected)+" ر.ي\nحسب الحركات المدخلة: "+Calc.money(projected)+" ر.ي\nالفرق: "+Calc.money(gap)+(gap==0?" — مطابق مبدئيًا":" — غير مطابق")+"\nيُعاد الفحص من الدفاتر الفعلية قبل اعتماد الترحيل.";}catch(RuntimeException e){return e.getMessage();}
 }
 static double projected(Db db,long shift,Plan p){
  double cash=db.cashboxesTotal()+db.total(shift,"CASH"),debts=debts(db)+db.total(shift,"DEBT")-db.total(shift,"COLLECTION"),company=db.supplierBalance(),stock=external(db);
  for(String m:Db.MATERIALS)stock+=ShiftWorkspace.expectedMaterial(db,shift,m)*p.nextCosts.get(m);
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT kind,amount,freight FROM shift_operations WHERE shift_id=? AND posted=0",new String[]{""+shift})){while(c.moveToNext()){
   String k=c.getString(0);double a=c.getDouble(1);
   if(k.equals("COLLECTION")){cash+=a;debts-=a;}
   else if(k.equals("LOAN")){cash-=a;debts+=a;}
   else if(k.equals("EXPENSE")||k.equals("BUY_CASH"))cash-=a;
   else if(k.equals("SUPPLIER")||k.equals("COMPANY_PAYMENT")){cash-=a;company+=a;}
   else if(k.equals("FUEL_SUPPLY")||k.equals("BUY_CREDIT")){company-=a;if(k.equals("FUEL_SUPPLY"))debts-=c.getDouble(2);}
  }}return cash+debts+stock+company;
 }
 static String history(Db db){StringBuilder b=new StringBuilder("حسابات العمال والعهدة المملوكة للشركة خارج رأس المال.\n");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT entry_date,expected,actual,gap,reason FROM capital_checks ORDER BY shift_id DESC LIMIT 60",null)){while(c.moveToNext())b.append("\n").append(c.getString(0)).append(c.getDouble(3)==0?" — مطابق":" — غير مطابق").append("\nالمتوقع: ").append(Calc.money(c.getDouble(1))).append(" • الفعلي: ").append(Calc.money(c.getDouble(2))).append("\nالفرق: ").append(Calc.money(c.getDouble(3))).append("\n").append(c.getString(4)).append("\n");}return b.toString();}
}
