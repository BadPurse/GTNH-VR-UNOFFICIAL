package com.gtnhvr;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.lwjgl.openxr.XrPosef;

/**
 * First camera integration: HMD orientation drives Minecraft's look direction.
 * Positional head offset is intentionally kept out of entity coordinates until
 * the renderer hook is installed; moving the entity itself would break physics.
 */
@Lwjgl3Aware
public final class HeadPoseController {
    private static boolean calibrated;
    private static float baseYaw;
    private static float basePitch;

    private HeadPoseController() {}

    public static void apply() {
        if (com.gtnhvr.settings.VRClientSettings.vrPlayMode == com.gtnhvr.settings.VRPlayMode.DISABLED
                || !com.gtnhvr.settings.VRClientSettings.headTracking) return;
        XrPosef pose = OpenXRRuntime.getHeadPose();
        if (!OpenXRRuntime.isSessionRunning() || pose == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        Entity entity = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (entity == null) return;

        org.joml.Quaternionf hmdOrientation = new org.joml.Quaternionf(
                pose.orientation().x(), pose.orientation().y(),
                pose.orientation().z(), pose.orientation().w()
        );
        org.joml.Vector3f fwdRoom = hmdOrientation.transform(new org.joml.Vector3f(0.0F, 0.0F, -1.0F));
        float roomYaw = (float) Math.toDegrees(Math.atan2(fwdRoom.x, -fwdRoom.z));

        if (!calibrated) {
            baseYaw = entity.rotationYaw - roomYaw;
            calibrated = true;
        }

        currentHmdYaw = roomYaw;

        float baseYawRad = (float) Math.toRadians(baseYaw);
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);

        // Room space (-Z forward, +X right) to Minecraft world space:
        float wx = -fwdRoom.x * cos + fwdRoom.z * sin;
        float wz = -fwdRoom.x * sin - fwdRoom.z * cos;
        float wy = fwdRoom.y;

        float horizLen = (float) Math.sqrt(wx * wx + wz * wz);
        float worldYaw = (horizLen > 1e-4F) ? (float) Math.toDegrees(Math.atan2(-wx, wz)) : entity.rotationYaw;
        float worldPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, wy))));

        currentHmdWorldYaw = worldYaw;

        // Rotation device (Visor RotationMode): HMD by default, a controller when selected,
        // and the "using hand" while an item is in use when Compatible Look is enabled.
        float lookYaw = worldYaw;
        float lookPitch = worldPitch;
        org.joml.Vector3f handDir = selectLookHandDirection(entity);
        if (handDir != null) {
            float handHoriz = (float) Math.sqrt(handDir.x * handDir.x + handDir.z * handDir.z);
            if (handHoriz > 1e-4F) {
                lookYaw = (float) Math.toDegrees(Math.atan2(-handDir.x, handDir.z));
            }
            lookPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, handDir.y))));
        }

        entity.rotationYaw = lookYaw;
        entity.prevRotationYaw = lookYaw;
        entity.rotationPitch = Math.max(-90.0f, Math.min(90.0f, lookPitch));
        entity.prevRotationPitch = entity.rotationPitch;

        if (entity instanceof net.minecraft.entity.EntityLivingBase living) {
            living.rotationYawHead = entity.rotationYaw;
            living.prevRotationYawHead = entity.rotationYaw;
            living.renderYawOffset = entity.rotationYaw;
            living.prevRenderYawOffset = entity.rotationYaw;
        }
    }

    private static float currentHmdYaw = 0.0F;
    private static float currentHmdWorldYaw = 0.0F;

    /** Minecraft-world yaw of the headset gaze, independent of the selected rotation device. */
    public static float getHmdWorldYaw() {
        return currentHmdWorldYaw;
    }

    private static org.joml.Vector3f selectLookHandDirection(Entity entity) {
        VRControllerHandler handler = VRControllerHandler.getInstance();
        com.gtnhvr.gameplay.tasks.VRTaskBow bow = com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance();
        if (bow.hasAim()) {
            return bow.getAimDirectionWorld(handler);
        }
        boolean flying = false;
        if (entity instanceof net.minecraft.entity.player.EntityPlayer player) {
            flying = player.capabilities.isFlying && player.ridingEntity == null;
        }
        com.gtnhvr.settings.RotationMode mode = flying
                ? com.gtnhvr.settings.VRClientSettings.rotationFlyMode
                : com.gtnhvr.settings.VRClientSettings.rotationMode;

        if (mode == com.gtnhvr.settings.RotationMode.MAIN_HAND && handler.isRightHandTracked()) {
            return new org.joml.Vector3f(handler.getRightHandWorldDir());
        }
        if (mode == com.gtnhvr.settings.RotationMode.OFFHAND && handler.isLeftHandTracked()) {
            return new org.joml.Vector3f(handler.getLeftHandWorldDir());
        }
        if (com.gtnhvr.settings.VRClientSettings.compatibleLookDirection) {
            int using = handler.getUsingHand();
            if (using == 1 && handler.isRightHandTracked()) {
                return new org.joml.Vector3f(handler.getRightHandWorldDir());
            }
            if (using == 2 && handler.isLeftHandTracked()) {
                return new org.joml.Vector3f(handler.getLeftHandWorldDir());
            }
        }
        return null;
    }

    public static float getY() {
        XrPosef pose = OpenXRRuntime.getHeadPose();
        return (pose != null) ? pose.position$().y() : 1.75F;
    }

    public static float getHmdYaw() {
        return currentHmdYaw;
    }

    public static float getWorldGazeYaw() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            return mc.thePlayer.rotationYaw;
        }
        return baseYaw + currentHmdYaw;
    }

    public static org.joml.Vector3f getWorldGazeDirection() {
        XrPosef pose = OpenXRRuntime.getHeadPose();
        if (pose == null) return new org.joml.Vector3f(0.0F, 0.0F, 1.0F);
        org.joml.Quaternionf hmdOrientation = new org.joml.Quaternionf(
                pose.orientation().x(), pose.orientation().y(),
                pose.orientation().z(), pose.orientation().w()
        );
        org.joml.Vector3f fwdRoom = hmdOrientation.transform(new org.joml.Vector3f(0.0F, 0.0F, -1.0F));
        // Rotate from room space to Minecraft world space:
        float baseYawRad = (float) Math.toRadians(baseYaw);
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);
        float wx = -fwdRoom.x * cos + fwdRoom.z * sin;
        float wz = -fwdRoom.x * sin - fwdRoom.z * cos;
        return new org.joml.Vector3f(wx, fwdRoom.y, wz);
    }

    public static float getBaseYaw() {
        return baseYaw;
    }

    public static void setBaseYaw(float yaw) {
        baseYaw = yaw;
    }

    public static void resetCalibration() {
        calibrated = false;
    }

    public static void recenter() {
        resetCalibration();
        OpenXRRuntime.resetSeatedBaseline();
    }
}
