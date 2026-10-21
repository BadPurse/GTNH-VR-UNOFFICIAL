package com.gtnhvr.settings;

import com.gtnhvr.GTNHVRLog;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

@Lwjgl3Aware
public final class VRClientSettings {
    private static Configuration config;

    // Common / Rendering
    public static VRPlayMode vrPlayMode = VRPlayMode.ENABLED;
    public static float worldScale = 1.0f;
    public static MirrorMode mirrorMode = MirrorMode.CROPPED;
    public static int mirrorEye = 0; // 0: LEFT, 1: RIGHT
    public static float renderScaleFactor = 1.0f;
    public static ScalingFilter scalingFilter = ScalingFilter.LANCZOS;
    public static GraphicsApi graphicsApi = GraphicsApi.AUTO;
    public static boolean headTracking = true;
    public static boolean dhMirrorPasses = false;

    // Shaders & Effects
    public static boolean shaderPerEyePipelines = true;
    public static boolean shaderSharedShadows = false;
    public static boolean shaderSharedSsbo = true;
    public static boolean selfShadowEnabled = true;
    public static boolean lowHealthIndicatorEnabled = true;
    public static boolean hitIndicatorEnabled = true;
    public static boolean freezeEffectEnabled = true;
    public static boolean pumpkinEffectEnabled = true;

    // Third Person Mirror
    public static float thirdPersonFov = 40.0f;
    public static float thirdPersonCameraPosX = -1.0f;
    public static float thirdPersonCameraPosY = 2.4f;
    public static float thirdPersonCameraPosZ = 2.75f;

    // Mixed Reality
    public static boolean mixedRealityRenderHands = false;
    public static boolean mixedRealityAsGrid2x2 = true;
    public static boolean mixedRealityWithFirstPerson = true;
    public static boolean mixedRealityAlphaMask = false;
    public static float mixedRealityFov = 40.0f;
    public static float mixedRealityAspectRatio = 16.0f / 9.0f;

    // VR Body & Height
    public static String defaultVrBody = "hands_only";
    public static boolean fbtEnabled = false;
    public static boolean handTrackingEnabled = false;
    public static boolean bodyHapticsEnabled = false;
    public static float playerModelArmsScale = 0.5f;
    public static float playerModelBodyScale = 1.0f;
    public static float playerModelLegScale = 1.0f;
    public static HeightMode heightMode = HeightMode.MATCH_MODEL;
    public static HeightAdjustment heightAdjustment = HeightAdjustment.CAMERA_OFFSET;
    public static HeightUnits heightUnits = HeightUnits.METRIC;
    public static boolean heightAuto = true;
    public static float playerHeight = 1.75f;

    // Controls
    public static boolean leftHanded = false;
    public static boolean hapticsEnabled = true;

    // Custom Hand / Item Offsets (Per-Hand & Per-Category)
    public static final ItemHandOffset DEFAULT_TOOL_RIGHT = new ItemHandOffset(0.41f, 0.025f, 0.355f, 0.23f, -23.0f, 0.0f, -19.0f);
    public static final ItemHandOffset DEFAULT_TOOL_LEFT = new ItemHandOffset(0.41f, 0.035f, 0.355f, 0.24f, -23.0f, 0.0f, -26.0f);
    public static ItemHandOffset toolRight = new ItemHandOffset(0.41f, 0.025f, 0.355f, 0.23f, -23.0f, 0.0f, -19.0f);
    public static ItemHandOffset toolLeft = new ItemHandOffset(0.41f, 0.035f, 0.355f, 0.24f, -23.0f, 0.0f, -26.0f);

    public static final ItemHandOffset DEFAULT_TINKER_RIGHT = new ItemHandOffset(0.41f, 0.375f, 0.500f, 0.375f, -19.0f, 37.0f, -61.0f);
    public static final ItemHandOffset DEFAULT_TINKER_LEFT = new ItemHandOffset(0.41f, 0.405f, 0.500f, 0.405f, -12.0f, 37.0f, -68.0f);
    public static ItemHandOffset tinkerRight = new ItemHandOffset(0.41f, 0.375f, 0.500f, 0.375f, -19.0f, 37.0f, -61.0f);
    public static ItemHandOffset tinkerLeft = new ItemHandOffset(0.41f, 0.405f, 0.500f, 0.405f, -12.0f, 37.0f, -68.0f);

    public static final ItemHandOffset DEFAULT_SWORD_RIGHT = new ItemHandOffset(0.30f, 0.005f, 0.265f, 0.24f, -19.0f, 0.0f, -12.0f);
    public static final ItemHandOffset DEFAULT_SWORD_LEFT = new ItemHandOffset(0.30f, 0.035f, 0.265f, 0.24f, -19.0f, 2.0f, -23.0f);
    public static ItemHandOffset swordRight = new ItemHandOffset(0.30f, 0.005f, 0.265f, 0.24f, -19.0f, 0.0f, -12.0f);
    public static ItemHandOffset swordLeft = new ItemHandOffset(0.30f, 0.035f, 0.265f, 0.24f, -19.0f, 2.0f, -23.0f);

    public static final ItemHandOffset DEFAULT_BLOCK_RIGHT = new ItemHandOffset(0.20f, -0.14f, 0.11f, -0.065f, 5.0f, -149.0f, -5.0f);
    public static final ItemHandOffset DEFAULT_BLOCK_LEFT = new ItemHandOffset(0.20f, 0.035f, 0.11f, 0.16f, 2.0f, -40.0f, -5.0f);
    public static ItemHandOffset blockRight = new ItemHandOffset(0.20f, -0.14f, 0.11f, -0.065f, 5.0f, -149.0f, -5.0f);
    public static ItemHandOffset blockLeft = new ItemHandOffset(0.20f, 0.035f, 0.11f, 0.16f, 2.0f, -40.0f, -5.0f);

    public static final ItemHandOffset DEFAULT_TORCH_RIGHT = new ItemHandOffset(0.26f, -0.045f, -0.075f, -0.11f, -2.0f, 93.0f, 26.0f);
    public static final ItemHandOffset DEFAULT_TORCH_LEFT = new ItemHandOffset(0.24f, 0.005f, -0.075f, -0.10f, 5.0f, 93.0f, 26.0f);
    public static ItemHandOffset torchRight = new ItemHandOffset(0.26f, -0.045f, -0.075f, -0.11f, -2.0f, 93.0f, 26.0f);
    public static ItemHandOffset torchLeft = new ItemHandOffset(0.24f, 0.005f, -0.075f, -0.10f, 5.0f, 93.0f, 26.0f);

