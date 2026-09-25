package com.alameer.station.shifts;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

/** Native SQLite transaction and backup verification, with invented data only. */
@RunWith(AndroidJUnit4.class)
public class OpeningImportDeviceTest {
 @Test public void privateOpeningAndFirstShiftSurviveNativeBackup()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.deleteDatabase("alameer_station.db");Db db=new Db(c);
  try{
   JSONObject p=new JSONObject();p.put("format","alameer-opening-v1");p.put("date","2026-01-01");p.put("capital",240);p.put("gasOpeningProfit",0);
   p.put("rates",new JSONObject().put("SAR",10).put("USD",20));
   p.put("cashboxes",new JSONArray().put(new JSONObject().put("name","صندوق اختبار").put("balances",new JSONObject().put("YER",100))));
   p.put("debtors",new JSONArray());p.put("offsite",new JSONArray());
   p.put("workers",new JSONArray().put(new JSONObject().put("name","عامل اختبار").put("balance",500)));
   p.put("suppliers",new JSONArray().put(new JSONObject().put("code","OIL").put("balance",50)).put(new JSONObject().put("code","GAS").put("balance",-10)));
   JSONArray materials=new JSONArray();for(String m:Db.MATERIALS)materials.put(new JSONObject().put("name",m).put("quantity",m.equals("بترول")?10:0).put("buy",10).put("freight",0).put("sell",12));p.put("materials",materials);
   p.put("pumps",new JSONArray().put(new JSONObject().put("name","عداد اختبار").put("fuel","بترول").put("reading",10)));
   db.addCashbox("قديم",5);p.put("capital",241);try{OpeningImport.apply(db,p,"invalid");fail();}catch(IllegalStateException expected){}assertEquals(5,db.cashboxesTotal(),0);
   p.put("capital",240);OpeningImport.apply(db,p,"native-synthetic");assertEquals(240,Capital.actual(db),0.000001);
   long shift=db.openSoloShift(db.soloWorkerId());ShiftWorkspace.ensure(db,shift);db.getWritableDatabase().execSQL("UPDATE shifts SET shift_date='2026-01-02' WHERE id=?",new Object[]{shift});
   db.getWritableDatabase().execSQL("UPDATE readings SET current=previous+1,sales=price WHERE shift_id=?",new Object[]{shift});db.addMovement(shift,"CASH","استلام",12);for(int i=0;i<3;i++)ShiftWorkspace.review(db,shift,i);
   db.closeAndPostShift(shift,db.soloWorkerId(),"",db.defaultCashbox());assertEquals(242,Capital.actual(db),0.000001);assertEquals(0,Capital.scalar(db,"SELECT gap FROM capital_checks"),0);
   assertEquals(db.journalDebit(),db.journalCredit(),0.000001);assertEquals(90,Capital.scalar(db,"SELECT SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END) FROM journal_lines WHERE account='مخزون الوقود'"),0.000001);
   File backup=new File(c.getCacheDir(),"opening-native.db");Backup.snapshot(db,backup);assertTrue(Backup.validDatabase(backup));backup.delete();
   assertTrue(new ReportTable(db,shift).rows.size()>0);
  }finally{db.close();c.deleteDatabase("alameer_station.db");}
 }
}
