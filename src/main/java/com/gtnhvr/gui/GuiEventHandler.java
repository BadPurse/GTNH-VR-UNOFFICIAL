package com.gtnhvr.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;

@Lwjgl3Aware
public final class GuiEventHandler {
    private static final GuiEventHandler INSTANCE = new GuiEventHandler();
    private static boolean registered;
    private static boolean vrSessionWasRunning;

    public static final int BUTTON_ID_OPTIONS = 99901;
    public static final int BUTTON_ID_INGAME = 99902;
    public static final int BUTTON_ID_ANGELICA = 99903;

    private GuiEventHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        // Forge GUI events and FML tick events are posted on different buses in 1.7.10.
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    private static boolean bypassVanillaRedirect = false;
    /** The exact pause-screen instance explicitly requested from Game Menu. */
    private static GuiScreen bypassVanillaRedirectTarget = null;

    public static void setBypassVanillaRedirect(boolean bypass) {
        bypassVanillaRedirect = bypass;
        bypassVanillaRedirectTarget = null;
    }

    /** Allow only this specific GuiIngameMenu instance through the VR pause-screen redirect. */
    public static void setBypassVanillaRedirect(GuiScreen target) {
        bypassVanillaRedirectTarget = target;
        bypassVanillaRedirect = target != null;
    }

    @SubscribeEvent
    public void onClientTick(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;

        boolean sessionRunning = com.gtnhvr.OpenXRRuntime.isSessionRunning();
        if (!vrSessionWasRunning && sessionRunning) {
            // If a desktop menu was already open when VR started, normalize that one screen now.
            // This must happen during the tick, never from DrawScreenEvent.Pre.
            VRGuiRenderer.ensureLogicalGuiScreen(Minecraft.getMinecraft());
        } else if (vrSessionWasRunning && !sessionRunning) {
            // A session can end while a screen remains open. Close any owned render scope and
            // restore that screen once, using the physical window resolution again.
            VRGuiRenderer.end();
            VRHudRenderer.end();
            VRGuiRenderer.restoreDesktopGuiResolution(Minecraft.getMinecraft());
        }
        vrSessionWasRunning = sessionRunning;
    }

