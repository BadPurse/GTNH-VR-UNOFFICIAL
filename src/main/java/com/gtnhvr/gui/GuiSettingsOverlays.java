package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Visor-style overlay setup for the legacy GTNH 1.7.10 client.
 *
 * This screen is deliberately independent of the previous large, hand-drawn
 * overlay editor.  Its interaction model follows Visor's SetupOverlayWidgetSet:
 * select an overlay, select an option group, edit that group's properties,
 * preview changes live, then explicitly save or discard them.
 *
 * Minecraft 1.7.10 does not have Visor's generic addon/overlay registry or its
 * modern widget framework, so the groups below are backed by the settings the
 * GTNH-VR renderers actually consume.
 */
@Lwjgl3Aware
public class GuiSettingsOverlays extends GuiScreen {
    private static final int UI_WIDTH = 257;
    private static final int UI_HEIGHT = 256;

    private static final ResourceLocation BACKGROUND_OVERLAYS =
            new ResourceLocation("gtnhvr", "visor/textures/gui/overlays/settings/bg_main_1.png");
    private static final ResourceLocation BACKGROUND_CREATE =
            new ResourceLocation("gtnhvr", "visor/textures/gui/overlays/settings/bg_main_2.png");
    private static final ResourceLocation BACKGROUND_GROUP =
            new ResourceLocation("gtnhvr", "visor/textures/gui/overlays/settings/bg_general_128x256.png");
    private static final ResourceLocation BACKGROUND_EXTRA =
            new ResourceLocation("gtnhvr", "visor/textures/gui/overlays/settings/bg_main_extra_1.png");
    private static final ResourceLocation GENERAL_SPRITES =
            new ResourceLocation("gtnhvr", "visor/textures/gui/overlays/settings/general_1.png");

    private static final String[] TEMPLATE_IDS = {
            VROverlayConfig.HUD, VROverlayConfig.GAME_SCREEN, VROverlayConfig.KEYBOARD
    };
    private static final String[] TEMPLATE_NAMES = { "HUD", "Game Screen", "Virtual Keyboard" };

    private static GuiSettingsOverlays activeInstance;

    private enum Tab { OVERLAYS, CREATE }
    private enum Group { GENERAL, VISIBILITY, POSE, RESIZING, MISC }

    private final GuiScreen parentScreen;
    private final GuiScaleHelper scaleHelper = new GuiScaleHelper(UI_WIDTH, UI_HEIGHT);
    private final Map<String, VROverlayConfig.Entry> originalValues = new LinkedHashMap<>();
    private final List<String> originalIds = new ArrayList<>();

    private Tab tab = Tab.OVERLAYS;
    private Group group = Group.POSE;
    private String selectedId = VROverlayConfig.HUD;
    private int listScroll;
    private int templateIndex;
    private boolean showFilters;
    private boolean filterBuiltIn = true;
    private boolean filterCustom = true;
    private boolean confirmRemove;
    private boolean confirmDiscard;
    private boolean draggingScreen;
    private boolean poseEditor;
    private boolean previewEnabled = true;
    private boolean dirty;
    private int activeSlider = -1;
    private String statusText;
    private long statusUntil;
    private VROverlayConfig.Entry copiedGroup;
    private Group copiedGroupType;

    private GuiTextField searchField;
    private GuiTextField createNameField;
    private GuiTextField createDescriptionField;
    private GuiTextField customNameField;
    private GuiTextField customDescriptionField;

    private int mouseLogicalX;
    private int mouseLogicalY;

    public GuiSettingsOverlays(GuiScreen parent) {
        this.parentScreen = parent;
        activeInstance = this;
        takeSnapshot();
        refreshSelectedFields();
    }

    /** Used by OpenXRRuntime to keep the selected target visible while this editor is open. */
    public static String getActivePreviewOverlayId() {
        return activeInstance == null ? null : activeInstance.selectedId;
    }

    public static boolean isSetupScreenOpen() {
        return activeInstance != null;
    }

    public static boolean isLivePreviewEnabled() {
        return activeInstance != null && activeInstance.poseEditor && activeInstance.previewEnabled;
    }

    @Override
    public void initGui() {
        scaleHelper.computeScale(width, height);
        searchField = new GuiTextField(fontRendererObj, 8, 35, 94, 12);
        searchField.setMaxStringLength(80);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setTextColor(0xFFFFFFFF);
        searchField.setDisabledTextColour(0xFF777777);
        createNameField = new GuiTextField(fontRendererObj, 76, 75, 164, 14);
        createNameField.setMaxStringLength(40);
        createDescriptionField = new GuiTextField(fontRendererObj, 76, 98, 164, 14);
        createDescriptionField.setMaxStringLength(120);
        customNameField = new GuiTextField(fontRendererObj, 118, 146, 126, 13);
        customNameField.setMaxStringLength(40);
        customDescriptionField = new GuiTextField(fontRendererObj, 118, 165, 126, 13);
        customDescriptionField.setMaxStringLength(120);
        refreshSelectedFields();
    }

