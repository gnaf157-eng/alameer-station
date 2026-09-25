package com.alameer.station.shifts;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.print.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="ar-rSA-w360dp-h800dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LedgerPdfTest {
 Context context;Db db;long customer;
 @Before public void start(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);db.setSetting("station_name","محطة الأمير");customer=db.addDebtor("السائق سالم عبدالله","",1000);}
 @After public void stop(){db.close();context.deleteDatabase("alameer_station.db");}
 View findText(View root,String part){if(root instanceof TextView&&((TextView)root).getText().toString().contains(part))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=findText(((ViewGroup)root).getChildAt(i),part);if(found!=null)return found;}return null;}
 @Test public void exportOpensOnlyInsideTheSelectedAccountAndOffersDateRangeAndBothOutputs()throws Exception{
  org.robolectric.android.controller.ActivityController<LedgerActivity> controller=Robolectric.buildActivity(LedgerActivity.class,LedgerActivity.intent(context,"debt_entries")).setup();LedgerActivity a=controller.get();View root=a.findViewById(android.R.id.content);assertNull(root.findViewWithTag("ledger-export"));View account=findText(root,"السائق سالم عبدالله");assertNotNull(account);account.performClick();View button=root.findViewWithTag("ledger-export");assertNotNull(button);button.performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());assertNotNull(dialog.findViewById(android.R.id.content).findViewWithTag("ledger-from"));assertNotNull(dialog.findViewById(android.R.id.content).findViewWithTag("ledger-print"));assertNotNull(dialog.findViewById(android.R.id.content).findViewWithTag("ledger-share"));
  View decor=dialog.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(752,View.MeasureSpec.EXACTLY));decor.layout(0,0,360,752);Shadows.shadowOf(Looper.getMainLooper()).idle();Bitmap bitmap=Bitmap.createBitmap(360,752,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));File dir=new File("build/reports/ui");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,"ledger-export.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();dialog.dismiss();controller.pause().stop().destroy();
 }
}
