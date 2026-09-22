package com.alameer.station.shifts;
import android.content.Context;
import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class EntryDraftTest {
 @Test public void incompleteDraftSurvivesReopeningWithoutAffectingAccounts(){
  Context c=RuntimeEnvironment.getApplication();c.deleteDatabase("alameer_station.db");
  Db db=new Db(c);long box=db.addCashbox("اختبار مسودة",0);
  EditText amount=new EditText(c),note=new EditText(c);amount.setText("125");note.setText("غير مكتمل");
  String key="test_draft";EntryDraft.clear(c,key);
  assertTrue(EntryDraft.save(c,key,"2026-09-22",amount,note));
  EditText restoredAmount=new EditText(c),restoredNote=new EditText(c);String[] date={"2026-09-01"};
  assertTrue(EntryDraft.restore(c,key,date,restoredAmount,restoredNote));
  assertEquals("125",restoredAmount.getText().toString());assertEquals("غير مكتمل",restoredNote.getText().toString());
  assertEquals("2026-09-22",date[0]);assertEquals(0,db.cashboxBalance(box),0.001);
  EntryDraft.clear(c,key);assertFalse(EntryDraft.restore(c,key,date,restoredAmount,restoredNote));
  db.close();c.deleteDatabase("alameer_station.db");
 }
 @Test public void customerDraftsAreIsolated(){
  Context c=RuntimeEnvironment.getApplication();EditText field=new EditText(c);field.setText("77");
  EntryDraft.clear(c,"one");EntryDraft.clear(c,"two");EntryDraft.save(c,"one","2026-09-22",field);
  String[] date={"2026-09-22"};assertFalse(EntryDraft.restore(c,"two",date,new EditText(c)));
  EntryDraft.clear(c,"one");
 }
}
