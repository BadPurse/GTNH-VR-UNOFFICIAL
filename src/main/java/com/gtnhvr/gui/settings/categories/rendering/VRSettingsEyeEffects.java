package com.gtnhvr.gui.settings.categories.rendering;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsEyeEffects extends VROptionsSet {

    public VRSettingsEyeEffects(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.rendering.effects.eye_effects.low_health_indicator"),
                () -> VRClientSettings.lowHealthIndicatorEnabled,
                v -> { VRClientSettings.lowHealthIndicatorEnabled = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.effects.eye_effects.low_health_indicator.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.rendering.effects.eye_effects.hit_indicator"),
                () -> VRClientSettings.hitIndicatorEnabled,
                v -> { VRClientSettings.hitIndicatorEnabled = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.effects.eye_effects.hit_indicator.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.rendering.effects.eye_effects.freeze"),
                () -> VRClientSettings.freezeEffectEnabled,
                v -> { VRClientSettings.freezeEffectEnabled = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.effects.eye_effects.freeze.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.rendering.effects.eye_effects.pumpkin"),
                () -> VRClientSettings.pumpkinEffectEnabled,
                v -> { VRClientSettings.pumpkinEffectEnabled = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.effects.eye_effects.pumpkin.tooltip")
        ));
    }
}
