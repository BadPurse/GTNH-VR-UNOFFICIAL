package com.gtnhvr.network;

import net.minecraft.entity.player.EntityPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Short-lived server cache for the next physical VR bow release. */
public final class VRBowServerState {
    private static final long MAX_AGE_MILLIS = 1500L;
    private static final ConcurrentMap<UUID, LaunchState> STATES = new ConcurrentHashMap<>();

    private VRBowServerState() {}

    public static void set(EntityPlayer player, float tension, float yaw, float pitch) {
        if (player == null || !finite(tension) || !finite(yaw) || !finite(pitch)) return;
        if (tension < 0.0F || tension > 1.0F || yaw < -180.0F || yaw > 180.0F
                || pitch < -90.0F || pitch > 90.0F) return;
        STATES.put(player.getUniqueID(), new LaunchState(tension, yaw, pitch, System.currentTimeMillis()));
    }

    public static LaunchState getRecent(EntityPlayer player) {
        if (player == null) return null;
        UUID id = player.getUniqueID();
        LaunchState state = STATES.get(id);
        if (state == null) return null;
        long age = System.currentTimeMillis() - state.receivedAtMillis;
        if (age < 0L || age > MAX_AGE_MILLIS) {
            STATES.remove(id, state);
            return null;
        }
        return state;
    }

    public static void clear(EntityPlayer player) {
        if (player != null) STATES.remove(player.getUniqueID());
    }

    public static final class LaunchState {
        public final float tension;
        public final float yaw;
        public final float pitch;
        private final long receivedAtMillis;

        private LaunchState(float tension, float yaw, float pitch, long receivedAtMillis) {
            this.tension = tension;
            this.yaw = yaw;
            this.pitch = pitch;
            this.receivedAtMillis = receivedAtMillis;
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
