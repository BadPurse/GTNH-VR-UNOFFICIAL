package com.gtnhvr.settings;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.gui.settings.categories.*;

public enum VRSettingsCategory {
    PRESETS("presets"),
    RENDERING("rendering"),
    VR_BODY("vr_body"),
    CONTROLS("controls"),
    MOVEMENT("movement"),
    IMMERSION("immersion"),
    GUI("gui"),
    WORLD("world");

    private final String key;

    VRSettingsCategory(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public VROptionsSet createOptionsSet(VRSettingsScreen screen) {
        switch (this) {
            case PRESETS: return new VRSettingsPresets(screen, null);
            case RENDERING: return new VRSettingsRendering(screen, null);
            case VR_BODY: return new VRSettingsBody(screen, null);
            case CONTROLS: return new VRSettingsControls(screen, null);
            case MOVEMENT: return new VRSettingsMovement(screen, null);
            case IMMERSION: return new VRSettingsImmersion(screen, null);
            case GUI: return new VRSettingsGui(screen, null);
            case WORLD: return new VRSettingsWorld(screen, null);
            default: return new VRSettingsPresets(screen, null);
        }
    }
}
