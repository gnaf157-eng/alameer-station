package com.alameer.station.shifts;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class LinkTest {

    @Test public void generatedCodeIsTwelveLettersInThreeGroups() {
        String code = Link.generate(new Random(7));
        assertTrue(Link.valid(code));
        assertEquals(12, Link.normalize(code).length());
        assertEquals(2, code.length() - code.replace("-", "").length());
    }

    @Test public void normalizeIgnoresCaseSpacesAndDashes() {
        assertEquals("ABCD2345KLMN", Link.normalize("abcd-2345 klmn"));
        assertTrue(Link.valid("abcd 2345-klmn"));
        assertFalse(Link.valid("ABC"));
        assertFalse(Link.valid(null));
    }

    @Test public void sameCodeGivesSameChannelOnBothDevices() {
        String manager = Link.topic("ABCD-2345-KLMN");
        String worker = Link.topic("abcd2345klmn");
        assertEquals(manager, worker);
        assertTrue(manager.startsWith("tabiq-"));
    }

    @Test public void differentCodesGiveDifferentChannels() {
        assertNotEquals(Link.topic("ABCD-2345-KLMN"), Link.topic("ABCD-2345-KLMP"));
    }

    @Test public void channelNameNeverLeaksTheCode() {
        String code = "ABCD2345KLMN";
        assertFalse(Link.topic(code).contains(code));
        assertFalse(Link.secret(code).contains(code));
        // المفتاح غير اسم القناة حتى لا يُشتق أحدهما من الآخر.
        assertNotEquals(Link.topic(code), Link.secret(code));
    }

    @Test public void cipherRoundTripsArabicText() {
        String key = Link.secret("ABCD2345KLMN");
        String text = "وردية العامل — مبيعات 175,000 ر.ي\nسطر ثانٍ";
        String sealed = Link.cipher(text, key);
        assertNotEquals(text, sealed);
        assertEquals(text, Link.decipher(sealed, key));
    }

    @Test public void wrongKeyDoesNotRecoverTheText() {
        String sealed = Link.cipher("بيانات سرية", Link.secret("ABCD2345KLMN"));
        assertNotEquals("بيانات سرية", Link.decipher(sealed, Link.secret("WXYZ6789PQRS")));
    }

    @Test public void prettyRestoresTheReadableGrouping() {
        assertEquals("ABCD-2345-KLMN", Link.pretty("abcd2345klmn"));
    }

    @Test public void urlsPointAtTheDerivedChannel() {
        String code = "ABCD2345KLMN";
        assertTrue(Link.url(code).endsWith(Link.topic(code)));
        assertTrue(Link.pullUrl(code, "all").contains("poll=1"));
    }
}
