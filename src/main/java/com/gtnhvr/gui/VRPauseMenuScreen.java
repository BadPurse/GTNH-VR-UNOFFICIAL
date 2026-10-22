package com.gtnhvr.gui;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 Visor VRPauseMenuScreen for Minecraft 1.7.10:
 * The dedicated in-game VR menu that replaces the vanilla pause screen
 * when playing in VR. Provides tabs for Main, Commands, and Tools with laser-aim support.
 */
@Lwjgl3Aware
public class VRPauseMenuScreen extends GuiScreen {

    private static final int COLUMN_W = 204;
    private static final int BTN_H = 20;
    private static final int BTN_HALF = (COLUMN_W - 4) / 2; // 100
    private static final int GAP = 4;
    private static final int TAB_W = (COLUMN_W - 8) / 3; // 65
    private static final int BTN_QUARTER = (COLUMN_W - 3 * GAP) / 4; // 48
    private static final int BTN_THIRD = (COLUMN_W - 2 * GAP) / 3; // 65
    private static final int LABEL_H = 11;
    private static final int SECTION_GAP = 6;

    private enum Tab {
        MAIN("visor.screen.pause_menu.tab.main"),
        COMMANDS("visor.screen.pause_menu.tab.commands"),
        TOOLS("visor.screen.pause_menu.tab.tools");

        final String langKey;
        Tab(String key) { this.langKey = key; }
    }

    private Tab currentTab = Tab.MAIN;

    // Section header rendering positions
    private static class SectionHeader {
        final int x, y;
        final String text;
        SectionHeader(int x, int y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }
    }
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();

    public VRPauseMenuScreen() {}

    @SuppressWarnings("unchecked")
    @Override
    public void initGui() {
        this.buttonList.clear();
        this.sectionHeaders.clear();

        boolean hasPerms = (this.mc != null && this.mc.thePlayer != null) &&
                (this.mc.isIntegratedServerRunning() || this.mc.thePlayer.canCommandSenderUseCommand(2, ""));

        if (this.currentTab == Tab.COMMANDS && !hasPerms) {
            this.currentTab = Tab.MAIN;
        }

        int cx = this.width / 2;
        int startY = this.height / 2 - totalColumnHeight() / 2;
        int y = startY + 9 + 16;
        int tx = cx - COLUMN_W / 2;

        // Top tab buttons
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int btnX = tx + i * (TAB_W + 4);
            GuiButton tabBtn = new GuiButton(100 + i, btnX, y, TAB_W, BTN_H, I18n.format(tab.langKey));
            if (tab == Tab.COMMANDS && !hasPerms) {
                tabBtn.visible = false;
            } else {
                tabBtn.visible = true;
                tabBtn.enabled = (this.currentTab != tab);
            }
            this.buttonList.add(tabBtn);
        }

        y += BTN_H + GAP + 2;
        y = buildContent(cx, y);
        y += 4;

