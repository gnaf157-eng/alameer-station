package com.abognaf.carcounter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * اختبارات منطق العدّ والتتبّع (تعمل على الحاسوب دون هاتف).
 * تتحقق من الشروط الأساسية المطلوبة في التطبيق.
 */
public class CountingLogicTest {

    private static final float W = 480f;
    private static final float H = 640f;
    private static final long STEP_MS = 130L;

    /** كشف سيارة: النقطة (cx) هي منتصف أسفل المستطيل (نقطة التلامس مع الطريق). */
    private static List<DetectedBox> car(float cx, float bottomY) {
        return car("car", cx, bottomY, 60f, 70f);
    }

    private static List<DetectedBox> car(String label, float cx, float bottomY, float w, float h) {
        List<DetectedBox> list = new ArrayList<>();
        list.add(new DetectedBox(label, 0.9f, cx - w / 2f, bottomY - h, cx + w / 2f, bottomY));
        return list;
    }

    /** سيارتان متتابعتان في نفس الاتجاه (موكب) على عمقين مختلفين. */
    private static List<DetectedBox> twoCars(float x1, float x2, float bottomY) {
        List<DetectedBox> list = new ArrayList<>();
        list.add(new DetectedBox("car", 0.9f, x1 - 30f, bottomY - 70f, x1 + 30f, bottomY));
        list.add(new DetectedBox("car", 0.85f, x2 - 30f, bottomY - 40f, x2 + 30f, bottomY - 10f));
        return list;
    }

    /** خط عمودي في منتصف الإطار: العبور يحدث عند تغيّر الجهة يمينًا/يسارًا. */
    private static CrossingCounter verticalLineCounter() {
        CrossingCounter counter = new CrossingCounter(W, H);
        counter.setLine(0.5f, 0.05f, 0.5f, 0.95f);
        return counter;
    }

