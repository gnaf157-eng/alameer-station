package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CashboxFlowTest {
 Db db;Context context;long box,shift;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);db.setSetting("name_set","1");db.setRate("SAR",100);db.setRate("USD",400);box=db.addCashbox("الخزنة",1000);db.setDefaultCashbox(box);for(String m:Db.MATERIALS)db.setFuelPrice(m,10);shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);db.getWritableDatabase().execSQL("UPDATE readings SET current=previous,sales=0 WHERE shift_id=?",new Object[]{shift});}
 @After public void cleanup(){db.close();context.deleteDatabase("alameer_station.db");}
 int rows(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
 double cashJournal(){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END),0) FROM journal_lines WHERE account=?",new String[]{Journal.CASH})){c.moveToFirst();return c.getDouble(0);}}
 void review(){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM cashboxes WHERE active=1",null)){while(c.moveToNext())for(String code:CashAccounts.currencies(db,c.getLong(0),shift))ShiftWorkspace.count(db,shift,1,CashAccounts.key(c.getLong(0),code),CashAccounts.expected(db,shift,c.getLong(0),code));}for(String m:Db.MATERIALS)ShiftWorkspace.count(db,shift,2,m,0);for(int section=0;section<3;section++)ShiftWorkspace.review(db,shift,section);}
 void refuse(Runnable r){try{r.run();fail("must reject");}catch(RuntimeException expected){}}
 @Test public void eachCurrencyHasItsOwnOpeningAndBalance(){
  CashAccounts.setOpening(db,box,"SAR",10);CashAccounts.setOpening(db,box,"USD",2);
  assertEquals(1000,CashAccounts.posted(db,box,"YER"),0.00001);assertEquals(10,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(2,CashAccounts.posted(db,box,"USD"),0.00001);assertEquals(2800,db.cashboxBalance(box),0.00001);
  ShiftWorkspace.addCash(db,shift,1,"EXPENSE",box,0,"SAR","YER",3,"كهرباء");assertEquals(7,CashAccounts.expected(db,shift,box,"SAR"),0.00001);assertEquals(1000,CashAccounts.expected(db,shift,box,"YER"),0.00001);assertEquals(0,rows("cashbox_entries"));
  db.setRate("SAR",200);review();db.closeAndPostShift(shift,db.soloWorkerId(),"",box);assertEquals(7,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(2500,db.cashboxBalance(box),0.00001);
 }
 @Test public void editingApprovedOpeningPostsOnlyAuditedDifference(){
  db.postOpeningBalances(ShiftDates.today());int original=rows("journal");CashAccounts.setOpening(db,box,"YER",1400);assertEquals(1400,cashJournal(),0.00001);assertEquals(1400,db.cashboxBalance(box),0.00001);assertEquals(original+1,rows("journal"));
  CashAccounts.setOpening(db,box,"YER",1200);assertEquals(1200,cashJournal(),0.00001);assertEquals(original+2,rows("journal"));assertEquals(0,rows("cashbox_entries"));
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM ledger_audit WHERE action='EDIT_CURRENCY_OPENING' AND entity_id=?",new String[]{""+box})){c.moveToFirst();assertEquals(2,c.getInt(0));}
  review();CashAccounts.setOpening(db,box,"YER",1500);assertEquals(1,ShiftWorkspace.reviewed(db,shift));ShiftWorkspace.review(db,shift,1);assertEquals(3,ShiftWorkspace.reviewed(db,shift));
 }
 @Test public void openingAdjustmentRollsBackIfJournalFails(){db.postOpeningBalances(ShiftDates.today());db.getWritableDatabase().execSQL("CREATE TRIGGER reject_opening BEFORE INSERT ON journal WHEN NEW.source='CASH_OPENING_ADJUSTMENT' BEGIN SELECT RAISE(ABORT,'test'); END");refuse(()->CashAccounts.setOpening(db,box,"YER",1500));assertEquals(1000,CashAccounts.opening(db,box,"YER")[0],0.00001);assertEquals(1000,db.cashboxBalance(box),0.00001);assertEquals(1000,cashJournal(),0.00001);}
 @Test public void editingForeignOpeningKeepsItsOriginalRate(){CashAccounts.setOpening(db,box,"SAR",10);db.postOpeningBalances(ShiftDates.today());db.setRate("SAR",200);CashAccounts.setOpening(db,box,"SAR",15);assertEquals(2500,db.cashboxBalance(box),0.00001);assertEquals(2500,cashJournal(),0.00001);assertEquals(100,CashAccounts.opening(db,box,"SAR")[1],0.00001);}
 @Test public void foreignTransferAndReceiptUseTheirSelectedCurrencies(){
  CashAccounts.setOpening(db,box,"SAR",10);long other=db.addCashbox("صندوق آخر",0);
  ShiftWorkspace.addCash(db,shift,1,"TRANSFER",box,other,"SAR","USD",4,"تحويل");
  assertEquals(6,CashAccounts.expected(db,shift,box,"SAR"),0.00001);assertEquals(1,CashAccounts.expected(db,shift,other,"USD"),0.00001);assertEquals(1000,CashAccounts.expected(db,shift,box,"YER"),0.00001);
  review();db.closeAndPostShift(shift,db.soloWorkerId(),"",box);assertEquals(6,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(1,CashAccounts.posted(db,other,"USD"),0.00001);assertEquals(db.journalDebit(),db.journalCredit(),0.00001);try(Cursor c=db.unpostedShifts()){assertEquals(0,c.getCount());}try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM journal WHERE total<=0",null)){c.moveToFirst();assertEquals(0,c.getInt(0));}
 }
 @Test public void workerReceiptAndCompanyPaymentKeepSelectedCurrencyAndRate(){
  CashAccounts.setOpening(db,box,"SAR",10);db.addMovement(shift,"COLLECTION","عميل",200);db.addMovement(shift,"CASH","تسليم",200);ShiftWorkspace.selectBox(db,shift,box,"SAR");ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",box,0,"SAR","YER",1,"شركة النفط");db.setRate("SAR",200);
  assertEquals(11,CashAccounts.expected(db,shift,box,"SAR"),0.00001);assertEquals(1000,CashAccounts.expected(db,shift,box,"YER"),0.00001);review();db.closeAndPostShift(shift,db.soloWorkerId(),"",box);assertEquals(11,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(1000,CashAccounts.posted(db,box,"YER"),0.00001);assertEquals(100,db.supplierBalance("OIL"),0.00001);assertEquals(2100,db.cashboxBalance(box),0.00001);
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT currency,orig_amount,rate FROM cashbox_entries ORDER BY id",null)){assertTrue(c.moveToFirst());assertEquals("SAR",c.getString(0));assertEquals(2,c.getDouble(1),0.00001);assertEquals(100,c.getDouble(2),0.00001);assertTrue(c.moveToNext());assertEquals("SAR",c.getString(0));assertEquals(1,c.getDouble(1),0.00001);assertEquals(100,c.getDouble(2),0.00001);assertFalse(c.moveToNext());}
 }
 @Test public void upgradeFrom22PreservesAmountsAndRetiresOpenCashCounts(){
  long sar=ShiftWorkspace.addBox(db,"صندوق سعودي","SAR",10);ShiftWorkspace.addCash(db,shift,1,"EXPENSE",sar,0,"SAR","YER",2,"قديم");ShiftWorkspace.count(db,shift,1,CashAccounts.key(sar,"SAR"),8);
  db.getWritableDatabase().execSQL("UPDATE shift_counts SET account=? WHERE account=?",new Object[]{""+sar,CashAccounts.key(sar,"SAR")});db.getWritableDatabase().execSQL("UPDATE shift_operations SET currency='',target_currency=''");db.getWritableDatabase().execSQL("DROP TABLE cashbox_openings");db.getWritableDatabase().setVersion(22);db.close();db=new Db(context);
  assertEquals(26,db.getReadableDatabase().getVersion());assertEquals(1000,db.cashboxBalance(sar),0.00001);assertEquals(10,CashAccounts.posted(db,sar,"SAR"),0.00001);assertEquals(8,CashAccounts.expected(db,shift,sar,"SAR"),0.00001);assertNull(ShiftWorkspace.counted(db,shift,1,CashAccounts.key(sar,"SAR")));
 }
 @Test public void openingEditorSavesTheChosenCurrencyAfterApproval(){
  db.postOpeningBalances(ShiftDates.today());
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.cashOpeningDialog(box);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();Spinner code=dialog.getWindow().getDecorView().findViewWithTag("opening-currency");EditText amount=dialog.getWindow().getDecorView().findViewWithTag("opening-amount");code.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();amount.setText("5");dialog.getButton(-1).performClick();assertEquals(5,CashAccounts.opening(db,box,"SAR")[0],0.00001);assertEquals(1500,cashJournal(),0.00001);assertEquals(1000,CashAccounts.opening(db,box,"YER")[0],0.00001);controller.pause().stop().destroy();
 }
 @Test public void cardStaysOpenForRepeatedEntriesAndShowsLiveBalances(){
  CashAccounts.setOpening(db,box,"SAR",10);ShiftWorkspace.review(db,shift,0);
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.workspacePage(5);WorkspaceForms host=new WorkspaceForms(a,a.pages[5],1);NameDirectory.remember(db,"كهرباء",NameDirectory.EXPENSE);ShiftWorkspace.customer(db,"عميل");CashEntryCard card=new CashEntryCard(host,box);card.chooseKind(1);card.show();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();card.currency.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  card.person.setText("كهرباء");card.amount.setText("3");assertEquals("10 سعودي",card.current.getText().toString());assertEquals("7 سعودي",card.after.getText().toString());card.dialog.getButton(-1).performClick();assertTrue(card.dialog.isShowing());assertEquals(box,card.boxId);assertEquals("SAR",card.code());assertEquals("",card.amount.getText().toString());assertEquals("7 سعودي",card.current.getText().toString());
  card.chooseKind(0);card.person.setText("عميل");card.amount.setText("2");assertEquals("9 سعودي",card.after.getText().toString());card.dialog.getButton(-1).performClick();assertTrue(card.dialog.isShowing());assertEquals(2,rows("shift_operations"));assertEquals("9 سعودي",card.current.getText().toString());assertEquals(0,rows("cashbox_entries"));card.dialog.getButton(-2).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(card.dialog.isShowing());controller.pause().stop().destroy();
 }
}
