package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Synthetic fixtures only: production opening data must never enter this repository. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class OpeningImportTest {
 Db db;Context context;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);}
 @After public void teardown(){db.close();context.deleteDatabase("alameer_station.db");}
 static JSONObject fixture()throws Exception{
  return new JSONObject("{\"format\":\"alameer-opening-v1\",\"date\":\"2026-01-01\",\"capital\":1000,\"gasOpeningProfit\":10,"+
   "\"rates\":{\"SAR\":10,\"USD\":20},\"cashboxes\":[{\"name\":\"صندوق أ\",\"balances\":{\"YER\":100,\"SAR\":5}},{\"name\":\"صندوق ب\",\"balances\":{\"YER\":-20}}],"+
   "\"debtors\":[{\"name\":\"عميل أ\",\"balance\":200},{\"name\":\"دائن ب\",\"balance\":-30}],"+
   "\"materials\":[{\"name\":\"بترول\",\"quantity\":100,\"buy\":2,\"freight\":1,\"sell\":4},{\"name\":\"ديزل\",\"quantity\":50,\"buy\":4,\"freight\":1,\"sell\":6},{\"name\":\"غاز\",\"quantity\":0,\"buy\":3,\"freight\":0,\"sell\":4}],"+
   "\"offsite\":[{\"material\":\"ديزل\",\"location\":\"مخزن خارجي\",\"quantity\":20,\"cost\":10,\"owned\":true},{\"material\":\"بترول\",\"location\":\"عهدة الشركة\",\"quantity\":900,\"cost\":0,\"owned\":false}],"+
   "\"suppliers\":[{\"code\":\"OIL\",\"balance\":100},{\"code\":\"GAS\",\"balance\":-150}],"+
   "\"workers\":[{\"name\":\"عامل أ\",\"balance\":77},{\"name\":\"عامل ب\",\"balance\":-11}],"+
   "\"pumps\":[{\"name\":\"طرمبة أ\",\"fuel\":\"بترول\",\"reading\":123},{\"name\":\"طرمبة ب\",\"fuel\":\"ديزل\",\"reading\":456},{\"name\":\"طرمبة ج\",\"fuel\":\"غاز\",\"reading\":789}]}");
 }
 double scalar(String sql){return Capital.scalar(db,sql);}
 void imported()throws Exception{OpeningImport.apply(db,fixture(),"synthetic-fixture");}
 @Test public void signedMultiCurrencyOpeningMatchesAndDoesNotRecreateHistory()throws Exception{
  db.setLockPin("8642");db.addCashbox("تجربة",123);imported();
  assertEquals(1000,Capital.actual(db),0.000001);assertEquals(130,db.cashboxesTotal(),0.000001);assertEquals(170,db.debtsTotal()-db.creditsTotal(),0.000001);
  assertEquals(750,db.stockValueTotal(),0.000001);assertEquals(100,db.supplierBalance("OIL"),0.000001);assertEquals(-150,db.supplierBalance("GAS"),0.000001);
  assertEquals(66,scalar("SELECT SUM(opening) FROM worker_accounts"),0.000001);assertEquals(0,db.expensesTotal(),0.000001);assertEquals(0,scalar("SELECT COUNT(*) FROM shifts"),0);
  assertEquals(0,scalar("SELECT COUNT(*) FROM supplier_entries"),0);assertEquals(1,scalar("SELECT COUNT(*) FROM journal WHERE source='OPENING'"),0);assertEquals(db.journalDebit(),db.journalCredit(),0.000001);assertTrue(db.checkLockPin("8642"));
  assertEquals(-150,LedgerStatement.load(db,"supplier_entries","GAS","الشركة","",ShiftDates.today()).closing,0.000001);
  assertEquals(5,CashAccounts.posted(db,db.defaultCashbox(),"SAR"),0.000001);assertEquals(123,scalar("SELECT last_reading FROM pumps WHERE fuel='بترول'"),0);
  try{OpeningImport.apply(db,fixture(),"again");fail();}catch(IllegalStateException expected){}assertEquals(1000,Capital.actual(db),0.000001);
  java.io.File f=new java.io.File(context.getCacheDir(),"opening-test.db");Backup.snapshot(db,f);assertTrue(Backup.validDatabase(f));f.delete();
 }
 @Test public void wrongCapitalOrDuplicateNameRollsBackAllTestData()throws Exception{
  long id=db.addCashbox("قبل",777);JSONObject p=fixture();p.put("capital",999);
  try{OpeningImport.apply(db,p,"bad");fail();}catch(IllegalStateException expected){}assertEquals(777,db.cashboxBalance(id),0);assertFalse(Capital.enabled(db));assertEquals("",db.setting("opening_import_hash",""));
  p=fixture();p.getJSONArray("debtors").getJSONObject(1).put("name","عميل أ");try{OpeningImport.apply(db,p,"bad");fail();}catch(RuntimeException expected){}assertEquals(777,db.cashboxBalance(id),0);
 }
 long shift()throws Exception{long id=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,id);db.getWritableDatabase().execSQL("UPDATE readings SET current=previous WHERE shift_id=?",new Object[]{id});return id;}
 void sell(long shift,String fuel,double litres){db.getWritableDatabase().execSQL("UPDATE readings SET current=previous+?,sales=?*price WHERE shift_id=? AND pump_id IN (SELECT id FROM pumps WHERE fuel=?)",new Object[]{litres,litres,shift,fuel});}
 void review(long id){for(int i=0;i<3;i++)ShiftWorkspace.review(db,id,i);}
 void post(long id){db.closeAndPostShift(id,db.soloWorkerId(),"",db.defaultCashbox());}
 @Test public void salesExpensesTransfersAndSupplyKeepCapitalMatched()throws Exception{
  imported();long id=shift();sell(id,"بترول",10);db.addMovement(id,"CASH","استلام",35);db.addMovement(id,"EXPENSE","ماء",5);
  ShiftWorkspace.addSupply(db,id,"غاز",10,"سائق تجريبي","");ShiftWorkspace.addSupply(db,id,"بترول",10,"سائق تجريبي","");
  ShiftWorkspace.add(db,id,1,"EXPENSE",db.defaultCashbox(),0,"",0,2,"كهرباء");
  long second=(long)scalar("SELECT id FROM cashboxes WHERE name='صندوق ب'");ShiftWorkspace.add(db,id,1,"TRANSFER",db.defaultCashbox(),second,"",0,10,"تحويل");
  review(id);post(id);assertEquals(1003,Capital.actual(db),0.000001);assertEquals(0,scalar("SELECT gap FROM capital_checks"),0);assertEquals(10,scalar("SELECT profit FROM capital_checks"),0);assertEquals(7,scalar("SELECT expenses FROM capital_checks"),0);
  long next=shift();sell(next,"غاز",2);db.addMovement(next,"CASH","استلام",8);review(next);post(next);assertEquals(1005,Capital.actual(db),0.000001);
  assertEquals(0,scalar("SELECT SUM(abs(gap)) FROM capital_checks"),0);assertEquals(2,scalar("SELECT COUNT(*) FROM capital_checks"),0);
 }
 @Test public void missingPostedLegAbortsEverythingAndManagerOverrideRetainsGap()throws Exception{
  imported();long id=shift();sell(id,"بترول",10);db.addMovement(id,"CASH","استلام",40);review(id);
  db.getWritableDatabase().execSQL("CREATE TRIGGER skip_cash AFTER INSERT ON cashbox_entries BEGIN DELETE FROM cashbox_entries WHERE id=NEW.id; END");
  try{post(id);fail();}catch(Capital.Check expected){assertEquals(-40,expected.gap,0);}
  assertTrue(db.isOpen(id));assertEquals(1000,Capital.actual(db),0.000001);assertEquals(0,scalar("SELECT COUNT(*) FROM capital_checks"),0);assertEquals(0,scalar("SELECT COUNT(*) FROM posted_shifts"),0);
  db.closeAndPostShift(id,db.soloWorkerId(),"فرق تحت المراجعة",db.defaultCashbox(),true);
  assertEquals(-40,scalar("SELECT gap FROM capital_checks"),0);assertEquals(970,Capital.actual(db),0.000001);
  db.getWritableDatabase().execSQL("DROP TRIGGER skip_cash");long next=shift();review(next);post(next);
  assertEquals(970,scalar("SELECT previous FROM capital_checks ORDER BY shift_id DESC LIMIT 1"),0);assertEquals(-40,scalar("SELECT SUM(gap) FROM capital_checks"),0);
 }
 @Test public void laterPriceChangesDoNotRevalueExistingInventory()throws Exception{
  imported();long id=shift();db.setBuyPrice("بترول",8);db.setFreightPrice("بترول",2);assertEquals(1000,Capital.actual(db),0.000001);
  sell(id,"بترول",10);db.addMovement(id,"CASH","استلام",40);ShiftWorkspace.addSupply(db,id,"بترول",10,"السائق","");review(id);post(id);
  assertEquals(1010,Capital.actual(db),0.000001);assertEquals(3.7,Capital.cost(db,"بترول"),0.000001);assertEquals(0,scalar("SELECT gap FROM capital_checks"),0);
 }
}
