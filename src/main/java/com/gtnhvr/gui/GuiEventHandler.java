package com.gtnhvr.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.util.List;

@Lwjgl3Aware
public final class GuiEventHandler {
    private static final GuiEventHandler INSTANCE = new GuiEventHandler();
    private static boolean registered;

    public static final int BUTTON_ID_OPTIONS = 99901;
    public static final int BUTTON_ID_INGAME = 99902;
    public static final int BUTTON_ID_ANGELICA = 99903;

    private static int originalGuiScale = -1;
    private static boolean reinitializingGui = false;

    private static int guiPixelWidth(Minecraft mc) {
        return com.gtnhvr.OpenXRRuntime.isSessionRunning()
                ? VRGuiRenderer.getCaptureWidth() : mc.displayWidth;
    }

    private static int guiPixelHeight(Minecraft mc) {
        return com.gtnhvr.OpenXRRuntime.isSessionRunning()
                ? VRGuiRenderer.getCaptureHeight() : mc.displayHeight;
    }

    private static final Field FIELD_X_SIZE = findField(GuiContainer.class, "xSize", "field_146999_f");
    private static final Field FIELD_Y_SIZE = findField(GuiContainer.class, "ySize", "field_147000_g");
    private static final Field FIELD_GUI_LEFT = findField(GuiContainer.class, "guiLeft", "field_147003_i");
    private static final Field FIELD_GUI_TOP = findField(GuiContainer.class, "guiTop", "field_147009_r");

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return findField(superclass, names);
        }
        return null;
    }

    private static int getIntField(Field field, Object obj, int def) {
        if (field != null) {
            try {
                return field.getInt(obj);
            } catch (Throwable ignored) {}
        }
        return def;
    }

    private static void setIntField(Field field, Object obj, int val) {
        if (field != null) {
            try {
                field.setInt(obj, val);
            } catch (Throwable ignored) {}
        }
    }

    private static int computeScaleFactor(Minecraft mc, int guiScaleSetting) {
        int pixelWidth = guiPixelWidth(mc);
        int pixelHeight = guiPixelHeight(mc);
        if (pixelWidth <= 0 || pixelHeight <= 0) return 1;
        int scale = 1;
        int k = guiScaleSetting;
        if (k == 0) {
            k = 1000;
        }
        while (scale < k && pixelWidth / (scale + 1) >= 320 && pixelHeight / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    private GuiEventHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    private static boolean bypassVanillaRedirect = false;

    public static void setBypassVanillaRedirect(boolean bypass) {
        bypassVanillaRedirect = bypass;
    }

    @SubscribeEvent
    public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent event) {
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

        // Auto-fit oversized containers (e.g. 200+ slot chests) so they never clip offscreen
        if (event.gui instanceof GuiContainer) {
            GuiContainer container = (GuiContainer) event.gui;
            Minecraft mc = Minecraft.getMinecraft();
            if (originalGuiScale == -1) {
                originalGuiScale = mc.gameSettings.guiScale;
            }
            int baseSetting = originalGuiScale;
            int currentScale = computeScaleFactor(mc, baseSetting);
            int xSize = getIntField(FIELD_X_SIZE, container, 176);
            int ySize = getIntField(FIELD_Y_SIZE, container, 166);

            if (xSize > 0 && ySize > 0) {
                int pad = 10;
                int targetScale = currentScale;
                while (targetScale > 1 && (guiPixelWidth(mc) / targetScale < xSize + pad || guiPixelHeight(mc) / targetScale < ySize + pad)) {
                    targetScale--;
                }
                if (targetScale < currentScale) {
                    mc.gameSettings.guiScale = targetScale;
                } else {
                    mc.gameSettings.guiScale = originalGuiScale;
                }
            }
        } else {
            if (originalGuiScale != -1) {
                Minecraft.getMinecraft().gameSettings.guiScale = originalGuiScale;
                originalGuiScale = -1;
            }
        }
    }

    @SubscribeEvent
    public void onInitGuiPre(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (event.gui instanceof GuiIngameMenu && com.gtnhvr.OpenXRRuntime.isSessionRunning()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.ticksExisted < 40) {
                event.setCanceled(true);
                return;
            }
            if (!bypassVanillaRedirect) {
                event.setCanceled(true);
                mc.displayGuiScreen(new VRPauseMenuScreen());
                return;
            }
        }
        bypassVanillaRedirect = false;
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
                    && !(gui instanceof net.minecraft.client.gui.GuiScreenBook)) {
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

        if (gui instanceof GuiContainer) {
            GuiContainer container = (GuiContainer) gui;
            if (!reinitializingGui) {
                Minecraft mc = Minecraft.getMinecraft();
                int xSize = getIntField(FIELD_X_SIZE, container, 176);
                int ySize = getIntField(FIELD_Y_SIZE, container, 166);
                if (xSize > 0 && ySize > 0) {
                    int curEff = computeScaleFactor(mc, mc.gameSettings.guiScale);
                    int pad = 10;
                    int target = curEff;
                    while (target > 1 && (guiPixelWidth(mc) / target < xSize + pad || guiPixelHeight(mc) / target < ySize + pad)) {
                        target--;
                    }
                    if (target < curEff) {
                        reinitializingGui = true;
                        try {
                            mc.gameSettings.guiScale = target;
                            net.minecraft.client.gui.ScaledResolution scaled =
                                    new net.minecraft.client.gui.ScaledResolution(mc, guiPixelWidth(mc), guiPixelHeight(mc));
                            gui.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());
                        } finally {
                            reinitializingGui = false;
                        }
                    }
                }
            }

            int top = getIntField(FIELD_GUI_TOP, container, 0);
            int left = getIntField(FIELD_GUI_LEFT, container, 0);
            if (top < 0) {
                setIntField(FIELD_GUI_TOP, container, 0);
            }
            if (left < 0) {
                setIntField(FIELD_GUI_LEFT, container, 0);
            }
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
                // Fallback only. MixinGuiIngame normally starts capture before ScaledResolution is created.
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
