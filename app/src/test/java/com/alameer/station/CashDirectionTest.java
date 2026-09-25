package com.alameer.station.shifts;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CashDirectionTest {
 Context context;Db db;long shift,box;
 @Before public void setUp(){
  context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setSetting("name_set","1");db.setTelegramOn(false);
  box=db.addCashbox("المحطة",1000);db.setDefaultCashbox(box);db.setRate("SAR",100);db.setRate("USD",400);
  shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);
  db.getWritableDatabase().execSQL("UPDATE readings SET current=previous,price=100,sales=0 WHERE shift_id=?",new Object[]{shift});
 }
 @After public void tearDown(){db.close();context.deleteDatabase("alameer_station.db");}
 int rows(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
 void reject(Runnable action){try{action.run();fail("Expected rejection");}catch(RuntimeException expected){}}
 void finishShift(){ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);for(String m:Db.MATERIALS)ShiftWorkspace.count(db,shift,2,m,0);ShiftWorkspace.review(db,shift,2);db.closeAndPostShift(shift,db.soloWorkerId(),"",box);}
 @Test public void customerInAndOutAffectCashAndSettlementInOppositeDirections(){
  long customer=db.addDebtor("أحمد","",500);
  NameDirectory.addCash(db,shift,box,"IN","YER",200,"أحمد",NameDirectory.CUSTOMER,"");
  NameDirectory.addCash(db,shift,box,"OUT","YER",50,"أحمد",NameDirectory.CUSTOMER,"");
  assertEquals(1150,CashAccounts.expected(db,shift,box,"YER"),0.00001);assertEquals(500,db.debtorBalance(customer),0.00001);assertEquals(0,rows("debt_entries"));
  finishShift();assertEquals(1150,db.cashboxBalance(box),0.00001);assertEquals(350,db.debtorBalance(customer),0.00001);assertEquals(2,rows("cashbox_entries"));assertEquals(2,rows("debt_entries"));assertEquals(0,rows("expense_entries"));assertEquals(db.journalDebit(),db.journalCredit(),0.00001);
 }
 @Test public void excessPaymentBecomesCustomerCreditAndOutgoingReducesIt(){
  long customer=db.addDebtor("سالم","",0);
  NameDirectory.addCash(db,shift,box,"IN","YER",300,"سالم",NameDirectory.CUSTOMER,"");NameDirectory.addCash(db,shift,box,"OUT","YER",80,"سالم",NameDirectory.CUSTOMER,"");
  finishShift();assertEquals(-220,db.debtorBalance(customer),0.00001);assertEquals(1220,db.cashboxBalance(box),0.00001);
 }
 @Test public void expenseNameAndMemoRemainSeparateAndNeverCreateCustomerDebt(){
  String name="كهرباء — المحطة";NameDirectory.addCash(db,shift,box,"OUT","YER",60,name,NameDirectory.EXPENSE,"فاتورة شهر");
  assertEquals(NameDirectory.EXPENSE,NameDirectory.role(db,name));reject(()->NameDirectory.addCash(db,shift,box,"IN","YER",20,name,NameDirectory.EXPENSE,""));assertEquals(1,rows("shift_operations"));
  finishShift();assertEquals(940,db.cashboxBalance(box),0.00001);assertEquals(0,rows("debt_entries"));assertEquals(0,rows("debtors"));
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT category,amount,note FROM expense_entries",null)){assertTrue(c.moveToFirst());assertEquals(name,c.getString(0));assertEquals(60,c.getDouble(1),0.00001);assertTrue(c.getString(2).contains("فاتورة شهر"));assertFalse(c.moveToNext());}
 }
 @Test public void allNameFieldsAreSuggestedAndSameNameRolesRemainDistinct(){
  db.addMovement(shift,"EXPENSE","كهرباء",10);db.addMovement(shift,"COLLECTION","زبون العامل",10);db.renameWorker(db.soloWorkerId(),"عامل محفوظ");
  db.setBuyPrice("بترول",1);ShiftWorkspace.addSupply(db,shift,"بترول",1,"سائق محفوظ","");
  NameDirectory.remember(db,"سالم",NameDirectory.EXPENSE);ShiftWorkspace.customer(db,"سالم");
  assertTrue(NameDirectory.names(db).contains("كهرباء"));assertTrue(NameDirectory.names(db).contains("زبون العامل"));assertTrue(NameDirectory.names(db).contains("عامل محفوظ"));assertTrue(NameDirectory.names(db).contains("سائق محفوظ"));assertEquals("",NameDirectory.role(db,"سالم"));
  int collisions=0;for(NameDirectory.Entry e:NameDirectory.entries(db))if(e.name.equals("سالم"))collisions++;assertEquals(2,collisions);
 }
 @Test public void aFailedCashEntryDoesNotRememberNameOrCreateAccount(){
  db.getWritableDatabase().execSQL("CREATE TRIGGER reject_cash BEFORE INSERT ON shift_operations BEGIN SELECT RAISE(ABORT,'test'); END");
  reject(()->NameDirectory.addCash(db,shift,box,"IN","YER",100,"جديد",NameDirectory.CUSTOMER,""));assertEquals(0,rows("debtors"));assertEquals(0,rows("shift_operations"));assertFalse(NameDirectory.names(db).contains("جديد"));
 }
 @Test public void selectedCurrencyKeepsItsRateForCustomerAndExpense(){
  CashAccounts.setOpening(db,box,"SAR",10);long customer=db.addDebtor("عميل سعودي","",500);
  NameDirectory.addCash(db,shift,box,"IN","SAR",2,"عميل سعودي",NameDirectory.CUSTOMER,"");NameDirectory.addCash(db,shift,box,"OUT","SAR",1,"نقل",NameDirectory.EXPENSE,"");db.setRate("SAR",200);
  finishShift();assertEquals(11,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(300,db.debtorBalance(customer),0.00001);assertEquals(2100,db.cashboxBalance(box),0.00001);
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT amount FROM expense_entries",null)){assertTrue(c.moveToFirst());assertEquals(100,c.getDouble(0),0.00001);}
 }
 @Test public void nameClassificationCancelsCleanlyAndIsRememberedAfterSaving(){
  ShiftWorkspace.review(db,shift,0);org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.workspacePage(5);
  assertNotNull(a.pages[5].findViewWithTag("cashbox-tile-"+box));assertNull(a.pages[5].findViewWithTag("cash-tile-0"));
  CashEntryCard card=new CashEntryCard(new WorkspaceForms(a,a.pages[5],1),box);card.show();card.chooseKind(1);card.person.setText("اتصالات");card.amount.setText("20");card.dialog.getButton(-1).performClick();
  assertTrue(card.classificationDialog.isShowing());card.classificationDialog.getButton(-2).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,rows("shift_operations"));assertFalse(NameDirectory.names(db).contains("اتصالات"));
  card.dialog.getButton(-1).performClick();card.classificationDialog.getListView().performItemClick(null,1,1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(card.dialog.isShowing());assertEquals(1,rows("shift_operations"));assertEquals("980 يمني",card.current.getText().toString());
  card.person.setText("اتصالات");assertEquals(NameDirectory.EXPENSE,card.selectedRole);card.amount.setText("10");card.dialog.getButton(-1).performClick();assertEquals(2,rows("shift_operations"));assertTrue(card.dialog.isShowing());assertEquals("970 يمني",card.current.getText().toString());card.dialog.dismiss();controller.pause().stop().destroy();
  db.close();db=new Db(context);assertEquals(NameDirectory.EXPENSE,NameDirectory.role(db,"اتصالات"));
 }
 @Test public void transferOnlyTouchesTwoCashboxesAndIgnoresHiddenCustomerName(){
  long other=db.addCashbox("الصراف",0);ShiftWorkspace.review(db,shift,0);
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.workspacePage(5);
  CashEntryCard card=new CashEntryCard(new WorkspaceForms(a,a.pages[5],1),box);card.show();card.person.setText("لا تنشئه");card.chooseKind(2);assertEquals(View.GONE,card.person.getVisibility());assertFalse(card.boxes.ids.contains(box));
  card.to.setSelection(card.boxes.ids.indexOf(other));Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();card.amount.setText("200");card.dialog.getButton(-1).performClick();assertEquals(1,rows("shift_operations"));assertEquals(0,rows("debtors"));assertEquals(800,CashAccounts.expected(db,shift,box,"YER"),0.00001);assertEquals(200,CashAccounts.expected(db,shift,other,"YER"),0.00001);card.dialog.dismiss();controller.pause().stop().destroy();
  finishShift();assertEquals(800,db.cashboxBalance(box),0.00001);assertEquals(200,db.cashboxBalance(other),0.00001);assertEquals(0,rows("debt_entries"));assertEquals(0,rows("expense_entries"));assertEquals(db.journalDebit(),db.journalCredit(),0.00001);
 }
 @Test public void upgrade23KeepsLedgerAmountsAndDropsOnlyObsoleteOpenCashCounts(){
  db.addCashTransaction(box,"IN",35,"قديم",db.shiftDate(shift),"YER","SALE",0);ShiftWorkspace.count(db,shift,1,""+box,999);ShiftWorkspace.count(db,shift,2,"بترول",0);
  db.getWritableDatabase().execSQL("INSERT INTO shift_counts VALUES(999,1,'1:YER',55,55)");db.getWritableDatabase().setVersion(23);db.close();db=new Db(context);
  assertEquals(24,db.getReadableDatabase().getVersion());assertEquals(1035,db.cashboxBalance(box),0.00001);assertEquals(1,rows("cashbox_entries"));assertNull(ShiftWorkspace.counted(db,shift,1,""+box));assertEquals(0,ShiftWorkspace.counted(db,shift,2,"بترول"),0.00001);assertEquals(55,ShiftWorkspace.counted(db,999,1,"1:YER"),0.00001);ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);
 }
}