    @SubscribeEvent
    public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent event) {
        // Keep the pause-menu bypass scoped to the exact requested screen instance.
        if (bypassVanillaRedirectTarget != null && event.gui != bypassVanillaRedirectTarget) {
            bypassVanillaRedirectTarget = null;
            bypassVanillaRedirect = false;
        }

        if (!com.gtnhvr.OpenXRRuntime.isSessionRunning()) return;

        VRVirtualScreen.getInstance().resetOrientation();

        VRVirtualKeyboard keyboard = VRVirtualKeyboard.getInstance();
        keyboard.onGuiOpen(event.gui);
        boolean textEntryScreen = event.gui instanceof net.minecraft.client.gui.GuiChat
                || event.gui instanceof net.minecraft.client.gui.inventory.GuiEditSign
                || event.gui instanceof net.minecraft.client.gui.GuiScreenBook
                || event.gui instanceof net.minecraft.client.gui.GuiRepair;

        if (event.gui == null) {
            // Closing the GUI also closes a manually opened keyboard.
            keyboard.setVisible(false);
        } else if (textEntryScreen && com.gtnhvr.settings.VRClientSettings.keyboardAutoLayout) {
            // Mark this as auto-opened so it closes again when leaving text entry.
            keyboard.showAutoKeyboard(event.gui);
        } else {
            // Only hides an auto-opened keyboard; a manually toggled keyboard remains user-controlled.
            keyboard.hideAutoKeyboard();
        }

    }

    @SubscribeEvent
    public void onInitGuiPre(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (event.gui instanceof GuiIngameMenu && com.gtnhvr.OpenXRRuntime.isSessionRunning()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.ticksExisted < 40) {
                if (event.gui == bypassVanillaRedirectTarget) {
                    bypassVanillaRedirectTarget = null;
                    bypassVanillaRedirect = false;
                }
                event.setCanceled(true);
                return;
            }

            // Keep a targeted bypass alive if the same screen is initialized again. Virtual
            // dimensions are now supplied before initGui; the renderer no longer resizes it per frame.
            if (bypassVanillaRedirectTarget != null && event.gui == bypassVanillaRedirectTarget) {
                return;
            }

            // Legacy untargeted bypass remains a one-shot compatibility path.
            if (bypassVanillaRedirect && bypassVanillaRedirectTarget == null) {
                bypassVanillaRedirect = false;
                return;
            }

            bypassVanillaRedirectTarget = null;
            bypassVanillaRedirect = false;
            event.setCanceled(true);
            mc.displayGuiScreen(new VRPauseMenuScreen());
            return;
        }

        // A target survives unrelated screen initialization, but a legacy untargeted bypass
        // is still one-shot and must not unexpectedly disable the redirect later.
        if (bypassVanillaRedirectTarget == null) {
            bypassVanillaRedirect = false;
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        if (com.gtnhvr.OpenXRRuntime.isSessionRunning() && com.gtnhvr.settings.VRClientSettings.keyboardAutoLayout && gui != null) {
            if (GuiHelper.hasAnyFocusedTextField(gui)) {
                VRVirtualKeyboard.getInstance().showAutoKeyboard(gui);
            } else if (!(gui instanceof net.minecraft.client.gui.GuiChat)
                    && !(gui instanceof net.minecraft.client.gui.inventory.GuiEditSign)
                    && !(gui instanceof net.minecraft.client.gui.GuiScreenBook)
                    && !(gui instanceof net.minecraft.client.gui.GuiRepair)) {
                // Some mod screens reuse themselves and fire InitGui without a GuiOpen event.
                VRVirtualKeyboard.getInstance().hideAutoKeyboard();
            }
        }
        List<Object> buttons = event.buttonList;
        if (gui == null || buttons == null) return;

        String className = gui.getClass().getName();
        if (gui instanceof net.minecraft.client.gui.GuiMainMenu || className.contains("Custom") || className.contains("custommainmenu")) {
            String text = net.minecraft.client.resources.I18n.format("visor.options.main.button");
            buttons.add(new GuiButton(BUTTON_ID_OPTIONS, gui.width / 2 + 104, gui.height / 4 + 48 + 72 + 12, 80, 20, text));
        } else if (gui instanceof GuiOptions) {
            String text = net.minecraft.client.resources.I18n.format("visor.options.main.button");
            GuiButton fovBtn = null;
            for (Object obj : buttons) {
                if (obj instanceof GuiButton b) {
                    if (b.id == 201 || (b.displayString != null && (b.displayString.toLowerCase().contains("fov") || b.displayString.toLowerCase().contains("поле зрения")))) {
                        fovBtn = b;
                        break;
                    }
                }
            }
            int btnX = (fovBtn != null) ? fovBtn.xPosition : (gui.width / 2 - 155);
            int btnY = (fovBtn != null) ? (fovBtn.yPosition + fovBtn.height + 4) : (gui.height / 6 + 16);
            int btnW = (fovBtn != null) ? fovBtn.width : 150;
            buttons.add(new GuiButton(BUTTON_ID_OPTIONS, btnX, btnY, btnW, 20, text));
        } else if (gui instanceof GuiIngameMenu) {
            int maxBottom = 0;
            for (Object obj : buttons) {
                if (obj instanceof GuiButton b) {
                    maxBottom = Math.max(maxBottom, b.yPosition + b.height);
                }
            }
            int btnY = maxBottom > 0 ? maxBottom + 4 : gui.height / 4 + 130;
            String text = net.minecraft.client.resources.I18n.format("visor.options.main.button");
            buttons.add(new GuiButton(BUTTON_ID_INGAME, gui.width / 2 - 100, btnY, 200, 20, text));
        } else if (gui.getClass().getSimpleName().contains("ReeseSodiumVideoOptionsScreen")) {
            String text = net.minecraft.client.resources.I18n.format("visor.options.main");
            buttons.add(new GuiButton(BUTTON_ID_ANGELICA, gui.width - 110, 6, 100, 18, text));
        }

    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.button == null) return;
        int id = event.button.id;
        if (id == BUTTON_ID_OPTIONS || id == BUTTON_ID_INGAME || id == BUTTON_ID_ANGELICA) {
            Minecraft.getMinecraft().displayGuiScreen(new VRSettingsScreen(event.gui));
        }
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!com.gtnhvr.OpenXRRuntime.isSessionRunning()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null && !VRGuiRenderer.isCapturing()) {
            // Include GTNH's replacement title screen and graphics/settings screens before a world loads.
            boolean mainMenu = mc.theWorld == null;
            VRGuiRenderer.begin(VRGuiRenderer.getCaptureWidth(), VRGuiRenderer.getCaptureHeight(), mainMenu);
        }
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        // Do NOT unbind VRGuiRenderer here! NEI (GuiContainerManager), tooltips,
        // and bookmarks draw AFTER DrawScreenEvent.Post. Unbinding here causes NEI
        // to draw directly to FBO 0 and flicker/disappear from the VR virtual screen.
    }



    /**
     * Mods that draw their own first-person arm (Backhand, Battlegear, Iris hand pass) do it from
     * RenderHandEvent. In VR the hands are rendered at the controllers by VRHandRenderer, so the
     * flat camera-attached hand is suppressed for every listener.
     */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (com.gtnhvr.OpenXRRuntime.isSessionRunning()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onRenderGameOverlayPre(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
        if (!com.gtnhvr.OpenXRRuntime.isSessionRunning()) return;

        if (event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.theWorld != null && !VRHudRenderer.isCapturing()) {
                // Fallback for Forge variants that do not pass through the expected render hook.
                VRHudRenderer.begin(VRGuiRenderer.getCaptureWidth(), VRGuiRenderer.getCaptureHeight());
            }
            return;
        }

        // Visor 1:1 HUD: completely suppress vanilla flat bottom hotbar, screen crosshair, helmet, and portal overlays
        // (Vignette is cancelled via MixinGuiIngame)
        if (event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.HOTBAR
                || event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CROSSHAIRS
                || event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.PORTAL) {
            event.setCanceled(true);
        } else if (event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.HELMET
                && !com.gtnhvr.settings.VRClientSettings.pumpkinEffectEnabled) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (!com.gtnhvr.OpenXRRuntime.isSessionRunning()) return;
        if (event.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            VRVirtualScreen.setLastInteractedBlock(event.x, event.y, event.z);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(net.minecraftforge.event.entity.player.EntityInteractEvent event) {
        if (!com.gtnhvr.OpenXRRuntime.isSessionRunning()) return;
        if (event.target != null) {
            VRVirtualScreen.setLastInteractedEntity(event.target);
        }
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onRenderGameOverlayPost(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if (!com.gtnhvr.OpenXRRuntime.isSessionRunning()) return;

        if (event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) {
            Minecraft mc = Minecraft.getMinecraft();
            if (VRHudRenderer.isCapturing()) {
                if (mc != null && mc.thePlayer != null) {
                    net.minecraft.client.gui.ScaledResolution scaled =
                            new net.minecraft.client.gui.ScaledResolution(
                                    mc, VRHudRenderer.getWidth(), VRHudRenderer.getHeight());
                    VRScreenEffects.render(scaled.getScaledWidth(), scaled.getScaledHeight());
                }
                // MixinGuiIngame closes capture at TAIL after all HUD elements draw.
            }
        }
    }
}
