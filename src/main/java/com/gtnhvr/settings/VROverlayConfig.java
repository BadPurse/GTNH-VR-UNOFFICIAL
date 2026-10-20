package com.gtnhvr.settings;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent per-overlay configuration edited in the "Setting overlay (VR)" screen
 * (Visor VROverlaySettings): visibility, anchor, scale, distance and offsets.
 * The renderers read these values directly, so every control of the screen has an effect.
 *
 * Anchor meaning: HMD follows the head position, MAIN_HAND / OFFHAND follow that controller,
 * WORLD stays where it was placed in the room.
 */
public final class VROverlayConfig {
    public static final String HUD = "hud";
    public static final String HOTBAR_MAIN = "hotbar_main";
    public static final String HOTBAR_OFFHAND = "hotbar_offhand";
    public static final String GAME_SCREEN = "game_screen";
    public static final String KEYBOARD = "keyboard";
    public static final String CROSSHAIR = "crosshair";
    public static final String WRIST = "wrist";
    public static final String STATUS = "status";
    public static final String MAP = "map";
    public static final String INFO = "info";
    public static final String POTIONS = "potions";

    public static final String[] IDS = {HUD, HOTBAR_MAIN, HOTBAR_OFFHAND, GAME_SCREEN, KEYBOARD, CROSSHAIR, WRIST, STATUS, MAP, INFO, POTIONS};
    public static final String[] ANCHORS = {"HMD", "MAIN_HAND", "OFFHAND", "WORLD"};

    public static final class Entry {
        public boolean visible = true;
        public String anchor = "HMD";
        public float scale = 1.0F;
        public float distance = 1.0F;
        public float offsetX = 0.0F;
        public float offsetY = 0.0F;
        public boolean flag = false;

        public void set(Entry other) {
            visible = other.visible;
            anchor = other.anchor;
            scale = other.scale;
            distance = other.distance;
            offsetX = other.offsetX;
            offsetY = other.offsetY;
            flag = other.flag;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static Configuration config;

    static {
        for (String id : IDS) {
            ENTRIES.put(id, defaults(id));
        }
    }

    private VROverlayConfig() {}

    public static Entry defaults(String id) {
        Entry e = new Entry();
        switch (id) {
            case HUD:
                e.anchor = "HMD";
                e.distance = 1.8F;
                e.flag = true; // curved
                break;
            case HOTBAR_MAIN:
            case HOTBAR_OFFHAND:
                e.anchor = "HMD";
                e.distance = 0.55F;
                break;
            case GAME_SCREEN:
                e.anchor = "WORLD";
                e.distance = 1.35F;
                break;
            case KEYBOARD:
                e.anchor = "WORLD";
                e.distance = 1.05F;
                e.flag = true; // auto show on text fields
                break;
            case CROSSHAIR:
                e.anchor = "WORLD"; // both hands
                e.distance = 4.5F;
                e.flag = true; // laser
                break;
            case WRIST:
                e.anchor = "OFFHAND";
                e.scale = 0.5F;
                e.distance = 0.14F;
                break;
            case STATUS:
                e.anchor = "OFFHAND";
                e.scale = 0.45F;
                e.distance = 0.14F;
                break;
            case MAP:
                e.anchor = "MAIN_HAND";
                e.scale = 0.50F;
                e.distance = 0.14F;
                break;
            case INFO:
                e.anchor = "MAIN_HAND";
                e.scale = 0.50F;
                e.distance = 0.14F;
                e.offsetY = 0.11F;
                break;
            case POTIONS:
                e.anchor = "OFFHAND";
                e.scale = 0.50F;
                e.distance = 0.14F;
                e.offsetY = 0.065F;
                break;
            default:
                break;
        }
        return e;
    }

    public static Entry get(String id) {
        Entry e = ENTRIES.get(id);
        if (e == null) {
            e = defaults(id);
            ENTRIES.put(id, e);
        }
        return e;
    }

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        if (config == null) return;
        try {
            config.load();
            for (String id : IDS) {
                Entry d = defaults(id);
                Entry e = get(id);
                String cat = "overlay_" + id;
                e.visible = config.get(cat, "visible", d.visible).getBoolean();
                e.anchor = normalizeAnchor(config.get(cat, "anchor", d.anchor).getString(), d.anchor);
                e.scale = clamp((float) config.get(cat, "scale", (double) d.scale).getDouble(), 0.2F, 3.0F);
                e.distance = clamp((float) config.get(cat, "distance", (double) d.distance).getDouble(), 0.1F, 6.0F);
                e.offsetX = clamp((float) config.get(cat, "offsetX", (double) d.offsetX).getDouble(), -1.0F, 1.0F);
                e.offsetY = clamp((float) config.get(cat, "offsetY", (double) d.offsetY).getDouble(), -1.0F, 1.0F);
                e.flag = config.get(cat, "flag", d.flag).getBoolean();
            }
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static void save() {
        if (config == null) return;
        for (String id : IDS) {
            Entry e = get(id);
            String cat = "overlay_" + id;
            config.get(cat, "visible", true).set(e.visible);
            config.get(cat, "anchor", "HMD").set(e.anchor);
            config.get(cat, "scale", 1.0D).set((double) e.scale);
            config.get(cat, "distance", 1.0D).set((double) e.distance);
            config.get(cat, "offsetX", 0.0D).set((double) e.offsetX);
            config.get(cat, "offsetY", 0.0D).set((double) e.offsetY);
            config.get(cat, "flag", false).set(e.flag);
        }
        config.save();
    }

    private static String normalizeAnchor(String value, String fallback) {
        for (String a : ANCHORS) {
            if (a.equalsIgnoreCase(value)) {
                return a;
            }
        }
        return fallback;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
