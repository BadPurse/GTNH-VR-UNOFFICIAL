package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Renders an authentic GregTech New Horizons 3D VR Main Menu environment:
 * 1. Deep Space Cosmic Void & procedural 3D twinkling starfield (zero pixelation, infinite resolution).
 * 2. Multi-spectral galactic nebula dust clouds (electric cyan and cosmic violet).
 * 3. Majestic distant Gas Giant planet with glowing atmospheric limb & planetary rings.
 * 4. Distant rotating GregTech / Stargate orbital ring with glowing cyan chevrons.
 * 5. Crisp high-tech GregTech Machine Casing platform under player's feet with pulsing circuit traces.
 */
@Lwjgl3Aware
public final class VRMenuEnvironment {
    private static final VRMenuEnvironment INSTANCE = new VRMenuEnvironment();

    private static final ResourceLocation GT_FLOOR_TEXTURE = new ResourceLocation("gregtech", "textures/blocks/materialicons/blockCasingAdvanced.png");
    private static final ResourceLocation FALLBACK_FLOOR_TEXTURE = new ResourceLocation("textures/blocks/iron_block.png");
    private static ResourceLocation resolvedFloorTexture = null;

    // Starfield data
    private static final int NUM_STARS = 1400;
    private final float[] starPositions = new float[NUM_STARS * 3];
    private final float[] starColors = new float[NUM_STARS * 3];
    private final float[] starSizes = new float[NUM_STARS];
    private final float[] starTwinkleSpeed = new float[NUM_STARS];
    private final float[] starPhase = new float[NUM_STARS];

    private float environmentRotation = 0.0F;
    private float ringRotation = 0.0F;
    private float animTime = 0.0F;
    private long lastTimeNano = System.nanoTime();

    private VRMenuEnvironment() {
        initStarfield();
    }

    public static VRMenuEnvironment getInstance() {
        return INSTANCE;
    }

    private void initStarfield() {
        Random rand = new Random(0x47544E48L); // Seed: "GTNH"
        float radius = 75.0F;

        for (int i = 0; i < NUM_STARS; i++) {
            // Fibonacci sphere distribution for uniform celestial coverage
            float phi = (float) Math.acos(1.0 - 2.0 * (i + 0.5) / NUM_STARS);
            float theta = (float) (Math.PI * (1.0 + Math.sqrt(5.0)) * i);

            float r = radius + (rand.nextFloat() * 10.0F - 5.0F);
            starPositions[i * 3]     = (float) (r * Math.sin(phi) * Math.cos(theta));
            starPositions[i * 3 + 1] = (float) (r * Math.cos(phi));
            starPositions[i * 3 + 2] = (float) (r * Math.sin(phi) * Math.sin(theta));

            // Star spectral color distribution (GTNH cosmic space palette)
            float roll = rand.nextFloat();
            if (roll < 0.40F) {
                // Stellar blue-white
                starColors[i * 3]     = 0.70F + rand.nextFloat() * 0.20F;
                starColors[i * 3 + 1] = 0.85F + rand.nextFloat() * 0.15F;
                starColors[i * 3 + 2] = 1.00F;
            } else if (roll < 0.65F) {
                // Electric cyan (GregTech neon energy)
                starColors[i * 3]     = 0.20F;
                starColors[i * 3 + 1] = 0.90F + rand.nextFloat() * 0.10F;
                starColors[i * 3 + 2] = 1.00F;
            } else if (roll < 0.85F) {
                // Brilliant white
                starColors[i * 3]     = 0.95F;
                starColors[i * 3 + 1] = 0.97F;
                starColors[i * 3 + 2] = 1.00F;
            } else {
                // Distant supergiant amber/gold
                starColors[i * 3]     = 1.00F;
                starColors[i * 3 + 1] = 0.80F + rand.nextFloat() * 0.15F;
                starColors[i * 3 + 2] = 0.45F;
            }

            starSizes[i] = 1.5F + rand.nextFloat() * 2.2F;
            starTwinkleSpeed[i] = 1.5F + rand.nextFloat() * 3.5F;
            starPhase[i] = rand.nextFloat() * (float) (Math.PI * 2.0);
        }
    }

