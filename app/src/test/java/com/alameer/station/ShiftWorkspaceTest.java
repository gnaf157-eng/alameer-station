package com.alameer.station.shifts;
import android.content.Context;
import android.database.Cursor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ShiftWorkspaceTest {
 Db db;Context context;long shift,box;
 @Before public void start(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);box=db.addCashbox("صندوق",0);db.setDefaultCashbox(box);shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);db.getWritableDatabase().execSQL("UPDATE readings SET current=previous+1,price=100,sales=100 WHERE shift_id=?",new Object[]{shift});db.addMovement(shift,"CASH","تسليم",db.sales(shift));}
 @After public void stop(){db.close();context.deleteDatabase("alameer_station.db");}
 int count(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
 void refuse(Runnable r){try{r.run();fail("must reject");}catch(IllegalStateException|IllegalArgumentException expected){}}
 void review(){for(int i=0;i<3;i++)ShiftWorkspace.review(db,shift,i);}
 void close(){db.closeAndPostShift(shift,db.soloWorkerId(),"",box);}
 @Test public void draftsDoNotChangeAnyLedgerAndSurviveReopening(){ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");ShiftWorkspace.add(db,shift,2,"BUY_CREDIT",0,0,"بترول",10,100,"فاتورة");assertEquals(0,count("journal"));assertEquals(0,count("cashbox_entries"));assertEquals(0,count("material_entries"));db.close();db=new Db(context);assertEquals(2,count("shift_operations"));assertEquals(0,db.cashboxBalance(box),0.001);}
 @Test public void reviewAllThreeAndZeroDifferenceAreRequired(){refuse(this::close);ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);refuse(this::close);ShiftWorkspace.review(db,shift,2);db.addMovement(shift,"EXPENSE","فرق",1);review();refuse(this::close);assertTrue(db.isOpen(shift));assertEquals(0,count("journal"));}
 @Test public void editsInvalidateReviewButSavingSameReadingsDoesNot(){review();db.getWritableDatabase().execSQL("UPDATE readings SET current=current WHERE shift_id=?",new Object[]{shift});assertEquals(7,ShiftWorkspace.reviewed(db,shift));db.addMovement(shift,"DEBT","عميل",1);assertEquals(6,ShiftWorkspace.reviewed(db,shift));}
 @Test public void allSectionsPostOnceWithOneCodeAndImmutableRecords(){
  ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");ShiftWorkspace.add(db,shift,2,"BUY_CREDIT",0,0,"بترول",10,100,"فاتورة");review();close();
  assertEquals(780,db.cashboxBalance(box),0.001);assertFalse(db.isOpen(shift));assertEquals(1,count("posted_shifts"));assertTrue(count("shift_links")>0);assertEquals(2,count("shift_operations"));refuse(this::close);refuse(()->db.reopenShift(shift,"تصحيح"));refuse(()->db.unpostShift(shift));
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT row_id FROM shift_links WHERE entity='journal' LIMIT 1",null)){assertTrue(c.moveToFirst());long id=c.getLong(0);refuse(()->db.reverseEntry(id,"اختبار"));}
  ReportTable report=new ReportTable(db,shift);boolean cash=false,material=false,code=false;for(ReportTable.Row r:report.rows)for(Object v:r.cells){cash|="كهرباء".equals(v);material|="فاتورة".equals(v);code|=db.shiftCode(shift).equals(v);}assertTrue(cash&&material&&code);
  long next=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,next);assertNotEquals(shift,next);assertNotEquals(db.shiftCode(shift),db.shiftCode(next));
 }
 @Test public void failureInLastSectionRollsBackEverything(){
  ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");ShiftWorkspace.add(db,shift,2,"BUY_CREDIT",0,0,"بترول",10,100,"فاتورة");review();
  db.getWritableDatabase().execSQL("CREATE TRIGGER reject_supply BEFORE INSERT ON supplier_entries BEGIN SELECT RAISE(ABORT,'test'); END");
  try{close();fail();}catch(RuntimeException expected){}
  assertTrue(db.isOpen(shift));assertEquals(0,count("journal"));assertEquals(0,count("cashbox_entries"));assertEquals(0,count("material_entries"));assertEquals(0,count("shift_links"));assertEquals(0,count("posted_shifts"));assertEquals(7,ShiftWorkspace.reviewed(db,shift));
  try(Cursor c=ShiftWorkspace.operations(db,shift,1)){assertTrue(c.moveToFirst());assertEquals(0,c.getInt(8));}
 }
 @Test public void cashPurchaseAndTransferHaveBalancedRealCounterparts(){long other=db.addCashbox("ثان",0);ShiftWorkspace.add(db,shift,1,"TRANSFER",box,other,"",0,50,"تحويل");ShiftWorkspace.add(db,shift,2,"BUY_CASH",box,0,"غاز",5,100,"شراء");review();close();assertEquals(650,db.cashboxBalance(box),0.001);assertEquals(50,db.cashboxBalance(other),0.001);try(Cursor c=db.getReadableDatabase().rawQuery("SELECT SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END) FROM journal_lines",null)){c.moveToFirst();assertEquals(0,c.getDouble(0),0.000001);}}
 @Test public void schema20UpgradePreservesExistingAmounts(){
  db.addCashTransaction(box,"IN",123.45,"قديم",db.shiftDate(shift),"YER","SALE",0);
  android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();
  for(String table:new String[]{"readings","movements","shift_operations"})for(String event:new String[]{"INSERT","UPDATE","DELETE"})sql.execSQL("DROP TRIGGER IF EXISTS review_"+table+"_"+event);
  sql.execSQL("DROP TABLE shift_operations");sql.execSQL("DROP TABLE shift_workspace");sql.execSQL("DROP TABLE shift_links");sql.setVersion(20);db.close();db=new Db(context);
  assertEquals(21,db.getWritableDatabase().getVersion());assertEquals(123.45,db.cashboxBalance(box),0.000001);assertEquals(1,count("journal"));assertEquals(0,count("shift_operations"));assertEquals(0,count("shift_workspace"));
 }
 @Test public void mobileTabsAndReadOnlyLedgerCanOpen(){
  db.setSetting("name_set","1");
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).create().start().resume();
  ShiftActivity screen=controller.get();assertEquals(3,screen.tabs.length);assertEquals("مطابقة العامل",screen.tabs[0].getText().toString());screen.workspacePage(5);screen.workspacePage(6);screen.workspacePage(2);controller.pause().stop().destroy();
  org.robolectric.android.controller.ActivityController<LedgerActivity> ledger=Robolectric.buildActivity(LedgerActivity.class,LedgerActivity.intent(context,"cashbox_entries")).create().start().resume();ledger.pause().stop().destroy();
 }
 @Test public void reportCannotPresentUnpostedDraftAsOfficial(){refuse(()->new ReportTable(db,shift));}
}
