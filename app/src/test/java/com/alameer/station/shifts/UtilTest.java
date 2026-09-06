package com.alameer.station.shifts;

import static org.junit.Assert.*;
import org.junit.Test;

public class UtilTest {
    @Test public void numberParsesPlainDigits(){ assertEquals(1500.0, Util.number("1500"), 0.001); }
    @Test public void numberParsesDecimalsAndSpaces(){ assertEquals(12.5, Util.number("  12.5 "), 0.001); }
    @Test public void numberFallsBackToZeroOnGarbage(){
        assertEquals(0.0, Util.number("abc"), 0.001);
        assertEquals(0.0, Util.number(""), 0.001);
    }
    @Test public void pinHashIsStableAndNotPlainText(){
        String hash = Db.hash("1234");
        assertEquals(hash, Db.hash("1234"));
        assertEquals(64, hash.length());
        assertFalse(hash.contains("1234"));
    }
    @Test public void differentPinsProduceDifferentHashes(){
        assertNotEquals(Db.hash("1111"), Db.hash("2222"));
    }
    @Test public void pinHashIgnoresSurroundingSpaces(){
        assertEquals(Db.hash("4321"), Db.hash(" 4321 "));
    }
    @Test public void arabicLabelsCoverEveryStatus(){
        assertEquals("معتمدة", PdfReport.arabicStatus("APPROVED"));
        assertEquals("مُرجعة للتصحيح", PdfReport.arabicStatus("RETURNED"));
        assertEquals("مقبوضات", PdfReport.arabicType("COLLECTION"));
        assertEquals("مخاريج", PdfReport.arabicType("EXPENSE"));
    }
}
