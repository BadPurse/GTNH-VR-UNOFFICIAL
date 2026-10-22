package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        public String name;
        public String description;
        public boolean custom;
        public String template = VROverlayConfig.HUD;
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

    private GuiTextField overlaySearchField;
    private GuiTextField customNameField;
    private GuiTextField customDescriptionField;
    private GuiTextField templateSearchField;
    private boolean showFilterPanel;
    private boolean filterBuiltIn = true;
    private boolean filterCustom = true;
    private boolean filterHasOptions = true;
    private boolean filterNoOptions = true;
    private String selectedGroup;
    private boolean confirmRemove;
    private boolean changesNotSaved;
    private VROverlayConfig.Entry copiedOptions;
    private String copiedGroup;

    private static final String GROUP_GENERAL = "General";
    private static final String GROUP_VISIBILITY = "Visibility";
    private static final String GROUP_POSE = "Pose";
    private static final String GROUP_RESIZING = "Resizing";
    private static final String GROUP_REGION = "Screen Region";
    private static final String GROUP_MISC = "Misc";

    private static final String[] TEMPLATES = {"HUD", "Game Screen", "Virtual Keyboard"};
    private static final String[] TEMPLATE_IDS = {VROverlayConfig.HUD, VROverlayConfig.GAME_SCREEN, VROverlayConfig.KEYBOARD};

    private int getMaxListScroll() {
        int itemH = scaleHelper.scaledSize(26);
        int totalH = getVisibleOverlays().size() * itemH;
        int listH = scaleHelper.scaledSize(176);
        return Math.max(0, totalH - listH);
    }

    private int getMaxDetailScroll() {
        if (selectedIndex < 0 || selectedIndex >= overlays.size()) return 0;
        OverlayEntry cur = overlays.get(selectedIndex);
        int detailW = scaleHelper.scaledSize(106);
        int areaH = scaleHelper.scaledSize(176);
        if (selectedGroup != null) {
            int rowsCount = rowsForGroup(cur.id, selectedGroup).length;
            int totalH = scaleHelper.scaledSize(25) + rowsCount * scaleHelper.scaledSize(18);
            return Math.max(0, totalH - areaH);
        }
        int descLines = this.fontRendererObj.listFormattedStringToWidth("\u00a77" + cur.description, detailW).size();
        int groupCount = groupsFor(cur.id).length;
        int totalH = scaleHelper.scaledSize(38) + descLines * this.fontRendererObj.FONT_HEIGHT
                + groupCount * scaleHelper.scaledSize(16);
        return Math.max(0, totalH - areaH);
    }

    private List<OverlayEntry> getVisibleOverlays() {
        List<OverlayEntry> result = new ArrayList<>();
        String query = overlaySearchField == null ? "" : overlaySearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (OverlayEntry entry : overlays) {
            if (entry.custom ? !filterCustom : !filterBuiltIn) continue;
            boolean hasOptions = rowsFor(entry.id).length > 0;
            if (hasOptions ? !filterHasOptions : !filterNoOptions) continue;
            if (!query.isEmpty()) {
                String haystack = (entry.name + " " + entry.id + " " + entry.description + " " + entry.template)
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            result.add(entry);
        }
        return result;
    }

    private static boolean hasRow(String[] rows, String wanted) {
        for (String row : rows) if (wanted.equals(row)) return true;
        return false;
    }

    private static String[] groupsFor(String id) {
        String[] rows = rowsFor(id);
        List<String> groups = new ArrayList<>();
        groups.add(GROUP_GENERAL);
        if (hasRow(rows, "VIS")) groups.add(GROUP_VISIBILITY);
        if (hasRow(rows, "ANCHOR") || hasRow(rows, "DIST")) groups.add(GROUP_POSE);
        if (hasRow(rows, "SCALE")) groups.add(GROUP_RESIZING);
        if (hasRow(rows, "OFFX") || hasRow(rows, "OFFY")) groups.add(GROUP_REGION);
        if (hasRow(rows, "FLAG")) groups.add(GROUP_MISC);
        return groups.toArray(new String[0]);
    }

    private static String[] rowsForGroup(String id, String group) {
        String[] rows = rowsFor(id);
        if (GROUP_VISIBILITY.equals(group)) return hasRow(rows, "VIS") ? new String[] {"VIS"} : new String[0];
        if (GROUP_POSE.equals(group)) {
            List<String> selected = new ArrayList<>();
            if (hasRow(rows, "ANCHOR")) selected.add("ANCHOR");
            if (hasRow(rows, "DIST")) selected.add("DIST");
            return selected.toArray(new String[0]);
        }
        if (GROUP_RESIZING.equals(group)) return hasRow(rows, "SCALE") ? new String[] {"SCALE"} : new String[0];
        if (GROUP_REGION.equals(group)) {
            List<String> selected = new ArrayList<>();
            if (hasRow(rows, "OFFX")) selected.add("OFFX");
            if (hasRow(rows, "OFFY")) selected.add("OFFY");
            return selected.toArray(new String[0]);
        }
        if (GROUP_MISC.equals(group)) return hasRow(rows, "FLAG") ? new String[] {"FLAG"} : new String[0];
        return new String[0];
    }

    private static VROverlayConfig.Entry copyToConfig(OverlayEntry overlay) {
        VROverlayConfig.Entry entry = new VROverlayConfig.Entry();
        entry.visible = overlay.visible;
        entry.anchor = overlay.anchor;
        entry.scale = overlay.scale;
        entry.distance = overlay.distance;
        entry.offsetX = overlay.offsetX;
        entry.offsetY = overlay.offsetY;
        entry.flag = overlay.extraFlag;
        entry.custom = overlay.custom;
        entry.name = overlay.name;
        entry.description = overlay.description;
        entry.template = overlay.template;
        return entry;
    }

    private void copyCurrentGroup() {
        if (selectedIndex < 0 || selectedIndex >= overlays.size() || selectedGroup == null
                || GROUP_GENERAL.equals(selectedGroup)) return;
        copiedOptions = copyToConfig(overlays.get(selectedIndex));
        copiedGroup = selectedGroup;
        saveMessage = "Copied " + selectedGroup;
        saveMessageTime = System.currentTimeMillis();
    }

    private void pasteCurrentGroup() {
        if (selectedIndex < 0 || selectedIndex >= overlays.size() || selectedGroup == null
                || copiedOptions == null || !selectedGroup.equals(copiedGroup)) return;
        OverlayEntry cur = overlays.get(selectedIndex);
        if (GROUP_VISIBILITY.equals(selectedGroup)) cur.visible = copiedOptions.visible;
        else if (GROUP_POSE.equals(selectedGroup)) {
            cur.anchor = copiedOptions.anchor;
            cur.distance = copiedOptions.distance;
        } else if (GROUP_RESIZING.equals(selectedGroup)) cur.scale = copiedOptions.scale;
        else if (GROUP_REGION.equals(selectedGroup)) {
            cur.offsetX = copiedOptions.offsetX;
            cur.offsetY = copiedOptions.offsetY;
        } else if (GROUP_MISC.equals(selectedGroup)) cur.extraFlag = copiedOptions.flag;
        changesNotSaved = true;
        saveMessage = "Pasted " + selectedGroup;
        saveMessageTime = System.currentTimeMillis();
    }

    private void reloadOverlayFromDisk() {
        String selectedId = selectedIndex >= 0 && selectedIndex < overlays.size()
                ? overlays.get(selectedIndex).id : null;
        VROverlayConfig.load();
        VRClientSettings.load();
        loadOverlayData();
        selectedIndex = 0;
        if (selectedId != null) {
            for (int i = 0; i < overlays.size(); i++) {
                if (selectedId.equals(overlays.get(i).id)) { selectedIndex = i; break; }
            }
        }
        selectedGroup = null;
        confirmRemove = false;
        changesNotSaved = false;
        VRCustomOverlayRenderer.resetPoses();
        saveMessage = "Reloaded saved overlay settings";
        saveMessageTime = System.currentTimeMillis();
    }

    private String saveMessage = null;
    private long saveMessageTime = 0L;

    // Create tab state
    private int selectedTemplate = 0;

    public GuiSettingsOverlays(GuiScreen parent) {
        this.parentScreen = parent;
        loadOverlayData();
    }

    private static String nameOf(String id) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(id);
        if (entry.custom && entry.name != null && !entry.name.trim().isEmpty()) return entry.name;
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
            default: return id;
        }
    }

    private static String descriptionOf(String id) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(id);
        if (entry.custom) {
            if (entry.description != null && !entry.description.trim().isEmpty()) return entry.description;
            return "Custom copy of the " + nameOf(entry.template) + " overlay template.";
        }
        switch (id) {
            case VROverlayConfig.HUD: return "Vanilla HUD with chat, effects and notifications floating in front of you.";
            case VROverlayConfig.HOTBAR_MAIN: return "Radial 3x3 hotbar opened with the main hand grip.";
            case VROverlayConfig.HOTBAR_OFFHAND: return "Radial 3x3 hotbar opened with the off hand grip.";
            case VROverlayConfig.GAME_SCREEN: return "Floating screen for inventories, chests, crafting and menus.";
            case VROverlayConfig.KEYBOARD: return "Virtual keyboard for chat, signs and search fields.";
            case VROverlayConfig.CROSSHAIR: return "3D '+' crosshair and laser. Anchor: MAIN_HAND or OFFHAND shows one hand, others show both.";
            case VROverlayConfig.STATUS: return "Health, hunger, armor, air and experience status HUD attached to the left wrist / forearm.";
            case VROverlayConfig.MAP: return "Real-time JourneyMap minimap attached to the right wrist / forearm.";
            case VROverlayConfig.INFO: return "Coordinates, biome, time, light level and FPS from top-left HUD attached to your arm.";
            case VROverlayConfig.POTIONS: return "Active potion effects, icons and duration countdowns from bottom-right HUD attached to your arm.";
            default: return "Inventory on the back of the off hand: turn the wrist towards your face. Trigger = click, A = right click, off hand trigger = shift click.";
        }
    }

    /** Controls shown for an overlay, in order. */
    private static String[] rowsFor(String id) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(id);
        if (entry.custom) id = entry.template;
        switch (id) {
            case VROverlayConfig.HUD:
            case VROverlayConfig.HOTBAR_MAIN:
            case VROverlayConfig.HOTBAR_OFFHAND:
                return new String[] {"VIS", "ANCHOR", "SCALE", "DIST", "OFFX", "OFFY", "FLAG"};
            case VROverlayConfig.GAME_SCREEN:
                return new String[] {"VIS", "ANCHOR", "SCALE", "DIST", "OFFX", "OFFY"};
            case VROverlayConfig.KEYBOARD:
                return new String[] {"VIS", "SCALE", "DIST"};
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
        VROverlayConfig.Entry entry = VROverlayConfig.get(id);
        if (entry.custom) id = entry.template;
        switch (id) {
            case VROverlayConfig.HUD: return "Curved HUD";
            case VROverlayConfig.CROSSHAIR: return "Laser Beam";
            default: return "Slot Numbers";
        }
    }

    private void loadOverlayData() {
        overlays.clear();
        for (String id : VROverlayConfig.getAllIds()) {
            VROverlayConfig.Entry config = VROverlayConfig.get(id);
            OverlayEntry overlay = new OverlayEntry(id, nameOf(id), descriptionOf(id), config.anchor,
                    config.visible, config.scale, config.distance, 1.0F, config.flag);
            overlay.offsetX = config.offsetX;
            overlay.offsetY = config.offsetY;
            overlay.custom = config.custom;
            overlay.template = config.template;

            // Keep legacy settings screens and overlay setup in sync for built-in overlays.
            // User-created copies must never overwrite the source overlay's global settings.
            if (!config.custom) {
                switch (id) {
                    case VROverlayConfig.HUD:
                        overlay.scale = VRClientSettings.hudScale;
                        overlay.distance = VRClientSettings.hudDistance;
                        overlay.extraFlag = VRClientSettings.curvedHud;
                        break;
                    case VROverlayConfig.HOTBAR_MAIN:
                    case VROverlayConfig.HOTBAR_OFFHAND:
                        overlay.extraFlag = VRClientSettings.hotbarSlotNumbers;
                        break;
                    case VROverlayConfig.CROSSHAIR:
                        overlay.visible = VRClientSettings.vrCrosshair;
                        overlay.distance = VRClientSettings.interactionReach;
                        overlay.extraFlag = VRClientSettings.crosshairLaser;
                        break;
                    default:
                        break;
                }
            }
            overlays.add(overlay);
        }
        selectedIndex = overlays.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, overlays.size() - 1));
        overlayListScroll = Math.max(0, overlayListScroll);
        overlayDetailScroll = Math.max(0, overlayDetailScroll);
        changesNotSaved = false;
    }

    private void applyOverlayData() {
        for (OverlayEntry overlay : overlays) {
            VROverlayConfig.Entry config = VROverlayConfig.get(overlay.id);
            config.visible = overlay.visible;
            config.anchor = overlay.anchor;
            config.scale = overlay.scale;
            config.distance = overlay.distance;
            config.offsetX = overlay.offsetX;
            config.offsetY = overlay.offsetY;
            config.flag = overlay.extraFlag;
            if (overlay.custom) {
                config.custom = true;
                config.name = overlay.name;
                config.description = overlay.description;
                config.template = overlay.template;
            } else {
                switch (overlay.id) {
                    case VROverlayConfig.HUD:
                        VRClientSettings.hudScale = overlay.scale;
                        VRClientSettings.hudDistance = overlay.distance;
                        VRClientSettings.curvedHud = overlay.extraFlag;
                        break;
                    case VROverlayConfig.HOTBAR_MAIN:
                    case VROverlayConfig.HOTBAR_OFFHAND:
                        VRClientSettings.hotbarSlotNumbers = overlay.extraFlag;
                        break;
                    case VROverlayConfig.CROSSHAIR:
                        VRClientSettings.vrCrosshair = overlay.visible;
                        VRClientSettings.interactionReach = overlay.distance;
                        VRClientSettings.crosshairLaser = overlay.extraFlag;
                        break;
                    default:
                        break;
                }
            }
        }
        VROverlayConfig.save();
        VRClientSettings.save();
        changesNotSaved = false;

        // Pose caches are reset only after Save, just like Visor applies saved option groups.
        VRHudRenderer.resetSmoothing();
        VRVirtualScreen.getInstance().resetOrientation();
        VRVirtualKeyboard.getInstance().resetDefaultPose();
        VRCustomOverlayRenderer.resetPoses();

        saveMessage = I18n.format("visor.overlay.options.overlays.save") + " OK!";
        saveMessageTime = System.currentTimeMillis();
    }

    private void resetOverlayDefaults() {
        if (selectedIndex < 0 || selectedIndex >= overlays.size()) return;
        OverlayEntry overlay = overlays.get(selectedIndex);
        VROverlayConfig.Entry defaults = VROverlayConfig.defaults(overlay.custom ? overlay.template : overlay.id);
        overlay.anchor = defaults.anchor;
        overlay.visible = defaults.visible;
        overlay.scale = defaults.scale;
        overlay.distance = defaults.distance;
        overlay.offsetX = defaults.offsetX;
        overlay.offsetY = defaults.offsetY;
        overlay.extraFlag = defaults.flag;
        changesNotSaved = true;
        if (VROverlayConfig.HOTBAR_MAIN.equals(overlay.id)
                || VROverlayConfig.HOTBAR_OFFHAND.equals(overlay.id)) {
            overlay.extraFlag = false;
        }
        overlayDetailScroll = 0;
        confirmRemove = false;
    }

    @Override
    public void initGui() {
        scaleHelper.computeScale(this.width, this.height);
        if (overlaySearchField == null) {
            overlaySearchField = new GuiTextField(0, this.fontRendererObj, 0, 0, 1, 1);
            overlaySearchField.setMaxStringLength(64);
        }
        if (customNameField == null) {
            customNameField = new GuiTextField(1, this.fontRendererObj, 0, 0, 1, 1);
            customNameField.setMaxStringLength(48);
        }
        if (customDescriptionField == null) {
            customDescriptionField = new GuiTextField(2, this.fontRendererObj, 0, 0, 1, 1);
            customDescriptionField.setMaxStringLength(120);
        }
        if (templateSearchField == null) {
            templateSearchField = new GuiTextField(3, this.fontRendererObj, 0, 0, 1, 1);
            templateSearchField.setMaxStringLength(64);
        }
        layoutTextFields();
    }

    private void layoutTextFields() {
        if (overlaySearchField != null) {
            overlaySearchField.xPosition = scaleHelper.scaledX(12);
            overlaySearchField.yPosition = scaleHelper.scaledY(35);
            overlaySearchField.width = scaleHelper.scaledSize(74);
            overlaySearchField.height = scaleHelper.scaledSize(15);
        }
        if (customNameField != null) {
            customNameField.xPosition = scaleHelper.scaledX(119);
            customNameField.yPosition = scaleHelper.scaledY(72);
            customNameField.width = scaleHelper.scaledSize(126);
            customNameField.height = scaleHelper.scaledSize(15);
        }
        if (customDescriptionField != null) {
            customDescriptionField.xPosition = scaleHelper.scaledX(119);
            customDescriptionField.yPosition = scaleHelper.scaledY(100);
            customDescriptionField.width = scaleHelper.scaledSize(126);
            customDescriptionField.height = scaleHelper.scaledSize(15);
        }
        if (templateSearchField != null) {
            templateSearchField.xPosition = scaleHelper.scaledX(14);
            templateSearchField.yPosition = scaleHelper.scaledY(35);
            templateSearchField.width = scaleHelper.scaledSize(96);
            templateSearchField.height = scaleHelper.scaledSize(15);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        scaleHelper.computeScale(this.width, this.height);
        layoutTextFields();

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
        int searchY = bgY + scaleHelper.scaledSize(35);
        int listY = bgY + scaleHelper.scaledSize(54);
        int listW = scaleHelper.scaledSize(96);
        int listH = scaleHelper.scaledSize(176);
        int itemH = scaleHelper.scaledSize(26);

        drawString(this.fontRendererObj, "\u00a77" + I18n.format("visor.overlay.options.overlays"),
                listX, listY - scaleHelper.scaledSize(7), 0xAAAAAA);

        // Search and filter entry point mirror Visor's searchable overlay list.
        if (overlaySearchField != null) {
            overlaySearchField.drawTextBox();
            if (overlaySearchField.getText().isEmpty() && !overlaySearchField.isFocused()) {
                drawString(this.fontRendererObj, "Find overlays", overlaySearchField.xPosition + scaleHelper.scaledSize(3),
                        overlaySearchField.yPosition + scaleHelper.scaledSize(4), 0xFF777777);
            }
        }
        int filterX = listX + scaleHelper.scaledSize(77);
        int filterW = scaleHelper.scaledSize(19);
        int filterH = scaleHelper.scaledSize(15);
        boolean filterHover = isHovered(mouseX, mouseY, filterX, searchY, filterW, filterH);
        drawRect(filterX, searchY, filterX + filterW, searchY + filterH,
                showFilterPanel ? 0xFF305080 : (filterHover ? 0xFF454545 : 0xFF222222));
        drawCenteredString(this.fontRendererObj, "F", filterX + filterW / 2,
                searchY + (filterH - 8) / 2, filterHover || showFilterPanel ? 0xFFFFFF : 0xAAAAAA);

        overlayListScroll = Math.max(0, Math.min(getMaxListScroll(), overlayListScroll));
        VRSettingsScreen.enableScissor(this.mc, listX, listY, listW, listH, this.width, this.height);
        List<OverlayEntry> visible = getVisibleOverlays();
        for (int i = 0; i < visible.size(); i++) {
            OverlayEntry entry = visible.get(i);
            int curY = listY + i * itemH - overlayListScroll;
            if (curY + itemH < listY || curY > listY + listH) continue;
            boolean hovered = isHovered(mouseX, mouseY, listX, curY, listW, itemH - scaleHelper.scaledSize(2))
                    && isHovered(mouseX, mouseY, listX, listY, listW, listH);
            boolean selected = selectedIndex >= 0 && selectedIndex < overlays.size()
                    && overlays.get(selectedIndex).id.equals(entry.id);
            int rowBottom = curY + itemH - scaleHelper.scaledSize(2);
            drawRect(listX, curY, listX + listW, rowBottom,
                    selected ? 0xFF305080 : (hovered ? 0xFF404040 : 0xFF202020));
            if (selected) drawRect(listX, curY, listX + scaleHelper.scaledSize(2), rowBottom, 0xFF26D9FF);
            if (entry.custom) drawRect(listX + scaleHelper.scaledSize(4), curY + scaleHelper.scaledSize(5),
                    listX + scaleHelper.scaledSize(6), curY + scaleHelper.scaledSize(18), 0xFF26D9FF);
            drawString(this.fontRendererObj, entry.name, listX + scaleHelper.scaledSize(entry.custom ? 9 : 6),
                    curY + scaleHelper.scaledSize(3), selected ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999));
            String sub = entry.anchor + " / " + (entry.visible ? "ON" : "OFF");
            drawString(this.fontRendererObj, "\u00a78" + sub, listX + scaleHelper.scaledSize(6),
                    curY + scaleHelper.scaledSize(14), 0x777777);
        }
        VRSettingsScreen.disableScissor();

        int maxListScroll = getMaxListScroll();
        if (maxListScroll > 0) {
            drawScrollbar(listX + listW + scaleHelper.scaledSize(2), listY,
                    scaleHelper.scaledSize(2), listH, overlayListScroll, maxListScroll);
        }

        // Compact in-canvas filter panel so the VR ray remains on the actual GUI plane.
        if (showFilterPanel) {
            int fx = listX + scaleHelper.scaledSize(1);
            int fy = listY + scaleHelper.scaledSize(2);
            int fw = listW - scaleHelper.scaledSize(2);
            int fh = scaleHelper.scaledSize(76);
            drawRect(fx, fy, fx + fw, fy + fh, 0xFF111820);
            drawRect(fx, fy, fx + fw, fy + scaleHelper.scaledSize(12), 0xFF26374A);
            drawString(this.fontRendererObj, "FILTERS", fx + scaleHelper.scaledSize(4),
                    fy + scaleHelper.scaledSize(2), 0xFFFFFFFF);
            String[] labels = {"Built-in overlays", "Custom overlays", "Have options", "No options"};
            boolean[] values = {filterBuiltIn, filterCustom, filterHasOptions, filterNoOptions};
            for (int i = 0; i < labels.length; i++) {
                int ry = fy + scaleHelper.scaledSize(14 + i * 15);
                drawRect(fx + scaleHelper.scaledSize(3), ry, fx + scaleHelper.scaledSize(12),
                        ry + scaleHelper.scaledSize(9), values[i] ? 0xFF26895B : 0xFF303030);
                drawString(this.fontRendererObj, values[i] ? "\u00a7a\u2713" : "\u00a77\u25a1",
                        fx + scaleHelper.scaledSize(4), ry, 0xFFFFFFFF);
                drawString(this.fontRendererObj, labels[i], fx + scaleHelper.scaledSize(15),
                        ry, values[i] ? 0xFFEEEEEE : 0xFF999999);
            }
        }

        if (selectedIndex < 0 || selectedIndex >= overlays.size()) return;
        OverlayEntry current = overlays.get(selectedIndex);
        int detailX = bgX + scaleHelper.scaledSize(118);
        int detailY = bgY + scaleHelper.scaledSize(36);
        int detailW = scaleHelper.scaledSize(106);
        int detailH = scaleHelper.scaledSize(176);
        overlayDetailScroll = Math.max(0, Math.min(getMaxDetailScroll(), overlayDetailScroll));

        VRSettingsScreen.enableScissor(this.mc, detailX, detailY, detailW + scaleHelper.scaledSize(6),
                detailH, this.width, this.height);
        if (selectedGroup == null) {
            drawString(this.fontRendererObj, "\u00a7f\u00a7l" + current.name, detailX,
                    detailY + scaleHelper.scaledSize(1) - overlayDetailScroll, 0xFFFFFF);
            String kind = current.custom ? "CUSTOM / " + nameOf(current.template).toUpperCase(Locale.ROOT)
                    : "BUILT-IN OVERLAY";
            drawString(this.fontRendererObj, "\u00a78" + kind, detailX,
                    detailY + scaleHelper.scaledSize(12) - overlayDetailScroll, 0x888888);

            int descY = detailY + scaleHelper.scaledSize(23) - overlayDetailScroll;
            int descLines = this.fontRendererObj.listFormattedStringToWidth("\u00a77" + current.description, detailW).size();
            this.fontRendererObj.drawSplitString("\u00a77" + current.description, detailX, descY, detailW, 0x888888);

            int optionsLabelY = descY + descLines * this.fontRendererObj.FONT_HEIGHT + scaleHelper.scaledSize(5);
            drawString(this.fontRendererObj, "\u00a77OPTION GROUPS", detailX, optionsLabelY, 0xAAAAAA);
            int groupY = optionsLabelY + scaleHelper.scaledSize(11);
            int groupStep = scaleHelper.scaledSize(16);
            String[] groups = groupsFor(current.id);
            for (int i = 0; i < groups.length; i++) {
                int gy = groupY + i * groupStep;
                boolean hover = isHovered(mouseX, mouseY, detailX, gy, detailW, scaleHelper.scaledSize(14));
                drawRect(detailX, gy, detailX + detailW, gy + scaleHelper.scaledSize(14),
                        hover ? 0xFF3C3C3C : 0xFF242424);
                drawString(this.fontRendererObj, groups[i], detailX + scaleHelper.scaledSize(4),
                        gy + scaleHelper.scaledSize(3), hover ? 0xFFFFFFFF : 0xFFCCCCCC);
                drawString(this.fontRendererObj, "\u00a7b>", detailX + detailW - scaleHelper.scaledSize(8),
                        gy + scaleHelper.scaledSize(3), 0xFF26D9FF);
            }
        } else {
            drawString(this.fontRendererObj, "\u00a7b< " + selectedGroup, detailX,
                    detailY + scaleHelper.scaledSize(2), 0xFFFFFFFF);
            if (GROUP_GENERAL.equals(selectedGroup)) {
                drawString(this.fontRendererObj, "\u00a77ID: \u00a7f" + current.id, detailX,
                        detailY + scaleHelper.scaledSize(26), 0xFFFFFFFF);
                drawString(this.fontRendererObj, "\u00a77Type: \u00a7f" + (current.custom ? "Custom copy" : "Built-in"),
                        detailX, detailY + scaleHelper.scaledSize(42), 0xFFFFFFFF);
                drawString(this.fontRendererObj, "\u00a77Template: \u00a7f" + nameOf(current.custom ? current.template : current.id),
                        detailX, detailY + scaleHelper.scaledSize(58), 0xFFFFFFFF);
                this.fontRendererObj.drawSplitString(current.description, detailX,
                        detailY + scaleHelper.scaledSize(78), detailW, 0xAAAAAA);
            } else {
                String[] rows = rowsForGroup(current.id, selectedGroup);
                int curY = detailY + scaleHelper.scaledSize(25) - overlayDetailScroll;
                int ctrlH = scaleHelper.scaledSize(14);
                int step = scaleHelper.scaledSize(18);
                for (String row : rows) {
                    boolean hover = isHovered(mouseX, mouseY, detailX, curY, detailW, ctrlH)
                            && isHovered(mouseX, mouseY, detailX, detailY, detailW, detailH);
                    switch (row) {
                        case "VIS":
                            drawRect(detailX, curY, detailX + detailW, curY + ctrlH, hover ? 0xFF404040 : 0xFF282828);
                            drawCenteredString(this.fontRendererObj,
                                    I18n.format("visor.overlay.options.visibility") + ": "
                                            + (current.visible ? "\u00a7aON" : "\u00a7cOFF"),
                                    detailX + detailW / 2, curY + (ctrlH - 8) / 2, 0xFFFFFF);
                            break;
                        case "ANCHOR":
                            drawRect(detailX, curY, detailX + detailW, curY + ctrlH, hover ? 0xFF404040 : 0xFF282828);
                            drawCenteredString(this.fontRendererObj, "Anchor: \u00a7b" + current.anchor,
                                    detailX + detailW / 2, curY + (ctrlH - 8) / 2, 0xFFFFFF);
                            break;
                        case "SCALE":
                            drawSlider(mouseX, detailX, curY, detailW, ctrlH, "Scale", current.scale, 0.2F, 3.0F, "%.2fx", 0);
                            break;
                        case "DIST":
                            drawSlider(mouseX, detailX, curY, detailW, ctrlH,
                                    VROverlayConfig.CROSSHAIR.equals(current.id) ? "Reach" : "Distance",
                                    current.distance, 0.2F, 6.0F, "%.2fm", 1);
                            break;
                        case "OFFX":
                            drawSlider(mouseX, detailX, curY, detailW, ctrlH, "Offset X", current.offsetX, -0.5F, 0.5F, "%.2fm", 2);
                            break;
                        case "OFFY":
                            drawSlider(mouseX, detailX, curY, detailW, ctrlH, "Offset Y", current.offsetY, -0.5F, 0.5F, "%.2fm", 3);
                            break;
                        default:
                            drawRect(detailX, curY, detailX + detailW, curY + ctrlH, hover ? 0xFF404040 : 0xFF282828);
                            drawCenteredString(this.fontRendererObj,
                                    flagLabelOf(current.id) + ": " + (current.extraFlag ? "\u00a7aON" : "\u00a77OFF"),
                                    detailX + detailW / 2, curY + (ctrlH - 8) / 2, 0xFFFFFF);
                            break;
                    }
                    curY += step;
                }

                // Keep action icons outside the detail-panel scissor, like Visor's side rail.
                VRSettingsScreen.disableScissor();
                int actionX = bgX + scaleHelper.scaledSize(234);
                int actionSize = scaleHelper.scaledSize(17);
                String[] actions = {"C", "P", "R"};
                int[] actionYs = {99, 122, 145};
                boolean[] enabled = {true, copiedOptions != null && selectedGroup.equals(copiedGroup), true};
                for (int i = 0; i < actions.length; i++) {
                    int ay = bgY + scaleHelper.scaledSize(actionYs[i]);
                    boolean hover = isHovered(mouseX, mouseY, actionX, ay, actionSize, actionSize);
                    int color = enabled[i] ? (hover ? 0xFF36536B : 0xFF202D38) : 0xFF202020;
                    drawRect(actionX, ay, actionX + actionSize, ay + actionSize, color);
                    drawCenteredString(this.fontRendererObj, actions[i], actionX + actionSize / 2,
                            ay + (actionSize - 8) / 2, enabled[i] ? 0xFF26D9FF : 0xFF666666);
                }
            }
        }
        VRSettingsScreen.disableScissor();

        // Fixed footer buttons: reset/save, or an explicit two-step removal confirmation.
        int btnY = bgY + scaleHelper.scaledSize(218);
        int halfW = (detailW - scaleHelper.scaledSize(4)) / 2;
        int btnH = scaleHelper.scaledSize(15);
        int saveX = detailX + halfW + scaleHelper.scaledSize(4);
        if (confirmRemove && current.custom) {
            drawRect(detailX, btnY, detailX + halfW, btnY + btnH, 0xFF702B2B);
            drawCenteredString(this.fontRendererObj, "Confirm Remove", detailX + halfW / 2,
                    btnY + (btnH - 8) / 2, 0xFFFFFFFF);
            drawRect(saveX, btnY, saveX + halfW, btnY + btnH, 0xFF383838);
            drawCenteredString(this.fontRendererObj, "Cancel", saveX + halfW / 2,
                    btnY + (btnH - 8) / 2, 0xFFFFFFFF);
        } else {
            boolean defaultsHover = isHovered(mouseX, mouseY, detailX, btnY, halfW, btnH);
            drawRect(detailX, btnY, detailX + halfW, btnY + btnH, defaultsHover ? 0xFF604040 : 0xFF402828);
            drawCenteredString(this.fontRendererObj, I18n.format("visor.overlay.options.overlays.load_defaults"),
                    detailX + halfW / 2, btnY + (btnH - 8) / 2, defaultsHover ? 0xFFFFA0 : 0xFFDDDDDD);
            boolean saveHover = isHovered(mouseX, mouseY, saveX, btnY, halfW, btnH);
            int saveColor = changesNotSaved ? (saveHover ? 0xFF8A651A : 0xFF624711)
                    : (saveHover ? 0xFF306030 : 0xFF204020);
            drawRect(saveX, btnY, saveX + halfW, btnY + btnH, saveColor);
            String saveLabel = changesNotSaved ? "* Save" : I18n.format("visor.overlay.options.overlays.save");
            drawCenteredString(this.fontRendererObj, saveLabel,
                    saveX + halfW / 2, btnY + (btnH - 8) / 2,
                    changesNotSaved ? 0xFFFFD780 : (saveHover ? 0xFFA0FFA0 : 0xFF88EE88));
        }

        if (current.custom) {
            int removeX = bgX + scaleHelper.scaledSize(234);
            int removeY = bgY + scaleHelper.scaledSize(193);
            int removeSize = scaleHelper.scaledSize(17);
            boolean removeHover = isHovered(mouseX, mouseY, removeX, removeY, removeSize, removeSize);
            drawRect(removeX, removeY, removeX + removeSize, removeY + removeSize,
                    removeHover || confirmRemove ? 0xFF702B2B : 0xFF382020);
            drawCenteredString(this.fontRendererObj, "\u00a7cX", removeX + removeSize / 2,
                    removeY + (removeSize - 8) / 2, 0xFFFFFFFF);
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
        int searchY = bgY + scaleHelper.scaledSize(35);
        int listY = bgY + scaleHelper.scaledSize(54);
        int listW = scaleHelper.scaledSize(96);
        int itemH = scaleHelper.scaledSize(24);

        drawString(this.fontRendererObj, "\u00a77SELECT TEMPLATE", listX,
                listY - scaleHelper.scaledSize(7), 0xAAAAAA);
        if (templateSearchField != null) {
            templateSearchField.drawTextBox();
            if (templateSearchField.getText().isEmpty() && !templateSearchField.isFocused()) {
                drawString(this.fontRendererObj, "Find templates", templateSearchField.xPosition + scaleHelper.scaledSize(3),
                        templateSearchField.yPosition + scaleHelper.scaledSize(4), 0xFF777777);
            }
        }

        String query = templateSearchField == null ? "" : templateSearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < TEMPLATES.length; i++) {
            String searchable = (TEMPLATES[i] + " " + templateDescription(TEMPLATE_IDS[i])).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            int visibleIndex = visibleTemplateIndexBefore(i, query);
            int rowY = listY + visibleIndex * itemH;
            boolean hovered = isHovered(mouseX, mouseY, listX, rowY, listW, itemH - scaleHelper.scaledSize(2));
            boolean selected = i == selectedTemplate;
            drawRect(listX, rowY, listX + listW, rowY + itemH - scaleHelper.scaledSize(2),
                    selected ? 0xFF305080 : (hovered ? 0xFF404040 : 0xFF202020));
            if (selected) drawRect(listX, rowY, listX + scaleHelper.scaledSize(2),
                    rowY + itemH - scaleHelper.scaledSize(2), 0xFF26D9FF);
            drawString(this.fontRendererObj, TEMPLATES[i], listX + scaleHelper.scaledSize(6),
                    rowY + scaleHelper.scaledSize(7), selected ? 0xFFFFFFFF : 0xFFCCCCCC);
        }

        int detailX = bgX + scaleHelper.scaledSize(118);
        int detailY = bgY + scaleHelper.scaledSize(40);
        int detailW = scaleHelper.scaledSize(128);
        drawString(this.fontRendererObj, "\u00a7f\u00a7lCreate Overlay", detailX, detailY, 0xFFFFFFFF);
        drawString(this.fontRendererObj, "\u00a77Display name", detailX, bgY + scaleHelper.scaledSize(61), 0xFFAAAAAA);
        if (customNameField != null) customNameField.drawTextBox();
        drawString(this.fontRendererObj, "\u00a77Description (optional)", detailX,
                bgY + scaleHelper.scaledSize(89), 0xFFAAAAAA);
        if (customDescriptionField != null) customDescriptionField.drawTextBox();

        int infoY = bgY + scaleHelper.scaledSize(122);
        drawString(this.fontRendererObj, "\u00a77Template behaviour", detailX, infoY, 0xFFAAAAAA);
        this.fontRendererObj.drawSplitString(templateDescription(TEMPLATE_IDS[selectedTemplate]), detailX,
                infoY + scaleHelper.scaledSize(11), detailW, 0x999999);

        int createY = bgY + scaleHelper.scaledSize(218);
        int createH = scaleHelper.scaledSize(16);
        boolean createHover = isHovered(mouseX, mouseY, detailX, createY, detailW, createH);
        boolean valid = customNameField != null && !customNameField.getText().trim().isEmpty();
        drawRect(detailX, createY, detailX + detailW, createY + createH,
                !valid ? 0xFF252525 : (createHover ? 0xFF306030 : 0xFF204020));
        drawCenteredString(this.fontRendererObj, "\u00a7a+ Create Overlay", detailX + detailW / 2,
                createY + (createH - 8) / 2, valid ? 0xFFFFFFFF : 0xFF777777);
    }

    private static String templateDescription(String template) {
        if (VROverlayConfig.GAME_SCREEN.equals(template)) {
            return "Independent copy of the live GUI texture for inventory, crafting, AE2 and machine screens.";
        }
        if (VROverlayConfig.KEYBOARD.equals(template)) {
            return "Independent copy of the virtual keyboard texture, with its own placement and scale.";
        }
        return "Independent copy of the captured HUD texture. Its position, anchor, scale and curvature are saved separately.";
    }

    private int visibleTemplateIndexBefore(int templateIndex, String query) {
        int count = 0;
        for (int i = 0; i < templateIndex; i++) {
            String searchable = (TEMPLATES[i] + " " + templateDescription(TEMPLATE_IDS[i])).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) count++;
        }
        return count;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        int bgX = scaleHelper.getStartX();
        int bgY = scaleHelper.getStartY();
        int tabW = scaleHelper.scaledSize(115);
        int tabH = scaleHelper.scaledSize(23);
        int tabY = bgY + scaleHelper.scaledSize(6);

        if (currentTab == Tab.OVERLAYS) {
            if (isHovered(mouseX, mouseY, bgX + tabW, tabY, tabW, tabH)) {
                playClickSound();
                currentTab = Tab.CREATE_OVERLAY;
                showFilterPanel = false;
                overlaySearchField.setFocused(false);
                customNameField.setFocused(false);
                customDescriptionField.setFocused(false);
                templateSearchField.setFocused(false);
                return;
            }
        } else if (isHovered(mouseX, mouseY, bgX, tabY, tabW, tabH)) {
            playClickSound();
            currentTab = Tab.OVERLAYS;
            customNameField.setFocused(false);
            customDescriptionField.setFocused(false);
            templateSearchField.setFocused(false);
            return;
        }

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
            if (overlaySearchField != null) overlaySearchField.mouseClicked(mouseX, mouseY, button);
            customNameField.setFocused(false);
            customDescriptionField.setFocused(false);
            templateSearchField.setFocused(false);
            handleOverlaysClick(mouseX, mouseY, bgX, bgY);
        } else {
            if (templateSearchField != null) templateSearchField.mouseClicked(mouseX, mouseY, button);
            if (customNameField != null) customNameField.mouseClicked(mouseX, mouseY, button);
            if (customDescriptionField != null) customDescriptionField.mouseClicked(mouseX, mouseY, button);
            if (overlaySearchField != null) overlaySearchField.setFocused(false);
            handleCreateClick(mouseX, mouseY, bgX, bgY);
        }
    }

    private void handleOverlaysClick(int mouseX, int mouseY, int bgX, int bgY) {
        int listX = bgX + scaleHelper.scaledSize(12);
        int searchY = bgY + scaleHelper.scaledSize(35);
        int listY = bgY + scaleHelper.scaledSize(54);
        int listW = scaleHelper.scaledSize(96);
        int listH = scaleHelper.scaledSize(176);
        int itemH = scaleHelper.scaledSize(26);
        int filterX = listX + scaleHelper.scaledSize(77);
        int filterW = scaleHelper.scaledSize(19);
        int filterH = scaleHelper.scaledSize(15);

        if (isHovered(mouseX, mouseY, filterX, searchY, filterW, filterH)) {
            showFilterPanel = !showFilterPanel;
            playClickSound();
            return;
        }

        if (showFilterPanel) {
            int fx = listX + scaleHelper.scaledSize(1);
            int fy = listY + scaleHelper.scaledSize(2);
            int fw = listW - scaleHelper.scaledSize(2);
            int fh = scaleHelper.scaledSize(76);
            if (isHovered(mouseX, mouseY, fx, fy, fw, fh)) {
                for (int i = 0; i < 4; i++) {
                    int rowY = fy + scaleHelper.scaledSize(14 + i * 15);
                    if (!isHovered(mouseX, mouseY, fx, rowY, fw, scaleHelper.scaledSize(13))) continue;
                    if (i == 0) filterBuiltIn = !filterBuiltIn;
                    else if (i == 1) filterCustom = !filterCustom;
                    else if (i == 2) filterHasOptions = !filterHasOptions;
                    else filterNoOptions = !filterNoOptions;
                    overlayListScroll = 0;
                    playClickSound();
                    return;
                }
                return;
            }
            showFilterPanel = false;
        }

        // Select from the *filtered* list, mapping the displayed row back to its real entry.
        if (isHovered(mouseX, mouseY, listX, listY, listW, listH)) {
            List<OverlayEntry> visible = getVisibleOverlays();
            for (int i = 0; i < visible.size(); i++) {
                int rowY = listY + i * itemH - overlayListScroll;
                if (isHovered(mouseX, mouseY, listX, rowY, listW, itemH - scaleHelper.scaledSize(2))) {
                    selectedIndex = overlays.indexOf(visible.get(i));
                    selectedGroup = null;
                    overlayDetailScroll = 0;
                    confirmRemove = false;
                    playClickSound();
                    return;
                }
            }
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= overlays.size()) return;
        OverlayEntry current = overlays.get(selectedIndex);
        int detailX = bgX + scaleHelper.scaledSize(118);
        int detailY = bgY + scaleHelper.scaledSize(36);
        int detailW = scaleHelper.scaledSize(106);
        int detailH = scaleHelper.scaledSize(176);
        int btnY = bgY + scaleHelper.scaledSize(218);
        int btnH = scaleHelper.scaledSize(15);
        int halfW = (detailW - scaleHelper.scaledSize(4)) / 2;
        int saveX = detailX + halfW + scaleHelper.scaledSize(4);

        // In the group overview, selecting an entry opens the dedicated options group.
        if (selectedGroup == null && isHovered(mouseX, mouseY, detailX, detailY, detailW, detailH)) {
            int descLines = this.fontRendererObj.listFormattedStringToWidth("\u00a77" + current.description, detailW).size();
            int optionsLabelY = detailY + scaleHelper.scaledSize(23)
                    + descLines * this.fontRendererObj.FONT_HEIGHT + scaleHelper.scaledSize(5);
            int groupY = optionsLabelY + scaleHelper.scaledSize(11) - overlayDetailScroll;
            int groupStep = scaleHelper.scaledSize(16);
            String[] groups = groupsFor(current.id);
            for (int i = 0; i < groups.length; i++) {
                int rowY = groupY + i * groupStep;
                if (isHovered(mouseX, mouseY, detailX, rowY, detailW, scaleHelper.scaledSize(14))) {
                    selectedGroup = groups[i];
                    overlayDetailScroll = 0;
                    confirmRemove = false;
                    playClickSound();
                    return;
                }
            }
        } else if (selectedGroup != null) {
            // Clicking the group header returns to the overlay identity/setup card.
            if (isHovered(mouseX, mouseY, detailX, detailY, detailW, scaleHelper.scaledSize(20))) {
                selectedGroup = null;
                overlayDetailScroll = 0;
                playClickSound();
                return;
            }

            // Copy / paste / reload controls use the right-side texture rail.
            if (!GROUP_GENERAL.equals(selectedGroup)) {
                int actionX = bgX + scaleHelper.scaledSize(234);
                int actionSize = scaleHelper.scaledSize(17);
                int[] actionYs = {99, 122, 145};
                for (int i = 0; i < actionYs.length; i++) {
                    int actionY = bgY + scaleHelper.scaledSize(actionYs[i]);
                    if (!isHovered(mouseX, mouseY, actionX, actionY, actionSize, actionSize)) continue;
                    if (i == 0) copyCurrentGroup();
                    else if (i == 1) pasteCurrentGroup();
                    else reloadOverlayFromDisk();
                    playClickSound();
                    return;
                }
            }

            if (!GROUP_GENERAL.equals(selectedGroup)
                    && isHovered(mouseX, mouseY, detailX, detailY, detailW, detailH)) {
                int rowY = detailY + scaleHelper.scaledSize(25) - overlayDetailScroll;
                int ctrlH = scaleHelper.scaledSize(14);
                int step = scaleHelper.scaledSize(18);
                for (String row : rowsForGroup(current.id, selectedGroup)) {
                    if (isHovered(mouseX, mouseY, detailX, rowY, detailW, ctrlH)) {
                        switch (row) {
                            case "VIS":
                                current.visible = !current.visible;
                                changesNotSaved = true;
                                playClickSound();
                                break;
                            case "ANCHOR":
                                cycleAnchor(current);
                                changesNotSaved = true;
                                playClickSound();
                                break;
                            case "SCALE":
                                activeSlider = 0;
                                updateSliderFromMouse(mouseX, detailX, detailW, current, 0);
                                changesNotSaved = true;
                                break;
                            case "DIST":
                                activeSlider = 1;
                                updateSliderFromMouse(mouseX, detailX, detailW, current, 1);
                                changesNotSaved = true;
                                break;
                            case "OFFX":
                                activeSlider = 2;
                                updateSliderFromMouse(mouseX, detailX, detailW, current, 2);
                                changesNotSaved = true;
                                break;
                            case "OFFY":
                                activeSlider = 3;
                                updateSliderFromMouse(mouseX, detailX, detailW, current, 3);
                                changesNotSaved = true;
                                break;
                            default:
                                current.extraFlag = !current.extraFlag;
                                changesNotSaved = true;
                                playClickSound();
                                break;
                        }
                        return;
                    }
                    rowY += step;
                }
            }
        }

        // Remove custom overlays only after a second, explicit confirmation.
        if (current.custom) {
            int removeX = bgX + scaleHelper.scaledSize(234);
            int removeY = bgY + scaleHelper.scaledSize(193);
            int removeSize = scaleHelper.scaledSize(17);
            if (isHovered(mouseX, mouseY, removeX, removeY, removeSize, removeSize)) {
                confirmRemove = !confirmRemove;
                playClickSound();
                return;
            }
        }

        if (isHovered(mouseX, mouseY, detailX, btnY, halfW, btnH)) {
            if (confirmRemove && current.custom) {
                String removedId = current.id;
                VROverlayConfig.removeCustom(removedId);
                overlays.remove(selectedIndex);
                selectedIndex = overlays.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, overlays.size() - 1));
                selectedGroup = null;
                confirmRemove = false;
                VRCustomOverlayRenderer.resetPoses();
                playClickSound();
            } else {
                resetOverlayDefaults();
                playClickSound();
            }
            return;
        }
        if (isHovered(mouseX, mouseY, saveX, btnY, halfW, btnH)) {
            if (confirmRemove && current.custom) {
                confirmRemove = false;
            } else {
                applyOverlayData();
                playClickSound();
            }
        }
    }

    private void handleCreateClick(int mouseX, int mouseY, int bgX, int bgY) {
        int listX = bgX + scaleHelper.scaledSize(14);
        int listY = bgY + scaleHelper.scaledSize(54);
        int listW = scaleHelper.scaledSize(96);
        int itemH = scaleHelper.scaledSize(24);
        String query = templateSearchField == null ? "" : templateSearchField.getText().trim().toLowerCase(Locale.ROOT);

        for (int i = 0; i < TEMPLATES.length; i++) {
            String searchable = (TEMPLATES[i] + " " + templateDescription(TEMPLATE_IDS[i])).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            int row = visibleTemplateIndexBefore(i, query);
            int rowY = listY + row * itemH;
            if (isHovered(mouseX, mouseY, listX, rowY, listW, itemH - scaleHelper.scaledSize(2))) {
                selectedTemplate = i;
                playClickSound();
                return;
            }
        }

        int detailX = bgX + scaleHelper.scaledSize(118);
        int createY = bgY + scaleHelper.scaledSize(218);
        int detailW = scaleHelper.scaledSize(128);
        int createH = scaleHelper.scaledSize(16);
        if (isHovered(mouseX, mouseY, detailX, createY, detailW, createH)) {
            String name = customNameField == null ? "" : customNameField.getText().trim();
            if (name.isEmpty()) {
                saveMessage = "Enter a display name first";
                saveMessageTime = System.currentTimeMillis();
                return;
            }
            String description = customDescriptionField == null ? "" : customDescriptionField.getText().trim();
            String id = VROverlayConfig.createCustom(name, description, TEMPLATE_IDS[selectedTemplate]);
            if (id == null) {
                saveMessage = "Could not create overlay";
                saveMessageTime = System.currentTimeMillis();
                return;
            }
            VROverlayConfig.Entry created = VROverlayConfig.get(id);
            OverlayEntry createdRow = new OverlayEntry(id, name, description, created.anchor,
                    created.visible, created.scale, created.distance, 1.0F, created.flag);
            createdRow.offsetX = created.offsetX;
            createdRow.offsetY = created.offsetY;
            createdRow.custom = true;
            createdRow.template = created.template;
            overlays.add(createdRow);
            selectedIndex = overlays.size() - 1;
            if (overlaySearchField != null) overlaySearchField.setText("");
            filterBuiltIn = true;
            filterCustom = true;
            filterHasOptions = true;
            filterNoOptions = true;
            showFilterPanel = false;
            selectedGroup = null;
            confirmRemove = false;
            currentTab = Tab.OVERLAYS;
            customNameField.setText("");
            customDescriptionField.setText("");
            templateSearchField.setText("");
            templateSearchField.setFocused(false);
            customNameField.setFocused(false);
            customDescriptionField.setFocused(false);
            saveMessage = "Created " + name;
            saveMessageTime = System.currentTimeMillis();
            playClickSound();
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
    public void updateScreen() {
        super.updateScreen();
        if (overlaySearchField != null) overlaySearchField.updateCursorCounter();
        if (customNameField != null) customNameField.updateCursorCounter();
        if (customDescriptionField != null) customDescriptionField.updateCursorCounter();
        if (templateSearchField != null) templateSearchField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(parentScreen);
            return;
        }

        if (currentTab == Tab.OVERLAYS && overlaySearchField != null && overlaySearchField.isFocused()) {
            if (overlaySearchField.textboxKeyTyped(typedChar, keyCode)) overlayListScroll = 0;
            return;
        }
        if (currentTab == Tab.CREATE_OVERLAY) {
            if (customNameField != null && customNameField.isFocused()) {
                customNameField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
            if (customDescriptionField != null && customDescriptionField.isFocused()) {
                customDescriptionField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
            if (templateSearchField != null && templateSearchField.isFocused()) {
                templateSearchField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
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
