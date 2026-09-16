package com.alameer.station.shifts;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShiftCodeTest {

    @Test public void sameShiftAlwaysGivesSameCode() {
        String a = ShiftFile.code("dev1", "2026-09-16", 7);
        String b = ShiftFile.code("dev1", "2026-09-16", 7);
        assertEquals(a, b);
    }

    @Test public void differentShiftsGiveDifferentCodes() {
        String base = ShiftFile.code("dev1", "2026-09-16", 7);
        assertNotEquals(base, ShiftFile.code("dev1", "2026-09-16", 8));
        assertNotEquals(base, ShiftFile.code("dev2", "2026-09-16", 7));
        assertNotEquals(base, ShiftFile.code("dev1", "2026-09-17", 7));
    }

    @Test public void codeCarriesTheDateAndIsReadable() {
        String code = ShiftFile.code("dev1", "2026-09-16", 7);
        assertTrue(code.startsWith("W-260916-"));
        assertEquals(14, code.length());
        // بلا حروف تلتبس على العين.
        assertFalse(code.substring(9).contains("0"));
        assertFalse(code.substring(9).contains("1"));
    }

    @Test public void codeSurvivesTheTransferFile() {
        ShiftFile.Shift s = new ShiftFile.Shift();
        s.worker = "عامل الديزل";
        s.date = "2026-09-16";
        s.number = 7;
        s.device = "dev1";
        s.readings.add(new ShiftFile.Reading("ديزل 1", "ديزل", 1000, 1200, 250));
        String expected = ShiftFile.code("dev1", "2026-09-16", 7);

        ShiftFile.Shift back = ShiftFile.read(ShiftFile.write(s));
        assertEquals(expected, back.code);
    }

    @Test public void explicitCodeIsPreserved() {
        ShiftFile.Shift s = new ShiftFile.Shift();
        s.worker = "سالم";
        s.date = "2026-09-16";
        s.number = 3;
        s.device = "dev9";
        s.code = "W-260916-ABCDE";
        s.readings.add(new ShiftFile.Reading("بترول 1", "بترول", 0, 100, 300));
        assertEquals("W-260916-ABCDE", ShiftFile.read(ShiftFile.write(s)).code);
    }
}
