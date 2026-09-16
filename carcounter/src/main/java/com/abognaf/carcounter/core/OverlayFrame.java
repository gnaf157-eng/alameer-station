package com.abognaf.carcounter.core;

import java.util.Collections;
import java.util.List;

/** لقطة واحدة من نتائج المعالجة تُمرَّر إلى الواجهة لرسمها. */
public final class OverlayFrame {

    public final List<OverlayItem> items;
    public final List<CrossingEvent> events;
    public final int total;
    public final int in;
    public final int out;
    public final int activeTracks;
    public final long timeMs;
    public final float fps;
    public final long inferenceMs;
    public final int rawDetections;

    public OverlayFrame(List<OverlayItem> items, List<CrossingEvent> events,
                        int total, int in, int out, int activeTracks,
                        long timeMs, float fps, long inferenceMs, int rawDetections) {
        this.items = items;
        this.events = events;
        this.total = total;
        this.in = in;
        this.out = out;
        this.activeTracks = activeTracks;
        this.timeMs = timeMs;
        this.fps = fps;
        this.inferenceMs = inferenceMs;
        this.rawDetections = rawDetections;
    }

    public static OverlayFrame empty() {
        return new OverlayFrame(Collections.<OverlayItem>emptyList(),
                Collections.<CrossingEvent>emptyList(), 0, 0, 0, 0, 0L, 0f, 0L, 0);
    }
}
