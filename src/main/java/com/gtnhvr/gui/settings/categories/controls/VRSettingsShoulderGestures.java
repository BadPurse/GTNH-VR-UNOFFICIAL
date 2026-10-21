package com.gtnhvr.gui.settings.categories.controls;

import com.gtnhvr.gameplay.VRShoulderGestures;
import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import java.util.List;

/**
 * Settings for the two opposite-shoulder controller gestures.
 * Bindings are selected from registered Minecraft/Forge key bindings instead of
 * depending exclusively on hard-coded translation-key names.
 */
public final class VRSettingsShoulderGestures extends VROptionsSet {

    public VRSettingsShoulderGestures(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("gtnhvr.controls.shoulder.enabled"),
                () -> VRClientSettings.shoulderJetpackEnabled,
                value -> {
                    VRClientSettings.shoulderJetpackEnabled = value;
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.enabled.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("gtnhvr.controls.shoulder.jetpack"),
                () -> VRClientSettings.shoulderJetpackGestureEnabled,
                value -> {
                    VRClientSettings.shoulderJetpackGestureEnabled = value;
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.jetpack.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("gtnhvr.controls.shoulder.glide"),
                () -> VRClientSettings.shoulderGlideGestureEnabled,
                value -> {
                    VRClientSettings.shoulderGlideGestureEnabled = value;
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.glide.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 2,
                () -> I18n.format("gtnhvr.controls.shoulder.jetpack_key") + ": "
                        + VRShoulderGestures.getBindingDisplayName(
                                Minecraft.getMinecraft(), VRClientSettings.shoulderJetpackKeyBinding),
                () -> {
                    VRClientSettings.shoulderJetpackKeyBinding = cycleBinding(
                            VRShoulderGestures.getJetpackKeyBindingChoices(Minecraft.getMinecraft()),
                            VRClientSettings.shoulderJetpackKeyBinding);
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.jetpack_key.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 2,
                () -> I18n.format("gtnhvr.controls.shoulder.glide_key") + ": "
                        + VRShoulderGestures.getBindingDisplayName(
                                Minecraft.getMinecraft(), VRClientSettings.shoulderGlideKeyBinding),
                () -> {
                    VRClientSettings.shoulderGlideKeyBinding = cycleBinding(
                            VRShoulderGestures.getGlideKeyBindingChoices(Minecraft.getMinecraft()),
                            VRClientSettings.shoulderGlideKeyBinding);
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.glide_key.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 3,
                I18n.format("gtnhvr.controls.shoulder.distance"),
                0.10F, 0.45F, 0.01F,
                () -> VRClientSettings.shoulderGestureDistance,
                value -> {
                    VRClientSettings.shoulderGestureDistance = value;
                    VRClientSettings.save();
                },
                value -> String.format("%.0f cm", value * 100.0F),
                I18n.format("gtnhvr.controls.shoulder.distance.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 3,
                I18n.format("gtnhvr.controls.shoulder.hold"),
                0.25F, 2.50F, 0.25F,
                () -> VRClientSettings.shoulderGestureHoldSeconds,
                value -> {
                    VRClientSettings.shoulderGestureHoldSeconds = value;
                    VRClientSettings.save();
                },
                value -> String.format("%.2f s", value),
                I18n.format("gtnhvr.controls.shoulder.hold.tooltip")
        ));
    }

    private static String cycleBinding(List<String> choices, String current) {
        if (choices == null || choices.isEmpty()) return "AUTO";
        int index = choices.indexOf(current);
        return choices.get((index + 1) % choices.size());
    }
}
