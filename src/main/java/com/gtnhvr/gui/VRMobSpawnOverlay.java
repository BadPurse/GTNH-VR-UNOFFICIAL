package com.gtnhvr.gui;

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

/**
 * VR Mob Spawn Overlay (F7-style light level indicator).
 * Renders colored crosses on the top face of blocks within a configurable radius:
 * - RED cross: mobs can spawn NOW (block light < 8 AND sky light < 8)
 * - YELLOW cross: mobs can spawn at NIGHT (block light < 8 but sky light >= 8)
 *
 * Toggled via the Tools overlay button in VR settings.
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

        double px = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partialTicks;
        double py = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * partialTicks;
        double pz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partialTicks;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.5F);

            // Offset slightly above the block surface to prevent z-fighting
            float yOffset = 0.005F;

            for (int i = 0; i < markerCount; i++) {
                double bx = markerX[i] - px;
                double by = markerY[i] - py;
                double bz = markerZ[i] - pz;

                byte type = markerType[i];
                if (type == 1) {
                    // Yellow: spawns at night only
                    GL11.glColor4f(1.0F, 1.0F, 0.0F, 0.75F);
                } else {
                    // Red: always spawns
                    GL11.glColor4f(1.0F, 0.1F, 0.1F, 0.85F);
                }

                // Draw an X cross on top of the block below (at y level)
                GL11.glBegin(GL11.GL_LINES);
                // Diagonal 1: (0.2, 0, 0.2) to (0.8, 0, 0.8)
                GL11.glVertex3d(bx + 0.2, by + yOffset, bz + 0.2);
                GL11.glVertex3d(bx + 0.8, by + yOffset, bz + 0.8);
                // Diagonal 2: (0.8, 0, 0.2) to (0.2, 0, 0.8)
                GL11.glVertex3d(bx + 0.8, by + yOffset, bz + 0.2);
                GL11.glVertex3d(bx + 0.2, by + yOffset, bz + 0.8);
                GL11.glEnd();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
