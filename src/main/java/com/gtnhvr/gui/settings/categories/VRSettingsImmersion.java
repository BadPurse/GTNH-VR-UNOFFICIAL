package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.gui.settings.categories.immersion.VRSettingsImmersionAdvanced;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsImmersion extends VROptionsSet {

    public VRSettingsImmersion(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Advanced Immersion Submenu & Room Sneak
        widgets.add(new OptionWidget.SubmenuWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.immersion.advanced.button"),
                () -> new VRSettingsImmersionAdvanced(screen, this),
                I18n.format("visor.options.immersion.advanced.button")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 0,
                I18n.format("visor.options.immersion.room_sneak"),
                () -> VRClientSettings.roomSneak,
                v -> {
                    VRClientSettings.roomSneak = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_sneak.tooltip")
        ));

        // Row 1: Room Crawl & Room Climb
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("visor.options.immersion.room_crawl"),
                () -> VRClientSettings.roomCrawl,
                v -> {
                    VRClientSettings.roomCrawl = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_crawl.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.immersion.room_climb"),
                () -> VRClientSettings.roomClimb,
                v -> {
                    VRClientSettings.roomClimb = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_climb.tooltip")
        ));

        // Row 2: Room Jump & Room Swim
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.immersion.room_jump"),
                () -> VRClientSettings.roomJump,
                v -> {
                    VRClientSettings.roomJump = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_jump.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 2,
                I18n.format("visor.options.immersion.room_swim"),
                () -> VRClientSettings.roomSwim,
                v -> {
                    VRClientSettings.roomSwim = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_swim.tooltip")
        ));

        // Row 3: Room Dismount Vehicle & Room Boat Rowing
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 3,
                I18n.format("visor.options.immersion.room_dismount_vehicle"),
                () -> VRClientSettings.roomDismountVehicle,
                v -> {
                    VRClientSettings.roomDismountVehicle = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_dismount_vehicle.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 3,
                I18n.format("visor.options.immersion.room_boat_rowing"),
                () -> VRClientSettings.roomBoatRowing,
                v -> {
                    VRClientSettings.roomBoatRowing = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.immersion.room_boat_rowing.tooltip")
        ));
    }
}
