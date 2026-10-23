package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    // Keep GUI initialization on the same logical 1920x1080 canvas used for VR capture.
    // Without this scope, the first captured frame calls setWorldAndResolution() from inside
    // drawScreen(), re-running complex AE2/NEI init code during rendering.
    private static int gtnhvr$screenResolutionScopeDepth = 0;
    private static int gtnhvr$savedScreenDisplayWidth = -1;
    private static int gtnhvr$savedScreenDisplayHeight = -1;

    @Inject(method = {"displayGuiScreen", "func_147108_a"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$beginVirtualGuiScreenInit(net.minecraft.client.gui.GuiScreen newScreen, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;

        Minecraft mc = (Minecraft) (Object) this;
        if (gtnhvr$screenResolutionScopeDepth++ == 0) {
            gtnhvr$savedScreenDisplayWidth = mc.displayWidth;
            gtnhvr$savedScreenDisplayHeight = mc.displayHeight;
        }

        // Nested screen changes also need the virtual dimensions while the new screen initializes.
        if (newScreen != null) {
            mc.displayWidth = com.gtnhvr.gui.VRGuiRenderer.getCaptureWidth();
            mc.displayHeight = com.gtnhvr.gui.VRGuiRenderer.getCaptureHeight();
        }
    }

    @Inject(method = {"displayGuiScreen", "func_147108_a"}, at = @At("RETURN"), require = 0)
    private void gtnhvr$endVirtualGuiScreenInit(net.minecraft.client.gui.GuiScreen newScreen, CallbackInfo ci) {
        if (gtnhvr$screenResolutionScopeDepth <= 0) return;
        if (--gtnhvr$screenResolutionScopeDepth != 0) return;

        Minecraft mc = (Minecraft) (Object) this;
        if (gtnhvr$savedScreenDisplayWidth > 0 && gtnhvr$savedScreenDisplayHeight > 0) {
            mc.displayWidth = gtnhvr$savedScreenDisplayWidth;
            mc.displayHeight = gtnhvr$savedScreenDisplayHeight;
        }
        gtnhvr$savedScreenDisplayWidth = -1;
        gtnhvr$savedScreenDisplayHeight = -1;
    }

    @Inject(method = {"setIngameFocus", "func_71381_h"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$onSetIngameFocus(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.gameSettings != null) {
                mc.gameSettings.pauseOnLostFocus = false;
            }
        }
    }

    @Inject(method = {"displayInGameMenu", "func_71385_j"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhvr$onDisplayInGameMenu(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            Minecraft mc = (Minecraft) (Object) this;
            // Never auto-pause while world is loading or player has just joined
            if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.ticksExisted < 40) {
                ci.cancel();
            }
        }
    }

    @Inject(method = {"runTick", "func_71407_l"}, at = @At("HEAD"), require = 0)
    private void gtnhvr$onRunTick(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning()) {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.gameSettings != null && mc.gameSettings.pauseOnLostFocus) {
                mc.gameSettings.pauseOnLostFocus = false;
            }
        }
    }

    @Inject(method = {"shutdownMinecraftApplet", "func_71405_e"}, at = @At("HEAD"))
    private void gtnhvr$onShutdown(CallbackInfo ci) {
        // Cleanly shutdown OpenXR swapchains, session, and instance while OpenGL context is still alive
        OpenXRRuntime.shutdown();
    }

    @Inject(method = {"shutdownMinecraftApplet", "func_71405_e"}, at = @At("TAIL"))
    private void gtnhvr$onShutdownExit(CallbackInfo ci) {
        // Ensure JVM exits cleanly and doesn't hang on OpenXR runtime driver background threads
        try {
            System.exit(0);
        } catch (Throwable ignored) {}
    }

    @Inject(method = {"getFramebuffer", "func_147110_a"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$getVREyeFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        if (OpenXRRuntime.isVRStereoActive()) {
            Framebuffer eyeFb = OpenXRRuntime.getCurrentEyeFramebuffer();
            if (eyeFb != null) {
                cir.setReturnValue(eyeFb);
            }
        }
    }

    @Inject(method = {"getLimitFramerate", "func_90020_K"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$uncapMainMenuFramerate(CallbackInfoReturnable<Integer> cir) {
        if (OpenXRRuntime.isSessionRunning()) {
            cir.setReturnValue(260);
        }
    }

    @Inject(method = {"isFramerateLimitBelowMax", "func_147107_h"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$noDisplaySyncInVR(CallbackInfoReturnable<Boolean> cir) {
        if (OpenXRRuntime.isSessionRunning()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = {"displayGuiScreen", "func_147108_a"}, at = @At("HEAD"))
    private void gtnhvr$onDisplayGuiScreen(net.minecraft.client.gui.GuiScreen newScreen, CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;
        if (!com.gtnhvr.settings.VRClientSettings.keyboardAutoLayout) return;

        if (newScreen == null) {
            com.gtnhvr.gui.VRVirtualKeyboard.getInstance().hideAutoKeyboard();
        } else if (newScreen instanceof net.minecraft.client.gui.GuiChat
                || newScreen instanceof net.minecraft.client.gui.inventory.GuiEditSign
                || newScreen instanceof net.minecraft.client.gui.GuiScreenBook
                || newScreen instanceof net.minecraft.client.gui.GuiRepair) {
            com.gtnhvr.gui.VRVirtualKeyboard.getInstance().showAutoKeyboard(newScreen);
        } else {
            com.gtnhvr.gui.VRVirtualKeyboard.getInstance().hideAutoKeyboard();
        }
    }

    private static int gtnhvr$savedCurrentItem = -1;

    @Inject(method = {"func_147121_ag", "rightClickMouse"}, at = @At("HEAD"))
    private void gtnhvr$beforeRightClickMouse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (!OpenXRRuntime.isSessionRunning() || mc.thePlayer == null || mc.thePlayer.inventory == null) return;
        com.gtnhvr.VRControllerHandler handler = com.gtnhvr.VRControllerHandler.getInstance();
        if (handler != null && handler.isLeftHandActive()) {
            int offhandSlot = com.gtnhvr.gui.VRHotbarSelector.getInstance().getOffhandSlot();
            if (offhandSlot >= 0 && offhandSlot < 9) {
                gtnhvr$savedCurrentItem = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = offhandSlot;
            }
        }
    }

    @Inject(method = {"func_147121_ag", "rightClickMouse"}, at = @At("RETURN"))
    private void gtnhvr$afterRightClickMouse(CallbackInfo ci) {
        if (gtnhvr$savedCurrentItem != -1) {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.thePlayer != null && mc.thePlayer.inventory != null) {
                mc.thePlayer.inventory.currentItem = gtnhvr$savedCurrentItem;
                if (mc.getNetHandler() != null) {
                    mc.getNetHandler().addToSendQueue(
                            new net.minecraft.network.play.client.C09PacketHeldItemChange(gtnhvr$savedCurrentItem));
                }
            }
            gtnhvr$savedCurrentItem = -1;
        }
    }
}
