package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.gui.settings.categories.rendering.*;
import com.gtnhvr.settings.GraphicsApi;
import com.gtnhvr.settings.MirrorMode;
import com.gtnhvr.settings.ScalingFilter;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsRendering extends VROptionsSet {

    public VRSettingsRendering(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 0,
                () -> I18n.format("visor.options.rendering.mirror_mode") + ": " + I18n.format("visor.options.enums.MirrorMode." + VRClientSettings.mirrorMode.name()),
                () -> {
                    MirrorMode[] modes = MirrorMode.values();
                    int next = (VRClientSettings.mirrorMode.ordinal() + 1) % modes.length;
                    VRClientSettings.mirrorMode = modes[next];
                    VRClientSettings.save();
                    init(); // Refresh dynamic buttons based on mirror mode
                },
                I18n.format("visor.options.rendering.mirror_mode.tooltip")
        ));

        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.rendering.effects.button"),
                () -> new VRSettingsEffects(screen, this),
                I18n.format("visor.options.rendering.effects.button")
        ));

        // Row 1
        if (VRClientSettings.mirrorMode == MirrorMode.CROPPED || VRClientSettings.mirrorMode == MirrorMode.SINGLE) {
            widgets.add(new OptionWidget.ButtonWidget(
                    OptionWidgetPosition.LEFT, 1,
                    () -> I18n.format("visor.options.rendering.mirror_eye") + ": " + (VRClientSettings.mirrorEye == 0 ? "Left" : "Right"),
                    () -> {
                        VRClientSettings.mirrorEye = (VRClientSettings.mirrorEye == 0) ? 1 : 0;
                        VRClientSettings.save();
                    },
                    I18n.format("visor.options.rendering.mirror_eye")
            ));
        } else if (VRClientSettings.mirrorMode == MirrorMode.THIRD_PERSON) {
            widgets.add(new OptionWidget.SubmenuWidget(
                    OptionWidgetPosition.LEFT, 1,
                    I18n.format("visor.options.rendering.third_person.button"),
                    () -> new VRSettingsThirdPerson(screen, this),
                    I18n.format("visor.options.rendering.third_person.button")
            ));
        } else if (VRClientSettings.mirrorMode == MirrorMode.MIXED_REALITY) {
            widgets.add(new OptionWidget.SubmenuWidget(
                    OptionWidgetPosition.LEFT, 1,
                    I18n.format("visor.options.rendering.mixed_reality.button"),
                    () -> new VRSettingsMixedReality(screen, this),
                    I18n.format("visor.options.rendering.mixed_reality.button")
            ));
        } else {
            widgets.add(new OptionWidget.SliderWidget(
                    OptionWidgetPosition.LEFT, 1,
                    I18n.format("visor.options.rendering.world_scale"),
                    0.5f, 2.0f, 0.1f,
                    () -> VRClientSettings.worldScale,
                    v -> { VRClientSettings.worldScale = v; VRClientSettings.save(); },
                    v -> String.format("%.1fx", v),
                    I18n.format("visor.options.rendering.world_scale.tooltip")
            ));
        }

        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.rendering.shaders.button"),
                () -> new VRSettingsShaders(screen, this),
                I18n.format("visor.options.rendering.shaders.button")
        ));

        // Row 2
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.rendering.world_scale"),
                0.5f, 2.0f, 0.1f,
                () -> VRClientSettings.worldScale,
                v -> { VRClientSettings.worldScale = v; VRClientSettings.save(); },
                v -> String.format("%.1fx", v),
                I18n.format("visor.options.rendering.world_scale.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 2,
                () -> I18n.format("visor.options.rendering.graphics_api") + ": " + VRClientSettings.graphicsApi.name(),
                () -> {
                    GraphicsApi[] apis = GraphicsApi.values();
                    int next = (VRClientSettings.graphicsApi.ordinal() + 1) % apis.length;
                    VRClientSettings.graphicsApi = apis[next];
                    VRClientSettings.save();
                },
                I18n.format("visor.options.rendering.graphics_api")
        ));

        // Row 3
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 3,
                "Head Tracking",
                () -> VRClientSettings.headTracking,
                v -> { VRClientSettings.headTracking = v; VRClientSettings.save(); },
                "Apply HMD rotation directly to Minecraft player camera"
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 3,
                I18n.format("visor.options.rendering.render_scale"),
                0.5f, 3.0f, 0.1f,
                () -> VRClientSettings.renderScaleFactor,
                v -> { VRClientSettings.renderScaleFactor = v; VRClientSettings.save(); },
                v -> String.format("%.0f%%", v * 100f),
                I18n.format("visor.options.rendering.render_scale.tooltip")
        ));

        // Row 4
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 4,
                I18n.format("visor.options.rendering.dh_mirror_passes"),
                () -> VRClientSettings.dhMirrorPasses,
                v -> { VRClientSettings.dhMirrorPasses = v; VRClientSettings.save(); },
                I18n.format("visor.options.rendering.dh_mirror_passes.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 4,
                () -> I18n.format("visor.options.rendering.scaling_filter") + ": " + VRClientSettings.scalingFilter.name(),
                () -> {
                    ScalingFilter[] filters = ScalingFilter.values();
                    int next = (VRClientSettings.scalingFilter.ordinal() + 1) % filters.length;
                    VRClientSettings.scalingFilter = filters[next];
                    VRClientSettings.save();
                },
                I18n.format("visor.options.rendering.scaling_filter")
        ));
    }
}
