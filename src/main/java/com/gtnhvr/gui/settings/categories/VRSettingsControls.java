package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsControls extends VROptionsSet {

    public VRSettingsControls(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Handedness & Controller Bindings
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.controls.left_handed"),
                () -> VRClientSettings.leftHanded,
                v -> {
                    VRClientSettings.leftHanded = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.controls.left_handed")
        ));

        // Configure all digital controller inputs, thumbstick directions, and shoulder gestures.
        // A binding opens the existing VR virtual keyboard for selection.
        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("gtnhvr.controls.bindings.title"),
                () -> new com.gtnhvr.gui.settings.categories.controls.VRSettingsControllerBindings(screen, this),
                I18n.format("gtnhvr.controls.bindings.tooltip")
        ));

        // Row 1: Controller Vibration / Haptics
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.vr_body.body_haptics"),
                0.0f, 1.0f, 0.05f,
                () -> VRClientSettings.hapticStrength,
                v -> {
                    VRClientSettings.hapticStrength = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f%%", v * 100.0f),
                I18n.format("visor.options.vr_body.body_haptics.tooltip")
        ));

        // Row 1 Right: Keyboard Auto Layout toggle
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.gui.keyboard.auto_layout"),
                () -> VRClientSettings.keyboardAutoLayout,
                v -> {
                    VRClientSettings.keyboardAutoLayout = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.gui.keyboard.auto_layout")
        ));

        // Row 2: Custom Item / Hand Offsets
        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.LEFT, 2,
                "§6" + I18n.format("visor.options.controls.hand_item_offsets.button"),
                () -> new com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets(screen, this),
                I18n.format("visor.options.item_offsets.category.tooltip")
        ));

    }
}
