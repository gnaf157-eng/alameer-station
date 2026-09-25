package com.alameer.station.shifts;

import android.database.Cursor;

/** Read reporting labels from linked records, never infer expenses from free text. */
final class CashReportDetails {
 final String person,box;final boolean expense;
 CashReportDetails(String person,String box,boolean expense){this.person=person;this.box=box;this.expense=expense;}
 static CashReportDetails load(Db db,long id){
  String box="",note="",direction="";
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT b.name,e.note,e.direction FROM cashbox_entries e JOIN cashboxes b ON b.id=e.box_id WHERE e.id=?",new String[]{""+id})){if(c.moveToFirst()){box=c.getString(0);note=c.getString(1);direction=c.getString(2);}}
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT category FROM expense_entries WHERE cashbox_entry=? LIMIT 1",new String[]{""+id})){if(c.moveToFirst())return new CashReportDetails(c.getString(0),box,"OUT".equals(direction));}
  String side="IN".equals(direction)?"CREDIT":"DEBIT";
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT l.party,l.account FROM journal j JOIN journal_lines l ON l.entry_id=j.id WHERE ((j.source='CASH_EXPLICIT' AND j.source_id=?) OR j.id IN(SELECT entry_id FROM settlement_links WHERE cashbox_entry=?)) AND l.side=? AND j.reversed_by=0 AND j.reverses=0 ORDER BY l.id LIMIT 1",new String[]{""+id,""+id,side})){
   if(c.moveToFirst())return new CashReportDetails(c.getString(0).isEmpty()?note:c.getString(0),box,"OUT".equals(direction)&&Journal.EXPENSE.equals(c.getString(1)));
  }
  return new CashReportDetails(note,box,false);
 }
}
