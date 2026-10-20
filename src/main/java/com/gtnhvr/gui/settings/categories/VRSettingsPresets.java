package com.gtnhvr.gui.settings.categories;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRPresetManager;
import net.minecraft.client.resources.I18n;

import java.util.List;

public class VRSettingsPresets extends VROptionsSet {

    private boolean viewingCustom = false;
    private String selectedPreset = "Defaults";

    public VRSettingsPresets(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
        List<String> builtIn = VRPresetManager.getBuiltInPresetNames();
        if (!builtIn.isEmpty()) {
            selectedPreset = builtIn.get(0);
        }
    }

    @Override
    protected void buildWidgets() {
        // Row 0: Mode Switcher (Built-in Presets <-> Custom Presets) & Open Folder Button
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 0,
                () -> viewingCustom ? "📁 " + I18n.format("visor.options.presets.custom") : "⭐ " + I18n.format("visor.options.presets.builtin"),
                () -> {
                    viewingCustom = !viewingCustom;
                    if (viewingCustom) {
                        List<String> custom = VRPresetManager.getCustomPresetNames();
                        selectedPreset = custom.isEmpty() ? null : custom.get(0);
                    } else {
                        List<String> builtIn = VRPresetManager.getBuiltInPresetNames();
                        selectedPreset = builtIn.isEmpty() ? null : builtIn.get(0);
                    }
                    init();
                },
                I18n.format("visor.options.presets.button")
        ));

        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 0,
                () -> "📂 " + I18n.format("visor.options.presets.folder"),
                () -> {
                    VRPresetManager.openPresetsFolder();
                },
                I18n.format("visor.options.presets.folder")
        ));

        // Rows 1-3: List of presets for the active mode
        List<String> presets = viewingCustom ? VRPresetManager.getCustomPresetNames() : VRPresetManager.getBuiltInPresetNames();

        if (presets.isEmpty()) {
            widgets.add(new OptionWidget.ButtonWidget(
                    OptionWidgetPosition.CENTER, 1,
                    () -> I18n.format("visor.options.presets.none"),
                    () -> {},
                    I18n.format("visor.options.presets.none")
            ));
        } else {
            for (int i = 0; i < Math.min(6, presets.size()); i++) {
                final String presetName = presets.get(i);
                OptionWidgetPosition pos = (i % 2 == 0) ? OptionWidgetPosition.LEFT : OptionWidgetPosition.RIGHT;
                int row = 1 + (i / 2);

                widgets.add(new OptionWidget.ButtonWidget(
                        pos, row,
                        () -> {
                            boolean isSelected = presetName.equals(selectedPreset);
                            return (isSelected ? "► " : "   ") + presetName;
                        },
                        () -> {
                            selectedPreset = presetName;
                            init();
                        },
                        presetName
                ));
            }
        }

        // Row 4: Action Buttons (Apply, Save Current, Delete)
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 4,
                () -> "✔ " + I18n.format("visor.options.presets.apply"),
                () -> {
                    if (selectedPreset != null) {
                        VRPresetManager.applyPreset(selectedPreset);
                        screen.initGui(); // Refresh entire screen with applied settings
                    }
                },
                I18n.format("visor.options.presets.apply")
        ));

        if (viewingCustom && selectedPreset != null) {
            widgets.add(new OptionWidget.ButtonWidget(
                    OptionWidgetPosition.RIGHT, 4,
                    () -> "✖ " + I18n.format("visor.options.presets.delete"),
                    () -> {
                        VRPresetManager.deleteCustomPreset(selectedPreset);
                        List<String> custom = VRPresetManager.getCustomPresetNames();
                        selectedPreset = custom.isEmpty() ? null : custom.get(0);
                        init();
                    },
                    I18n.format("visor.options.presets.delete")
            ));
        } else {
            widgets.add(new OptionWidget.ButtonWidget(
                    OptionWidgetPosition.RIGHT, 4,
                    () -> "💾 " + I18n.format("visor.options.presets.create"),
                    () -> {
                        String name = "Preset " + (VRPresetManager.getCustomPresetNames().size() + 1);
                        VRPresetManager.saveCustomPreset(name);
                        viewingCustom = true;
                        selectedPreset = name;
                        init();
                    },
                    I18n.format("visor.options.presets.create")
            ));
        }
    }
}
