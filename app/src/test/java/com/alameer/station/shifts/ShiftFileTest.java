package com.alameer.station.shifts;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShiftFileTest {

    private ShiftFile.Shift sample() {
        ShiftFile.Shift s = new ShiftFile.Shift();
        s.station = "محطة الأمير";
        s.worker = "عامل الديزل";
        s.date = "2026-09-16";
        s.number = 12;
        s.device = "abc123";
        s.readings.add(new ShiftFile.Reading("ديزل 1", "ديزل", 1000, 1500, 250));
        s.readings.add(new ShiftFile.Reading("ديزل 2", "ديزل", 2000, 2200, 250));
        s.moves.add(new ShiftFile.Move("CASH", "تسليم", 150000));
        s.moves.add(new ShiftFile.Move("DEBT", "أحمد", 25000));
        return s;
    }

    @Test public void roundTripKeepsEveryValue() {
        ShiftFile.Shift back = ShiftFile.read(ShiftFile.write(sample()));
        assertEquals("عامل الديزل", back.worker);
        assertEquals("2026-09-16", back.date);
        assertEquals(12, back.number);
        assertEquals(2, back.readings.size());
        assertEquals(2, back.moves.size());
        assertEquals(1500, back.readings.get(0).current, 0.001);
        assertEquals(25000, back.total("DEBT"), 0.001);
    }

    @Test public void totalsMatchTheShiftMath() {
        ShiftFile.Shift s = sample();
        // (500 + 200) لتر × 250 = 175000
        assertEquals(175000, s.sales(), 0.001);
        assertEquals(150000, s.total("CASH"), 0.001);
        // 175000 + 0 - 150000 - 25000 - 0 = 0
        assertEquals(0, s.balance(), 0.001);
    }

    @Test public void tamperedFileIsRejected() {
        String text = ShiftFile.write(sample());
        String tampered = text.replace("150000", "50000");
        try {
            ShiftFile.read(tampered);
            fail("كان يجب رفض الملف المُعدَّل");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("مُعدَّل"));
        }
    }

    @Test public void foreignFileIsRejected() {
        try {
            ShiftFile.read("مرحبا\nهذا ملف آخر");
            fail("كان يجب رفض الملف الغريب");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("ليس ملف وردية"));
        }
    }

    @Test public void emptyFileIsRejected() {
        try {
            ShiftFile.read("");
            fail("كان يجب رفض الملف الفارغ");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("فارغ"));
        }
    }

    @Test public void separatorsInNamesDoNotBreakTheFile() {
        ShiftFile.Shift s = sample();
        s.moves.add(new ShiftFile.Move("EXPENSE", "زيت|ماء", 500));
        ShiftFile.Shift back = ShiftFile.read(ShiftFile.write(s));
        assertEquals(3, back.moves.size());
        assertEquals(500, back.total("EXPENSE"), 0.001);
    }

    @Test public void fileNameCarriesDateAndWorker() {
        String name = ShiftFile.fileName(sample());
        assertTrue(name.contains("2026-09-16"));
        assertTrue(name.endsWith(".tabiq"));
    }
}
