package com.alameer.station.shifts;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class LedgerTest {

    @Test public void balancedShiftHasNoGap() {
        // مبيعات 100000 ومقبوضات 20000 مقابل نقد 90000 ودين 20000 ومخاريج 10000.
        double debit = Ledger.debit(90000, 20000, 10000, 0);
        double credit = Ledger.credit(100000, 20000);
        assertTrue(Ledger.balanced(debit - credit));
        assertEquals("متوازن", Ledger.state(debit - credit));
    }

    @Test public void shortageShowsAsDebitSide() {
        double gap = Ledger.debit(80000, 0, 0, 0) - Ledger.credit(100000, 0);
        assertFalse(Ledger.balanced(gap));
        assertEquals("غير متوازن", Ledger.state(gap));
        assertTrue(Ledger.explain(gap).contains("الدائن يزيد"));
    }

    @Test public void totalsAndOffenders() {
        List<Ledger.Row> rows = Ledger.newList();
        rows.add(new Ledger.Row(1, "أحمد", "2026-09-01", 50000, 50000));
        rows.add(new Ledger.Row(2, "سالم", "2026-09-02", 30000, 25000));
        rows.add(new Ledger.Row(3, "علي", "2026-09-03", 10000, 12000));

        assertEquals(90000, Ledger.totalDebit(rows), 0.001);
        assertEquals(87000, Ledger.totalCredit(rows), 0.001);
        assertEquals(3000, Ledger.gap(rows), 0.001);

        List<Ledger.Row> bad = Ledger.offenders(rows);
        assertEquals(2, bad.size());
        // الأكبر أثرًا أولًا: فرق 5000 قبل فرق 2000.
        assertEquals(2, bad.get(0).shiftId);
        assertEquals(3, bad.get(1).shiftId);
    }

    @Test public void centsAreToleratedButRialsAreNot() {
        assertTrue(Ledger.balanced(0.004));
        assertFalse(Ledger.balanced(1));
    }
}
