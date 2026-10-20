package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.MovementMode;
import com.gtnhvr.settings.RotationMode;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsMovement extends VROptionsSet {

    public VRSettingsMovement(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Movement Mode & Rotation Mode
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 0,
                () -> I18n.format("visor.options.movement.mode") + ": " + I18n.format("visor.options.enums.MovementMode." + VRClientSettings.movementMode.name()),
                () -> {
                    MovementMode[] modes = MovementMode.values();
                    int next = (VRClientSettings.movementMode.ordinal() + 1) % modes.length;
                    VRClientSettings.movementMode = modes[next];
                    VRClientSettings.save();
                },
                I18n.format("visor.options.movement.mode.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 0,
                () -> I18n.format("visor.options.movement.rotation_mode") + ": " + I18n.format("visor.options.enums.RotationMode." + VRClientSettings.rotationMode.name()),
                () -> {
                    RotationMode[] modes = RotationMode.values();
                    int next = (VRClientSettings.rotationMode.ordinal() + 1) % modes.length;
                    VRClientSettings.rotationMode = modes[next];
                    VRClientSettings.save();
                },
                I18n.format("visor.options.movement.rotation_mode.tooltip")
        ));

        // Row 1: Rotation Step & Smooth Rotation Sensitivity
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.movement.world_rotation.step"),
                0.0f, 90.0f, 15.0f,
                () -> VRClientSettings.worldRotationStep,
                v -> {
                    VRClientSettings.worldRotationStep = v;
                    VRClientSettings.save();
                    init();
                },
                v -> (v <= 0.0f) ? I18n.format("visor.options.movement.world_rotation.smooth") : String.format("%.0f°", v),
                I18n.format("visor.options.movement.world_rotation.step.tooltip")
        ));

        if (VRClientSettings.worldRotationStep <= 0.0f) {
            widgets.add(new OptionWidget.SliderWidget(
                    OptionWidgetPosition.RIGHT, 1,
                    I18n.format("visor.options.movement.world_rotation.smooth_sensitivity"),
                    0.04f, 0.30f, 0.02f,
                    () -> VRClientSettings.worldRotationSmoothSensitivity,
                    v -> {
                        VRClientSettings.worldRotationSmoothSensitivity = v;
                        VRClientSettings.save();
                    },
                    v -> String.format("%.2f", v),
                    I18n.format("visor.options.movement.world_rotation.smooth_sensitivity.tooltip")
            ));
        } else {
            widgets.add(new OptionWidget.ToggleWidget(
                    OptionWidgetPosition.RIGHT, 1,
                    I18n.format("visor.options.movement.walk_up"),
                    () -> VRClientSettings.walkUpEnabled,
                    v -> {
                        VRClientSettings.walkUpEnabled = v;
                        VRClientSettings.save();
                    },
                    I18n.format("visor.options.movement.walk_up.tooltip")
            ));
        }

        // Row 2: Walk Up (if not placed on row 1) or Treadmill
        if (VRClientSettings.worldRotationStep <= 0.0f) {
            widgets.add(new OptionWidget.ToggleWidget(
                    OptionWidgetPosition.LEFT, 2,
                    I18n.format("visor.options.movement.walk_up"),
                    () -> VRClientSettings.walkUpEnabled,
                    v -> {
                        VRClientSettings.walkUpEnabled = v;
                        VRClientSettings.save();
                    },
                    I18n.format("visor.options.movement.walk_up.tooltip")
            ));

            widgets.add(new OptionWidget.ToggleWidget(
                    OptionWidgetPosition.RIGHT, 2,
                    I18n.format("visor.options.movement.treadmill.enabled"),
                    () -> VRClientSettings.treadmillEnabled,
                    v -> {
                        VRClientSettings.treadmillEnabled = v;
                        VRClientSettings.save();
                        init();
                    },
                    I18n.format("visor.options.movement.treadmill.enabled.tooltip")
            ));
        } else {
            widgets.add(new OptionWidget.ToggleWidget(
                    OptionWidgetPosition.LEFT, 2,
                    I18n.format("visor.options.movement.treadmill.enabled"),
                    () -> VRClientSettings.treadmillEnabled,
                    v -> {
                        VRClientSettings.treadmillEnabled = v;
                        VRClientSettings.save();
                        init();
                    },
                    I18n.format("visor.options.movement.treadmill.enabled.tooltip")
            ));

            if (VRClientSettings.treadmillEnabled) {
                widgets.add(new OptionWidget.SliderWidget(
                        OptionWidgetPosition.RIGHT, 2,
                        I18n.format("visor.options.movement.treadmill.speed_scale"),
                        0.25f, 3.0f, 0.25f,
                        () -> VRClientSettings.treadmillSpeedScale,
                        v -> {
                            VRClientSettings.treadmillSpeedScale = v;
                            VRClientSettings.save();
                        },
                        v -> String.format("x%.2f", v),
                        I18n.format("visor.options.movement.treadmill.speed_scale.tooltip")
                ));
            }
        }

        // Row 3: Sprint Threshold & Compatible Look Direction
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 3,
                I18n.format("visor.options.movement.sprint_threshold"),
                0.50f, 1.0f, 0.05f,
                () -> VRClientSettings.sprintThreshold,
                v -> {
                    VRClientSettings.sprintThreshold = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f%%", v * 100.0f),
                I18n.format("visor.options.movement.sprint_threshold.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 3,
                I18n.format("visor.options.movement.compatible_look_direction"),
                () -> VRClientSettings.compatibleLookDirection,
                v -> {
                    VRClientSettings.compatibleLookDirection = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.movement.compatible_look_direction.tooltip")
        ));
    }
}
