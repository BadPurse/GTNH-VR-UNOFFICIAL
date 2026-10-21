package com.gtnhvr.gui;

import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 Visor Overlays Configuration Screen (VROverlaySettings).
 * Replicates the authentic Visor 0.6.0 overlay settings interface:
 * - Tabs: "Overlays" and "Create Overlay"
 * - Backgrounds: bg_main_1.png (256x256), bg_main_extra_1.png (27x245)
 * - Navigation: Close (X) button, Drag button
 * - Overlays List (Left panel): HUD, Hotbar Main, Hotbar Offhand, Game Screen, Keyboard, Crosshair
 * - Overlay Setup (Right panel): Visibility, Anchor (HMD / MAIN_HAND / OFFHAND / WORLD),
 *   interactive Scale slider, Position/Distance sliders, Opacity/Laser toggles,
 *   Load Defaults, and Save.
 */
@Lwjgl3Aware
public class GuiSettingsOverlays extends GuiScreen {

    private static final ResourceLocation BG_MAIN_1 = new ResourceLocation("gtnhvr", "textures/gui/overlays/settings/bg_main_1.png");
    private static final ResourceLocation BG_MAIN_2 = new ResourceLocation("gtnhvr", "textures/gui/overlays/settings/bg_main_2.png");
    private static final ResourceLocation BG_MAIN_EXTRA = new ResourceLocation("gtnhvr", "textures/gui/overlays/settings/bg_main_extra_1.png");
    private static final ResourceLocation GENERAL_1 = new ResourceLocation("gtnhvr", "textures/gui/overlays/settings/general_1.png");

    private final GuiScreen parentScreen;
    private final GuiScaleHelper scaleHelper = new GuiScaleHelper(257, 256);

    public enum Tab {
        OVERLAYS,
        CREATE_OVERLAY
    }

    private Tab currentTab = Tab.OVERLAYS;

    public static class OverlayEntry {
        public final String id;
        public final String name;
        public final String description;
        public String anchor;
        public boolean visible;
        public float scale;
        public float distance;
        public float opacity;
        public boolean extraFlag; // e.g. laser for crosshair, curved for HUD
        public float offsetX = 0.0F;
        public float offsetY = 0.0F;

