package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.VRGuiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(method = {"updateCameraAndRender", "func_78480_b"}, at = @At("HEAD"))
    private void gtnhvr$noPauseOnLostFocusInVR(float partialTicks, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.gameSettings != null && mc.gameSettings.pauseOnLostFocus) {
                mc.gameSettings.pauseOnLostFocus = false;
            }
        }
    }

    @Inject(method = {"setupCameraTransform", "func_78479_a"}, at = @At("TAIL"))
    private void gtnhvr$applyVREyeCamera(float partialTicks, int pass, CallbackInfo ci) {
        if (!OpenXRRuntime.isVRStereoActive()) return;

        int eyeIndex = OpenXRRuntime.getCurrentEyeIndex();

        Minecraft mc = Minecraft.getMinecraft();
        float farPlane = (mc != null && mc.gameSettings != null)
                ? Math.max(128.0F, mc.gameSettings.renderDistanceChunks * 32.0F)
                : 256.0F;

        // Set eye swapchain viewport to native headset resolution
        GL11.glViewport(0, 0, OpenXRRuntime.getEyeWidth(eyeIndex), OpenXRRuntime.getEyeHeight(eyeIndex));

        // 1. Asymmetric OpenXR optical FOV projection matrix
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        FloatBuffer proj = OpenXRRuntime.getEyeProjectionBuffer(eyeIndex, 0.05F, farPlane);
        if (proj != null) {
            GL11.glLoadMatrixf(proj);
        }

        // 2. 6-DoF OpenXR eye orientation and IPD translation
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        FloatBuffer modelView = OpenXRRuntime.getEyeModelViewBuffer(eyeIndex, partialTicks);
        if (modelView != null) {
            GL11.glLoadMatrixf(modelView);
        }

        // 3. Update ActiveRenderInfo so Angelica and vanilla frustum culling use eye matrices
        if (mc != null && mc.thePlayer != null) {
            net.minecraft.client.renderer.ActiveRenderInfo.updateRenderInfo(mc.thePlayer, false);
        }

        // 4. Update Angelica's RenderingState directly with eye matrices
        try {
            if (proj != null) com.gtnewhorizons.angelica.rendering.RenderingState.INSTANCE.setProjectionMatrix(proj);
            if (modelView != null) com.gtnewhorizons.angelica.rendering.RenderingState.INSTANCE.setModelViewMatrix(modelView);
        } catch (Throwable ignored) {}
    }

    @Inject(method = {"renderWorld", "func_78471_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$renderVRStereo(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        if (OpenXRRuntime.isRenderingEyePass()) return;

        // Cancel the flat 2D single pass
        ci.cancel();

        // Run authentic dual-pass stereoscopic world render for Left and Right eyes
        OpenXRRuntime.renderVRStereoWorld((EntityRenderer) (Object) this, partialTicks, finishTimeNano);
    }

    /**
     * Captures 2D main menu into VRGuiRenderer FBO for floating VR display.
     * In-game GUIs (Esc menu, inventories, chests) are captured cleanly via
     * GuiEventHandler (DrawScreenEvent.Pre / Post) so renderWorld is never wrapped!
     */
    @Inject(method = {"updateCameraAndRender", "func_78480_b"}, at = @At("HEAD"))
    private void gtnhvr$onUpdateCameraAndRenderHead(float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.theWorld == null) {
            boolean isMainMenu = mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu;
            VRGuiRenderer.begin(VRGuiRenderer.getCaptureWidth(), VRGuiRenderer.getCaptureHeight(), isMainMenu);
        }
    }

    @Inject(method = {"updateCameraAndRender", "func_78480_b"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V"),
            require = 0)
    private void gtnhvr$beginInGameGuiCapture(float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning() && mc != null && mc.currentScreen != null && mc.theWorld != null) {
            VRGuiRenderer.begin(mc.displayWidth, mc.displayHeight, false);
        }
    }


    // The capture is NOT closed right after drawScreen(): Forge posts DrawScreenEvent.Post afterwards
    // and NEI (item panel, bookmarks, tooltips) draws from that event. The capture ends at TAIL.

    @Inject(method = {"updateCameraAndRender", "func_78480_b"}, at = @At("TAIL"))
    private void gtnhvr$onUpdateCameraAndRenderTail(float partialTicks, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning() && VRGuiRenderer.isCapturing()) {
            VRGuiRenderer.end();
        }
    }


    @Inject(method = {"renderHand", "func_78476_b"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$cancelFlatHand(float partialTicks, int pass, CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            ci.cancel();
        }
    }
    /**
     * Redirects Minecraft 1.7.10 raycasting (mining, block placing, mob attacking, reach)
     * from headset view to the 6-DoF right controller hand aim ray (1:1 Visor VRAimPicker).
     */
    @Inject(method = {"getMouseOver", "func_78473_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$onGetMouseOverHead(float partialTicks, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderViewEntity == null || mc.theWorld == null) return;
        com.gtnhvr.VRControllerHandler handler = com.gtnhvr.VRControllerHandler.getInstance();
        if (!handler.isRightHandTracked()) return;

        com.gtnhvr.VRHandAimPicker.update(mc, partialTicks);
        ci.cancel();
    }
}
