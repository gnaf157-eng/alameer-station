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
    @Test public void journalFailureRollsBackCash(){db.getWritableDatabase().execSQL("CREATE TRIGGER reject_journal BEFORE INSERT ON journal BEGIN SELECT RAISE(ABORT,'test failure'); END");try{db.addCashTransaction(box,"IN",10,"test",date,"YER","SALE",0);fail();}catch(RuntimeException expected){}assertEquals(0,count("cashbox_entries"));}
    @Test public void legacyEditsCannotReintroduceSuspenseAndCancellationStillWorks(){long id=legacyCash(100,"test","YER");refused(()->db.updateCashboxEntry(id,"IN",200,"test",date,"YER"));refused(()->db.updateCashboxEntry(id,"IN",300,"test",date,"YER"));assertEquals(100,db.cashboxBalance(box),0.001);assertEquals(100,account(Journal.CASH),0.001);assertEquals(1,count("journal"));assertTrue(db.deleteCashboxEntry(id));assertEquals(0,account(Journal.CASH),0.001);}
    @Test public void lockedEditCannotAlterOriginal(){long id=legacyCash(100,"test","YER");db.lockPeriod("2026-09","test");refused(()->db.updateCashboxEntry(id,"OUT",200,"new","2026-10-01","YER"));refused(()->db.deleteCashboxEntry(id));assertEquals(100,db.cashboxBalance(box),0.001);assertEquals(1,count("journal"));}
    @Test public void rejectedLegacyEditKeepsStoredExchangeRate(){db.setRate("SAR",139.5);long id=legacyCash(100,"test","SAR");db.setRate("SAR",200);refused(()->db.updateCashboxEntry(id,"IN",100,"changed note",date,"SAR"));assertEquals(13950,db.cashboxBalance(box),0.001);assertEquals(13950,account(Journal.CASH),0.001);}
    @Test public void linkedSupplierCashCannotBeEditedAlone(){long id=db.paySupplier("OIL",box,500,"test",date);long cash;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT cashbox_entry FROM supplier_entries WHERE id=?",new String[]{""+id})){c.moveToFirst();cash=c.getLong(0);}refused(()->db.updateCashboxEntry(cash,"IN",900,"test",date,"YER"));db.voidSupplierEntry(id);assertEquals(0,db.cashboxBalance(box),0.001);assertEquals(0,account(Journal.CASH),0.001);}
    @Test public void supplierTransferVoidRestoresDebtor(){long who=db.addDebtor("عميل", "",0);db.addCustomerTransaction(who,"CREDIT_SALE",0,500,"test",date);long id=db.moveDebtorToSupplier(who,"OIL","test",date);assertEquals(0,db.debtorBalance(who),0.001);assertEquals(500,db.supplierBalance("OIL"),0.001);db.voidSupplierEntry(id);assertEquals(500,db.debtorBalance(who),0.001);assertEquals(0,db.supplierBalance("OIL"),0.001);assertEquals(500,account(Journal.RECEIVABLE),0.001);}
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
    @Test public void upgrade19PreservesEveryExistingRow(){long id=legacyCash(123,"keep","YER");String before;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT amount||note||entry_date FROM cashbox_entries WHERE id=?",new String[]{""+id})){c.moveToFirst();before=c.getString(0);}db.onUpgrade(db.getWritableDatabase(),19,20);try(Cursor c=db.getReadableDatabase().rawQuery("SELECT amount||note||entry_date FROM cashbox_entries WHERE id=?",new String[]{""+id})){assertTrue(c.moveToFirst());assertEquals(before,c.getString(0));}}

    long filledShift(){long id=db.openSoloShift(db.soloWorkerId());db.getWritableDatabase().execSQL("UPDATE readings SET current=previous+1,price=100,sales=100 WHERE shift_id=?",new Object[]{id});db.addMovement(id,"CASH","نقد",db.sales(id));return id;}
    long sourceJournal(String source,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM journal WHERE source=? AND source_id=? AND reversed_by=0 AND reverses=0",new String[]{source,""+id})){assertTrue(c.moveToFirst());return c.getLong(0);}}
    @Test public void failedCloseKeepsStatusPumpsAndLedgers(){
        long shift=filledShift();db.getWritableDatabase().execSQL("CREATE TRIGGER reject_journal BEFORE INSERT ON journal BEGIN SELECT RAISE(ABORT,'test'); END");
        try{db.closeAndPostShift(shift,db.soloWorkerId(),"",box);fail();}catch(RuntimeException expected){}
        assertTrue(db.isOpen(shift));assertEquals(0,count("posted_shifts"));assertEquals(0,count("cashbox_entries"));
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT SUM(last_reading) FROM pumps",null)){c.moveToFirst();assertEquals(0,c.getDouble(0),0.001);}
    }
    @Test public void reopenedShiftCannotRewindCurrentPumpReadings(){
        long shift=filledShift();db.closeAndPostShift(shift,db.soloWorkerId(),"",box);
        db.getWritableDatabase().execSQL("UPDATE pumps SET last_reading=500,price=999");
        db.reopenShift(shift,"تصحيح");db.syncShiftWithSettings(shift);assertEquals(800,db.sales(shift),0.001);
        db.closeAndPostShift(shift,db.soloWorkerId(),"",box);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT MIN(last_reading) FROM pumps",null)){c.moveToFirst();assertEquals(500,c.getDouble(0),0.001);}
    }
    @Test public void settlementAndSourceDeletionRestoreBothLedgers(){
        long debtor=db.addDebtor("عميل","",0);db.addCustomerTransaction(debtor,"CREDIT_SALE",0,100,"بيع",date);
        long cash=legacyCash(100,"سداد","YER");
        db.settleSuspense(sourceJournal("CASHBOX",cash),Journal.RECEIVABLE,"عميل","سداد");assertEquals(0,db.debtorBalance(debtor),0.001);
        db.deleteCashboxEntry(cash);assertEquals(100,db.debtorBalance(debtor),0.001);assertEquals(100,account(Journal.RECEIVABLE),0.001);assertEquals(0,count("settlement_links"));
    }
    @Test public void settlementRequiresExistingDebtor(){long cash=legacyCash(100,"سداد","YER");refused(()->db.settleSuspense(sourceJournal("CASHBOX",cash),Journal.RECEIVABLE,"غير موجود","سداد"));assertEquals(1,count("journal"));}
    @Test public void cashSettlementWritesDefaultCashboxAndReversesIt(){
        db.setDefaultCashbox(box);long debtor=db.addDebtor("عميل","",0);long debt=legacyDebt(debtor,100,"سلفة");
        db.settleSuspense(sourceJournal("DEBT",debt),Journal.CASH,"","سلفة نقدية");assertEquals(-100,db.cashboxBalance(box),0.001);
        db.deleteDebtEntry(debt);assertEquals(0,db.cashboxBalance(box),0.001);assertEquals(0,account(Journal.CASH),0.001);
    }
    @Test public void backupIncludesCommittedWalAndOpeningData()throws Exception{
        db.getWritableDatabase().enableWriteAheadLogging();db.setCashboxOpening(box,250);db.addCashTransaction(box,"IN",123,"حركة أخيرة",date,"YER","SALE",0);
        java.io.File target=new java.io.File(context.getCacheDir(),"snapshot-test.db");Backup.snapshot(db,target);
        try(android.database.sqlite.SQLiteDatabase copy=android.database.sqlite.SQLiteDatabase.openDatabase(target.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
            try(Cursor c=copy.rawQuery("SELECT amount,note FROM cashbox_entries",null)){assertTrue(c.moveToFirst());assertEquals(123,c.getDouble(0),0.001);assertEquals("حركة أخيرة",c.getString(1));}
            assertEquals(21,copy.getVersion());
        }finally{target.delete();}
    }
    @Test public void backupRejectsFutureSchema()throws Exception{
        java.io.File target=new java.io.File(context.getCacheDir(),"future-test.db");Backup.snapshot(db,target);
        try(android.database.sqlite.SQLiteDatabase copy=android.database.sqlite.SQLiteDatabase.openDatabase(target.getPath(),null,0)){copy.setVersion(99);}
        assertFalse(Backup.validDatabase(target));target.delete();
    }
    @Test public void parserHandlesArabicAndRejectsNonFinite(){assertEquals(1234.5,Calc.number("١٬٢٣٤٫٥"),0.0001);assertEquals(1234.5,Calc.number("۱٬۲۳۴٫۵"),0.0001);assertEquals(0,Calc.number("NaN"),0);assertEquals(0,Calc.number("Infinity"),0);}

    @Test public void closedShiftCannotChangeMovementsOrReadings(){
        long shift=filledShift();long movement,reading;
        try(Cursor c=db.movements(shift)){c.moveToFirst();movement=c.getLong(0);}
        try(Cursor c=db.shiftReadings(shift)){c.moveToFirst();reading=c.getLong(0);}
        db.closeAndPostShift(shift,db.soloWorkerId(),"",box);
        refused(()->db.addMovement(shift,"CASH","خطأ",1));refused(()->db.deleteMovement(movement));
        refused(()->db.updateMovement(movement,"CASH","خطأ",1));assertFalse(db.saveReading(reading,999));assertFalse(db.savePrevious(reading,0));
    }
    @Test public void supplierLiabilitiesDoNotNetAgainstOtherSupplierAdvances(){
        db.getWritableDatabase().execSQL("INSERT INTO supplier_entries(kind,amount,supplier,entry_date,created_at) VALUES('BUY',500,'OIL',?,?)",new Object[]{date,date});
        db.getWritableDatabase().execSQL("INSERT INTO supplier_entries(kind,amount,supplier,entry_date,created_at) VALUES('PAY',700,'GAS',?,?)",new Object[]{date,date});
        assertEquals(200,db.supplierBalance(),0.001);assertEquals(500,db.supplierOwed(),0.001);
    }

    @Test public void staleSyncAcknowledgementDoesNotClearNewRevision(){
        long shift=filledShift();db.closeAndPostShift(shift,db.soloWorkerId(),"",box);int revision;
        try(Cursor c=db.pendingSync()){assertTrue(c.moveToFirst());revision=c.getInt(12);}
        db.reopenShift(shift,"تصحيح");assertFalse(db.markSynced(shift,revision));
        db.closeAndPostShift(shift,db.soloWorkerId(),"",box);assertFalse(db.markSynced(shift,revision));assertTrue(db.markSynced(shift,revision+1));
    }
    @Test public void actual19SchemaGetsAdditiveColumnsWithoutChangingAmounts(){
        android.database.sqlite.SQLiteDatabase old=android.database.sqlite.SQLiteDatabase.create(null);
        try{
            old.execSQL("CREATE TABLE cashbox_entries(id INTEGER PRIMARY KEY,amount REAL,note TEXT)");
            old.execSQL("CREATE TABLE debt_entries(id INTEGER PRIMARY KEY,amount REAL)");
            old.execSQL("CREATE TABLE expense_entries(id INTEGER PRIMARY KEY,amount REAL)");
            old.execSQL("CREATE TABLE supplier_entries(id INTEGER PRIMARY KEY,amount REAL)");
            old.execSQL("INSERT INTO cashbox_entries VALUES(41,123.45,'keep')");
            db.onUpgrade(old,19,20);
            try(Cursor c=old.rawQuery("SELECT id,amount,note,managed FROM cashbox_entries",null)){
                assertTrue(c.moveToFirst());assertEquals(41,c.getLong(0));assertEquals(123.45,c.getDouble(1),0.000001);assertEquals("keep",c.getString(2));assertEquals(0,c.getInt(3));
            }
        }finally{old.close();}
    }

    @Test public void nonzeroShiftCannotCloseOrPostEvenWithReason(){
        for(double amount:new double[]{1,-1,0.001,-0.001}){
            long shift=filledShift();
            db.addMovement(shift,amount>0?"COLLECTION":"EXPENSE","اختبار فرق",Math.abs(amount));
            refused(()->db.closeAndPostShift(shift,db.soloWorkerId(),"سبب مكتوب",box));
            refused(()->db.submit(shift,db.soloWorkerId(),"سبب مكتوب"));
            refused(()->db.approve(shift));
            refused(()->db.closeUnmatched(shift));
            refused(()->db.postShift(shift,box));
            refused(()->db.journalShift(shift));
            assertTrue(db.isOpen(shift));
            assertFalse(db.shiftPosted(shift));
            assertFalse(db.shiftJournalled(shift));
            db.addMovement(shift,amount>0?"CASH":"COLLECTION","تصحيح الفرق",Math.abs(amount));
            db.closeAndPostShift(shift,db.soloWorkerId(),"",box);
            assertFalse(db.isOpen(shift));
        }
    }

    @Test public void explicitCollectionUpdatesBothLedgersAndCancellationRestoresBoth(){
        long person=db.addDebtor("عميل مباشر","",0);
        db.addCustomerTransaction(person,"CREDIT_SALE",0,200,"فاتورة مستقلة",date);
        long receipt=db.addCustomerTransaction(person,"COLLECTION",box,80,"قبض",date);
        assertEquals(120,db.debtorBalance(person),0.001);
        assertEquals(80,db.cashboxBalance(box),0.001);
        assertEquals(0,account(Journal.SUSPENSE),0.001);
        assertEquals(120,account(Journal.RECEIVABLE),0.001);
        long cashId;
        try(Cursor c=db.cashboxEntries(box,10)){assertTrue(c.moveToFirst());cashId=c.getLong(0);}
        final long linked=cashId;refused(()->db.deleteCashboxEntry(linked));
        assertTrue(db.deleteDebtEntry(receipt));
        assertEquals(200,db.debtorBalance(person),0.001);
        assertEquals(0,db.cashboxBalance(box),0.001);
        assertEquals(200,account(Journal.RECEIVABLE),0.001);
    }
    @Test public void explicitLoanMovesCashWithoutSalesAndReverses(){
        long person=db.addDebtor("مستلف","",0);
        long id=db.addCustomerTransaction(person,"CASH_LOAN",box,50,"سلفة",date);
        assertEquals(50,db.debtorBalance(person),0.001);
        assertEquals(-50,db.cashboxBalance(box),0.001);
        assertEquals(0,account(Journal.SALES),0.001);
        db.deleteDebtEntry(id);
        assertEquals(0,db.debtorBalance(person),0.001);
        assertEquals(0,db.cashboxBalance(box),0.001);
    }
    @Test public void explicitCustomerFailureLeavesNoHalfTransaction(){
        long person=db.addDebtor("عميل اختبار","",0);
        refused(()->db.addCustomerTransaction(person,"COLLECTION",0,10,"",date));
        assertEquals(0,count("debt_entries"));
        db.getWritableDatabase().execSQL("CREATE TRIGGER reject_explicit BEFORE INSERT ON journal BEGIN SELECT RAISE(ABORT,'test'); END");
        try{db.addCustomerTransaction(person,"COLLECTION",box,10,"",date);fail();}catch(RuntimeException expected){}
        assertEquals(0,count("debt_entries"));assertEquals(0,count("cashbox_entries"));
        assertEquals(0,count("settlement_links"));
    }

    @Test public void explicitCashTransferCancelsBothBoxes(){
        long other=db.addCashbox("الصندوق الآخر",0);
        long id=db.addCashTransaction(box,"OUT",40,"تحويل",date,"YER","TRANSFER",other);
        assertEquals(-40,db.cashboxBalance(box),0.001);assertEquals(40,db.cashboxBalance(other),0.001);
        assertEquals(0,account(Journal.CASH),0.001);assertEquals(0,account(Journal.SUSPENSE),0.001);
        db.deleteCashboxEntry(id);
        assertEquals(0,db.cashboxBalance(box),0.001);assertEquals(0,db.cashboxBalance(other),0.001);
    }
    @Test public void explicitCashCollectionAndExpenseHaveLinkedCounterparts(){
        long who=db.addDebtor("عميل الصندوق","",0);
        long receipt=db.addCashTransaction(box,"IN",60,"تحصيل",date,"YER","CUSTOMER",who);
        assertEquals(-60,db.debtorBalance(who),0.001);
        assertEquals(60,db.cashboxBalance(box),0.001);
        db.deleteCashboxEntry(receipt);assertEquals(0,db.debtorBalance(who),0.001);
        long expense=db.addCashTransaction(box,"OUT",25,"كهرباء",date,"YER","EXPENSE",0);
        assertEquals(1,count("expense_entries"));assertEquals(25,account(Journal.EXPENSE),0.001);
        db.deleteCashboxEntry(expense);assertEquals(0,count("expense_entries"));
        assertEquals(0,account(Journal.EXPENSE),0.001);assertEquals(0,db.cashboxBalance(box),0.001);
    }

    // Simulate persisted pre-migration records in the test database only.
    long legacyCash(double amount,String note,String currency){
        long id=db.addCashTransaction(box,"IN",amount,note,date,currency,"SALE",0);
        legacyJournal("CASH_EXPLICIT","CASHBOX",id);return id;
    }
    long legacyDebt(long person,double amount,String note){
        long id=db.addCustomerTransaction(person,"CREDIT_SALE",0,amount,note,date);
        legacyJournal("DEBT_EXPLICIT","DEBT",id);return id;
    }
    void legacyJournal(String from,String to,long id){
        long journal=sourceJournal(from,id);
        db.getWritableDatabase().execSQL("UPDATE journal_lines SET account=? WHERE entry_id=? AND account=?",new Object[]{Journal.SUSPENSE,journal,Journal.SALES});
        db.getWritableDatabase().execSQL("UPDATE journal SET source=? WHERE id=?",new Object[]{to,journal});
    }
    @Test public void allNewSuspensePathsAreRejectedWithoutPartialRows(){
        long who=db.addDebtor("عميل","",0);
        refused(()->db.addCashboxEntry(box,"IN",10,"قبض",date));
        refused(()->db.addCashboxEntry(box,"OUT",10,"صرف",date));
        refused(()->db.addDebtEntry(who,"DEBT",10,"دين",date));
        refused(()->db.addDebtEntry(who,"PAID",10,"سداد",date));
        refused(()->db.addExpense("مصروف",10,"مصروف بلا صندوق",date,0,0));
        refused(()->db.postEntry(Journal.simple("اختبار",date,"TEST",1,Journal.CASH,Journal.SUSPENSE,10,"")));
        assertEquals(0,count("journal"));assertEquals(0,count("journal_lines"));
        assertEquals(0,count("cashbox_entries"));assertEquals(0,count("debt_entries"));
        assertEquals(0,count("expense_entries"));assertEquals(0,count("settlement_links"));
    }
}

