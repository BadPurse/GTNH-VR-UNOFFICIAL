package com.gtnhvr.gui.settings.categories.rendering;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsMixedReality extends VROptionsSet {

    public VRSettingsMixedReality(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.rendering.mixed_reality.render_hands"),
                () -> VRClientSettings.mixedRealityRenderHands,
                v -> { VRClientSettings.mixedRealityRenderHands = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.mixed_reality.render_hands.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.rendering.mixed_reality.as_grid_2_x_2"),
                () -> VRClientSettings.mixedRealityAsGrid2x2,
                v -> { VRClientSettings.mixedRealityAsGrid2x2 = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.mixed_reality.as_grid_2_x_2.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.rendering.mixed_reality.with_first_person"),
                () -> VRClientSettings.mixedRealityWithFirstPerson,
                v -> { VRClientSettings.mixedRealityWithFirstPerson = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.mixed_reality.with_first_person.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.rendering.mixed_reality.alpha_mask"),
                () -> VRClientSettings.mixedRealityAlphaMask,
                v -> { VRClientSettings.mixedRealityAlphaMask = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.mixed_reality.alpha_mask.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.rendering.mixed_reality.fov"),
                20.0f, 120.0f, 5.0f,
                () -> VRClientSettings.mixedRealityFov,
                v -> { VRClientSettings.mixedRealityFov = v; VRClientSettings.save(); },
                v -> String.format("%.0f°", v),
                I18n.format("visor.options.rendering.mixed_reality.fov.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 2,
                () -> "Aspect: " + (Math.abs(VRClientSettings.mixedRealityAspectRatio - 16.0f / 9.0f) < 0.05f ? "16:9" : "4:3"),
                () -> {
                    if (Math.abs(VRClientSettings.mixedRealityAspectRatio - 16.0f / 9.0f) < 0.05f) {
                        VRClientSettings.mixedRealityAspectRatio = 4.0f / 3.0f;
                    } else {
                        VRClientSettings.mixedRealityAspectRatio = 16.0f / 9.0f;
                    }
                    VRClientSettings.save();
                },
                "Mixed Reality desktop capture aspect ratio"
        ));
    }
}
