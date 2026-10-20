package com.gtnhvr.util;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * 1:1 Visor Vector3fHistory:
 * Sliding window of 3D position samples with millisecond timestamps.
 * Provides accurate calculation of velocity/speed, net displacement,
 * total path length, and average position over a specified time window.
 */
@Lwjgl3Aware
public class Vector3fHistory {
    private final Deque<Entry> history;
    private final int capacity;
    private final LongSupplier clock;

    public Vector3fHistory(int capacity, LongSupplier clock) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.history = new ArrayDeque<>(capacity);
        this.clock = clock;
    }

    public Vector3fHistory(int capacity) {
        this(capacity, System::currentTimeMillis);
    }

    /** Add a new position sample, evicting the oldest if we exceed capacity. */
    public synchronized void add(Vector3fc pos) {
        history.addLast(new Entry(new Vector3f(pos), clock.getAsLong()));
        if (history.size() > capacity) {
            history.removeFirst();
        }
    }

    /** Remove all stored samples. */
    public synchronized void clear() {
        history.clear();
    }

    /**
     * @return the most recent sample
     */
    public synchronized Vector3f latest() {
        Entry last = history.peekLast();
        if (last == null) return new Vector3f(0.0F, 0.0F, 0.0F);
        return new Vector3f(last.pos);
    }

    /**
     * @return total path-length traveled over the last `seconds`
     */
    public synchronized float totalMovement(float seconds) {
        List<Entry> recent = getRecent(seconds);
        if (recent.size() < 2) return 0.0f;

        float sum = 0.0f;
        for (int i = 1; i < recent.size(); i++) {
            sum += recent.get(i).pos.distance(recent.get(i - 1).pos);
        }
        return sum;
    }

    /**
     * @return net displacement vector from oldest->newest sample in the last `seconds`
     */
    public synchronized Vector3f netMovement(float seconds) {
        List<Entry> recent = getRecent(seconds);
        if (recent.size() < 2) return new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3fc first = recent.get(0).pos;
        Vector3fc last = recent.get(recent.size() - 1).pos;
        return last.sub(first, new Vector3f());
    }

    /**
     * @return average speed (distance/time in meters per second) over each segment in the last `seconds`
     */
    public synchronized float averageSpeed(float seconds) {
        List<Entry> recent = getRecent(seconds);
        if (recent.size() < 2) return 0.0f;

        float sumSpeeds = 0.0f;
        int segments = 0;
        for (int i = 1; i < recent.size(); i++) {
            Entry prev = recent.get(i - 1), curr = recent.get(i);
            float dtSeconds = (curr.timestamp - prev.timestamp) / 1000.0f;
            if (dtSeconds > 0) {
                float dist = curr.pos.distance(prev.pos);
                sumSpeeds += dist / dtSeconds;
                segments++;
            }
        }
        return segments == 0 ? 0.0f : sumSpeeds / segments;
    }

    /**
     * @return arithmetic mean of all positions over the last `seconds`
     */
    public synchronized Vector3f averagePosition(float seconds) {
        List<Entry> recent = getRecent(seconds);
        if (recent.isEmpty()) return new Vector3f(0.0F, 0.0F, 0.0F);

        float x = 0, y = 0, z = 0;
        for (Entry e : recent) {
            x += e.pos.x();
            y += e.pos.y();
            z += e.pos.z();
        }
        float inv = 1.0f / recent.size();
        return new Vector3f(x * inv, y * inv, z * inv);
    }

    /**
     * Helper: walk backward from newest->oldest until timestamp < now - seconds*1000.
     * Returns the remaining list in 'chronological' order (oldest first).
     */
    private List<Entry> getRecent(float seconds) {
        long now = clock.getAsLong();
        long cutoff = now - (long) (seconds * 1_000.0F);
        List<Entry> out = new ArrayList<>(capacity);
        for (Iterator<Entry> it = history.descendingIterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.timestamp < cutoff) break;
            out.add(0, e);
        }
        return out;
    }

    private static class Entry {
        final Vector3fc pos;
        final long timestamp;

        Entry(Vector3fc pos, long timestamp) {
            this.pos = pos;
            this.timestamp = timestamp;
        }
    }
}
