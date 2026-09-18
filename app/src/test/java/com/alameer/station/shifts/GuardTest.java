package com.alameer.station.shifts;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class GuardTest {

    @Test public void lockOnlyAfterFiveFailures() {
        assertEquals(0, Guard.lockSeconds(4));
        assertEquals(60, Guard.lockSeconds(5));
    }

    @Test public void lockDoublesAndIsCapped() {
        assertEquals(60, Guard.lockSeconds(5));
        assertEquals(120, Guard.lockSeconds(10));
        assertEquals(240, Guard.lockSeconds(15));
        assertEquals(15 * 60, Guard.lockSeconds(500));
    }

    @Test public void lockExpiresWithTime() {
        long now = 1_000_000L;
        assertTrue(Guard.locked(5, now, now + 30_000));
        assertFalse(Guard.locked(5, now, now + 61_000));
        assertEquals(30, Guard.lockLeft(5, now, now + 30_000));
    }

    @Test public void saltMakesTheSamePasswordHashDifferently() {
        String a = Guard.hash("2216", "saltone");
        String b = Guard.hash("2216", "salttwo");
        assertNotEquals(a, b);
        // ونفس الملح يعطي نفس النتيجة دائمًا.
        assertEquals(a, Guard.hash("2216", "saltone"));
    }

    @Test public void hashNeverContainsThePassword() {
        assertFalse(Guard.hash("2216", "s").contains("2216"));
    }

    @Test public void constantTimeCompareStillWorks() {
        assertTrue(Guard.same("abc", "abc"));
        assertFalse(Guard.same("abc", "abd"));
        assertFalse(Guard.same("abc", "ab"));
        assertFalse(Guard.same(null, "a"));
    }

    @Test public void weakPasswordsAreRejected() {
        assertFalse(Guard.weakness("2216").isEmpty());
        assertFalse(Guard.weakness("6114").isEmpty());
        assertFalse(Guard.weakness("1111").isEmpty());
        assertFalse(Guard.weakness("1234").isEmpty());
        assertFalse(Guard.weakness("12").isEmpty());
        assertEquals("", Guard.weakness("8371"));
    }

    @Test public void saltsAreRandomAndLongEnough() {
        String a = Guard.newSalt(new Random(1));
        String b = Guard.newSalt(new Random(2));
        assertEquals(32, a.length());
        assertNotEquals(a, b);
    }
}
