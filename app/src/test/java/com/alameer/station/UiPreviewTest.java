package com.alameer.station.shifts;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="ar-rSA-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiPreviewTest {
 @Test public void nativePhoneScreensRender() throws Exception {
  Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");Db db=new Db(context);db.setSetting("name_set","1");db.setSetting("station_name","محطة الأمير");db.setTelegramOn(false);long box=db.addCashbox("صندوق المحطة",0);db.setDefaultCashbox(box);for(String mat:Db.MATERIALS)db.setFuelPrice(mat,100);
  long shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);db.getWritableDatabase().execSQL("UPDATE readings SET current=previous,price=100,sales=0 WHERE shift_id=?",new Object[]{shift});
  for(String mat:Db.MATERIALS)ShiftWorkspace.count(db,shift,2,mat,0);
  org.robolectric.android.controller.ActivityController<HomeActivity> home=Robolectric.buildActivity(HomeActivity.class).setup();capture(home.get(),"home");home.pause().stop().destroy();ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);
  CashAccounts.setOpening(db,box,"SAR",500);long second=db.addCashbox("الصراف",120000);db.addCashbox("درج المحطة",15000);db.addCashbox("صندوق البيت",20000);NameDirectory.remember(db,"كهرباء",NameDirectory.EXPENSE);ShiftWorkspace.review(db,shift,1);
  org.robolectric.android.controller.ActivityController<ShiftActivity> work=Robolectric.buildActivity(ShiftActivity.class).setup();work.get().workspacePage(5);assertEquals(5,work.get().page);capture(work.get(),"cash");WorkspaceForms host=new WorkspaceForms(work.get(),work.get().pages[5],1);CashEntryCard entry=new CashEntryCard(host,box);entry.chooseKind(1);entry.show();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();entry.currency.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();entry.person.setText("كهرباء");entry.amount.setText("50");captureView(entry.dialog.getWindow().getDecorView(),"cash-entry");entry.dialog.dismiss();ShiftWorkspace.review(db,shift,1);work.get().workspacePage(6);assertEquals(6,work.get().page);capture(work.get(),"materials");
  db.addMovement(shift,"COLLECTION","محمد — تحصيل",400);
  db.addMovement(shift,"CASH","تسليم العامل",250);
  db.addMovement(shift,"DEBT","أحمد — آجل",100);
  db.addMovement(shift,"EXPENSE","كهرباء",50);
  work.pause().resume();work.get().workspacePage(1);capture(work.get(),"worker-entry-icons");
  android.graphics.Rect movementBounds=new android.graphics.Rect(0,0,work.get().movementsBox.getWidth(),work.get().movementsBox.getHeight());
  work.get().screenScroll.offsetDescendantRectToMyCoords(work.get().movementsBox,movementBounds);
  work.get().screenScroll.scrollTo(0,Math.max(0,movementBounds.top-28));
  capture(work.get(),"worker-movements");
  work.pause().stop().destroy();db.close();context.deleteDatabase("alameer_station.db");
 }
 private void capture(Activity a,String name) throws Exception {
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  captureView(a.findViewById(android.R.id.content),name);
 }
 private void captureView(View v,String name) throws Exception {
  v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(752,View.MeasureSpec.EXACTLY));v.layout(0,0,360,752);
  Bitmap b=Bitmap.createBitmap(360,752,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/reports/ui");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();
 }
}
