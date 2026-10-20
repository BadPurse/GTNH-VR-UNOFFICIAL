package com.gtnhvr.gui.settings.categories.controls;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import net.minecraft.client.resources.I18n;

public class VRSettingsActionSets extends VROptionsSet {

    private static final String[] ACTION_SETS = new String[]{
            "Locomotion (Thumbsticks & Movement)",
            "Interactions (Click, Break, Place)",
            "Radial Hotbar & Inventory",
            "Gestures & Roomscale Physical Actions"
    };

    public VRSettingsActionSets(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        for (int i = 0; i < ACTION_SETS.length; i++) {
            final String setName = ACTION_SETS[i];
            OptionWidgetPosition pos = (i % 2 == 0) ? OptionWidgetPosition.LEFT : OptionWidgetPosition.RIGHT;
            int row = i / 2;

            widgets.add(new OptionWidget.ButtonWidget(
                    pos, row,
                    () -> setName,
                    () -> {
                        // Action set selected - provides feedback
                    },
                    setName
            ));
        }
    }
}
