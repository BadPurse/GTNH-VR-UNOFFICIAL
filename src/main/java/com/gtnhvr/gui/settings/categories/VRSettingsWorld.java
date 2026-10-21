package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class VRSettingsWorld extends VROptionsSet {

    public VRSettingsWorld(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    private boolean isEditable() {
        return Minecraft.getMinecraft().theWorld == null;
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Two-Handed Weapons & Better Swinging
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.world.two_handed"),
                () -> VRClientSettings.worldTwoHanded,
                v -> {
                    VRClientSettings.worldTwoHanded = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.world.two_handed.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.world.better_swinging"),
                () -> VRClientSettings.worldBetterSwinging,
                v -> {
                    VRClientSettings.worldBetterSwinging = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.world.better_swinging.tooltip")
        ));

        // Row 1: Roomscale Shield & Attacks While Blocking
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.world.roomscale_shield"),
                () -> VRClientSettings.worldRoomscaleShield,
                v -> {
                    VRClientSettings.worldRoomscaleShield = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.world.roomscale_shield.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.world.attacks_while_blocking"),
                () -> VRClientSettings.worldAttacksWhileBlocking,
                v -> {
                    VRClientSettings.worldAttacksWhileBlocking = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.world.attacks_while_blocking.tooltip")
        ));

        // Row 2: Interaction Reach & Crosshair Laser
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.world.reach_distance"),
                2.5f, 6.0f, 0.25f,
                () -> VRClientSettings.interactionReach,
                v -> {
                    VRClientSettings.interactionReach = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.2f m", v),
                I18n.format("visor.options.world.reach_distance")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 2,
                I18n.format("visor.options.world.crosshair_laser"),
                () -> VRClientSettings.crosshairLaser,
                v -> {
                    VRClientSettings.crosshairLaser = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.world.crosshair_laser")
        ));

        // Row 3: Mob Spawn Overlay (F7) & Jetpack Shoulder Gestures
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 3,
                "Mob Spawn Overlay (F7)",
                () -> com.gtnhvr.gui.VRMobSpawnOverlay.getInstance().isEnabled(),
                v -> com.gtnhvr.gui.VRMobSpawnOverlay.getInstance().setEnabled(v),
                "Shows colored crosses where hostile mobs can spawn. Red = always, Yellow = at night."
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 3,
                "Shoulder Jetpack Gestures",
                () -> VRClientSettings.shoulderJetpackEnabled,
                v -> {
                    VRClientSettings.shoulderJetpackEnabled = v;
                    VRClientSettings.save();
                },
                "Point right hand at left shoulder for 1s to toggle jetpack. Left hand at right shoulder to toggle glide."
        ));
    }
}
