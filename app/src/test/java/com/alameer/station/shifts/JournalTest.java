package com.alameer.station.shifts;

import org.junit.Test;

import static org.junit.Assert.*;

public class JournalTest {

    private Journal.Entry entry() {
        return new Journal.Entry("اختبار", "2026-09-16", "TEST", 1);
    }

    @Test public void balancedEntryIsAccepted() {
        Journal.Entry e = entry().debit(Journal.CASH, 5000, "").credit(Journal.SALES, 5000, "");
        assertTrue(e.balanced());
        assertEquals("", Journal.rejectReason(e));
        assertTrue(Journal.valid(e));
    }

    @Test public void unbalancedEntryIsRejected() {
        Journal.Entry e = entry().debit(Journal.CASH, 5000, "").credit(Journal.SALES, 4000, "");
        assertFalse(e.balanced());
        assertTrue(Journal.rejectReason(e).contains("غير متوازن"));
    }

    @Test public void oneSidedEntryIsRejected() {
        Journal.Entry e = entry().debit(Journal.CASH, 1000, "");
        assertTrue(Journal.rejectReason(e).contains("طرفًا مدينًا"));
    }

    @Test public void zeroAndNegativeAmountsAreRefusedAtTheLine() {
        try {
            entry().debit(Journal.CASH, 0, "");
            fail("يجب رفض المبلغ الصفري");
        } catch (IllegalArgumentException expected) { }
        try {
            entry().credit(Journal.SALES, -50, "");
            fail("يجب رفض المبلغ السالب");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void multiLineShiftEntryBalances() {
        // مبيعات 100000 ومقبوضات 20000 مقابل نقد 90000 ودين 20000 ومخاريج 10000.
        Journal.Entry e = Journal.shiftEntry(7, "2026-09-16", 100000, 20000, 90000, 20000, 10000, 0);
        assertTrue(Journal.valid(e));
        assertEquals(120000, e.totalDebit(), 0.001);
        assertEquals(120000, e.totalCredit(), 0.001);
    }

    @Test public void shiftShortageLandsOnTheWorkerAccount() {
        // فرق 3000 باقٍ مع العامل يجعل القيد متوازنًا بقيده على عهدته.
        Journal.Entry e = Journal.shiftEntry(8, "2026-09-16", 100000, 0, 90000, 5000, 2000, 3000);
        assertTrue(Journal.valid(e));
        boolean worker = false;
        for (Journal.Line l : e.lines)
            if (Journal.WORKER.equals(l.account)) { worker = true; assertTrue(l.debit()); }
        assertTrue(worker);
    }

    @Test public void reversalMirrorsEverySide() {
        Journal.Entry e = entry().debit(Journal.CASH, 5000, "").credit(Journal.SALES, 5000, "");
        Journal.Entry r = Journal.reverseOf(e, "خطأ إدخال", "2026-09-17");
        assertTrue(Journal.valid(r));
        assertEquals(e.totalDebit(), r.totalCredit(), 0.001);
        assertEquals(e.totalCredit(), r.totalDebit(), 0.001);
        assertTrue(r.memo.contains("خطأ إدخال"));
        // القيد مع عكسه يساوي صفرًا في كل طرف.
        assertEquals(0, (e.totalDebit() - r.totalDebit()) - (e.totalCredit() - r.totalCredit()), 0.001);
    }

    @Test public void datelessEntryIsRejected() {
        Journal.Entry e = new Journal.Entry("بلا تاريخ", "", "TEST", 1)
                .debit(Journal.CASH, 100, "").credit(Journal.SALES, 100, "");
        assertEquals("تاريخ القيد مطلوب", Journal.rejectReason(e));
    }

    @Test public void manualEntryBalancesBothSides() {
        // وارد صندوق 25000: الصندوق مدين والطرف المقابل دائن.
        Journal.Entry e = Journal.simple("وارد صالح مضفر", "2026-09-18", "CASHBOX", 5,
                Journal.CASH, Journal.SUSPENSE, 25000, "صالح مضفر");
        assertTrue(Journal.valid(e));
        assertEquals(25000, e.totalDebit(), 0.001);
        assertEquals(25000, e.totalCredit(), 0.001);
        assertEquals(2, e.lines.size());
        assertTrue(e.lines.get(0).debit());
        assertEquals(Journal.CASH, e.lines.get(0).account);
        assertFalse(e.lines.get(1).debit());
    }

    @Test public void manualExpensePaidFromCashHasNoSuspense() {
        Journal.Entry e = Journal.simple("مخاريج: زيت", "2026-09-18", "EXPENSE", 9,
                Journal.EXPENSE, Journal.CASH, 4000, "زيت");
        assertTrue(Journal.valid(e));
        for (Journal.Line l : e.lines) assertNotEquals(Journal.SUSPENSE, l.account);
    }

    @Test public void centsTolerated() {
        assertTrue(Journal.balanced(0.004));
        assertFalse(Journal.balanced(0.02));
    }

    @Test public void periodIsDerivedFromDate() {
        assertEquals("2026-09", Journal.periodOf("2026-09-16"));
        assertEquals("", Journal.periodOf(""));
    }
}
