package com.gtnhvr.gui.settings.categories.rendering;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsEffects extends VROptionsSet {

    public VRSettingsEffects(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.rendering.effects.eye_effects.button"),
                () -> new VRSettingsEyeEffects(screen, this),
                I18n.format("visor.options.rendering.effects.eye_effects.button")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.rendering.effects.self_shadow"),
                () -> VRClientSettings.selfShadowEnabled,
                v -> { VRClientSettings.selfShadowEnabled = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.effects.self_shadow.tooltip")
        ));
    }
}