        public OverlayEntry(String id, String name, String description, String anchor, boolean visible,
                            float scale, float distance, float opacity, boolean extraFlag) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.anchor = anchor;
            this.visible = visible;
            this.scale = scale;
            this.distance = distance;
            this.opacity = opacity;
            this.extraFlag = extraFlag;
        }
    }

    private final List<OverlayEntry> overlays = new ArrayList<>();
    private int selectedIndex = 0;
    private int activeSlider = -1; // 0: scale, 1: distance, 2: opacity
    private int overlayListScroll = 0;
    private int overlayDetailScroll = 0;

    private int getMaxListScroll() {
        int itemH = scaleHelper.scaledSize(26);
        int totalH = overlays.size() * itemH;
        int listH = scaleHelper.scaledSize(196);
        return Math.max(0, totalH - listH);
    }

    private int getMaxDetailScroll() {
        if (selectedIndex < 0 || selectedIndex >= overlays.size()) return 0;
        OverlayEntry cur = overlays.get(selectedIndex);
        int detailW = scaleHelper.scaledSize(106);
        int descLines = this.fontRendererObj.listFormattedStringToWidth("\u00a77" + cur.description, detailW).size();
        int descH = descLines * this.fontRendererObj.FONT_HEIGHT + scaleHelper.scaledSize(6);
        int rowsCount = rowsFor(cur.id).length;
        int step = scaleHelper.scaledSize(18);
        int totalH = scaleHelper.scaledSize(13) + descH + rowsCount * step;
        int areaH = scaleHelper.scaledSize(176);
        return Math.max(0, totalH - areaH);
    }

    private String saveMessage = null;
    private long saveMessageTime = 0L;

    // Create tab state
    private int selectedTemplate = 0;
    private static final String[] TEMPLATES = {"HUD", "Chat", "Button", "Texture"};

    public GuiSettingsOverlays(GuiScreen parent) {
        this.parentScreen = parent;
        loadOverlayData();
    }

    private static String nameOf(String id) {
        switch (id) {
            case VROverlayConfig.HUD: return "HUD";
            case VROverlayConfig.HOTBAR_MAIN: return "Hotbar Main";
            case VROverlayConfig.HOTBAR_OFFHAND: return "Hotbar Offhand";
            case VROverlayConfig.GAME_SCREEN: return "Game Screen";
            case VROverlayConfig.KEYBOARD: return "Keyboard";
            case VROverlayConfig.CROSSHAIR: return "Crosshair";
            case VROverlayConfig.WRIST: return "Wrist Inventory";
            case VROverlayConfig.STATUS: return "Wrist Status (HP/Food)";
            case VROverlayConfig.MAP: return "Wrist Map (JourneyMap)";
            case VROverlayConfig.INFO: return "Wrist Info (Top-Left)";
            case VROverlayConfig.POTIONS: return "Wrist Potions (Bottom-Right)";
            default: return "Overlay";
        }
    }

    private static String descriptionOf(String id) {
        switch (id) {
            case VROverlayConfig.HUD:
                return "Vanilla HUD with chat, effects and notifications floating in front of you.";
            case VROverlayConfig.HOTBAR_MAIN:
                return "Radial 3x3 hotbar opened with the main hand grip.";
            case VROverlayConfig.HOTBAR_OFFHAND:
                return "Radial 3x3 hotbar opened with the off hand grip.";
            case VROverlayConfig.GAME_SCREEN:
                return "Floating screen for inventories, chests, crafting and menus.";
            case VROverlayConfig.KEYBOARD:
                return "Virtual keyboard for chat, signs and search fields.";
            case VROverlayConfig.CROSSHAIR:
                return "3D '+' crosshair and laser. Anchor: MAIN_HAND or OFFHAND shows one hand, others show both.";
            case VROverlayConfig.STATUS:
                return "Health, hunger, armor, air and experience status HUD attached to the left wrist / forearm.";
            case VROverlayConfig.MAP:
                return "Real-time JourneyMap minimap attached to the right wrist / forearm.";
            case VROverlayConfig.INFO:
                return "Coordinates, biome, time, light level and FPS from top-left HUD attached to your arm.";
            case VROverlayConfig.POTIONS:
                return "Active potion effects, icons and duration countdowns from bottom-right HUD attached to your arm.";
            default:
                return "Inventory on the back of the off hand (Visor Essentials): turn the wrist towards your face. Trigger = click, A = right click, off hand trigger = shift click.";
        }
    }

    /** Controls shown for an overlay, in order. */
    private static String[] rowsFor(String id) {
        switch (id) {
            case VROverlayConfig.HUD:
            case VROverlayConfig.HOTBAR_MAIN:
            case VROverlayConfig.HOTBAR_OFFHAND:
                return new String[] {"VIS", "ANCHOR", "SCALE", "DIST", "OFFX", "OFFY", "FLAG"};
            case VROverlayConfig.GAME_SCREEN:
                return new String[] {"ANCHOR", "SCALE", "DIST", "OFFX", "OFFY"};
            case VROverlayConfig.KEYBOARD:
                return new String[] {"VIS", "SCALE", "DIST", "FLAG"};
            case VROverlayConfig.CROSSHAIR:
                return new String[] {"VIS", "ANCHOR", "SCALE", "DIST", "FLAG"};
            case VROverlayConfig.STATUS:
            case VROverlayConfig.MAP:
            case VROverlayConfig.INFO:
            case VROverlayConfig.POTIONS:
                return new String[] {"VIS", "ANCHOR", "SCALE", "DIST", "OFFX", "OFFY"};
            default:
                return new String[] {"VIS", "SCALE", "OFFX", "OFFY"};
        }
    }

    private static String flagLabelOf(String id) {
        switch (id) {
            case VROverlayConfig.HUD: return "Curved HUD";
            case VROverlayConfig.CROSSHAIR: return "Laser Beam";
            case VROverlayConfig.KEYBOARD: return "Auto Show";
            default: return "Slot Numbers";
        }
    }

    private void loadOverlayData() {
        overlays.clear();
        for (String id : VROverlayConfig.IDS) {
            VROverlayConfig.Entry c = VROverlayConfig.get(id);
            OverlayEntry o = new OverlayEntry(id, nameOf(id), descriptionOf(id), c.anchor, c.visible,
                    c.scale, c.distance, 1.0F, c.flag);
            o.offsetX = c.offsetX;
            o.offsetY = c.offsetY;
            // Values that other settings screens also edit stay linked to those settings.
            switch (id) {
                case VROverlayConfig.HUD:
                    o.scale = VRClientSettings.hudScale;
                    o.distance = VRClientSettings.hudDistance;
                    o.extraFlag = VRClientSettings.curvedHud;
                    break;
                case VROverlayConfig.HOTBAR_MAIN:
                case VROverlayConfig.HOTBAR_OFFHAND:
                    o.extraFlag = VRClientSettings.hotbarSlotNumbers;
                    break;
                case VROverlayConfig.CROSSHAIR:
                    o.visible = VRClientSettings.vrCrosshair;
                    o.distance = VRClientSettings.interactionReach;
                    o.extraFlag = VRClientSettings.crosshairLaser;
                    break;
                default:
                    break;
            }
            overlays.add(o);
        }
    }

    private void applyOverlayData() {
        for (OverlayEntry o : overlays) {
            VROverlayConfig.Entry c = VROverlayConfig.get(o.id);
            c.visible = o.visible;
            c.anchor = o.anchor;
            c.scale = o.scale;
            c.distance = o.distance;
            c.offsetX = o.offsetX;
            c.offsetY = o.offsetY;
            c.flag = o.extraFlag;
            switch (o.id) {
                case VROverlayConfig.HUD:
                    VRClientSettings.hudScale = o.scale;
                    VRClientSettings.hudDistance = o.distance;
                    VRClientSettings.curvedHud = o.extraFlag;
                    break;
                case VROverlayConfig.HOTBAR_MAIN:
                case VROverlayConfig.HOTBAR_OFFHAND:
                    VRClientSettings.hotbarSlotNumbers = o.extraFlag;
                    break;
                case VROverlayConfig.CROSSHAIR:
                    VRClientSettings.vrCrosshair = o.visible;
                    VRClientSettings.interactionReach = o.distance;
                    VRClientSettings.crosshairLaser = o.extraFlag;
                    break;
                default:
                    break;
            }
        }
        VROverlayConfig.save();
        VRClientSettings.save();

        // Re-place overlays that cache their pose so the new values show immediately.
        VRHudRenderer.resetSmoothing();
        VRVirtualScreen.getInstance().resetOrientation();
        VRVirtualKeyboard.getInstance().resetDefaultPose();

        saveMessage = I18n.format("visor.overlay.options.overlays.save") + " OK!";
        saveMessageTime = System.currentTimeMillis();
    }

    private void resetOverlayDefaults() {
        if (selectedIndex < 0 || selectedIndex >= overlays.size()) {
            return;
        }
        OverlayEntry cur = overlays.get(selectedIndex);
        VROverlayConfig.Entry d = VROverlayConfig.defaults(cur.id);
        cur.anchor = d.anchor;
        cur.visible = d.visible;
        cur.scale = d.scale;
        cur.distance = d.distance;
        cur.offsetX = d.offsetX;
        cur.offsetY = d.offsetY;
        cur.extraFlag = d.flag;
        if (cur.id.equals(VROverlayConfig.HOTBAR_MAIN) || cur.id.equals(VROverlayConfig.HOTBAR_OFFHAND)) {
            cur.extraFlag = false; // slot numbers are off by default
        }
    }

    @Override
    public void initGui() {
        scaleHelper.computeScale(this.width, this.height);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        scaleHelper.computeScale(this.width, this.height);

        // Dark translucent background tint
        drawDefaultBackground();

        int bgX = scaleHelper.getStartX();
        int bgY = scaleHelper.getStartY();
        int bgW = scaleHelper.scaledSize(256);
        int bgH = scaleHelper.scaledSize(256);
        int extraX = bgX + scaleHelper.scaledSize(230);
        int extraW = scaleHelper.scaledSize(27);
        int extraH = scaleHelper.scaledSize(245);

        // Solid opaque backing rect matching Visor to prevent any VR alpha see-through
        drawRect(bgX, bgY, extraX + extraW, bgY + bgH, 0xFF18181A);

        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // 1. Draw Main Background (bg_main_1 for Overlays, bg_main_2 for Create)
        ResourceLocation mainBg = (currentTab == Tab.OVERLAYS) ? BG_MAIN_1 : BG_MAIN_2;
        this.mc.getTextureManager().bindTexture(mainBg);
        drawTexturedQuad(bgX, bgY, bgW, bgH, 0, 0, 256, 256, 256, 256);

        // 2. Draw Extra Background (bg_main_extra_1) on right side (Visor 1:1 at x=230)
        this.mc.getTextureManager().bindTexture(BG_MAIN_EXTRA);
        drawTexturedQuad(extraX, bgY + scaleHelper.scaledSize(6), extraW, extraH, 0, 0, 27, 245, 27, 245);

        // 3. Draw Tab Buttons at top (115 x 23 scaled)
        int tabW = scaleHelper.scaledSize(115);
        int tabH = scaleHelper.scaledSize(23);
        int tabY = bgY + scaleHelper.scaledSize(6);

        this.mc.getTextureManager().bindTexture(GENERAL_1);

        if (currentTab == Tab.OVERLAYS) {
            // "Overlays" tab active on left
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays"),
                    bgX + tabW / 2, tabY + (tabH - 8) / 2, 0xFFFFFF);

            // "Create Overlay" tab button on right (u: 64, v: 0, w: 115, h: 23)
            int tabRightX = bgX + tabW;
            boolean tabHover = isHovered(mouseX, mouseY, tabRightX, tabY, tabW, tabH);
            GLStateManager.glColor4f(tabHover ? 1.0F : 0.85F, tabHover ? 1.0F : 0.85F, 1.0F, 1.0F);
            drawTexturedQuad(tabRightX, tabY, tabW, tabH, 64, 0, 115, 23, 179, 188);
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays.create_overlay"),
                    tabRightX + tabW / 2, tabY + (tabH - 8) / 2, tabHover ? 0xFFFFA0 : 0xCCCCCC);
        } else {
            // "Overlays" tab button on left (u: 64, v: 24, w: 115, h: 23)
            int tabLeftX = bgX;
            boolean tabHover = isHovered(mouseX, mouseY, tabLeftX, tabY, tabW, tabH);
            GLStateManager.glColor4f(tabHover ? 1.0F : 0.85F, tabHover ? 1.0F : 0.85F, 1.0F, 1.0F);
            drawTexturedQuad(tabLeftX, tabY, tabW, tabH, 64, 24, 115, 23, 179, 188);
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays"),
                    tabLeftX + tabW / 2, tabY + (tabH - 8) / 2, tabHover ? 0xFFFFA0 : 0xCCCCCC);

            // "Create Overlay" tab active on right
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays.create_overlay"),
                    bgX + tabW + tabW / 2, tabY + (tabH - 8) / 2, 0xFFFFFF);
        }

        // 4. Close (X) button at extraX + 4, bgY + 12 (size 19x19)
        int closeBtnX = extraX + scaleHelper.scaledSize(4);
        int closeBtnY = bgY + scaleHelper.scaledSize(12);
        int btnSz = scaleHelper.scaledSize(19);
        boolean closeHover = isHovered(mouseX, mouseY, closeBtnX, closeBtnY, btnSz, btnSz);
        int closeU = closeHover ? 145 : 110;
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(GENERAL_1);
        drawTexturedQuad(closeBtnX, closeBtnY, btnSz, btnSz, closeU, 112, 34, 34, 179, 188);

        // 5. Drag button at extraX + 4, bgY + 35 (size 19x19)
        int dragBtnY = bgY + scaleHelper.scaledSize(35);
        boolean dragHover = isHovered(mouseX, mouseY, closeBtnX, dragBtnY, btnSz, btnSz);
        int dragU = dragHover ? 110 : 75;
        drawTexturedQuad(closeBtnX, dragBtnY, btnSz, btnSz, dragU, 152, 34, 34, 179, 188);

        // Render Tab Contents
        if (currentTab == Tab.OVERLAYS) {
            drawOverlaysTab(mouseX, mouseY, bgX, bgY);
        } else {
            drawCreateTab(mouseX, mouseY, bgX, bgY);
        }

        // Save confirmation toast
        if (saveMessage != null) {
            long elapsed = System.currentTimeMillis() - saveMessageTime;
            if (elapsed < 3000L) {
                int toastY = bgY + scaleHelper.scaledSize(238);
                drawCenteredString(this.fontRendererObj, "\u00a7a" + saveMessage, bgX + bgW / 2, toastY, 0x55FF55);
            } else {
                saveMessage = null;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawOverlaysTab(int mouseX, int mouseY, int bgX, int bgY) {
        int listX = bgX + scaleHelper.scaledSize(12);
        int listY = bgY + scaleHelper.scaledSize(36);
        int listW = scaleHelper.scaledSize(96);
        int listH = scaleHelper.scaledSize(196);
        int itemH = scaleHelper.scaledSize(26);

        // Left Panel: Overlays List Header
        drawString(this.fontRendererObj, "\u00a77" + I18n.format("visor.overlay.options.overlays"),
                listX, listY - scaleHelper.scaledSize(8), 0xAAAAAA);

        // Clamp list scroll
        overlayListScroll = Math.max(0, Math.min(getMaxListScroll(), overlayListScroll));

        // Enable scissor for left list
        VRSettingsScreen.enableScissor(this.mc, listX, listY, listW, listH, this.width, this.height);

        // List Entries
        for (int i = 0; i < overlays.size(); i++) {
            OverlayEntry entry = overlays.get(i);
            int curY = listY + i * itemH - overlayListScroll;
            if (curY + itemH < listY || curY > listY + listH) {
                continue;
            }
            boolean hovered = isHovered(mouseX, mouseY, listX, curY, listW, itemH - scaleHelper.scaledSize(2))
                    && isHovered(mouseX, mouseY, listX, listY, listW, listH);
            boolean selected = (i == selectedIndex);

            // Entry Background
            int bgColor = selected ? 0xFF305080 : (hovered ? 0xFF404040 : 0xFF202020);
            drawRect(listX, curY, listX + listW, curY + itemH - scaleHelper.scaledSize(2), bgColor);

            // Left accent bar on selected item
            if (selected) {
                drawRect(listX, curY, listX + scaleHelper.scaledSize(2), curY + itemH - scaleHelper.scaledSize(2), 0xFF26D9FF);
            }

            // Name
            int textColor = selected ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            drawString(this.fontRendererObj, entry.name, listX + scaleHelper.scaledSize(6),
                    curY + scaleHelper.scaledSize(3), textColor);

            // Subtitle / Anchor badge
            String sub = entry.anchor + " | " + (entry.visible ? "\u00a7aON" : "\u00a7cOFF");
            drawString(this.fontRendererObj, "\u00a78" + sub, listX + scaleHelper.scaledSize(6),
                    curY + scaleHelper.scaledSize(14), 0x777777);
        }

        VRSettingsScreen.disableScissor();

        // Draw scrollbar for left list if content overflows
        int maxListScroll = getMaxListScroll();
        if (maxListScroll > 0) {
            drawScrollbar(listX + listW + scaleHelper.scaledSize(2), listY, scaleHelper.scaledSize(2), listH,
                    overlayListScroll, maxListScroll);
        }

        // Right Panel: Selected Overlay Detail Configuration
        if (selectedIndex >= 0 && selectedIndex < overlays.size()) {
            OverlayEntry cur = overlays.get(selectedIndex);
            int detailX = bgX + scaleHelper.scaledSize(118);
            int detailY = bgY + scaleHelper.scaledSize(36);
            int detailW = scaleHelper.scaledSize(106);
            int detailH = scaleHelper.scaledSize(176);

            overlayDetailScroll = Math.max(0, Math.min(getMaxDetailScroll(), overlayDetailScroll));

            // Enable scissor for detail area
            VRSettingsScreen.enableScissor(this.mc, detailX, detailY, detailW + scaleHelper.scaledSize(6), detailH, this.width, this.height);

            // 1. Overlay Title
            int curY = detailY - overlayDetailScroll;
            drawString(this.fontRendererObj, "\u00a7f\u00a7l" + cur.name, detailX, curY, 0xFFFFFF);
            curY += scaleHelper.scaledSize(13);

            // 2. Description (wrapped)
            int descLines = this.fontRendererObj.listFormattedStringToWidth("\u00a77" + cur.description, detailW).size();
            this.fontRendererObj.drawSplitString("\u00a77" + cur.description, detailX, curY, detailW, 0x888888);
            curY += descLines * this.fontRendererObj.FONT_HEIGHT + scaleHelper.scaledSize(6);

            // 3. Controls
            int ctrlH = scaleHelper.scaledSize(14);
            int step = scaleHelper.scaledSize(18);

            for (String row : rowsFor(cur.id)) {
                boolean hover = isHovered(mouseX, mouseY, detailX, curY, detailW, ctrlH)
                        && isHovered(mouseX, mouseY, detailX, detailY, detailW, detailH);
                switch (row) {
                    case "VIS":
                        drawRect(detailX, curY, detailX + detailW, curY + ctrlH, hover ? 0xFF404040 : 0xFF282828);
                        drawCenteredString(this.fontRendererObj,
                                I18n.format("visor.overlay.options.visibility") + ": "
                                        + (cur.visible ? "\u00a7aON" : "\u00a7cOFF"),
                                detailX + detailW / 2, curY + (ctrlH - 8) / 2, 0xFFFFFF);
                        break;
                    case "ANCHOR":
                        drawRect(detailX, curY, detailX + detailW, curY + ctrlH, hover ? 0xFF404040 : 0xFF282828);
                        drawCenteredString(this.fontRendererObj, "Anchor: \u00a7b" + cur.anchor,
                                detailX + detailW / 2, curY + (ctrlH - 8) / 2, 0xFFFFFF);
                        break;
                    case "SCALE":
                        drawSlider(mouseX, detailX, curY, detailW, ctrlH, "Scale", cur.scale, 0.2F, 3.0F, "%.2fx", 0);
                        break;
                    case "DIST":
                        drawSlider(mouseX, detailX, curY, detailW, ctrlH,
                                cur.id.equals(VROverlayConfig.CROSSHAIR) ? "Reach" : "Distance",
                                cur.distance, 0.2F, 6.0F, "%.2fm", 1);
                        break;
                    case "OFFX":
                        drawSlider(mouseX, detailX, curY, detailW, ctrlH, "Offset X", cur.offsetX, -0.5F, 0.5F, "%.2fm", 2);
                        break;
                    case "OFFY":
                        drawSlider(mouseX, detailX, curY, detailW, ctrlH, "Offset Y", cur.offsetY, -0.5F, 0.5F, "%.2fm", 3);
                        break;
                    default: // FLAG
                        drawRect(detailX, curY, detailX + detailW, curY + ctrlH, hover ? 0xFF404040 : 0xFF282828);
                        drawCenteredString(this.fontRendererObj,
                                flagLabelOf(cur.id) + ": " + (cur.extraFlag ? "\u00a7aON" : "\u00a77OFF"),
                                detailX + detailW / 2, curY + (ctrlH - 8) / 2, 0xFFFFFF);
                        break;
                }
                curY += step;
            }

            VRSettingsScreen.disableScissor();

            // Draw scrollbar for detail panel if content overflows
            int maxDetailScroll = getMaxDetailScroll();
            if (maxDetailScroll > 0) {
                drawScrollbar(detailX + detailW + scaleHelper.scaledSize(2), detailY, scaleHelper.scaledSize(2), detailH,
                        overlayDetailScroll, maxDetailScroll);
            }

            // Action Buttons at bottom (Fixed)
            int btnY = bgY + scaleHelper.scaledSize(218);
            int halfW = (detailW - scaleHelper.scaledSize(4)) / 2;

            // "Load defaults" Button
            int defX = detailX;
            boolean defHover = isHovered(mouseX, mouseY, defX, btnY, halfW, scaleHelper.scaledSize(15));
            drawRect(defX, btnY, defX + halfW, btnY + scaleHelper.scaledSize(15), defHover ? 0xFF604040 : 0xFF402828);
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays.load_defaults"),
                    defX + halfW / 2, btnY + (scaleHelper.scaledSize(15) - 8) / 2, defHover ? 0xFFFFA0 : 0xDDDDDD);

            // "Save" Button
            int saveX = detailX + halfW + scaleHelper.scaledSize(4);
            boolean saveHover = isHovered(mouseX, mouseY, saveX, btnY, halfW, scaleHelper.scaledSize(15));
            drawRect(saveX, btnY, saveX + halfW, btnY + scaleHelper.scaledSize(15), saveHover ? 0xFF306030 : 0xFF204020);
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays.save"),
                    saveX + halfW / 2, btnY + (scaleHelper.scaledSize(15) - 8) / 2, saveHover ? 0xA0FFA0 : 0x88EE88);
        }
    }

    private void drawSlider(int mouseX, int x, int y, int w, int h, String name, float val, float min, float max, String fmt, int sliderIndex) {
        drawRect(x, y, x + w, y + h, 0xFF202020);
        float pct = Math.max(0.0F, Math.min(1.0F, (val - min) / (max - min)));
        int fillW = Math.round(w * pct);
        drawRect(x, y, x + fillW, y + h, 0xFF306080);
        drawRect(x + fillW - scaleHelper.scaledSize(2), y, x + fillW, y + h, 0xFF26D9FF);

        String text = name + ": " + String.format(fmt, val);
        drawCenteredString(this.fontRendererObj, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
    }

    private void drawCreateTab(int mouseX, int mouseY, int bgX, int bgY) {
        int listX = bgX + scaleHelper.scaledSize(14);
        int listY = bgY + scaleHelper.scaledSize(40);
        int listW = scaleHelper.scaledSize(96);
        int itemH = scaleHelper.scaledSize(22);

        drawString(this.fontRendererObj, "\u00a77Select Template:", listX, listY - scaleHelper.scaledSize(8), 0xAAAAAA);

        for (int i = 0; i < TEMPLATES.length; i++) {
            int curY = listY + i * itemH;
            boolean hovered = isHovered(mouseX, mouseY, listX, curY, listW, itemH - scaleHelper.scaledSize(2));
            boolean selected = (i == selectedTemplate);

            drawRect(listX, curY, listX + listW, curY + itemH - scaleHelper.scaledSize(2),
                    selected ? 0xFF305080 : (hovered ? 0xFF404040 : 0xFF202020));
            if (selected) {
                drawRect(listX, curY, listX + scaleHelper.scaledSize(2), curY + itemH - scaleHelper.scaledSize(2), 0xFF26D9FF);
            }
            drawString(this.fontRendererObj, TEMPLATES[i], listX + scaleHelper.scaledSize(6),
                    curY + (itemH - 10) / 2, selected ? 0xFFFFFF : 0xCCCCCC);
        }

        // Right side: Creation Properties
        int detailX = bgX + scaleHelper.scaledSize(118);
        int detailY = bgY + scaleHelper.scaledSize(40);
        int detailW = scaleHelper.scaledSize(128);

        drawString(this.fontRendererObj, "\u00a7f\u00a7lCreate " + TEMPLATES[selectedTemplate] + " Overlay", detailX, detailY, 0xFFFFFF);
        detailY += scaleHelper.scaledSize(18);

        drawRect(detailX, detailY, detailX + detailW, detailY + scaleHelper.scaledSize(16), 0xFF202020);
        drawString(this.fontRendererObj, "\u00a78Name: custom_" + TEMPLATES[selectedTemplate].toLowerCase(),
                detailX + scaleHelper.scaledSize(4), detailY + scaleHelper.scaledSize(4), 0xAAAAAA);
        detailY += scaleHelper.scaledSize(22);

        drawRect(detailX, detailY, detailX + detailW, detailY + scaleHelper.scaledSize(16), 0xFF202020);
        drawString(this.fontRendererObj, "\u00a78Anchor: HMD",
                detailX + scaleHelper.scaledSize(4), detailY + scaleHelper.scaledSize(4), 0xAAAAAA);
        detailY += scaleHelper.scaledSize(28);

        // Create Button
        boolean createHover = isHovered(mouseX, mouseY, detailX, detailY, detailW, scaleHelper.scaledSize(18));
        drawRect(detailX, detailY, detailX + detailW, detailY + scaleHelper.scaledSize(18), createHover ? 0xFF306030 : 0xFF204020);
        drawCenteredString(this.fontRendererObj, "\u00a7a+ Create Overlay", detailX + detailW / 2,
                detailY + (scaleHelper.scaledSize(18) - 8) / 2, 0xFFFFFF);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int bgX = scaleHelper.getStartX();
        int bgY = scaleHelper.getStartY();
        int tabW = scaleHelper.scaledSize(115);
        int tabH = scaleHelper.scaledSize(23);
        int tabY = bgY + scaleHelper.scaledSize(6);

        // 1. Tab switches
        if (currentTab == Tab.OVERLAYS) {
            if (isHovered(mouseX, mouseY, bgX + tabW, tabY, tabW, tabH)) {
                playClickSound();
                currentTab = Tab.CREATE_OVERLAY;
                return;
            }
        } else {
            if (isHovered(mouseX, mouseY, bgX, tabY, tabW, tabH)) {
                playClickSound();
                currentTab = Tab.OVERLAYS;
                return;
            }
        }

        // 2. Close button
        int extraX = bgX + scaleHelper.scaledSize(230);
        int closeBtnX = extraX + scaleHelper.scaledSize(4);
        int closeBtnY = bgY + scaleHelper.scaledSize(12);
        int btnSz = scaleHelper.scaledSize(19);
        if (isHovered(mouseX, mouseY, closeBtnX, closeBtnY, btnSz, btnSz)) {
            playClickSound();
            this.mc.displayGuiScreen(parentScreen);
            return;
        }

        if (currentTab == Tab.OVERLAYS) {
            handleOverlaysClick(mouseX, mouseY, bgX, bgY);
        } else {
            handleCreateClick(mouseX, mouseY, bgX, bgY);
        }
    }

    private void handleOverlaysClick(int mouseX, int mouseY, int bgX, int bgY) {
        int listX = bgX + scaleHelper.scaledSize(12);
        int listY = bgY + scaleHelper.scaledSize(36);
        int listW = scaleHelper.scaledSize(96);
        int listH = scaleHelper.scaledSize(196);
        int itemH = scaleHelper.scaledSize(26);

        // Select overlay from list (only if clicking within list bounds)
        if (isHovered(mouseX, mouseY, listX, listY, listW, listH)) {
            for (int i = 0; i < overlays.size(); i++) {
                int curY = listY + i * itemH - overlayListScroll;
                if (isHovered(mouseX, mouseY, listX, curY, listW, itemH - scaleHelper.scaledSize(2))) {
                    if (selectedIndex != i) {
                        selectedIndex = i;
                        overlayDetailScroll = 0;
                    }
                    playClickSound();
                    return;
                }
            }
        }

        if (selectedIndex >= 0 && selectedIndex < overlays.size()) {
            OverlayEntry cur = overlays.get(selectedIndex);
            int detailX = bgX + scaleHelper.scaledSize(118);
            int detailY = bgY + scaleHelper.scaledSize(36);
            int detailW = scaleHelper.scaledSize(106);
            int detailH = scaleHelper.scaledSize(176);

            // Controls inside detail scroll area
            if (isHovered(mouseX, mouseY, detailX, detailY, detailW, detailH)) {
                int curY = detailY - overlayDetailScroll;
                curY += scaleHelper.scaledSize(13); // title
                int descLines = this.fontRendererObj.listFormattedStringToWidth("\u00a77" + cur.description, detailW).size();
                curY += descLines * this.fontRendererObj.FONT_HEIGHT + scaleHelper.scaledSize(6);

                int ctrlH = scaleHelper.scaledSize(14);
                int step = scaleHelper.scaledSize(18);

                for (String row : rowsFor(cur.id)) {
                    if (isHovered(mouseX, mouseY, detailX, curY, detailW, ctrlH)) {
                        switch (row) {
                            case "VIS":
                                cur.visible = !cur.visible;
                                playClickSound();
                                break;
                            case "ANCHOR":
                                cycleAnchor(cur);
                                playClickSound();
                                break;
                            case "SCALE":
                                activeSlider = 0;
                                updateSliderFromMouse(mouseX, detailX, detailW, cur, 0);
                                break;
                            case "DIST":
                                activeSlider = 1;
                                updateSliderFromMouse(mouseX, detailX, detailW, cur, 1);
                                break;
                            case "OFFX":
                                activeSlider = 2;
                                updateSliderFromMouse(mouseX, detailX, detailW, cur, 2);
                                break;
                            case "OFFY":
                                activeSlider = 3;
                                updateSliderFromMouse(mouseX, detailX, detailW, cur, 3);
                                break;
                            default:
                                cur.extraFlag = !cur.extraFlag;
                                playClickSound();
                                break;
                        }
                        return;
                    }
                    curY += step;
                }
            }

            // Bottom Buttons (Fixed outside detail area)
            int btnY = bgY + scaleHelper.scaledSize(218);
            int halfW = (detailW - scaleHelper.scaledSize(4)) / 2;

            // Load Defaults
            if (isHovered(mouseX, mouseY, detailX, btnY, halfW, scaleHelper.scaledSize(15))) {
                resetOverlayDefaults();
                playClickSound();
                return;
            }

            // Save
            int saveX = detailX + halfW + scaleHelper.scaledSize(4);
            if (isHovered(mouseX, mouseY, saveX, btnY, halfW, scaleHelper.scaledSize(15))) {
                applyOverlayData();
                playClickSound();
                return;
            }
        }
    }

    private void handleCreateClick(int mouseX, int mouseY, int bgX, int bgY) {
        int listX = bgX + scaleHelper.scaledSize(14);
        int listY = bgY + scaleHelper.scaledSize(40);
        int listW = scaleHelper.scaledSize(96);
        int itemH = scaleHelper.scaledSize(22);

        for (int i = 0; i < TEMPLATES.length; i++) {
            int curY = listY + i * itemH;
            if (isHovered(mouseX, mouseY, listX, curY, listW, itemH - scaleHelper.scaledSize(2))) {
                selectedTemplate = i;
                playClickSound();
                return;
            }
        }

        int detailX = bgX + scaleHelper.scaledSize(118);
        int detailY = bgY + scaleHelper.scaledSize(40) + scaleHelper.scaledSize(18 + 22 + 28);
        int detailW = scaleHelper.scaledSize(128);
        if (isHovered(mouseX, mouseY, detailX, detailY, detailW, scaleHelper.scaledSize(18))) {
            playClickSound();
            saveMessage = "Custom " + TEMPLATES[selectedTemplate] + " Created!";
            saveMessageTime = System.currentTimeMillis();
        }
    }

    private void playClickSound() {
        this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
                new ResourceLocation("gui.button.press"), 1.0F));
    }

    private void cycleAnchor(OverlayEntry cur) {
        switch (cur.anchor) {
            case "HMD": cur.anchor = "MAIN_HAND"; break;
            case "MAIN_HAND": cur.anchor = "OFFHAND"; break;
            case "OFFHAND": cur.anchor = "WORLD"; break;
            default: cur.anchor = "HMD"; break;
        }
    }

    private float stickListScrollAccum = 0.0F;
    private float stickDetailScrollAccum = 0.0F;

    public void handleStickScroll(float stickY, int mouseX, int mouseY) {
        if (currentTab != Tab.OVERLAYS) return;

        if (Math.abs(stickY) < 0.15F) {
            stickListScrollAccum = 0.0F;
            stickDetailScrollAccum = 0.0F;
            return;
        }

        int bgX = scaleHelper.getStartX();
        int splitX = bgX + scaleHelper.scaledSize(112);

        // If pointer is in the left list area or left side of menu
        boolean targetLeft = (mouseX > 0 && mouseX < splitX);

        float speed = scaleHelper.scaledSize(4) * stickY;

        if (targetLeft) {
            int max = getMaxListScroll();
            if (max > 0) {
                stickListScrollAccum -= speed;
                if (Math.abs(stickListScrollAccum) >= 1.0F) {
                    int delta = (int) stickListScrollAccum;
                    overlayListScroll = Math.max(0, Math.min(max, overlayListScroll + delta));
                    stickListScrollAccum -= delta;
                }
            }
        } else {
            // Target right details panel
            int max = getMaxDetailScroll();
            if (max > 0) {
                stickDetailScrollAccum -= speed;
                if (Math.abs(stickDetailScrollAccum) >= 1.0F) {
                    int delta = (int) stickDetailScrollAccum;
                    overlayDetailScroll = Math.max(0, Math.min(max, overlayDetailScroll + delta));
                    stickDetailScrollAccum -= delta;
                }
            } else {
                int listMax = getMaxListScroll();
                if (listMax > 0) {
                    stickListScrollAccum -= speed;
                    if (Math.abs(stickListScrollAccum) >= 1.0F) {
                        int delta = (int) stickListScrollAccum;
                        overlayListScroll = Math.max(0, Math.min(listMax, overlayListScroll + delta));
                        stickListScrollAccum -= delta;
                    }
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (currentTab != Tab.OVERLAYS) return;

        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            int d = (wheel > 0) ? -scaleHelper.scaledSize(14) : scaleHelper.scaledSize(14);
            int mouseX = org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth;

            int bgX = scaleHelper.getStartX();
            int splitX = bgX + scaleHelper.scaledSize(112);

            if (mouseX > 0 && mouseX < splitX) {
                overlayListScroll = Math.max(0, Math.min(getMaxListScroll(), overlayListScroll + d));
            } else {
                int maxDetail = getMaxDetailScroll();
                if (maxDetail > 0) {
                    overlayDetailScroll = Math.max(0, Math.min(maxDetail, overlayDetailScroll + d));
                } else {
                    overlayListScroll = Math.max(0, Math.min(getMaxListScroll(), overlayListScroll + d));
                }
            }
        }
    }

    private void drawScrollbar(int x, int y, int w, int h, int currentScroll, int maxScroll) {
        if (maxScroll <= 0) return;
        drawRect(x, y, x + w, y + h, 0xFF141414);
        int thumbH = Math.max(scaleHelper.scaledSize(10), h * h / (h + maxScroll));
        int thumbY = y + (h - thumbH) * currentScroll / maxScroll;
        drawRect(x, thumbY, x + w, thumbY + thumbH, 0xFF7BB3F0);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (activeSlider >= 0 && selectedIndex >= 0 && selectedIndex < overlays.size()) {
            OverlayEntry cur = overlays.get(selectedIndex);
            int bgX = scaleHelper.getStartX();
            int detailX = bgX + scaleHelper.scaledSize(118);
            int detailW = scaleHelper.scaledSize(106);
            updateSliderFromMouse(mouseX, detailX, detailW, cur, activeSlider);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which >= 0) {
            activeSlider = -1;
        }
    }

    private void updateSliderFromMouse(int mouseX, int x, int w, OverlayEntry cur, int slider) {
        float pct = Math.max(0.0F, Math.min(1.0F, (float) (mouseX - x) / (float) w));
        if (slider == 0) {
            cur.scale = 0.2F + pct * 2.8F;           // 0.2x .. 3.0x
        } else if (slider == 1) {
            cur.distance = 0.2F + pct * 5.8F;        // 0.2 m .. 6.0 m
        } else if (slider == 2) {
            cur.offsetX = -0.5F + pct;               // -0.5 m .. 0.5 m
        } else if (slider == 3) {
            cur.offsetY = -0.5F + pct;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC
            this.mc.displayGuiScreen(parentScreen);
        }
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawTexturedQuad(int x, int y, int width, int height,
                                  float u, float v, float uWidth, float vHeight,
                                  float texWidth, float texHeight) {
        float f = 1.0F / texWidth;
        float f1 = 1.0F / texHeight;
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, u * f, (v + vHeight) * f1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, (u + uWidth) * f, (v + vHeight) * f1);
        tessellator.addVertexWithUV(x + width, y, 0.0D, (u + uWidth) * f, v * f1);
        tessellator.addVertexWithUV(x, y, 0.0D, u * f, v * f1);
        tessellator.draw();
    }
}
