package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Dedicated Minecraft Framebuffer wrapper for VRGuiRenderer.
 * Prevents mods like NEI and BetterQuesting (which call Minecraft.getMinecraft().getFramebuffer().bindFramebuffer())
 * from leaking GUI/tooltips into the desktop window framebuffer or unbinding the VR capture buffer.
 */
@Lwjgl3Aware
public class VRGuiFramebuffer extends Framebuffer implements IRenderTargetExt {
    private int colorVersion = 1;
    private int depthVersion = 1;

    public VRGuiFramebuffer(int width, int height) {
        super(width, height, true);
        this.framebufferWidth = width;
        this.framebufferHeight = height;
        this.framebufferTextureWidth = width;
        this.framebufferTextureHeight = height;
    }

    public void update(int fboId, int textureId, int depthRbId, int w, int h) {
        // update() is called only when the shared render-target manager allocates/reallocates
        // storage, not every frame. Increment versions even if the driver reuses the same GL ID.
        this.colorVersion++;
        this.depthVersion++;
        this.framebufferObject = fboId;
        this.framebufferTexture = textureId;
        this.depthBuffer = depthRbId;
        this.framebufferWidth = w;
        this.framebufferHeight = h;
        this.framebufferTextureWidth = w;
        this.framebufferTextureHeight = h;
    }

    @Override
    public int iris$getDepthBufferVersion() {
        return depthVersion;
    }

    @Override
    public int iris$getColorBufferVersion() {
        return colorVersion;
    }

    @Override
    public int iris$getDepthTextureId() {
        return -1;
    }

    @Override
    public void createBindFramebuffer(int w, int h) {
        // Lifecycle managed by VRGuiRenderer
    }

    @Override
    public void deleteFramebuffer() {
        // Lifecycle managed by VRGuiRenderer
    }

    @Override
    public void bindFramebuffer(boolean setViewport) {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferObject);
        if (setViewport) {
            GLStateManager.glViewport(0, 0, this.framebufferWidth, this.framebufferHeight);
        }
    }

    @Override
    public void unbindFramebuffer() {
        // Keep the GUI capture FBO bound during GUI capture pass
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferObject);
    }

    @Override
    public void bindFramebufferTexture() {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, this.framebufferTexture);
    }

    @Override
    public void unbindFramebufferTexture() {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