    @Override
    public void onGuiClosed() {
        if (activeInstance == this) activeInstance = null;
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (searchField != null) searchField.updateCursorCounter();
        if (createNameField != null) createNameField.updateCursorCounter();
        if (createDescriptionField != null) createDescriptionField.updateCursorCounter();
        if (customNameField != null) customNameField.updateCursorCounter();
        if (customDescriptionField != null) customDescriptionField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        scaleHelper.computeScale(width, height);
        mouseLogicalX = toLogicalX(mouseX);
        mouseLogicalY = toLogicalY(mouseY);

        drawDefaultBackground();
        GL11.glPushMatrix();
        GL11.glTranslatef(scaleHelper.getStartX(), scaleHelper.getStartY(), 0.0F);
        GL11.glScalef(scaleHelper.getScale(), scaleHelper.getScale(), 1.0F);
        drawTexture(tab == Tab.CREATE ? BACKGROUND_CREATE : BACKGROUND_OVERLAYS, 0, 0, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);
        if (tab == Tab.OVERLAYS) {
            if (poseEditor) drawPoseEditor(mouseLogicalX, mouseLogicalY);
            else drawOverlayTab(mouseLogicalX, mouseLogicalY, partialTicks);
        } else drawCreateTab(mouseLogicalX, mouseLogicalY);
        drawTopNavigation(mouseLogicalX, mouseLogicalY);
        if (confirmRemove) drawConfirmation("Remove this custom overlay?", "REMOVE", "CANCEL");
        if (confirmDiscard) drawConfirmation("Discard unsaved changes?", "DISCARD", "KEEP EDITING");
        if (statusText != null && System.currentTimeMillis() < statusUntil) {
            drawCenteredString(fontRendererObj, statusText, UI_WIDTH / 2, 245, 0xFFBFE8FF);
        }
        GL11.glPopMatrix();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTopNavigation(int mx, int my) {
        // Use the original Visor atlas sprites and placement for the tab, close and drag controls.
        if (tab == Tab.OVERLAYS) {
            drawString(fontRendererObj, "Overlays", 6, 13, 0xFFE2E2E2);
            drawTextureRegion(GENERAL_SPRITES, 115, 6, 115, 23, 64, 0, 115, 23, 179, 188);
            drawCenteredString(fontRendererObj, "Create Overlay", 172, 13, 0xFFD9D9D9);
        } else {
            drawString(fontRendererObj, "Create Overlay", 121, 13, 0xFFE2E2E2);
            drawTextureRegion(GENERAL_SPRITES, 0, 6, 115, 23, 64, 24, 115, 23, 179, 188);
            drawCenteredString(fontRendererObj, "Overlays", 57, 13, 0xFFD9D9D9);
        }
        drawTextureRegion(GENERAL_SPRITES, 235, 12, 19, 19, 110, 112, 34, 34, 179, 188);
        drawTextureRegion(GENERAL_SPRITES, 235, 35, 19, 19, 75, 152, 34, 34, 179, 188);
        if (hit(mx, my, 235, 35, 19, 19)) {
            drawPanel(235, 35, 19, 19, 0x335DBBFF);
        }
    }

    private void drawOverlayTab(int mx, int my, float partialTicks) {
        drawPanel(6, 27, 101, 215, 0xB815171B);
        drawPanel(111, 27, 140, 215, 0xB8101115);
        drawString(fontRendererObj, "OVERLAYS", 9, 27, 0xFF9FCDFB);

        drawPanel(7, 33, 98, 15, 0xFF171A20);
        searchField.drawTextBox();
        drawButton(88, 33, 15, 14, showFilters ? "v" : "≡", showFilters, hit(mx, my, 88, 33, 15, 14));

        if (showFilters) {
            drawPanel(7, 49, 98, 28, 0xFF20242B);
            drawCheckbox(10, 52, "Built-in", filterBuiltIn);
            drawCheckbox(10, 64, "Custom", filterCustom);
        }

        int listY = showFilters ? 80 : 50;
        int listBottom = 190;
        drawPanel(7, listY, 98, listBottom - listY, 0xAA0A0B0E);
        List<String> visible = getVisibleIds();
        int rowHeight = 11;
        int visibleRows = Math.max(1, (listBottom - listY - 2) / rowHeight);
        int maxScroll = Math.max(0, visible.size() - visibleRows);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        for (int row = 0; row < visibleRows && row + listScroll < visible.size(); row++) {
            String id = visible.get(row + listScroll);
            int y = listY + 2 + row * rowHeight;
            boolean selected = id.equals(selectedId);
            if (selected) drawPanel(8, y, 96, rowHeight - 1, 0xFF315E84);
            else if (hit(mx, my, 8, y, 96, rowHeight - 1)) drawPanel(8, y, 96, rowHeight - 1, 0xFF2B3038);
            drawString(fontRendererObj, clip(nameOf(id), 17), 11, y + 2, selected ? 0xFFFFFFFF : 0xFFD2D4D8);
            drawString(fontRendererObj, VROverlayConfig.get(id).custom ? "C" : "•", 94, y + 2,
                    VROverlayConfig.get(id).custom ? 0xFF83D6A7 : 0xFF7D8793);
        }
        if (visible.isEmpty()) drawString(fontRendererObj, "No overlays found", 14, listY + 8, 0xFF969AA1);
        drawButton(7, 194, 98, 14, "Load from disk", false, hit(mx, my, 7, 194, 98, 14));
        drawButton(7, 210, 98, 14, "Load defaults", false, hit(mx, my, 7, 210, 98, 14));
        drawButton(7, 226, 98, 14, "Close", false, hit(mx, my, 7, 226, 98, 14));

        if (selectedId == null || !containsId(selectedId)) {
            drawCenteredString(fontRendererObj, "Select an overlay", 180, 54, 0xFFB5BAC2);
            return;
        }
        drawOverlayIdentity(mx, my);
        drawLivePreview(mx, my, partialTicks);
        drawGroupTabs(mx, my);
        drawSelectedGroup(mx, my);
        drawFooterActions(mx, my);
    }

    private void drawOverlayIdentity(int mx, int my) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
        drawPanel(115, 31, 132, 27, 0xFF181B20);
        drawPanel(119, 35, 18, 18, entry.custom ? 0xFF345D4D : 0xFF3A485A);
        drawCenteredString(fontRendererObj, entry.custom ? "C" : "V", 128, 40, 0xFFFFFFFF);
        drawString(fontRendererObj, clip(nameOf(selectedId), 19), 141, 35, 0xFFFFFFFF);
        drawString(fontRendererObj, clip("ID: " + selectedId, 20), 141, 45, 0xFFADB6C2);
        drawString(fontRendererObj, entry.custom ? "CUSTOM OVERLAY" : "BUILT-IN OVERLAY", 117, 60,
                entry.custom ? 0xFF83D6A7 : 0xFF9FCDFB);
    }

