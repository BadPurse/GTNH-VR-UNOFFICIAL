package com.gtnhvr;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

@Lwjgl3Aware
public final class VRConfig {
    private static Configuration config;

    public static boolean vrEnabled = true;
    public static boolean headTracking = true;
    public static boolean snapTurning = true;
    public static float snapTurnAngle = 45.0f;
    public static float worldScale = 1.0f;
    public static int mirrorMode = 0; // 0: Both, 1: Left Eye, 2: Right Eye, 3: Off

    private VRConfig() {}

    public static void init(File configFile) {
        config = new Configuration(configFile);
        sync();
    }

    public static void sync() {
        if (config == null) return;
        try {
            config.load();

            vrEnabled = config.getBoolean("vrEnabled", Configuration.CATEGORY_GENERAL, vrEnabled,
                    "Enable OpenXR VR rendering and tracking");
            headTracking = config.getBoolean("headTracking", Configuration.CATEGORY_GENERAL, headTracking,
                    "Apply HMD orientation to player camera");
            snapTurning = config.getBoolean("snapTurning", Configuration.CATEGORY_GENERAL, snapTurning,
                    "Enable snap turning");
            snapTurnAngle = (float) config.get(Configuration.CATEGORY_GENERAL, "snapTurnAngle",
                    (double) snapTurnAngle, "Angle in degrees for snap turns").getDouble();
            worldScale = (float) config.get(Configuration.CATEGORY_GENERAL, "worldScale",
                    (double) worldScale, "Scale multiplier for world rendering").getDouble();
            mirrorMode = config.getInt("mirrorMode", Configuration.CATEGORY_GENERAL, mirrorMode, 0, 3,
                    "Desktop mirror mode (0: Both, 1: Left, 2: Right, 3: Off)");

            if (config.hasChanged()) {
                config.save();
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to load GTNHVR config: %s", t);
        }
    }

    public static void save() {
        if (config == null) return;
        try {
            config.get(Configuration.CATEGORY_GENERAL, "vrEnabled", true).set(vrEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "headTracking", true).set(headTracking);
            config.get(Configuration.CATEGORY_GENERAL, "snapTurning", true).set(snapTurning);
            config.get(Configuration.CATEGORY_GENERAL, "snapTurnAngle", 45.0).set((double) snapTurnAngle);
            config.get(Configuration.CATEGORY_GENERAL, "worldScale", 1.0).set((double) worldScale);
            config.get(Configuration.CATEGORY_GENERAL, "mirrorMode", 0).set(mirrorMode);
            config.save();
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to save GTNHVR config: %s", t);
        }
    }
}
