package com.gtnhvr.network;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe remote pose cache with a short interpolation buffer and expiry. */
public final class VRRemotePoseClientState {
    private static final long STALE_AFTER_NANOS = 2_500_000_000L;
    private static final long INTERPOLATION_DELAY_NANOS = 50_000_000L;
    private static final ConcurrentMap<UUID, Samples> POSES = new ConcurrentHashMap<>();

    private VRRemotePoseClientState() {}

    public static void accept(UUID id, VRPoseData pose) {
        if (id == null || pose == null || !pose.isWellFormed()) return;
        if (pose.flags == 0) {
            POSES.remove(id);
            return;
        }
        long now = System.nanoTime();
        Samples previous = POSES.get(id);
        if (previous == null || now - previous.currentAt > STALE_AFTER_NANOS) {
            POSES.put(id, new Samples(null, pose.copy(), 0L, now));
        } else {
            POSES.put(id, new Samples(previous.current.copy(), pose.copy(), previous.currentAt, now));
        }
    }

    /** Returns a sample interpolated one network tick behind to smooth 20 Hz updates. */
    public static VRPoseData getInterpolated(UUID id) {
        if (id == null) return null;
        Samples samples = POSES.get(id);
        if (samples == null) return null;
        long now = System.nanoTime();
        if (now - samples.currentAt > STALE_AFTER_NANOS) {
            POSES.remove(id, samples);
            return null;
        }
        if (samples.previous == null || samples.currentAt <= samples.previousAt) return samples.current.copy();
        long targetTime = now - INTERPOLATION_DELAY_NANOS;
        float t = (float) ((targetTime - samples.previousAt)
                / (double) (samples.currentAt - samples.previousAt));
        t = Math.max(0.0F, Math.min(1.0F, t));
        return VRPoseData.interpolate(samples.previous, samples.current, t);
    }

    public static void clear() { POSES.clear(); }

    private static final class Samples {
        private final VRPoseData previous;
        private final VRPoseData current;
        private final long previousAt;
        private final long currentAt;

        private Samples(VRPoseData previous, VRPoseData current, long previousAt, long currentAt) {
            this.previous = previous;
            this.current = current;
            this.previousAt = previousAt;
            this.currentAt = currentAt;
        }
    }
}
