package com.alameer.station.shifts;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.view.View;
import android.widget.*;
/** Account directory and statements. No ledger-writing actions. */
public final class LedgerActivity extends Activity {
 private Db db;private LinearLayout rows;private TextView title;private String table;
 public static Intent intent(Context c,String table){return new Intent(c,LedgerActivity.class).putExtra("table",table);}
 @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);table=getIntent().getStringExtra("table");
  if(!java.util.Arrays.asList(ShiftWorkspace.LEDGERS).contains(table)){finish();return;}
  LinearLayout root=StationUi.column(this);int p=StationUi.dp(this,16);root.setPadding(p,p,p,p);root.setBackgroundColor(Util.BG);
  title=StationUi.text(this,"",23,true);root.addView(title);root.addView(StationUi.text(this,"الأرصدة المرحّلة • اضغط الحساب لعرض كشفه",14,false));
  root.addView(StationUi.button(this,"العودة للرئيسية",false,this::finish),StationUi.space(this));
  ScrollView scroll=new ScrollView(this);rows=StationUi.column(this);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);Util.safeInsets(root);accounts();
 }
 private void account(String name,double balance,String unit,String source,String field,String key){
  rows.addView(StationUi.button(this,name+"\n"+Calc.money(balance)+" "+unit,false,()->statement(source,field,key,name,balance,unit)),StationUi.space(this));
 }
 private void accounts(){rows.removeAllViews();
  if(table.equals("cashbox_entries")){title.setText("دفتر الصناديق");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM cashboxes ORDER BY name",null)){while(c.moveToNext())account(c.getString(1),ShiftWorkspace.nativeCash(db,c.getLong(0)),Db.currencyName(ShiftWorkspace.boxCurrency(db,c.getLong(0))),table,"box_id",c.getString(0));}}
  else if(table.equals("debt_entries")){title.setText("دفتر العملاء والسائقين");rows.addView(StationUi.text(this,"الموجب لنا • السالب علينا",14,false));try(Cursor c=db.getReadableDatabase().rawQuery("SELECT d.id,d.name,d.opening+COALESCE(SUM(CASE WHEN e.direction='DEBT' THEN e.amount ELSE -e.amount END),0) FROM debtors d LEFT JOIN debt_entries e ON e.debtor_id=d.id GROUP BY d.id ORDER BY d.name",null)){while(c.moveToNext())account(c.getString(1),c.getDouble(2),"ر.ي",table,"debtor_id",c.getString(0));}}
  else if(table.equals("material_entries")){title.setText("دفتر المواد والشركات");for(String m:Db.MATERIALS)account(m,db.materialSummary(m)[3],"لتر",table,"material",m);rows.addView(StationUi.text(this,"حسابات الشركات — الموجب لنا والسالب علينا",15,true));for(String s:new String[]{"OIL","GAS"})account(Db.supplierName(s),db.supplierBalance(s),"ر.ي","supplier_entries","supplier",s);}
  else if(table.equals("expense_entries")){title.setText("دفتر المصاريف");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT category,SUM(amount) FROM expense_entries GROUP BY category ORDER BY category",null)){while(c.moveToNext())account(c.getString(0),c.getDouble(1),"ر.ي",table,"category",c.getString(0));}}
  else {statement(table,null,null,table.equals("journal")?"دفتر القيود":"حسابات الشركات",0,"ر.ي");return;}
  if(rows.getChildCount()==0)rows.addView(StationUi.text(this,"لا توجد حسابات بعد",16,false));
 }
 private void statement(String source,String field,String key,String name,double balance,String unit){
  rows.removeAllViews();title.setText(name);rows.addView(StationUi.text(this,"الرصيد: "+Calc.money(balance)+" "+unit,18,true));
  rows.addView(StationUi.button(this,"رجوع إلى الحسابات",false,this::accounts),StationUi.space(this));
  String amount=source.equals("material_entries")?"e.litres":source.equals("journal")?"e.total":"e.amount";
  String note=source.equals("journal")?"e.memo":"e.note";
  String direction=source.equals("cashbox_entries")||source.equals("debt_entries")||source.equals("material_entries")?"e.direction":source.equals("supplier_entries")?"e.kind":"''";
  String code="COALESCE((SELECT s.shift_code FROM shift_links l JOIN shifts s ON s.id=l.shift_id WHERE l.entity='"+source+"' AND l.row_id=e.id),'سجل سابق')";
  String where=field==null?"":" WHERE e."+field+"=?";String[] args=field==null?null:new String[]{key};
  String fx=source.equals("cashbox_entries")?",e.currency,e.orig_amount,e.rate":",'YER',0,1";
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT e.entry_date,"+amount+","+note+","+direction+","+code+fx+" FROM "+source+" e"+where+" ORDER BY e.entry_date DESC,e.id DESC LIMIT 500",args)){
   rows.addView(StationUi.text(this,"آخر 500 حركة — الرصيد أعلاه يشمل كامل الحساب",12,false));
   while(c.moveToNext()){String dir=c.getString(3);String translated=dir.equals("IN")?"وارد":dir.equals("OUT")?"صادر":dir.equals("DEBT")?"عليه":dir.equals("PAID")?"له / سداد":dir.equals("PAY")?"توريد مبلغ":dir.equals("BUY")?"توريد مواد":dir;
    String line=translated+" • "+Calc.money(c.getDouble(1))+" "+(source.equals("material_entries")?"لتر":"ر.ي");if(source.equals("cashbox_entries")&&!c.getString(5).equals("YER"))line+="\n"+Calc.money(c.getDouble(6))+" "+Db.currencyName(c.getString(5))+" • سعر التحويل "+Calc.money(c.getDouble(7));
    TextView row=StationUi.text(this,line+"\n"+c.getString(2)+"\n"+c.getString(0)+" • "+c.getString(4),15,false);row.setTextIsSelectable(true);LinearLayout card=StationUi.card(this);card.addView(row);rows.addView(card,StationUi.space(this));}
  }
 }
 @Override protected void onDestroy(){if(db!=null)db.close();super.onDestroy();}
}
