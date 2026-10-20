package com.gtnhvr.gui.settings.categories.body;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.*;
import net.minecraft.client.resources.I18n;

public class VRSettingsBodyHeight extends VROptionsSet {
    private int measureCountdown = 0;

    public VRSettingsBodyHeight(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Height Mode & Height Adjustment
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 0,
                () -> I18n.format("visor.options.vr_body_height.mode") + ": " + I18n.format("visor.options.enums.HeightMode." + VRClientSettings.heightMode.name()),
                () -> {
                    HeightMode[] modes = HeightMode.values();
                    int next = (VRClientSettings.heightMode.ordinal() + 1) % modes.length;
                    VRClientSettings.heightMode = modes[next];
                    VRClientSettings.save();
                },
                I18n.format("visor.options.vr_body_height.mode.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 0,
                () -> I18n.format("visor.options.vr_body_height.adjustment") + ": " + I18n.format("visor.options.enums.HeightAdjustment." + VRClientSettings.heightAdjustment.name()),
                () -> {
                    HeightAdjustment[] adjustments = HeightAdjustment.values();
                    int next = (VRClientSettings.heightAdjustment.ordinal() + 1) % adjustments.length;
                    VRClientSettings.heightAdjustment = adjustments[next];
                    VRClientSettings.save();
                },
                I18n.format("visor.options.vr_body_height.adjustment.tooltip")
        ));

        // Row 1: Height Standing slider & Height Units button
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.vr_body_height.standing"),
                1.2f, 2.2f, 0.01f,
                () -> VRClientSettings.playerHeight,
                v -> {
                    VRClientSettings.playerHeight = v;
                    VRClientSettings.save();
                },
                v -> HeightFormat.format(v),
                I18n.format("visor.options.vr_body_height.standing.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 1,
                () -> I18n.format("visor.options.vr_body_height.units") + ": " + I18n.format("visor.options.enums.HeightUnits." + VRClientSettings.heightUnits.name()),
                () -> {
                    VRClientSettings.heightUnits = (VRClientSettings.heightUnits == HeightUnits.METRIC) ? HeightUnits.IMPERIAL : HeightUnits.METRIC;
                    VRClientSettings.save();
                    init(); // Rebuild slider with updated formatting
                },
                I18n.format("visor.options.vr_body_height.units.tooltip")
        ));

        // Row 2: Auto Height Toggle & Measure / Calibrate Button
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.vr_body_height.auto"),
                () -> VRClientSettings.autoHeight,
                v -> {
                    VRClientSettings.autoHeight = v;
                    if (v) {
                        com.gtnhvr.VRHeightCalibrator.requestAutoMeasure();
                    }
                    VRClientSettings.save();
                },
                I18n.format("visor.options.vr_body_height.auto.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 2,
                () -> measureCountdown > 0
                        ? I18n.format("visor.messages.height_measure_countdown", measureCountdown)
                        : I18n.format("visor.options.vr_body.height.measure.button"),
                () -> {
                    float currentHmdY = HeadPoseController.getY();
                    if (currentHmdY > 0.5f && currentHmdY < 2.5f) {
                        VRClientSettings.playerHeight = com.gtnhvr.VRHeightCalibrator.eyeToHeight(currentHmdY);
                        VRClientSettings.save();
                    }
                    init();
                },
                I18n.format("visor.options.vr_body.height.measure.button")
        ));
    }
}
