package com.gtnhvr.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Server registry for explicitly compatible clients and visual-only VR pose samples. */
public final class VRRemotePoseServerState {
    private static final long MAX_POSE_AGE_NANOS = 2_500_000_000L;
    private static final long MIN_PACKET_INTERVAL_NANOS = 25_000_000L;
    private static final float MAX_HORIZONTAL_OFFSET = 10.0F;
    private static final ConcurrentMap<UUID, Boolean> CAPABLE_CLIENTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PoseEntry> POSES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_UPLOAD_NANOS = new ConcurrentHashMap<>();

    private VRRemotePoseServerState() {}

    public static boolean hasClient(EntityPlayer player) {
        return player != null && CAPABLE_CLIENTS.containsKey(player.getUniqueID());
    }

    public static void clientJoinedVR(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        CAPABLE_CLIENTS.put(player.getUniqueID(), Boolean.TRUE);
        List<?> players = player.worldObj.playerEntities;
        for (Object value : players) {
            if (!(value instanceof EntityPlayerMP)) continue;
            EntityPlayerMP other = (EntityPlayerMP) value;
            if (other == player || !hasClient(other)) continue;
            VRPoseData pose = getRecent(other.getUniqueID());
            if (pose != null) VRNetwork.sendRemotePoseToPlayer(other, pose, player);
        }
    }

    public static void acceptPose(EntityPlayerMP player, VRPoseData sample) {
        if (player == null || sample == null || player.worldObj == null || player.worldObj.isRemote
                || !hasClient(player) || !sample.isWellFormed()) return;

        UUID id = player.getUniqueID();
        long now = System.nanoTime();
        Long previous = LAST_UPLOAD_NANOS.get(id);
        if (previous != null && now - previous < MIN_PACKET_INTERVAL_NANOS) return;

        if (sample.flags == 0) {
            LAST_UPLOAD_NANOS.put(id, now);
            POSES.remove(id);
            VRNetwork.broadcastRemotePose(player, VRPoseData.empty());
            return;
        }

        if (!plausiblePosition(player, sample)) return;
        LAST_UPLOAD_NANOS.put(id, now);
        VRPoseData copy = sample.copy();
        POSES.put(id, new PoseEntry(copy, now));
        VRNetwork.broadcastRemotePose(player, copy);
    }

    public static VRPoseData getRecent(UUID id) {
        if (id == null) return null;
        PoseEntry entry = POSES.get(id);
        if (entry == null) return null;
        if (System.nanoTime() - entry.receivedAtNanos > MAX_POSE_AGE_NANOS) {
            POSES.remove(id, entry);
            return null;
        }
        return entry.pose.copy();
    }

    public static void clearPlayer(EntityPlayerMP player) {
        if (player == null) return;
        if (player.worldObj != null && !player.worldObj.isRemote) {
            VRNetwork.broadcastRemotePose(player, VRPoseData.empty());
        }
        UUID id = player.getUniqueID();
        CAPABLE_CLIENTS.remove(id);
        POSES.remove(id);
        LAST_UPLOAD_NANOS.remove(id);
    }

    private static boolean plausiblePosition(EntityPlayerMP player, VRPoseData pose) {
        if ((pose.flags & VRPoseData.HEAD_TRACKED) != 0
                && !plausiblePosition(player, pose.headX, pose.headY, pose.headZ)) return false;
        if ((pose.flags & VRPoseData.LEFT_HAND_TRACKED) != 0
                && !plausiblePosition(player, pose.leftX, pose.leftY, pose.leftZ)) return false;
        if ((pose.flags & VRPoseData.RIGHT_HAND_TRACKED) != 0
                && !plausiblePosition(player, pose.rightX, pose.rightY, pose.rightZ)) return false;
        return true;
    }

    private static boolean plausiblePosition(EntityPlayerMP player, float x, float y, float z) {
        double dx = x - player.posX;
        double dz = z - player.posZ;
        return Math.abs(dx) <= MAX_HORIZONTAL_OFFSET && Math.abs(dz) <= MAX_HORIZONTAL_OFFSET
                && y >= player.posY - 3.0D && y <= player.posY + 10.0D;
    }

    private static final class PoseEntry {
        private final VRPoseData pose;
        private final long receivedAtNanos;

        private PoseEntry(VRPoseData pose, long receivedAtNanos) {
            this.pose = pose;
            this.receivedAtNanos = receivedAtNanos;
        }
    }
}
