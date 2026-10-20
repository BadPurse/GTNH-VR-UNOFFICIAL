package com.gtnhvr.gui.settings.categories.immersion;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsImmersionAdvanced extends VROptionsSet {

    public VRSettingsImmersionAdvanced(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Sneak & Crawl thresholds
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.immersion.room_sneak.threshold"),
                0.75f, 0.90f, 0.01f,
                () -> VRClientSettings.roomSneakThreshold,
                v -> {
                    VRClientSettings.roomSneakThreshold = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f%%", v * 100.0f),
                I18n.format("visor.options.immersion.room_sneak.threshold.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.immersion.room_crawl.threshold"),
                0.55f, 0.75f, 0.01f,
                () -> VRClientSettings.roomCrawlThreshold,
                v -> {
                    VRClientSettings.roomCrawlThreshold = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f%%", v * 100.0f),
                I18n.format("visor.options.immersion.room_crawl.threshold.tooltip")
        ));

        // Row 1: Jump threshold & Swing speed threshold
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.immersion.room_jump.threshold"),
                1.01f, 1.20f, 0.01f,
                () -> VRClientSettings.roomJumpThreshold,
                v -> {
                    VRClientSettings.roomJumpThreshold = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f%%", v * 100.0f),
                I18n.format("visor.options.immersion.room_jump.threshold.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.immersion.swing.speed_threshold"),
                1.0f, 5.0f, 0.25f,
                () -> VRClientSettings.swingSpeedThreshold,
                v -> {
                    VRClientSettings.swingSpeedThreshold = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.2f m/s", v),
                I18n.format("visor.options.immersion.swing.speed_threshold.tooltip")
        ));

        // Row 2: Swing in Creative & Room Consume
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.immersion.swing.in_creative"),
                () -> VRClientSettings.swingInCreative,
                v -> {
                    VRClientSettings.swingInCreative = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.swing.in_creative.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 2,
                I18n.format("visor.options.immersion.room_consume"),
                () -> VRClientSettings.roomConsume,
                v -> {
                    VRClientSettings.roomConsume = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_consume.tooltip")
        ));
    }
}
