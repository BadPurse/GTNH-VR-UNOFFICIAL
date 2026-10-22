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
        // Force the first scan after enabling. Otherwise an off/on toggle during the
        // same ten-tick scan window leaves the overlay empty until the next world tick.
        lastScanTick = -1L;
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
        if (lastScanTick >= 0L && currentTick - lastScanTick < SCAN_INTERVAL_TICKS) {
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
        render(partialTicks, com.gtnhvr.OpenXRRuntime.getCurrentEyeIndex());
    }

    /**
     * Draw spawn markers in the same eye/world camera space as the world crosshair.
     * Do not transform them to room space: this pass is rendered with the eye projection
     * and Minecraft-world camera matrix, so world-relative block coordinates are required.
     */
    public void render(float partialTicks, int eyeIndex) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return;

        // Keep markers alive even if this render path runs before the next controller update.
        update(mc);
        if (markerCount == 0) return;

        net.minecraft.entity.Entity renderEntity =
                mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        double camX = renderEntity.prevPosX + (renderEntity.posX - renderEntity.prevPosX) * (double) partialTicks;
        double camY = renderEntity.prevPosY + (renderEntity.posY - renderEntity.prevPosY) * (double) partialTicks;
        double camZ = renderEntity.prevPosZ + (renderEntity.posZ - renderEntity.prevPosZ) * (double) partialTicks;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        java.nio.FloatBuffer projection = com.gtnhvr.OpenXRRuntime.getEyeProjectionBuffer(eyeIndex, 0.05F, 100.0F);
        if (projection != null) {
            GLStateManager.glLoadMatrix(projection);
        }

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        java.nio.FloatBuffer modelView = com.gtnhvr.OpenXRRuntime.getEyeModelViewBuffer(eyeIndex, partialTicks);
        if (modelView != null) {
            GLStateManager.glLoadMatrix(modelView);
        }

        try {
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
            GLStateManager.glDepthMask(false);
            GLStateManager.glLineWidth(2.5F);

            // markerY is the air block above the solid support block; its Y coordinate
            // is exactly the support block's top face. The small offset avoids z-fighting.
            final double yOffset = 0.005D;
            for (int i = 0; i < markerCount; i++) {
                double bx = markerX[i] - camX;
                double by = markerY[i] - camY + yOffset;
                double bz = markerZ[i] - camZ;

                byte type = markerType[i];
                if (type == 1) {
                    GLStateManager.glColor4f(1.0F, 1.0F, 0.0F, 0.85F);
                } else {
                    GLStateManager.glColor4f(1.0F, 0.1F, 0.1F, 0.95F);
                }

                GLStateManager.glBegin(GL11.GL_LINES);
                GLStateManager.glVertex3d(bx + 0.2D, by, bz + 0.2D);
                GLStateManager.glVertex3d(bx + 0.8D, by, bz + 0.8D);
                GLStateManager.glVertex3d(bx + 0.8D, by, bz + 0.2D);
                GLStateManager.glVertex3d(bx + 0.2D, by, bz + 0.8D);
                GLStateManager.glEnd();
            }
        } finally {
            GLStateManager.glPopMatrix(); // Restore model-view
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix(); // Restore projection
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPopAttrib();
        }
    }

}
