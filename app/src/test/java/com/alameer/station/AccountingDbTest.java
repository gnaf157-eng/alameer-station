package com.alameer.station.shifts;
import android.content.Context;
import android.database.Cursor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AccountingDbTest {
    Db db;Context context;String date="2026-09-19";long box;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);box=db.addCashbox("صندوق اختبار",0);}
    @After public void teardown(){db.close();context.deleteDatabase("alameer_station.db");}
    int count(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
    double account(String name){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN side='DEBIT' THEN amount ELSE -amount END),0) FROM journal_lines WHERE account=?",new String[]{name})){c.moveToFirst();return c.getDouble(0);}}
    void refused(Runnable action){try{action.run();fail("operation must be refused");}catch(IllegalStateException|IllegalArgumentException expected){}}
    @Test public void lockedPeriodCannotWriteAnyLedger(){db.lockPeriod("2026-09","test");refused(()->db.addCashboxEntry(box,"IN",10,"test",date));assertEquals(0,count("cashbox_entries"));assertEquals(0,count("journal"));}
    @Test public void journalFailureRollsBackCash(){db.getWritableDatabase().execSQL("CREATE TRIGGER reject_journal BEFORE INSERT ON journal BEGIN SELECT RAISE(ABORT,'test failure'); END");try{db.addCashboxEntry(box,"IN",10,"test",date);fail();}catch(RuntimeException expected){}assertEquals(0,count("cashbox_entries"));}
    @Test public void repeatedEditsOnlyReverseLiveOriginal(){long id=db.addCashboxEntry(box,"IN",100,"test",date);db.updateCashboxEntry(id,"IN",200,"test",date,"YER");db.updateCashboxEntry(id,"IN",300,"test",date,"YER");assertEquals(300,db.cashboxBalance(box),0.001);assertEquals(300,account(Journal.CASH),0.001);assertTrue(db.deleteCashboxEntry(id));assertEquals(0,account(Journal.CASH),0.001);}
    @Test public void lockedEditCannotAlterOriginal(){long id=db.addCashboxEntry(box,"IN",100,"test",date);db.lockPeriod("2026-09","test");refused(()->db.updateCashboxEntry(id,"OUT",200,"new","2026-10-01","YER"));refused(()->db.deleteCashboxEntry(id));assertEquals(100,db.cashboxBalance(box),0.001);assertEquals(1,count("journal"));}
    @Test public void editKeepsStoredExchangeRate(){db.setRate("SAR",139.5);long id=db.addCashboxEntry(box,"IN",100,"test",date,"SAR");db.setRate("SAR",200);db.updateCashboxEntry(id,"IN",100,"changed note",date,"SAR");assertEquals(13950,db.cashboxBalance(box),0.001);assertEquals(13950,account(Journal.CASH),0.001);}
    @Test public void linkedSupplierCashCannotBeEditedAlone(){long id=db.paySupplier("OIL",box,500,"test",date);long cash;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_entry FROM supplier_entries WHERE id=?",new String[]{""+id})){c.moveToFirst();cash=c.getLong(0);}refused(()->db.updateCashboxEntry(cash,"IN",900,"test",date,"YER"));db.voidSupplierEntry(id);assertEquals(0,db.cashboxBalance(box),0.001);assertEquals(0,account(Journal.CASH),0.001);}
    @Test public void supplierTransferVoidRestoresDebtor(){long who=db.addDebtor("عميل", "",0);db.addDebtEntry(who,"DEBT",500,"test",date);long id=db.moveDebtorToSupplier(who,"OIL","test",date);assertEquals(0,db.debtorBalance(who),0.001);assertEquals(-500,db.supplierBalance("OIL"),0.001);db.voidSupplierEntry(id);assertEquals(500,db.debtorBalance(who),0.001);assertEquals(0,db.supplierBalance("OIL"),0.001);assertEquals(500,account(Journal.RECEIVABLE),0.001);}
    @Test public void expenseDeleteRestoresCash(){long id=db.addExpense("زيت",100,"test",date,box,0);assertEquals(-100,db.cashboxBalance(box),0.001);db.deleteExpense(id);assertEquals(0,db.cashboxBalance(box),0.001);assertEquals(0,account(Journal.CASH),0.001);assertEquals(0,account(Journal.EXPENSE),0.001);}
    @Test public void openingCanOnlyBePostedOnce(){db.setCashboxOpening(box,-100);db.postOpeningBalances(date);assertEquals(-100,account(Journal.CASH),0.001);refused(()->db.postOpeningBalances(date));refused(()->db.setCashboxOpening(box,200));assertEquals(1,count("journal"));}
    @Test public void archivedAccountsStillIncluded(){long who=db.addDebtor("عميل","",100);db.setDebtorActive(who,false);db.setCashboxOpening(box,200);db.setCashboxActive(box,false);assertEquals(100,db.debtsTotal(),0.001);assertEquals(200,db.cashboxesTotal(),0.001);}
    @Test public void reopenRepostDoesNotDuplicateOrLoseJournal(){
        long shift=db.openSoloShift(db.soloWorkerId());
        db.getWritableDatabase().execSQL("UPDATE readings SET current=previous+1,price=100,sales=100 WHERE shift_id=?",new Object[]{shift});
        double sales=db.sales(shift);db.addMovement(shift,"CASH","نقد",sales);db.submit(shift,db.soloWorkerId(),"");db.approve(shift);
        db.postShift(shift,box);db.journalShift(shift);assertEquals(sales,account(Journal.CASH),0.001);
        db.reopenShift(shift,"تصحيح");assertFalse(db.shiftPosted(shift));assertFalse(db.shiftJournalled(shift));assertEquals(0,account(Journal.CASH),0.001);
        db.submit(shift,db.soloWorkerId(),"");db.approve(shift);db.postShift(shift,box);db.journalShift(shift);db.postShift(shift,box);db.journalShift(shift);
        assertEquals(sales,db.cashboxBalance(box),0.001);assertEquals(sales,account(Journal.CASH),0.001);assertEquals(1,count("posted_shifts"));
    }
    @Test public void invalidCurrencyAndRatesAreRejected(){refused(()->db.setRate("SAR",Double.POSITIVE_INFINITY));refused(()->db.addCashboxEntry(box,"IN",1,"",date,"BAD"));assertEquals(0,count("cashbox_entries"));}
    @Test public void upgrade19PreservesEveryExistingRow(){long id=db.addCashboxEntry(box,"IN",123,"keep",date);String before;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT amount||note||entry_date FROM cashbox_entries WHERE id=?",new String[]{""+id})){c.moveToFirst();before=c.getString(0);}db.onUpgrade(db.getWritableDatabase(),19,20);try(Cursor c=db.getReadableDatabase().rawQuery("SELECT amount||note||entry_date FROM cashbox_entries WHERE id=?",new String[]{""+id})){assertTrue(c.moveToFirst());assertEquals(before,c.getString(0));}}
}
