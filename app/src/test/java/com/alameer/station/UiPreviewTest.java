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
  for(String mat:Db.MATERIALS)ShiftWorkspace.count(db,shift,2,mat,0);ShiftWorkspace.count(db,shift,1,""+box,0);
  org.robolectric.android.controller.ActivityController<HomeActivity> home=Robolectric.buildActivity(HomeActivity.class).setup();capture(home.get(),"home");home.pause().stop().destroy();ShiftWorkspace.review(db,shift,0);ShiftWorkspace.review(db,shift,1);
  org.robolectric.android.controller.ActivityController<ShiftActivity> work=Robolectric.buildActivity(ShiftActivity.class).setup();work.get().workspacePage(5);assertEquals(5,work.get().page);capture(work.get(),"cash");work.get().workspacePage(6);assertEquals(6,work.get().page);capture(work.get(),"materials");work.pause().stop().destroy();db.close();context.deleteDatabase("alameer_station.db");
 }
 private void capture(Activity a,String name) throws Exception {
  View v=a.findViewById(android.R.id.content);v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(752,View.MeasureSpec.EXACTLY));v.layout(0,0,360,752);
  Bitmap b=Bitmap.createBitmap(360,752,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/reports/ui");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();
 }
}
