package com.alameer.station.shifts;

import org.junit.Test;

import static org.junit.Assert.*;

public class DipTest {

    @Test public void shortageIsNegativeAndSurplusPositive() {
        assertEquals(-500, Dip.gap(10000, 9500), 0.001);
        assertEquals(300, Dip.gap(10000, 10300), 0.001);
        assertEquals("عجز", Dip.direction(Dip.gap(10000, 9500)));
        assertEquals("زيادة", Dip.direction(Dip.gap(10000, 10300)));
        assertEquals("مطابق تمامًا", Dip.direction(0));
    }

    @Test public void toleranceIsHalfPercentButNeverBelowTwentyLitres() {
        assertEquals(100, Dip.tolerance(20000), 0.001);
        // مخزون صغير يأخذ الحد الأدنى الثابت.
        assertEquals(20, Dip.tolerance(1000), 0.001);
    }

    @Test public void levelsFollowTheTolerance() {
        // الحد 100 لتر عند مخزون 20000.
        assertEquals(Dip.OK, Dip.level(20000, 19950));
        assertEquals(Dip.WATCH, Dip.level(20000, 19850));
        assertEquals(Dip.ALARM, Dip.level(20000, 19500));
    }

    @Test public void valueUsesThePumpPrice() {
        assertEquals(125000, Dip.value(-500, 250), 0.001);
        assertEquals(0, Dip.value(-500, 0), 0.001);
    }

    @Test public void correctionDirectionOffsetsTheGap() {
        // العجز يُخرج لترات من المخزون، والزيادة تُدخلها.
        assertEquals("OUT", Dip.correctionDirection(-500));
        assertEquals("IN", Dip.correctionDirection(300));
    }

    @Test public void percentIsSafeOnEmptyTank() {
        assertEquals(0, Dip.percent(0, 100), 0.001);
        assertEquals(-5, Dip.percent(10000, 9500), 0.001);
    }

    @Test public void summaryAndNoteDescribeTheGap() {
        String s = Dip.summary(10000, 9500, 250);
        assertTrue(s.contains("عجز"));
        assertTrue(s.contains("500"));
        assertTrue(Dip.note(-500, "تسرّب").contains("تسرّب"));
        assertTrue(Dip.note(-500, "").startsWith("مطابقة مقياس"));
        assertEquals("مطابق تمامًا", Dip.summary(10000, 10000, 250));
    }
}
