package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LedgerStatementTest {
 Context context;Db db;long box,customer;
 @Before public void start(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);box=db.addCashbox("الصراف",0);customer=db.addDebtor("سالم","",100);}
 @After public void stop(){db.close();context.deleteDatabase("alameer_station.db");}
 void sql(String text,Object...args){db.getWritableDatabase().execSQL(text,args);}
 void cash(long id,String date,String direction,String currency,double amount,double original,double rate){sql("INSERT INTO cashbox_entries(box_id,entry_date,direction,currency,amount,orig_amount,rate,note,created_at) VALUES(?,?,?,?,?,?,?,'حركة','2026-01-01')",id,date,direction,currency,amount,original,rate);}
 void debt(String date,String direction,double amount){sql("INSERT INTO debt_entries(debtor_id,entry_date,direction,amount,note,created_at) VALUES(?,?,?,?,'حركة','2026-01-01')",customer,date,direction,amount);}
 void material(String date,String material,String direction,double quantity){sql("INSERT INTO material_entries(entry_date,material,direction,litres,note,created_at) VALUES(?,?,?,?,'حركة','2026-01-01')",date,material,direction,quantity);}
 void supplier(String date,String company,String kind,double value,int voided){sql("INSERT INTO supplier_entries(entry_date,supplier,kind,amount,voided,created_at) VALUES(?,?,?,?,?,'2026-01-01')",date,company,kind,value,voided);}
 LedgerStatement load(String table,String key){return LedgerStatement.load(db,table,key,"حساب تجريبي","2026-01-10","2026-01-31");}
 void reject(Runnable action){try{action.run();fail("Expected rejection");}catch(IllegalArgumentException expected){}}
 @Test public void cashStatementSeparatesCurrenciesAndUsesStoredRateAndNegativeOpening(){
  db.setRate("SAR",100);CashAccounts.setOpening(db,box,"SAR",-10);
  cash(box,"2026-01-01","IN","SAR",2000,20,100);cash(box,"2026-01-10","OUT","SAR",200,2,100);cash(box,"2026-01-31","IN","SAR",100,0,100);cash(box,"2026-02-01","IN","SAR",5000,50,100);
  cash(box,"2026-01-15","IN","USD",999,999,1);long other=db.addCashbox("آخر",0);cash(other,"2026-01-15","IN","SAR",999,999,1);db.setRate("SAR",200);
  LedgerStatement s=load("cashbox_entries",CashAccounts.key(box,"SAR"));assertEquals(10,s.opening,0.00001);assertEquals(1,s.increase,0.00001);assertEquals(2,s.decrease,0.00001);assertEquals(9,s.closing,0.00001);assertEquals(2,s.rows.size());assertEquals("سعودي",s.unit);
  LedgerStatement full=LedgerStatement.load(db,"cashbox_entries",CashAccounts.key(box,"SAR"),"الصراف","","2026-12-31");assertEquals(-10,full.opening,0.00001);assertEquals(CashAccounts.posted(db,box,"SAR"),full.closing,0.00001);assertEquals("علينا 10",full.balanceLabel(full.opening));
 }
 @Test public void customerCreditAndSameDayOrderHaveCorrectRunningBalances(){
  debt("2026-01-01","PAID",150);debt("2026-01-10","DEBT",10);debt("2026-01-10","PAID",20);debt("2026-02-01","DEBT",100);
  LedgerStatement s=load("debt_entries",""+customer);assertEquals(-50,s.opening,0.00001);assertEquals(-40,s.rows.get(0).balance,0.00001);assertEquals(-60,s.rows.get(1).balance,0.00001);assertTrue(s.rows.get(0).id<s.rows.get(1).id);assertEquals("علينا 60",s.balanceLabel(s.closing));assertEquals(10,s.increase,0.00001);assertEquals(20,s.decrease,0.00001);
 }
 @Test public void companyStatementExcludesVoidsAndOtherCompanyAndShowsSuppliedLitres(){
  supplier("2026-01-01","OIL","PAY",500,0);supplier("2026-01-10","OIL","BUY",100,0);sql("UPDATE supplier_entries SET material='ديزل',litres=5 WHERE kind='BUY'");supplier("2026-01-15","OIL","BUY",999,1);supplier("2026-01-15","GAS","PAY",999,0);
  LedgerStatement s=load("supplier_entries","OIL");assertEquals(1,s.rows.size());assertEquals(500,s.opening,0.00001);assertEquals(400,s.closing,0.00001);assertEquals(db.supplierBalance("OIL"),s.closing,0.00001);assertTrue(s.rows.get(0).note.contains("ديزل • 5 لتر"));
 }
 @Test public void materialStatementUsesLitresAndKeepsSalesAsOneOutgoingMovement(){
  material("2026-01-01","بترول","IN",1000);material("2026-01-10","بترول","OUT",20.125);material("2026-01-31","بترول","IN",50);material("2026-01-15","غاز","IN",900);
  LedgerStatement s=load("material_entries","بترول");assertEquals("لتر",s.unit);assertEquals(1000,s.opening,0.00001);assertEquals(1029.875,s.closing,0.00001);assertEquals(db.materialSummary("بترول")[3],s.closing,0.00001);assertEquals(2,s.rows.size());assertEquals("1,029.875",LedgerStatement.number(s.closing));
 }
 @Test public void expenseStatementIncludesPriorBalanceAndOnlySelectedCategory(){
  for(Object[] row:new Object[][]{{"2026-01-01",20,"كهرباء"},{"2026-01-10",5,"كهرباء"},{"2026-01-15",-2,"كهرباء"},{"2026-01-15",100,"أجرة"}})sql("INSERT INTO expense_entries(entry_date,amount,category,created_at) VALUES(?,?,?,'2026-01-01')",row);
  LedgerStatement s=load("expense_entries","كهرباء");assertEquals(20,s.opening,0.00001);assertEquals(5,s.increase,0.00001);assertEquals(2,s.decrease,0.00001);assertEquals(23,s.closing,0.00001);assertFalse(s.signedAccount);
 }
 @Test public void statementDoesNotTruncateAtTheScreensFiveHundredRows(){
  db.getWritableDatabase().beginTransaction();try{for(int i=0;i<507;i++)debt("2026-01-10","DEBT",1);db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}
  LedgerStatement s=load("debt_entries",""+customer);assertEquals(507,s.rows.size());assertEquals(100,s.opening,0.00001);assertEquals(607,s.closing,0.00001);assertEquals(607,s.rows.get(506).balance,0.00001);
 }
 @Test public void noMovementsInPeriodStillPrintsBroughtForwardBalance(){
  debt("2026-01-01","PAID",30);LedgerStatement s=load("debt_entries",""+customer);assertTrue(s.rows.isEmpty());assertEquals(70,s.opening,0.00001);assertEquals(70,s.closing,0.00001);assertEquals(0,s.increase,0.00001);
 }
 @Test public void unpostedCompanyOperationsAreAbsentAndExportDoesNotWriteLedgers(){
  long shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",box,0,10,"قيد الوردية");
  LedgerStatement s=LedgerStatement.load(db,"supplier_entries","OIL","شركة النفط","",ShiftDates.today());assertTrue(s.rows.isEmpty());assertEquals(0,s.closing,0.00001);
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT (SELECT COUNT(*) FROM supplier_entries),(SELECT COUNT(*) FROM shift_operations),(SELECT COUNT(*) FROM journal)",null)){c.moveToFirst();assertEquals(0,c.getInt(0));assertEquals(1,c.getInt(1));assertEquals(0,c.getInt(2));}assertEquals(25,db.getReadableDatabase().getVersion());
 }
 @Test public void invalidRangeOrAccountCannotExportAnUnfilteredLedger(){
  reject(()->LedgerStatement.validateRange("2026-02-01","2026-01-01"));reject(()->LedgerStatement.validateRange("2026-02-30","2026-03-01"));reject(()->LedgerStatement.load(db,"journal","1","القيود","","2026-01-01"));reject(()->LedgerStatement.load(db,"cashbox_entries","","كل الصناديق","","2026-01-01"));reject(()->load("material_entries","غير موجود"));
 }
}
