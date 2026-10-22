package com.gtnhvr.settings;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        public float opacity = 1.0F;
        public float offsetX = 0.0F;
        public float offsetY = 0.0F;
        public boolean flag = false;
        /** True for user-created copies of a supported overlay template. */
        public boolean custom = false;
        public String name = "";
        public String description = "";
        public String template = HUD;

        public void set(Entry other) {
            visible = other.visible;
            anchor = other.anchor;
            scale = other.scale;
            distance = other.distance;
            opacity = other.opacity;
            offsetX = other.offsetX;
            offsetY = other.offsetY;
            flag = other.flag;
            custom = other.custom;
            name = other.name;
            description = other.description;
            template = other.template;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Entry> CUSTOM_ENTRIES = new LinkedHashMap<>();
    private static Configuration config;

    static {
        for (String id : IDS) {
            ENTRIES.put(id, defaults(id));
        }
    }

    private VROverlayConfig() {}

    /** All built-in overlays followed by saved user-created overlay copies. */
    public static String[] getAllIds() {
        List<String> ids = new ArrayList<>();
        Collections.addAll(ids, IDS);
        ids.addAll(CUSTOM_ENTRIES.keySet());
        return ids.toArray(new String[0]);
    }

    public static String[] getCustomIds(String template) {
        String normalized = normalizeTemplate(template);
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Entry> item : CUSTOM_ENTRIES.entrySet()) {
            if (normalized.equals(item.getValue().template)) ids.add(item.getKey());
        }
        return ids.toArray(new String[0]);
    }

    public static boolean isBuiltIn(String id) {
        for (String candidate : IDS) if (candidate.equals(id)) return true;
        return false;
    }

    /** Creates a persistent clone. Rendering is provided by the matching template renderer. */
    public static String createCustom(String name, String description, String template) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) return null;
        String normalizedTemplate = normalizeTemplate(template);
        if (!HUD.equals(normalizedTemplate) && !GAME_SCREEN.equals(normalizedTemplate)
                && !KEYBOARD.equals(normalizedTemplate)) return null;

        String base = cleanName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "overlay";
        if (base.length() > 38) base = base.substring(0, 38);
        String id = "custom_" + base;
        int suffix = 2;
        while (isBuiltIn(id) || CUSTOM_ENTRIES.containsKey(id)) {
            id = "custom_" + base + "_" + suffix++;
        }

        Entry entry = defaults(normalizedTemplate);
        entry.custom = true;
        entry.name = cleanName;
        entry.description = description == null ? "" : description.trim();
        entry.template = normalizedTemplate;
        // Start copies slightly beside the source so users can see and grab the new overlay immediately.
        entry.offsetX = 0.32F;
        CUSTOM_ENTRIES.put(id, entry);
        ENTRIES.put(id, entry);
        save();
        return id;
    }

    public static boolean removeCustom(String id) {
        Entry removed = CUSTOM_ENTRIES.remove(id);
        if (removed == null) return false;
        ENTRIES.remove(id);
        if (config != null) {
            String overlayCategory = "overlay_" + id;
            String customCategory = "custom_overlay_" + id;
            if (config.getCategoryNames().contains(overlayCategory)) config.removeCategory(config.getCategory(overlayCategory));
            if (config.getCategoryNames().contains(customCategory)) config.removeCategory(config.getCategory(customCategory));
            config.save();
        }
        return true;
    }

    private static String normalizeTemplate(String value) {
        if (value == null) return HUD;
        String id = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if ("gamescreen".equals(id)) id = GAME_SCREEN;
        if ("virtual_keyboard".equals(id)) id = KEYBOARD;
        if (HUD.equals(id) || GAME_SCREEN.equals(id) || KEYBOARD.equals(id)
                || CROSSHAIR.equals(id) || HOTBAR_MAIN.equals(id) || HOTBAR_OFFHAND.equals(id)
                || WRIST.equals(id) || STATUS.equals(id) || MAP.equals(id) || INFO.equals(id) || POTIONS.equals(id)) {
            return id;
        }
        return HUD;
    }

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
            // Rebuild the dynamic list from the persisted metadata categories. This avoids
            // losing custom overlays when the config is reloaded or the game restarts.
            for (String oldId : new ArrayList<>(CUSTOM_ENTRIES.keySet())) ENTRIES.remove(oldId);
            CUSTOM_ENTRIES.clear();
            for (String category : new ArrayList<>(config.getCategoryNames())) {
                if (!category.startsWith("custom_overlay_")) continue;
                String id = category.substring("custom_overlay_".length());
                if (id.isEmpty() || isBuiltIn(id)) continue;
                String template = normalizeTemplate(config.get(category, "template", HUD).getString());
                Entry custom = defaults(template);
                custom.custom = true;
                custom.template = template;
                custom.name = config.get(category, "name", id).getString();
                custom.description = config.get(category, "description", "").getString();
                CUSTOM_ENTRIES.put(id, custom);
                ENTRIES.put(id, custom);
            }
            for (String id : IDS) {
                Entry d = defaults(id);
                Entry e = get(id);
                String cat = "overlay_" + id;
                e.visible = config.get(cat, "visible", d.visible).getBoolean();
                e.anchor = normalizeAnchor(config.get(cat, "anchor", d.anchor).getString(), d.anchor);
                e.scale = clamp((float) config.get(cat, "scale", (double) d.scale).getDouble(), 0.2F, 3.0F);
                e.distance = clamp((float) config.get(cat, "distance", (double) d.distance).getDouble(), 0.1F, 6.0F);
                e.opacity = clamp((float) config.get(cat, "opacity", (double) d.opacity).getDouble(), 0.05F, 1.0F);
                e.opacity = clamp((float) config.get(cat, "opacity", (double) d.opacity).getDouble(), 0.05F, 1.0F);
                e.offsetX = clamp((float) config.get(cat, "offsetX", (double) d.offsetX).getDouble(), -1.0F, 1.0F);
                e.offsetY = clamp((float) config.get(cat, "offsetY", (double) d.offsetY).getDouble(), -1.0F, 1.0F);
                e.flag = config.get(cat, "flag", d.flag).getBoolean();
            }
            for (Map.Entry<String, Entry> item : CUSTOM_ENTRIES.entrySet()) {
                String id = item.getKey();
                Entry e = item.getValue();
                Entry d = defaults(e.template);
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
        for (String id : IDS) saveEntry(id, get(id), defaults(id));
        for (Map.Entry<String, Entry> item : CUSTOM_ENTRIES.entrySet()) {
            String id = item.getKey();
            Entry entry = item.getValue();
            saveEntry(id, entry, defaults(entry.template));
            String category = "custom_overlay_" + id;
            config.get(category, "name", entry.name).set(entry.name);
            config.get(category, "description", entry.description).set(entry.description);
            config.get(category, "template", entry.template).set(entry.template);
        }
        config.save();
    }

    private static void saveEntry(String id, Entry entry, Entry defaults) {
        String category = "overlay_" + id;
        config.get(category, "visible", defaults.visible).set(entry.visible);
        config.get(category, "anchor", defaults.anchor).set(entry.anchor);
        config.get(category, "scale", (double) defaults.scale).set((double) entry.scale);
        config.get(category, "distance", (double) defaults.distance).set((double) entry.distance);
        config.get(category, "opacity", (double) defaults.opacity).set((double) entry.opacity);
        config.get(category, "offsetX", (double) defaults.offsetX).set((double) entry.offsetX);
        config.get(category, "offsetY", (double) defaults.offsetY).set((double) entry.offsetY);
        config.get(category, "flag", defaults.flag).set(entry.flag);
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
