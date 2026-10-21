package com.gtnhvr.gameplay.tasks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.MovementMode;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 Visor TaskTeleport ported to Minecraft 1.7.10:
 * When MovementMode is TELEPORT, pushing the movement thumbstick forward casts
 * a 3D parabolic teleport arc from the controller hand.
 * Intersects with solid ground ensuring 2-block standing clearance.
 * Releasing thumbstick teleports the player, resets fall distance, plays step sounds,
 * and consumes survival food exhaustion.
 */
@Lwjgl3Aware
public class VRTaskTeleport {
    private static final VRTaskTeleport INSTANCE = new VRTaskTeleport();

    private static final int MAX_STEPS = 30;
    private static final float STEP_TIME = 0.06F;
    private static final float INITIAL_VELOCITY = 14.0F;
    private static final float GRAVITY = 9.8F;

    private boolean isAiming = false;
    private final List<Vector3f> arcPoints = new ArrayList<>();
    private Vec3 destination = null;
    private boolean validLanding = false;

    private VRTaskTeleport() {}

    public static VRTaskTeleport getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (VRClientSettings.movementMode != MovementMode.TELEPORT) {
            clear();
            return;
        }

        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear();
            return;
        }

        float stickY = handler.getLeftStickY();
        boolean aimingInput = stickY > 0.55F;

        if (aimingInput) {
            isAiming = true;
            calculateArc(mc, handler);
        } else {
            if (isAiming) {
                // Stick released -> execute teleport if landing was valid!
                if (validLanding && destination != null) {
                    executeTeleport(mc, handler);
                }
            }
            clear();
        }
    }

    private void calculateArc(Minecraft mc, VRControllerHandler handler) {
        arcPoints.clear();
        destination = null;
        validLanding = false;

        final float partialTicks = 1.0F;
        Vector3f handRoomPos = handler.getLeftHandPos();
        Vec3 start = handler.roomToWorld(handRoomPos, partialTicks);
        Vector3f dir = handler.getLeftHandWorldDir();

        // The arc range grows with the world scale (range ~ v^2 / g).
        float scale = Math.max(0.25F, Math.min(4.0F, VRClientSettings.worldScale));
        float speed = INITIAL_VELOCITY * (float) Math.sqrt(scale);

        arcPoints.add(new Vector3f(handRoomPos));

        Vec3 current = Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord);
        float velX = dir.x * speed;
        float velY = dir.y * speed;
        float velZ = dir.z * speed;

        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3 next = Vec3.createVectorHelper(
                    current.xCoord + velX * STEP_TIME,
                    current.yCoord + velY * STEP_TIME,
                    current.zCoord + velZ * STEP_TIME);
            velY -= GRAVITY * STEP_TIME;

            MovingObjectPosition hit = mc.theWorld.func_147447_a(current, next, false, true, false);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (hit.sideHit == 1) { // top face of a block
                    double footX = hit.blockX + 0.5D;
                    double footY = hit.hitVec.yCoord;
                    double footZ = hit.blockZ + 0.5D;
                    if (hasStandingRoom(mc, footX, footY, footZ)) {
                        destination = Vec3.createVectorHelper(footX, footY, footZ);
                        validLanding = true;
                    }
                }
                arcPoints.add(handler.worldToRoom(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord, partialTicks));
                break;
            }
            current = next;
            arcPoints.add(handler.worldToRoom(next.xCoord, next.yCoord, next.zCoord, partialTicks));
        }
    }

    /** The player's collision box must fit at the landing point (also handles slabs and stairs). */
    private boolean hasStandingRoom(Minecraft mc, double x, double footY, double z) {
        float halfWidth = mc.thePlayer.width * 0.5F;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                x - halfWidth, footY + 0.02D, z - halfWidth,
                x + halfWidth, footY + mc.thePlayer.height, z + halfWidth);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty();
    }

    private void executeTeleport(Minecraft mc, VRControllerHandler handler) {
        double dist = mc.thePlayer.getDistance(destination.xCoord, destination.yCoord, destination.zCoord);
        mc.thePlayer.setPositionAndUpdate(destination.xCoord, destination.yCoord, destination.zCoord);
        mc.thePlayer.fallDistance = 0.0F;
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;

        // Step sound
        int bx = MathHelper.floor_double(destination.xCoord);
        int by = MathHelper.floor_double(destination.yCoord - 0.05D);
        int bz = MathHelper.floor_double(destination.zCoord);
        Block block = mc.theWorld.getBlock(bx, by, bz);
        if (block != null) {
            mc.theWorld.playSound(destination.xCoord, destination.yCoord, destination.zCoord,
                    block.stepSound.getStepResourcePath(),
                    (block.stepSound.getVolume() + 1.0F) / 4.0F,
                    block.stepSound.getPitch() * 0.8F, false);
        }

        // Survival hunger exhaustion
        if (mc.thePlayer.capabilities != null && !mc.thePlayer.capabilities.isCreativeMode) {
            mc.thePlayer.addExhaustion((float) (dist * 0.05F));
        }

        handler.triggerHaptic(true, 8_000_000L, 0.50F, 0);
    }

    public void renderTeleportArc() {
        if (!isAiming || arcPoints.size() < 2) return;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        try {
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GLStateManager.glLineWidth(4.0F);

            if (validLanding) {
                GLStateManager.glColor4f(0.20F, 0.80F, 1.0F, 0.85F); // Vibrant cyan
            } else {
                GLStateManager.glColor4f(1.0F, 0.25F, 0.25F, 0.70F); // Red (invalid)
            }

            GLStateManager.glBegin(GL11.GL_LINE_STRIP);
            for (Vector3f pt : arcPoints) {
                GLStateManager.glVertex3f(pt.x, pt.y, pt.z);
            }
            GLStateManager.glEnd();

            // Landing reticle disk if valid
            if (validLanding && destination != null && !arcPoints.isEmpty()) {
                Vector3f lastPt = arcPoints.get(arcPoints.size() - 1);
                GLStateManager.glPushMatrix();
                GLStateManager.glTranslatef(lastPt.x, lastPt.y + 0.02F, lastPt.z);
                GLStateManager.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

                GLStateManager.glColor4f(0.20F, 0.90F, 1.0F, 0.60F);
                GLStateManager.glBegin(GL11.GL_LINE_LOOP);
                int segments = 24;
                float radius = 0.50F / Math.max(0.25F, Math.min(4.0F, VRClientSettings.worldScale));
                for (int s = 0; s < segments; s++) {
                    double theta = 2.0 * Math.PI * s / segments;
                    GLStateManager.glVertex2d(Math.cos(theta) * radius, Math.sin(theta) * radius);
                }
                GLStateManager.glEnd();
                GLStateManager.glPopMatrix();
            }

        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    public boolean isAiming() {
        return isAiming;
    }

    public void clear() {
        isAiming = false;
        arcPoints.clear();
        destination = null;
        validLanding = false;
    }
}
