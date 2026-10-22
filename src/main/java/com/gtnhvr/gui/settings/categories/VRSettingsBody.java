package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.gui.settings.categories.body.VRSettingsBodyHeight;
import com.gtnhvr.gui.settings.categories.body.VRSettingsBodySelect;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsBody extends VROptionsSet {

    public VRSettingsBody(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Submenus Body Select & Body Height
        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.vr_body.select.button"),
                () -> new VRSettingsBodySelect(screen, this),
                I18n.format("visor.options.vr_body.select.button")
        ));

        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.vr_body.height.button"),
                () -> new VRSettingsBodyHeight(screen, this),
                I18n.format("visor.options.vr_body.height.button")
        ));

        // Row 1: Full Body Tracking & Hand Tracking
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.vr_body.fbt"),
                () -> VRClientSettings.fullBodyTracking,
                v -> {
                    VRClientSettings.fullBodyTracking = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.vr_body.fbt.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.vr_body.hand_tracking"),
                () -> VRClientSettings.handTracking,
                v -> {
                    VRClientSettings.handTracking = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.vr_body.hand_tracking.tooltip")
        ));

        // Row 2: Body Haptics & Seated Mode
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.vr_body.body_haptics"),
                () -> VRClientSettings.bodyHaptics,
                v -> {
                    VRClientSettings.bodyHaptics = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.vr_body.body_haptics.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 2,
                I18n.format("visor.options.movement.seated"),
                () -> VRClientSettings.seated,
                v -> {
                    VRClientSettings.seated = v;
                    if (v) {
                        // Entering seated mode must not inherit a crouch from a prior standing session.
                        com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance()
                                .resetHeightCalibration();
                        com.gtnhvr.VRControllerHandler.getInstance().resetSneakToggle();
                    }
                    VRClientSettings.save();
                },
                I18n.format("visor.options.movement.seated")
        ));
    }
}
