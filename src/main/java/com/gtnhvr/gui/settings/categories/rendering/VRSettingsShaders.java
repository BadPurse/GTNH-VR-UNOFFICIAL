package com.gtnhvr.gui.settings.categories.rendering;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsShaders extends VROptionsSet {

    public VRSettingsShaders(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.rendering.shaders.per_eye_pipelines"),
                () -> VRClientSettings.shaderPerEyePipelines,
                v -> { VRClientSettings.shaderPerEyePipelines = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.shaders.per_eye_pipelines.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.rendering.shaders.shared_shadows"),
                () -> VRClientSettings.shaderSharedShadows,
                v -> { VRClientSettings.shaderSharedShadows = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.shaders.shared_shadows.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.rendering.shaders.shared_ssbo"),
                () -> VRClientSettings.shaderSharedSsbo,
                v -> { VRClientSettings.shaderSharedSsbo = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.shaders.shared_ssbo.tooltip")
        ));
    }
}
