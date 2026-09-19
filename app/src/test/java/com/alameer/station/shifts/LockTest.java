package com.alameer.station.shifts;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class LockTest {

    @Test public void weakPinsAreRejected() {
        assertFalse(Lock.reject("12", null).isEmpty());
        assertFalse(Lock.reject("1111", null).isEmpty());
        assertFalse(Lock.reject("1234", null).isEmpty());
        assertEquals("", Lock.reject("8372", null));
    }

    @Test public void mismatchIsCaught() {
        assertFalse(Lock.reject("8372", "8373").isEmpty());
        assertEquals("", Lock.reject("8372", "8372"));
    }

    @Test public void saltChangesTheHashAndNeverLeaksThePin() {
        String a = Lock.hash("8372", "saltone");
        String b = Lock.hash("8372", "salttwo");
        assertNotEquals(a, b);
        assertEquals(a, Lock.hash("8372", "saltone"));
        assertFalse(a.contains("8372"));
    }

    @Test public void constantTimeCompare() {
        assertTrue(Lock.same("abc", "abc"));
        assertFalse(Lock.same("abc", "abd"));
        assertFalse(Lock.same(null, "abc"));
    }

    @Test public void lockIsSkippedWhenDisabled() {
        assertFalse(Lock.shouldAsk(false, false, 0, 1000));
    }

    @Test public void lockIsAskedOnFirstOpen() {
        assertTrue(Lock.shouldAsk(true, false, 0, 1000));
    }

    @Test public void shortTripsDoNotRelock() {
        long left = 100_000L;
        // خلال المهلة لا يُطلب القفل من جديد.
        assertFalse(Lock.shouldAsk(true, true, left, left + 30_000));
        // وبعدها يُطلب.
        assertTrue(Lock.shouldAsk(true, true, left, left + 70_000));
    }

    @Test public void saltsAreRandom() {
        assertNotEquals(Lock.newSalt(new Random(1)), Lock.newSalt(new Random(2)));
        assertEquals(24, Lock.newSalt(new Random(1)).length());
    }
}
