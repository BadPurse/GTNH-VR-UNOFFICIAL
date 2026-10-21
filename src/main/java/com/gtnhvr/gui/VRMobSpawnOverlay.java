package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;
import org.joml.Vector3f;

/**
 * VR Mob Spawn Overlay (F7-style light level indicator).
 * Renders colored crosses on the top face of blocks within a configurable radius:
 * - RED cross: mobs can spawn NOW (block light < 8 AND sky light < 8)
 * - YELLOW cross: mobs can spawn at NIGHT (block light < 8 but sky light >= 8)
 *
 * Toggled from the Tools tab of the VR Game Menu.
 * Based on the same algorithm as NEI F7 overlay.
 */
@Lwjgl3Aware
public final class VRMobSpawnOverlay {
    private static final VRMobSpawnOverlay INSTANCE = new VRMobSpawnOverlay();

    private boolean enabled = false;
    private int scanRadius = 16;

    // Cached scan results to avoid re-scanning every frame
    private int[] markerX = new int[0];
    private int[] markerY = new int[0];
    private int[] markerZ = new int[0];
    private byte[] markerType = new byte[0]; // 0=none, 1=yellow(night), 2=red(always)
    private int markerCount = 0;
    private long lastScanTick = -1L;
    private static final int SCAN_INTERVAL_TICKS = 10; // Re-scan every 10 ticks (0.5s)

    private VRMobSpawnOverlay() {}

    public static VRMobSpawnOverlay getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            markerCount = 0;
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Scans blocks around the player and caches spawn-eligible positions.
     * Called once every SCAN_INTERVAL_TICKS from the render loop.
     */
    public void update(Minecraft mc) {
        if (!enabled || mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }

        long currentTick = mc.theWorld.getTotalWorldTime();
        if (currentTick - lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }
        lastScanTick = currentTick;

        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);

        World world = mc.theWorld;
        int radius = Math.min(scanRadius, 24);
        int maxMarkers = (radius * 2 + 1) * (radius * 2 + 1) * 5; // rough upper bound
        if (markerX.length < maxMarkers) {
            markerX = new int[maxMarkers];
            markerY = new int[maxMarkers];
            markerZ = new int[maxMarkers];
            markerType = new byte[maxMarkers];
        }

        int count = 0;
        for (int dx = -radius; dx <= radius && count < maxMarkers; dx++) {
            for (int dz = -radius; dz <= radius && count < maxMarkers; dz++) {
                int x = px + dx;
                int z = pz + dz;

                // Check 5 layers around player Y
                for (int dy = -2; dy <= 2 && count < maxMarkers; dy++) {
                    int y = py + dy;
                    if (y < 1 || y > 254) continue;

                    // The block at (x, y, z) must be air/passable and the block below must be solid
                    Block blockAt = world.getBlock(x, y, z);
                    Block blockBelow = world.getBlock(x, y - 1, z);

                    if (blockAt == null || blockBelow == null) continue;

                    // Block must be passable (air, tall grass, etc.)
                    if (blockAt.isNormalCube()) continue;
                    if (blockAt.getMaterial() != Material.air
                            && blockAt.getMaterial() != Material.plants
                            && blockAt.getMaterial() != Material.vine) continue;

                    // Block above must also be passable (mobs are 2 blocks tall)
                    Block blockAbove = world.getBlock(x, y + 1, z);
                    if (blockAbove != null && blockAbove.isNormalCube()) continue;

                    // Block below must be solid and spawnable
                    if (!blockBelow.isNormalCube()) continue;
                    if (!blockBelow.getMaterial().isOpaque()) continue;

                    // Check if this block type can support mob spawning
                    // (Bedrock, barriers, slabs etc. can't)
                    if (blockBelow.getMaterial() == Material.air) continue;

                    // Check light levels
                    int blockLight = world.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
                    int skyLight = world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);

                    if (blockLight >= 8) continue; // Well-lit by torches, mobs can't spawn

                    byte type;
                    if (skyLight >= 8) {
                        type = 1; // Yellow: safe during day, dangerous at night
                    } else {
                        type = 2; // Red: always dangerous
                    }

                    markerX[count] = x;
                    markerY[count] = y;
                    markerZ[count] = z;
                    markerType[count] = type;
                    count++;
                }
            }
        }
        markerCount = count;
    }

    /**
     * Renders the mob spawn markers in 3D world space as colored X crosses on block surfaces.
     * Must be called during the world render pass after terrain but before GUI.
     */
    public void render(float partialTicks) {
        if (!enabled || markerCount == 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        // renderStereoOverlays uses the room-space menu model-view matrix, not Minecraft's
        // world camera matrix. Convert every marker from world space to the same room space.
        // The stored Y is the air block's floor level, i.e. the top face of the solid block below.
        VRControllerHandler controller = VRControllerHandler.getInstance();
        double worldCrossHalfSize = 0.30D;
        double yaw = Math.toRadians(180.0D + com.gtnhvr.HeadPoseController.getBaseYaw());
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        try {
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthMask(false);
            GLStateManager.glLineWidth(2.5F);

            // Offset slightly above the block surface to prevent z-fighting.
            // Use the same world->room transform as world-anchored VR screens so markers line up
            // with the actual block face under the room-space render matrix.
            double yOffset = 0.005D;

            for (int i = 0; i < markerCount; i++) {
                Vector3f center = controller.worldToRoom(
                        markerX[i] + 0.5D,
                        markerY[i] + yOffset,
                        markerZ[i] + 0.5D,
                        partialTicks);

                byte type = markerType[i];
                if (type == 1) {
                    // Yellow: spawns at night only
                    GLStateManager.glColor4f(1.0F, 1.0F, 0.0F, 0.75F);
                } else {
                    // Red: always spawns
                    GLStateManager.glColor4f(1.0F, 0.1F, 0.1F, 0.85F);
                }

                // Draw a centered 0.6 x 0.6 block-top cross. Rotate its horizontal offsets
                // by the same world-yaw transform used by worldToRoom().
                GLStateManager.glBegin(GL11.GL_LINES);
                gtnhvr$vertexRoom(center, -worldCrossHalfSize, -worldCrossHalfSize, cosYaw, sinYaw);
                gtnhvr$vertexRoom(center,  worldCrossHalfSize,  worldCrossHalfSize, cosYaw, sinYaw);
                gtnhvr$vertexRoom(center,  worldCrossHalfSize, -worldCrossHalfSize, cosYaw, sinYaw);
                gtnhvr$vertexRoom(center, -worldCrossHalfSize,  worldCrossHalfSize, cosYaw, sinYaw);
                GLStateManager.glEnd();
            }
        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    private static void gtnhvr$vertexRoom(Vector3f center, double worldDx, double worldDz,
                                           double cosYaw, double sinYaw) {
        double roomDx = worldDx * cosYaw + worldDz * sinYaw;
        double roomDz = -worldDx * sinYaw + worldDz * cosYaw;
        GLStateManager.glVertex3d(center.x + roomDx, center.y, center.z + roomDz);
    }
}
