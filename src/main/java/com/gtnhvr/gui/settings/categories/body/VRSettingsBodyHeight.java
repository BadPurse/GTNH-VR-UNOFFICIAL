package com.gtnhvr.gui.settings.categories.body;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.OpenXRRuntime;
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
                    com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().resetHeightCalibration();
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
                    // Alignment is an explicit reset action: clear a stale physical crouch
                    // baseline and the controller's latched sneak toggle even if this sample
                    // is rejected because the user is seated or the reference space is LOCAL.
                    com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().resetHeightCalibration();
                    com.gtnhvr.VRControllerHandler.getInstance().resetSneakToggle();

                    float currentHmdY = HeadPoseController.getY();
                    // Height calibration is only meaningful while standing in a floor-relative space.
                    // A seated headset height around 0.7 m must not be clamped into a false 1.2 m avatar.
                    if (!VRClientSettings.seated && OpenXRRuntime.isStageSpace()
                            && currentHmdY > 0.90F && currentHmdY < 2.5F) {
                        VRClientSettings.playerHeight = Math.max(1.2F, Math.min(2.4F,
                                com.gtnhvr.VRHeightCalibrator.eyeToHeight(currentHmdY)));
                        com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().resetHeightCalibration();
                        VRClientSettings.save();
                    } else {
                        com.gtnhvr.GTNHVRLog.chat(I18n.format("visor.messages.height_auto_hint"));
                    }
                    init();
                },
                I18n.format("visor.options.vr_body.height.measure.button")
        ));
    }
}
