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
 @Before public void start(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);box=db.addCashbox("صندوق",0);db.setDefaultCashbox(box);shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);for(String m:Db.MATERIALS)db.getWritableDatabase().execSQL("INSERT INTO material_entries(material,direction,litres,entry_date,created_at) VALUES(?,'IN',1000,'2026-09-01','2026-09-01')",new Object[]{m});db.getWritableDatabase().execSQL("UPDATE readings SET current=previous+1,price=100,sales=100 WHERE shift_id=?",new Object[]{shift});db.addMovement(shift,"CASH","تسليم",db.sales(shift));}
 @After public void stop(){db.close();context.deleteDatabase("alameer_station.db");}
 int count(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
 void refuse(Runnable r){try{r.run();fail("must reject");}catch(IllegalStateException|IllegalArgumentException expected){}}
 void counts(){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM cashboxes WHERE active=1",null)){while(c.moveToNext())ShiftWorkspace.count(db,shift,1,c.getString(0),ShiftWorkspace.expectedCash(db,shift,c.getLong(0)));}for(String m:Db.MATERIALS)ShiftWorkspace.count(db,shift,2,m,ShiftWorkspace.expectedMaterial(db,shift,m));}
 void review(){for(int i=0;i<3;i++)ShiftWorkspace.review(db,shift,i);}
 void close(){db.closeAndPostShift(shift,db.soloWorkerId(),"",box);}
 @Test public void draftsDoNotChangeAnyLedgerAndSurviveReopening(){ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");ShiftWorkspace.add(db,shift,2,"BUY_CREDIT",0,0,"بترول",10,100,"فاتورة");assertEquals(0,count("journal"));assertEquals(0,count("cashbox_entries"));assertEquals(3,count("material_entries"));db.close();db=new Db(context);assertEquals(2,count("shift_operations"));assertEquals(0,db.cashboxBalance(box),0.001);}
 @Test public void reviewAllThreeAndZeroDifferenceAreRequired(){refuse(this::close);counts();ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);refuse(this::close);ShiftWorkspace.review(db,shift,2);db.addMovement(shift,"EXPENSE","فرق",1);refuse(this::review);refuse(this::close);assertTrue(db.isOpen(shift));assertEquals(0,count("journal"));}
 @Test public void editsInvalidateReviewButSavingSameReadingsDoesNot(){review();db.getWritableDatabase().execSQL("UPDATE readings SET current=current WHERE shift_id=?",new Object[]{shift});assertEquals(7,ShiftWorkspace.reviewed(db,shift));db.addMovement(shift,"DEBT","عميل",1);assertEquals(0,ShiftWorkspace.reviewed(db,shift));}
 @Test public void allSectionsPostOnceWithOneCodeAndImmutableRecords(){
  ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");ShiftWorkspace.add(db,shift,2,"BUY_CREDIT",0,0,"بترول",10,100,"فاتورة");review();close();
  assertEquals(780,db.cashboxBalance(box),0.001);assertFalse(db.isOpen(shift));assertEquals(1,count("posted_shifts"));assertTrue(count("shift_links")>0);assertEquals(2,count("shift_operations"));refuse(this::close);refuse(()->db.reopenShift(shift,"تصحيح"));refuse(()->db.unpostShift(shift));
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT row_id FROM shift_links WHERE entity='journal' LIMIT 1",null)){assertTrue(c.moveToFirst());long id=c.getLong(0);refuse(()->db.reverseEntry(id,"اختبار"));}
  ReportTable report=new ReportTable(db,shift);boolean cash=false,material=false,code=false;for(ReportTable.Row r:report.rows)for(Object v:r.cells){cash|="كهرباء\nصادر — مخاريج".equals(v);material|="فاتورة".equals(v);code|=db.shiftCode(shift).equals(v);}assertTrue(cash&&material&&code);
  boolean cashTable=false;for(ReportTable.Row r:report.rows){
   if("حركة المواد الإضافية".equals(r.cells[0]))break;
   if("المخاريج".equals(r.cells[0])&&"وارد".equals(r.cells[1])&&"صادر".equals(r.cells[2])){cashTable=true;continue;}
   if(cashTable)for(int col=0;col<3;col++)assertTrue("Cash amount columns must contain only numbers or blanks",r.cells[col]==null||"".equals(r.cells[col])||r.cells[col] instanceof Number||r.cells[col] instanceof XlsxWorkbook.Formula);
  }assertTrue(cashTable);
  long next=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,next);assertNotEquals(shift,next);assertNotEquals(db.shiftCode(shift),db.shiftCode(next));
 }
 @Test public void failureInLastSectionRollsBackEverything(){
  ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");ShiftWorkspace.add(db,shift,2,"BUY_CREDIT",0,0,"بترول",10,100,"فاتورة");review();
  db.getWritableDatabase().execSQL("CREATE TRIGGER reject_supply BEFORE INSERT ON supplier_entries BEGIN SELECT RAISE(ABORT,'test'); END");
  try{close();fail();}catch(RuntimeException expected){}
  assertTrue(db.isOpen(shift));assertEquals(0,count("journal"));assertEquals(0,count("cashbox_entries"));assertEquals(3,count("material_entries"));assertEquals(0,count("shift_links"));assertEquals(0,count("posted_shifts"));assertEquals(7,ShiftWorkspace.reviewed(db,shift));
  try(Cursor c=ShiftWorkspace.operations(db,shift,1)){assertTrue(c.moveToFirst());assertEquals(0,c.getInt(8));}
 }
 @Test public void cashPurchaseAndTransferHaveBalancedRealCounterparts(){long other=db.addCashbox("ثان",0);ShiftWorkspace.add(db,shift,1,"TRANSFER",box,other,"",0,50,"تحويل");ShiftWorkspace.add(db,shift,2,"BUY_CASH",box,0,"غاز",5,100,"شراء");review();close();assertEquals(650,db.cashboxBalance(box),0.001);assertEquals(50,db.cashboxBalance(other),0.001);try(Cursor c=db.getReadableDatabase().rawQuery("SELECT SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END) FROM journal_lines",null)){c.moveToFirst();assertEquals(0,c.getDouble(0),0.000001);}}
 @Test public void schema20UpgradePreservesExistingAmounts(){
  db.addCashTransaction(box,"IN",123.45,"قديم",db.shiftDate(shift),"YER","SALE",0);
  android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();
  for(String table:new String[]{"readings","movements","shift_operations"})for(String event:new String[]{"INSERT","UPDATE","DELETE"})sql.execSQL("DROP TRIGGER IF EXISTS review_"+table+"_"+event);
  sql.execSQL("DROP TABLE shift_operations");sql.execSQL("DROP TABLE shift_workspace");sql.execSQL("DROP TABLE shift_links");sql.setVersion(20);db.close();db=new Db(context);
  assertEquals(26,db.getWritableDatabase().getVersion());assertEquals(123.45,db.cashboxBalance(box),0.000001);assertEquals(1,count("journal"));assertEquals(0,count("shift_operations"));assertEquals(0,count("shift_workspace"));
 }
 @Test public void mobileTabsAndReadOnlyLedgerCanOpen(){
  db.setSetting("name_set","1");
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).create().start().resume();
  ShiftActivity screen=controller.get();assertEquals(3,screen.tabs.length);assertEquals("مطابقة العامل",screen.tabs[0].getText().toString());screen.tabs[1].performClick();assertTrue(screen.tabs[1].isSelected());assertEquals(0,ShiftWorkspace.reviewed(db,shift));screen.tabs[2].performClick();assertTrue(screen.tabs[2].isSelected());assertEquals(0,ShiftWorkspace.reviewed(db,shift));screen.tabs[0].performClick();assertTrue(screen.tabs[0].isSelected());screen.workspacePage(2);controller.pause().stop().destroy();
  org.robolectric.android.controller.ActivityController<LedgerActivity> ledger=Robolectric.buildActivity(LedgerActivity.class,LedgerActivity.intent(context,"cashbox_entries")).create().start().resume();ledger.pause().stop().destroy();
 }
 @Test public void workerPageAutosavesReadingsAndRejectsInvalidInputWithoutNavigation(){
  db.setSetting("name_set","1");context.getSharedPreferences("reading_drafts",0).edit().clear().commit();
  db.getWritableDatabase().execSQL("UPDATE pumps SET last_reading=100,price=100");
  db.getWritableDatabase().execSQL("UPDATE readings SET previous=100,current=101 WHERE shift_id=?",new Object[]{shift});
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup();
  ShiftActivity screen=controller.get();
  assertEquals(screen.pages[0],screen.readingsBox.getParent());
  assertEquals(screen.pages[0],screen.movementsBox.getParent());
  assertNotNull(findText(screen.pages[0],"تأكيد مطابقة العامل"));
  assertNull(findText(screen.pages[0],"حفظ القراءات ومتابعة الوردية"));
  assertNull(findText(screen.pages[0],"مطابقة وتسليم الوردية"));
  screen.workspacePage(1);assertEquals(0,screen.page);screen.workspacePage(2);assertEquals(0,screen.page);
  ShiftActivity.ReadingInput input=screen.inputs.get(0);
  ShiftWorkspace.review(db,shift,1);ShiftWorkspace.review(db,shift,2);assertEquals(6,ShiftWorkspace.reviewed(db,shift));
  input.current.setText("102");
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT current,sales,price FROM readings WHERE id=?",new String[]{String.valueOf(input.id)})){
   assertTrue(c.moveToFirst());assertEquals(102,c.getDouble(0),0);assertEquals(2*c.getDouble(2),c.getDouble(1),0.00001);
  }
  assertEquals(0,ShiftWorkspace.reviewed(db,shift));assertEquals(0,count("journal"));
  input.current.setText("99");
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT current,sales FROM readings WHERE id=?",new String[]{String.valueOf(input.id)})){
   assertTrue(c.moveToFirst());assertTrue(c.isNull(0));assertEquals(0,c.getDouble(1),0);
  }
  refuse(()->ShiftWorkspace.review(db,shift,0));refuse(this::close);
  controller.pause().resume();assertEquals("99",screen.inputs.get(0).current.getText().toString());
  screen.inputs.get(0).current.setText("103");controller.pause().resume();assertEquals("103",screen.inputs.get(0).current.getText().toString());
  screen.inputs.get(0).current.setText("");controller.pause().resume();assertEquals("",screen.inputs.get(0).current.getText().toString());
  assertEquals(0,count("journal"));assertTrue(db.isOpen(shift));controller.pause().stop().destroy();
 }
 @Test public void independentReviewsAllowAnyOrderButNeverPartialPosting(){
  ShiftWorkspace.review(db,shift,2);assertEquals(4,ShiftWorkspace.reviewed(db,shift));refuse(this::close);
  ShiftWorkspace.review(db,shift,1);assertEquals(6,ShiftWorkspace.reviewed(db,shift));refuse(this::close);
  assertEquals(0,count("shift_counts"));assertEquals(0,count("journal"));
  ShiftWorkspace.review(db,shift,0);assertEquals(7,ShiftWorkspace.reviewed(db,shift));close();assertFalse(db.isOpen(shift));
 }
 @Test public void cashAndMaterialsCanBeConfirmedWhileWorkerHasDifference(){
  db.addMovement(shift,"EXPENSE","فرق",1);
  ShiftWorkspace.review(db,shift,1);ShiftWorkspace.review(db,shift,2);
  assertEquals(6,ShiftWorkspace.reviewed(db,shift));refuse(()->ShiftWorkspace.review(db,shift,0));refuse(this::close);
  assertTrue(db.isOpen(shift));assertEquals(0,count("journal"));
 }
 @Test public void navigationAndPostingButtonsNeverConfirmReviewsImplicitly(){
  db.setSetting("name_set","1");
  org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).create().start().resume();
  ShiftActivity screen=controller.get();screen.workspacePage(5);
  clickText(screen.pages[5],"متابعة إلى المواد");assertTrue(screen.tabs[2].isSelected());assertEquals(0,ShiftWorkspace.reviewed(db,shift));
  clickText(screen.pages[6],"ترحيل الوردية بالكامل");assertEquals(0,ShiftWorkspace.reviewed(db,shift));assertTrue(db.isOpen(shift));
  clickText(screen.pages[6],"تأكيد مراجعة المواد");assertEquals(4,ShiftWorkspace.reviewed(db,shift));
  screen.workspacePage(5);clickText(screen.pages[5],"تأكيد مراجعة الصناديق");assertEquals(6,ShiftWorkspace.reviewed(db,shift));
  controller.pause().stop().destroy();
 }
 void clickText(android.view.View root,String label){android.view.View found=findText(root,label);assertNotNull(label,found);found.performClick();}
 android.view.View findText(android.view.View root,String label){
  if(root instanceof android.widget.TextView&&label.equals(((android.widget.TextView)root).getText().toString()))return root;
  if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.view.View found=findText(group.getChildAt(i),label);if(found!=null)return found;}}
  return null;
 }
 @Test public void reportCannotPresentUnpostedDraftAsOfficial(){refuse(()->new ReportTable(db,shift));}
 @Test public void finalPostingDoesNotRequirePhysicalCounts(){for(int i=0;i<3;i++)ShiftWorkspace.review(db,shift,i);assertEquals(0,count("shift_counts"));close();assertFalse(db.isOpen(shift));}
 @Test public void supplyAndCompanyPaymentPostFreightOnce(){
  db.setBuyPrice("بترول",20);db.setFreightPrice("بترول",2);
  ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",box,0,300,"دفع للشركة");
  ShiftWorkspace.addSupply(db,shift,"بترول",10,"السائق سالم","");
  assertEquals(0,db.supplierBalance("OIL"),0.00001);assertEquals(100,ShiftWorkspace.expectedCompany(db,shift,"OIL"),0.00001);assertEquals(0,count("debt_entries"));
  db.setBuyPrice("بترول",99);db.setFreightPrice("بترول",99);review();close();
  assertEquals(500,db.cashboxBalance(box),0.00001);assertEquals(100,db.supplierBalance("OIL"),0.00001);
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT SUM(CASE WHEN direction='DEBT' THEN amount ELSE -amount END) FROM debt_entries e JOIN debtors d ON d.id=e.debtor_id WHERE d.name='السائق سالم'",null)){c.moveToFirst();assertEquals(-20,c.getDouble(0),0.00001);}
  assertEquals(7,ShiftWorkspace.reviewed(db,shift));assertEquals(db.journalDebit(),db.journalCredit(),0.00001);
  boolean freight=false;for(ReportTable.Row r:new ReportTable(db,shift).rows)for(Object v:r.cells)freight|="أجرة نقل مستحقة".equals(v);assertTrue(freight);refuse(this::close);
 }
 @Test public void exchangeRatesAreSnapshottedForBothBoxes(){
  db.setRate("SAR",100);db.setRate("USD",400);long sar=ShiftWorkspace.addBox(db,"سعودي","SAR",10),usd=ShiftWorkspace.addBox(db,"دولار","USD",0);
  ShiftWorkspace.addCash(db,shift,1,"TRANSFER",sar,usd,4,"صرف عملة");db.setRate("SAR",200);db.setRate("USD",500);
  assertEquals(6,ShiftWorkspace.expectedCash(db,shift,sar),0.00001);assertEquals(1,ShiftWorkspace.expectedCash(db,shift,usd),0.00001);review();close();assertEquals(6,ShiftWorkspace.nativeCash(db,sar),0.00001);assertEquals(1,ShiftWorkspace.nativeCash(db,usd),0.00001);
 }
 @Test public void addedMaterialsPaymentInvalidatesOnlyMaterialsReview(){review();ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",box,0,1,"سداد");assertEquals(3,ShiftWorkspace.reviewed(db,shift));refuse(this::close);ShiftWorkspace.review(db,shift,2);close();}
 @Test public void schema21UpgradeRetainsOperationsWithoutRequiringCounts(){
  ShiftWorkspace.add(db,shift,1,"EXPENSE",box,0,"",0,20,"كهرباء");db.getWritableDatabase().execSQL("UPDATE shift_workspace SET strict_counts=0,reviewed=7");db.getWritableDatabase().setVersion(21);db.close();db=new Db(context);assertEquals(26,db.getReadableDatabase().getVersion());assertEquals(1,count("shift_operations"));assertEquals(0,ShiftWorkspace.reviewed(db,shift));ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);ShiftWorkspace.review(db,shift,2);close();
 }

 @Test public void countInputDoesNotTreatInvalidTextAsZero(){assertNull(WorkspaceForms.validCount("."));assertNull(WorkspaceForms.validCount("1..0"));assertNull(WorkspaceForms.validCount(""));assertEquals(12.5,WorkspaceForms.validCount("١٢٫٥"),0.00001);assertEquals(0,WorkspaceForms.validCount("0"),0.00001);}
 @Test public void workerNameDoesNotRewriteEarlierReports(){ShiftWorkspace.nameWorker(db,shift,"سالم");review();close();long next=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,next);ShiftWorkspace.nameWorker(db,next,"علي");assertEquals("سالم",ShiftWorkspace.workerName(db,shift));assertEquals("علي",ShiftWorkspace.workerName(db,next));}

}
