package com.gtnhvr.network;

import io.netty.buffer.ByteBuf;

/** Visual-only world-space pose payload shared by the Forge 1.7.10 client and server. */
public final class VRPoseData {
    public static final int HEAD_TRACKED = 1;
    public static final int LEFT_HAND_TRACKED = 2;
    public static final int RIGHT_HAND_TRACKED = 4;
    public static final int KNOWN_FLAGS = HEAD_TRACKED | LEFT_HAND_TRACKED | RIGHT_HAND_TRACKED;

    public int flags;
    public float headX, headY, headZ, headYaw, headPitch;
    public float leftX, leftY, leftZ, leftYaw, leftPitch;
    public float rightX, rightY, rightZ, rightYaw, rightPitch;
    public float headQx, headQy, headQz, headQw = 1.0F;
    public float leftQx, leftQy, leftQz, leftQw = 1.0F;
    public float rightQx, rightQy, rightQz, rightQw = 1.0F;

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
        VRPoseData out = new VRPoseData(flags, headX, headY, headZ, headYaw, headPitch,
                leftX, leftY, leftZ, leftYaw, leftPitch,
                rightX, rightY, rightZ, rightYaw, rightPitch);
        out.headQx = headQx; out.headQy = headQy; out.headQz = headQz; out.headQw = headQw;
        out.leftQx = leftQx; out.leftQy = leftQy; out.leftQz = leftQz; out.leftQw = leftQw;
        out.rightQx = rightQx; out.rightQy = rightQy; out.rightQz = rightQz; out.rightQw = rightQw;
        return out;
    }

    public boolean isWellFormed() {
        if ((flags & ~KNOWN_FLAGS) != 0) return false;
        return position(headX, headY, headZ) && angle(headYaw, headPitch)
                && position(leftX, leftY, leftZ) && angle(leftYaw, leftPitch)
                && position(rightX, rightY, rightZ) && angle(rightYaw, rightPitch)
                && quaternion(headQx, headQy, headQz, headQw, (flags & HEAD_TRACKED) != 0)
                && quaternion(leftQx, leftQy, leftQz, leftQw, (flags & LEFT_HAND_TRACKED) != 0)
                && quaternion(rightQx, rightQy, rightQz, rightQw, (flags & RIGHT_HAND_TRACKED) != 0);
    }

    private static boolean position(float x, float y, float z) {
        return finite(x) && finite(y) && finite(z) && Math.abs(x) < 30_000_000.0F
                && Math.abs(y) < 30_000_000.0F && Math.abs(z) < 30_000_000.0F;
    }

    private static boolean angle(float yaw, float pitch) {
        return finite(yaw) && finite(pitch) && yaw >= -180.0F && yaw <= 180.0F
                && pitch >= -90.0F && pitch <= 90.0F;
    }

    private static boolean quaternion(float x, float y, float z, float w, boolean required) {
        if (!finite(x) || !finite(y) || !finite(z) || !finite(w)) return false;
        if (!required) return true;
        float lengthSquared = x * x + y * y + z * z + w * w;
        return lengthSquared >= 0.5F && lengthSquared <= 1.5F;
    }

    public void write(ByteBuf buffer) {
        buffer.writeInt(flags);
        buffer.writeFloat(headX); buffer.writeFloat(headY); buffer.writeFloat(headZ);
        buffer.writeFloat(headYaw); buffer.writeFloat(headPitch);
        buffer.writeFloat(leftX); buffer.writeFloat(leftY); buffer.writeFloat(leftZ);
        buffer.writeFloat(leftYaw); buffer.writeFloat(leftPitch);
        buffer.writeFloat(rightX); buffer.writeFloat(rightY); buffer.writeFloat(rightZ);
        buffer.writeFloat(rightYaw); buffer.writeFloat(rightPitch);
        buffer.writeFloat(headQx); buffer.writeFloat(headQy); buffer.writeFloat(headQz); buffer.writeFloat(headQw);
        buffer.writeFloat(leftQx); buffer.writeFloat(leftQy); buffer.writeFloat(leftQz); buffer.writeFloat(leftQw);
        buffer.writeFloat(rightQx); buffer.writeFloat(rightQy); buffer.writeFloat(rightQz); buffer.writeFloat(rightQw);
    }

    public static VRPoseData read(ByteBuf buffer) {
        VRPoseData out = new VRPoseData(buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        out.headQx = buffer.readFloat(); out.headQy = buffer.readFloat();
        out.headQz = buffer.readFloat(); out.headQw = buffer.readFloat();
        out.leftQx = buffer.readFloat(); out.leftQy = buffer.readFloat();
        out.leftQz = buffer.readFloat(); out.leftQw = buffer.readFloat();
        out.rightQx = buffer.readFloat(); out.rightQy = buffer.readFloat();
        out.rightQz = buffer.readFloat(); out.rightQw = buffer.readFloat();
        return out;
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
        float[] h = lerpQuaternion(previous, current, HEAD_TRACKED,
                previous.headQx, previous.headQy, previous.headQz, previous.headQw,
                current.headQx, current.headQy, current.headQz, current.headQw, t);
        out.headQx = h[0]; out.headQy = h[1]; out.headQz = h[2]; out.headQw = h[3];
        float[] l = lerpQuaternion(previous, current, LEFT_HAND_TRACKED,
                previous.leftQx, previous.leftQy, previous.leftQz, previous.leftQw,
                current.leftQx, current.leftQy, current.leftQz, current.leftQw, t);
        out.leftQx = l[0]; out.leftQy = l[1]; out.leftQz = l[2]; out.leftQw = l[3];
        float[] r = lerpQuaternion(previous, current, RIGHT_HAND_TRACKED,
                previous.rightQx, previous.rightQy, previous.rightQz, previous.rightQw,
                current.rightQx, current.rightQy, current.rightQz, current.rightQw, t);
        out.rightQx = r[0]; out.rightQy = r[1]; out.rightQz = r[2]; out.rightQw = r[3];
        return out;
    }

    private static float[] lerpQuaternion(VRPoseData previous, VRPoseData current, int flag,
                                          float ax, float ay, float az, float aw,
                                          float bx, float by, float bz, float bw, float t) {
        if ((current.flags & flag) == 0 || (previous.flags & flag) == 0) {
            return new float[]{bx, by, bz, bw};
        }
        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0.0F) {
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }
        float x = ax + (bx - ax) * t;
        float y = ay + (by - ay) * t;
        float z = az + (bz - az) * t;
        float w = aw + (bw - aw) * t;
        float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (length < 1.0E-6F) return new float[]{bx, by, bz, bw};
        return new float[]{x / length, y / length, z / length, w / length};
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
