package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.gui.settings.categories.gui.VRSettingsGuiHotbar;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsGui extends VROptionsSet {

    public VRSettingsGui(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Keyboard Layouts Submenu & Hotbar Submenu
        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.controls.keyboard_layouts.button"),
                () -> new com.gtnhvr.gui.settings.categories.gui.VRSettingsKeyboardLayouts(screen, this),
                I18n.format("visor.options.controls.keyboard_layouts.button")
        ));

        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.gui.hotbar.button"),
                () -> new VRSettingsGuiHotbar(screen, this),
                I18n.format("visor.options.gui.hotbar.button")
        ));

        // Row 1: HUD Scale & HUD Distance
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.gui.hud_scale"),
                0.5f, 2.5f, 0.1f,
                () -> VRClientSettings.hudScale,
                v -> {
                    VRClientSettings.hudScale = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.1fx", v),
                I18n.format("visor.options.gui.hud_scale")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.gui.hud_distance"),
                0.5f, 3.5f, 0.25f,
                () -> VRClientSettings.hudDistance,
                v -> {
                    VRClientSettings.hudDistance = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.2f m", v),
                I18n.format("visor.options.gui.hud_distance")
        ));

        // Row 2: Settings Text Scale & Curved HUD
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.gui.settings_text_scale"),
                0.5f, 1.1f, 0.05f,
                () -> VRClientSettings.settingsTextScale,
                v -> {
                    VRClientSettings.settingsTextScale = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.2f", v),
                I18n.format("visor.options.gui.settings_text_scale.tooltip")
        ));

        // Row 3: Curved HUD & Keyboard Auto Layout
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 3,
                I18n.format("visor.options.gui.curved_hud"),
                () -> VRClientSettings.curvedHud,
                v -> {
                    VRClientSettings.curvedHud = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.gui.curved_hud")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 3,
                I18n.format("visor.options.gui.keyboard.auto_layout"),
                () -> VRClientSettings.keyboardAutoLayout,
                v -> {
                    VRClientSettings.keyboardAutoLayout = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.gui.keyboard.auto_layout")
        ));
    }
}
