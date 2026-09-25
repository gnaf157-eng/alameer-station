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
 static double actual(Db db){return db.cashboxesTotal()+(db.debtsTotal()-db.creditsTotal())+stock(db)+db.supplierBalance();}
 static double previous(Db db){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT actual FROM capital_checks ORDER BY shift_id DESC LIMIT 1",null)){if(c.moveToFirst())return c.getDouble(0);}return Double.parseDouble(db.setting("capital_opening","0"));}
 static void capture(Db db,long shift){if(!enabled(db))return;for(String m:Db.MATERIALS)db.getWritableDatabase().execSQL("INSERT OR IGNORE INTO shift_cost VALUES(?,?,?)",new Object[]{shift,m,cost(db,m)});}
 static final class Check extends IllegalStateException {
  final double gap;
  Check(double gap){super("فرق رأس المال: "+Calc.money(gap)+" ر.ي. لم يُرحّل شيء. راجع الحركات أو اعتمد الفرق برمز المدير وسبب واضح.");this.gap=gap;}
 }
 static final class Plan {
  double previous,profit,expenses,expected;
  final Map<String,Double> nextCosts=new LinkedHashMap<>();
 }
 static Plan plan(Db db,long shift){
  if(!enabled(db))return null;capture(db,shift);Plan p=new Plan();p.previous=previous(db);double cogs=0;
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
  p.profit=db.sales(shift)-cogs;
  p.expenses=db.total(shift,"EXPENSE")+scalar(db,"SELECT COALESCE(SUM(amount),0) FROM shift_operations WHERE shift_id=? AND kind='EXPENSE' AND posted=0",""+shift);
  p.expected=p.previous+p.profit-p.expenses;return p;
 }
 static void finish(Db db,long shift,Plan p,String reason,boolean override){
  if(p==null)return;
  for(Map.Entry<String,Double> x:p.nextCosts.entrySet())db.getWritableDatabase().execSQL("INSERT OR REPLACE INTO stock_cost VALUES(?,?)",new Object[]{x.getKey(),x.getValue()});
  double actual=actual(db),gap=money(actual-p.expected);String why=reason==null?"":reason.trim();
  if(gap!=0&&(!override||why.length()<5))throw new Check(gap);
  db.getWritableDatabase().execSQL("INSERT INTO capital_checks VALUES(?,?,?,?,?,?,?,?,?,?)",new Object[]{shift,db.shiftDate(shift),p.previous,p.profit,p.expenses,p.expected,actual,gap,gap==0?"":why,Util.now()});
  if(gap!=0)db.audit("capital",shift,"UNMATCHED_APPROVED","",Double.toString(gap),why);
 }
 static String preview(Db db,long shift){
  if(!enabled(db))return "";
  try{Plan p=plan(db,shift);return "رقابة رأس المال\nالسابق: "+Calc.money(p.previous)+" ر.ي\nربح المبيعات: "+Calc.money(p.profit)+" • المخاريج: "+Calc.money(p.expenses)+"\nالمتوقع عند الترحيل: "+Calc.money(p.expected)+" ر.ي\nتُقارن النتيجة الفعلية بعد ترحيل جميع الأطراف وقبل اعتماد الوردية.";}catch(RuntimeException e){return e.getMessage();}
 }
 static String history(Db db){StringBuilder b=new StringBuilder("حسابات العمال والعهدة المملوكة للشركة خارج رأس المال.\n");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT entry_date,expected,actual,gap,reason FROM capital_checks ORDER BY shift_id DESC LIMIT 60",null)){while(c.moveToNext())b.append("\n").append(c.getString(0)).append(c.getDouble(3)==0?" — مطابق":" — غير مطابق").append("\nالمتوقع: ").append(Calc.money(c.getDouble(1))).append(" • الفعلي: ").append(Calc.money(c.getDouble(2))).append("\nالفرق: ").append(Calc.money(c.getDouble(3))).append("\n").append(c.getString(4)).append("\n");}return b.toString();}
}
