package com.gtnhvr.gui;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

@Lwjgl3Aware
public class GuiVRSettings extends GuiScreen {
    private final GuiScreen parentScreen;

    private GuiButton btnVREnabled;
    private GuiButton btnHeadTracking;
    private GuiButton btnSnapTurn;
    private GuiButton btnWorldScale;
    private GuiButton btnMirrorMode;
    private GuiButton btnRecenter;
    private GuiButton btnDone;

    public GuiVRSettings(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int topY = this.height / 6;

        btnVREnabled = new GuiButton(1, centerX - 155, topY, 150, 20, getVREnabledText());
        btnHeadTracking = new GuiButton(2, centerX + 5, topY, 150, 20, getHeadTrackingText());

        btnSnapTurn = new GuiButton(3, centerX - 155, topY + 28, 150, 20, getSnapTurnText());
        btnWorldScale = new GuiButton(4, centerX + 5, topY + 28, 150, 20, getWorldScaleText());

        btnMirrorMode = new GuiButton(5, centerX - 155, topY + 56, 150, 20, getMirrorModeText());
        btnRecenter = new GuiButton(6, centerX + 5, topY + 56, 150, 20, "§eRecenter HMD");

        btnDone = new GuiButton(200, centerX - 100, topY + 120, 200, 20, I18n.format("gui.done"));

        this.buttonList.add(btnVREnabled);
        this.buttonList.add(btnHeadTracking);
        this.buttonList.add(btnSnapTurn);
        this.buttonList.add(btnWorldScale);
        this.buttonList.add(btnMirrorMode);
        this.buttonList.add(btnRecenter);
        this.buttonList.add(btnDone);
    }

    private String getVREnabledText() {
        return "VR Mode: " + (VRConfig.vrEnabled ? "§aEnabled" : "§cDisabled");
    }

    private String getHeadTrackingText() {
        return "Head Tracking: " + (VRConfig.headTracking ? "§aON" : "§7OFF");
    }

    private String getSnapTurnText() {
        return "Turning: " + (VRConfig.snapTurning ? "§aSnap (45°)" : "§bSmooth");
    }

    private String getWorldScaleText() {
        return "World Scale: §e" + String.format("%.1fx", com.gtnhvr.settings.VRClientSettings.worldScale);
    }

    private String getMirrorModeText() {
        switch (VRConfig.mirrorMode) {
            case 0: return "Mirror: §aBoth Eyes";
            case 1: return "Mirror: §bLeft Eye";
            case 2: return "Mirror: §bRight Eye";
            default: return "Mirror: §7Off";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;

        if (button.id == 1) {
            VRConfig.vrEnabled = !VRConfig.vrEnabled;
            btnVREnabled.displayString = getVREnabledText();
            VRConfig.save();
        } else if (button.id == 2) {
            VRConfig.headTracking = !VRConfig.headTracking;
            btnHeadTracking.displayString = getHeadTrackingText();
            VRConfig.save();
        } else if (button.id == 3) {
            VRConfig.snapTurning = !VRConfig.snapTurning;
            btnSnapTurn.displayString = getSnapTurnText();
            VRConfig.save();
        } else if (button.id == 4) {
            if (com.gtnhvr.settings.VRClientSettings.worldScale < 0.85f) com.gtnhvr.settings.VRClientSettings.worldScale = 0.9f;
            else if (com.gtnhvr.settings.VRClientSettings.worldScale < 0.95f) com.gtnhvr.settings.VRClientSettings.worldScale = 1.0f;
            else if (com.gtnhvr.settings.VRClientSettings.worldScale < 1.05f) com.gtnhvr.settings.VRClientSettings.worldScale = 1.1f;
            else if (com.gtnhvr.settings.VRClientSettings.worldScale < 1.15f) com.gtnhvr.settings.VRClientSettings.worldScale = 1.2f;
            else com.gtnhvr.settings.VRClientSettings.worldScale = 0.8f;
            btnWorldScale.displayString = getWorldScaleText();
            VRConfig.save();
        } else if (button.id == 5) {
            VRConfig.mirrorMode = (VRConfig.mirrorMode + 1) % 4;
            btnMirrorMode.displayString = getMirrorModeText();
            VRConfig.save();
        } else if (button.id == 6) {
            HeadPoseController.recenter();
        } else if (button.id == 200) {
            VRConfig.save();
            this.mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "§lGT New Horizons — VR Settings", this.width / 2, 20, 0xFFFFFF);

        String status;
        if (OpenXRRuntime.isSessionRunning()) {
            status = "§a● OpenXR Session ACTIVE (HMD Tracking)";
        } else if (OpenXRRuntime.isReady()) {
            status = "§e● OpenXR Ready (Waiting for HMD)";
        } else {
            status = "§c● OpenXR Inactive / Headset Not Detected";
        }
        this.drawCenteredString(this.fontRendererObj, status, this.width / 2, 40, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
