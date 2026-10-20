package com.gtnhvr.gui.settings.categories.rendering;

import com.gtnhvr.HeadPoseController;
import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsThirdPerson extends VROptionsSet {

    public VRSettingsThirdPerson(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.rendering.third_person.fov"),
                20.0f, 120.0f, 5.0f,
                () -> VRClientSettings.thirdPersonFov,
                v -> { VRClientSettings.thirdPersonFov = v; VRClientSettings.save(); },
                v -> String.format("%.0f°", v),
                I18n.format("visor.options.rendering.third_person.fov.tooltip")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 0,
                () -> I18n.format("visor.options.rendering.third_person.reposition_camera"),
                () -> {
                    HeadPoseController.recenter();
                },
                I18n.format("visor.options.rendering.third_person.reposition_camera")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 1,
                "Camera X",
                -5.0f, 5.0f, 0.25f,
                () -> VRClientSettings.thirdPersonCameraPosX,
                v -> { VRClientSettings.thirdPersonCameraPosX = v; VRClientSettings.save(); },
                v -> String.format("%.2fm", v),
                "Third person camera horizontal offset"
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 1,
                "Camera Y",
                0.0f, 5.0f, 0.25f,
                () -> VRClientSettings.thirdPersonCameraPosY,
                v -> { VRClientSettings.thirdPersonCameraPosY = v; VRClientSettings.save(); },
                v -> String.format("%.2fm", v),
                "Third person camera height offset"
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 2,
                "Camera Z",
                0.5f, 8.0f, 0.25f,
                () -> VRClientSettings.thirdPersonCameraPosZ,
                v -> { VRClientSettings.thirdPersonCameraPosZ = v; VRClientSettings.save(); },
                v -> String.format("%.2fm", v),
                "Third person camera distance behind player"
        ));
    }
}
