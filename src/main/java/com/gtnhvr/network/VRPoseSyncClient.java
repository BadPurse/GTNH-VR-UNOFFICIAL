package com.gtnhvr.network;

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

/** Samples and uploads display-only poses at 20 Hz after a v2 server handshake, including while a GUI is open. */
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
            Vector3f gazeRoom = new Quaternionf(head.orientation().x(), head.orientation().y(),
                    head.orientation().z(), head.orientation().w()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
            Vector3f gaze = controllers.roomDirectionToWorld(gazeRoom);
            if (gaze.lengthSquared() > 1.0E-6F) gaze.normalize();
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
            pose.leftYaw = controllers.getLeftHandWorldYaw();
            pose.leftPitch = controllers.getLeftHandWorldPitch();
            pose.flags |= VRPoseData.LEFT_HAND_TRACKED;
        }

        if (controllers.isRightHandTracked()) {
            Vector3f right = controllers.getRightHandWorldPos(1.0F);
            pose.rightX = right.x; pose.rightY = right.y; pose.rightZ = right.z;
            pose.rightYaw = controllers.getRightHandWorldYaw();
            pose.rightPitch = controllers.getRightHandWorldPitch();
            pose.flags |= VRPoseData.RIGHT_HAND_TRACKED;
        }

        if (pose.flags != 0 && pose.isWellFormed()) {
            VRNetwork.sendPose(pose);
            sentActivePose = true;
        } else {
            clearIfNeeded();
        }
    }

    private static void clearIfNeeded() {
        if (sentActivePose && VRNetwork.serverSupportsPoseSync()) VRNetwork.sendPose(VRPoseData.empty());
        sentActivePose = false;
    }
}
