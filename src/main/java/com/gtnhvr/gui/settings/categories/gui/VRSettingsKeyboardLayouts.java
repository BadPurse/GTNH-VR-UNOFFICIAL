package com.gtnhvr.gui.settings.categories.gui;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.keyboard.KeyboardLayout;
import com.gtnhvr.gui.keyboard.KeyboardLayouts;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.resources.I18n;

import java.util.List;

public class VRSettingsKeyboardLayouts extends VROptionsSet {

    public VRSettingsKeyboardLayouts(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        // Row 0 Left: Auto Detect
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 0,
                I18n.format("visor.options.controls.keyboard_layouts.auto"),
                () -> VRClientSettings.keyboardAutoLayout,
                v -> {
                    VRClientSettings.keyboardAutoLayout = v;
                    VRClientSettings.save();
                },
                I18n.format("visor.options.controls.keyboard_layouts.auto.tooltip")
        ));

        // Layout checkboxes for all 9 international layouts
        addLayoutToggle(KeyboardLayout.ENGLISH, OptionWidgetPosition.RIGHT, 0);
        addLayoutToggle(KeyboardLayout.RUSSIAN, OptionWidgetPosition.LEFT, 1);
        addLayoutToggle(KeyboardLayout.UKRAINIAN, OptionWidgetPosition.RIGHT, 1);
        addLayoutToggle(KeyboardLayout.GERMAN, OptionWidgetPosition.LEFT, 2);
        addLayoutToggle(KeyboardLayout.FRENCH, OptionWidgetPosition.RIGHT, 2);
        addLayoutToggle(KeyboardLayout.SPANISH, OptionWidgetPosition.LEFT, 3);
        addLayoutToggle(KeyboardLayout.ITALIAN, OptionWidgetPosition.RIGHT, 3);
        addLayoutToggle(KeyboardLayout.PORTUGUESE, OptionWidgetPosition.LEFT, 4);
        addLayoutToggle(KeyboardLayout.HUNGARIAN, OptionWidgetPosition.RIGHT, 4);
    }

    private void addLayoutToggle(KeyboardLayout layout, OptionWidgetPosition pos, int row) {
        widgets.add(new OptionWidget.ToggleWidget(
                pos, row,
                layout.getDisplayName() + " (" + layout.getLabel() + ")",
                () -> {
                    List<KeyboardLayout> selected = KeyboardLayouts.getSelected();
                    return selected.contains(layout);
                },
                checked -> {
                    List<KeyboardLayout> selected = KeyboardLayouts.getSelected();
                    if (checked) {
                        if (!selected.contains(layout)) {
                            selected.add(layout);
                        }
                    } else {
                        // Prevent unchecking all layouts
                        if (selected.size() > 1) {
                            selected.remove(layout);
                        }
                    }
                    KeyboardLayouts.setSelected(selected);
                },
                I18n.format("visor.options.controls.keyboard_layouts.tooltip", layout.getDisplayName())
        ));
    }
}