        // Bottom Quit / Disconnect button
        String quitText = (this.mc.isIntegratedServerRunning())
                ? I18n.format("menu.returnToMenu")
                : I18n.format("menu.disconnect");
        this.buttonList.add(new GuiButton(99, cx - COLUMN_W / 2, y, COLUMN_W, BTN_H, quitText));
    }

    @SuppressWarnings("unchecked")
    private int buildContent(int cx, int y) {
        int left = cx - COLUMN_W / 2;
        int right = left + BTN_HALF + GAP;

        switch (this.currentTab) {
            case MAIN -> {
                // Row 0: Inventory & Height Calibration
                this.buttonList.add(new GuiButton(1, left, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.inventory")));
                this.buttonList.add(new GuiButton(2, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.measure_height")));
                y += BTN_H + GAP;

                // Row 1: Quest Book & Chat
                this.buttonList.add(new GuiButton(3, left, y, BTN_HALF, BTN_H, "§6" + I18n.format("visor.screen.pause_menu.button.quest_book")));
                this.buttonList.add(new GuiButton(4, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.chat")));
                y += BTN_H + GAP;

                // Row 2: Vanilla Pause Menu & VR Settings
                this.buttonList.add(new GuiButton(5, left, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.pause_menu")));
                this.buttonList.add(new GuiButton(6, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.vr_settings")));
                y += BTN_H + GAP;
            }

            case COMMANDS -> {
                // Section 1: Game Mode
                registerSection(left, y, I18n.format("visor.screen.pause_menu.section.game_mode"));
                y += LABEL_H;

                this.buttonList.add(new GuiButton(10, left, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.survival")));
                this.buttonList.add(new GuiButton(11, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.creative")));
                y += BTN_H + GAP;

                this.buttonList.add(new GuiButton(12, left, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.spectator")));
                this.buttonList.add(new GuiButton(13, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.adventure")));
                y += BTN_H + GAP;

                y += SECTION_GAP;

                // Section 2: Time
                registerSection(left, y, I18n.format("visor.screen.pause_menu.section.time"));
                y += LABEL_H;

                String[] timeLabels = {
                        I18n.format("visor.screen.pause_menu.button.dawn"),
                        I18n.format("visor.screen.pause_menu.button.noon"),
                        I18n.format("visor.screen.pause_menu.button.dusk"),
                        I18n.format("visor.screen.pause_menu.button.night")
                };
                for (int i = 0; i < 4; i++) {
                    int btnX = left + i * (BTN_QUARTER + GAP);
                    this.buttonList.add(new GuiButton(20 + i, btnX, y, BTN_QUARTER, BTN_H, timeLabels[i]));
                }
                y += BTN_H + GAP;

                y += SECTION_GAP;

                // Section 3: Weather
                registerSection(left, y, I18n.format("visor.screen.pause_menu.section.weather"));
                y += LABEL_H;

                String[] weatherLabels = {
                        I18n.format("visor.screen.pause_menu.button.clear"),
                        I18n.format("visor.screen.pause_menu.button.rain"),
                        I18n.format("visor.screen.pause_menu.button.thunder")
                };
                for (int i = 0; i < 3; i++) {
                    int btnX = left + i * (BTN_THIRD + GAP);
                    this.buttonList.add(new GuiButton(30 + i, btnX, y, BTN_THIRD, BTN_H, weatherLabels[i]));
                }
                y += BTN_H + GAP;
            }

            case TOOLS -> {
                // Row 0: Hitboxes & Chunk Borders
                this.buttonList.add(new GuiButton(40, left, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.hitboxes")));
                this.buttonList.add(new GuiButton(41, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.chunk_borders")));
                y += BTN_H + GAP;

                // Row 1: Reload Chunks & Clear Chat
                this.buttonList.add(new GuiButton(42, left, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.reload_chunks")));
                this.buttonList.add(new GuiButton(43, right, y, BTN_HALF, BTN_H, I18n.format("visor.screen.pause_menu.button.clear_chat")));
                y += BTN_H + GAP;

                // Row 2: Hand Item Offsets Screen
                this.buttonList.add(new GuiButton(44, left, y, COLUMN_W, BTN_H, "§6" + I18n.format("visor.screen.pause_menu.button.hand_item_offsets")));
                y += BTN_H + GAP;

                // Row 3: Toggle the F7-style mob spawn markers.
                this.buttonList.add(new GuiButton(45, left, y, COLUMN_W, BTN_H, mobSpawnButtonLabel()));
                y += BTN_H + GAP;
            }
        }

        return y;
    }

    private String mobSpawnButtonLabel() {
        String stateKey = VRMobSpawnOverlay.getInstance().isEnabled()
                ? "visor.screen.pause_menu.state.on"
                : "visor.screen.pause_menu.state.off";
        return I18n.format("visor.screen.pause_menu.button.mob_spawns", I18n.format(stateKey));
    }

    private void registerSection(int x, int y, String text) {
        this.sectionHeaders.add(new SectionHeader(x, y, text));
    }

    private int totalColumnHeight() {
        int contentH = switch (this.currentTab) {
            case MAIN -> 3 * (BTN_H + GAP);
            case TOOLS -> 4 * (BTN_H + GAP);
            case COMMANDS -> 3 * LABEL_H + 4 * (BTN_H + GAP) + 2 * SECTION_GAP;
        };
        return 9 + 16 + BTN_H + 6 + contentH + 6 + BTN_H;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;

        // Tabs
        if (button.id >= 100 && button.id <= 102) {
            this.currentTab = Tab.values()[button.id - 100];
            initGui();
            return;
        }

        switch (button.id) {
            // Main Tab
            case 1 -> { // Inventory
                if (this.mc != null && this.mc.thePlayer != null) {
                    this.mc.displayGuiScreen(new GuiInventory(this.mc.thePlayer));
                }
            }
            case 2 -> { // Height Calibration
                float hmdY = HeadPoseController.getY();
                if (hmdY > 0.5f && hmdY < 2.5f) {
                    VRClientSettings.playerHeight = hmdY;
                    VRClientSettings.save();
                    if (this.mc != null && this.mc.thePlayer != null) {
                        this.mc.thePlayer.addChatMessage(new ChatComponentText(
                                "§a[Visor VR] " + I18n.format("visor.options.vr_body_height.standing") + ": " + String.format("%.2f m", hmdY)
                        ));
                    }
                }
            }
            case 3 -> { // Quest Book
                openQuestBook(this.mc);
            }
            case 4 -> { // Chat
                if (this.mc != null) {
                    this.mc.displayGuiScreen(new GuiChat());
                }
            }
            case 5 -> { // Vanilla Pause Menu
                GuiIngameMenu pauseMenu = new GuiIngameMenu();
                // Allow this exact instance through the VR redirect; do not consume the bypass
                // on an unrelated GUI init event.
                GuiEventHandler.setBypassVanillaRedirect(pauseMenu);
                this.mc.displayGuiScreen(pauseMenu);
            }
            case 6 -> { // VR Settings
                this.mc.displayGuiScreen(new VRSettingsScreen(this));
            }

            // Commands Tab: Game Modes
            case 10 -> sendCommand("/gamemode survival");
            case 11 -> sendCommand("/gamemode creative");
            case 12 -> sendCommand("/gamemode spectator");
            case 13 -> sendCommand("/gamemode adventure");

            // Commands Tab: Time
            case 20 -> sendCommand("/time set 0");
            case 21 -> sendCommand("/time set 6000");
            case 22 -> sendCommand("/time set 12000");
            case 23 -> sendCommand("/time set 18000");

            // Commands Tab: Weather
            case 30 -> sendCommand("/weather clear");
            case 31 -> sendCommand("/weather rain");
            case 32 -> sendCommand("/weather thunder");

            // Tools Tab
            case 40 -> { // Hitboxes
                net.minecraft.client.renderer.entity.RenderManager.debugBoundingBox =
                        !net.minecraft.client.renderer.entity.RenderManager.debugBoundingBox;
                if (this.mc.thePlayer != null) {
                    this.mc.thePlayer.addChatMessage(new ChatComponentText(
                            "§e[Visor VR] Hitboxes: " + (net.minecraft.client.renderer.entity.RenderManager.debugBoundingBox ? "§aON" : "§cOFF")
                    ));
                }
            }
            case 41 -> { // Chunk borders
                if (this.mc.thePlayer != null) {
                    this.mc.thePlayer.addChatMessage(new ChatComponentText("§b[Visor VR] Chunk borders"));
                }
            }
            case 42 -> { // Reload chunks
                if (this.mc.renderGlobal != null) {
                    this.mc.renderGlobal.loadRenderers();
                    if (this.mc.thePlayer != null) {
                        this.mc.thePlayer.addChatMessage(new ChatComponentText("§a[Visor VR] Chunks reloaded"));
                    }
                }
            }
            case 43 -> { // Clear chat
                if (this.mc.ingameGUI != null && this.mc.ingameGUI.getChatGUI() != null) {
                    this.mc.ingameGUI.getChatGUI().clearChatMessages();
                }
            }
            case 44 -> { // Hand Item Offsets
                VRSettingsScreen settings = new VRSettingsScreen(this);
                settings.pushOptionsSet(new com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets(settings, null));
                this.mc.displayGuiScreen(settings);
            }
            case 45 -> { // Mob spawn markers
                VRMobSpawnOverlay overlay = VRMobSpawnOverlay.getInstance();
                overlay.toggle();
                button.displayString = mobSpawnButtonLabel();
                if (this.mc != null && this.mc.thePlayer != null) {
                    this.mc.thePlayer.addChatMessage(new ChatComponentText(
                            "§e[Visor VR] " + mobSpawnButtonLabel()
                    ));
                }
            }

            // Bottom Quit / Disconnect
            case 99 -> {
                boolean isLocal = this.mc.isIntegratedServerRunning();
                if (this.mc.theWorld != null) {
                    this.mc.theWorld.sendQuittingDisconnectingPacket();
                }
                this.mc.loadWorld((WorldClient) null);
                if (isLocal) {
                    this.mc.displayGuiScreen(new GuiMainMenu());
                } else {
                    this.mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
                }
            }
        }
    }

    public static void openQuestBook(Minecraft mc) {
        if (mc == null) return;
        try {
            Class<?> clazz = Class.forName("betterquesting.client.gui2.GuiHome");
            java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(net.minecraft.client.gui.GuiScreen.class);
            net.minecraft.client.gui.GuiScreen screen = (net.minecraft.client.gui.GuiScreen) ctor.newInstance((Object) null);
            mc.displayGuiScreen(screen);
            return;
        } catch (Throwable t1) {
            try {
                Class<?> hqmClazz = Class.forName("hardcorequesting.client.interfaces.GuiQuestBook");
                net.minecraft.client.gui.GuiScreen hqmScreen = (net.minecraft.client.gui.GuiScreen) hqmClazz.newInstance();
                mc.displayGuiScreen(hqmScreen);
                return;
            } catch (Throwable t2) {
                if (mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage("/bq_user default");
                }
            }
        }
    }

    private void sendCommand(String cmd) {
        if (this.mc != null && this.mc.thePlayer != null) {
            this.mc.thePlayer.sendChatMessage(cmd);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int cx = this.width / 2;
        int startY = this.height / 2 - totalColumnHeight() / 2;

        // Title
        drawCenteredString(this.fontRendererObj, I18n.format("visor.screen.pause_menu.title"), cx, startY, 0xFFFFFFFF);

        int dividerColor = 0xFF555555;
        int divY = startY + 9 + 10;
        drawRect(cx - COLUMN_W / 2, divY, cx + COLUMN_W / 2, divY + 1, dividerColor);

        int tabStripY = startY + 9 + 16;
        int accentX = cx - COLUMN_W / 2 + this.currentTab.ordinal() * (TAB_W + 4);
        // Cyan accent indicator above active tab (1:1 Visor)
        drawRect(accentX, tabStripY - 2, accentX + TAB_W, tabStripY - 1, 0xFF6AE3EA);

        // Section headers in COMMANDS tab
        if (this.currentTab == Tab.COMMANDS) {
            for (SectionHeader header : this.sectionHeaders) {
                int lblW = this.fontRendererObj.getStringWidth(header.text);
                int lineY = header.y + 4;

                drawRect(header.x, lineY, header.x + 18, lineY + 1, dividerColor);
                drawRect(header.x + 22 + lblW, lineY, header.x + COLUMN_W, lineY + 1, dividerColor);
                drawString(this.fontRendererObj, header.text, header.x + 20, header.y, 0xFF6AE3EA);
            }
        }

        // [X] Close button in top-right corner
        int btnW = 20;
        int btnH = 16;
        int closeX = cx + COLUMN_W / 2 - btnW;
        int closeY = startY;
        boolean closeHover = mouseX >= closeX - 4 && mouseX <= closeX + btnW + 4 && mouseY >= closeY - 2 && mouseY <= closeY + btnH + 4;
        drawRect(closeX - 2, closeY - 2, closeX + btnW + 2, closeY + btnH, closeHover ? 0x99AA2222 : 0x55000000);
        drawCenteredString(this.fontRendererObj, "✕", closeX + btnW / 2, closeY + 2, closeHover ? 0xFFFFFFFF : 0xFFAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int cx = this.width / 2;
        int startY = this.height / 2 - totalColumnHeight() / 2;
        int btnW = 20;
        int btnH = 16;
        int closeX = cx + COLUMN_W / 2 - btnW;
        int closeY = startY;
        if (mouseButton == 0 && mouseX >= closeX - 6 && mouseX <= closeX + btnW + 6 && mouseY >= closeY - 4 && mouseY <= closeY + btnH + 6) {
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == 1) { // ESC
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
