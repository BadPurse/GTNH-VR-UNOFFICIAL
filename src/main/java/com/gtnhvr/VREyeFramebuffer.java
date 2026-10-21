package com.gtnhvr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Lwjgl3Aware
public class VREyeFramebuffer extends Framebuffer implements IRenderTargetExt {
    private final int eyeIndex;
    public int irisDepthTextureId = -1;
    public int irisDepthVersion;
    public int irisColorVersion = 1;

    public VREyeFramebuffer(int eyeIndex, int width, int height) {
        super(width, height, true);
        this.eyeIndex = eyeIndex;
        this.irisDepthVersion = eyeIndex + 1; // Distinct versions per eye (Eye 0 = 1, Eye 1 = 2)
        this.framebufferWidth = width;
        this.framebufferHeight = height;
        this.framebufferTextureWidth = width;
        this.framebufferTextureHeight = height;
    }

    public void updateFromSwapchain(int fboId, int textureId, int depthRbId, int depthTexId, int w, int h) {
        if (this.framebufferTexture != textureId) {
            this.irisColorVersion++;
        }
        if (this.irisDepthTextureId != depthTexId) {
            this.irisDepthTextureId = depthTexId;
            this.irisDepthVersion += 2;
        }
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
        return irisDepthVersion;
    }

    @Override
    public int iris$getColorBufferVersion() {
        return irisColorVersion;
    }

    @Override
    public int iris$getDepthTextureId() {
        return irisDepthTextureId;
    }

    @Override
    public void createBindFramebuffer(int w, int h) {
        // No-op: OpenXR owns swapchain allocation
    }

    @Override
    public void deleteFramebuffer() {
        // No-op: OpenXR lifecycle manages the textures & FBOs
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
        // Keep current eye swapchain FBO bound during VR eye pass
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

    public int getEyeIndex() {
        return eyeIndex;
    }
}
