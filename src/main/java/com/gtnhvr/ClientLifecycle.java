package com.gtnhvr;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent;
import cpw.mods.fml.relauncher.Side;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;

/**
 * Client-only lifecycle hook.
 * Initializes OpenXR as soon as Minecraft's OpenGL/SDL window is active
 * and handles per-frame VR rendering loop.
 */
@Lwjgl3Aware
public final class ClientLifecycle {
    private static final ClientLifecycle INSTANCE = new ClientLifecycle();
    private static boolean registered;
    private static long lastAttemptNanos = 0;

    private ClientLifecycle() {}

    public static void register() {
        if (registered) return;
        registered = true;
        FMLCommonHandler.instance().bus().register(INSTANCE);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    OpenXRRuntime.shutdown();
                } catch (Throwable ignored) {}
            }, "GTNHVR-ShutdownHook"));
        } catch (Throwable ignored) {}
        GTNHVRLog.info("Client lifecycle registered on FML bus");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.side != Side.CLIENT) return;

        if (event.phase == TickEvent.Phase.START) {
            if (OpenXRRuntime.isSessionRunning()) {
                HeadPoseController.apply();
            }
            return;
        }

        if (event.phase != TickEvent.Phase.END) return;

        if (com.gtnhvr.settings.VRClientSettings.vrPlayMode == com.gtnhvr.settings.VRPlayMode.DISABLED) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenXRRuntime.isSessionRunning()) {
            HeadPoseController.apply();
            tickBodyHaptics(mc);
            VRHeightCalibrator.tick(mc);
            com.gtnhvr.api.VRAddonRegistry.dispatchClientTick(mc);
            return;
        }

        long now = System.nanoTime();
        if (now - lastAttemptNanos < 3_000_000_000L) { // retry every 3s
            return;
        }
        lastAttemptNanos = now;

        if (mc == null) return;

        // DO NOT initialize OpenXR during early Forge loading screen or before the game reaches the main menu!
        // This ensures the game window and loading screen never hang, freeze, or show "Not responding".
        if (cpw.mods.fml.client.FMLClientHandler.instance().isLoading()) return;
        if (mc.currentScreen == null && mc.theWorld == null) return;

        GTNHVRLog.info("Checking OpenXR runtime and headset status...");
        if (OpenXRRuntime.initialize()) {
            GTNHVRLog.info("OpenXR initialized successfully! VR mode ACTIVE.");
            GTNHVRLog.chat("OpenXR session started! VR mode activated.");
        } else {
            GTNHVRLog.info("OpenXR runtime not available or headset not ready (will retry).");
        }
    }

    private int lastHurtTime = 0;

    /** Pulses both controllers when the player takes damage (bodyHaptics setting). */
    private void tickBodyHaptics(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) {
            lastHurtTime = 0;
            return;
        }
        int hurt = mc.thePlayer.hurtTime;
        boolean enabled = com.gtnhvr.settings.VRClientSettings.bodyHapticsEnabled
                || com.gtnhvr.settings.VRClientSettings.bodyHaptics;
        if (enabled && hurt > 0 && hurt > lastHurtTime) {
            float amplitude = Math.max(0.2F, Math.min(1.0F, com.gtnhvr.settings.VRClientSettings.hapticStrength * 1.6F));
            VRControllerHandler handler = VRControllerHandler.getInstance();
            handler.triggerHaptic(true, 180_000_000L, amplitude, 0);
            handler.triggerHaptic(false, 180_000_000L, amplitude, 0);
        }
        lastHurtTime = hurt;
    }

    @SubscribeEvent
    public void onRenderStart(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        com.gtnhvr.gui.VRGuiRenderer.newFrame();
        OpenXRRuntime.beginFrame();
        HeadPoseController.apply();
    }

    @SubscribeEvent
    public void onRenderEnd(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (com.gtnhvr.gui.VRGuiRenderer.isCapturing()) {
            com.gtnhvr.gui.VRGuiRenderer.end();
        }
        OpenXRRuntime.endFrame();
    }
}
