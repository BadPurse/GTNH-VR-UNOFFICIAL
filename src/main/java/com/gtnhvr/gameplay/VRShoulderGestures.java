package com.gtnhvr.gameplay;

import com.gtnhvr.GTNHVRLog;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shoulder gesture detector for jetpack and glide activation.
 *
 * - Right hand pointing at left shoulder for >= 1 second -> toggle jetpack ON/OFF
 * - Left hand pointing at right shoulder for >= 1 second -> toggle glide/hover ON/OFF
 *
 * Works with any mod that provides a jetpack or gliding item by:
 * 1. Sending the appropriate keybind press that the mod listens for
 * 2. Using reflection to call mod-specific toggle methods
 * 3. Falling back to simulating the vanilla key event
 *
 * Compatible mods: Adventure Jetpack, Simply Jetpacks, IC2 Jetpack, Modular Powersuits,
 * GraviSuite, EnderIO Glider, OpenBlocks Hang Glider, etc.
 */
@Lwjgl3Aware
public final class VRShoulderGestures {
    private static final VRShoulderGestures INSTANCE = new VRShoulderGestures();

    // Shoulder detection parameters (distance and hold duration are user-configurable).
    private static final float SHOULDER_OFFSET_Y = 0.10F;
    private static final float SHOULDER_OFFSET_X = 0.18F;
    private static final long COOLDOWN_MS = 2000L;

    private static float getGestureDistance() {
        return Math.max(0.10F, Math.min(0.45F, VRClientSettings.shoulderGestureDistance));
    }

    private static long getGestureHoldDurationMs() {
        float seconds = Math.max(0.25F, Math.min(2.50F, VRClientSettings.shoulderGestureHoldSeconds));
        return Math.round(seconds * 1000.0F);
    }

    public static List<String> getJetpackKeyBindingChoices(Minecraft mc) {
        return getKeyBindingChoices(mc, true);
    }

    public static List<String> getGlideKeyBindingChoices(Minecraft mc) {
        return getKeyBindingChoices(mc, false);
    }

    private static List<String> getKeyBindingChoices(Minecraft mc, boolean jetpack) {
        Set<String> choices = new LinkedHashSet<>();
        choices.add("AUTO");
        if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindings == null) {
            return new ArrayList<>(choices);
        }

