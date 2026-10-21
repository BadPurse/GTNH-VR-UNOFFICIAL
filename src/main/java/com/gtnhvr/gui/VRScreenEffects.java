package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Red edge vignette drawn into the VR HUD texture: a flash when the player is hit and a
 * pulsing warning at low health (Visor hit indicator and low health indicator settings).
 */
@Lwjgl3Aware
public final class VRScreenEffects {
    private static final float LOW_HEALTH = 6.0F;

    private VRScreenEffects() {}

    public static void render(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || width <= 0 || height <= 0) return;

        float alpha = 0.0F;
        if (VRClientSettings.hitIndicatorEnabled && mc.thePlayer.hurtTime > 0 && mc.thePlayer.maxHurtTime > 0) {
            alpha = Math.max(alpha, 0.55F * mc.thePlayer.hurtTime / (float) mc.thePlayer.maxHurtTime);
        }
        float health = mc.thePlayer.getHealth();
        if (VRClientSettings.lowHealthIndicatorEnabled && health > 0.0F && health <= LOW_HEALTH) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() * 0.006D);
            alpha = Math.max(alpha, 0.18F + 0.22F * pulse);
        }
        if (alpha <= 0.01F) return;

        float w = width;
        float h = height;
        float t = 0.22F * h;
        float r = 0.85F;
        float g = 0.05F;
        float b = 0.05F;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glBegin(GL11.GL_QUADS);
            // top
            GLStateManager.glColor4f(r, g, b, alpha); GLStateManager.glVertex2f(0.0F, 0.0F); GLStateManager.glVertex2f(w, 0.0F);
            GLStateManager.glColor4f(r, g, b, 0.0F); GLStateManager.glVertex2f(w, t); GLStateManager.glVertex2f(0.0F, t);
            // bottom
            GLStateManager.glColor4f(r, g, b, 0.0F); GLStateManager.glVertex2f(0.0F, h - t); GLStateManager.glVertex2f(w, h - t);
            GLStateManager.glColor4f(r, g, b, alpha); GLStateManager.glVertex2f(w, h); GLStateManager.glVertex2f(0.0F, h);
            // left
            GLStateManager.glColor4f(r, g, b, alpha); GLStateManager.glVertex2f(0.0F, 0.0F); GLStateManager.glVertex2f(0.0F, h);
            GLStateManager.glColor4f(r, g, b, 0.0F); GLStateManager.glVertex2f(t, h); GLStateManager.glVertex2f(t, 0.0F);
            // right
            GLStateManager.glColor4f(r, g, b, 0.0F); GLStateManager.glVertex2f(w - t, 0.0F); GLStateManager.glVertex2f(w - t, h);
            GLStateManager.glColor4f(r, g, b, alpha); GLStateManager.glVertex2f(w, h); GLStateManager.glVertex2f(w, 0.0F);
            GLStateManager.glEnd();
        } finally {
            GLStateManager.glPopAttrib();
        }
    }
}