    private void drawLivePreview(int mx, int my, float partialTicks) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
        drawPanel(115, 71, 132, 44, 0xFF090A0D);
        drawString(fontRendererObj, "LIVE PREVIEW", 119, 73, 0xFF9DA5AF);
        int cx = 181;
        int cy = 94;
        float size = Math.max(0.30F, Math.min(1.0F, entry.scale / 2.2F));
        int w = Math.max(14, (int) (72 * size));
        int h = Math.max(8, (int) (25 * size));
        int x = cx - w / 2 + Math.round(entry.offsetX * 28.0F);
        int y = cy - h / 2 - Math.round(entry.offsetY * 25.0F);
        x = Math.max(118, Math.min(244 - w, x));
        y = Math.max(82, Math.min(108 - h, y));
        int alpha = Math.max(20, Math.min(220, (int) (entry.opacity * 220.0F)));
        int fill = (alpha << 24) | 0x002C3A4B;
        int edge = entry.visible ? 0xFF69B8F5 : 0xFFB65D61;
        drawPanel(x, y, w, h, fill);
        drawOutline(x, y, w, h, edge);
        drawPreviewContent(baseId(selectedId), x, y, w, h, entry.opacity);
        String poseText = anchorShort(entry.anchor) + "  " + String.format(Locale.ROOT, "%.2fm", entry.distance);
        drawString(fontRendererObj, clip(poseText, 22), 119, 105, entry.visible ? 0xFFD8E8F6 : 0xFFE79A9A);
        if (!entry.visible) drawCenteredString(fontRendererObj, "HIDDEN", x + w / 2, y + h / 2 - 3, 0xFFFFA0A0);
    }

    private void drawPreviewContent(String id, int x, int y, int w, int h, float opacity) {
        int a = Math.max(30, Math.min(220, (int) (opacity * 220.0F)));
        int c = (a << 24) | 0x003E627D;
        if (VROverlayConfig.HUD.equals(id) || VROverlayConfig.STATUS.equals(id)
                || VROverlayConfig.INFO.equals(id) || VROverlayConfig.POTIONS.equals(id)) {
            drawPanel(x + 3, y + 3, Math.max(3, w - 6), 2, c);
            drawPanel(x + 3, y + 7, Math.max(3, (w - 8) / 2), 2, c);
            drawPanel(x + 3, y + 11, Math.max(3, (w - 8) / 3), 2, c);
        } else if (VROverlayConfig.HOTBAR_MAIN.equals(id) || VROverlayConfig.HOTBAR_OFFHAND.equals(id)) {
            int slot = Math.max(2, (w - 8) / 3);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                drawOutline(x + 3 + col * (slot + 1), y + 3 + row * (slot + 1), slot, slot, c);
            }
        } else if (VROverlayConfig.CROSSHAIR.equals(id)) {
            drawPanel(x + w / 2 - 1, y + 2, 2, h - 4, c);
            drawPanel(x + 2, y + h / 2 - 1, w - 4, 2, c);
        } else if (VROverlayConfig.KEYBOARD.equals(id)) {
            for (int row = 0; row < 3; row++) {
                int count = row == 2 ? 5 : 8;
                int keyW = Math.max(2, (w - 8) / count);
                for (int col = 0; col < count; col++)
                    drawOutline(x + 3 + col * (keyW + 1), y + 3 + row * Math.max(3, (h - 6) / 3), keyW, 3, c);
            }
        } else {
            drawOutline(x + 4, y + 4, Math.max(3, w - 8), Math.max(3, h - 8), c);
        }
    }

    private void drawGroupTabs(int mx, int my) {
        drawString(fontRendererObj, "OPTION GROUP", 116, 118, 0xFF9DA5AF);
        String[] labels = { "General", "Visible", "Pose", "Size", "Misc" };
        Group[] groups = Group.values();
        int x = 115;
        for (int i = 0; i < groups.length; i++) {
            int tabW = i == 0 ? 29 : 25;
            boolean supported = groups[i] != Group.GENERAL || VROverlayConfig.get(selectedId).custom;
            drawButton(x, 128, tabW, 14, labels[i], group == groups[i], hit(mx, my, x, 128, tabW, 14), supported);
            x += tabW + 1;
        }
    }

    private void drawSelectedGroup(int mx, int my) {
        drawPanel(115, 144, 132, 73, 0xCC15181D);
        VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
        switch (group) {
            case GENERAL:
                drawString(fontRendererObj, "Overlay identity", 119, 146, 0xFFE8EDF2);
                if (entry.custom) {
                    drawString(fontRendererObj, "Name", 119, 157, 0xFFB2BAC5);
                    customNameField.drawTextBox();
                    drawString(fontRendererObj, "Description", 119, 177, 0xFFB2BAC5);
                    customDescriptionField.drawTextBox();
                } else {
                    drawWrapped("This overlay is supplied by GTNH-VR. Its identity is read-only.", 119, 160, 124, 9, 0xFFBEC4CC);
                }
                break;
            case VISIBILITY:
                drawString(fontRendererObj, "Visibility", 119, 148, 0xFFE8EDF2);
                drawToggle(119, 165, 120, 18, "Overlay enabled", entry.visible, mx, my, "visible");
                drawString(fontRendererObj, "Changes apply immediately.", 119, 191, 0xFF9DA5AF);
                break;
            case POSE:
                drawString(fontRendererObj, "Position and orientation", 119, 146, 0xFFE8EDF2);
                drawButton(119, 156, 123, 13, "Position: " + anchorLabel(entry.anchor), false,
                        hit(mx, my, 119, 156, 123, 13));
                drawSlider(119, 174, 123, "Distance", entry.distance, 0.1F, 6.0F,
                        String.format(Locale.ROOT, "%.2f m", entry.distance), 0, mx, my);
                drawButton(119, 197, 123, 14, "Open Pose Editor", false,
                        hit(mx, my, 119, 197, 123, 14));
                break;
            case RESIZING:
                drawString(fontRendererObj, "Overlay size", 119, 147, 0xFFE8EDF2);
                drawSlider(119, 170, 123, "Scale", entry.scale, 0.2F, 3.0F,
                        String.format(Locale.ROOT, "%.2fx", entry.scale), 3, mx, my);
                drawString(fontRendererObj, "Resize is reflected in preview.", 119, 199, 0xFF9DA5AF);
                break;
            case MISC:
                drawString(fontRendererObj, "Appearance", 119, 147, 0xFFE8EDF2);
                drawSlider(119, 170, 123, "Opacity", entry.opacity, 0.05F, 1.0F,
                        String.format(Locale.ROOT, "%.0f%%", entry.opacity * 100.0F), 4, mx, my);
                String flagName = flagLabelOf(selectedId);
                if (flagName != null) drawToggle(119, 190, 123, 14, flagName, entry.flag, mx, my, "flag");
                break;
        }
    }

    private void drawFooterActions(int mx, int my) {
        drawButton(115, 219, 40, 14, "Copy", copiedGroupType == group, hit(mx, my, 115, 219, 40, 14), canCopy(group));
        drawButton(157, 219, 40, 14, "Paste", false, hit(mx, my, 157, 219, 40, 14),
                copiedGroup != null && copiedGroupType == group && canCopy(group));
        drawButton(199, 219, 48, 14, "Reset", false, hit(mx, my, 199, 219, 48, 14));
        drawButton(115, 235, 64, 14, dirty ? "Save *" : "Save", dirty, hit(mx, my, 115, 235, 64, 14));
        drawButton(181, 235, 66, 14, VROverlayConfig.get(selectedId).custom ? "Remove" : "Revert",
                false, hit(mx, my, 181, 235, 66, 14));
    }

    private void drawPoseEditor(int mx, int my) {
        drawPanel(6, 27, 245, 215, 0xE8101216);
        drawOutline(6, 27, 245, 215, 0xFF56616D);
        drawString(fontRendererObj, "POSE EDITOR", 12, 31, 0xFF9FCDFB);
        drawString(fontRendererObj, clip(nameOf(selectedId), 28), 12, 43, 0xFFFFFFFF);
        drawString(fontRendererObj, "Independent position and rotation anchors", 12, 53, 0xFF9DA5AF);

        VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
        drawPanel(11, 64, 111, 78, 0xFF171B21);
        drawPanel(133, 64, 111, 78, 0xFF171B21);
        drawString(fontRendererObj, "POSITION", 15, 66, 0xFFB9DFFF);
        drawString(fontRendererObj, "ORIENTATION", 137, 66, 0xFFB9DFFF);
        drawButton(14, 79, 105, 14, anchorShort(entry.anchor), false, hit(mx, my, 14, 79, 105, 14));
        drawButton(136, 79, 105, 14, anchorShort(entry.rotationAnchor), false, hit(mx, my, 136, 79, 105, 14));
        drawSlider(12, 98, 110, "Forward / Z", entry.distance, 0.1F, 6.0F, String.format(Locale.ROOT, "%.2f m", entry.distance), 10, mx, my);
        drawSlider(12, 117, 110, "Horizontal / X", entry.offsetX, -1.0F, 1.0F, String.format(Locale.ROOT, "%.2f m", entry.offsetX), 11, mx, my);
        drawSlider(134, 98, 110, "Pitch / X", entry.rotationX, -180.0F, 180.0F, String.format(Locale.ROOT, "%.0f°", entry.rotationX), 13, mx, my);
        drawSlider(134, 117, 110, "Yaw / Y", entry.rotationY, -180.0F, 180.0F, String.format(Locale.ROOT, "%.0f°", entry.rotationY), 14, mx, my);
        drawSlider(12, 136, 110, "Vertical / Y", entry.offsetY, -1.0F, 1.0F, String.format(Locale.ROOT, "%.2f m", entry.offsetY), 12, mx, my);
        drawSlider(134, 136, 110, "Roll / Z", entry.rotationZ, -180.0F, 180.0F, String.format(Locale.ROOT, "%.0f°", entry.rotationZ), 15, mx, my);
        drawButton(12, 161, 110, 15, "Aim at headset", false, hit(mx, my, 12, 161, 110, 15));
        drawButton(134, 161, 110, 15, "Use position anchor", false, hit(mx, my, 134, 161, 110, 15));
        drawButton(12, 179, 110, 15, "Teleport to headset", false, hit(mx, my, 12, 179, 110, 15));
        drawButton(134, 179, 110, 15, "Preview: " + (previewEnabled ? "ON" : "OFF"), previewEnabled, hit(mx, my, 134, 179, 110, 15));
        drawString(fontRendererObj, "Adjust values and watch the VR preview move.", 12, 199, 0xFF9DA5AF);
        drawButton(12, 218, 110, 16, "Back to options", false, hit(mx, my, 12, 218, 110, 16));
        drawButton(134, 218, 110, 16, dirty ? "Save changes *" : "Save changes", dirty, hit(mx, my, 134, 218, 110, 16));
    }

    private void drawCreateTab(int mx, int my) {
        drawPanel(12, 30, 233, 204, 0xB8121418);
        drawString(fontRendererObj, "CREATE OVERLAY", 18, 35, 0xFF9FCDFB);
        drawString(fontRendererObj, "Create a configurable copy of a supported template.", 18, 49, 0xFFCBD0D6);
        drawString(fontRendererObj, "Template", 18, 65, 0xFFB2BAC5);
        drawButton(76, 61, 164, 14, TEMPLATE_NAMES[templateIndex], false, hit(mx, my, 76, 61, 164, 14));
        drawString(fontRendererObj, "Name", 18, 78, 0xFFB2BAC5);
        createNameField.drawTextBox();
        drawString(fontRendererObj, "Description", 18, 101, 0xFFB2BAC5);
        createDescriptionField.drawTextBox();
        drawLiveCreateCard();
        drawButton(18, 206, 104, 17, "Create overlay", false, hit(mx, my, 18, 206, 104, 17));
        drawButton(128, 206, 112, 17, "Back to overlays", false, hit(mx, my, 128, 206, 112, 17));
        drawString(fontRendererObj, "The copy has independent pose, size, visibility and opacity.", 18, 233, 0xFF9DA5AF);
    }

    private void drawLiveCreateCard() {
        drawPanel(18, 127, 222, 70, 0xFF0A0B0D);
        drawString(fontRendererObj, "PREVIEW", 23, 132, 0xFF9DA5AF);
        int x = 82, y = 150, w = 90, h = TEMPLATE_IDS[templateIndex].equals(VROverlayConfig.KEYBOARD) ? 31 : 42;
        drawPanel(x, y, w, h, 0xCC263949);
        drawOutline(x, y, w, h, 0xFF6BB8F0);
        drawPreviewContent(TEMPLATE_IDS[templateIndex], x, y, w, h, 1.0F);
    }

    private void drawConfirmation(String prompt, String accept, String cancel) {
        drawRect(0, 0, UI_WIDTH, UI_HEIGHT, 0x99000000);
        drawPanel(48, 101, 161, 54, 0xFF171A20);
        drawOutline(48, 101, 161, 54, 0xFF76B9F1);
        drawCenteredString(fontRendererObj, prompt, 128, 111, 0xFFFFFFFF);
        drawButton(55, 130, 73, 16, accept, false, false);
        drawButton(133, 130, 69, 16, cancel, false, false);
    }

    private void drawToggle(int x, int y, int w, int h, String label, boolean value, int mx, int my, String property) {
        drawButton(x, y, w, h, label, value, hit(mx, my, x, y, w, h));
        drawPanel(x + w - 15, y + 3, 11, h - 6, value ? 0xFF3FAD75 : 0xFF545B65);
        drawPanel(x + w - (value ? 8 : 14), y + 5, 5, h - 10, 0xFFFFFFFF);
    }

    private void drawSlider(int x, int y, int w, String label, float value, float min, float max,
                            String valueText, int sliderId, int mx, int my) {
        drawString(fontRendererObj, label, x, y, 0xFFD7DCE3);
        drawString(fontRendererObj, valueText, x + w - fontRendererObj.getStringWidth(valueText), y, 0xFF9FCDFB);
        int trackX = x + 1, trackY = y + 10, trackW = w - 2;
        drawPanel(trackX, trackY, trackW, 3, 0xFF303842);
        float pct = (value - min) / (max - min);
        pct = Math.max(0.0F, Math.min(1.0F, pct));
        drawPanel(trackX, trackY, Math.max(1, (int) (trackW * pct)), 3, 0xFF579DD7);
        int knobX = trackX + (int) (trackW * pct) - 2;
        drawPanel(knobX, trackY - 2, 5, 7, activeSlider == sliderId ? 0xFFFFFFFF : 0xFFB9DFFF);
    }

    private void drawButton(int x, int y, int w, int h, String label, boolean selected, boolean hovered) {
        drawButton(x, y, w, h, label, selected, hovered, true);
    }

    private void drawButton(int x, int y, int w, int h, String label, boolean selected, boolean hovered, boolean enabled) {
        int background = !enabled ? 0xFF1C1E22 : selected ? 0xFF315F85 : hovered ? 0xFF353D47 : 0xFF252930;
        int border = selected ? 0xFF76B9F1 : hovered && enabled ? 0xFF6D7784 : 0xFF444B55;
        drawPanel(x, y, w, h, background);
        drawOutline(x, y, w, h, border);
        int color = !enabled ? 0xFF70757D : selected ? 0xFFFFFFFF : 0xFFDDE2E8;
        drawCenteredString(fontRendererObj, clip(label, Math.max(3, w / 4)), x + w / 2, y + (h - 8) / 2, color);
    }

    private void drawCheckbox(int x, int y, String label, boolean checked) {
        drawOutline(x, y + 1, 8, 8, checked ? 0xFF76B9F1 : 0xFF8A919B);
        if (checked) drawPanel(x + 2, y + 3, 4, 4, 0xFF76B9F1);
        drawString(fontRendererObj, label, x + 12, y + 1, 0xFFD6DCE3);
    }

    private void drawPanel(int x, int y, int w, int h, int color) {
        drawRect(x, y, x + w, y + h, color);
    }

    private void drawOutline(int x, int y, int w, int h, int color) {
        drawPanel(x, y, w, 1, color);
        drawPanel(x, y + h - 1, w, 1, color);
        drawPanel(x, y, 1, h, color);
        drawPanel(x + w - 1, y, 1, h, color);
    }

    private void drawTexture(ResourceLocation texture, int x, int y, int w, int h, int texW, int texH) {
        drawTextureRegion(texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    private void drawTextureRegion(ResourceLocation texture, int x, int y, int w, int h,
                                   float u, float v, float regionW, float regionH,
                                   float texW, float texH) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GL11.glColor4f(1, 1, 1, 1);
        float u0 = u / texW;
        float u1 = (u + regionW) / texW;
        float v0 = v / texH;
        float v1 = (v + regionH) / texH;
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + h, 0, u0, v1);
        tessellator.addVertexWithUV(x + w, y + h, 0, u1, v1);
        tessellator.addVertexWithUV(x + w, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }

    private void drawWrapped(String text, int x, int y, int maxWidth, int lineHeight, int color) {
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (fontRendererObj.getStringWidth(next) > maxWidth && line.length() > 0) {
                drawString(fontRendererObj, line.toString(), x, yy, color);
                yy += lineHeight;
                line = new StringBuilder(word);
            } else line = new StringBuilder(next);
        }
        if (line.length() > 0) drawString(fontRendererObj, line.toString(), x, yy, color);
    }

    private List<String> getVisibleIds() {
        List<String> ids = new ArrayList<>();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (String id : VROverlayConfig.getAllIds()) {
            VROverlayConfig.Entry entry = VROverlayConfig.get(id);
            if (entry.custom && !filterCustom) continue;
            if (!entry.custom && !filterBuiltIn) continue;
            if (!query.isEmpty() && !nameOf(id).toLowerCase(Locale.ROOT).contains(query)
                    && !id.toLowerCase(Locale.ROOT).contains(query)) continue;
            ids.add(id);
        }
        return ids;
    }

    private String nameOf(String id) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(id);
        if (entry.custom && entry.name != null && !entry.name.trim().isEmpty()) return entry.name;
        switch (baseId(id)) {
            case VROverlayConfig.HUD: return "HUD";
            case VROverlayConfig.HOTBAR_MAIN: return "Hotbar Main";
            case VROverlayConfig.HOTBAR_OFFHAND: return "Hotbar Offhand";
            case VROverlayConfig.GAME_SCREEN: return "Game Screen";
            case VROverlayConfig.KEYBOARD: return "Virtual Keyboard";
            case VROverlayConfig.CROSSHAIR: return "Crosshair";
            case VROverlayConfig.WRIST: return "Wrist Inventory";
            case VROverlayConfig.STATUS: return "Wrist Status";
            case VROverlayConfig.MAP: return "Wrist Map";
            case VROverlayConfig.INFO: return "Wrist Info";
            case VROverlayConfig.POTIONS: return "Wrist Potions";
            default: return id;
        }
    }

    private String baseId(String id) {
        VROverlayConfig.Entry entry = VROverlayConfig.get(id);
        return entry.custom ? entry.template : id;
    }

    private String anchorLabel(String anchor) {
        if ("MAIN_HAND".equals(anchor)) return "Main Hand";
        if ("OFFHAND".equals(anchor)) return "Off Hand";
        if ("WORLD".equals(anchor)) return "World";
        return "Head (HMD)";
    }

    private String nextAnchor(String anchor) {
        for (int i = 0; i < VROverlayConfig.ANCHORS.length; i++) {
            if (VROverlayConfig.ANCHORS[i].equals(anchor)) {
                return VROverlayConfig.ANCHORS[(i + 1) % VROverlayConfig.ANCHORS.length];
            }
        }
        return "HMD";
    }

    private String anchorShort(String anchor) {
        if ("MAIN_HAND".equals(anchor)) return "MAIN HAND";
        if ("OFFHAND".equals(anchor)) return "OFFHAND";
        if ("WORLD".equals(anchor)) return "WORLD";
        return "HMD";
    }

    private String flagLabelOf(String id) {
        switch (baseId(id)) {
            case VROverlayConfig.HUD: return "Curved HUD";
            case VROverlayConfig.HOTBAR_MAIN:
            case VROverlayConfig.HOTBAR_OFFHAND: return "Slot numbers";
            case VROverlayConfig.CROSSHAIR: return "Laser beam";
            default: return null;
        }
    }

    private boolean canCopy(Group selectedGroup) {
        return selectedGroup != Group.GENERAL;
    }

    private void takeSnapshot() {
        originalValues.clear();
        originalIds.clear();
        for (String id : VROverlayConfig.getAllIds()) {
            VROverlayConfig.Entry entry = VROverlayConfig.get(id);
            syncEntryFromLegacy(id, entry);
            VROverlayConfig.Entry copy = new VROverlayConfig.Entry();
            copy.set(entry);
            originalValues.put(id, copy);
            originalIds.add(id);
        }
    }

    private void refreshSelectedFields() {
        if (selectedId == null || !containsId(selectedId)) {
            String[] ids = VROverlayConfig.getAllIds();
            selectedId = ids.length == 0 ? null : ids[0];
        }
        if (customNameField != null && customDescriptionField != null && selectedId != null) {
            VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
            customNameField.setText(entry.custom ? safe(entry.name) : "");
            customDescriptionField.setText(entry.custom ? safe(entry.description) : "");
        }
    }

    private void syncEntryFromLegacy(String id, VROverlayConfig.Entry entry) {
        if (entry.custom) return;
        if (VROverlayConfig.HUD.equals(id)) {
            entry.scale = VRClientSettings.hudScale;
            entry.distance = VRClientSettings.hudDistance;
            entry.flag = VRClientSettings.curvedHud;
        } else if (VROverlayConfig.HOTBAR_MAIN.equals(id) || VROverlayConfig.HOTBAR_OFFHAND.equals(id)) {
            entry.flag = VRClientSettings.hotbarSlotNumbers;
        } else if (VROverlayConfig.CROSSHAIR.equals(id)) {
            entry.visible = VRClientSettings.vrCrosshair;
            entry.distance = VRClientSettings.interactionReach;
            entry.flag = VRClientSettings.crosshairLaser;
        }
    }

    private void syncLegacyFromEntry(String id, VROverlayConfig.Entry entry) {
        if (entry.custom) return;
        switch (id) {
            case VROverlayConfig.HUD:
                VRClientSettings.hudScale = entry.scale;
                VRClientSettings.hudDistance = entry.distance;
                VRClientSettings.curvedHud = entry.flag;
                break;
            case VROverlayConfig.HOTBAR_MAIN:
            case VROverlayConfig.HOTBAR_OFFHAND:
                VRClientSettings.hotbarSlotNumbers = entry.flag;
                break;
            case VROverlayConfig.CROSSHAIR:
                VRClientSettings.vrCrosshair = entry.visible;
                VRClientSettings.interactionReach = entry.distance;
                VRClientSettings.crosshairLaser = entry.flag;
                break;
        }
    }

    private void onValueChanged() {
        dirty = true;
        if (selectedId != null && containsId(selectedId)) {
            VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
            if (group == Group.GENERAL && entry.custom) {
                entry.name = customNameField.getText();
                entry.description = customDescriptionField.getText();
            }
            syncLegacyFromEntry(selectedId, entry);
        }
    }

    private void saveChanges() {
        if (selectedId != null && containsId(selectedId)) onValueChanged();
        VROverlayConfig.save();
        VRClientSettings.save();
        takeSnapshot();
        dirty = false;
        status("Overlay settings saved");
    }

    private void discardChanges() {
        for (String id : VROverlayConfig.getAllIds()) {
            if (!originalIds.contains(id) && VROverlayConfig.get(id).custom) VROverlayConfig.removeCustom(id);
        }
        for (Map.Entry<String, VROverlayConfig.Entry> item : originalValues.entrySet()) {
            if (containsId(item.getKey())) {
                VROverlayConfig.get(item.getKey()).set(item.getValue());
                syncLegacyFromEntry(item.getKey(), VROverlayConfig.get(item.getKey()));
            }
        }
        VROverlayConfig.save();
        VRClientSettings.save();
        dirty = false;
        confirmDiscard = false;
        leaveScreen();
    }

    private void leaveScreen() {
        activeSlider = -1;
        mc.displayGuiScreen(parentScreen);
    }

    private void loadDefaultsForGroup() {
        if (selectedId == null) return;
        VROverlayConfig.Entry current = VROverlayConfig.get(selectedId);
        VROverlayConfig.Entry defaults = VROverlayConfig.defaults(selectedId);
        switch (group) {
            case VISIBILITY:
                current.visible = defaults.visible;
                break;
            case POSE:
                current.anchor = defaults.anchor;
                current.rotationAnchor = defaults.rotationAnchor;
                current.distance = defaults.distance;
                current.offsetX = defaults.offsetX;
                current.offsetY = defaults.offsetY;
                current.rotationX = defaults.rotationX;
                current.rotationY = defaults.rotationY;
                current.rotationZ = defaults.rotationZ;
                break;
            case RESIZING:
                current.scale = defaults.scale;
                break;
            case MISC:
                current.opacity = defaults.opacity;
                current.flag = defaults.flag;
                break;
            case GENERAL:
                if (current.custom) {
                    current.name = "";
                    current.description = "";
                    refreshSelectedFields();
                }
                break;
        }
        syncLegacyFromEntry(selectedId, current);
        dirty = true;
    }

    private void copyCurrentGroup() {
        if (selectedId == null || group == Group.GENERAL) return;
        copiedGroup = new VROverlayConfig.Entry();
        copiedGroup.set(VROverlayConfig.get(selectedId));
        copiedGroupType = group;
        status("Option group copied");
    }

    private void pasteCurrentGroup() {
        if (selectedId == null || copiedGroup == null || copiedGroupType != group) return;
        VROverlayConfig.Entry target = VROverlayConfig.get(selectedId);
        switch (group) {
            case VISIBILITY: target.visible = copiedGroup.visible; break;
            case POSE:
                target.anchor = copiedGroup.anchor;
                target.rotationAnchor = copiedGroup.rotationAnchor;
                target.distance = copiedGroup.distance;
                target.offsetX = copiedGroup.offsetX;
                target.offsetY = copiedGroup.offsetY;
                target.rotationX = copiedGroup.rotationX;
                target.rotationY = copiedGroup.rotationY;
                target.rotationZ = copiedGroup.rotationZ;
                break;
            case RESIZING: target.scale = copiedGroup.scale; break;
            case MISC:
                target.opacity = copiedGroup.opacity;
                target.flag = copiedGroup.flag;
                break;
            default: return;
        }
        syncLegacyFromEntry(selectedId, target);
        dirty = true;
        status("Option group pasted");
    }

    private void reloadFromDisk() {
        VROverlayConfig.load();
        VRClientSettings.load();
        listScroll = 0;
        takeSnapshot();
        refreshSelectedFields();
        dirty = false;
        status("Reloaded saved overlay settings");
    }

    private void createOverlay() {
        String name = createNameField.getText() == null ? "" : createNameField.getText().trim();
        if (name.isEmpty()) {
            status("Enter an overlay name first");
            return;
        }
        String description = safe(createDescriptionField.getText()).trim();
        String id = VROverlayConfig.createCustom(name, description, TEMPLATE_IDS[templateIndex]);
        selectedId = id;
        tab = Tab.OVERLAYS;
        group = Group.POSE;
        searchField.setText("");
        listScroll = 0;
        refreshSelectedFields();
        dirty = true;
        status("Overlay created — save to keep your changes");
    }

    private void removeSelectedOverlay() {
        if (selectedId == null || !VROverlayConfig.get(selectedId).custom) return;
        String removed = selectedId;
        VROverlayConfig.removeCustom(removed);
        selectedId = null;
        confirmRemove = false;
        rebuildSelection();
        takeSnapshot();
        dirty = false;
        status("Custom overlay removed");
    }

    private void rebuildSelection() {
        List<String> ids = new ArrayList<>();
        for (String id : VROverlayConfig.getAllIds()) ids.add(id);
        selectedId = ids.isEmpty() ? null : ids.get(0);
        refreshSelectedFields();
    }

    private float stickScrollAccum;

    /**
     * Thumbstick navigation provided by VRControllerHandler while this screen is open.
     * Scroll the filtered overlay list when pointing at the left pane; otherwise
     * cycle the selected option group so every group is reachable without a mouse.
     */
    public void handleStickScroll(float stickY, int mouseX, int mouseY) {
        if (tab != Tab.OVERLAYS || poseEditor) return;
        if (Math.abs(stickY) < 0.15F) {
            stickScrollAccum = 0.0F;
            return;
        }
        stickScrollAccum += stickY * 0.55F;
        if (Math.abs(stickScrollAccum) < 0.42F) return;
        int steps = Math.max(1, (int) Math.abs(stickScrollAccum));
        int direction = stickScrollAccum > 0.0F ? -1 : 1;
        stickScrollAccum = 0.0F;
        int logicalX = toLogicalX(mouseX);
        int logicalY = toLogicalY(mouseY);
        if (logicalX <= 108 && logicalY >= 48 && logicalY <= 191) {
            int maxScroll = Math.max(0, getVisibleIds().size() - Math.max(1, (190 - (showFilters ? 80 : 50) - 2) / 11));
            listScroll = Math.max(0, Math.min(maxScroll, listScroll + direction * steps));
        } else {
            Group[] groups = Group.values();
            int current = group.ordinal();
            int next = Math.max(0, Math.min(groups.length - 1, current + direction * steps));
            if (groups[next] != Group.GENERAL || (selectedId != null && VROverlayConfig.get(selectedId).custom)) {
                group = groups[next];
            }
        }
    }

    private boolean containsId(String id) {
        if (id == null) return false;
        for (String candidate : VROverlayConfig.getAllIds()) if (candidate.equals(id)) return true;
        return false;
    }

    private void status(String value) {
        statusText = value;
        statusUntil = System.currentTimeMillis() + 2200L;
    }

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int toLogicalX(int x) {
        return Math.round((x - scaleHelper.getStartX()) / Math.max(0.01F, scaleHelper.getScale()));
    }

    private int toLogicalY(int y) {
        return Math.round((y - scaleHelper.getStartY()) / Math.max(0.01F, scaleHelper.getScale()));
    }

    private String clip(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private String safe(String value) { return value == null ? "" : value; }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int mx = toLogicalX(mouseX);
        int my = toLogicalY(mouseY);
        if (confirmRemove) {
            if (hit(mx, my, 55, 130, 73, 16)) removeSelectedOverlay();
            else if (hit(mx, my, 133, 130, 69, 16)) confirmRemove = false;
            return;
        }
        if (confirmDiscard) {
            if (hit(mx, my, 55, 130, 73, 16)) discardChanges();
            else if (hit(mx, my, 133, 130, 69, 16)) confirmDiscard = false;
            return;
        }
        if (searchField != null) searchField.mouseClicked(mx, my, mouseButton);
        if (tab == Tab.CREATE) {
            createNameField.mouseClicked(mx, my, mouseButton);
            createDescriptionField.mouseClicked(mx, my, mouseButton);
        } else if (group == Group.GENERAL && selectedId != null && VROverlayConfig.get(selectedId).custom) {
            customNameField.mouseClicked(mx, my, mouseButton);
            customDescriptionField.mouseClicked(mx, my, mouseButton);
        }

        if (mouseButton != 0) return;
        if (hit(mx, my, 235, 12, 19, 19)) {
            if (dirty) confirmDiscard = true;
            else leaveScreen();
            return;
        }
        if (hit(mx, my, 235, 35, 19, 19)) {
            VRControllerHandler controller = VRControllerHandler.getInstance();
            if (controller.isRightHandTracked()) {
                VRVirtualScreen.getInstance().startDragging(controller.getRightHandPos(), controller.getRightHandRot());
                draggingScreen = true;
                status("Grab the menu with the right controller; release to place it");
            } else {
                status("Track the right controller to move this menu");
            }
            return;
        }
        if (hit(mx, my, 115, 6, 115, 23)) { tab = Tab.CREATE; return; }
        if (hit(mx, my, 0, 6, 115, 23)) { tab = Tab.OVERLAYS; return; }

        if (tab == Tab.CREATE) {
            if (hit(mx, my, 76, 61, 164, 14)) {
                templateIndex = (templateIndex + 1) % TEMPLATE_IDS.length;
            } else if (hit(mx, my, 18, 206, 104, 17)) createOverlay();
            else if (hit(mx, my, 128, 206, 112, 17)) tab = Tab.OVERLAYS;
            return;
        }

        if (poseEditor) {
            VROverlayConfig.Entry pose = VROverlayConfig.get(selectedId);
            if (hit(mx, my, 14, 79, 105, 14)) { pose.anchor = nextAnchor(pose.anchor); onValueChanged(); return; }
            if (hit(mx, my, 136, 79, 105, 14)) { pose.rotationAnchor = nextAnchor(pose.rotationAnchor); onValueChanged(); return; }
            if (hit(mx, my, 12, 107, 108, 8)) { activeSlider = 10; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 12, 126, 108, 8)) { activeSlider = 11; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 12, 145, 108, 8)) { activeSlider = 12; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 134, 107, 108, 8)) { activeSlider = 13; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 134, 126, 108, 8)) { activeSlider = 14; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 134, 145, 108, 8)) { activeSlider = 15; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 12, 161, 110, 15)) {
                pose.rotationAnchor = "HMD";
                pose.rotationX = pose.rotationY = pose.rotationZ = 0.0F;
                onValueChanged(); return;
            }
            if (hit(mx, my, 134, 161, 110, 15)) { pose.rotationAnchor = pose.anchor; onValueChanged(); return; }
            if (hit(mx, my, 12, 179, 110, 15)) {
                pose.anchor = "HMD"; pose.rotationAnchor = "HMD"; pose.distance = 1.2F;
                pose.offsetX = pose.offsetY = 0.0F;
                pose.rotationX = pose.rotationY = pose.rotationZ = 0.0F;
                onValueChanged(); return;
            }
            if (hit(mx, my, 134, 179, 110, 15)) {
                previewEnabled = !previewEnabled;
                status(previewEnabled ? "Live preview enabled" : "Live preview paused");
                return;
            }
            if (hit(mx, my, 12, 218, 110, 16)) { poseEditor = false; activeSlider = -1; return; }
            if (hit(mx, my, 134, 218, 110, 16)) { saveChanges(); return; }
            return;
        }

        if (hit(mx, my, 88, 33, 15, 14)) { showFilters = !showFilters; return; }
        if (showFilters && hit(mx, my, 10, 52, 90, 10)) { filterBuiltIn = !filterBuiltIn; listScroll = 0; return; }
        if (showFilters && hit(mx, my, 10, 64, 90, 10)) { filterCustom = !filterCustom; listScroll = 0; return; }

        int listY = showFilters ? 80 : 50;
        int listBottom = 190;
        int rowHeight = 11;
        int visibleRows = Math.max(1, (listBottom - listY - 2) / rowHeight);
        List<String> ids = getVisibleIds();
        for (int row = 0; row < visibleRows && row + listScroll < ids.size(); row++) {
            int y = listY + 2 + row * rowHeight;
            if (hit(mx, my, 8, y, 96, rowHeight - 1)) {
                selectedId = ids.get(row + listScroll);
                group = Group.POSE;
                confirmRemove = false;
                refreshSelectedFields();
                return;
            }
        }
        if (hit(mx, my, 7, 194, 98, 14)) { reloadFromDisk(); return; }
        if (hit(mx, my, 7, 210, 98, 14)) { loadDefaultsForGroup(); return; }
        if (hit(mx, my, 7, 226, 98, 14)) {
            if (dirty) confirmDiscard = true;
            else leaveScreen();
            return;
        }

        int tabX = 115;
        int[] widths = {29, 25, 25, 25, 25};
        Group[] groups = Group.values();
        for (int i = 0; i < groups.length; i++) {
            if (hit(mx, my, tabX, 128, widths[i], 14)) {
                if (groups[i] != Group.GENERAL || VROverlayConfig.get(selectedId).custom) group = groups[i];
                return;
            }
            tabX += widths[i] + 1;
        }
        if (selectedId == null) return;
        VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
        if (group == Group.POSE) {
            if (hit(mx, my, 120, 182, 119, 8)) { activeSlider = 0; updateSliderFromLogicalMouse(mx); return; }
            if (hit(mx, my, 119, 197, 123, 14)) { poseEditor = true; activeSlider = -1; return; }
        }
        if (group == Group.RESIZING && hit(mx, my, 120, 178, 119, 8)) {
            activeSlider = 3; updateSliderFromLogicalMouse(mx); return;
        }
        if (group == Group.MISC && hit(mx, my, 120, 178, 119, 8)) {
            activeSlider = 4; updateSliderFromLogicalMouse(mx); return;
        }
        if (group == Group.VISIBILITY && hit(mx, my, 119, 165, 123, 18)) {
            entry.visible = !entry.visible; onValueChanged(); return;
        }
        if (group == Group.POSE && hit(mx, my, 119, 156, 123, 13)) {
            int next = 0;
            for (int i = 0; i < VROverlayConfig.ANCHORS.length; i++) if (VROverlayConfig.ANCHORS[i].equals(entry.anchor)) next = i + 1;
            entry.anchor = VROverlayConfig.ANCHORS[next % VROverlayConfig.ANCHORS.length];
            onValueChanged(); return;
        }
        if (group == Group.MISC && flagLabelOf(selectedId) != null && hit(mx, my, 119, 190, 123, 14)) {
            entry.flag = !entry.flag; onValueChanged(); return;
        }
        if (group == Group.GENERAL && entry.custom
                && (customNameField.isFocused() || customDescriptionField.isFocused())) {
            entry.name = customNameField.getText();
            entry.description = customDescriptionField.getText();
            onValueChanged();
            return;
        }
        if (hit(mx, my, 115, 219, 40, 14)) { copyCurrentGroup(); return; }
        if (hit(mx, my, 157, 219, 40, 14)) { pasteCurrentGroup(); return; }
        if (hit(mx, my, 199, 219, 48, 14)) { loadDefaultsForGroup(); return; }
        if (hit(mx, my, 115, 235, 64, 14)) { saveChanges(); return; }
        if (hit(mx, my, 181, 235, 66, 14)) {
            if (entry.custom) confirmRemove = true;
            else { confirmDiscard = true; }
        }
    }

    private void updateSliderFromLogicalMouse(int mx) {
        if (activeSlider < 0 || selectedId == null) return;
        VROverlayConfig.Entry entry = VROverlayConfig.get(selectedId);
        boolean poseEditorSlider = activeSlider >= 10 && activeSlider <= 15;
        int trackStart = poseEditorSlider ? (activeSlider >= 13 ? 135 : 13) : 120;
        int trackWidth = poseEditorSlider ? 108 : 119;
        float pct = Math.max(0.0F, Math.min(1.0F, (float) (mx - trackStart) / (float) trackWidth));
        switch (activeSlider) {
            case 0: entry.distance = 0.1F + pct * 5.9F; break;
            case 1: entry.offsetX = -1.0F + pct * 2.0F; break;
            case 2: entry.offsetY = -1.0F + pct * 2.0F; break;
            case 3: entry.scale = 0.2F + pct * 2.8F; break;
            case 4: entry.opacity = 0.05F + pct * 0.95F; break;
            case 10: entry.distance = 0.1F + pct * 5.9F; break;
            case 11: entry.offsetX = -1.0F + pct * 2.0F; break;
            case 12: entry.offsetY = -1.0F + pct * 2.0F; break;
            case 13: entry.rotationX = -180.0F + pct * 360.0F; break;
            case 14: entry.rotationY = -180.0F + pct * 360.0F; break;
            case 15: entry.rotationZ = -180.0F + pct * 360.0F; break;
            default: return;
        }
        onValueChanged();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && activeSlider >= 0) {
            updateSliderFromLogicalMouse(toLogicalX(mouseX));
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which >= 0) {
            activeSlider = -1;
            if (draggingScreen) {
                VRVirtualScreen.getInstance().stopDragging();
                draggingScreen = false;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int sx = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int sy = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int mx = toLogicalX(sx);
        int my = toLogicalY(sy);
        if (tab == Tab.OVERLAYS && hit(mx, my, 7, 50, 98, 140)) {
            List<String> ids = getVisibleIds();
            int listY = showFilters ? 80 : 50;
            int visibleRows = Math.max(1, (190 - listY - 2) / 11);
            int maxScroll = Math.max(0, ids.size() - visibleRows);
            listScroll = Math.max(0, Math.min(maxScroll, listScroll + (wheel > 0 ? -2 : 2)));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            if (confirmRemove) { confirmRemove = false; return; }
            if (confirmDiscard) { confirmDiscard = false; return; }
            if (dirty) confirmDiscard = true;
            else leaveScreen();
            return;
        }
        if (tab == Tab.CREATE) {
            if (createNameField.textboxKeyTyped(typedChar, keyCode)) return;
            createDescriptionField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (searchField != null && searchField.isFocused()) {
            if (searchField.textboxKeyTyped(typedChar, keyCode)) listScroll = 0;
            return;
        }
        if (group == Group.GENERAL && selectedId != null && VROverlayConfig.get(selectedId).custom) {
            if (customNameField.textboxKeyTyped(typedChar, keyCode)) {
                VROverlayConfig.get(selectedId).name = customNameField.getText();
                onValueChanged();
                return;
            }
            if (customDescriptionField.textboxKeyTyped(typedChar, keyCode)) {
                VROverlayConfig.get(selectedId).description = customDescriptionField.getText();
                onValueChanged();
            }
        }
    }

    private static boolean hitStatic(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