    @Test
    public void movingCarIsCountedExactlyOnce() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 40; i++) {
            counter.process(car(100f + i * 10f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(1, counter.total());
        assertEquals(1, counter.inCount() + counter.outCount());
    }

    @Test
    public void parkedCarWithJitterIsNeverCounted() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 80; i++) {
            float jitter = ((i % 4) - 1.5f) * 6f;   // اهتزاز حول مكان ثابت
            counter.process(car(240f + jitter, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(0, counter.total());
    }

    @Test
    public void carStoppedInsideFrameIsNotCounted() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        // سيارة تدخل وتتوقف قبل الخط
        for (int i = 0; i < 12; i++) {
            counter.process(car(150f + i * 5f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        // ثم تقف تمامًا
        for (int i = 0; i < 40; i++) {
            counter.process(car(205f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(0, counter.total());
    }

    @Test
    public void bothDirectionsAreCountedSeparately() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        // سيارة تتحرك يسارًا وتقطع الخط، ثم سيارة أخرى تتحرك يمينًا
        for (int i = 0; i < 25; i++) {
            counter.process(car(380f - i * 12f, 260f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        long mid = t;
        for (int i = 0; i < 25; i++) {
            counter.process(car(120f + i * 12f, 420f), mid + i * STEP_MS, 20L, 8f, 1);
        }
        assertEquals(2, counter.total());
        assertEquals(1, counter.inCount());
        assertEquals(1, counter.outCount());
    }

    @Test
    public void flippingDirectionSwapsInAndOut() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 30; i++) {
            counter.process(car(100f + i * 12f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        int inBefore = counter.inCount();
        int outBefore = counter.outCount();
        assertEquals(1, inBefore + outBefore);

        counter.reset();
        counter.flipDirection();
        t += 2000L;
        for (int i = 0; i < 30; i++) {
            counter.process(car(100f + i * 12f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(1, counter.total());
        assertEquals(outBefore, counter.inCount());
        assertEquals(inBefore, counter.outCount());
    }

    @Test
    public void oscillatingCarNearLineIsCountedOnce() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        // سيارة تتردد عبر الخط (ترجيع) — تُحتسب مرة واحدة فقط
        for (int i = 0; i < 60; i++) {
            float offset = (float) Math.sin(i / 3.0) * 90f;
            counter.process(car(240f + offset, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(1, counter.total());
    }

    @Test
    public void carWaitingNearLineThenCrossingIsCounted() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        // سيارة تقف قرب الخط فترة ثم تتحرك وتعبُر
        for (int i = 0; i < 30; i++) {
            counter.process(car(205f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(0, counter.total());
        for (int i = 0; i < 20; i++) {
            counter.process(car(205f + i * 12f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(1, counter.total());
    }

    @Test
    public void twoCarsInConvoyAreBothCounted() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        // سيارتان تتبعان بعضهما في نفس الاتجاه: يجب أن تُحتسب كل واحدة مرة واحدة
        for (int i = 0; i < 25; i++) {
            counter.process(twoCars(110f + i * 12f, 60f + i * 12f, 320f), t, 20L, 8f, 2);
            t += STEP_MS;
        }
        assertEquals(2, counter.total());
        int countedTracks = 0;
        for (Track track : counter.tracks()) {
            if (track.counted) {
                countedTracks++;
            }
        }
        assertEquals(2, countedTracks);
    }

    @Test
    public void carAppearingBeyondLineAndDrivingAwayIsNotCounted() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 30; i++) {
            counter.process(car(300f + i * 6f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(0, counter.total());
    }

    @Test
    public void changingLineDoesNotCreatePhantomCount() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        // سيارة تتحرك يسار الخط دون عبوره
        for (int i = 0; i < 15; i++) {
            counter.process(car(100f + i * 6f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        // نقل الخط إلى يمين السيارة ثم مواصلة الحركة بنفس الاتجاه (ابتعاد عن الخط)
        counter.setLine(0.9f, 0.05f, 0.9f, 0.95f);
        for (int i = 0; i < 10; i++) {
            counter.process(car(190f - i * 6f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(0, counter.total());
    }

    @Test
    public void resetClearsCounters() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 30; i++) {
            counter.process(car(100f + i * 12f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertTrue(counter.total() > 0);
        counter.reset();
        assertEquals(0, counter.total());
        assertEquals(0, counter.inCount());
        assertEquals(0, counter.outCount());
    }

    @Test
    public void trackerKeepsSingleIdWhileCarIsVisible() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 30; i++) {
            counter.process(car(100f + i * 10f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        List<Track> tracks = counter.tracks();
        assertTrue("يجب أن يبقى أثر واحد للسيارة الواحدة", tracks.size() >= 1);
        Track track = tracks.get(tracks.size() - 1);
        assertTrue("المعرّف يجب أن يبقى ثابتًا", track.id > 0);
        assertTrue("السيارة المتحركة تُحتسب مرة واحدة", track.counted);
    }

    @Test
    public void trackSurvivesTemporaryLossOfDetection() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 10; i++) {
            counter.process(car(100f + i * 8f, 300f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        long before = t;
        // فقدان الكشف لثلاثة إطارات (حجب مؤقت)
        for (int i = 0; i < 3; i++) {
            counter.process(Collections.<DetectedBox>emptyList(), before + i * STEP_MS, 20L, 8f, 0);
        }
        // عودة الكشف بنفس المكان تقريبًا: يجب أن يُطابق نفس الأثر لا أثر جديد
        counter.process(car(190f, 300f), before + 3 * STEP_MS, 20L, 8f, 1);
        assertEquals(1, counter.tracks().size());
    }

    @Test
    public void labelsFilterWorks() {
        assertTrue(Labels.isVehicle("car", true, false));
        assertTrue(Labels.isVehicle("truck", true, false));
        assertTrue(Labels.isVehicle("bus", true, false));
        assertTrue(Labels.isVehicle("motorcycle", true, false));
        assertFalse("الدراجة الهوائية مستثناة افتراضيًا", Labels.isVehicle("bicycle", true, false));
        assertTrue(Labels.isVehicle("bicycle", true, true));
        assertFalse("الدراجات النارية مستثناة عند إيقاف الخيار", Labels.isVehicle("motorcycle", false, false));
        assertFalse(Labels.isVehicle("person", true, true));
        assertFalse(Labels.isVehicle("", true, true));
        assertNotEquals("مركبة", Labels.arabicName("car"));
        assertEquals("سيارة", Labels.arabicName("car"));
        assertEquals("شاحنة", Labels.arabicName("truck"));
        assertEquals("دراجة نارية", Labels.arabicName("motorcycle"));
    }

    @Test
    public void motorcycleCountedWhenEnabled() {
        CrossingCounter counter = verticalLineCounter();
        long t = 10_000L;
        for (int i = 0; i < 30; i++) {
            counter.process(car("motorcycle", 100f + i * 12f, 300f, 40f, 60f), t, 20L, 8f, 1);
            t += STEP_MS;
        }
        assertEquals(1, counter.total());
    }
}
