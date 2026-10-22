package com.gtnhvr.settings;

import com.gtnhvr.GTNHVRLog;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VRPresetManager {
    private static final List<Preset> builtInPresets = new ArrayList<>();
    private static final List<Preset> customPresets = new ArrayList<>();
    private static File presetsDir;

    public static class Preset {
        private final String id;
        private final String name;
        private final String description;
        private final boolean builtIn;
        private final Map<String, String> values = new HashMap<>();

        public Preset(String id, String name, String description, boolean builtIn) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.builtIn = builtIn;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isBuiltIn() { return builtIn; }
        public Map<String, String> getValues() { return values; }

        public void apply() {
            if (builtIn) {
                applyBuiltIn(id);
            } else {
                applyCustom(values);
            }
            // Presets set the persisted field names; synchronize the legacy widget aliases
            // before save(), otherwise save() would copy stale alias values back over the preset.
            VRClientSettings.syncCompatibilityAliasesFromCanonical();
            VRClientSettings.save();
        }
    }

    static {
        // Built-in presets matching Visor 1:1
        Preset def = new Preset("defaults", "Defaults",
                "The preset with default settings for everything.\nBalanced for room-scale play and comfort.", true);
        builtInPresets.add(def);

        Preset handHud = new Preset("defaults_hand_hud", "Defaults: Hand HUD",
                "The defaults preset alternative with\nHUD elements attached to hand and wrist.", true);
        builtInPresets.add(handHud);

        Preset perf = new Preset("performance", "High Performance",
                "Optimized settings to maximize framerate:\nLower render scale and disabled extra mirrors.", true);
        builtInPresets.add(perf);

        Preset imm = new Preset("immersion", "Full Immersion",
                "Maximum physical immersion:\nPhysical climbing, crouch sneaking, realistic jump, and eat by mouth.", true);
        builtInPresets.add(imm);
    }

    private VRPresetManager() {}

    public static void init(File mcDataDir) {
        presetsDir = new File(new File(mcDataDir, "visor"), "custom_presets");
        if (!presetsDir.exists()) {
            presetsDir.mkdirs();
        }
        reloadCustomPresets();
    }

    public static List<Preset> getBuiltInPresets() {
        return Collections.unmodifiableList(builtInPresets);
    }

    public static List<String> getBuiltInPresetNames() {
        List<String> list = new ArrayList<>();
        for (Preset p : builtInPresets) list.add(p.getName());
        return list;
    }

    public static List<Preset> getCustomPresets() {
        return Collections.unmodifiableList(customPresets);
    }

    public static List<String> getCustomPresetNames() {
        List<String> list = new ArrayList<>();
        for (Preset p : customPresets) list.add(p.getName());
        return list;
    }

    public static void applyPreset(String name) {
        for (Preset p : builtInPresets) {
            if (p.getName().equalsIgnoreCase(name) || p.getId().equalsIgnoreCase(name)) {
                p.apply();
                return;
            }
        }
        for (Preset p : customPresets) {
            if (p.getName().equalsIgnoreCase(name) || p.getId().equalsIgnoreCase(name)) {
                p.apply();
                return;
            }
        }
    }

    public static void saveCustomPreset(String name) {
        createCustomPreset(name, "Custom preset created from VR Settings.", true, true, true);
    }

    public static void reloadCustomPresets() {
        customPresets.clear();
        if (presetsDir == null || !presetsDir.exists()) return;

        File[] files = presetsDir.listFiles((dir, name) -> name.endsWith(".cfg") || name.endsWith(".txt") || name.endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                String name = f.getName().replace(".cfg", "").replace(".yml", "");
                StringBuilder desc = new StringBuilder();
                Map<String, String> map = new HashMap<>();

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("# desc:")) {
                        desc.append(line.substring(7).trim()).append("\n");
                    } else if (line.startsWith("# name:")) {
                        name = line.substring(7).trim();
                    } else if (!line.startsWith("#") && line.contains("=")) {
                        int eq = line.indexOf('=');
                        String k = line.substring(0, eq).trim();
                        String v = line.substring(eq + 1).trim();
                        map.put(k, v);
                    }
                }

                Preset p = new Preset(f.getName(), name, desc.length() > 0 ? desc.toString().trim() : "Custom user preset.", false);
                p.getValues().putAll(map);
                customPresets.add(p);
            } catch (Throwable t) {
                GTNHVRLog.warn("Failed to load preset %s: %s", f.getName(), t);
            }
        }
    }

    public static void createCustomPreset(String name, String description, boolean general, boolean controls, boolean gui) {
        if (presetsDir == null) return;
        if (!presetsDir.exists()) presetsDir.mkdirs();

        String fileId = name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase(Locale.ROOT);
        if (fileId.isEmpty()) fileId = "preset_" + System.currentTimeMillis();
        File target = new File(presetsDir, fileId + ".cfg");

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
            writer.write("# name: " + name); writer.newLine();
            writer.write("# desc: " + description.replace("\n", " ")); writer.newLine();

            if (general) {
                writer.write("vrPlayMode=" + VRClientSettings.vrPlayMode.name()); writer.newLine();
                writer.write("worldScale=" + VRClientSettings.worldScale); writer.newLine();
                writer.write("renderScaleFactor=" + VRClientSettings.renderScaleFactor); writer.newLine();
                writer.write("mirrorMode=" + VRClientSettings.mirrorMode.name()); writer.newLine();
                writer.write("mirrorEye=" + VRClientSettings.mirrorEye); writer.newLine();
                writer.write("headTracking=" + VRClientSettings.headTracking); writer.newLine();
                writer.write("scalingFilter=" + VRClientSettings.scalingFilter.name()); writer.newLine();
                writer.write("graphicsApi=" + VRClientSettings.graphicsApi.name()); writer.newLine();
                writer.write("movementMode=" + VRClientSettings.movementMode.name()); writer.newLine();
                writer.write("rotationMode=" + VRClientSettings.rotationMode.name()); writer.newLine();
                writer.write("worldRotationStep=" + VRClientSettings.worldRotationStep); writer.newLine();
                writer.write("worldRotationSmoothSensitivity=" + VRClientSettings.worldRotationSmoothSensitivity); writer.newLine();
                writer.write("walkUpEnabled=" + VRClientSettings.walkUpEnabled); writer.newLine();
                writer.write("sprintThreshold=" + VRClientSettings.sprintThreshold); writer.newLine();
            }
            if (controls) {
                writer.write("leftHanded=" + VRClientSettings.leftHanded); writer.newLine();
                writer.write("hapticsEnabled=" + VRClientSettings.hapticsEnabled); writer.newLine();
                writer.write("physicalSneaking=" + VRClientSettings.physicalSneaking); writer.newLine();
                writer.write("physicalClimbing=" + VRClientSettings.physicalClimbing); writer.newLine();
                writer.write("physicalCrawling=" + VRClientSettings.physicalCrawling); writer.newLine();
                writer.write("realisticJump=" + VRClientSettings.realisticJump); writer.newLine();
                writer.write("roomConsumeEnabled=" + VRClientSettings.roomConsumeEnabled); writer.newLine();
                writer.write("interactionDistance=" + VRClientSettings.interactionDistance); writer.newLine();
                writer.write("vrCrosshair=" + VRClientSettings.vrCrosshair); writer.newLine();
            }
            if (gui) {
                writer.write("hotbarMode=" + VRClientSettings.hotbarMode); writer.newLine();
                writer.write("hudDistance=" + VRClientSettings.hudDistance); writer.newLine();
                writer.write("hudScale=" + VRClientSettings.hudScale); writer.newLine();
                writer.write("curvedHud=" + VRClientSettings.curvedHud); writer.newLine();
                writer.write("hotbarCenterRadius=" + VRClientSettings.hotbarCenterRadius); writer.newLine();
                writer.write("hotbarHysteresisMargin=" + VRClientSettings.hotbarHysteresisMargin); writer.newLine();
                writer.write("hotbarSlotNumbers=" + VRClientSettings.hotbarSlotNumbers); writer.newLine();
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to save custom preset %s: %s", target.getName(), t);
        }

        reloadCustomPresets();
    }

    public static void deleteCustomPreset(String id) {
        if (presetsDir == null || !presetsDir.exists()) return;
        File target = new File(presetsDir, id);
        if (target.exists()) {
            target.delete();
        }
        reloadCustomPresets();
    }

    public static void openPresetsFolder() {
        if (presetsDir == null) return;
        if (!presetsDir.exists()) presetsDir.mkdirs();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(presetsDir);
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to open presets folder: %s", t);
        }
    }

    private static void applyBuiltIn(String id) {
        switch (id) {
            case "defaults" -> VRClientSettings.loadDefaults();
            case "defaults_hand_hud" -> {
                VRClientSettings.loadDefaults();
                VRClientSettings.hotbarMode = 0; // Hand/Wrist
                VRClientSettings.hudDistance = 1.8f;
                VRClientSettings.curvedHud = true;
            }
            case "performance" -> {
                VRClientSettings.loadDefaults();
                VRClientSettings.renderScaleFactor = 0.8f;
                VRClientSettings.mirrorMode = MirrorMode.OFF;
                VRClientSettings.shaderPerEyePipelines = false;
                VRClientSettings.selfShadowEnabled = false;
            }
            case "immersion" -> {
                VRClientSettings.loadDefaults();
                VRClientSettings.physicalSneaking = true;
                VRClientSettings.physicalClimbing = true;
                VRClientSettings.physicalCrawling = true;
                VRClientSettings.realisticJump = true;
                VRClientSettings.roomConsumeEnabled = true;
                VRClientSettings.worldBetterSwinging = true;
                VRClientSettings.worldRoomscaleShield = true;
            }
        }
    }

    private static void applyCustom(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            try {
                switch (k) {
                    case "vrPlayMode" -> VRClientSettings.vrPlayMode = VRPlayMode.valueOf(v);
                    case "worldScale" -> VRClientSettings.worldScale = Float.parseFloat(v);
                    case "renderScaleFactor" -> VRClientSettings.renderScaleFactor = Float.parseFloat(v);
                    case "mirrorMode" -> VRClientSettings.mirrorMode = MirrorMode.valueOf(v);
                    case "mirrorEye" -> VRClientSettings.mirrorEye = Integer.parseInt(v);
                    case "headTracking" -> VRClientSettings.headTracking = Boolean.parseBoolean(v);
                    case "scalingFilter" -> VRClientSettings.scalingFilter = ScalingFilter.valueOf(v);
                    case "graphicsApi" -> VRClientSettings.graphicsApi = GraphicsApi.valueOf(v);
                    case "movementMode" -> VRClientSettings.movementMode = MovementMode.valueOf(v);
                    case "rotationMode" -> VRClientSettings.rotationMode = RotationMode.parse(v, RotationMode.HMD);
                    case "worldRotationStep" -> VRClientSettings.worldRotationStep = Float.parseFloat(v);
                    case "worldRotationSmoothSensitivity" -> VRClientSettings.worldRotationSmoothSensitivity = Float.parseFloat(v);
                    case "walkUpEnabled" -> VRClientSettings.walkUpEnabled = Boolean.parseBoolean(v);
                    case "sprintThreshold" -> VRClientSettings.sprintThreshold = Float.parseFloat(v);
                    case "leftHanded" -> VRClientSettings.leftHanded = Boolean.parseBoolean(v);
                    case "hapticsEnabled" -> VRClientSettings.hapticsEnabled = Boolean.parseBoolean(v);
                    case "physicalSneaking" -> VRClientSettings.physicalSneaking = Boolean.parseBoolean(v);
                    case "physicalClimbing" -> VRClientSettings.physicalClimbing = Boolean.parseBoolean(v);
                    case "physicalCrawling" -> VRClientSettings.physicalCrawling = Boolean.parseBoolean(v);
                    case "realisticJump" -> VRClientSettings.realisticJump = Boolean.parseBoolean(v);
                    case "roomConsumeEnabled" -> VRClientSettings.roomConsumeEnabled = Boolean.parseBoolean(v);
                    case "interactionDistance" -> VRClientSettings.interactionDistance = Float.parseFloat(v);
                    case "vrCrosshair" -> VRClientSettings.vrCrosshair = Boolean.parseBoolean(v);
                    case "hotbarMode" -> VRClientSettings.hotbarMode = Integer.parseInt(v);
                    case "hudDistance" -> VRClientSettings.hudDistance = Float.parseFloat(v);
                    case "hudScale" -> VRClientSettings.hudScale = Float.parseFloat(v);
                    case "curvedHud" -> VRClientSettings.curvedHud = Boolean.parseBoolean(v);
                    case "hotbarCenterRadius" -> VRClientSettings.hotbarCenterRadius = Float.parseFloat(v);
                    case "hotbarHysteresisMargin" -> VRClientSettings.hotbarHysteresisMargin = Float.parseFloat(v);
                    case "hotbarSlotNumbers" -> VRClientSettings.hotbarSlotNumbers = Boolean.parseBoolean(v);
                }
            } catch (Throwable ignored) {}
        }
    }
}
