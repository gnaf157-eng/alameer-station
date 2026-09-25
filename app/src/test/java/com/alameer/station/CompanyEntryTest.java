package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CompanyEntryTest {
 Context context;Db db;long shift,box;
 @Before public void start(){
  context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setSetting("name_set","1");db.setTelegramOn(false);
  db.setRate("SAR",100);for(String m:Db.MATERIALS){db.setFuelPrice(m,100);db.setBuyPrice(m,20);db.setFreightPrice(m,2);}
  box=ShiftWorkspace.addBox(db,"الصراف","SAR",100);db.setDefaultCashbox(box);shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);
  db.getWritableDatabase().execSQL("UPDATE readings SET current=previous,price=100,sales=0 WHERE shift_id=?",new Object[]{shift});ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);
 }
 @After public void stop(){db.close();context.deleteDatabase("alameer_station.db");}
 void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
 int rows(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
 void reject(Runnable action){try{action.run();fail("Expected rejection");}catch(RuntimeException expected){}}
 void close(){ShiftWorkspace.review(db,shift,2);db.closeAndPostShift(shift,db.soloWorkerId(),"",box);}
 boolean hasCountInput(View v){if(v instanceof EditText)return true;if(v instanceof TextView){String s=((TextView)v).getText().toString();if(s.contains("مطابقة المواد")||s.contains("الجرد الفعلي"))return true;}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasCountInput(((ViewGroup)v).getChildAt(i)))return true;return false;}
 @Test public void twoCompanyCardsShareOneRowAndOpenTheirOwnMaterialChoices(){
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.workspacePage(6);idle();assertEquals(6,a.page);
  LinearLayout row=a.pages[6].findViewWithTag("company-tiles");assertNotNull(row);assertEquals(LinearLayout.HORIZONTAL,row.getOrientation());assertEquals(2,row.getChildCount());assertNotNull(row.findViewWithTag("company-tile-OIL"));assertNotNull(row.findViewWithTag("company-tile-GAS"));assertFalse(hasCountInput(a.pages[6]));
  row.findViewWithTag("company-tile-GAS").performClick();idle();android.app.AlertDialog opened=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(opened.isShowing());Spinner gasMaterial=opened.findViewById(android.R.id.content).findViewWithTag("company-material");assertEquals(1,gasMaterial.getCount());assertEquals("غاز",gasMaterial.getItemAtPosition(0));opened.dismiss();
  CompanyEntryCard oil=new CompanyEntryCard(new WorkspaceForms(a,a.pages[6],2),"OIL");oil.show();idle();oil.chooseKind(1);assertEquals(View.GONE,oil.paymentGroup.getVisibility());assertEquals(View.VISIBLE,oil.supplyGroup.getVisibility());assertEquals(2,oil.material.getCount());assertEquals("بترول",oil.material.getItemAtPosition(0));assertEquals("ديزل",oil.material.getItemAtPosition(1));oil.dialog.dismiss();controller.pause().stop().destroy();
 }
 @Test public void companyPaymentDialogUsesSelectedCashCurrencyAndKeepsSnapshot(){
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.workspacePage(6);
  CompanyEntryCard card=new CompanyEntryCard(new WorkspaceForms(a,a.pages[6],2),"GAS");card.show();idle();card.box.setSelection(card.boxes.ids.indexOf(box));idle();assertEquals("SAR",card.code());card.amount.setText("٢");
  assertEquals("لنا 200 ر.ي",card.after.getText().toString());assertTrue(card.cashBalance.getText().toString().contains("98"));card.dialog.getButton(-1).performClick();idle();assertFalse(card.dialog.isShowing());assertEquals(1,rows("shift_operations"));assertEquals(3,ShiftWorkspace.reviewed(db,shift));assertEquals(0,rows("supplier_entries"));assertEquals(200,ShiftWorkspace.expectedCompany(db,shift,"GAS"),0.00001);assertEquals(0,ShiftWorkspace.expectedCompany(db,shift,"OIL"),0.00001);
  controller.pause().stop().destroy();db.setRate("SAR",200);close();assertEquals(98,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(200,db.supplierBalance("GAS"),0.00001);assertEquals(0,db.supplierBalance("OIL"),0.00001);assertEquals(0,rows("debt_entries"));assertEquals(0,rows("shift_counts"));assertEquals(db.journalDebit(),db.journalCredit(),0.00001);
 }
 @Test public void gasSupplyDialogUsesOnlyGasAndPreservesDriverFreight(){
  NameDirectory.remember(db,"سائق محفوظ",NameDirectory.CUSTOMER);
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();ShiftActivity a=controller.get();a.workspacePage(6);
  CompanyEntryCard card=new CompanyEntryCard(new WorkspaceForms(a,a.pages[6],2),"GAS");card.show();idle();card.chooseKind(1);assertTrue(NameDirectory.names(db).contains("سائق محفوظ"));card.quantity.setText("٥");card.driver.setText("سائق محفوظ");assertEquals("علينا 100 ر.ي",card.after.getText().toString());assertTrue(card.total.getText().toString().contains("10 ر.ي"));card.dialog.getButton(-1).performClick();idle();assertFalse(card.dialog.isShowing());assertEquals(3,ShiftWorkspace.reviewed(db,shift));controller.pause().stop().destroy();
  db.setBuyPrice("غاز",99);db.setFreightPrice("غاز",99);close();assertEquals(-100,db.supplierBalance("GAS"),0.00001);assertEquals(0,db.supplierBalance("OIL"),0.00001);assertEquals(5,db.materialSummary("غاز")[3],0.00001);assertEquals(-10,db.debtorBalance(ShiftWorkspace.customer(db,"سائق محفوظ")),0.00001);assertEquals(100,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(0,rows("cashbox_entries"));
 }
 @Test public void mixedCompaniesPostTogetherWithoutPhysicalCountsAndCannotPostTwice(){
  ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",box,0,"SAR","YER",5,"للنفط");ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",box,1,"SAR","YER",3,"للغاز");
  ShiftWorkspace.addCompanySupply(db,shift,"OIL","ديزل",10,"سالم","");ShiftWorkspace.addCompanySupply(db,shift,"GAS","غاز",5,"علي","");assertEquals(3,ShiftWorkspace.reviewed(db,shift));assertEquals(0,rows("shift_counts"));close();
  assertEquals(300,db.supplierBalance("OIL"),0.00001);assertEquals(200,db.supplierBalance("GAS"),0.00001);assertEquals(92,CashAccounts.posted(db,box,"SAR"),0.00001);assertEquals(-20,db.debtorBalance(ShiftWorkspace.customer(db,"سالم")),0.00001);assertEquals(-10,db.debtorBalance(ShiftWorkspace.customer(db,"علي")),0.00001);assertEquals(db.journalDebit(),db.journalCredit(),0.00001);assertEquals(4,rows("supplier_entries"));assertEquals(1,rows("posted_shifts"));reject(this::close);assertEquals(4,rows("supplier_entries"));
 }
 @Test public void invalidSupplyDoesNotCreateAnOperationOrAName(){
  reject(()->ShiftWorkspace.addCompanySupply(db,shift,"GAS","بترول",1,"جديد",""));reject(()->ShiftWorkspace.addCompanySupply(db,shift,"OIL","غاز",1,"جديد",""));reject(()->ShiftWorkspace.addCompanySupply(db,shift,"OIL","بترول",-1,"جديد",""));reject(()->ShiftWorkspace.addCompanySupply(db,shift,"GAS","غاز",1," ",""));assertEquals(0,rows("shift_operations"));assertEquals(0,rows("debtors"));assertFalse(NameDirectory.names(db).contains("جديد"));
 }
 @Test public void materialEditsAndDeletionKeepCashReviewButCashEditsInvalidateIt(){
  long row=ShiftWorkspace.addCompanySupply(db,shift,"OIL","بترول",1,"سالم","");ShiftWorkspace.review(db,shift,2);db.getWritableDatabase().execSQL("UPDATE shift_operations SET note='ملاحظة' WHERE id=?",new Object[]{row});assertEquals(3,ShiftWorkspace.reviewed(db,shift));ShiftWorkspace.review(db,shift,2);ShiftWorkspace.delete(db,shift,row);assertEquals(3,ShiftWorkspace.reviewed(db,shift));ShiftWorkspace.addCash(db,shift,1,"EXPENSE",box,0,"SAR","YER",1,"كهرباء");assertEquals(1,ShiftWorkspace.reviewed(db,shift));reject(()->ShiftWorkspace.review(db,shift,2));
 }
 @Test public void schema24UpgradePreservesMoneyAndOperationsAndHistoricalCounts(){
  db.paySupplier("OIL",box,100,"قديم",db.shiftDate(shift));ShiftWorkspace.addCompanySupply(db,shift,"GAS","غاز",5,"سالم","");ShiftWorkspace.count(db,shift,2,"غاز",90);db.getWritableDatabase().execSQL("INSERT INTO shift_counts VALUES(999,2,'غاز',25,25)");db.getWritableDatabase().setVersion(24);db.close();db=new Db(context);
  assertEquals(26,db.getReadableDatabase().getVersion());assertEquals(100,db.supplierBalance("OIL"),0.00001);assertEquals(9900,db.cashboxBalance(box),0.00001);assertEquals(1,rows("shift_operations"));assertNull(ShiftWorkspace.counted(db,shift,2,"غاز"));assertEquals(25,ShiftWorkspace.counted(db,999,2,"غاز"),0.00001);assertEquals(0,ShiftWorkspace.reviewed(db,shift));ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);close();assertEquals(-100,db.supplierBalance("GAS"),0.00001);
 }
}
