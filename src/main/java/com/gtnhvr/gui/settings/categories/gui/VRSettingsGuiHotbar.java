package com.gtnhvr.gui.settings.categories.gui;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsGuiHotbar extends VROptionsSet {

    public VRSettingsGuiHotbar(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Disable Vanilla Hotbar in HUD & Show Slot Numbers
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.gui_hotbar.hud_disabled"),
                () -> VRClientSettings.hudDisabledHotbar,
                v -> {
                    VRClientSettings.hudDisabledHotbar = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.gui_hotbar.hud_disabled.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.gui_hotbar.slot_numbers"),
                () -> VRClientSettings.hotbarSlotNumbers,
                v -> {
                    VRClientSettings.hotbarSlotNumbers = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.gui_hotbar.slot_numbers.tooltip")
        ));

        // Row 1: Center Deadzone Radius & Hysteresis Margin
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.gui_hotbar.center_radius"),
                10.0f, 90.0f, 5.0f,
                () -> VRClientSettings.hotbarCenterRadius,
                v -> {
                    VRClientSettings.hotbarCenterRadius = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f px", v),
                I18n.format("visor.options.gui_hotbar.center_radius.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.gui_hotbar.hysteresis_margin"),
                0.0f, 15.0f, 1.0f,
                () -> VRClientSettings.hotbarHysteresisMargin,
                v -> {
                    VRClientSettings.hotbarHysteresisMargin = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f°", v),
                I18n.format("visor.options.gui_hotbar.hysteresis_margin.tooltip")
        ));
    }
}