    private static ResourceLocation getFloorTexture(Minecraft mc) {
        if (resolvedFloorTexture != null) return resolvedFloorTexture;
        try {
            if (mc.getResourceManager().getResource(GT_FLOOR_TEXTURE) != null) {
                resolvedFloorTexture = GT_FLOOR_TEXTURE;
                return resolvedFloorTexture;
            }
        } catch (Throwable ignored) {}
        resolvedFloorTexture = FALLBACK_FLOOR_TEXTURE;
        return resolvedFloorTexture;
    }

    public void update() {
        long now = System.nanoTime();
        float delta = (now - lastTimeNano) / 1_000_000_000.0F;
        lastTimeNano = now;
        animTime += delta;
        environmentRotation = (environmentRotation + delta * 0.8F) % 360.0F;
        ringRotation = (ringRotation + delta * 3.5F) % 360.0F;
    }

    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderEngine == null) return;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthMask(false);

            // 1. Render rotating GTNH Deep-Space Cosmos & Starfield (Infinite Resolution)
            GLStateManager.glPushMatrix();
            GLStateManager.glRotatef(environmentRotation, 0.0F, 1.0F, 0.0F);
            renderGalacticNebula();
            renderStarfield();
            renderCelestialPlanet();
            GLStateManager.glPopMatrix();

            // 2. Render Distant Orbiting GregTech / Stargate Ring
            renderStargateRing();

            // 3. Render Crisp GregTech Steel Industrial Platform under player's feet
            GLStateManager.glDepthMask(true);
            renderFloorPlatform(mc);

        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }
    }

    private void renderStarfield() {
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // Additive luminous glow for stars
        GLStateManager.glEnable(GL11.GL_POINT_SMOOTH);

        for (int i = 0; i < NUM_STARS; i++) {
            float twinkle = 0.65F + 0.35F * (float) Math.sin(animTime * starTwinkleSpeed[i] + starPhase[i]);
            GLStateManager.glPointSize(starSizes[i]);
            GLStateManager.glColor4f(
                    starColors[i * 3],
                    starColors[i * 3 + 1],
                    starColors[i * 3 + 2],
                    twinkle
            );
            GLStateManager.glBegin(GL11.GL_POINTS);
            GLStateManager.glVertex3f(
                    starPositions[i * 3],
                    starPositions[i * 3 + 1],
                    starPositions[i * 3 + 2]
            );
            GLStateManager.glEnd();
        }

        GLStateManager.glDisable(GL11.GL_POINT_SMOOTH);
    }

    private void renderGalacticNebula() {
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        int segments = 40;
        float radius = 70.0F;
        float bandHalfHeight = 16.0F;

        // Primary celestial cyan/blue galactic dust plane
        GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = (i % segments) * (Math.PI * 2.0 / segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float t = (float) (0.5 + 0.5 * Math.sin(angle * 2.0));
            float r = 0.05F + 0.12F * t;
            float g = 0.18F + 0.10F * (1.0F - t);
            float b = 0.38F + 0.20F * t;
            float alpha = 0.18F + 0.08F * (float) Math.cos(angle * 3.0);

            GLStateManager.glColor4f(r, g, b, 0.0F);
            GLStateManager.glVertex3f(cos * radius, bandHalfHeight, sin * radius);

            GLStateManager.glColor4f(r * 1.4F, g * 1.3F, b * 1.5F, alpha);
            GLStateManager.glVertex3f(cos * radius, 0.0F, sin * radius);
        }
        GLStateManager.glEnd();

        GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = (i % segments) * (Math.PI * 2.0 / segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float t = (float) (0.5 + 0.5 * Math.sin(angle * 2.0));
            float r = 0.05F + 0.12F * t;
            float g = 0.18F + 0.10F * (1.0F - t);
            float b = 0.38F + 0.20F * t;
            float alpha = 0.18F + 0.08F * (float) Math.cos(angle * 3.0);

            GLStateManager.glColor4f(r * 1.4F, g * 1.3F, b * 1.5F, alpha);
            GLStateManager.glVertex3f(cos * radius, 0.0F, sin * radius);

            GLStateManager.glColor4f(r, g, b, 0.0F);
            GLStateManager.glVertex3f(cos * radius, -bandHalfHeight, sin * radius);
        }
        GLStateManager.glEnd();

        // Secondary ethereal violet/magenta celestial dust ribbon
        GLStateManager.glPushMatrix();
        GLStateManager.glRotatef(35.0F, 1.0F, 0.0F, 0.5F);
        GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = (i % segments) * (Math.PI * 2.0 / segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float alpha = 0.12F + 0.06F * (float) Math.sin(angle * 4.0);

            GLStateManager.glColor4f(0.25F, 0.08F, 0.35F, 0.0F);
            GLStateManager.glVertex3f(cos * radius, 10.0F, sin * radius);

            GLStateManager.glColor4f(0.35F, 0.10F, 0.45F, alpha);
            GLStateManager.glVertex3f(cos * radius, 0.0F, sin * radius);
        }
        GLStateManager.glEnd();
        GLStateManager.glPopMatrix();
    }

    /**
     * Renders a majestic distant Ringed Gas Giant on the celestial horizon.
     */
    private void renderCelestialPlanet() {
        GLStateManager.glPushMatrix();
        // Positioned high on the horizon beyond the stargate
        GLStateManager.glTranslatef(32.0F, 22.0F, -54.0F);
        GLStateManager.glRotatef(-25.0F, 0.0F, 0.0F, 1.0F);
        GLStateManager.glRotatef(15.0F, 1.0F, 0.0F, 0.0F);

        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float planetRadius = 8.5F;
        int rings = 16;
        int sectors = 32;

        // 1. Planet sphere body with atmospheric shading
        for (int i = 0; i < rings; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / rings);
            float z0  = (float) Math.sin(lat0) * planetRadius;
            float zr0 = (float) Math.cos(lat0) * planetRadius;

            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / rings);
            float z1  = (float) Math.sin(lat1) * planetRadius;
            float zr1 = (float) Math.cos(lat1) * planetRadius;

            GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= sectors; j++) {
                double lng = 2.0 * Math.PI * (double) j / sectors;
                float x = (float) Math.cos(lng);
                float y = (float) Math.sin(lng);

                // Lighting calculation: light source from upper-left
                float light = 0.55F + 0.45F * (x * 0.7F + z0 / planetRadius * 0.5F);
                light = Math.max(0.12F, Math.min(1.0F, light));

                // Sapphire to cyan gas giant band color
                float band = (float) (0.5 + 0.5 * Math.sin(lat0 * 6.0));
                float pr = (0.08F + 0.08F * band) * light;
                float pg = (0.22F + 0.20F * band) * light;
                float pb = (0.48F + 0.35F * band) * light;

                GLStateManager.glColor4f(pr, pg, pb, 0.98F);
                GLStateManager.glVertex3f(x * zr0, z0, y * zr0);

                GLStateManager.glColor4f(pr * 1.05F, pg * 1.05F, pb * 1.05F, 0.98F);
                GLStateManager.glVertex3f(x * zr1, z1, y * zr1);
            }
            GLStateManager.glEnd();
        }

        // 2. Glowing atmospheric limb ring
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GLStateManager.glLineWidth(3.0F);
        GLStateManager.glColor4f(0.1F, 0.8F, 1.0F, 0.65F);
        GLStateManager.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 48; i++) {
            double a = i * (Math.PI * 2.0 / 48.0);
            GLStateManager.glVertex3f((float) Math.cos(a) * (planetRadius + 0.05F), (float) Math.sin(a) * (planetRadius + 0.05F), 0.0F);
        }
        GLStateManager.glEnd();

        // 3. Translucent icy planetary ring system
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float ringInner = 12.0F;
        float ringOuter = 19.5F;
        int ringSegments = 48;

        GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= ringSegments; i++) {
            double a = (i % ringSegments) * (Math.PI * 2.0 / ringSegments);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float ringAlpha = 0.40F + 0.20F * (float) Math.sin(a * 2.0);

            GLStateManager.glColor4f(0.45F, 0.75F, 0.95F, ringAlpha);
            GLStateManager.glVertex3f(cos * ringOuter, 0.0F, sin * ringOuter);

            GLStateManager.glColor4f(0.25F, 0.55F, 0.85F, ringAlpha * 0.4F);
            GLStateManager.glVertex3f(cos * ringInner, 0.0F, sin * ringInner);
        }
        GLStateManager.glEnd();

        GLStateManager.glPopMatrix();
    }

    private void renderStargateRing() {
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(0.0F, 10.0F, -48.0F);
        GLStateManager.glRotatef(18.0F, 1.0F, 0.0F, 0.0F);
        GLStateManager.glRotatef(ringRotation, 0.0F, 0.0F, 1.0F);

        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int segments = 48;
        float outerR = 18.0F;
        float innerR = 15.5F;

        // Dark titanium structural ring
        GLStateManager.glColor4f(0.12F, 0.14F, 0.18F, 0.95F);
        GLStateManager.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double a = (i % segments) * (Math.PI * 2.0 / segments);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            GLStateManager.glVertex3f(cos * outerR, sin * outerR, 0.0F);
            GLStateManager.glVertex3f(cos * innerR, sin * innerR, 0.0F);
        }
        GLStateManager.glEnd();

        // Glowing cyan inner energy conduit
        float glowPulse = 0.75F + 0.25F * (float) Math.sin(animTime * 3.0);
        GLStateManager.glLineWidth(3.0F);
        GLStateManager.glColor4f(0.0F, 0.85F, 1.0F, glowPulse);
        GLStateManager.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double a = i * (Math.PI * 2.0 / segments);
            GLStateManager.glVertex3f((float) Math.cos(a) * innerR, (float) Math.sin(a) * innerR, 0.05F);
        }
        GLStateManager.glEnd();

        // 8 Stargate chevrons with high-tech glowing glyphs
        GLStateManager.glColor4f(0.0F, 0.95F, 1.0F, 0.90F);
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI * 2.0 / 8.0);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float midR = (innerR + outerR) * 0.5F;
            float cx = cos * midR;
            float cy = sin * midR;
            float s = 0.75F;

            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glVertex3f(cx - s, cy - s, 0.08F);
            GLStateManager.glVertex3f(cx + s, cy - s, 0.08F);
            GLStateManager.glVertex3f(cx + s, cy + s, 0.08F);
            GLStateManager.glVertex3f(cx - s, cy + s, 0.08F);
            GLStateManager.glEnd();
        }

        GLStateManager.glPopMatrix();
    }

    private void renderFloorPlatform(Minecraft mc) {
        ResourceLocation texture = getFloorTexture(mc);
        mc.renderEngine.bindTexture(texture);

        // GL_NEAREST eliminates blur / soapy look for authentic crisp GregTech pixel art!
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glColor4f(0.88F, 0.90F, 0.94F, 1.0F);

        float halfW = 2.0F;
        float halfL = 2.0F;
        float repeat = 8.0F;
        float floorY = com.gtnhvr.OpenXRRuntime.isStageSpace() ? 0.0F : -1.62F; // Real room floor in STAGE, feet offset in LOCAL
        float edgeY = floorY - 0.22F; // Slanted bevel depth

        // 1. Top face of GTNH platform (standing deck)
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glTexCoord2f(0.0F, 0.0F);     GLStateManager.glVertex3f(-halfW, floorY, -halfL);
        GLStateManager.glTexCoord2f(0.0F, repeat);    GLStateManager.glVertex3f(-halfW, floorY,  halfL);
        GLStateManager.glTexCoord2f(repeat, repeat); GLStateManager.glVertex3f( halfW, floorY,  halfL);
        GLStateManager.glTexCoord2f(repeat, 0.0F);   GLStateManager.glVertex3f( halfW, floorY, -halfL);
        GLStateManager.glEnd();

        // 2. Beveled industrial skirts
        GLStateManager.glColor4f(0.48F, 0.52F, 0.58F, 1.0F);
        GLStateManager.glBegin(GL11.GL_QUADS);
        // North
        GLStateManager.glTexCoord2f(0.0F, 0.0F);   GLStateManager.glVertex3f(-halfW, floorY, -halfL);
        GLStateManager.glTexCoord2f(repeat, 0.0F); GLStateManager.glVertex3f( halfW, floorY, -halfL);
        GLStateManager.glTexCoord2f(repeat, 0.5F); GLStateManager.glVertex3f( halfW, edgeY,  -halfL);
        GLStateManager.glTexCoord2f(0.0F, 0.5F);   GLStateManager.glVertex3f(-halfW, edgeY,  -halfL);
        // South
        GLStateManager.glTexCoord2f(0.0F, 0.0F);   GLStateManager.glVertex3f( halfW, floorY,  halfL);
        GLStateManager.glTexCoord2f(repeat, 0.0F); GLStateManager.glVertex3f(-halfW, floorY,  halfL);
        GLStateManager.glTexCoord2f(repeat, 0.5F); GLStateManager.glVertex3f(-halfW, edgeY,   halfL);
        GLStateManager.glTexCoord2f(0.0F, 0.5F);   GLStateManager.glVertex3f( halfW, edgeY,   halfL);
        // East
        GLStateManager.glTexCoord2f(0.0F, 0.0F);   GLStateManager.glVertex3f( halfW, floorY, -halfL);
        GLStateManager.glTexCoord2f(repeat, 0.0F); GLStateManager.glVertex3f( halfW, floorY,  halfL);
        GLStateManager.glTexCoord2f(repeat, 0.5F); GLStateManager.glVertex3f( halfW, edgeY,   halfL);
        GLStateManager.glTexCoord2f(0.0F, 0.5F);   GLStateManager.glVertex3f( halfW, edgeY,  -halfL);
        // West
        GLStateManager.glTexCoord2f(0.0F, 0.0F);   GLStateManager.glVertex3f(-halfW, floorY,  halfL);
        GLStateManager.glTexCoord2f(repeat, 0.0F); GLStateManager.glVertex3f(-halfW, floorY, -halfL);
        GLStateManager.glTexCoord2f(repeat, 0.5F); GLStateManager.glVertex3f(-halfW, edgeY,  -halfL);
        GLStateManager.glTexCoord2f(0.0F, 0.5F);   GLStateManager.glVertex3f(-halfW, edgeY,   halfL);
        GLStateManager.glEnd();

        // 3. High-Tech Glowing Cyan Perimeter Loop
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glLineWidth(3.5F);
        float pulse = 0.80F + 0.20F * (float) Math.sin(animTime * 2.5);
        GLStateManager.glColor4f(0.0F, 0.90F, 1.0F, pulse);

        GLStateManager.glBegin(GL11.GL_LINE_LOOP);
        GLStateManager.glVertex3f(-halfW, floorY + 0.003F, -halfL);
        GLStateManager.glVertex3f(-halfW, floorY + 0.003F,  halfL);
        GLStateManager.glVertex3f( halfW, floorY + 0.003F,  halfL);
        GLStateManager.glVertex3f( halfW, floorY + 0.003F, -halfL);
        GLStateManager.glEnd();

        // 4. Circular VR Foot Alignment Guide
        float footRadius = 0.65F;
        GLStateManager.glLineWidth(2.2F);
        GLStateManager.glColor4f(0.0F, 0.80F, 1.0F, pulse * 0.85F);
        GLStateManager.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 32; i++) {
            double a = i * (Math.PI * 2.0 / 32.0);
            GLStateManager.glVertex3f((float) Math.cos(a) * footRadius, floorY + 0.003F, (float) Math.sin(a) * footRadius);
        }
        GLStateManager.glEnd();

        // 5. Corner Emitter Status Pods (Pulsing high-tech nodes)
        float podSize = 0.08F;
        float[][] corners = {
                {-halfW + 0.15F, -halfL + 0.15F},
                {-halfW + 0.15F,  halfL - 0.15F},
                { halfW - 0.15F,  halfL - 0.15F},
                { halfW - 0.15F, -halfL + 0.15F}
        };

        GLStateManager.glColor4f(0.2F, 0.95F, 1.0F, 0.95F);
        for (float[] c : corners) {
            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glVertex3f(c[0] - podSize, floorY + 0.004F, c[1] - podSize);
            GLStateManager.glVertex3f(c[0] + podSize, floorY + 0.004F, c[1] - podSize);
            GLStateManager.glVertex3f(c[0] + podSize, floorY + 0.004F, c[1] + podSize);
            GLStateManager.glVertex3f(c[0] - podSize, floorY + 0.004F, c[1] + podSize);
            GLStateManager.glEnd();
        }

        // 6. Glowing Energy Circuit Traces connecting Corner Pods to Center Guide
        GLStateManager.glLineWidth(2.0F);
        GLStateManager.glColor4f(0.0F, 0.85F, 1.0F, pulse * 0.70F);
        GLStateManager.glBegin(GL11.GL_LINES);
        for (float[] c : corners) {
            float dist = (float) Math.sqrt(c[0] * c[0] + c[1] * c[1]);
            float nx = c[0] / dist;
            float nz = c[1] / dist;
            // Radial trace from corner pod edge to center foot guide
            GLStateManager.glVertex3f(c[0] - nx * podSize, floorY + 0.0035F, c[1] - nz * podSize);
            GLStateManager.glVertex3f(nx * footRadius, floorY + 0.0035F, nz * footRadius);
        }
        GLStateManager.glEnd();

        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glDisable(GL11.GL_BLEND);
    }
}
