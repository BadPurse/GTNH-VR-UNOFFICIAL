package com.gtnhvr.gui.settings.categories.controls;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.gui.settings.categories.VRSettingsGui;
import com.gtnhvr.gui.settings.categories.VRSettingsImmersion;
import com.gtnhvr.gui.settings.categories.VRSettingsMovement;
import com.gtnhvr.gui.settings.categories.VRSettingsWorld;
import net.minecraft.client.resources.I18n;

/**
 * Groups the existing control-related settings into working subpages.
 * Previously these entries were inert buttons that only played a click sound.
 */
public class VRSettingsActionSets extends VROptionsSet {

    private static final String[] ACTION_SET_KEYS = new String[]{
            "gtnhvr.controls.group.movement",
            "gtnhvr.controls.group.interactions",
            "gtnhvr.controls.group.interface",
            "gtnhvr.controls.group.roomscale"
    };

    public VRSettingsActionSets(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        for (int i = 0; i < ACTION_SET_KEYS.length; i++) {
            final int index = i;
            String key = ACTION_SET_KEYS[index];
            OptionWidgetPosition pos = (index % 2 == 0)
                    ? OptionWidgetPosition.LEFT : OptionWidgetPosition.RIGHT;
            int row = index / 2;

            widgets.add(new OptionWidget.SubmenuWidget(
                    pos, row,
                    I18n.format(key),
                    () -> openSettingsGroup(index),
                    I18n.format(key + ".tooltip")
            ));
        }
    }

    private VROptionsSet openSettingsGroup(int index) {
        switch (index) {
            case 0:
                return new VRSettingsMovement(screen, this);
            case 1:
                return new VRSettingsWorld(screen, this);
            case 2:
                return new VRSettingsGui(screen, this);
            case 3:
            default:
                return new VRSettingsImmersion(screen, this);
        }
    }
}
