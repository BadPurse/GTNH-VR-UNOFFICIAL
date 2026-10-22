package com.gtnhvr.gui;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.network.VRPoseData;
import com.gtnhvr.network.VRRemotePoseClientState;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.UUID;

/** Draws networked remote VR arm poses. It never changes gameplay or player entity state. */
@Lwjgl3Aware
@SideOnly(Side.CLIENT)
public final class VRRemotePoseRenderer {
    private VRRemotePoseRenderer() {}

    public static void render(Minecraft mc, float partialTicks) {
        if (mc == null || mc.theWorld == null || mc.currentScreen != null || mc.thePlayer == null) return;
        VRControllerHandler controllers = VRControllerHandler.getInstance();
        for (Object value : mc.theWorld.playerEntities) {
            if (!(value instanceof AbstractClientPlayer)) continue;
            AbstractClientPlayer player = (AbstractClientPlayer) value;
            if (player == mc.thePlayer) continue;
            UUID id = player.getUniqueID();
            VRPoseData pose = VRRemotePoseClientState.getInterpolated(id);
            if (pose == null || pose.flags == 0) continue;
            ResourceLocation skin = player.getLocationSkin();
            if (skin == null) continue;
            boolean slim = isSlimSkin(player);

            if ((pose.flags & VRPoseData.LEFT_HAND_TRACKED) != 0) {
                Vector3f room = controllers.worldToRoom(pose.leftX, pose.leftY, pose.leftZ, partialTicks);
                Quaternionf rotation = worldRotationToRoom(pose.leftQx, pose.leftQy, pose.leftQz, pose.leftQw);
                VRHandRenderer.renderRemoteArm(room, rotation, skin, false, mc, slim);
            }
            if ((pose.flags & VRPoseData.RIGHT_HAND_TRACKED) != 0) {
                Vector3f room = controllers.worldToRoom(pose.rightX, pose.rightY, pose.rightZ, partialTicks);
                Quaternionf rotation = worldRotationToRoom(pose.rightQx, pose.rightQy, pose.rightQz, pose.rightQw);
                VRHandRenderer.renderRemoteArm(room, rotation, skin, true, mc, slim);
            }
        }
    }

    /** Converts an absolute Minecraft-world orientation back into this client's room frame. */
    private static Quaternionf worldRotationToRoom(float qx, float qy, float qz, float qw) {
        float angle = (float) Math.toRadians(180.0F + HeadPoseController.getBaseYaw());
        return new Quaternionf().rotationY(angle).mul(new Quaternionf(qx, qy, qz, qw)).normalize();
    }

    private static boolean isSlimSkin(AbstractClientPlayer player) {
        try {
            Method method = player.getClass().getMethod("getSkinType");
            return "slim".equalsIgnoreCase(String.valueOf(method.invoke(player)));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
