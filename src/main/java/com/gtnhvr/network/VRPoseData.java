package com.gtnhvr.network;

import io.netty.buffer.ByteBuf;

/** Visual-only pose payload shared by the Forge 1.7.10 client and server. */
public final class VRPoseData {
    public static final int HEAD_TRACKED = 1;
    public static final int LEFT_HAND_TRACKED = 2;
    public static final int RIGHT_HAND_TRACKED = 4;
    public static final int KNOWN_FLAGS = HEAD_TRACKED | LEFT_HAND_TRACKED | RIGHT_HAND_TRACKED;

    public int flags;
    public float headX, headY, headZ, headYaw, headPitch;
    public float leftX, leftY, leftZ, leftYaw, leftPitch;
    public float rightX, rightY, rightZ, rightYaw, rightPitch;

    public VRPoseData() {}

    public VRPoseData(int flags, float headX, float headY, float headZ, float headYaw, float headPitch,
                      float leftX, float leftY, float leftZ, float leftYaw, float leftPitch,
                      float rightX, float rightY, float rightZ, float rightYaw, float rightPitch) {
        this.flags = flags;
        this.headX = headX; this.headY = headY; this.headZ = headZ;
        this.headYaw = headYaw; this.headPitch = headPitch;
        this.leftX = leftX; this.leftY = leftY; this.leftZ = leftZ;
        this.leftYaw = leftYaw; this.leftPitch = leftPitch;
        this.rightX = rightX; this.rightY = rightY; this.rightZ = rightZ;
        this.rightYaw = rightYaw; this.rightPitch = rightPitch;
    }

    public static VRPoseData empty() { return new VRPoseData(); }

    public VRPoseData copy() {
        return new VRPoseData(flags, headX, headY, headZ, headYaw, headPitch,
                leftX, leftY, leftZ, leftYaw, leftPitch,
                rightX, rightY, rightZ, rightYaw, rightPitch);
    }

    public boolean isWellFormed() {
        if ((flags & ~KNOWN_FLAGS) != 0) return false;
        return position(headX, headY, headZ) && angle(headYaw, headPitch)
                && position(leftX, leftY, leftZ) && angle(leftYaw, leftPitch)
                && position(rightX, rightY, rightZ) && angle(rightYaw, rightPitch);
    }

    private static boolean position(float x, float y, float z) {
        return finite(x) && finite(y) && finite(z) && Math.abs(x) < 30_000_000.0F
                && Math.abs(y) < 30_000_000.0F && Math.abs(z) < 30_000_000.0F;
    }

    private static boolean angle(float yaw, float pitch) {
        return finite(yaw) && finite(pitch) && yaw >= -180.0F && yaw <= 180.0F
                && pitch >= -90.0F && pitch <= 90.0F;
    }

    public void write(ByteBuf buffer) {
        buffer.writeInt(flags);
        buffer.writeFloat(headX); buffer.writeFloat(headY); buffer.writeFloat(headZ);
        buffer.writeFloat(headYaw); buffer.writeFloat(headPitch);
        buffer.writeFloat(leftX); buffer.writeFloat(leftY); buffer.writeFloat(leftZ);
        buffer.writeFloat(leftYaw); buffer.writeFloat(leftPitch);
        buffer.writeFloat(rightX); buffer.writeFloat(rightY); buffer.writeFloat(rightZ);
        buffer.writeFloat(rightYaw); buffer.writeFloat(rightPitch);
    }

    public static VRPoseData read(ByteBuf buffer) {
        return new VRPoseData(buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public static VRPoseData interpolate(VRPoseData previous, VRPoseData current, float t) {
        VRPoseData out = current.copy();
        out.headX = lerp(previous, current, HEAD_TRACKED, previous.headX, current.headX, t);
        out.headY = lerp(previous, current, HEAD_TRACKED, previous.headY, current.headY, t);
        out.headZ = lerp(previous, current, HEAD_TRACKED, previous.headZ, current.headZ, t);
        out.headYaw = lerpAngle(previous, current, HEAD_TRACKED, previous.headYaw, current.headYaw, t);
        out.headPitch = lerp(previous, current, HEAD_TRACKED, previous.headPitch, current.headPitch, t);
        out.leftX = lerp(previous, current, LEFT_HAND_TRACKED, previous.leftX, current.leftX, t);
        out.leftY = lerp(previous, current, LEFT_HAND_TRACKED, previous.leftY, current.leftY, t);
        out.leftZ = lerp(previous, current, LEFT_HAND_TRACKED, previous.leftZ, current.leftZ, t);
        out.leftYaw = lerpAngle(previous, current, LEFT_HAND_TRACKED, previous.leftYaw, current.leftYaw, t);
        out.leftPitch = lerp(previous, current, LEFT_HAND_TRACKED, previous.leftPitch, current.leftPitch, t);
        out.rightX = lerp(previous, current, RIGHT_HAND_TRACKED, previous.rightX, current.rightX, t);
        out.rightY = lerp(previous, current, RIGHT_HAND_TRACKED, previous.rightY, current.rightY, t);
        out.rightZ = lerp(previous, current, RIGHT_HAND_TRACKED, previous.rightZ, current.rightZ, t);
        out.rightYaw = lerpAngle(previous, current, RIGHT_HAND_TRACKED, previous.rightYaw, current.rightYaw, t);
        out.rightPitch = lerp(previous, current, RIGHT_HAND_TRACKED, previous.rightPitch, current.rightPitch, t);
        return out;
    }

    private static float lerp(VRPoseData previous, VRPoseData current, int flag, float a, float b, float t) {
        if ((current.flags & flag) == 0 || (previous.flags & flag) == 0) return b;
        return a + (b - a) * t;
    }

    private static float lerpAngle(VRPoseData previous, VRPoseData current,
                                   int flag, float a, float b, float t) {
        if ((current.flags & flag) == 0 || (previous.flags & flag) == 0) return b;
        float delta = (b - a) % 360.0F;
        if (delta > 180.0F) delta -= 360.0F;
        if (delta < -180.0F) delta += 360.0F;
        float value = a + delta * t;
        while (value > 180.0F) value -= 360.0F;
        while (value < -180.0F) value += 360.0F;
        return value;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
