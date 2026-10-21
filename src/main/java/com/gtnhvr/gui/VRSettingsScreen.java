package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VRSettingsCategory;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Lwjgl3Aware
public class VRSettingsScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND_TEX = new ResourceLocation("gtnhvr", "textures/gui/settings/background.png");
    private static final ResourceLocation GENERAL_TEX = new ResourceLocation("gtnhvr", "textures/gui/settings/general.png");
    private static final ResourceLocation ICON_TEX = new ResourceLocation("gtnhvr", "textures/icon_headset.png");

    private static final int GENERAL_TEX_W = 274;
    private static final int GENERAL_TEX_H = 260;

    private static final int CATEGORY_LIST_TOP = 27;
    private static final int CATEGORY_SCROLL_TOP = 43;
    private static final int CATEGORY_SCROLL_HEIGHT = 88;
    private static final int CATEGORY_BUTTON_HEIGHT = 12;
    private static final int CATEGORY_STEP = 14;

    private final GuiScreen parentScreen;
    private final GuiScaleHelper scaleHelper = new GuiScaleHelper(256, 159);

    private VRSettingsCategory activeCategory = VRSettingsCategory.PRESETS;
    private VROptionsSet currentOptionsSet;
    private int categoryScroll = 0;
    private long lastOptionClickTime = 0L;

    public VRSettingsScreen(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    public VRSettingsScreen(GuiScreen parentScreen, VROptionsSet initialSet) {
        this.parentScreen = parentScreen;
        this.currentOptionsSet = initialSet;
    }

    public GuiScreen getParentScreen() {
        return (parentScreen instanceof VRSettingsScreen) ? null : parentScreen;
    }

    public GuiScaleHelper getScaleHelper() {
        return scaleHelper;
    }

    @Override
    public void initGui() {
        scaleHelper.computeScale(this.width, this.height);
        if (currentOptionsSet == null) {
            currentOptionsSet = activeCategory.createOptionsSet(this);
        }
        currentOptionsSet.init();
    }

    public void openCategory(VRSettingsCategory category) {
        this.activeCategory = category;
        this.currentOptionsSet = category.createOptionsSet(this);
        this.currentOptionsSet.init();
        this.lastOptionClickTime = System.currentTimeMillis() + 250L;
    }

    public void pushOptionsSet(VROptionsSet nextSet) {
        this.currentOptionsSet = nextSet;
        this.currentOptionsSet.init();
        this.lastOptionClickTime = System.currentTimeMillis() + 250L;
    }

    public void popOptionsSet() {
        if (currentOptionsSet != null && currentOptionsSet.canGoBack()) {
            this.currentOptionsSet = currentOptionsSet.getPreviousSet();
            this.currentOptionsSet.init();
            this.lastOptionClickTime = System.currentTimeMillis() + 250L;
        }
    }

    public VROptionsSet getCurrentOptionsSet() {
        return currentOptionsSet;
    }

    // --- Coordinate getters for VROptionsSet ---
    public int getOptionLeftColumnX() {
        return scaleHelper.scaledX(58);
    }

    public int getOptionRightColumnX() {
        return scaleHelper.scaledX(130);
    }

    public int getOptionCenterColumnX() {
        return scaleHelper.scaledX(58);
    }

    public int getOptionColumnWidth() {
        return scaleHelper.scaledSize(68);
    }

    public int getOptionCenterWidth() {
        return scaleHelper.scaledSize(140);
    }

    public int getOptionWidgetHeight() {
        return scaleHelper.scaledSize(12);
    }

    public int getOptionRowStep() {
        return scaleHelper.scaledSize(15);
    }

    public int getOptionsStartY() {
        return scaleHelper.scaledY(30);
    }

    public int getOptionsAreaHeight() {
        return scaleHelper.scaledSize(102);
    }

    public int getOptionsAreaWidth() {
        return scaleHelper.scaledSize(142);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        scaleHelper.computeScale(this.width, this.height);

        int startX = scaleHelper.getStartX();
        int startY = scaleHelper.getStartY();
        int bgW = scaleHelper.scaledSize(256);
        int bgH = scaleHelper.scaledSize(159);

        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 1. Draw Visor Background Frame
        this.mc.getTextureManager().bindTexture(BACKGROUND_TEX);
        drawTexturedQuad(startX, startY, bgW, bgH, 0, 0, 256, 159, 256, 159);

        // 2. Draw Header (Icon + Title + Status + Close Button)
        this.mc.getTextureManager().bindTexture(ICON_TEX);
        drawTexturedQuad(scaleHelper.scaledX(4), scaleHelper.scaledY(3),
                scaleHelper.scaledSize(12), scaleHelper.scaledSize(12),
                0, 0, 16, 16, 16, 16);

        String title = I18n.format("visor.options.main");
        drawScaledStringCentered(title, scaleHelper.scaledX(128), scaleHelper.scaledY(4), 0xFFFFFF, scaleHelper.scaledSize(140));

        // Close button (X)
        int closeBtnX = scaleHelper.scaledX(240);
        int closeBtnY = scaleHelper.scaledY(4);
        int closeBtnSize = scaleHelper.scaledSize(10);
        int closeHitPad = scaleHelper.scaledSize(5);
        this.mc.getTextureManager().bindTexture(GENERAL_TEX);
        boolean closeHover = isHovered(mouseX, mouseY, closeBtnX - closeHitPad, closeBtnY - closeHitPad,
                closeBtnSize + closeHitPad * 2, closeBtnSize + closeHitPad * 2);
        int closeU = 208;
        int closeV = closeHover ? 52 : 52;
        drawTexturedQuad(closeBtnX, closeBtnY,
                closeBtnSize, closeBtnSize,
                closeU, closeV, 25, 25, GENERAL_TEX_W, GENERAL_TEX_H);

        // 3. Draw Category Tabs (Left column)
        drawCategoryTabs(mouseX, mouseY);

        // 4. Draw Right Sidebar Action Buttons
        drawRightSidebar(mouseX, mouseY);

        // 5. Draw Active Options Area (Center pane)
        if (currentOptionsSet != null) {
            currentOptionsSet.draw(this, mouseX, mouseY, partialTicks);
        }

        // 6. Draw Bottom Bar Buttons (Back & Load Defaults)
        drawBottomBar(mouseX, mouseY);

        // 7. Draw Tooltips
        drawTooltips(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCategoryTabs(int mouseX, int mouseY) {
        int tabX = scaleHelper.scaledX(4);
        int tabW = scaleHelper.scaledSize(50);
        int tabH = scaleHelper.scaledSize(CATEGORY_BUTTON_HEIGHT);
        int tabStep = scaleHelper.scaledSize(CATEGORY_STEP);

        VRSettingsCategory[] categories = VRSettingsCategory.values();

        // 1. Pinned PRESETS button
        boolean presetsHovered = isHovered(mouseX, mouseY, tabX, scaleHelper.scaledY(CATEGORY_LIST_TOP), tabW, tabH);
        boolean presetsSelected = (activeCategory == VRSettingsCategory.PRESETS);

        this.mc.getTextureManager().bindTexture(GENERAL_TEX);
        int presU = 0;
        int presV = presetsSelected ? 210 : (presetsHovered ? 159 : 108);
        int presH = (presetsSelected || presetsHovered) ? 50 : 51;
        drawTexturedQuad(tabX, scaleHelper.scaledY(CATEGORY_LIST_TOP), tabW, tabH, presU, presV, 208, presH, GENERAL_TEX_W, GENERAL_TEX_H);

        String presLabel = I18n.format("visor.options.presets.button");
        drawScaledStringCentered(presLabel, tabX + tabW / 2, scaleHelper.scaledY(CATEGORY_LIST_TOP) + (tabH - 8) / 2, presetsSelected ? 0xFFFFFF : 0xDDDDDD, tabW - 4);

        // 2. Scrollable category buttons below presets
        int maxScroll = getMaxCategoryScroll();
        int scrollableIndex = 0;
        int scrollAreaTop = scaleHelper.scaledY(CATEGORY_SCROLL_TOP);
        int scrollAreaBottom = scrollAreaTop + scaleHelper.scaledSize(CATEGORY_SCROLL_HEIGHT);

        for (int i = 1; i < categories.length; i++) {
            VRSettingsCategory cat = categories[i];
            int relIndex = scrollableIndex - categoryScroll;
            scrollableIndex++;

            int y = scrollAreaTop + relIndex * tabStep;
            if (y >= scrollAreaTop && y + tabH <= scrollAreaBottom) {
                boolean hovered = isHovered(mouseX, mouseY, tabX, y, tabW, tabH);
                boolean selected = (cat == activeCategory);

                int bgColor = selected ? 0xFF2A5072 : (hovered ? 0xFF3A6082 : 0xFF181818);
                int borderColor = selected ? 0xFF5A9AE2 : (hovered ? 0xFF4A8AC2 : 0xFF333333);

                drawSolidRect(tabX, y, tabX + tabW, y + tabH, bgColor);
                drawOutline(tabX, y, tabW, tabH, borderColor);

                String label = I18n.format("visor.options." + cat.getKey() + ".button");
                drawScaledStringCentered(label, tabX + tabW / 2, y + (tabH - 8) / 2, selected ? 0xFFFFFF : 0xCCCCCC, tabW - 4);
            }
        }

        // Scroll hints / scrollbar if categories overflow
        if (maxScroll > 0) {
            drawCategoryScrollbar(mouseX, mouseY, maxScroll);
        }
    }

    private int getMaxCategoryScroll() {
        int scrollableCount = VRSettingsCategory.values().length - 1; // Excluding presets
        int visibleCount = CATEGORY_SCROLL_HEIGHT / CATEGORY_STEP;
        return Math.max(0, scrollableCount - visibleCount);
    }

    private void drawCategoryScrollbar(int mouseX, int mouseY, int maxScroll) {
        int barX = scaleHelper.scaledX(54);
        int barY = scaleHelper.scaledY(CATEGORY_SCROLL_TOP);
        int barH = scaleHelper.scaledSize(CATEGORY_SCROLL_HEIGHT);
        int barW = scaleHelper.scaledSize(2);

        drawSolidRect(barX, barY, barX + barW, barY + barH, 0xFF111111);

        int thumbH = Math.max(scaleHelper.scaledSize(10), barH / (maxScroll + 1));
        int thumbY = barY + (barH - thumbH) * categoryScroll / maxScroll;
        drawSolidRect(barX, thumbY, barX + barW, thumbY + thumbH, 0xFF7BB3F0);
    }

    private void drawRightSidebar(int mouseX, int mouseY) {
        int btnX = scaleHelper.scaledX(202);
        int btnW = scaleHelper.scaledSize(50);
        int btnH = scaleHelper.scaledSize(12);

        // 1. Setup Overlays (VR)
        int y1 = scaleHelper.scaledY(27);
        boolean hov1 = isHovered(mouseX, mouseY, btnX, y1, btnW, btnH);
        drawSidebarButton(btnX, y1, btnW, btnH, I18n.format("visor.options.main.overlay_settings"), hov1);

        // 2. Setup Addons
        int y2 = scaleHelper.scaledY(41);
        boolean hov2 = isHovered(mouseX, mouseY, btnX, y2, btnW, btnH);
        drawSidebarButton(btnX, y2, btnW, btnH, I18n.format("visor.options.main.addons"), hov2);

        // 3. Join Community
        int y3 = scaleHelper.scaledY(55);
        boolean hov3 = isHovered(mouseX, mouseY, btnX, y3, btnW, btnH);
        drawSidebarButton(btnX, y3, btnW, btnH, I18n.format("visor.options.main.join_community"), hov3);
    }

    private void drawSidebarButton(int x, int y, int w, int h, String text, boolean hovered) {
        int bgColor = hovered ? 0xFF3A6082 : 0xFF181818;
        int borderColor = hovered ? 0xFF5A9AE2 : 0xFF333333;
        drawSolidRect(x, y, x + w, y + h, bgColor);
        drawOutline(x, y, w, h, borderColor);
        drawScaledStringCentered(text, x + w / 2, y + (h - 8) / 2, 0xFFFFFF, w - 4);
    }

    private void drawBottomBar(int mouseX, int mouseY) {
        int backX = scaleHelper.scaledX(38);
        int backY = scaleHelper.scaledY(135);
        int btnSize = scaleHelper.scaledSize(16);

        this.mc.getTextureManager().bindTexture(GENERAL_TEX);

        // Back button
        boolean canBack = (currentOptionsSet != null && currentOptionsSet.canGoBack());
        boolean backHover = isHovered(mouseX, mouseY, backX, backY, btnSize, btnSize);
        int backU = !canBack ? 208 : (backHover ? 156 : 104);
        drawTexturedQuad(backX, backY, btnSize, btnSize, backU, 0, 50, 50, GENERAL_TEX_W, GENERAL_TEX_H);

        // Load Defaults button
        int defX = scaleHelper.scaledX(202);
        int defY = scaleHelper.scaledY(135);
        boolean defHover = isHovered(mouseX, mouseY, defX, defY, btnSize, btnSize);
        int defU = defHover ? 52 : 0;
        drawTexturedQuad(defX, defY, btnSize, btnSize, defU, 52, 50, 50, GENERAL_TEX_W, GENERAL_TEX_H);
    }

    private void drawTooltips(int mouseX, int mouseY) {
        if (currentOptionsSet != null) {
            OptionWidget hovered = currentOptionsSet.getHoveredWidget(mouseX, mouseY);
            if (hovered != null && hovered.getTooltip() != null && !hovered.getTooltip().isEmpty()) {
                String raw = hovered.getTooltip().replace("\\n", "\n").replace("/n", "\n");
                List<String> lines = new ArrayList<>();
                for (String line : raw.split("\n")) {
                    lines.add(line);
                }
                drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESCAPE
            VRClientSettings.save();
            if (this.mc != null && this.mc.theWorld != null) {
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
            } else {
                GuiScreen target = getParentScreen();
                this.mc.displayGuiScreen(target);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // 1. Close Button [X]: Always return directly to the world if in-game
        int closeBtnX = scaleHelper.scaledX(240);
        int closeBtnY = scaleHelper.scaledY(4);
        int closeBtnSize = scaleHelper.scaledSize(10);
        int closeHitPad = scaleHelper.scaledSize(5);
        if (isHovered(mouseX, mouseY, closeBtnX - closeHitPad, closeBtnY - closeHitPad,
                closeBtnSize + closeHitPad * 2, closeBtnSize + closeHitPad * 2)) {
            playClickSound();
            VRClientSettings.save();
            if (this.mc.theWorld != null) {
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
            } else {
                this.mc.displayGuiScreen(getParentScreen());
            }
            return;
        }

        // 2. Back Button
        if (isHovered(mouseX, mouseY, scaleHelper.scaledX(38), scaleHelper.scaledY(135),
                scaleHelper.scaledSize(16), scaleHelper.scaledSize(16))) {
            if (currentOptionsSet != null && currentOptionsSet.canGoBack()) {
                playClickSound();
                popOptionsSet();
            } else {
                playClickSound();
                VRClientSettings.save();
                this.mc.displayGuiScreen(getParentScreen());
                if (this.mc.currentScreen == null && this.mc.theWorld != null) {
                    this.mc.setIngameFocus();
                }
            }
            return;
        }

        // 3. Load Defaults Button
        if (isHovered(mouseX, mouseY, scaleHelper.scaledX(202), scaleHelper.scaledY(135),
                scaleHelper.scaledSize(16), scaleHelper.scaledSize(16))) {
            playClickSound();
            if (currentOptionsSet != null) {
                currentOptionsSet.loadDefaults();
            } else {
                VRClientSettings.loadDefaults();
            }
            return;
        }

        // 4. Right Sidebar Action Buttons
        int sideX = scaleHelper.scaledX(202);
        int sideW = scaleHelper.scaledSize(50);
        int sideH = scaleHelper.scaledSize(12);

        if (isHovered(mouseX, mouseY, sideX, scaleHelper.scaledY(27), sideW, sideH)) {
            // Setup Overlays (VR)
            playClickSound();
            Minecraft.getMinecraft().displayGuiScreen(new GuiSettingsOverlays(this));
            return;
        }

        if (isHovered(mouseX, mouseY, sideX, scaleHelper.scaledY(41), sideW, sideH)) {
            // Setup Addons
            playClickSound();
            return;
        }

        if (isHovered(mouseX, mouseY, sideX, scaleHelper.scaledY(55), sideW, sideH)) {
            // Join Community
            playClickSound();
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI("https://discord.gg/wJX8sTDEdx"));
                }
            } catch (Exception ignored) {}
            return;
        }

        // 5. Category Tab Clicks
        int tabX = scaleHelper.scaledX(4);
        int tabW = scaleHelper.scaledSize(50);
        int tabH = scaleHelper.scaledSize(CATEGORY_BUTTON_HEIGHT);
        int tabStep = scaleHelper.scaledSize(CATEGORY_STEP);

        // Pinned PRESETS
        if (isHovered(mouseX, mouseY, tabX, scaleHelper.scaledY(CATEGORY_LIST_TOP), tabW, tabH)) {
            playClickSound();
            openCategory(VRSettingsCategory.PRESETS);
            return;
        }

        // Scrollable categories
        VRSettingsCategory[] categories = VRSettingsCategory.values();
        int scrollAreaTop = scaleHelper.scaledY(CATEGORY_SCROLL_TOP);
        int scrollAreaBottom = scrollAreaTop + scaleHelper.scaledSize(CATEGORY_SCROLL_HEIGHT);
        int scrollableIndex = 0;

        for (int i = 1; i < categories.length; i++) {
            int relIndex = scrollableIndex - categoryScroll;
            scrollableIndex++;
            int y = scrollAreaTop + relIndex * tabStep;
            if (y >= scrollAreaTop && y + tabH <= scrollAreaBottom) {
                if (isHovered(mouseX, mouseY, tabX, y, tabW, tabH)) {
                    playClickSound();
                    openCategory(categories[i]);
                    return;
                }
            }
        }

        // 6. Forward to Active Options Set
        long now = System.currentTimeMillis();
        if (now - lastOptionClickTime < 150L) {
            return;
        }

        if (currentOptionsSet != null && currentOptionsSet.mouseClicked(mouseX, mouseY, button)) {
            lastOptionClickTime = now;
            return;
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (currentOptionsSet != null) {
            currentOptionsSet.mouseClickMove(mouseX, mouseY, button, time);
        }
        super.mouseClickMove(mouseX, mouseY, button, time);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (currentOptionsSet != null) {
            currentOptionsSet.mouseMovedOrUp(mouseX, mouseY, which);
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            int d = (wheel < 0) ? 1 : -1;
            int mouseX = org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            int tabX = scaleHelper.scaledX(4);
            int tabW = scaleHelper.scaledSize(50);
            int tabY = scaleHelper.scaledY(CATEGORY_SCROLL_TOP);
            int tabH = scaleHelper.scaledSize(CATEGORY_SCROLL_HEIGHT);

            if (isHovered(mouseX, mouseY, tabX, tabY, tabW, tabH)) {
                categoryScroll = Math.max(0, Math.min(getMaxCategoryScroll(), categoryScroll + d));
            } else if (currentOptionsSet != null) {
                currentOptionsSet.handleMouseWheel(wheel);
            }
        }
    }

    private long lastCategoryStickScrollTime = 0L;

    public void handleStickScroll(float stickY, int mouseX, int mouseY) {
        if (Math.abs(stickY) < 0.15F) {
            if (currentOptionsSet != null) {
                currentOptionsSet.handleStickScroll(0.0F);
            }
            return;
        }

        int tabX = scaleHelper.scaledX(4);
        int tabW = scaleHelper.scaledSize(50);
        int tabY = scaleHelper.scaledY(CATEGORY_SCROLL_TOP);
        int tabH = scaleHelper.scaledSize(CATEGORY_SCROLL_HEIGHT);

        if (isHovered(mouseX, mouseY, tabX, tabY, tabW, tabH)) {
            long now = System.currentTimeMillis();
            if (now - lastCategoryStickScrollTime >= 120L) {
                lastCategoryStickScrollTime = now;
                int d = (stickY > 0) ? -1 : 1;
                categoryScroll = Math.max(0, Math.min(getMaxCategoryScroll(), categoryScroll + d));
            }
        } else if (currentOptionsSet != null) {
            currentOptionsSet.handleStickScroll(stickY);
        }
    }

    public static void enableScissor(Minecraft mc, int guiX, int guiY, int guiW, int guiH, int screenW, int screenH) {
        if (mc == null || screenW <= 0 || screenH <= 0 || mc.displayWidth <= 0 || mc.displayHeight <= 0) return;
        int sx = guiX * mc.displayWidth / screenW;
        int sy = (screenH - (guiY + guiH)) * mc.displayHeight / screenH;
        int sw = Math.max(1, guiW * mc.displayWidth / screenW);
        int sh = Math.max(1, guiH * mc.displayHeight / screenH);
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(sx, sy, sw, sh);
    }

    public static void disableScissor() {
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // --- Utility Drawing Helpers ---
    public boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void drawSolidRect(int x1, int y1, int x2, int y2, int color) {
        drawRect(x1, y1, x2, y2, color);
    }

    public void drawOutline(int x, int y, int w, int h, int color) {
        drawHorizontalLine(x, x + w - 1, y, color);
        drawHorizontalLine(x, x + w - 1, y + h - 1, color);
        drawVerticalLine(x, y, y + h, color);
        drawVerticalLine(x + w - 1, y, y + h, color);
    }

    public void drawScaledStringCentered(String text, int centerX, int centerY, int color, int maxW) {
        int strW = this.fontRendererObj.getStringWidth(text);
        if (strW <= maxW || strW == 0) {
            drawCenteredString(this.fontRendererObj, text, centerX, centerY, color);
            return;
        }

        float scale = (float) maxW / (float) strW;
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(centerX, centerY + 4, 0.0F);
        GLStateManager.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawStringWithShadow(text, -strW / 2, -4, color);
        GLStateManager.glPopMatrix();
    }

    public void playClickSound() {
        this.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    public static void drawTexturedQuad(int x, int y, int w, int h, int u, int v, int uW, int vH, int texW, int texH) {
        float f = 1.0F / (float) texW;
        float f1 = 1.0F / (float) texH;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0.0D, (float) u * f, (float) (v + vH) * f1);
        tess.addVertexWithUV(x + w, y + h, 0.0D, (float) (u + uW) * f, (float) (v + vH) * f1);
        tess.addVertexWithUV(x + w, y, 0.0D, (float) (u + uW) * f, (float) v * f1);
        tess.addVertexWithUV(x, y, 0.0D, (float) u * f, (float) v * f1);
        tess.draw();
    }
}