    public static final ItemHandOffset DEFAULT_BOW_RIGHT = new ItemHandOffset(0.28f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    public static final ItemHandOffset DEFAULT_BOW_LEFT = new ItemHandOffset(0.28f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    public static ItemHandOffset bowRight = new ItemHandOffset(0.28f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    public static ItemHandOffset bowLeft = new ItemHandOffset(0.28f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    public static final ItemHandOffset DEFAULT_ITEM_RIGHT = new ItemHandOffset(0.20f, -0.075f, 0.0f, 0.0f, 16.0f, 9.0f, 16.0f);
    public static final ItemHandOffset DEFAULT_ITEM_LEFT = new ItemHandOffset(0.20f, 0.075f, 0.0f, -0.005f, 47.0f, -23.0f, 180.0f);
    public static ItemHandOffset itemRight = new ItemHandOffset(0.20f, -0.075f, 0.0f, 0.0f, 16.0f, 9.0f, 16.0f);
    public static ItemHandOffset itemLeft = new ItemHandOffset(0.20f, 0.075f, 0.0f, -0.005f, 47.0f, -23.0f, 180.0f);

    // Compatibility fields
    public static float toolScale = 0.36f;
    public static float toolPosX = 0.0f;
    public static float toolPosY = 0.015f;
    public static float toolPosZ = -0.015f;
    public static float toolPitch = 0.0f;
    public static float toolYaw = 0.0f;
    public static float toolRoll = 0.0f;

    public static float swordScale = 0.36f;
    public static float swordPosX = 0.0f;
    public static float swordPosY = 0.015f;
    public static float swordPosZ = -0.015f;
    public static float swordPitch = 0.0f;
    public static float swordYaw = 0.0f;
    public static float swordRoll = 0.0f;

    public static float blockScale = 0.16f;
    public static float blockPosX = 0.015f;
    public static float blockPosY = 0.025f;
    public static float blockPosZ = -0.090f;
    public static float blockPitch = 20.0f;
    public static float blockYaw = 45.0f;
    public static float blockRoll = 0.0f;

    public static float itemScale = 0.20f;
    public static float itemPosX = 0.0f;
    public static float itemPosY = 0.025f;
    public static float itemPosZ = -0.055f;
    public static float itemPitch = 0.0f;
    public static float itemYaw = 0.0f;
    public static float itemRoll = 0.0f;

    public static ItemHandOffset getHandOffset(com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory cat, boolean isRight) {
        if (cat == null) return isRight ? itemRight : itemLeft;
        return switch (cat) {
            case TOOLS -> isRight ? toolRight : toolLeft;
            case TINKER -> isRight ? tinkerRight : tinkerLeft;
            case SWORDS -> isRight ? swordRight : swordLeft;
            case BLOCKS -> isRight ? blockRight : blockLeft;
            case TORCHES -> isRight ? torchRight : torchLeft;
            case BOWS -> isRight ? bowRight : bowLeft;
            case ITEMS -> isRight ? itemRight : itemLeft;
        };
    }

    public static ItemHandOffset getDefaultHandOffset(com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory cat, boolean isRight) {
        if (cat == null) return isRight ? DEFAULT_ITEM_RIGHT : DEFAULT_ITEM_LEFT;
        return switch (cat) {
            case TOOLS -> isRight ? DEFAULT_TOOL_RIGHT : DEFAULT_TOOL_LEFT;
            case TINKER -> isRight ? DEFAULT_TINKER_RIGHT : DEFAULT_TINKER_LEFT;
            case SWORDS -> isRight ? DEFAULT_SWORD_RIGHT : DEFAULT_SWORD_LEFT;
            case BLOCKS -> isRight ? DEFAULT_BLOCK_RIGHT : DEFAULT_BLOCK_LEFT;
            case TORCHES -> isRight ? DEFAULT_TORCH_RIGHT : DEFAULT_TORCH_LEFT;
            case BOWS -> isRight ? DEFAULT_BOW_RIGHT : DEFAULT_BOW_LEFT;
            case ITEMS -> isRight ? DEFAULT_ITEM_RIGHT : DEFAULT_ITEM_LEFT;
        };
    }

    public static void resetHandOffset(com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory cat, boolean isRight) {
        ItemHandOffset target = getHandOffset(cat, isRight);
        ItemHandOffset def = getDefaultHandOffset(cat, isRight);
        target.copyFrom(def);
    }

    // Movement
    public static MovementMode movementMode = MovementMode.CONTROLLER;
    public static RotationMode rotationMode = RotationMode.HMD;
    public static RotationMode rotationFlyMode = RotationMode.OFFHAND;
    public static float worldRotationStep = 45.0f; // 0: Smooth, 10, 30, 45, 90
    public static float worldRotationSmoothSensitivity = 0.06f;
    public static boolean walkUpEnabled = true;
    public static boolean compatibleLookDirection = true;
    public static float sprintThreshold = 0.9f;
    public static boolean treadmillEnabled = false;
    public static float treadmillSpeedScale = 1.0f;

    // Immersion
    public static boolean physicalSneaking = true;
    public static boolean physicalClimbing = true;
    public static boolean physicalCrawling = true;
    public static boolean realisticJump = false;
    public static boolean roomSneakEnabled = true;
    public static boolean roomClimbEnabled = true;
    public static boolean roomCrawlEnabled = true;
    public static boolean roomJumpEnabled = false;
    public static boolean roomSwimEnabled = true;
    public static boolean roomDismountVehicleEnabled = true;
    public static boolean roomBoatRowingEnabled = true;
    public static boolean roomConsumeEnabled = true;
    public static float roomSneakThreshold = 0.85f;
    public static float roomCrawlThreshold = 0.70f;
    public static float roomJumpThreshold = 1.05f;
    public static float swingSpeedThreshold = 3.0f;
    public static boolean swingInCreative = true;

    // GUI & Overlays
    public static float settingsTextScale = 0.85f;
    public static float guiScale = 0.0f; // 0: Auto
    public static boolean keyboardAutoLayout = true;
    public static String keyboardLayouts = "ENGLISH";
    public static String activeKeyboardLayout = "ENGLISH";
    public static int hotbarMode = 0; // 0: HAND/WRIST, 1: SCREEN, 2: RADIAL
    public static float hudDistance = 1.8f;
    public static float hudScale = 1.0f;
    public static boolean curvedHud = true;
    public static float hotbarCenterRadius = 50.0f;
    public static float hotbarHysteresisMargin = 7.0f;
    public static boolean hotbarSlotNumbers = false;
    public static boolean hudDisableHotbar = false;

    // World
    public static float interactionDistance = 3.5f;
    public static boolean vrCrosshair = true;
    public static boolean worldTwoHanded = true;
    public static boolean worldBetterSwinging = true;
    public static boolean worldRoomscaleShield = true;
    public static boolean worldAttacksWhileBlocking = true;
    public static boolean worldRoomCrawling = true;
    public static boolean worldRoomClimbing = true;
    public static int worldSupportedMovement = 0; // 0: Both, 1: Controller, 2: Teleport
    public static int worldSupportedHeightMode = 0; // 0: Both, 1: Match Model, 2: Real Size

    // Direct Visor compatibility fields & aliases
    public static boolean autoHeight = true;
    public static String vrBodyModel = "Steve (Classic)";
    public static boolean fullBodyTracking = false;
    public static boolean handTracking = false;
    public static boolean bodyHaptics = false;
    public static boolean seated = false;
    public static float hapticStrength = 0.5f;
    public static boolean roomSneak = true;
    public static boolean roomCrawl = true;
    public static boolean roomClimb = true;
    public static boolean roomJump = false;
    public static boolean roomSwim = true;
    public static boolean roomDismountVehicle = true;
    public static boolean roomBoatRowing = true;
    public static boolean roomConsume = true;
    public static boolean hudDisabledHotbar = false;
    public static float interactionReach = 4.5f;
    public static boolean crosshairLaser = false;

    // Autonomous Virtual Screen Resolution & Anti-XRay
    public static boolean autonomousResolution = true;
    public static int virtualScreenWidth = 2388;
    public static int virtualScreenHeight = 1668;
    public static boolean antiXrayVignette = true;

    // Shoulder gesture bindings and detection settings
    public static boolean shoulderJetpackEnabled = true;
    public static boolean shoulderJetpackGestureEnabled = true;
    public static boolean shoulderGlideGestureEnabled = true;
    public static float shoulderGestureDistance = 0.18F;
    public static float shoulderGestureHoldSeconds = 1.0F;
    public static String shoulderJetpackKeyBinding = "AUTO";
    public static String shoulderGlideKeyBinding = "AUTO";

    private VRClientSettings() {}

    public static void init(File configFile) {
        config = new Configuration(configFile);
        VRPresetManager.init(configFile.getParentFile());
        load();
    }

    public static void load() {
        if (config == null) return;
        try {
            config.load();

            // Rendering
            String modeStr = config.getString("vrPlayMode", "rendering", vrPlayMode.name(), "VR play mode");
            try { vrPlayMode = VRPlayMode.valueOf(modeStr); } catch (Exception ignored) {}

            worldScale = (float) config.get("rendering", "worldScale", (double) worldScale, "World scale multiplier").getDouble();

            String mirrorStr = config.getString("mirrorMode", "rendering", mirrorMode.name(), "Desktop mirror mode");
            try { mirrorMode = MirrorMode.valueOf(mirrorStr); } catch (Exception ignored) {}

            mirrorEye = config.getInt("mirrorEye", "rendering", mirrorEye, 0, 1, "Mirror eye: 0 Left, 1 Right");
            renderScaleFactor = (float) config.get("rendering", "renderScaleFactor", (double) renderScaleFactor, "Render resolution scale").getDouble();

            String filterStr = config.getString("scalingFilter", "rendering", scalingFilter.name(), "Texture scaling filter");
            try { scalingFilter = ScalingFilter.valueOf(filterStr); } catch (Exception ignored) {}

            String apiStr = config.getString("graphicsApi", "rendering", graphicsApi.name(), "OpenXR graphics API");
            try { graphicsApi = GraphicsApi.valueOf(apiStr); } catch (Exception ignored) {}

            headTracking = config.getBoolean("headTracking", "rendering", headTracking, "Apply HMD orientation to camera");
            dhMirrorPasses = config.getBoolean("dhMirrorPasses", "rendering", dhMirrorPasses, "Distant Horizons mirror passes");

            // Shaders & Effects
            shaderPerEyePipelines = config.getBoolean("shaderPerEyePipelines", "shaders", shaderPerEyePipelines, "Separate shader pipelines per eye");
            shaderSharedShadows = config.getBoolean("shaderSharedShadows", "shaders", shaderSharedShadows, "Share shadow map passes between eyes");
            shaderSharedSsbo = config.getBoolean("shaderSharedSsbo", "shaders", shaderSharedSsbo, "Share SSBO buffers between eyes");
            selfShadowEnabled = config.getBoolean("selfShadowEnabled", "effects", selfShadowEnabled, "Render self shadow in VR");
            lowHealthIndicatorEnabled = config.getBoolean("lowHealthIndicatorEnabled", "effects", lowHealthIndicatorEnabled, "Eye vignette when low health");
            hitIndicatorEnabled = config.getBoolean("hitIndicatorEnabled", "effects", hitIndicatorEnabled, "Eye flash when taking damage");
            freezeEffectEnabled = config.getBoolean("freezeEffectEnabled", "effects", freezeEffectEnabled, "Eye frost vignette in powdered snow");
            pumpkinEffectEnabled = config.getBoolean("pumpkinEffectEnabled", "effects", pumpkinEffectEnabled, "Wearable pumpkin overlay in VR");

            // Third Person & Mixed Reality
            thirdPersonFov = (float) config.get("thirdperson", "fov", (double) thirdPersonFov, "Third person camera FOV").getDouble();
            thirdPersonCameraPosX = (float) config.get("thirdperson", "posX", (double) thirdPersonCameraPosX, "Camera X offset").getDouble();
            thirdPersonCameraPosY = (float) config.get("thirdperson", "posY", (double) thirdPersonCameraPosY, "Camera Y offset").getDouble();
            thirdPersonCameraPosZ = (float) config.get("thirdperson", "posZ", (double) thirdPersonCameraPosZ, "Camera Z offset").getDouble();
            mixedRealityRenderHands = config.getBoolean("mixedRealityRenderHands", "mixedreality", mixedRealityRenderHands, "Render hands in MR mirror");
            mixedRealityAsGrid2x2 = config.getBoolean("mixedRealityAsGrid2x2", "mixedreality", mixedRealityAsGrid2x2, "Render MR as 2x2 grid");
            mixedRealityWithFirstPerson = config.getBoolean("mixedRealityWithFirstPerson", "mixedreality", mixedRealityWithFirstPerson, "MR includes first person");
            mixedRealityAlphaMask = config.getBoolean("mixedRealityAlphaMask", "mixedreality", mixedRealityAlphaMask, "Render MR alpha mask");
            mixedRealityFov = (float) config.get("mixedreality", "fov", (double) mixedRealityFov, "MR camera FOV").getDouble();

            // Controls
            leftHanded = config.getBoolean("leftHanded", "controls", leftHanded, "Left-handed mode");
            hapticsEnabled = config.getBoolean("hapticsEnabled", "controls", hapticsEnabled, "Controller vibration feedback");

            // Movement
            String mModeStr = config.getString("movementMode", "movement", movementMode.name(), "Movement locomotion mode");
            try { movementMode = MovementMode.valueOf(mModeStr); } catch (Exception ignored) {}

            String rModeStr = config.getString("rotationMode", "movement", rotationMode.name(), "Rotation mode");
            rotationMode = RotationMode.parse(rModeStr, RotationMode.HMD);

            String rfModeStr = config.getString("rotationFlyMode", "movement", rotationFlyMode.name(), "Flight rotation mode");
            rotationFlyMode = RotationMode.parse(rfModeStr, RotationMode.OFFHAND);

            worldRotationStep = (float) config.get("movement", "worldRotationStep", (double) worldRotationStep, "Snap turn angle: 0 for smooth, 10-90 for snap").getDouble();
            worldRotationSmoothSensitivity = (float) config.get("movement", "worldRotationSmoothSensitivity", (double) worldRotationSmoothSensitivity, "Smooth turn speed").getDouble();
            walkUpEnabled = config.getBoolean("walkUpEnabled", "movement", walkUpEnabled, "Auto-step 1 block heights in VR");
            compatibleLookDirection = config.getBoolean("compatibleLookDirection", "movement", compatibleLookDirection, "HMD aim compatibility for mods");
            sprintThreshold = (float) config.get("movement", "sprintThreshold", (double) sprintThreshold, "Thumbstick push threshold to sprint").getDouble();
            treadmillEnabled = config.getBoolean("treadmillEnabled", "movement", treadmillEnabled, "KAT Walk / Omnidirectional treadmill locomotion");
            treadmillSpeedScale = (float) config.get("movement", "treadmillSpeedScale", (double) treadmillSpeedScale, "Treadmill speed scale").getDouble();

            // GUI & Overlays
            settingsTextScale = (float) config.get("gui", "settingsTextScale", (double) settingsTextScale, "Settings text font scale").getDouble();
            guiScale = (float) config.get("gui", "guiScale", (double) guiScale, "Virtual GUI scale multiplier").getDouble();
            keyboardAutoLayout = config.getBoolean("keyboardAutoLayout", "gui", keyboardAutoLayout, "Auto-switch VR keyboard layout");
            keyboardLayouts = config.getString("keyboardLayouts", "gui", keyboardLayouts, "Active VR keyboard layouts");
            hotbarMode = config.getInt("hotbarMode", "gui", hotbarMode, 0, 2, "0: Hand/Wrist HUD, 1: Screen HUD, 2: Radial Selector");
            hudDistance = (float) config.get("gui", "hudDistance", (double) hudDistance, "HUD distance in meters").getDouble();
            hudScale = (float) config.get("gui", "hudScale", (double) hudScale, "HUD size scale").getDouble();
            curvedHud = config.getBoolean("curvedHud", "gui", curvedHud, "Curve VR HUD around player");
            hotbarCenterRadius = (float) config.get("gui", "hotbarCenterRadius", (double) hotbarCenterRadius, "Hotbar center deadzone radius").getDouble();
            hotbarHysteresisMargin = (float) config.get("gui", "hotbarHysteresisMargin", (double) hotbarHysteresisMargin, "Hotbar selection angular hysteresis").getDouble();
            hotbarSlotNumbers = config.getBoolean("hotbarSlotNumbers", "gui", hotbarSlotNumbers, "Render slot number badges in hotbar");
            hudDisableHotbar = config.getBoolean("hudDisableHotbar", "gui", hudDisableHotbar, "Disable vanilla bottom hotbar render");

            // VR Body & Height
            defaultVrBody = config.getString("defaultVrBody", "body", defaultVrBody, "Active VR body model (hands_only, steamvr_hands, player_model)");
            fbtEnabled = config.getBoolean("fbtEnabled", "body", fbtEnabled, "Full Body Tracking via Vive/Slime trackers");
            handTrackingEnabled = config.getBoolean("handTrackingEnabled", "body", handTrackingEnabled, "Optical hand tracking without controllers");
            bodyHapticsEnabled = config.getBoolean("bodyHapticsEnabled", "body", bodyHapticsEnabled, "Haptic vest / bHaptics support");
            playerModelArmsScale = (float) config.get("body", "playerModelArmsScale", (double) playerModelArmsScale, "Avatar arm thickness scale").getDouble();
            playerModelBodyScale = (float) config.get("body", "playerModelBodyScale", (double) playerModelBodyScale, "Avatar torso width scale").getDouble();
            playerModelLegScale = (float) config.get("body", "playerModelLegScale", (double) playerModelLegScale, "Avatar legs scale").getDouble();

            String hmStr = config.getString("heightMode", "body", heightMode.name(), "Height calibration mode");
            try { heightMode = HeightMode.valueOf(hmStr); } catch (Exception ignored) {}

            String haStr = config.getString("heightAdjustment", "body", heightAdjustment.name(), "Height adjustment target");
            try { heightAdjustment = HeightAdjustment.valueOf(haStr); } catch (Exception ignored) {}

            String huStr = config.getString("heightUnits", "body", heightUnits.name(), "Display height units (METRIC or IMPERIAL)");
            try { heightUnits = HeightUnits.valueOf(huStr); } catch (Exception ignored) {}

            heightAuto = config.getBoolean("heightAuto", "body", heightAuto, "Auto-measure player height on startup");
            playerHeight = (float) config.get("body", "playerHeight", (double) playerHeight, "Calibrated player standing height in meters").getDouble();

            // Immersion
            physicalSneaking = config.getBoolean("physicalSneaking", "immersion", physicalSneaking, "Sneak physically by bending knees");
            physicalClimbing = config.getBoolean("physicalClimbing", "immersion", physicalClimbing, "Climb ladders by grabbing with hands");
            physicalCrawling = config.getBoolean("physicalCrawling", "immersion", physicalCrawling, "Crawl physically in 1-block spaces");
            realisticJump = config.getBoolean("realisticJump", "immersion", realisticJump, "Jump by physical jumping");
            roomSwimEnabled = config.getBoolean("roomSwimEnabled", "immersion", roomSwimEnabled, "Swim using breaststroke arm motions");
            roomDismountVehicleEnabled = config.getBoolean("roomDismountVehicleEnabled", "immersion", roomDismountVehicleEnabled, "Dismount vehicle by physically standing up");
            roomBoatRowingEnabled = config.getBoolean("roomBoatRowingEnabled", "immersion", roomBoatRowingEnabled, "Row boats with hands");
            roomConsumeEnabled = config.getBoolean("roomConsumeEnabled", "immersion", roomConsumeEnabled, "Eat food by bringing it to mouth");
            roomSneakThreshold = (float) config.get("immersion", "roomSneakThreshold", (double) roomSneakThreshold, "Sneak height ratio threshold").getDouble();
            roomCrawlThreshold = (float) config.get("immersion", "roomCrawlThreshold", (double) roomCrawlThreshold, "Crawl height ratio threshold").getDouble();
            roomJumpThreshold = (float) config.get("immersion", "roomJumpThreshold", (double) roomJumpThreshold, "Jump HMD delta threshold").getDouble();
            swingSpeedThreshold = (float) config.get("immersion", "swingSpeedThreshold", (double) swingSpeedThreshold, "Controller attack swing speed threshold in m/s").getDouble();
            swingInCreative = config.getBoolean("swingInCreative", "immersion", swingInCreative, "Allow physical weapon swing in creative mode");

            // World
            interactionDistance = (float) config.get("world", "interactionDistance", (double) interactionDistance, "Max block interaction reach in meters").getDouble();
            vrCrosshair = config.getBoolean("vrCrosshair", "world", vrCrosshair, "Show laser crosshair on targeted blocks");
            worldTwoHanded = config.getBoolean("worldTwoHanded", "world", worldTwoHanded, "Two-handed weapon holding");
            worldBetterSwinging = config.getBoolean("worldBetterSwinging", "world", worldBetterSwinging, "Smooth directional combat swinging");
            worldRoomscaleShield = config.getBoolean("worldRoomscaleShield", "world", worldRoomscaleShield, "Block projectiles using physical controller position");
            worldAttacksWhileBlocking = config.getBoolean("worldAttacksWhileBlocking", "world", worldAttacksWhileBlocking, "Allow swinging sword while raising shield");
            worldRoomCrawling = config.getBoolean("worldRoomCrawling", "world", worldRoomCrawling, "Allow crawling in 1-block crawlspaces");
            worldRoomClimbing = config.getBoolean("worldRoomClimbing", "world", worldRoomClimbing, "Allow climbing ladders in survival");
            worldSupportedMovement = config.getInt("worldSupportedMovement", "world", worldSupportedMovement, 0, 2, "0: Both, 1: Controller, 2: Teleport");
            worldSupportedHeightMode = config.getInt("worldSupportedHeightMode", "world", worldSupportedHeightMode, 0, 2, "0: Both, 1: Match Model, 2: Real Size");

            // Custom Hand Offsets (Per-Hand & Per-Category)
            loadOffset("tool_right", toolRight, DEFAULT_TOOL_RIGHT);
            loadOffset("tool_left", toolLeft, DEFAULT_TOOL_LEFT);
            loadOffset("tinker_right", tinkerRight, DEFAULT_TINKER_RIGHT);
            loadOffset("tinker_left", tinkerLeft, DEFAULT_TINKER_LEFT);
            loadOffset("sword_right", swordRight, DEFAULT_SWORD_RIGHT);
            loadOffset("sword_left", swordLeft, DEFAULT_SWORD_LEFT);
            loadOffset("block_right", blockRight, DEFAULT_BLOCK_RIGHT);
            loadOffset("block_left", blockLeft, DEFAULT_BLOCK_LEFT);
            loadOffset("torch_right", torchRight, DEFAULT_TORCH_RIGHT);
            loadOffset("torch_left", torchLeft, DEFAULT_TORCH_LEFT);
            loadOffset("bow_right", bowRight, DEFAULT_BOW_RIGHT);
            loadOffset("bow_left", bowLeft, DEFAULT_BOW_LEFT);
            loadOffset("item_right", itemRight, DEFAULT_ITEM_RIGHT);
            loadOffset("item_left", itemLeft, DEFAULT_ITEM_LEFT);

            // Sync compatibility aliases
            autoHeight = heightAuto;
            vrBodyModel = defaultVrBody;
            fullBodyTracking = fbtEnabled;
            handTracking = handTrackingEnabled;
            bodyHaptics = bodyHapticsEnabled;
            roomSneak = physicalSneaking;
            roomCrawl = physicalCrawling;
            roomClimb = physicalClimbing;
            roomJump = realisticJump;
            roomSwim = roomSwimEnabled;
            roomDismountVehicle = roomDismountVehicleEnabled;
            roomBoatRowing = roomBoatRowingEnabled;
            roomConsume = roomConsumeEnabled;
            hudDisabledHotbar = hudDisableHotbar;
            interactionReach = interactionDistance;
            crosshairLaser = vrCrosshair;

            // Shoulder Gestures
            shoulderJetpackEnabled = config.getBoolean("shoulderJetpackEnabled", "world", shoulderJetpackEnabled, "Enable shoulder gestures");
            shoulderJetpackGestureEnabled = config.getBoolean("shoulderJetpackGestureEnabled", "world", shoulderJetpackGestureEnabled, "Right hand to left shoulder: toggle jetpack");
            shoulderGlideGestureEnabled = config.getBoolean("shoulderGlideGestureEnabled", "world", shoulderGlideGestureEnabled, "Left hand to right shoulder: toggle hover or glide");
            shoulderGestureDistance = (float) config.get("world", "shoulderGestureDistance", (double) shoulderGestureDistance, "Shoulder gesture detection distance in meters", 0.10D, 0.45D).getDouble();
            shoulderGestureHoldSeconds = (float) config.get("world", "shoulderGestureHoldSeconds", (double) shoulderGestureHoldSeconds, "How long a hand must stay at a shoulder", 0.25D, 2.50D).getDouble();
            shoulderJetpackKeyBinding = config.getString("shoulderJetpackKeyBinding", "world", shoulderJetpackKeyBinding, "Key binding description for the jetpack shoulder gesture; AUTO searches available bindings");
            shoulderGlideKeyBinding = config.getString("shoulderGlideKeyBinding", "world", shoulderGlideKeyBinding, "Key binding description for the hover/glide shoulder gesture; AUTO searches available bindings");

            if (config.hasChanged()) {
                config.save();
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to load Visor VR settings: %s", t);
        }
    }

    private static void loadOffset(String prefix, ItemHandOffset target, ItemHandOffset def) {
        target.scale = (float) config.get("custom_hands", prefix + "_scale", (double) def.scale).getDouble();
        target.posX = (float) config.get("custom_hands", prefix + "_pos_x", (double) def.posX).getDouble();
        target.posY = (float) config.get("custom_hands", prefix + "_pos_y", (double) def.posY).getDouble();
        target.posZ = (float) config.get("custom_hands", prefix + "_pos_z", (double) def.posZ).getDouble();
        target.pitch = (float) config.get("custom_hands", prefix + "_pitch", (double) def.pitch).getDouble();
        target.yaw = (float) config.get("custom_hands", prefix + "_yaw", (double) def.yaw).getDouble();
        target.roll = (float) config.get("custom_hands", prefix + "_roll", (double) def.roll).getDouble();
    }

    private static void saveOffset(String prefix, ItemHandOffset target, ItemHandOffset def) {
        config.get("custom_hands", prefix + "_scale", (double) def.scale).set((double) target.scale);
        config.get("custom_hands", prefix + "_pos_x", (double) def.posX).set((double) target.posX);
        config.get("custom_hands", prefix + "_pos_y", (double) def.posY).set((double) target.posY);
        config.get("custom_hands", prefix + "_pos_z", (double) def.posZ).set((double) target.posZ);
        config.get("custom_hands", prefix + "_pitch", (double) def.pitch).set((double) target.pitch);
        config.get("custom_hands", prefix + "_yaw", (double) def.yaw).set((double) target.yaw);
        config.get("custom_hands", prefix + "_roll", (double) def.roll).set((double) target.roll);
    }

    public static void save() {
        if (config == null) return;
        try {
            // Sync compatibility aliases
            heightAuto = autoHeight;
            defaultVrBody = vrBodyModel;
            fbtEnabled = fullBodyTracking;
            handTrackingEnabled = handTracking;
            bodyHapticsEnabled = bodyHaptics;
            physicalSneaking = roomSneak;
            physicalCrawling = roomCrawl;
            physicalClimbing = roomClimb;
            realisticJump = roomJump;
            roomSwimEnabled = roomSwim;
            roomDismountVehicleEnabled = roomDismountVehicle;
            roomBoatRowingEnabled = roomBoatRowing;
            roomConsumeEnabled = roomConsume;
            hudDisableHotbar = hudDisabledHotbar;
            interactionDistance = interactionReach;
            vrCrosshair = crosshairLaser;

            // Rendering
            config.get("rendering", "vrPlayMode", "ENABLED").set(vrPlayMode.name());
            config.get("rendering", "worldScale", 1.0).set((double) worldScale);
            config.get("rendering", "mirrorMode", "CROPPED").set(mirrorMode.name());
            config.get("rendering", "mirrorEye", 0).set(mirrorEye);
            config.get("rendering", "renderScaleFactor", 1.0).set((double) renderScaleFactor);
            config.get("rendering", "scalingFilter", "LANCZOS").set(scalingFilter.name());
            config.get("rendering", "graphicsApi", "AUTO").set(graphicsApi.name());
            config.get("rendering", "headTracking", true).set(headTracking);
            config.get("rendering", "dhMirrorPasses", false).set(dhMirrorPasses);

            // Shaders & Effects
            config.get("shaders", "shaderPerEyePipelines", true).set(shaderPerEyePipelines);
            config.get("shaders", "shaderSharedShadows", false).set(shaderSharedShadows);
            config.get("shaders", "shaderSharedSsbo", true).set(shaderSharedSsbo);
            config.get("effects", "selfShadowEnabled", true).set(selfShadowEnabled);
            config.get("effects", "lowHealthIndicatorEnabled", true).set(lowHealthIndicatorEnabled);
            config.get("effects", "hitIndicatorEnabled", true).set(hitIndicatorEnabled);
            config.get("effects", "freezeEffectEnabled", true).set(freezeEffectEnabled);
            config.get("effects", "pumpkinEffectEnabled", true).set(pumpkinEffectEnabled);

            // Third Person & Mixed Reality
            config.get("thirdperson", "fov", 40.0).set((double) thirdPersonFov);
            config.get("thirdperson", "posX", -1.0).set((double) thirdPersonCameraPosX);
            config.get("thirdperson", "posY", 2.4).set((double) thirdPersonCameraPosY);
            config.get("thirdperson", "posZ", 2.75).set((double) thirdPersonCameraPosZ);
            config.get("mixedreality", "mixedRealityRenderHands", false).set(mixedRealityRenderHands);
            config.get("mixedreality", "mixedRealityAsGrid2x2", true).set(mixedRealityAsGrid2x2);
            config.get("mixedreality", "mixedRealityWithFirstPerson", true).set(mixedRealityWithFirstPerson);
            config.get("mixedreality", "mixedRealityAlphaMask", false).set(mixedRealityAlphaMask);
            config.get("mixedreality", "fov", 40.0).set((double) mixedRealityFov);

            // Controls
            config.get("controls", "leftHanded", false).set(leftHanded);
            config.get("controls", "hapticsEnabled", true).set(hapticsEnabled);

            // Movement
            config.get("movement", "movementMode", "CONTROLLER").set(movementMode.name());
            config.get("movement", "rotationMode", "HMD").set(rotationMode.name());
            config.get("movement", "rotationFlyMode", "OFFHAND").set(rotationFlyMode.name());
            config.get("movement", "worldRotationStep", 45.0).set((double) worldRotationStep);
            config.get("movement", "worldRotationSmoothSensitivity", 0.06).set((double) worldRotationSmoothSensitivity);
            config.get("movement", "walkUpEnabled", true).set(walkUpEnabled);
            config.get("movement", "compatibleLookDirection", true).set(compatibleLookDirection);
            config.get("movement", "sprintThreshold", 0.9).set((double) sprintThreshold);
            config.get("movement", "treadmillEnabled", false).set(treadmillEnabled);
            config.get("movement", "treadmillSpeedScale", 1.0).set((double) treadmillSpeedScale);

            // GUI & Overlays
            config.get("gui", "settingsTextScale", 0.85).set((double) settingsTextScale);
            config.get("gui", "guiScale", 0.0).set((double) guiScale);
            config.get("gui", "keyboardAutoLayout", true).set(keyboardAutoLayout);
            config.get("gui", "keyboardLayouts", "ENGLISH").set(keyboardLayouts);
            config.get("gui", "hotbarMode", 0).set(hotbarMode);
            config.get("gui", "hudDistance", 1.8).set((double) hudDistance);
            config.get("gui", "hudScale", 1.0).set((double) hudScale);
            config.get("gui", "curvedHud", true).set(curvedHud);
            config.get("gui", "hotbarCenterRadius", 50.0).set((double) hotbarCenterRadius);
            config.get("gui", "hotbarHysteresisMargin", 7.0).set((double) hotbarHysteresisMargin);
            config.get("gui", "hotbarSlotNumbers", false).set(hotbarSlotNumbers);
            config.get("gui", "hudDisableHotbar", false).set(hudDisableHotbar);

            // VR Body & Height
            config.get("body", "defaultVrBody", "hands_only").set(defaultVrBody);
            config.get("body", "fbtEnabled", false).set(fbtEnabled);
            config.get("body", "handTrackingEnabled", false).set(handTrackingEnabled);
            config.get("body", "bodyHapticsEnabled", false).set(bodyHapticsEnabled);
            config.get("body", "playerModelArmsScale", 0.5).set((double) playerModelArmsScale);
            config.get("body", "playerModelBodyScale", 1.0).set((double) playerModelBodyScale);
            config.get("body", "playerModelLegScale", 1.0).set((double) playerModelLegScale);
            config.get("body", "heightMode", "MATCH_MODEL").set(heightMode.name());
            config.get("body", "heightAdjustment", "CAMERA_OFFSET").set(heightAdjustment.name());
            config.get("body", "heightUnits", "METRIC").set(heightUnits.name());
            config.get("body", "heightAuto", true).set(heightAuto);
            config.get("body", "playerHeight", 1.75).set((double) playerHeight);

            // Immersion
            config.get("immersion", "physicalSneaking", true).set(physicalSneaking);
            config.get("immersion", "physicalClimbing", true).set(physicalClimbing);
            config.get("immersion", "physicalCrawling", true).set(physicalCrawling);
            config.get("immersion", "realisticJump", false).set(realisticJump);
            config.get("immersion", "roomSwimEnabled", true).set(roomSwimEnabled);
            config.get("immersion", "roomDismountVehicleEnabled", true).set(roomDismountVehicleEnabled);
            config.get("immersion", "roomBoatRowingEnabled", true).set(roomBoatRowingEnabled);
            config.get("immersion", "roomConsumeEnabled", true).set(roomConsumeEnabled);
            config.get("immersion", "roomSneakThreshold", 0.85).set((double) roomSneakThreshold);
            config.get("immersion", "roomCrawlThreshold", 0.70).set((double) roomCrawlThreshold);
            config.get("immersion", "roomJumpThreshold", 1.05).set((double) roomJumpThreshold);
            config.get("immersion", "swingSpeedThreshold", 3.0).set((double) swingSpeedThreshold);
            config.get("immersion", "swingInCreative", true).set(swingInCreative);

            // World
            config.get("world", "interactionDistance", 3.5).set((double) interactionDistance);
            config.get("world", "vrCrosshair", true).set(vrCrosshair);
            config.get("world", "worldTwoHanded", true).set(worldTwoHanded);
            config.get("world", "worldBetterSwinging", true).set(worldBetterSwinging);
            config.get("world", "worldRoomscaleShield", true).set(worldRoomscaleShield);
            config.get("world", "worldAttacksWhileBlocking", true).set(worldAttacksWhileBlocking);
            config.get("world", "worldRoomCrawling", true).set(worldRoomCrawling);
            config.get("world", "worldRoomClimbing", true).set(worldRoomClimbing);
            config.get("world", "worldSupportedMovement", 0).set(worldSupportedMovement);
            config.get("world", "worldSupportedHeightMode", 0).set(worldSupportedHeightMode);
            config.get("world", "shoulderJetpackEnabled", true).set(shoulderJetpackEnabled);
            config.get("world", "shoulderJetpackGestureEnabled", true).set(shoulderJetpackGestureEnabled);
            config.get("world", "shoulderGlideGestureEnabled", true).set(shoulderGlideGestureEnabled);
            config.get("world", "shoulderGestureDistance", 0.18D).set((double) shoulderGestureDistance);
            config.get("world", "shoulderGestureHoldSeconds", 1.0D).set((double) shoulderGestureHoldSeconds);
            config.get("world", "shoulderJetpackKeyBinding", "AUTO").set(shoulderJetpackKeyBinding);
            config.get("world", "shoulderGlideKeyBinding", "AUTO").set(shoulderGlideKeyBinding);

            // Custom Hand Offsets (Per-Hand & Per-Category)
            saveOffset("tool_right", toolRight, DEFAULT_TOOL_RIGHT);
            saveOffset("tool_left", toolLeft, DEFAULT_TOOL_LEFT);
            saveOffset("tinker_right", tinkerRight, DEFAULT_TINKER_RIGHT);
            saveOffset("tinker_left", tinkerLeft, DEFAULT_TINKER_LEFT);
            saveOffset("sword_right", swordRight, DEFAULT_SWORD_RIGHT);
            saveOffset("sword_left", swordLeft, DEFAULT_SWORD_LEFT);
            saveOffset("block_right", blockRight, DEFAULT_BLOCK_RIGHT);
            saveOffset("block_left", blockLeft, DEFAULT_BLOCK_LEFT);
            saveOffset("torch_right", torchRight, DEFAULT_TORCH_RIGHT);
            saveOffset("torch_left", torchLeft, DEFAULT_TORCH_LEFT);
            saveOffset("bow_right", bowRight, DEFAULT_BOW_RIGHT);
            saveOffset("bow_left", bowLeft, DEFAULT_BOW_LEFT);
            saveOffset("item_right", itemRight, DEFAULT_ITEM_RIGHT);
            saveOffset("item_left", itemLeft, DEFAULT_ITEM_LEFT);

            config.save();
        } catch (Throwable t) {
            GTNHVRLog.warn("Failed to save Visor VR settings: %s", t);
        }
    }

    public static void loadDefaults() {
        vrPlayMode = VRPlayMode.ENABLED;
        worldScale = 1.0f;
        mirrorMode = MirrorMode.CROPPED;
        mirrorEye = 0;
        renderScaleFactor = 1.0f;
        scalingFilter = ScalingFilter.LANCZOS;
        graphicsApi = GraphicsApi.AUTO;
        headTracking = true;
        dhMirrorPasses = false;

        shaderPerEyePipelines = true;
        shaderSharedShadows = false;
        shaderSharedSsbo = true;
        selfShadowEnabled = true;
        lowHealthIndicatorEnabled = true;
        hitIndicatorEnabled = true;
        freezeEffectEnabled = true;
        pumpkinEffectEnabled = true;

        thirdPersonFov = 40.0f;
        thirdPersonCameraPosX = -1.0f;
        thirdPersonCameraPosY = 2.4f;
        thirdPersonCameraPosZ = 2.75f;
        mixedRealityRenderHands = false;
        mixedRealityAsGrid2x2 = true;
        mixedRealityWithFirstPerson = true;
        mixedRealityAlphaMask = false;
        mixedRealityFov = 40.0f;
        mixedRealityAspectRatio = 16.0f / 9.0f;

        defaultVrBody = "hands_only";
        fbtEnabled = false;
        handTrackingEnabled = false;
        bodyHapticsEnabled = false;
        playerModelArmsScale = 0.5f;
        playerModelBodyScale = 1.0f;
        playerModelLegScale = 1.0f;
        heightMode = HeightMode.MATCH_MODEL;
        heightAdjustment = HeightAdjustment.CAMERA_OFFSET;
        heightUnits = HeightUnits.METRIC;
        heightAuto = true;
        playerHeight = 1.75f;

        leftHanded = false;
        hapticsEnabled = true;

        movementMode = MovementMode.CONTROLLER;
        rotationMode = RotationMode.HMD;
        rotationFlyMode = RotationMode.OFFHAND;
        worldRotationStep = 45.0f;
        worldRotationSmoothSensitivity = 0.06f;
        walkUpEnabled = true;
        compatibleLookDirection = true;
        sprintThreshold = 0.9f;
        treadmillEnabled = false;
        treadmillSpeedScale = 1.0f;

        physicalSneaking = true;
        physicalClimbing = true;
        physicalCrawling = true;
        realisticJump = false;
        roomSwimEnabled = true;
        roomDismountVehicleEnabled = true;
        roomBoatRowingEnabled = true;
        roomConsumeEnabled = true;
        roomSneakThreshold = 0.85f;
        roomCrawlThreshold = 0.70f;
        roomJumpThreshold = 1.05f;
        swingSpeedThreshold = 3.0f;
        swingInCreative = true;

        settingsTextScale = 0.85f;
        guiScale = 0.0f;
        keyboardAutoLayout = true;
        keyboardLayouts = "ENGLISH";
        hotbarMode = 0;
        hudDistance = 1.8f;
        hudScale = 1.0f;
        curvedHud = true;
        hotbarCenterRadius = 50.0f;
        hotbarHysteresisMargin = 7.0f;
        hotbarSlotNumbers = false;
        hudDisableHotbar = false;

        interactionDistance = 3.5f;
        vrCrosshair = true;
        worldTwoHanded = true;
        worldBetterSwinging = true;
        worldRoomscaleShield = true;
        worldAttacksWhileBlocking = true;
        worldRoomCrawling = true;
        worldRoomClimbing = true;
        worldSupportedMovement = 0;
        worldSupportedHeightMode = 0;
        shoulderJetpackEnabled = true;
        shoulderJetpackGestureEnabled = true;
        shoulderGlideGestureEnabled = true;
        shoulderGestureDistance = 0.18F;
        shoulderGestureHoldSeconds = 1.0F;
        shoulderJetpackKeyBinding = "AUTO";
        shoulderGlideKeyBinding = "AUTO";

        autoHeight = true;
        vrBodyModel = "Steve (Classic)";
        fullBodyTracking = false;
        handTracking = false;
        bodyHaptics = false;
        seated = false;
        hapticStrength = 0.5f;
        roomSneak = true;
        roomCrawl = true;
        roomClimb = true;
        roomJump = false;
        roomSwim = true;
        roomDismountVehicle = true;
        roomBoatRowing = true;
        roomConsume = true;
        hudDisabledHotbar = false;
        interactionReach = 4.5f;
        crosshairLaser = false;

        toolRight.copyFrom(DEFAULT_TOOL_RIGHT);
        toolLeft.copyFrom(DEFAULT_TOOL_LEFT);
        tinkerRight.copyFrom(DEFAULT_TINKER_RIGHT);
        tinkerLeft.copyFrom(DEFAULT_TINKER_LEFT);
        swordRight.copyFrom(DEFAULT_SWORD_RIGHT);
        swordLeft.copyFrom(DEFAULT_SWORD_LEFT);
        blockRight.copyFrom(DEFAULT_BLOCK_RIGHT);
        blockLeft.copyFrom(DEFAULT_BLOCK_LEFT);
        torchRight.copyFrom(DEFAULT_TORCH_RIGHT);
        torchLeft.copyFrom(DEFAULT_TORCH_LEFT);
        bowRight.copyFrom(DEFAULT_BOW_RIGHT);
        bowLeft.copyFrom(DEFAULT_BOW_LEFT);
        itemRight.copyFrom(DEFAULT_ITEM_RIGHT);
        itemLeft.copyFrom(DEFAULT_ITEM_LEFT);

        save();
    }
}
