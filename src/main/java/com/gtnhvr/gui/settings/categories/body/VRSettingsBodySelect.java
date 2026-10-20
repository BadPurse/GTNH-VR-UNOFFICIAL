package com.gtnhvr.gui.settings.categories.body;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

public class VRSettingsBodySelect extends VROptionsSet {

    private static final String[] BODY_OPTIONS = new String[]{
            "Steve (Classic)",
            "Alex (Slim)",
            "Hands Only",
            "Hidden"
    };

    public VRSettingsBodySelect(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        for (int i = 0; i < BODY_OPTIONS.length; i++) {
            final String bodyName = BODY_OPTIONS[i];
            OptionWidgetPosition pos = (i % 2 == 0) ? OptionWidgetPosition.LEFT : OptionWidgetPosition.RIGHT;
            int row = i / 2;

            widgets.add(new OptionWidget.ButtonWidget(
                    pos, row,
                    () -> {
                        boolean selected = VRClientSettings.vrBodyModel.equalsIgnoreCase(bodyName);
                        return (selected ? "✔ " : "") + bodyName;
                    },
                    () -> {
                        VRClientSettings.vrBodyModel = bodyName;
                        VRClientSettings.save();
                        init();
                    },
                    bodyName
            ));
        }
    }
}
