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
                String filename = f.getName();
                int extension = filename.lastIndexOf('.');
                String name = extension > 0 ? filename.substring(0, extension) : filename;
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
                writer.write("compatibleLookDirection=" + VRClientSettings.compatibleLookDirection); writer.newLine();
                writer.write("sprintThreshold=" + VRClientSettings.sprintThreshold); writer.newLine();
                writer.write("treadmillEnabled=" + VRClientSettings.treadmillEnabled); writer.newLine();
                writer.write("treadmillSpeedScale=" + VRClientSettings.treadmillSpeedScale); writer.newLine();
                writer.write("rotationFlyMode=" + VRClientSettings.rotationFlyMode.name()); writer.newLine();
                writer.write("dhMirrorPasses=" + VRClientSettings.dhMirrorPasses); writer.newLine();
                writer.write("shaderPerEyePipelines=" + VRClientSettings.shaderPerEyePipelines); writer.newLine();
                writer.write("shaderSharedShadows=" + VRClientSettings.shaderSharedShadows); writer.newLine();
                writer.write("shaderSharedSsbo=" + VRClientSettings.shaderSharedSsbo); writer.newLine();
                writer.write("selfShadowEnabled=" + VRClientSettings.selfShadowEnabled); writer.newLine();
                writer.write("lowHealthIndicatorEnabled=" + VRClientSettings.lowHealthIndicatorEnabled); writer.newLine();
                writer.write("hitIndicatorEnabled=" + VRClientSettings.hitIndicatorEnabled); writer.newLine();
                writer.write("freezeEffectEnabled=" + VRClientSettings.freezeEffectEnabled); writer.newLine();
                writer.write("pumpkinEffectEnabled=" + VRClientSettings.pumpkinEffectEnabled); writer.newLine();
                writer.write("thirdPersonFov=" + VRClientSettings.thirdPersonFov); writer.newLine();
                writer.write("thirdPersonCameraPosX=" + VRClientSettings.thirdPersonCameraPosX); writer.newLine();
                writer.write("thirdPersonCameraPosY=" + VRClientSettings.thirdPersonCameraPosY); writer.newLine();
                writer.write("thirdPersonCameraPosZ=" + VRClientSettings.thirdPersonCameraPosZ); writer.newLine();
                writer.write("mixedRealityRenderHands=" + VRClientSettings.mixedRealityRenderHands); writer.newLine();
                writer.write("mixedRealityAsGrid2x2=" + VRClientSettings.mixedRealityAsGrid2x2); writer.newLine();
                writer.write("mixedRealityWithFirstPerson=" + VRClientSettings.mixedRealityWithFirstPerson); writer.newLine();
                writer.write("mixedRealityAlphaMask=" + VRClientSettings.mixedRealityAlphaMask); writer.newLine();
                writer.write("mixedRealityFov=" + VRClientSettings.mixedRealityFov); writer.newLine();
                writer.write("mixedRealityAspectRatio=" + VRClientSettings.mixedRealityAspectRatio); writer.newLine();
            }
            if (controls) {
                writer.write("leftHanded=" + VRClientSettings.leftHanded); writer.newLine();
                writer.write("hapticsEnabled=" + VRClientSettings.hapticsEnabled); writer.newLine();
                writer.write("physicalSneaking=" + VRClientSettings.physicalSneaking); writer.newLine();
                writer.write("physicalClimbing=" + VRClientSettings.physicalClimbing); writer.newLine();
                writer.write("physicalCrawling=" + VRClientSettings.physicalCrawling); writer.newLine();
                writer.write("realisticJump=" + VRClientSettings.realisticJump); writer.newLine();
                writer.write("roomSwimEnabled=" + VRClientSettings.roomSwimEnabled); writer.newLine();
                writer.write("roomDismountVehicleEnabled=" + VRClientSettings.roomDismountVehicleEnabled); writer.newLine();
                writer.write("roomBoatRowingEnabled=" + VRClientSettings.roomBoatRowingEnabled); writer.newLine();
                writer.write("roomConsumeEnabled=" + VRClientSettings.roomConsumeEnabled); writer.newLine();
                writer.write("roomSneakThreshold=" + VRClientSettings.roomSneakThreshold); writer.newLine();
                writer.write("roomCrawlThreshold=" + VRClientSettings.roomCrawlThreshold); writer.newLine();
                writer.write("roomJumpThreshold=" + VRClientSettings.roomJumpThreshold); writer.newLine();
                writer.write("swingSpeedThreshold=" + VRClientSettings.swingSpeedThreshold); writer.newLine();
                writer.write("swingInCreative=" + VRClientSettings.swingInCreative); writer.newLine();
                writer.write("defaultVrBody=" + VRClientSettings.defaultVrBody); writer.newLine();
                writer.write("vrBodyModel=" + VRClientSettings.vrBodyModel); writer.newLine();
                writer.write("fbtEnabled=" + VRClientSettings.fbtEnabled); writer.newLine();
                writer.write("handTrackingEnabled=" + VRClientSettings.handTrackingEnabled); writer.newLine();
                writer.write("bodyHapticsEnabled=" + VRClientSettings.bodyHapticsEnabled); writer.newLine();
                writer.write("playerModelArmsScale=" + VRClientSettings.playerModelArmsScale); writer.newLine();
                writer.write("playerModelBodyScale=" + VRClientSettings.playerModelBodyScale); writer.newLine();
                writer.write("playerModelLegScale=" + VRClientSettings.playerModelLegScale); writer.newLine();
                writer.write("heightMode=" + VRClientSettings.heightMode.name()); writer.newLine();
                writer.write("heightAdjustment=" + VRClientSettings.heightAdjustment.name()); writer.newLine();
                writer.write("heightUnits=" + VRClientSettings.heightUnits.name()); writer.newLine();
                writer.write("heightAuto=" + VRClientSettings.heightAuto); writer.newLine();
                writer.write("playerHeight=" + VRClientSettings.playerHeight); writer.newLine();
                writer.write("seated=" + VRClientSettings.seated); writer.newLine();
                writer.write("hapticStrength=" + VRClientSettings.hapticStrength); writer.newLine();
                writer.write("worldTwoHanded=" + VRClientSettings.worldTwoHanded); writer.newLine();
                writer.write("worldBetterSwinging=" + VRClientSettings.worldBetterSwinging); writer.newLine();
                writer.write("worldRoomscaleShield=" + VRClientSettings.worldRoomscaleShield); writer.newLine();
                writer.write("worldAttacksWhileBlocking=" + VRClientSettings.worldAttacksWhileBlocking); writer.newLine();
                writer.write("worldRoomCrawling=" + VRClientSettings.worldRoomCrawling); writer.newLine();
                writer.write("worldRoomClimbing=" + VRClientSettings.worldRoomClimbing); writer.newLine();
                writer.write("worldSupportedMovement=" + VRClientSettings.worldSupportedMovement); writer.newLine();
                writer.write("worldSupportedHeightMode=" + VRClientSettings.worldSupportedHeightMode); writer.newLine();
                writer.write("antiXrayVignette=" + VRClientSettings.antiXrayVignette); writer.newLine();
                writer.write("controllerRightTriggerKeyCode=" + VRClientSettings.controllerRightTriggerKeyCode); writer.newLine();
                writer.write("controllerLeftTriggerKeyCode=" + VRClientSettings.controllerLeftTriggerKeyCode); writer.newLine();
                writer.write("controllerRightAKeyCode=" + VRClientSettings.controllerRightAKeyCode); writer.newLine();
                writer.write("controllerRightBKeyCode=" + VRClientSettings.controllerRightBKeyCode); writer.newLine();
                writer.write("controllerLeftXKeyCode=" + VRClientSettings.controllerLeftXKeyCode); writer.newLine();
                writer.write("controllerLeftYKeyCode=" + VRClientSettings.controllerLeftYKeyCode); writer.newLine();
                writer.write("controllerLeftMenuKeyCode=" + VRClientSettings.controllerLeftMenuKeyCode); writer.newLine();
                writer.write("controllerRightStickClickKeyCode=" + VRClientSettings.controllerRightStickClickKeyCode); writer.newLine();
                writer.write("controllerLeftStickClickKeyCode=" + VRClientSettings.controllerLeftStickClickKeyCode); writer.newLine();
                writer.write("controllerRightGripKeyCode=" + VRClientSettings.controllerRightGripKeyCode); writer.newLine();
                writer.write("controllerLeftGripKeyCode=" + VRClientSettings.controllerLeftGripKeyCode); writer.newLine();
                writer.write("controllerLeftStickUpKeyCode=" + VRClientSettings.controllerLeftStickUpKeyCode); writer.newLine();
                writer.write("controllerLeftStickDownKeyCode=" + VRClientSettings.controllerLeftStickDownKeyCode); writer.newLine();
                writer.write("controllerLeftStickLeftKeyCode=" + VRClientSettings.controllerLeftStickLeftKeyCode); writer.newLine();
                writer.write("controllerLeftStickRightKeyCode=" + VRClientSettings.controllerLeftStickRightKeyCode); writer.newLine();
                writer.write("controllerRightStickUpKeyCode=" + VRClientSettings.controllerRightStickUpKeyCode); writer.newLine();
                writer.write("controllerRightStickDownKeyCode=" + VRClientSettings.controllerRightStickDownKeyCode); writer.newLine();
                writer.write("controllerRightStickLeftKeyCode=" + VRClientSettings.controllerRightStickLeftKeyCode); writer.newLine();
                writer.write("controllerRightStickRightKeyCode=" + VRClientSettings.controllerRightStickRightKeyCode); writer.newLine();
                writer.write("shoulderGesturesEnabled=" + VRClientSettings.shoulderGesturesEnabled); writer.newLine();
                writer.write("shoulderRightToLeftEnabled=" + VRClientSettings.shoulderRightToLeftEnabled); writer.newLine();
                writer.write("shoulderLeftToRightEnabled=" + VRClientSettings.shoulderLeftToRightEnabled); writer.newLine();
                writer.write("shoulderRightToLeftKeyCode=" + VRClientSettings.shoulderRightToLeftKeyCode); writer.newLine();
                writer.write("shoulderLeftToRightKeyCode=" + VRClientSettings.shoulderLeftToRightKeyCode); writer.newLine();
                writer.write("shoulderGestureDistance=" + VRClientSettings.shoulderGestureDistance); writer.newLine();
                writer.write("shoulderGestureHoldSeconds=" + VRClientSettings.shoulderGestureHoldSeconds); writer.newLine();
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
                writer.write("settingsTextScale=" + VRClientSettings.settingsTextScale); writer.newLine();
                writer.write("guiScale=" + VRClientSettings.guiScale); writer.newLine();
                writer.write("keyboardAutoLayout=" + VRClientSettings.keyboardAutoLayout); writer.newLine();
                writer.write("keyboardLayouts=" + VRClientSettings.keyboardLayouts); writer.newLine();
                writer.write("hudDisableHotbar=" + VRClientSettings.hudDisableHotbar); writer.newLine();
                writer.write("activeKeyboardLayout=" + VRClientSettings.activeKeyboardLayout); writer.newLine();
                for (String overlayId : VROverlayConfig.IDS) {
                    VROverlayConfig.Entry overlay = VROverlayConfig.get(overlayId);
                    String prefix = "overlay." + overlayId + ".";
                    writer.write(prefix + "visible=" + overlay.visible); writer.newLine();
                    writer.write(prefix + "anchor=" + overlay.anchor); writer.newLine();
                    writer.write(prefix + "scale=" + overlay.scale); writer.newLine();
                    writer.write(prefix + "distance=" + overlay.distance); writer.newLine();
                    writer.write(prefix + "offsetX=" + overlay.offsetX); writer.newLine();
                    writer.write(prefix + "offsetY=" + overlay.offsetY); writer.newLine();
                    writer.write(prefix + "flag=" + overlay.flag); writer.newLine();
                }
                for (String offsetKey : offsetKeys()) {
                    writeOffset(writer, offsetKey, getOffset(offsetKey));
                }
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

    private static void resetOverlayDefaults() {
        for (String overlayId : VROverlayConfig.IDS) {
            VROverlayConfig.get(overlayId).set(VROverlayConfig.defaults(overlayId));
        }
        VROverlayConfig.save();
    }

    private static void applyBuiltIn(String id) {
        switch (id) {
            case "defaults" -> {
                VRClientSettings.loadDefaults();
                resetOverlayDefaults();
            }
            case "defaults_hand_hud" -> {
                VRClientSettings.loadDefaults();
                resetOverlayDefaults();
                VRClientSettings.hotbarMode = 0; // Hand/Wrist
                VRClientSettings.hudDistance = 1.8f;
                VRClientSettings.curvedHud = true;
            }
            case "performance" -> {
                VRClientSettings.loadDefaults();
                resetOverlayDefaults();
                VRClientSettings.renderScaleFactor = 0.8f;
                VRClientSettings.mirrorMode = MirrorMode.OFF;
                VRClientSettings.shaderPerEyePipelines = false;
                VRClientSettings.selfShadowEnabled = false;
            }
            case "immersion" -> {
                VRClientSettings.loadDefaults();
                resetOverlayDefaults();
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

    private static String[] offsetKeys() {
        return new String[] {
                "tool_right", "tool_left", "tinker_right", "tinker_left",
                "sword_right", "sword_left", "block_right", "block_left",
                "torch_right", "torch_left", "bow_right", "bow_left",
                "item_right", "item_left"
        };
    }

    private static ItemHandOffset getOffset(String key) {
        return switch (key) {
            case "tool_right" -> VRClientSettings.toolRight;
            case "tool_left" -> VRClientSettings.toolLeft;
            case "tinker_right" -> VRClientSettings.tinkerRight;
            case "tinker_left" -> VRClientSettings.tinkerLeft;
            case "sword_right" -> VRClientSettings.swordRight;
            case "sword_left" -> VRClientSettings.swordLeft;
            case "block_right" -> VRClientSettings.blockRight;
            case "block_left" -> VRClientSettings.blockLeft;
            case "torch_right" -> VRClientSettings.torchRight;
            case "torch_left" -> VRClientSettings.torchLeft;
            case "bow_right" -> VRClientSettings.bowRight;
            case "bow_left" -> VRClientSettings.bowLeft;
            case "item_right" -> VRClientSettings.itemRight;
            case "item_left" -> VRClientSettings.itemLeft;
            default -> null;
        };
    }

    private static void writeOffset(BufferedWriter writer, String key, ItemHandOffset offset) throws IOException {
        if (offset == null) return;
        String prefix = "customOffset." + key + ".";
        writer.write(prefix + "scale=" + offset.scale); writer.newLine();
        writer.write(prefix + "posX=" + offset.posX); writer.newLine();
        writer.write(prefix + "posY=" + offset.posY); writer.newLine();
        writer.write(prefix + "posZ=" + offset.posZ); writer.newLine();
        writer.write(prefix + "pitch=" + offset.pitch); writer.newLine();
        writer.write(prefix + "yaw=" + offset.yaw); writer.newLine();
        writer.write(prefix + "roll=" + offset.roll); writer.newLine();
    }

    private static void applyOffsetValue(String key, String value) {
        String suffix = key.substring("customOffset.".length());
        int separator = suffix.lastIndexOf('.');
        if (separator <= 0 || separator == suffix.length() - 1) return;
        ItemHandOffset offset = getOffset(suffix.substring(0, separator));
        if (offset == null) return;
        float parsed;
        try { parsed = Float.parseFloat(value); } catch (NumberFormatException ignored) { return; }
        switch (suffix.substring(separator + 1)) {
            case "scale" -> offset.scale = Math.max(0.01F, Math.min(4.0F, parsed));
            case "posX" -> offset.posX = Math.max(-2.0F, Math.min(2.0F, parsed));
            case "posY" -> offset.posY = Math.max(-2.0F, Math.min(2.0F, parsed));
            case "posZ" -> offset.posZ = Math.max(-2.0F, Math.min(2.0F, parsed));
            case "pitch" -> offset.pitch = Math.max(-360.0F, Math.min(360.0F, parsed));
            case "yaw" -> offset.yaw = Math.max(-360.0F, Math.min(360.0F, parsed));
            case "roll" -> offset.roll = Math.max(-360.0F, Math.min(360.0F, parsed));
            default -> { }
        }
    }

    private static void applyOverlayValue(String key, String value) {
        String suffix = key.substring("overlay.".length());
        int separator = suffix.lastIndexOf('.');
        if (separator <= 0 || separator == suffix.length() - 1) return;
        String id = suffix.substring(0, separator);
        boolean known = false;
        for (String candidate : VROverlayConfig.IDS) {
            if (candidate.equals(id)) { known = true; break; }
        }
        if (!known) return;
        VROverlayConfig.Entry overlay = VROverlayConfig.get(id);
        switch (suffix.substring(separator + 1)) {
            case "visible" -> overlay.visible = Boolean.parseBoolean(value);
            case "anchor" -> {
                for (String anchor : VROverlayConfig.ANCHORS) {
                    if (anchor.equalsIgnoreCase(value)) { overlay.anchor = anchor; break; }
                }
            }
            case "scale" -> overlay.scale = Math.max(0.2F, Math.min(3.0F, Float.parseFloat(value)));
            case "distance" -> overlay.distance = Math.max(0.1F, Math.min(6.0F, Float.parseFloat(value)));
            case "offsetX" -> overlay.offsetX = Math.max(-1.0F, Math.min(1.0F, Float.parseFloat(value)));
            case "offsetY" -> overlay.offsetY = Math.max(-1.0F, Math.min(1.0F, Float.parseFloat(value)));
            case "flag" -> overlay.flag = Boolean.parseBoolean(value);
            default -> { }
        }
    }

    private static void applyCustom(Map<String, String> values) {
        boolean overlaysChanged = false;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            try {
                if (k.startsWith("customOffset.")) {
                    applyOffsetValue(k, v);
                    continue;
                }
                if (k.startsWith("overlay.")) {
                    applyOverlayValue(k, v);
                    overlaysChanged = true;
                    continue;
                }
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
                    case "compatibleLookDirection" -> VRClientSettings.compatibleLookDirection = Boolean.parseBoolean(v);
                    case "sprintThreshold" -> VRClientSettings.sprintThreshold = Float.parseFloat(v);
                    case "treadmillEnabled" -> VRClientSettings.treadmillEnabled = Boolean.parseBoolean(v);
                    case "treadmillSpeedScale" -> VRClientSettings.treadmillSpeedScale = Float.parseFloat(v);
                    case "rotationFlyMode" -> VRClientSettings.rotationFlyMode = RotationMode.parse(v, RotationMode.OFFHAND);
                    case "dhMirrorPasses" -> VRClientSettings.dhMirrorPasses = Boolean.parseBoolean(v);
                    case "shaderPerEyePipelines" -> VRClientSettings.shaderPerEyePipelines = Boolean.parseBoolean(v);
                    case "shaderSharedShadows" -> VRClientSettings.shaderSharedShadows = Boolean.parseBoolean(v);
                    case "shaderSharedSsbo" -> VRClientSettings.shaderSharedSsbo = Boolean.parseBoolean(v);
                    case "selfShadowEnabled" -> VRClientSettings.selfShadowEnabled = Boolean.parseBoolean(v);
                    case "lowHealthIndicatorEnabled" -> VRClientSettings.lowHealthIndicatorEnabled = Boolean.parseBoolean(v);
                    case "hitIndicatorEnabled" -> VRClientSettings.hitIndicatorEnabled = Boolean.parseBoolean(v);
                    case "freezeEffectEnabled" -> VRClientSettings.freezeEffectEnabled = Boolean.parseBoolean(v);
                    case "pumpkinEffectEnabled" -> VRClientSettings.pumpkinEffectEnabled = Boolean.parseBoolean(v);
                    case "thirdPersonFov" -> VRClientSettings.thirdPersonFov = Float.parseFloat(v);
                    case "thirdPersonCameraPosX" -> VRClientSettings.thirdPersonCameraPosX = Float.parseFloat(v);
                    case "thirdPersonCameraPosY" -> VRClientSettings.thirdPersonCameraPosY = Float.parseFloat(v);
                    case "thirdPersonCameraPosZ" -> VRClientSettings.thirdPersonCameraPosZ = Float.parseFloat(v);
                    case "mixedRealityRenderHands" -> VRClientSettings.mixedRealityRenderHands = Boolean.parseBoolean(v);
                    case "mixedRealityAsGrid2x2" -> VRClientSettings.mixedRealityAsGrid2x2 = Boolean.parseBoolean(v);
                    case "mixedRealityWithFirstPerson" -> VRClientSettings.mixedRealityWithFirstPerson = Boolean.parseBoolean(v);
                    case "mixedRealityAlphaMask" -> VRClientSettings.mixedRealityAlphaMask = Boolean.parseBoolean(v);
                    case "mixedRealityFov" -> VRClientSettings.mixedRealityFov = Float.parseFloat(v);
                    case "mixedRealityAspectRatio" -> VRClientSettings.mixedRealityAspectRatio = Float.parseFloat(v);
                    case "leftHanded" -> VRClientSettings.leftHanded = Boolean.parseBoolean(v);
                    case "hapticsEnabled" -> VRClientSettings.hapticsEnabled = Boolean.parseBoolean(v);
                    case "controllerRightTriggerKeyCode" -> VRClientSettings.controllerRightTriggerKeyCode = Integer.parseInt(v);
                    case "controllerLeftTriggerKeyCode" -> VRClientSettings.controllerLeftTriggerKeyCode = Integer.parseInt(v);
                    case "controllerRightAKeyCode" -> VRClientSettings.controllerRightAKeyCode = Integer.parseInt(v);
                    case "controllerRightBKeyCode" -> VRClientSettings.controllerRightBKeyCode = Integer.parseInt(v);
                    case "controllerLeftXKeyCode" -> VRClientSettings.controllerLeftXKeyCode = Integer.parseInt(v);
                    case "controllerLeftYKeyCode" -> VRClientSettings.controllerLeftYKeyCode = Integer.parseInt(v);
                    case "controllerLeftMenuKeyCode" -> VRClientSettings.controllerLeftMenuKeyCode = Integer.parseInt(v);
                    case "controllerRightStickClickKeyCode" -> VRClientSettings.controllerRightStickClickKeyCode = Integer.parseInt(v);
                    case "controllerLeftStickClickKeyCode" -> VRClientSettings.controllerLeftStickClickKeyCode = Integer.parseInt(v);
                    case "controllerRightGripKeyCode" -> VRClientSettings.controllerRightGripKeyCode = Integer.parseInt(v);
                    case "controllerLeftGripKeyCode" -> VRClientSettings.controllerLeftGripKeyCode = Integer.parseInt(v);
                    case "controllerLeftStickUpKeyCode" -> VRClientSettings.controllerLeftStickUpKeyCode = Integer.parseInt(v);
                    case "controllerLeftStickDownKeyCode" -> VRClientSettings.controllerLeftStickDownKeyCode = Integer.parseInt(v);
                    case "controllerLeftStickLeftKeyCode" -> VRClientSettings.controllerLeftStickLeftKeyCode = Integer.parseInt(v);
                    case "controllerLeftStickRightKeyCode" -> VRClientSettings.controllerLeftStickRightKeyCode = Integer.parseInt(v);
                    case "controllerRightStickUpKeyCode" -> VRClientSettings.controllerRightStickUpKeyCode = Integer.parseInt(v);
                    case "controllerRightStickDownKeyCode" -> VRClientSettings.controllerRightStickDownKeyCode = Integer.parseInt(v);
                    case "controllerRightStickLeftKeyCode" -> VRClientSettings.controllerRightStickLeftKeyCode = Integer.parseInt(v);
                    case "controllerRightStickRightKeyCode" -> VRClientSettings.controllerRightStickRightKeyCode = Integer.parseInt(v);
                    case "shoulderGesturesEnabled" -> VRClientSettings.shoulderGesturesEnabled = Boolean.parseBoolean(v);
                    case "shoulderRightToLeftEnabled" -> VRClientSettings.shoulderRightToLeftEnabled = Boolean.parseBoolean(v);
                    case "shoulderLeftToRightEnabled" -> VRClientSettings.shoulderLeftToRightEnabled = Boolean.parseBoolean(v);
                    case "shoulderRightToLeftKeyCode" -> VRClientSettings.shoulderRightToLeftKeyCode = Integer.parseInt(v);
                    case "shoulderLeftToRightKeyCode" -> VRClientSettings.shoulderLeftToRightKeyCode = Integer.parseInt(v);
                    case "shoulderGestureDistance" -> VRClientSettings.shoulderGestureDistance = Float.parseFloat(v);
                    case "shoulderGestureHoldSeconds" -> VRClientSettings.shoulderGestureHoldSeconds = Float.parseFloat(v);
                    case "physicalSneaking" -> VRClientSettings.physicalSneaking = Boolean.parseBoolean(v);
                    case "physicalClimbing" -> VRClientSettings.physicalClimbing = Boolean.parseBoolean(v);
                    case "physicalCrawling" -> VRClientSettings.physicalCrawling = Boolean.parseBoolean(v);
                    case "realisticJump" -> VRClientSettings.realisticJump = Boolean.parseBoolean(v);
                    case "roomSwimEnabled" -> VRClientSettings.roomSwimEnabled = Boolean.parseBoolean(v);
                    case "roomDismountVehicleEnabled" -> VRClientSettings.roomDismountVehicleEnabled = Boolean.parseBoolean(v);
                    case "roomBoatRowingEnabled" -> VRClientSettings.roomBoatRowingEnabled = Boolean.parseBoolean(v);
                    case "roomConsumeEnabled" -> VRClientSettings.roomConsumeEnabled = Boolean.parseBoolean(v);
                    case "roomSneakThreshold" -> VRClientSettings.roomSneakThreshold = Float.parseFloat(v);
                    case "roomCrawlThreshold" -> VRClientSettings.roomCrawlThreshold = Float.parseFloat(v);
                    case "roomJumpThreshold" -> VRClientSettings.roomJumpThreshold = Float.parseFloat(v);
                    case "swingSpeedThreshold" -> VRClientSettings.swingSpeedThreshold = Float.parseFloat(v);
                    case "swingInCreative" -> VRClientSettings.swingInCreative = Boolean.parseBoolean(v);
                    case "defaultVrBody" -> VRClientSettings.defaultVrBody = v;
                    case "vrBodyModel" -> VRClientSettings.vrBodyModel = v;
                    case "fbtEnabled" -> VRClientSettings.fbtEnabled = Boolean.parseBoolean(v);
                    case "handTrackingEnabled" -> VRClientSettings.handTrackingEnabled = Boolean.parseBoolean(v);
                    case "bodyHapticsEnabled" -> VRClientSettings.bodyHapticsEnabled = Boolean.parseBoolean(v);
                    case "playerModelArmsScale" -> VRClientSettings.playerModelArmsScale = Float.parseFloat(v);
                    case "playerModelBodyScale" -> VRClientSettings.playerModelBodyScale = Float.parseFloat(v);
                    case "playerModelLegScale" -> VRClientSettings.playerModelLegScale = Float.parseFloat(v);
                    case "heightMode" -> VRClientSettings.heightMode = HeightMode.valueOf(v);
                    case "heightAdjustment" -> VRClientSettings.heightAdjustment = HeightAdjustment.valueOf(v);
                    case "heightUnits" -> VRClientSettings.heightUnits = HeightUnits.valueOf(v);
                    case "heightAuto" -> VRClientSettings.heightAuto = Boolean.parseBoolean(v);
                    case "playerHeight" -> VRClientSettings.playerHeight = Float.parseFloat(v);
                    case "seated" -> VRClientSettings.seated = Boolean.parseBoolean(v);
                    case "hapticStrength" -> VRClientSettings.hapticStrength = Float.parseFloat(v);
                    case "worldTwoHanded" -> VRClientSettings.worldTwoHanded = Boolean.parseBoolean(v);
                    case "worldBetterSwinging" -> VRClientSettings.worldBetterSwinging = Boolean.parseBoolean(v);
                    case "worldRoomscaleShield" -> VRClientSettings.worldRoomscaleShield = Boolean.parseBoolean(v);
                    case "worldAttacksWhileBlocking" -> VRClientSettings.worldAttacksWhileBlocking = Boolean.parseBoolean(v);
                    case "worldRoomCrawling" -> VRClientSettings.worldRoomCrawling = Boolean.parseBoolean(v);
                    case "worldRoomClimbing" -> VRClientSettings.worldRoomClimbing = Boolean.parseBoolean(v);
                    case "worldSupportedMovement" -> VRClientSettings.worldSupportedMovement = Integer.parseInt(v);
                    case "worldSupportedHeightMode" -> VRClientSettings.worldSupportedHeightMode = Integer.parseInt(v);
                    case "antiXrayVignette" -> VRClientSettings.antiXrayVignette = Boolean.parseBoolean(v);
                    case "interactionDistance" -> VRClientSettings.interactionDistance = Float.parseFloat(v);
                    case "vrCrosshair" -> VRClientSettings.vrCrosshair = Boolean.parseBoolean(v);
                    case "hotbarMode" -> VRClientSettings.hotbarMode = Integer.parseInt(v);
                    case "settingsTextScale" -> VRClientSettings.settingsTextScale = Float.parseFloat(v);
                    case "guiScale" -> VRClientSettings.guiScale = Float.parseFloat(v);
                    case "keyboardAutoLayout" -> VRClientSettings.keyboardAutoLayout = Boolean.parseBoolean(v);
                    case "keyboardLayouts" -> VRClientSettings.keyboardLayouts = v;
                    case "activeKeyboardLayout" -> VRClientSettings.activeKeyboardLayout = v;
                    case "hudDisableHotbar" -> VRClientSettings.hudDisableHotbar = Boolean.parseBoolean(v);
                    case "hudDistance" -> VRClientSettings.hudDistance = Float.parseFloat(v);
                    case "hudScale" -> VRClientSettings.hudScale = Float.parseFloat(v);
                    case "curvedHud" -> VRClientSettings.curvedHud = Boolean.parseBoolean(v);
                    case "hotbarCenterRadius" -> VRClientSettings.hotbarCenterRadius = Float.parseFloat(v);
                    case "hotbarHysteresisMargin" -> VRClientSettings.hotbarHysteresisMargin = Float.parseFloat(v);
                    case "hotbarSlotNumbers" -> VRClientSettings.hotbarSlotNumbers = Boolean.parseBoolean(v);
                }
            } catch (Throwable ignored) {}
        }
        if (overlaysChanged) {
            VROverlayConfig.save();
        }
    }
}
