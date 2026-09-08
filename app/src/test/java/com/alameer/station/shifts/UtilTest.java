package com.alameer.station.shifts;

import static org.junit.Assert.*;
import org.junit.Test;

/** اختبارات المنطق الحسابي — تعمل على JVM بلا محاكي. */
public class UtilTest {

    @Test public void numberParsesPlainDigits(){ assertEquals(1500.0, Calc.number("1500"), 0.001); }
    @Test public void numberParsesDecimalsAndSpaces(){ assertEquals(12.5, Calc.number("  12.5 "), 0.001); }
    @Test public void numberFallsBackToZeroOnGarbage(){
        assertEquals(0.0, Calc.number("abc"), 0.001);
        assertEquals(0.0, Calc.number(""), 0.001);
        assertEquals(0.0, Calc.number(null), 0.001);
    }

    @Test public void balanceMatchesWhenCashCoversSales(){
        assertEquals(0.0, Calc.balance(10000, 0, 10000, 0, 0), 0.001);
        assertTrue(Calc.matched(Calc.balance(10000, 0, 10000, 0, 0)));
    }
    @Test public void balanceCountsDebtsAndExpenses(){
        // مبيعات 10000، سُلّم 7000 نقدًا، 2000 دَين، 1000 مخاريج => مطابقة
        assertEquals(0.0, Calc.balance(10000, 0, 7000, 2000, 1000), 0.001);
    }
    @Test public void balanceShowsShortage(){
        assertEquals(500.0, Calc.balance(10000, 0, 9500, 0, 0), 0.001);
        assertFalse(Calc.matched(500.0));
    }
    @Test public void collectionsIncreaseBalance(){
        assertEquals(300.0, Calc.balance(1000, 300, 1000, 0, 0), 0.001);
    }

    @Test public void pumpSalesUseMeterDifference(){
        assertEquals(5000.0, Calc.pumpSales(1000, 1010, 500), 0.001);
    }
    @Test public void pumpSalesAreZeroWhenMeterUnchanged(){
        assertEquals(0.0, Calc.pumpSales(1000, 1000, 500), 0.001);
    }

    @Test public void pinHashIsStableAndNotPlainText(){
        String hash = Calc.hash("1234");
        assertEquals(hash, Calc.hash("1234"));
        assertEquals(64, hash.length());
        assertFalse(hash.contains("1234"));
    }
    @Test public void differentPinsProduceDifferentHashes(){
        assertNotEquals(Calc.hash("1111"), Calc.hash("2222"));
    }
    @Test public void pinHashIgnoresSurroundingSpaces(){
        assertEquals(Calc.hash("4321"), Calc.hash(" 4321 "));
    }

    @Test public void arabicLabelsCoverEveryStatus(){
        assertEquals("معتمدة", Calc.arabicStatus("APPROVED"));
        assertEquals("مُرجعة للتصحيح", Calc.arabicStatus("RETURNED"));
        assertEquals("مرسلة للمدير", Calc.arabicStatus("SUBMITTED"));
        assertEquals("مفتوحة", Calc.arabicStatus("OPEN"));
    }
    @Test public void arabicMonthFormatsRealMonths(){
        assertEquals("سبتمبر 2026", Calc.arabicMonth("2026-09"));
        assertEquals("يناير 2025", Calc.arabicMonth("2025-01"));
        assertEquals("ديسمبر 2025", Calc.arabicMonth("2025-12"));
    }
    @Test public void arabicMonthFallsBackOnBadInput(){
        assertEquals("2026-13", Calc.arabicMonth("2026-13"));
        assertEquals("abc", Calc.arabicMonth("abc"));
    }

    @Test public void arabicLabelsCoverEveryMovementType(){
        assertEquals("مقبوضات", Calc.arabicType("COLLECTION"));
        assertEquals("نقد مسلّم", Calc.arabicType("CASH"));
        assertEquals("ديون", Calc.arabicType("DEBT"));
        assertEquals("مخاريج", Calc.arabicType("EXPENSE"));
    }
}
