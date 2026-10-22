package com.gtnhvr.network;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

/** Samples and uploads display-only poses at 20 Hz after a v3 server handshake, including while a GUI is open. */
@Lwjgl3Aware
@SideOnly(Side.CLIENT)
public final class VRPoseSyncClient {
    private static final long SEND_INTERVAL_NANOS = 50_000_000L;
    private static long lastSendNanos = 0L;
    private static boolean sentActivePose = false;

    private VRPoseSyncClient() {}

    public static void reset() {
        lastSendNanos = 0L;
        sentActivePose = false;
    }

    public static void tick() {
        if (!VRNetwork.serverSupportsPoseSync()) return;
        long now = System.nanoTime();
        if (now - lastSendNanos < SEND_INTERVAL_NANOS) return;
        lastSendNanos = now;

        Minecraft mc = Minecraft.getMinecraft();
        VRControllerHandler controllers = VRControllerHandler.getInstance();
        if (!OpenXRRuntime.isSessionRunning() || mc == null || mc.theWorld == null
                || mc.thePlayer == null) {
            clearIfNeeded();
            return;
        }

        VRPoseData pose = VRPoseData.empty();
        XrPosef head = OpenXRRuntime.getHeadPose();
        if (head != null) {
            Vector3f roomHead = new Vector3f(head.position$().x(), head.position$().y(), head.position$().z());
            Vec3 worldHead = controllers.roomToWorld(roomHead, 1.0F);
            Quaternionf headRoom = new Quaternionf(head.orientation().x(), head.orientation().y(),
                    head.orientation().z(), head.orientation().w());
            Quaternionf headWorld = roomRotationToWorld(headRoom);
            Vector3f gaze = headWorld.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            if (gaze.lengthSquared() > 1.0E-6F) gaze.normalize();
            storeQuaternion(pose, 0, headWorld);
            pose.headX = (float) worldHead.xCoord;
            pose.headY = (float) worldHead.yCoord;
            pose.headZ = (float) worldHead.zCoord;
            pose.headYaw = (float) Math.toDegrees(Math.atan2(-gaze.x, gaze.z));
            pose.headPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, gaze.y))));
            pose.flags |= VRPoseData.HEAD_TRACKED;
        }

        if (controllers.isLeftHandTracked()) {
            Vector3f left = controllers.getLeftHandWorldPos(1.0F);
            pose.leftX = left.x; pose.leftY = left.y; pose.leftZ = left.z;
            Quaternionf leftWorld = roomRotationToWorld(controllers.getLeftHandRot());
            Vector3f leftDirection = leftWorld.transform(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
            pose.leftYaw = (float) Math.toDegrees(Math.atan2(-leftDirection.x, leftDirection.z));
            pose.leftPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, leftDirection.y))));
            storeQuaternion(pose, 1, leftWorld);
            pose.flags |= VRPoseData.LEFT_HAND_TRACKED;
        }

        if (controllers.isRightHandTracked()) {
            Vector3f right = controllers.getRightHandWorldPos(1.0F);
            pose.rightX = right.x; pose.rightY = right.y; pose.rightZ = right.z;
            Quaternionf rightWorld = roomRotationToWorld(controllers.getRightHandRot());
            Vector3f rightDirection = rightWorld.transform(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
            pose.rightYaw = (float) Math.toDegrees(Math.atan2(-rightDirection.x, rightDirection.z));
            pose.rightPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, rightDirection.y))));
            storeQuaternion(pose, 2, rightWorld);
            pose.flags |= VRPoseData.RIGHT_HAND_TRACKED;
        }

        if (pose.flags != 0 && pose.isWellFormed()) {
            VRNetwork.sendPose(pose);
            sentActivePose = true;
        } else {
            clearIfNeeded();
        }
    }

    private static Quaternionf roomRotationToWorld(Quaternionf roomRotation) {
        float angle = (float) Math.toRadians(180.0F - HeadPoseController.getBaseYaw());
        return new Quaternionf().rotationY(angle).mul(new Quaternionf(roomRotation)).normalize();
    }

    private static void storeQuaternion(VRPoseData pose, int device, Quaternionf q) {
        switch (device) {
            case 0:
                pose.headQx = q.x; pose.headQy = q.y; pose.headQz = q.z; pose.headQw = q.w;
                break;
            case 1:
                pose.leftQx = q.x; pose.leftQy = q.y; pose.leftQz = q.z; pose.leftQw = q.w;
                break;
            case 2:
                pose.rightQx = q.x; pose.rightQy = q.y; pose.rightQz = q.z; pose.rightQw = q.w;
                break;
            default:
                throw new IllegalArgumentException("Unknown VR pose device: " + device);
        }
    }

    private static void clearIfNeeded() {
        if (sentActivePose && VRNetwork.serverSupportsPoseSync()) VRNetwork.sendPose(VRPoseData.empty());
        sentActivePose = false;
    }
}