        for (net.minecraft.client.settings.KeyBinding key : mc.gameSettings.keyBindings) {
            if (key == null || key.getKeyCode() == 0) continue;
            String description = key.getKeyDescription();
            if (description == null || description.isEmpty()) continue;
            String normalized = description.toLowerCase(Locale.ROOT);
            boolean matches;
            if (jetpack) {
                matches = (normalized.contains("jetpack")
                        || normalized.contains("flightmode")
                        || normalized.contains("flight_mode"))
                        && !normalized.contains("hover")
                        && !normalized.contains("glide");
            } else {
                matches = normalized.contains("hover")
                        || normalized.contains("glide")
                        || normalized.contains("jetpack") && normalized.contains("flightmode")
                        || normalized.contains("jetpack") && normalized.contains("flight_mode");
            }
            if (matches) choices.add(description);
        }
        return new ArrayList<>(choices);
    }

    public static String getBindingDisplayName(Minecraft mc, String binding) {
        if (binding == null || binding.isEmpty() || "AUTO".equalsIgnoreCase(binding)) {
            return I18n.format("gtnhvr.controls.shoulder.binding.auto");
        }
        if (mc != null && mc.gameSettings != null && mc.gameSettings.keyBindings != null) {
            for (net.minecraft.client.settings.KeyBinding key : mc.gameSettings.keyBindings) {
                if (key != null && binding.equalsIgnoreCase(key.getKeyDescription())) {
                    String localized = I18n.format(key.getKeyDescription());
                    return localized.equals(key.getKeyDescription()) ? key.getKeyDescription() : localized;
                }
            }
        }
        return I18n.format("gtnhvr.controls.shoulder.binding.missing") + " " + binding;
    }

    // State tracking for right hand -> left shoulder (jetpack)
    private long rightHandOnLeftShoulderStart = 0L;
    private boolean rightHandWasOnShoulder = false;
    private long lastJetpackToggleTime = 0L;

    // State tracking for left hand -> right shoulder (glide)
    private long leftHandOnRightShoulderStart = 0L;
    private boolean leftHandWasOnShoulder = false;
    private long lastGlideToggleTime = 0L;

    // Cached reflection methods (looked up once)
    private static boolean reflectionInitialized = false;
    private static Method adventureJetpackToggle = null;
    private static Method simplyJetpacksToggle = null;
    private static Method graviSuiteToggle = null;

    private VRShoulderGestures() {}

    public static VRShoulderGestures getInstance() {
        return INSTANCE;
    }

    /**
     * Called every frame from VRControllerHandler.handleInWorldInteraction().
     */
    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!VRClientSettings.shoulderJetpackEnabled) {
            resetState();
            return;
        }
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            resetState();
            return;
        }

        boolean jetpackGestureEnabled = VRClientSettings.shoulderJetpackGestureEnabled;
        boolean glideGestureEnabled = VRClientSettings.shoulderGlideGestureEnabled;
        if (!jetpackGestureEnabled) rightHandWasOnShoulder = false;
        if (!glideGestureEnabled) leftHandWasOnShoulder = false;
        if (!jetpackGestureEnabled && !glideGestureEnabled) return;

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) {
            resetState();
            return;
        }

        Vector3f headPos = new Vector3f(
                headPose.position$().x(),
                headPose.position$().y(),
                headPose.position$().z()
        );
        Quaternionf headRot = new Quaternionf(
                headPose.orientation().x(),
                headPose.orientation().y(),
                headPose.orientation().z(),
                headPose.orientation().w()
        );

        // Calculate shoulder positions in room space
        // Left shoulder: head position + offset to the left and slightly down
        Vector3f leftShoulder = new Vector3f();
        headRot.transform(new Vector3f(-SHOULDER_OFFSET_X, -SHOULDER_OFFSET_Y, 0.0F), leftShoulder);
        leftShoulder.add(headPos);

        // Right shoulder: head position + offset to the right and slightly down
        Vector3f rightShoulder = new Vector3f();
        headRot.transform(new Vector3f(SHOULDER_OFFSET_X, -SHOULDER_OFFSET_Y, 0.0F), rightShoulder);
        rightShoulder.add(headPos);

        long now = System.currentTimeMillis();
        float distanceThreshold = getGestureDistance();
        long holdDurationMs = getGestureHoldDurationMs();

        // RIGHT CONTROLLER / MAIN HAND -> LEFT SHOULDER: jetpack toggle.
        // Use the grip pose when available; the aim pose can sit several centimetres in front
        // of the controller and make a real shoulder-touch gesture miss its detection radius.
        if (jetpackGestureEnabled && handler.isRightHandTracked()) {
            Vector3f rightHandPos = handler.getRightGripHandPos();
            float distToLeftShoulder = new Vector3f(rightHandPos).sub(leftShoulder).length();

            if (distToLeftShoulder < distanceThreshold) {
                if (!rightHandWasOnShoulder) {
                    rightHandOnLeftShoulderStart = now;
                    rightHandWasOnShoulder = true;
                } else if (now - rightHandOnLeftShoulderStart >= holdDurationMs
                        && now - lastJetpackToggleTime >= COOLDOWN_MS) {
                    toggleJetpack(mc);
                    lastJetpackToggleTime = now;
                    rightHandWasOnShoulder = false;
                    handler.triggerHaptic(false, 30_000_000L, 0.85F, 0);
                    handler.triggerHaptic(true, 15_000_000L, 0.45F, 0);
                }
            } else {
                rightHandWasOnShoulder = false;
            }
        } else {
            rightHandWasOnShoulder = false;
        }

        // LEFT CONTROLLER / OFF HAND -> RIGHT SHOULDER: hover/glide toggle.
        if (glideGestureEnabled && handler.isLeftHandTracked()) {
            Vector3f leftHandPos = handler.getLeftGripHandPos();
            float distToRightShoulder = new Vector3f(leftHandPos).sub(rightShoulder).length();

            if (distToRightShoulder < distanceThreshold) {
                if (!leftHandWasOnShoulder) {
                    leftHandOnRightShoulderStart = now;
                    leftHandWasOnShoulder = true;
                } else if (now - leftHandOnRightShoulderStart >= holdDurationMs
                        && now - lastGlideToggleTime >= COOLDOWN_MS) {
                    toggleGlide(mc);
                    lastGlideToggleTime = now;
                    leftHandWasOnShoulder = false;
                    handler.triggerHaptic(true, 30_000_000L, 0.85F, 0);
                    handler.triggerHaptic(false, 15_000_000L, 0.45F, 0);
                }
            } else {
                leftHandWasOnShoulder = false;
            }
        } else {
            leftHandWasOnShoulder = false;
        }
    }

    /**
     * Attempts to toggle jetpack flight using multiple strategies:
     * 1. Simulate the keybind that Adventure Jetpack / Simply Jetpacks listens for
     * 2. Reflection-based direct API call
     * 3. NBT-based activation for IC2-style jetpacks
     */
    private void toggleJetpack(Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        GTNHVRLog.info("[VR Shoulder Gesture] Jetpack toggle activated!");

        // Honour an explicit user-selected binding before falling back to automatic detection.
        boolean toggled = tryConfiguredKeyBinding(mc, VRClientSettings.shoulderJetpackKeyBinding);
        if (!toggled) {
            toggled = tryKeyToggle(mc,
                    "key.jetpack.toggle", "key.simplyjetpacks.toggle",
                    "key.adventurejetpack.toggle", "key.jetpack",
                    "keybind.jetpack.toggle", "keybind.jetpack.engine");
        }

        // Reflection APIs and generic key-name matching are fallback paths for modpacks that
        // use a different translation key than the common names above.
        if (!toggled) toggled = tryReflectionToggleJetpack(player);
        if (!toggled) toggled = tryGenericJetpackKeybind(mc);
        if (!toggled) toggled = tryIC2JetpackToggle(player);

        if (!toggled) {
            GTNHVRLog.warn("[VR Shoulder Gesture] Could not find an active jetpack toggle key/API. "
                    + "Open VR Controls > Shoulder Gestures and choose the correct registered key.");
        }
    }

    /**
     * Attempts to toggle glide/hover mode using multiple strategies.
     */
    private void toggleGlide(Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        GTNHVRLog.info("[VR Shoulder Gesture] Glide/Hover toggle activated!");

        boolean toggled = tryConfiguredKeyBinding(mc, VRClientSettings.shoulderGlideKeyBinding);
        if (!toggled) {
            toggled = tryKeyToggle(mc,
                    "key.jetpack.hover", "key.simplyjetpacks.hover",
                    "key.adventurejetpack.hover", "key.hover", "key.glide",
                    "keybind.jetpack.hover", "keybind.jetpack.flightmode");
        }
        if (!toggled) toggled = tryReflectionToggleGlide(player);
        if (!toggled) toggled = tryGenericHoverKeybind(mc);

        if (!toggled) {
            GTNHVRLog.warn("[VR Shoulder Gesture] Could not find an active hover/glide toggle key/API. "
                    + "Open VR Controls > Shoulder Gestures and choose the correct registered key.");
        }
    }

    private boolean tryConfiguredKeyBinding(Minecraft mc, String configuredDescription) {
        if (configuredDescription == null || configuredDescription.isEmpty()
                || "AUTO".equalsIgnoreCase(configuredDescription)) {
            return false;
        }
        if (tryKeyToggle(mc, configuredDescription)) return true;
        GTNHVRLog.warn("[VR Shoulder Gesture] Selected key binding '%s' is unavailable or unbound; trying AUTO fallback.",
                configuredDescription);
        return false;
    }

    public static boolean isAutoKeyBinding(String binding) {
        return binding == null || binding.isEmpty() || "AUTO".equalsIgnoreCase(binding);
    }

    /**
     * Tries to find and press registered keybinds by their exact translation-key description.
     */
    private boolean tryKeyToggle(Minecraft mc, String... descriptions) {
        try {
            net.minecraft.client.settings.KeyBinding[] allKeys = mc.gameSettings.keyBindings;
            if (allKeys == null) return false;

            for (net.minecraft.client.settings.KeyBinding kb : allKeys) {
                if (kb == null) continue;
                String desc = kb.getKeyDescription();
                if (desc == null) continue;

                for (String target : descriptions) {
                    if (desc.equalsIgnoreCase(target)) {
                        int keyCode = kb.getKeyCode();
                        if (keyCode != 0) {
                            // Simulate a key press and release
                            net.minecraft.client.settings.KeyBinding.setKeyBindState(keyCode, true);
                            net.minecraft.client.settings.KeyBinding.onTick(keyCode);
                            // Schedule release on next tick
                            scheduleKeyRelease(keyCode);
                            GTNHVRLog.info("[VR Shoulder Gesture] Pressed keybind: %s (keyCode=%d)", desc, keyCode);
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("[VR Shoulder Gesture] Error trying keybind toggle: %s", t);
        }
        return false;
    }

    private int pendingKeyRelease = 0;
    private long pendingKeyReleaseAtNanos = 0L;

    private void scheduleKeyRelease(int keyCode) {
        if (pendingKeyRelease != 0 && pendingKeyRelease != keyCode) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(pendingKeyRelease, false);
        }
        pendingKeyRelease = keyCode;
        // Controller updates can happen at 72-120 Hz, not at Minecraft's 20 TPS. A frame-count
        // delay could release the key before the next game tick polls it, so use wall-clock time.
        pendingKeyReleaseAtNanos = System.nanoTime() + 120_000_000L;
    }

    /** Release synthetic key presses only after they have survived at least one Minecraft tick. */
    public void tick() {
        if (pendingKeyRelease != 0 && pendingKeyReleaseAtNanos != 0L
                && System.nanoTime() >= pendingKeyReleaseAtNanos) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(pendingKeyRelease, false);
            pendingKeyRelease = 0;
            pendingKeyReleaseAtNanos = 0L;
        }
    }

    /**
     * Try to toggle jetpack via reflection on known mod classes.
     */
    private boolean tryReflectionToggleJetpack(EntityPlayer player) {
        initReflection();
        // Try each discovered mod API independently so a broken/incompatible class does not
        // prevent attempts against another installed jetpack mod.
        try {
            if (adventureJetpackToggle != null) {
                adventureJetpackToggle.invoke(null, player);
                return true;
            }
        } catch (Throwable t) {
            GTNHVRLog.throttled("Adventure Jetpack API toggle failed: %s", t);
        }
        try {
            if (simplyJetpacksToggle != null) {
                simplyJetpacksToggle.invoke(null, player);
                return true;
            }
        } catch (Throwable t) {
            GTNHVRLog.throttled("Simply Jetpacks API toggle failed: %s", t);
        }
        return false;
    }

    /**
     * Try to toggle glide via reflection on known mod classes.
     */
    private boolean tryReflectionToggleGlide(EntityPlayer player) {
        initReflection();
        try {
            if (graviSuiteToggle != null) {
                graviSuiteToggle.invoke(null, player);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Try to find any registered keybind containing "jetpack" in its description.
     */
    private boolean tryGenericJetpackKeybind(Minecraft mc) {
        try {
            net.minecraft.client.settings.KeyBinding[] allKeys = mc.gameSettings.keyBindings;
            if (allKeys == null) return false;
            for (net.minecraft.client.settings.KeyBinding kb : allKeys) {
                if (kb == null) continue;
                String desc = kb.getKeyDescription();
                if (desc != null && desc.toLowerCase().contains("jetpack")
                        && !desc.toLowerCase().contains("hover")) {
                    int keyCode = kb.getKeyCode();
                    if (keyCode != 0) {
                        net.minecraft.client.settings.KeyBinding.setKeyBindState(keyCode, true);
                        net.minecraft.client.settings.KeyBinding.onTick(keyCode);
                        scheduleKeyRelease(keyCode);
                        GTNHVRLog.info("[VR Shoulder Gesture] Generic jetpack keybind: %s", desc);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("[VR Shoulder Gesture] Error in generic jetpack keybind: %s", t);
        }
        return false;
    }

    /**
     * Try to find any registered keybind containing "hover" or "glide" in its description.
     */
    private boolean tryGenericHoverKeybind(Minecraft mc) {
        try {
            net.minecraft.client.settings.KeyBinding[] allKeys = mc.gameSettings.keyBindings;
            if (allKeys == null) return false;
            for (net.minecraft.client.settings.KeyBinding kb : allKeys) {
                if (kb == null) continue;
                String desc = kb.getKeyDescription();
                if (desc != null && (desc.toLowerCase().contains("hover")
                        || desc.toLowerCase().contains("glide"))) {
                    int keyCode = kb.getKeyCode();
                    if (keyCode != 0) {
                        net.minecraft.client.settings.KeyBinding.setKeyBindState(keyCode, true);
                        net.minecraft.client.settings.KeyBinding.onTick(keyCode);
                        scheduleKeyRelease(keyCode);
                        GTNHVRLog.info("[VR Shoulder Gesture] Generic hover/glide keybind: %s", desc);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("[VR Shoulder Gesture] Error in generic hover keybind: %s", t);
        }
        return false;
    }

    /**
     * Try IC2 electric jetpack toggle via NBT on chest armor.
     */
    private boolean tryIC2JetpackToggle(EntityPlayer player) {
        try {
            ItemStack chest = player.inventory.armorInventory[2]; // Chestplate slot
            if (chest == null) return false;

            String itemClass = chest.getItem().getClass().getName().toLowerCase();
            if (itemClass.contains("jetpack") || itemClass.contains("quantumsuit")) {
                NBTTagCompound nbt = chest.getTagCompound();
                if (nbt == null) {
                    nbt = new NBTTagCompound();
                    chest.setTagCompound(nbt);
                }
                // IC2 jetpack stores engine state in NBT
                if (nbt.hasKey("engine")) {
                    nbt.setBoolean("engine", !nbt.getBoolean("engine"));
                    GTNHVRLog.info("[VR Shoulder Gesture] IC2 jetpack NBT toggle: engine=%b", nbt.getBoolean("engine"));
                    return true;
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.warn("[VR Shoulder Gesture] IC2 jetpack toggle error: %s", t);
        }
        return false;
    }

    private void trySimulateKey(Minecraft mc, String keyDescription) {
        try {
            net.minecraft.client.settings.KeyBinding[] allKeys = mc.gameSettings.keyBindings;
            if (allKeys == null) return;
            for (net.minecraft.client.settings.KeyBinding kb : allKeys) {
                if (kb == null) continue;
                String desc = kb.getKeyDescription();
                if (desc != null && desc.equalsIgnoreCase(keyDescription)) {
                    int keyCode = kb.getKeyCode();
                    if (keyCode != 0) {
                        net.minecraft.client.settings.KeyBinding.setKeyBindState(keyCode, true);
                        net.minecraft.client.settings.KeyBinding.onTick(keyCode);
                        scheduleKeyRelease(keyCode);
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            // Adventure Jetpack
            Class<?> ajClass = Class.forName("com.adventurejetpack.common.JetpackHandler");
            adventureJetpackToggle = ajClass.getMethod("toggleJetpack", EntityPlayer.class);
            GTNHVRLog.info("[VR Shoulder Gesture] Found Adventure Jetpack API");
        } catch (Throwable ignored) {}
        try {
            // Simply Jetpacks
            Class<?> sjClass = Class.forName("tonius.simplyjetpacks.handler.SyncHandler");
            simplyJetpacksToggle = sjClass.getMethod("processToggle", EntityPlayer.class);
            GTNHVRLog.info("[VR Shoulder Gesture] Found Simply Jetpacks API");
        } catch (Throwable ignored) {}
        try {
            // GraviSuite hover
            Class<?> gsClass = Class.forName("gravisuite.GraviSuiteHandler");
            graviSuiteToggle = gsClass.getMethod("toggleHover", EntityPlayer.class);
            GTNHVRLog.info("[VR Shoulder Gesture] Found GraviSuite API");
        } catch (Throwable ignored) {}
    }

    private void resetState() {
        rightHandWasOnShoulder = false;
        leftHandWasOnShoulder = false;
        rightHandOnLeftShoulderStart = 0L;
        leftHandOnRightShoulderStart = 0L;
    }

    public void clear() {
        resetState();
        if (pendingKeyRelease != 0) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(pendingKeyRelease, false);
        }
        pendingKeyRelease = 0;
        pendingKeyReleaseAtNanos = 0L;
    }
}
