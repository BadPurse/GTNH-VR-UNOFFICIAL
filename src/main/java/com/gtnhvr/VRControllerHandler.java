package com.gtnhvr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.gui.VRHotbarSelector;
import com.gtnhvr.gui.VRVirtualKeyboard;
import com.gtnhvr.gui.VRVirtualScreen;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * 6-DoF Touch Controller tracking and Visor-identical 1:1 control scheme:
 *
 * Menu / GUI Mode:
 * - Right Controller Aim: raycasts to floating 3D virtual screen.
 * - Multi-layer luminous laser beam (GL_DEPTH_TEST disabled) with circular reticle & dot on screen.
 * - Left Click: Right Trigger squeeze (>0.5F) or Right A button.
 * - Right Click: Right Grip squeeze (>0.5F).
 * - Scroll: Right Thumbstick Y.
 * - Back / Close / Escape: Right B, Left Y, or Left Menu button.
 *
 * In-Game Gameplay Mode (1:1 like Visor):
 * - Attack / Mine / Break: Right Trigger squeeze (>0.40F).
 * - Place Block / Use Item / Interact: Right Button A.
 * - Jump: Right Button B.
 * - Crouch / Sneak: Right Thumbstick Click.
 * - Snap Turn: Right Thumbstick X flick (45 degrees).
 * - 3x3 Hotbar Selector: HOLD Right Side Grip (main hand) or Left Side Grip (offhand).
 *   Tilt thumbstick in 8 directions or center to highlight slot, RELEASE side grip to equip!
 * - Movement (Left Thumbstick): analog velocity and WASD.
 * - Sprint: Left Thumbstick forward > 0.85F or Left Thumbstick Click.
 * - Inventory: Left Button X (opens GuiInventory).
 * - Pause Menu: Left Button Y or Left Menu button (opens GuiIngameMenu).
 */
@Lwjgl3Aware
public final class VRControllerHandler {
    private static final VRControllerHandler INSTANCE = new VRControllerHandler();

    private static final int MAP_RIGHT_TRIGGER = 0;
    private static final int MAP_LEFT_TRIGGER = 1;
    private static final int MAP_RIGHT_A = 2;
    private static final int MAP_RIGHT_B = 3;
    private static final int MAP_LEFT_X = 4;
    private static final int MAP_LEFT_Y = 5;
    private static final int MAP_LEFT_MENU = 6;
    private static final int MAP_RIGHT_STICK_CLICK = 7;
    private static final int MAP_LEFT_STICK_CLICK = 8;
    private static final int MAP_RIGHT_GRIP = 9;
    private static final int MAP_LEFT_GRIP = 10;
    private static final int MAP_LEFT_STICK_UP = 11;
    private static final int MAP_LEFT_STICK_DOWN = 12;
    private static final int MAP_LEFT_STICK_LEFT = 13;
    private static final int MAP_LEFT_STICK_RIGHT = 14;
    private static final int MAP_RIGHT_STICK_UP = 15;
    private static final int MAP_RIGHT_STICK_DOWN = 16;
    private static final int MAP_RIGHT_STICK_LEFT = 17;
    private static final int MAP_RIGHT_STICK_RIGHT = 18;
    private static final int MAPPED_CONTROLLER_KEY_COUNT = 19;

    private boolean initialized = false;
    private XrSession session;
    private XrActionSet actionSet;

    // Actions
    private XrAction aimAction;
    private XrAction gripPoseAction;
    private XrAction triggerAction;
    private XrAction gripAction;
    private XrAction stickAction;
    private XrAction stickClickAction;
    private XrAction primaryAction;    // Right A / Left X
    private XrAction secondaryAction;  // Right B / Left Y
    private XrAction menuAction;       // Left Menu
    private XrAction hapticAction;     // Vibration output

    // Action spaces
    private XrSpace rightAimSpace;
    private XrSpace leftAimSpace;
    private XrSpace rightGripSpace;
    private XrSpace leftGripSpace;

    // Subaction paths
    private long rightSubactionPath = 0L;
    private long leftSubactionPath = 0L;

    // Tracked controller poses
    private final Vector3f rightHandPos = new Vector3f();
    private final Quaternionf rightHandRot = new Quaternionf();
    private boolean rightHandTracked = false;

    private final Vector3f leftHandPos = new Vector3f();
    private final Quaternionf leftHandRot = new Quaternionf();
    private boolean leftHandTracked = false;

    // Grip-pose positions are tracked separately from aim poses. Shoulder gestures need the
    // physical controller location, not the forward-offset ray origin used for laser pointing.
    private final Vector3f rightGripHandPos = new Vector3f();
    private boolean rightGripHandTracked = false;
    private final Vector3f leftGripHandPos = new Vector3f();
    private boolean leftGripHandTracked = false;

    // Input values
    private float rightTriggerValue = 0.0F;
    private float leftTriggerValue = 0.0F;
    private float rightGripValue = 0.0F;
    private float leftGripValue = 0.0F;
    private float rightStickX = 0.0F;
    private float rightStickY = 0.0F;
    private float leftStickX = 0.0F;
    private float leftStickY = 0.0F;
    private boolean rightStickClick = false;
    private boolean lastRightStickClick = false;
    private boolean sneakToggled = false;
    private boolean leftStickClick = false;
    private boolean rightPrimaryValue = false;   // A
    private boolean leftPrimaryValue = false;    // X
    private boolean rightSecondaryValue = false; // B
    private boolean leftSecondaryValue = false;  // Y
    private boolean leftMenuValue = false;

    // Interaction state flags
    private boolean wasInMenu = true;
    private boolean isLeftCursorHand = false; // Auto-switches between left and right hands in menus
    private boolean screenDragHandIsLeft = false;
    private boolean kbDragHandIsLeft = false;
    private boolean menuLeftClicked = false;
    private boolean menuRightClicked = false;
    private boolean backClicked = false;

    private boolean inGameAttackHeld = false;
    /** Hysteresis latch for the analog attack trigger (activate at 0.45, release at 0.25). */
    private boolean rightAttackTriggerHeld = false;
    private boolean leftBindingTriggerHeld = false;
    private final int[] mappedControllerKeyCodes = new int[MAPPED_CONTROLLER_KEY_COUNT];
    private final boolean[] mappedControllerKeyHeld = new boolean[MAPPED_CONTROLLER_KEY_COUNT];
    /** Separate latch so analog grips and stick directions don't flicker around one cutoff. */
    private final boolean[] mappedAnalogLatch = new boolean[MAPPED_CONTROLLER_KEY_COUNT];
    private boolean inGameUseItemHeld = false;
    private int inGameUseItemRepeatDelay = 0;
    private boolean snapTurnCooldown = false;
    private boolean inventoryClicked = false;
    private boolean pauseClicked = false;
    private net.minecraft.world.World lastWorld = null;
    private int worldLoadGraceTicks = 0;
    private boolean lastJumpButtonState = false;
    private long lastJumpPressTime = 0L;
    private long lastMenuWheelTime = 0L;

    private static final Vector2f TEMP_VEC2 = new Vector2f();

    private VRControllerHandler() {}

    public static VRControllerHandler getInstance() {
        return INSTANCE;
    }

    private static class BindingEntry {
        final XrAction action;
        final String path;
        BindingEntry(XrAction action, String path) {
            this.action = action;
            this.path = path;
        }
    }

    public synchronized void init(XrInstance instance, XrSession session) {
        if (initialized) {
            destroy();
        }
        if (instance == null || session == null) return;
        this.session = session;

        try (MemoryStack stack = stackPush()) {
            // 1. Create Action Set
            XrActionSetCreateInfo setInfo = XrActionSetCreateInfo.calloc(stack)
                    .type(XR_TYPE_ACTION_SET_CREATE_INFO)
                    .actionSetName(stack.UTF8("gtnhvr_actions"))
                    .localizedActionSetName(stack.UTF8("GTNH VR Actions"))
                    .priority(0);
            PointerBuffer pSet = stack.mallocPointer(1);
            int setRes = xrCreateActionSet(instance, setInfo, pSet);
            if (setRes != XR_SUCCESS) {
                GTNHVRLog.warn("Failed to create OpenXR action set: error %d", setRes);
                return;
            }
            actionSet = new XrActionSet(pSet.get(0), instance);

            long leftPath = stringToPath(instance, "/user/hand/left", stack);
            long rightPath = stringToPath(instance, "/user/hand/right", stack);
            rightSubactionPath = rightPath;
            leftSubactionPath = leftPath;
            LongBuffer subactionPaths = stack.longs(leftPath, rightPath);
            LongBuffer leftOnly = stack.longs(leftPath);

            // 2. Create Actions
            aimAction = createAction(actionSet, "aim_pose", XR_ACTION_TYPE_POSE_INPUT, "Aim Pose", subactionPaths, stack);
            gripPoseAction = createAction(actionSet, "grip_pose", XR_ACTION_TYPE_POSE_INPUT, "Grip Pose", subactionPaths, stack);
            triggerAction = createAction(actionSet, "trigger", XR_ACTION_TYPE_FLOAT_INPUT, "Trigger", subactionPaths, stack);
            gripAction = createAction(actionSet, "squeeze", XR_ACTION_TYPE_FLOAT_INPUT, "Grip Squeeze", subactionPaths, stack);
            stickAction = createAction(actionSet, "thumbstick", XR_ACTION_TYPE_VECTOR2F_INPUT, "Thumbstick", subactionPaths, stack);
            stickClickAction = createAction(actionSet, "thumbstick_click", XR_ACTION_TYPE_BOOLEAN_INPUT, "Thumbstick Click", subactionPaths, stack);
            primaryAction = createAction(actionSet, "primary_button", XR_ACTION_TYPE_BOOLEAN_INPUT, "Primary Button", subactionPaths, stack);
            secondaryAction = createAction(actionSet, "secondary_button", XR_ACTION_TYPE_BOOLEAN_INPUT, "Secondary Button", subactionPaths, stack);
            menuAction = createAction(actionSet, "menu_button", XR_ACTION_TYPE_BOOLEAN_INPUT, "Menu Button", leftOnly, stack);
            hapticAction = createAction(actionSet, "haptic_output", XR_ACTION_TYPE_VIBRATION_OUTPUT, "Haptic Vibration", subactionPaths, stack);

            // 3. Suggested Bindings for Oculus Touch / Meta Quest
            List<BindingEntry> oculusList = new ArrayList<>();
            oculusList.add(new BindingEntry(aimAction, "/user/hand/right/input/aim/pose"));
            oculusList.add(new BindingEntry(aimAction, "/user/hand/left/input/aim/pose"));
            oculusList.add(new BindingEntry(gripPoseAction, "/user/hand/right/input/grip/pose"));
            oculusList.add(new BindingEntry(gripPoseAction, "/user/hand/left/input/grip/pose"));
            oculusList.add(new BindingEntry(triggerAction, "/user/hand/right/input/trigger/value"));
            oculusList.add(new BindingEntry(triggerAction, "/user/hand/left/input/trigger/value"));
            oculusList.add(new BindingEntry(gripAction, "/user/hand/right/input/squeeze/value"));
            oculusList.add(new BindingEntry(gripAction, "/user/hand/left/input/squeeze/value"));
            oculusList.add(new BindingEntry(stickAction, "/user/hand/right/input/thumbstick"));
            oculusList.add(new BindingEntry(stickAction, "/user/hand/left/input/thumbstick"));
            oculusList.add(new BindingEntry(stickClickAction, "/user/hand/right/input/thumbstick/click"));
            oculusList.add(new BindingEntry(stickClickAction, "/user/hand/left/input/thumbstick/click"));
            oculusList.add(new BindingEntry(primaryAction, "/user/hand/right/input/a/click"));
            oculusList.add(new BindingEntry(primaryAction, "/user/hand/left/input/x/click"));
            oculusList.add(new BindingEntry(secondaryAction, "/user/hand/right/input/b/click"));
            oculusList.add(new BindingEntry(secondaryAction, "/user/hand/left/input/y/click"));
            oculusList.add(new BindingEntry(menuAction, "/user/hand/left/input/menu/click"));
            oculusList.add(new BindingEntry(hapticAction, "/user/hand/right/output/haptic"));
            oculusList.add(new BindingEntry(hapticAction, "/user/hand/left/output/haptic"));
            suggestBindings(instance, "/interaction_profiles/oculus/touch_controller", oculusList, stack);
            suggestBindings(instance, "/interaction_profiles/meta/touch_controller_plus", oculusList, stack);
            suggestBindings(instance, "/interaction_profiles/meta/touch_controller_pro", oculusList, stack);

            // 4. Suggested Bindings for Valve Index
            List<BindingEntry> indexList = new ArrayList<>();
            indexList.add(new BindingEntry(aimAction, "/user/hand/right/input/aim/pose"));
            indexList.add(new BindingEntry(aimAction, "/user/hand/left/input/aim/pose"));
            indexList.add(new BindingEntry(gripPoseAction, "/user/hand/right/input/grip/pose"));
            indexList.add(new BindingEntry(gripPoseAction, "/user/hand/left/input/grip/pose"));
            indexList.add(new BindingEntry(triggerAction, "/user/hand/right/input/trigger/value"));
            indexList.add(new BindingEntry(triggerAction, "/user/hand/left/input/trigger/value"));
            indexList.add(new BindingEntry(gripAction, "/user/hand/right/input/squeeze/value"));
            indexList.add(new BindingEntry(gripAction, "/user/hand/left/input/squeeze/value"));
            indexList.add(new BindingEntry(stickAction, "/user/hand/right/input/thumbstick"));
            indexList.add(new BindingEntry(stickAction, "/user/hand/left/input/thumbstick"));
            indexList.add(new BindingEntry(stickClickAction, "/user/hand/right/input/thumbstick/click"));
            indexList.add(new BindingEntry(stickClickAction, "/user/hand/left/input/thumbstick/click"));
            indexList.add(new BindingEntry(primaryAction, "/user/hand/right/input/a/click"));
            indexList.add(new BindingEntry(primaryAction, "/user/hand/left/input/a/click"));
            indexList.add(new BindingEntry(secondaryAction, "/user/hand/right/input/b/click"));
            indexList.add(new BindingEntry(secondaryAction, "/user/hand/left/input/b/click"));
            indexList.add(new BindingEntry(hapticAction, "/user/hand/right/output/haptic"));
            indexList.add(new BindingEntry(hapticAction, "/user/hand/left/output/haptic"));
            suggestBindings(instance, "/interaction_profiles/valve/index_controller", indexList, stack);

            // 5. Attach Action Set to Session
            XrSessionActionSetsAttachInfo attachInfo = XrSessionActionSetsAttachInfo.calloc(stack)
                    .type(XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO)
                    .actionSets(stack.pointers(actionSet));
            int attachRes = xrAttachSessionActionSets(session, attachInfo);
            if (attachRes != XR_SUCCESS) {
                GTNHVRLog.warn("Failed to attach OpenXR action sets: error %d", attachRes);
                return;
            }

            // 6. Create Action Spaces for Aim and Grip Poses
            XrActionSpaceCreateInfo spaceInfo = XrActionSpaceCreateInfo.calloc(stack)
                    .type(XR_TYPE_ACTION_SPACE_CREATE_INFO)
                    .action(aimAction)
                    .subactionPath(rightPath);
            spaceInfo.poseInActionSpace().orientation().w(1.0F);
            PointerBuffer pSpace = stack.mallocPointer(1);
            if (xrCreateActionSpace(session, spaceInfo, pSpace) == XR_SUCCESS) {
                rightAimSpace = new XrSpace(pSpace.get(0), session);
            }

            spaceInfo.subactionPath(leftPath);
            pSpace.clear();
            if (xrCreateActionSpace(session, spaceInfo, pSpace) == XR_SUCCESS) {
                leftAimSpace = new XrSpace(pSpace.get(0), session);
            }

            spaceInfo.action(gripPoseAction);
            spaceInfo.subactionPath(rightPath);
            pSpace.clear();
            if (xrCreateActionSpace(session, spaceInfo, pSpace) == XR_SUCCESS) {
                rightGripSpace = new XrSpace(pSpace.get(0), session);
            }

            spaceInfo.subactionPath(leftPath);
            pSpace.clear();
            if (xrCreateActionSpace(session, spaceInfo, pSpace) == XR_SUCCESS) {
                leftGripSpace = new XrSpace(pSpace.get(0), session);
            }

            initialized = true;
            GTNHVRLog.info("OpenXR Controller Handler initialized successfully with full 1:1 Visor control scheme");

        } catch (Throwable t) {
            GTNHVRLog.warn("Error setting up OpenXR controllers: %s", t);
        }
    }

    private static void suggestBindings(XrInstance instance, String profilePathStr, List<BindingEntry> entries, MemoryStack stack) {
        if (entries.isEmpty()) return;
        long profilePath = stringToPath(instance, profilePathStr, stack);
        XrActionSuggestedBinding.Buffer bindings = XrActionSuggestedBinding.calloc(entries.size(), stack);
        for (int i = 0; i < entries.size(); i++) {
            BindingEntry e = entries.get(i);
            bindings.get(i).action(e.action).binding(stringToPath(instance, e.path, stack));
        }
        XrInteractionProfileSuggestedBinding suggested = XrInteractionProfileSuggestedBinding.calloc(stack)
                .type(XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING)
                .interactionProfile(profilePath)
                .suggestedBindings(bindings);
        int res = xrSuggestInteractionProfileBindings(instance, suggested);
        if (res == XR_SUCCESS) {
            GTNHVRLog.info("OpenXR: Suggested %d bindings for %s", entries.size(), profilePathStr);
        } else {
            GTNHVRLog.warn("OpenXR: Failed to suggest bindings for %s (error %d)", profilePathStr, res);
        }
    }

    private static XrAction createAction(XrActionSet set, String name, int type, String locName, LongBuffer subactions, MemoryStack stack) {
        XrActionCreateInfo info = XrActionCreateInfo.calloc(stack)
                .type(XR_TYPE_ACTION_CREATE_INFO)
                .actionName(stack.UTF8(name))
                .actionType(type)
                .localizedActionName(stack.UTF8(locName))
                .subactionPaths(subactions);
        PointerBuffer p = stack.mallocPointer(1);
        if (xrCreateAction(set, info, p) == XR_SUCCESS) {
            return new XrAction(p.get(0), set);
        }
        return null;
    }

    private static long stringToPath(XrInstance inst, String str, MemoryStack stack) {
        LongBuffer p = stack.mallocLong(1);
        xrStringToPath(inst, stack.UTF8(str), p);
        return p.get(0);
    }

    public void update(XrSession session, XrSpace localSpace, long displayTime) {
        if (!initialized || session == null || localSpace == null) return;

        try (MemoryStack stack = stackPush()) {
            // 1. Sync Action Sets FIRST (Must succeed before action spaces can be safely located)
            XrActiveActionSet.Buffer active = XrActiveActionSet.calloc(1, stack)
                    .actionSet(actionSet)
                    .subactionPath(0L);
            XrActionsSyncInfo sync = XrActionsSyncInfo.calloc(stack)
                    .type(XR_TYPE_ACTIONS_SYNC_INFO)
                    .activeActionSets(active);
            int syncRes = xrSyncActions(session, sync);
            if (syncRes < 0) {
                GTNHVRLog.throttled("OpenXR xrSyncActions failed with code %d", syncRes);
                return;
            }

            // 2. Locate Controller Spaces ONLY after successful action sync
            if (displayTime > 0) {
                locateSpaces(localSpace, displayTime, stack);
            }

            // 3. Poll Input Actions
            rightTriggerValue = getFloatState(session, triggerAction, rightSubactionPath, stack);
            leftTriggerValue = getFloatState(session, triggerAction, leftSubactionPath, stack);

            rightGripValue = getFloatState(session, gripAction, rightSubactionPath, stack);
            leftGripValue = getFloatState(session, gripAction, leftSubactionPath, stack);

            getVec2State(session, stickAction, rightSubactionPath, TEMP_VEC2, stack);
            rightStickX = TEMP_VEC2.x;
            rightStickY = TEMP_VEC2.y;

            getVec2State(session, stickAction, leftSubactionPath, TEMP_VEC2, stack);
            leftStickX = TEMP_VEC2.x;
            leftStickY = TEMP_VEC2.y;

            rightStickClick = getBoolState(session, stickClickAction, rightSubactionPath, stack);
            leftStickClick = getBoolState(session, stickClickAction, leftSubactionPath, stack);

            rightPrimaryValue = getBoolState(session, primaryAction, rightSubactionPath, stack);     // A
            leftPrimaryValue = getBoolState(session, primaryAction, leftSubactionPath, stack);       // X

            rightSecondaryValue = getBoolState(session, secondaryAction, rightSubactionPath, stack); // B
            leftSecondaryValue = getBoolState(session, secondaryAction, leftSubactionPath, stack);   // Y

            leftMenuValue = getBoolState(session, menuAction, leftSubactionPath, stack);

            applyHandedness();
            handleInteraction();

        } catch (Throwable t) {
            GTNHVRLog.warn("Controller update error: %s", t);
        }
    }

    /**
     * Left-handed mode: exchanges the logical main hand (right) and off hand (left) so every
     * consumer of this handler (interaction, hotbar, hands renderer, aim picker, movement) keeps
     * treating "right" as the dominant hand. The menu button stays on the physical left controller.
     */
    private void applyHandedness() {
        if (!VRClientSettings.leftHanded) return;

        Vector3f tmpPos = new Vector3f(rightHandPos);
        rightHandPos.set(leftHandPos);
        leftHandPos.set(tmpPos);

        Quaternionf tmpRot = new Quaternionf(rightHandRot);
        rightHandRot.set(leftHandRot);
        leftHandRot.set(tmpRot);

        boolean tmpTracked = rightHandTracked;
        rightHandTracked = leftHandTracked;
        leftHandTracked = tmpTracked;

        float tmpF = rightTriggerValue;
        rightTriggerValue = leftTriggerValue;
        leftTriggerValue = tmpF;

        tmpF = rightGripValue;
        rightGripValue = leftGripValue;
        leftGripValue = tmpF;

        tmpF = rightStickX;
        rightStickX = leftStickX;
        leftStickX = tmpF;

        tmpF = rightStickY;
        rightStickY = leftStickY;
        leftStickY = tmpF;

        boolean tmpB = rightStickClick;
        rightStickClick = leftStickClick;
        leftStickClick = tmpB;

        tmpB = rightPrimaryValue;
        rightPrimaryValue = leftPrimaryValue;
        leftPrimaryValue = tmpB;

        tmpB = rightSecondaryValue;
        rightSecondaryValue = leftSecondaryValue;
        leftSecondaryValue = tmpB;
    }

    private void locateSpaces(XrSpace localSpace, long displayTime, MemoryStack stack) {
        if (localSpace == null || displayTime <= 0) return;
        XrSpaceLocation loc = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION);

        // Right Hand (Prefer Aim Space for laser pointing, Fallback to Grip Space)
        boolean prevRightTracked = rightHandTracked;
        boolean rightFound = false;
        try {
            if (rightAimSpace != null && xrLocateSpace(rightAimSpace, localSpace, displayTime, loc) == XR_SUCCESS) {
                long flags = loc.locationFlags();
                if ((flags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                    XrPosef p = loc.pose();
                    rightHandPos.set(p.position$().x(), p.position$().y(), p.position$().z());
                    if ((flags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
                        rightHandRot.set(p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w());
                    }
                    rightFound = true;
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.throttled("Error locating rightAimSpace: %s", t);
        }

        if (!rightFound && rightGripSpace != null) {
            try {
                loc.type(XR_TYPE_SPACE_LOCATION);
                if (xrLocateSpace(rightGripSpace, localSpace, displayTime, loc) == XR_SUCCESS) {
                    long flags = loc.locationFlags();
                    if ((flags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                        XrPosef p = loc.pose();
                        rightHandPos.set(p.position$().x(), p.position$().y(), p.position$().z());
                        if ((flags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
                            rightHandRot.set(p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w());
                        }
                        rightFound = true;
                    }
                }
            } catch (Throwable t) {
                GTNHVRLog.throttled("Error locating rightGripSpace: %s", t);
            }
        }
        rightHandTracked = rightFound;
        if (rightHandTracked != prevRightTracked) {
            GTNHVRLog.info("Right controller tracking %s (pos=%.2f, %.2f, %.2f)",
                    rightHandTracked ? "ACQUIRED" : "LOST", rightHandPos.x, rightHandPos.y, rightHandPos.z);
        }

        // Left Hand (Prefer Aim Space, Fallback to Grip Space)
        boolean prevLeftTracked = leftHandTracked;
        boolean leftFound = false;
        try {
            loc.type(XR_TYPE_SPACE_LOCATION);
            if (leftAimSpace != null && xrLocateSpace(leftAimSpace, localSpace, displayTime, loc) == XR_SUCCESS) {
                long flags = loc.locationFlags();
                if ((flags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                    XrPosef p = loc.pose();
                    leftHandPos.set(p.position$().x(), p.position$().y(), p.position$().z());
                    if ((flags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
                        leftHandRot.set(p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w());
                    }
                    leftFound = true;
                }
            }
        } catch (Throwable t) {
            GTNHVRLog.throttled("Error locating leftAimSpace: %s", t);
        }

        if (!leftFound && leftGripSpace != null) {
            try {
                loc.type(XR_TYPE_SPACE_LOCATION);
                if (xrLocateSpace(leftGripSpace, localSpace, displayTime, loc) == XR_SUCCESS) {
                    long flags = loc.locationFlags();
                    if ((flags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                        XrPosef p = loc.pose();
                        leftHandPos.set(p.position$().x(), p.position$().y(), p.position$().z());
                        if ((flags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
                            leftHandRot.set(p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w());
                        }
                        leftFound = true;
                    }
                }
            } catch (Throwable t) {
                GTNHVRLog.throttled("Error locating leftGripSpace: %s", t);
            }
        }
        leftHandTracked = leftFound;
        if (leftHandTracked != prevLeftTracked) {
            GTNHVRLog.info("Left controller tracking %s (pos=%.2f, %.2f, %.2f)",
                    leftHandTracked ? "ACQUIRED" : "LOST", leftHandPos.x, leftHandPos.y, leftHandPos.z);
        }

        // Independently locate the physical grip origins for reliable shoulder-distance detection.
        rightGripHandTracked = locateGripPosition(rightGripSpace, localSpace, displayTime, loc, rightGripHandPos);
        leftGripHandTracked = locateGripPosition(leftGripSpace, localSpace, displayTime, loc, leftGripHandPos);
    }

    private boolean locateGripPosition(XrSpace gripSpace, XrSpace localSpace, long displayTime,
                                        XrSpaceLocation location, Vector3f outPosition) {
        if (gripSpace == null || localSpace == null || displayTime <= 0) return false;
        try {
            location.type(XR_TYPE_SPACE_LOCATION);
            if (xrLocateSpace(gripSpace, localSpace, displayTime, location) != XR_SUCCESS) return false;
            long flags = location.locationFlags();
            if ((flags & XR_SPACE_LOCATION_POSITION_VALID_BIT) == 0) return false;
            XrPosef pose = location.pose();
            outPosition.set(pose.position$().x(), pose.position$().y(), pose.position$().z());
            return true;
        } catch (Throwable t) {
            GTNHVRLog.throttled("Error locating controller grip pose: %s", t);
            return false;
        }
    }

    public synchronized void destroy() {
        if (!initialized) return;
        try {
            if (rightAimSpace != null) { xrDestroySpace(rightAimSpace); rightAimSpace = null; }
            if (leftAimSpace != null) { xrDestroySpace(leftAimSpace); leftAimSpace = null; }
            if (rightGripSpace != null) { xrDestroySpace(rightGripSpace); rightGripSpace = null; }
            if (leftGripSpace != null) { xrDestroySpace(leftGripSpace); leftGripSpace = null; }
            if (actionSet != null) { xrDestroyActionSet(actionSet); actionSet = null; }
        } catch (Throwable ignored) {}
        aimAction = null; gripPoseAction = null; triggerAction = null; gripAction = null;
        stickAction = null; stickClickAction = null; primaryAction = null; secondaryAction = null; menuAction = null;
        rightHandTracked = false; leftHandTracked = false;
        rightGripHandTracked = false; leftGripHandTracked = false;
        session = null;
        initialized = false;
        GTNHVRLog.info("VRControllerHandler destroyed and spaces cleaned up");
    }

    private static float getFloatState(XrSession session, XrAction action, long subactionPath, MemoryStack stack) {
        if (action == null) return 0.0F;
        XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR_TYPE_ACTION_STATE_GET_INFO)
                .action(action)
                .subactionPath(subactionPath);
        XrActionStateFloat state = XrActionStateFloat.calloc(stack).type(XR_TYPE_ACTION_STATE_FLOAT);
        if (xrGetActionStateFloat(session, info, state) == XR_SUCCESS && state.isActive()) {
            return state.currentState();
        }
        return 0.0F;
    }

    private static boolean getBoolState(XrSession session, XrAction action, long subactionPath, MemoryStack stack) {
        if (action == null) return false;
        XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR_TYPE_ACTION_STATE_GET_INFO)
                .action(action)
                .subactionPath(subactionPath);
        XrActionStateBoolean state = XrActionStateBoolean.calloc(stack).type(XR_TYPE_ACTION_STATE_BOOLEAN);
        if (xrGetActionStateBoolean(session, info, state) == XR_SUCCESS && state.isActive()) {
            return state.currentState();
        }
        return false;
    }

    private static void getVec2State(XrSession session, XrAction action, long subactionPath, Vector2f out, MemoryStack stack) {
        out.set(0.0F, 0.0F);
        if (action == null) return;
        XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack)
                .type(XR_TYPE_ACTION_STATE_GET_INFO)
                .action(action)
                .subactionPath(subactionPath);
        XrActionStateVector2f state = XrActionStateVector2f.calloc(stack).type(XR_TYPE_ACTION_STATE_VECTOR2F);
        if (xrGetActionStateVector2f(session, info, state) == XR_SUCCESS && state.isActive()) {
            out.set(state.currentState().x(), state.currentState().y());
        }
    }

    private void handleInteraction() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        // Synthetic shoulder-key releases must continue while Minecraft is in menus too.
        com.gtnhvr.gameplay.VRShoulderGestures.getInstance().tick();

        if (mc.theWorld != lastWorld) {
            // A world transition invalidates the previous press edge and any trigger latch.
            inGameAttackHeld = false;
            rightAttackTriggerHeld = false;
            inGameUseItemHeld = false;
            lastWorld = mc.theWorld;
            if (mc.theWorld != null) {
                worldLoadGraceTicks = 40; // ~2 seconds grace period on loading into world
                pauseClicked = true;
                inventoryClicked = true;
                backClicked = true;
            }
        }

        boolean inMenu = (mc.theWorld == null || mc.currentScreen != null);
        if (inMenu != wasInMenu) {
            wasInMenu = inMenu;
            if (inMenu) {
                // releaseAllInGameKeys clears Minecraft's pressed bit; reset our edge trackers too,
                // or the next trigger press may be mistaken for an already-held attack.
                inGameAttackHeld = false;
                rightAttackTriggerHeld = false;
                inGameUseItemHeld = false;
                releaseAllInGameKeys(mc);
                if (VRHotbarSelector.getInstance().isSelectorActive()) {
                    VRHotbarSelector.getInstance().closeAll();
                }
                clearAllGameplayTasks(mc);
            } else {
                releaseMenuClicks();
                pauseClicked = true;
                inventoryClicked = true;
                backClicked = true;
            }
        }

        if (inMenu) {
            handleMenuInteraction(mc);
        } else {
            handleInWorldInteraction(mc);
        }
    }

    private void releaseMenuClicks() {
        setMouseButtonState(0, false);
        setMouseButtonState(1, false);
        try {
            org.lwjglx.input.Mouse.sdlMouseButtonFlags = 0;
        } catch (Throwable ignored) {}
        if (menuLeftClicked) {
            menuLeftClicked = false;
            try {
                org.lwjglx.input.Mouse.addButtonEvent(0, false);
            } catch (Throwable ignored) {}
        }
        if (menuRightClicked) {
            menuRightClicked = false;
            try {
                org.lwjglx.input.Mouse.addButtonEvent(1, false);
            } catch (Throwable ignored) {}
        }
    }

    public static void releaseAllInGameKeys(Minecraft mc) {
        if (mc == null || mc.gameSettings == null) return;
        INSTANCE.releaseAllMappedControllerKeys();
        try {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        } catch (Throwable ignored) {}
    }

    /**
     * True while any remapped controller control is emitting the supplied keyboard key.
     * This allows remaps to drive vanilla actions too (jump, attack, movement, etc.).
     */
    public boolean isControllerMappedKeyHeld(int keyCode) {
        if (keyCode <= 0) return false;
        for (int i = 0; i < mappedControllerKeyHeld.length; i++) {
            if (mappedControllerKeyHeld[i] && mappedControllerKeyCodes[i] == keyCode) return true;
        }
        return false;
    }

    private boolean isMappedControllerKeyHeld(int keyCode) {
        if (keyCode <= 0) return false;
        return isControllerMappedKeyHeld(keyCode)
                // Shoulder gestures are another source of synthetic key presses; treat them as
                // part of the same aggregate state so the gameplay loop cannot release their key.
                || com.gtnhvr.gameplay.VRShoulderGestures.getInstance().isSyntheticKeyHeld(keyCode);
    }

    private boolean isMappedControllerKeyHeldByOther(int index, int keyCode) {
        if (keyCode <= 0) return false;
        for (int i = 0; i < mappedControllerKeyHeld.length; i++) {
            if (i != index && mappedControllerKeyHeld[i] && mappedControllerKeyCodes[i] == keyCode) return true;
        }
        return false;
    }

    private void updateMappedControllerKey(int index, int keyCode, boolean pressed) {
        if (index < 0 || index >= MAPPED_CONTROLLER_KEY_COUNT) return;
        int oldCode = mappedControllerKeyCodes[index];
        boolean wasHeld = mappedControllerKeyHeld[index];
        boolean nowHeld = pressed && keyCode > 0;

        if (wasHeld && (oldCode != keyCode || !nowHeld)
                && !isMappedControllerKeyHeldByOther(index, oldCode)
                && !com.gtnhvr.gameplay.VRShoulderGestures.getInstance().isSyntheticKeyHeld(oldCode)) {
            KeyBinding.setKeyBindState(oldCode, false);
        }
        if (nowHeld) {
            boolean newPress = !wasHeld || oldCode != keyCode;
            if (newPress && !isMappedControllerKeyHeldByOther(index, keyCode)
                    && !com.gtnhvr.gameplay.VRShoulderGestures.getInstance().isSyntheticKeyHeld(keyCode)) {
                KeyBinding.onTick(keyCode);
            }
            KeyBinding.setKeyBindState(keyCode, true);
        }

        mappedControllerKeyCodes[index] = keyCode;
        mappedControllerKeyHeld[index] = nowHeld;
    }

    private void releaseAllMappedControllerKeys() {
        for (int i = 0; i < MAPPED_CONTROLLER_KEY_COUNT; i++) {
            if (mappedControllerKeyHeld[i] && mappedControllerKeyCodes[i] > 0
                    && !com.gtnhvr.gameplay.VRShoulderGestures.getInstance().isSyntheticKeyHeld(mappedControllerKeyCodes[i])) {
                KeyBinding.setKeyBindState(mappedControllerKeyCodes[i], false);
            }
            mappedControllerKeyHeld[i] = false;
            mappedAnalogLatch[i] = false;
        }
        rightAttackTriggerHeld = false;
        leftBindingTriggerHeld = false;
    }

    private boolean updateAnalogLatch(int index, float value, boolean positive, float pressThreshold, float releaseThreshold) {
        if (index < 0 || index >= MAPPED_CONTROLLER_KEY_COUNT) return false;
        if (positive) {
            if (value >= pressThreshold) mappedAnalogLatch[index] = true;
            else if (value <= releaseThreshold) mappedAnalogLatch[index] = false;
        } else {
            if (value <= -pressThreshold) mappedAnalogLatch[index] = true;
            else if (value >= -releaseThreshold) mappedAnalogLatch[index] = false;
        }
        return mappedAnalogLatch[index];
    }

    private void updateMappedControllerKeys() {
        // Hysteresis on analog triggers, grips and stick directions keeps key output stable.
        if (rightTriggerValue >= 0.45F) rightAttackTriggerHeld = true;
        else if (rightTriggerValue <= 0.25F) rightAttackTriggerHeld = false;
        if (leftTriggerValue >= 0.45F) leftBindingTriggerHeld = true;
        else if (leftTriggerValue <= 0.25F) leftBindingTriggerHeld = false;

        boolean selectorActive = VRHotbarSelector.getInstance().isSelectorActive();
        updateMappedControllerKey(MAP_RIGHT_TRIGGER, VRClientSettings.controllerRightTriggerKeyCode, rightAttackTriggerHeld);
        updateMappedControllerKey(MAP_LEFT_TRIGGER, VRClientSettings.controllerLeftTriggerKeyCode, leftBindingTriggerHeld);
        updateMappedControllerKey(MAP_RIGHT_A, VRClientSettings.controllerRightAKeyCode, rightPrimaryValue);
        updateMappedControllerKey(MAP_RIGHT_B, VRClientSettings.controllerRightBKeyCode, rightSecondaryValue);
        updateMappedControllerKey(MAP_LEFT_X, VRClientSettings.controllerLeftXKeyCode, leftPrimaryValue);
        updateMappedControllerKey(MAP_LEFT_Y, VRClientSettings.controllerLeftYKeyCode, leftSecondaryValue);
        updateMappedControllerKey(MAP_LEFT_MENU, VRClientSettings.controllerLeftMenuKeyCode, leftMenuValue);
        updateMappedControllerKey(MAP_RIGHT_STICK_CLICK, VRClientSettings.controllerRightStickClickKeyCode, rightStickClick);
        updateMappedControllerKey(MAP_LEFT_STICK_CLICK, VRClientSettings.controllerLeftStickClickKeyCode, leftStickClick);
        updateMappedControllerKey(MAP_RIGHT_GRIP, VRClientSettings.controllerRightGripKeyCode,
                updateAnalogLatch(MAP_RIGHT_GRIP, rightGripValue, true, 0.45F, 0.25F));
        updateMappedControllerKey(MAP_LEFT_GRIP, VRClientSettings.controllerLeftGripKeyCode,
                updateAnalogLatch(MAP_LEFT_GRIP, leftGripValue, true, 0.45F, 0.25F));

        updateMappedControllerKey(MAP_LEFT_STICK_UP, VRClientSettings.controllerLeftStickUpKeyCode,
                updateAnalogLatch(MAP_LEFT_STICK_UP, leftStickY, true, 0.60F, 0.40F));
        updateMappedControllerKey(MAP_LEFT_STICK_DOWN, VRClientSettings.controllerLeftStickDownKeyCode,
                updateAnalogLatch(MAP_LEFT_STICK_DOWN, leftStickY, false, 0.60F, 0.40F));
        updateMappedControllerKey(MAP_LEFT_STICK_LEFT, VRClientSettings.controllerLeftStickLeftKeyCode,
                updateAnalogLatch(MAP_LEFT_STICK_LEFT, leftStickX, false, 0.60F, 0.40F));
        updateMappedControllerKey(MAP_LEFT_STICK_RIGHT, VRClientSettings.controllerLeftStickRightKeyCode,
                updateAnalogLatch(MAP_LEFT_STICK_RIGHT, leftStickX, true, 0.60F, 0.40F));
        boolean rightStickUpMapped = updateAnalogLatch(MAP_RIGHT_STICK_UP, rightStickY, true, 0.60F, 0.40F);
        boolean rightStickDownMapped = updateAnalogLatch(MAP_RIGHT_STICK_DOWN, rightStickY, false, 0.60F, 0.40F);
        boolean rightStickLeftMapped = updateAnalogLatch(MAP_RIGHT_STICK_LEFT, rightStickX, false, 0.60F, 0.40F);
        boolean rightStickRightMapped = updateAnalogLatch(MAP_RIGHT_STICK_RIGHT, rightStickX, true, 0.60F, 0.40F);
        updateMappedControllerKey(MAP_RIGHT_STICK_UP, VRClientSettings.controllerRightStickUpKeyCode,
                !selectorActive && rightStickUpMapped);
        updateMappedControllerKey(MAP_RIGHT_STICK_DOWN, VRClientSettings.controllerRightStickDownKeyCode,
                !selectorActive && rightStickDownMapped);
        updateMappedControllerKey(MAP_RIGHT_STICK_LEFT, VRClientSettings.controllerRightStickLeftKeyCode,
                !selectorActive && rightStickLeftMapped);
        updateMappedControllerKey(MAP_RIGHT_STICK_RIGHT, VRClientSettings.controllerRightStickRightKeyCode,
                !selectorActive && rightStickRightMapped);
    }

    private void handleMenuInteraction(Minecraft mc) {
        // Back / Close / Escape (Right B, Left Y, or Left Menu button)
        boolean backPressed = rightSecondaryValue || leftSecondaryValue || leftMenuValue;
        if (backPressed && !backClicked) {
            backClicked = true;
            if (mc.currentScreen != null) {
                if (mc.theWorld != null) {
                    // Close in-game GUI and resume game immediately
                    mc.displayGuiScreen(null);
                    mc.setIngameFocus();
                    return;
                } else if (mc.currentScreen instanceof com.gtnhvr.gui.VRSettingsScreen vrSettings) {
                    net.minecraft.client.gui.GuiScreen parent = vrSettings.getParentScreen();
                    if (parent instanceof com.gtnhvr.gui.VRSettingsScreen) parent = null;
                    mc.displayGuiScreen(parent);
                    return;
                }
            }
        } else if (!backPressed) {
            backClicked = false;
        }

        VRVirtualScreen screen = VRVirtualScreen.getInstance();
        VRVirtualKeyboard keyboard = VRVirtualKeyboard.getInstance();

        // Menu Thumbstick Scrolling (Direct & Universal)
        float stickScrollY = (Math.abs(rightStickY) > 0.15F) ? rightStickY : (Math.abs(leftStickY) > 0.15F ? leftStickY : 0.0F);
        if (mc.currentScreen != null) {
            if (mc.currentScreen instanceof com.gtnhvr.gui.VRSettingsScreen vrSettings) {
                int mouseX = screen.isCursorActive() ? (int) (screen.getCursorU() * vrSettings.width) : vrSettings.width / 2;
                int mouseY = screen.isCursorActive() ? (int) ((1.0F - screen.getCursorV()) * vrSettings.height) : vrSettings.height / 2;
                vrSettings.handleStickScroll(stickScrollY, mouseX, mouseY);
            } else if (mc.currentScreen instanceof com.gtnhvr.gui.GuiSettingsOverlays overlaySettings) {
                int mouseX = screen.isCursorActive() ? (int) (screen.getCursorU() * overlaySettings.width) : overlaySettings.width / 2;
                int mouseY = screen.isCursorActive() ? (int) ((1.0F - screen.getCursorV()) * overlaySettings.height) : overlaySettings.height / 2;
                overlaySettings.handleStickScroll(stickScrollY, mouseX, mouseY);
            } else if (stickScrollY != 0.0F) {
                // Rate-limited wheel events for vanilla / modded screens (GuiSlot, Creative inv, NEI, etc.)
                long now = System.currentTimeMillis();
                if (now - lastMenuWheelTime >= 70L) {
                    lastMenuWheelTime = now;
                    int wheelDelta = (stickScrollY > 0) ? 1 : -1;
                    try {
                        org.lwjglx.input.Mouse.addWheelEvent((double) wheelDelta);
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 1. Ongoing Dragging / Resizing for Virtual Screen
        if (screen.isDragging() || screen.isResizing()) {
            float trigger = screenDragHandIsLeft ? leftTriggerValue : rightTriggerValue;
            float grip = screenDragHandIsLeft ? leftGripValue : rightGripValue;
            if (trigger < 0.25F && grip < 0.25F) {
                if (screen.isDragging()) screen.stopDragging();
                if (screen.isResizing()) screen.stopResizing();
                releaseMenuClicks();
            } else {
                Vector3f handPos = screenDragHandIsLeft ? leftHandPos : rightHandPos;
                Quaternionf handRot = screenDragHandIsLeft ? leftHandRot : rightHandRot;
                if (screen.isDragging()) {
                    screen.updateDragging(handPos, handRot);
                    screen.setLastHitPoint(screen.getDragBarWorldPos());
                } else {
                    screen.updateResizing(handPos);
                    screen.setLastHitPoint(screen.getResizeHandleWorldPos());
                }
                releaseMenuClicks();
                return;
            }
        }

        // 2. Ongoing Dragging / Resizing for Virtual Keyboard
        if (keyboard.isDragging() || keyboard.isResizing()) {
            float trigger = kbDragHandIsLeft ? leftTriggerValue : rightTriggerValue;
            float grip = kbDragHandIsLeft ? leftGripValue : rightGripValue;
            if (trigger < 0.25F && grip < 0.25F) {
                if (keyboard.isDragging()) keyboard.stopDragging();
                if (keyboard.isResizing()) keyboard.stopResizing();
                releaseMenuClicks();
            } else {
                Vector3f handPos = kbDragHandIsLeft ? leftHandPos : rightHandPos;
                Quaternionf handRot = kbDragHandIsLeft ? leftHandRot : rightHandRot;
                if (keyboard.isDragging()) {
                    keyboard.updateDragging(handPos, handRot);
                    keyboard.setLastHitPoint(keyboard.getDragBarWorldPos());
                } else {
                    keyboard.updateResizing(handPos);
                    keyboard.setLastHitPoint(keyboard.getResizeHandleWorldPos());
                }
                releaseMenuClicks();
                return;
            }
        }

        // 3. Auto-switch active cursor hand (locked to right hand when left hand holds GuiInventory)
        if (mc != null && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory) {
            isLeftCursorHand = false;
        } else {
            boolean leftClickAttempt = (leftTriggerValue > 0.40F || leftPrimaryValue || leftGripValue > 0.40F);
            boolean rightClickAttempt = (rightTriggerValue > 0.40F || rightPrimaryValue || rightGripValue > 0.40F);

            if (leftClickAttempt && !rightClickAttempt && leftHandTracked) {
                isLeftCursorHand = true;
            } else if (rightClickAttempt && !leftClickAttempt && rightHandTracked) {
                isLeftCursorHand = false;
            }
        }

        boolean activeTracked = isLeftCursorHand ? leftHandTracked : rightHandTracked;
        if (!activeTracked) {
            screen.setCursorUV(-1.0F, -1.0F, false);
            releaseMenuClicks();
            return;
        }

        Vector3f activeHandPos = isLeftCursorHand ? leftHandPos : rightHandPos;
        Quaternionf activeHandRot = isLeftCursorHand ? leftHandRot : rightHandRot;
        Vector3f activeRayDir = activeHandRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        float activeTrigger = isLeftCursorHand ? leftTriggerValue : rightTriggerValue;
        float activeGrip = isLeftCursorHand ? leftGripValue : rightGripValue;
        boolean activePrimary = isLeftCursorHand ? leftPrimaryValue : rightPrimaryValue;

        // 4. Update 3D Virtual Keyboard interaction (typing, lasers, haptics, handles)
        keyboard.update(mc, this);

        // Check if cursor is interacting with Virtual Keyboard
        if (keyboard.isVisible() && keyboard.isHovered()) {
            screen.setCursorUV(-1.0F, -1.0F, false);
            releaseMenuClicks();
            return;
        }

        // 5. Virtual Screen raycast & interaction
        org.joml.Vector2f screenUV = new org.joml.Vector2f(-1.0F, -1.0F);
        Vector3f screenHitPoint = new Vector3f();
        boolean screenHit = screen.raycastCheck(activeHandPos, activeRayDir, screenUV, screenHitPoint);

        if (!screenHit) {
            screen.setCursorUV(-1.0F, -1.0F, false);
            if (activeTrigger < 0.25F && activeGrip < 0.25F && !activePrimary) {
                releaseMenuClicks();
            }
            return;
        }

        VRVirtualScreen.ScreenHandle scrHandle = screen.getHoveredHandle();
        if (scrHandle == VRVirtualScreen.ScreenHandle.DRAG_BAR) {
            screen.setCursorUV(-1.0F, -1.0F, false);
            screen.setLastHitPoint(screenHitPoint);
            releaseMenuClicks();
            if ((activeTrigger > 0.40F || activeGrip > 0.40F) && !screen.isDragging()) {
                screen.startDragging(activeHandPos, activeHandRot);
                screenDragHandIsLeft = isLeftCursorHand;
                screen.setLastHitPoint(screen.getDragBarWorldPos());
                triggerHaptic(isLeftCursorHand, 8_000_000L, 0.65F, 0);
            }
            return;
        } else if (scrHandle == VRVirtualScreen.ScreenHandle.RESIZE_HANDLE) {
            screen.setCursorUV(-1.0F, -1.0F, false);
            screen.setLastHitPoint(screenHitPoint);
            releaseMenuClicks();
            if ((activeTrigger > 0.40F || activeGrip > 0.40F) && !screen.isResizing()) {
                screen.startResizing(activeHandPos);
                screenDragHandIsLeft = isLeftCursorHand;
                screen.setLastHitPoint(screen.getResizeHandleWorldPos());
                triggerHaptic(isLeftCursorHand, 8_000_000L, 0.65F, 0);
            }
            return;
        } else if (scrHandle == VRVirtualScreen.ScreenHandle.SCREEN) {
            screen.setCursorUV(screenUV.x, screenUV.y, true);
            screen.setLastHitPoint(screenHitPoint);

            // Convert normalized screen UV directly to the window's mouse-event pixel
            // coordinates. GuiScreen.handleMouseInput then maps those pixels into its own
            // width/height. Do not apply ScaledResolution here: that would scale the pointer
            // a second time when the virtual GUI resolution differs from the desktop window.
            int cursorW = (mc != null && mc.displayWidth > 0) ? mc.displayWidth : 1;
            int cursorH = (mc != null && mc.displayHeight > 0) ? mc.displayHeight : 1;
            float curU = Math.max(0.0F, Math.min(1.0F, screenUV.x));
            float curV = Math.max(0.0F, Math.min(1.0F, screenUV.y));
            int screenX = Math.round(curU * cursorW);
            int glY = Math.round(curV * cursorH);
            updateLwjglMousePosition(screenX, glY);

            // Left Click from active hand: TRIGGER ONLY (hysteresis: squeeze > 0.45F, release < 0.25F)
            boolean isLeftClicking = (activeTrigger > 0.45F);
            if (isLeftClicking) {
                setMouseButtonState(0, true);
                if (!menuLeftClicked) {
                    menuLeftClicked = true;
                    try {
                        org.lwjglx.input.Mouse.addButtonEvent(0, true);
                    } catch (Throwable ignored) {}
                    triggerHaptic(isLeftCursorHand, 8_000_000L, 0.60F, 0);
                }
            } else if (activeTrigger < 0.25F) {
                setMouseButtonState(0, false);
                if (menuLeftClicked) {
                    menuLeftClicked = false;
                    try {
                        org.lwjglx.input.Mouse.addButtonEvent(0, false);
                    } catch (Throwable ignored) {}
                }
            }

            // Right Click from active hand: Button A (activePrimary) OR Side Grip (squeeze > 0.45F, release < 0.25F)
            boolean isRightClicking = activePrimary || (activeGrip > 0.45F);
            if (isRightClicking) {
                setMouseButtonState(1, true);
                if (!menuRightClicked) {
                    menuRightClicked = true;
                    try {
                        org.lwjglx.input.Mouse.addButtonEvent(1, true);
                    } catch (Throwable ignored) {}
                    triggerHaptic(isLeftCursorHand, 8_000_000L, 0.50F, 0);
                }
            } else if (!activePrimary && activeGrip < 0.25F) {
                setMouseButtonState(1, false);
                if (menuRightClicked) {
                    menuRightClicked = false;
                    try {
                        org.lwjglx.input.Mouse.addButtonEvent(1, false);
                    } catch (Throwable ignored) {}
                }
            }
        } else {
            if (activeTrigger < 0.25F && activeGrip < 0.25F && !activePrimary) {
                releaseMenuClicks();
            }
        }
    }

    private void handleInWorldInteraction(Minecraft mc) {
        releaseMenuClicks();
        updateMappedControllerKeys();

        // 0. Update Hand 6-DoF Raycasting for block mining and placement (Visor VRAimPicker)
        VRHandAimPicker.update(mc, 1.0F);

        // Run Visor Physical Gameplay Tasks
        com.gtnhvr.gameplay.tasks.VRTaskSwing.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskRoomConsume.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskRoomClimb.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskRoomJump.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().update(mc);
        com.gtnhvr.gameplay.tasks.VRTaskRoomSwim.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskVehicle.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskBoat.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskTeleport.getInstance().update(mc, this);
        com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance().update(mc, this);
        com.gtnhvr.gui.VRWristInventory.getInstance().update(mc, this);
        com.gtnhvr.gui.VRWristStatusOverlay.getInstance().update(mc, this);
        com.gtnhvr.gui.VRWristMapOverlay.getInstance().update(mc, this);
        com.gtnhvr.gui.VRWristInfoOverlay.getInstance().update(mc, this);
        com.gtnhvr.gui.VRWristPotionOverlay.getInstance().update(mc, this);
        com.gtnhvr.gui.VRMobSpawnOverlay.getInstance().update(mc);
        com.gtnhvr.gameplay.VRShoulderGestures.getInstance().update(mc, this);

        if (worldLoadGraceTicks > 0) {
            worldLoadGraceTicks--;
            pauseClicked = true;
            inventoryClicked = true;
            backClicked = true;
        }

        // 1. Pause Menu. A bound button emits its assigned key instead of its legacy action.
        boolean pausePressed = (leftSecondaryValue && VRClientSettings.controllerLeftYKeyCode <= 0)
                || (leftMenuValue && VRClientSettings.controllerLeftMenuKeyCode <= 0);
        if (pausePressed && !pauseClicked && worldLoadGraceTicks == 0) {
            pauseClicked = true;
            releaseAllInGameKeys(mc);
            if (VRHotbarSelector.getInstance().isSelectorActive()) {
                VRHotbarSelector.getInstance().closeAll();
            }
            clearAllGameplayTasks(mc);
            mc.displayGuiScreen(new com.gtnhvr.gui.VRPauseMenuScreen());
            return;
        } else if (!pausePressed && worldLoadGraceTicks == 0) {
            pauseClicked = false;
        }

        // 2. Open Inventory (Left Button X), unless it has been assigned another key.
        if (leftPrimaryValue && VRClientSettings.controllerLeftXKeyCode <= 0
                && !inventoryClicked && worldLoadGraceTicks == 0) {
            inventoryClicked = true;
            releaseAllInGameKeys(mc);
            if (VRHotbarSelector.getInstance().isSelectorActive()) {
                VRHotbarSelector.getInstance().closeAll();
            }
            clearAllGameplayTasks(mc);
            if (mc.thePlayer != null) {
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            }
            return;
        } else if ((!leftPrimaryValue || VRClientSettings.controllerLeftXKeyCode > 0) && worldLoadGraceTicks == 0) {
            inventoryClicked = false;
        }

        // 3. Radial hotbar selectors (Visor TaskHotBar): hold a side grip to open, release to confirm
        VRHotbarSelector hotbar = VRHotbarSelector.getInstance();

        org.lwjgl.openxr.XrPosef headPose = OpenXRRuntime.getHeadPose();
        Vector3f hmdPos = (headPose != null)
                ? new Vector3f(headPose.position$().x(), headPose.position$().y(), headPose.position$().z())
                : new Vector3f(0.0F, 1.62F, 0.0F);
        Quaternionf hmdRot = (headPose != null)
                ? new Quaternionf(headPose.orientation().x(), headPose.orientation().y(),
                        headPose.orientation().z(), headPose.orientation().w())
                : new Quaternionf();

        // Gripping a ladder or vine climbs; it must not open the hotbar at the same time.
        boolean climbing = com.gtnhvr.gameplay.tasks.VRTaskRoomClimb.getInstance().isClimbing();
        hotbar.update(mc, (climbing || VRClientSettings.controllerRightGripKeyCode > 0) ? 0.0F : rightGripValue,
                (climbing || VRClientSettings.controllerLeftGripKeyCode > 0) ? 0.0F : leftGripValue,
                rightHandPos, rightHandRot, rightHandTracked,
                leftHandPos, leftHandRot, leftHandTracked,
                hmdPos, hmdRot);

        // Step Height (Walk Up)
        if (mc.thePlayer != null) {
            mc.thePlayer.stepHeight = VRClientSettings.walkUpEnabled ? 1.05F : 0.6F;
        }

        // 4. World Turning (Right Thumbstick X). A remapped horizontal direction emits a key instead.
        {
            float turnX = rightStickX;
            if ((turnX < 0.0F && VRClientSettings.controllerRightStickLeftKeyCode > 0)
                    || (turnX > 0.0F && VRClientSettings.controllerRightStickRightKeyCode > 0)) {
                turnX = 0.0F;
            }
            if (VRClientSettings.worldRotationStep <= 0.0F) {
                // Smooth rotation
                if (Math.abs(turnX) > 0.15F) {
                    float rotSpeed = turnX * VRClientSettings.worldRotationSmoothSensitivity * 40.0F;
                    HeadPoseController.setBaseYaw(HeadPoseController.getBaseYaw() + rotSpeed);
                    HeadPoseController.apply();
                }
            } else {
                // Snap turning
                if (Math.abs(turnX) > 0.55F) {
                    if (!snapTurnCooldown) {
                        snapTurnCooldown = true;
                        float step = VRClientSettings.worldRotationStep;
                        float angle = (turnX > 0) ? step : -step;
                        HeadPoseController.setBaseYaw(HeadPoseController.getBaseYaw() + angle);
                        HeadPoseController.apply(); // Immediately sync thePlayer.rotationYaw
                        triggerHaptic(false, 3_000_000L, VRClientSettings.hapticStrength, 0);
                    }
                } else if (Math.abs(rightStickX) < 0.20F) {
                    snapTurnCooldown = false;
                }
            }
        }

        // 5. Jump (Right Button B). When B is remapped, the key mapper owns its output.
        boolean defaultJumpState = rightSecondaryValue && VRClientSettings.controllerRightBKeyCode <= 0;
        boolean mappedJumpState = isMappedControllerKeyHeld(mc.gameSettings.keyBindJump.getKeyCode());
        boolean jumpState = defaultJumpState || mappedJumpState;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), jumpState);
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.jump = jumpState;
        }

        boolean jumpPressedThisFrame = defaultJumpState && !lastJumpButtonState;
        lastJumpButtonState = defaultJumpState;

        if (jumpPressedThisFrame && !mappedJumpState) {
            KeyBinding.onTick(mc.gameSettings.keyBindJump.getKeyCode());
            long now = System.currentTimeMillis();
            if (now - lastJumpPressTime < 400L) { // Double tap within 400ms toggles creative flight
                if (mc.thePlayer != null && mc.thePlayer.capabilities.allowFlying) {
                    mc.thePlayer.capabilities.isFlying = !mc.thePlayer.capabilities.isFlying;
                    mc.thePlayer.sendPlayerAbilities();
                    triggerHaptic(false, 15_000_000L, 0.65F, 0);
                }
                lastJumpPressTime = 0L;
            } else {
                lastJumpPressTime = now;
            }
        }

        // 6. Crouch / Sneak (Right Thumbstick Click Toggle OR Physical Room Sneak)
        if (rightStickClick && !lastRightStickClick
                && VRClientSettings.controllerRightStickClickKeyCode <= 0) {
            sneakToggled = !sneakToggled;
            triggerHaptic(false, 8_000_000L, 0.40F, 0);
        }
        lastRightStickClick = rightStickClick;

        if (jumpPressedThisFrame) {
            sneakToggled = false;
        }

        com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl roomTask =
                com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance();
        boolean roomSneakActive = roomTask.isSneaking();
        boolean roomCrawling = roomTask.isCrawling();
        boolean mappedSneakState = isMappedControllerKeyHeld(mc.gameSettings.keyBindSneak.getKeyCode());
        boolean sneakState = sneakToggled || roomSneakActive || mappedSneakState;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneakState);
        if (mc.thePlayer != null) {
            // Keep Minecraft's crouch flag synchronized for mods (e.g. Tinkers' Construct).
            if (mc.thePlayer.movementInput != null) {
                mc.thePlayer.movementInput.sneak = sneakState;
            }
            mc.thePlayer.setSneaking(sneakState);

            // Room-crawling owns its much shorter 0.65 m hitbox and yOffset. Do not let
            // the ordinary 1.50 m sneak hitbox overwrite it during the same interaction tick.
            if (roomCrawling) {
                // VRTaskRoomCrawl.update() has already applied the crawl dimensions.
            } else if (sneakState) {
                mc.thePlayer.yOffset = 1.34F;
                mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 1.50D;
            } else if (mc.thePlayer.yOffset == 1.34F) {
                mc.thePlayer.yOffset = 1.62F;
                mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 1.80D;
            }
        }

        // 7. Attack / Mine / Break Block. Preserve the anti-flicker trigger latch from above.
        boolean blockingStopsAttack = !VRClientSettings.worldAttacksWhileBlocking
                && mc.thePlayer != null && mc.thePlayer.isBlocking();
        boolean mappedAttackState = isMappedControllerKeyHeld(mc.gameSettings.keyBindAttack.getKeyCode());
        boolean defaultAttackState = rightAttackTriggerHeld && VRClientSettings.controllerRightTriggerKeyCode <= 0;
        boolean attackState = (defaultAttackState || mappedAttackState) && !hotbar.isMainSelectorActive()
                && !com.gtnhvr.gui.VRWristInventory.getInstance().isActive()
                && !blockingStopsAttack;
        if (attackState || (rightPrimaryValue && VRClientSettings.controllerRightAKeyCode <= 0)) {
            leftHandActive = false;
        } else if (leftTriggerValue > 0.35F) {
            leftHandActive = true;
        }
        // Re-arm the click edge if another screen/mod cleared the Minecraft key state
        // while the controller-side held flag was still true.
        boolean attackKeyDown = mc.gameSettings.keyBindAttack.getIsKeyPressed();
        if (attackState && !mappedAttackState && (!inGameAttackHeld || !attackKeyDown)) {
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        inGameAttackHeld = attackState;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), attackState);
        if (attackState && mc.thePlayer != null && mc.thePlayer.ticksExisted % 4 == 0) {
            triggerHaptic(false, 3_000_000L, 0.35F, 0);
        }

        // 8. Use Item / Place Block / Interact (Left Trigger squeeze > 0.35F OR Right Button A OR TaskRoomConsume)
        boolean roomConsume = com.gtnhvr.gameplay.tasks.VRTaskRoomConsume.getInstance().isConsuming();
        boolean roomConsumeLeft = com.gtnhvr.gameplay.tasks.VRTaskRoomConsume.getInstance().isConsumingLeft();
        boolean isLeftTriggerUse = (VRClientSettings.worldTwoHanded
                && VRClientSettings.controllerLeftTriggerKeyCode <= 0 && leftTriggerValue > 0.35F) || roomConsumeLeft;
        boolean rightAUse = rightPrimaryValue && VRClientSettings.controllerRightAKeyCode <= 0;
        boolean mappedUseItemState = isMappedControllerKeyHeld(mc.gameSettings.keyBindUseItem.getKeyCode());
        boolean useItemState = com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance().isBowHeld(mc)
                ? com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance().isDrawing()
                : (isLeftTriggerUse || rightAUse || roomConsume || mappedUseItemState) && !hotbar.isSelectorActive()
                && !com.gtnhvr.gui.VRWristInventory.getInstance().isActive();
        if (useItemState) {
            if (isLeftTriggerUse) {
                leftHandActive = true;
            } else if (rightPrimaryValue) {
                leftHandActive = false;
            }
            if (mc.objectMouseOver != null) {
                if (mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
                    com.gtnhvr.gui.VRVirtualScreen.setLastInteractedBlock(
                            mc.objectMouseOver.blockX,
                            mc.objectMouseOver.blockY,
                            mc.objectMouseOver.blockZ
                    );
                } else if (mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY && mc.objectMouseOver.entityHit != null) {
                    com.gtnhvr.gui.VRVirtualScreen.setLastInteractedEntity(mc.objectMouseOver.entityHit);
                }
            }

            if (!inGameUseItemHeld && !mappedUseItemState) {
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            }
        }
        inGameUseItemHeld = useItemState;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), useItemState);

        // 9. Locomotion / Movement (Left Thumbstick)
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            applyVRMovement(mc.thePlayer.movementInput);
        }
    }

    /**
     * 1:1 Visor Locomotion (GameActionMovement & MovementInputMixin):
     * Directly translates left thumbstick into Minecraft movement impulses.
     * Forward on thumbstick (+Y) moves the player in their current gaze direction.
     * Turning with the right stick or physically turning instantly steers movement direction.
     */
    public void applyVRMovement(net.minecraft.util.MovementInput input) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.currentScreen != null) return;

        // If aiming teleport arc or physically climbing, don't apply ground walking movement
        if (com.gtnhvr.gameplay.tasks.VRTaskTeleport.getInstance().isAiming()) return;
        // In teleport mode the stick only aims the teleport arc; no smooth locomotion.
        if (VRClientSettings.movementMode == com.gtnhvr.settings.MovementMode.TELEPORT) return;
        if (com.gtnhvr.gameplay.tasks.VRTaskRoomClimb.getInstance().isClimbing()) return;


        float deadzone = 0.08F;
        float sx = (Math.abs(leftStickX) > deadzone)
                ? Math.signum(leftStickX) * (Math.abs(leftStickX) - deadzone) / (1.0F - deadzone) : 0.0F;
        float sy = (Math.abs(leftStickY) > deadzone)
                ? Math.signum(leftStickY) * (Math.abs(leftStickY) - deadzone) / (1.0F - deadzone) : 0.0F;

        boolean mappedMovementInput =
                isMappedControllerKeyHeld(mc.gameSettings.keyBindForward.getKeyCode())
                || isMappedControllerKeyHeld(mc.gameSettings.keyBindBack.getKeyCode())
                || isMappedControllerKeyHeld(mc.gameSettings.keyBindLeft.getKeyCode())
                || isMappedControllerKeyHeld(mc.gameSettings.keyBindRight.getKeyCode());
        if (sx == 0.0F && sy == 0.0F && !mappedMovementInput) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            return;
        }

        // Clamp total magnitude to 1.0F
        float mag = (float) Math.sqrt(sx * sx + sy * sy);
        if (mag > 1.0F) {
            sx /= mag;
            sy /= mag;
        }

        // If a stick direction is explicitly remapped, do not also apply that direction as analog walking.
        if (leftStickY > 0.0F && VRClientSettings.controllerLeftStickUpKeyCode > 0) sy = 0.0F;
        if (leftStickY < 0.0F && VRClientSettings.controllerLeftStickDownKeyCode > 0) sy = 0.0F;
        if (leftStickX < 0.0F && VRClientSettings.controllerLeftStickLeftKeyCode > 0) sx = 0.0F;
        if (leftStickX > 0.0F && VRClientSettings.controllerLeftStickRightKeyCode > 0) sx = 0.0F;

        float forward = sy;
        float strafe = -sx; // In Minecraft, moveStrafe is +1 for left, -1 for right
        // A remapped stick direction can target any existing Minecraft/Forge/mod key binding.
        if (isMappedControllerKeyHeld(mc.gameSettings.keyBindForward.getKeyCode())) forward += 1.0F;
        if (isMappedControllerKeyHeld(mc.gameSettings.keyBindBack.getKeyCode())) forward -= 1.0F;
        if (isMappedControllerKeyHeld(mc.gameSettings.keyBindLeft.getKeyCode())) strafe += 1.0F;
        if (isMappedControllerKeyHeld(mc.gameSettings.keyBindRight.getKeyCode())) strafe -= 1.0F;
        float combinedMag = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (combinedMag > 1.0F) {
            forward /= combinedMag;
            strafe /= combinedMag;
        }

        // If movement mode is CONTROLLER or OFFHAND, steer relative to left controller direction:
        if ((com.gtnhvr.settings.VRClientSettings.movementMode == com.gtnhvr.settings.MovementMode.CONTROLLER
                || com.gtnhvr.settings.VRClientSettings.movementMode == com.gtnhvr.settings.MovementMode.OFFHAND)
                && leftHandTracked && mc.thePlayer != null) {
            float handYaw = getLeftHandWorldYaw();
            float deltaYaw = (float) Math.toRadians(handYaw - mc.thePlayer.rotationYaw);
            float cos = (float) Math.cos(deltaYaw);
            float sin = (float) Math.sin(deltaYaw);
            float rotForward = forward * cos + strafe * sin;
            float rotStrafe = strafe * cos - forward * sin;
            forward = rotForward;
            strafe = rotStrafe;
        } else if (com.gtnhvr.settings.VRClientSettings.movementMode == com.gtnhvr.settings.MovementMode.HMD
                && mc.thePlayer != null) {
            // Steer relative to the headset gaze even when another device controls the look direction.
            float deltaYaw = (float) Math.toRadians(HeadPoseController.getHmdWorldYaw() - mc.thePlayer.rotationYaw);
            float cos = (float) Math.cos(deltaYaw);
            float sin = (float) Math.sin(deltaYaw);
            float rotForward = forward * cos + strafe * sin;
            float rotStrafe = strafe * cos - forward * sin;
            forward = rotForward;
            strafe = rotStrafe;
        }

        // Auto-sprinting when pushed forward (connected to VRClientSettings.sprintThreshold)
        boolean mappedSprint = isMappedControllerKeyHeld(mc.gameSettings.keyBindSprint.getKeyCode());
        boolean sprint = ((leftStickClick && VRClientSettings.controllerLeftStickClickKeyCode <= 0)
                || mappedSprint || forward >= com.gtnhvr.settings.VRClientSettings.sprintThreshold);
        if (sprint) {
            sneakToggled = false;
        }
        if (sprint && !mc.thePlayer.isSneaking()) {
            mc.thePlayer.setSprinting(true);
            forward = 1.0F;
        }

        input.moveForward = forward;
        input.moveStrafe = strafe;

        if (input.sneak) {
            input.moveForward *= 0.3F;
            input.moveStrafe *= 0.3F;
        }

        // Synchronize vanilla key bindings directly to match effective impulses
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),
                forward > 0.20F || isMappedControllerKeyHeld(mc.gameSettings.keyBindForward.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),
                forward < -0.20F || isMappedControllerKeyHeld(mc.gameSettings.keyBindBack.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(),
                strafe > 0.20F || isMappedControllerKeyHeld(mc.gameSettings.keyBindLeft.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),
                strafe < -0.20F || isMappedControllerKeyHeld(mc.gameSettings.keyBindRight.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(),
                sprint || isMappedControllerKeyHeld(mc.gameSettings.keyBindSprint.getKeyCode()));
    }

    public void setKbDragHand(boolean isLeft) {
        this.kbDragHandIsLeft = isLeft;
    }

    /**
     * Renders luminous 3D laser pointer beam from active controller hand to virtual screen hit point.
     * When Virtual Keyboard is open, renders simultaneous lasers for BOTH hands!
     * GL_DEPTH_TEST is disabled during rendering to ensure laser is never occluded or clipped.
     * Originates from the forward tip of the pointer tool (-0.160m along -Z).
     */
    public void renderLaser() {
        VRVirtualKeyboard keyboard = VRVirtualKeyboard.getInstance();
        if (keyboard.isVisible()) {
            if (rightHandTracked) {
                renderSingleLaser(false);
            }
            if (leftHandTracked) {
                renderSingleLaser(true);
            }
            return;
        }

        boolean leftActive = isLeftCursorHand && leftHandTracked;
        boolean rightActive = !isLeftCursorHand && rightHandTracked;
        if (!leftActive && !rightActive) {
            if (rightHandTracked) {
                leftActive = false;
            } else if (leftHandTracked) {
                leftActive = true;
            } else {
                return;
            }
        }
        renderSingleLaser(leftActive);
    }

    private void renderSingleLaser(boolean leftActive) {
        Vector3f activePos = leftActive ? leftHandPos : rightHandPos;
        Quaternionf activeRot = leftActive ? leftHandRot : rightHandRot;

        // Pointer tip offset: barrel extends forward to z = -0.160m (start slightly inside emitter tip)
        Vector3f laserStart = activeRot.transform(new Vector3f(0.0F, 0.0F, -0.158F), new Vector3f()).add(activePos);

        VRVirtualScreen screen = VRVirtualScreen.getInstance();
        VRVirtualKeyboard keyboard = VRVirtualKeyboard.getInstance();
        com.gtnhvr.gui.VRWristInventory wristInv = com.gtnhvr.gui.VRWristInventory.getInstance();
        Vector3f endPos;
        boolean hittingTarget = false;
        Quaternionf targetRot = null;

        boolean isPointerForWrist = (wristInv.isWristLeft() && !leftActive) || (!wristInv.isWristLeft() && leftActive);
        if (wristInv.isActive() && isPointerForWrist) {
            // Wrist inventory overlay uses '+' crosshair without laser beam
            return;
        }

        VRVirtualKeyboard.PointerState kbPointer = keyboard.getPointer(leftActive); if (keyboard.isVisible() && kbPointer != null && kbPointer.hit && kbPointer.handle != VRVirtualKeyboard.KeyboardHandle.NONE) {
            endPos = new Vector3f(kbPointer.hitPoint);
            hittingTarget = true;
            targetRot = keyboard.getKbRot();
        } else if (screen.getHoveredHandle() != VRVirtualScreen.ScreenHandle.NONE && isLeftCursorHand == leftActive) {
            endPos = new Vector3f(screen.getLastHitPoint());
            hittingTarget = true;
            targetRot = screen.getScreenRot();
        } else {
            // Forward 2.5 meters along controller aim direction
            Vector3f forward = activeRot.transform(new Vector3f(0.0F, 0.0F, -2.5F), new Vector3f());
            endPos = new Vector3f(laserStart).add(forward);
        }

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL); // Allows pointer tool chassis to occlude the laser origin!
        GLStateManager.glDepthMask(false);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // Additive luminous glow

        // Theme colors: Cyan for right hand, Amber for left hand
        float r = leftActive ? 1.0F : 0.0F;
        float g = leftActive ? 0.65F : 0.85F;
        float b = leftActive ? 0.10F : 1.0F;

        // 1. Outer glow beam
        GLStateManager.glLineWidth(5.0F);
        GLStateManager.glBegin(GL11.GL_LINES);
        GLStateManager.glColor4f(r, g, b, 0.25F);
        GLStateManager.glVertex3f(laserStart.x, laserStart.y, laserStart.z);
        GLStateManager.glColor4f(r, g, b, hittingTarget ? 0.65F : 0.35F);
        GLStateManager.glVertex3f(endPos.x, endPos.y, endPos.z);
        GLStateManager.glEnd();

        // 2. Core sharp laser beam
        GLStateManager.glLineWidth(2.5F);
        GLStateManager.glBegin(GL11.GL_LINES);
        GLStateManager.glColor4f(Math.min(1.0F, r + 0.3F), Math.min(1.0F, g + 0.3F), Math.min(1.0F, b + 0.3F), 0.60F);
        GLStateManager.glVertex3f(laserStart.x, laserStart.y, laserStart.z);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, hittingTarget ? 1.0F : 0.70F);
        GLStateManager.glVertex3f(endPos.x, endPos.y, endPos.z);
        GLStateManager.glEnd();

        // 3. Bright target reticle at hit point on virtual screen or keyboard
        if (hittingTarget && targetRot != null) {
            GLStateManager.glPushMatrix();
            GLStateManager.glTranslatef(endPos.x, endPos.y, endPos.z);
            org.joml.AxisAngle4f aa = new org.joml.AxisAngle4f(targetRot);
            if (aa.angle != 0.0F && (aa.x != 0.0F || aa.y != 0.0F || aa.z != 0.0F)) {
                GLStateManager.glRotatef((float) Math.toDegrees(aa.angle), aa.x, aa.y, aa.z);
            }

            // Glowing ring reticle (radius 2.0 cm)
            GLStateManager.glLineWidth(2.5F);
            GLStateManager.glColor4f(r, g, b, 0.95F);
            GLStateManager.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 24; i++) {
                double a = i * (Math.PI * 2.0 / 24.0);
                GLStateManager.glVertex3f((float) Math.cos(a) * 0.020F, (float) Math.sin(a) * 0.020F, 0.002F);
            }
            GLStateManager.glEnd();

            // Solid bright white center dot (radius 0.6 cm)
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GLStateManager.glBegin(GL11.GL_TRIANGLE_FAN);
            GLStateManager.glVertex3f(0.0F, 0.0F, 0.002F);
            for (int i = 0; i <= 16; i++) {
                double a = i * (Math.PI * 2.0 / 16.0);
                GLStateManager.glVertex3f((float) Math.cos(a) * 0.006F, (float) Math.sin(a) * 0.006F, 0.002F);
            }
            GLStateManager.glEnd();

            GLStateManager.glPopMatrix();
        }

        GLStateManager.glPopAttrib();
    }

    public boolean isRightHandTracked() {
        return rightHandTracked;
    }

    public Vector3f getRightHandPos() {
        return rightHandPos;
    }

    public Quaternionf getRightHandRot() {
        return rightHandRot;
    }

    public boolean isLeftHandTracked() {
        return leftHandTracked;
    }

    public Vector3f getLeftHandPos() {
        return leftHandPos;
    }

    public Quaternionf getLeftHandRot() {
        return leftHandRot;
    }

    public boolean isRightGripHandTracked() {
        return rightGripHandTracked;
    }

    /** Physical (not handedness-swapped) right controller grip position. */
    public Vector3f getRightGripHandPos() {
        return rightGripHandPos;
    }

    public boolean isLeftGripHandTracked() {
        return leftGripHandTracked;
    }

    /** Physical (not handedness-swapped) left controller grip position. */
    public Vector3f getLeftGripHandPos() {
        return leftGripHandPos;
    }

    public float getRightTriggerValue() { return VRClientSettings.controllerRightTriggerKeyCode > 0 ? 0.0F : rightTriggerValue; }
    public float getLeftTriggerValue() { return VRClientSettings.controllerLeftTriggerKeyCode > 0 ? 0.0F : leftTriggerValue; }
    public float getRightGripValue() { return VRClientSettings.controllerRightGripKeyCode > 0 ? 0.0F : rightGripValue; }
    public float getLeftGripValue() { return VRClientSettings.controllerLeftGripKeyCode > 0 ? 0.0F : leftGripValue; }
    public float getLeftStickX() {
        if (leftStickX < 0.0F && VRClientSettings.controllerLeftStickLeftKeyCode > 0) return 0.0F;
        if (leftStickX > 0.0F && VRClientSettings.controllerLeftStickRightKeyCode > 0) return 0.0F;
        return leftStickX;
    }
    public float getLeftStickY() {
        if (leftStickY > 0.0F && VRClientSettings.controllerLeftStickUpKeyCode > 0) return 0.0F;
        if (leftStickY < 0.0F && VRClientSettings.controllerLeftStickDownKeyCode > 0) return 0.0F;
        return leftStickY;
    }
    public float getRightStickX() {
        if (rightStickX < 0.0F && VRClientSettings.controllerRightStickLeftKeyCode > 0) return 0.0F;
        if (rightStickX > 0.0F && VRClientSettings.controllerRightStickRightKeyCode > 0) return 0.0F;
        return rightStickX;
    }
    public float getRightStickY() {
        if (rightStickY > 0.0F && VRClientSettings.controllerRightStickUpKeyCode > 0) return 0.0F;
        if (rightStickY < 0.0F && VRClientSettings.controllerRightStickDownKeyCode > 0) return 0.0F;
        return rightStickY;
    }
    public boolean isSneakActive() {
        return sneakToggled || (com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().isSneaking());
    }
    public boolean isLeftCursorHand() { return isLeftCursorHand; }
    public boolean isLeftPrimaryValue() { return VRClientSettings.controllerLeftXKeyCode <= 0 && leftPrimaryValue; }
    public boolean isRightPrimaryValue() { return VRClientSettings.controllerRightAKeyCode <= 0 && rightPrimaryValue; }
    public boolean isLeftPrimaryPressed() { return isLeftPrimaryValue(); }
    public boolean isRightPrimaryPressed() { return isRightPrimaryValue(); }
    public boolean isLeftSecondaryValue() { return VRClientSettings.controllerLeftYKeyCode <= 0 && leftSecondaryValue; }
    public boolean isRightSecondaryValue() { return VRClientSettings.controllerRightBKeyCode <= 0 && rightSecondaryValue; }

    private static java.lang.reflect.Field mouseLatestXField;
    private static java.lang.reflect.Field mouseLatestYField;
    private static java.lang.reflect.Field mouseXField;
    private static java.lang.reflect.Field mouseYField;
    private static java.lang.reflect.Field mouseLastEventXField;
    private static java.lang.reflect.Field mouseLastEventYField;
    private static java.nio.ByteBuffer mouseButtonsBuffer;
    private static boolean mouseFieldsInitialized = false;

    private static void initMouseReflection() {
        if (!mouseFieldsInitialized) {
            mouseFieldsInitialized = true;
            try {
                Class<?> clazz = Class.forName("org.lwjglx.input.Mouse");
                mouseLatestXField = clazz.getDeclaredField("latestX");
                mouseLatestXField.setAccessible(true);
                mouseLatestYField = clazz.getDeclaredField("latestY");
                mouseLatestYField.setAccessible(true);
                mouseXField = clazz.getDeclaredField("x");
                mouseXField.setAccessible(true);
                mouseYField = clazz.getDeclaredField("y");
                mouseYField.setAccessible(true);
                try {
                    mouseLastEventXField = clazz.getDeclaredField("lastEventX");
                    mouseLastEventXField.setAccessible(true);
                    mouseLastEventYField = clazz.getDeclaredField("lastEventY");
                    mouseLastEventYField.setAccessible(true);
                } catch (Throwable ignored) {}
                java.lang.reflect.Field btnField = clazz.getDeclaredField("buttons");
                btnField.setAccessible(true);
                mouseButtonsBuffer = (java.nio.ByteBuffer) btnField.get(null);
            } catch (Throwable ignored) {}
        }
    }

    private static void setMouseButtonState(int button, boolean state) {
        com.gtnhvr.input.VRMouseBridge.setButton(button, state);
        initMouseReflection();
        if (mouseButtonsBuffer != null && button >= 0 && button < mouseButtonsBuffer.capacity()) {
            mouseButtonsBuffer.put(button, (byte) (state ? 1 : 0));
        }
        try {
            int sdlButton = org.lwjglx.input.Mouse.lwjglToSdlMouseButton(button);
            int bit = 1 << (sdlButton - 1);
            if (state) {
                org.lwjglx.input.Mouse.sdlMouseButtonFlags |= bit;
            } else {
                org.lwjglx.input.Mouse.sdlMouseButtonFlags &= ~bit;
            }
        } catch (Throwable ignored) {}
    }

    private static void updateLwjglMousePosition(int screenX, int glY) {
        initMouseReflection();
        try {
            if (mouseLatestXField != null) mouseLatestXField.setInt(null, screenX);
            if (mouseLatestYField != null) mouseLatestYField.setInt(null, glY);
            if (mouseXField != null) mouseXField.setInt(null, screenX);
            if (mouseYField != null) mouseYField.setInt(null, glY);
            if (mouseLastEventXField != null) mouseLastEventXField.setInt(null, screenX);
            if (mouseLastEventYField != null) mouseLastEventYField.setInt(null, glY);
        } catch (Throwable ignored) {}
    }

    private final Vector3f tempHandWorldPos = new Vector3f();
    private float rightHandWorldPitch = 0.0F;
    private float rightHandWorldYaw = 0.0F;

    /**
     * Calculates the right controller's 3D position in the Minecraft world,
     * accounting for player position and world yaw.
     */
    /**
     * Converts a room-space point into a Minecraft world position. Uses the same mapping that places
     * the point under the eye matrices, so a controller at room position X has its ray start exactly
     * where the hand is drawn.
     */
    public net.minecraft.util.Vec3 roomToWorld(Vector3f room, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return net.minecraft.util.Vec3.createVectorHelper(room.x, room.y, room.z);
        }
        double px = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partialTicks;
        double py = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * partialTicks;
        double pz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partialTicks;
        Vector3f rel = OpenXRRuntime.roomToCameraRelative(0, room);
        return net.minecraft.util.Vec3.createVectorHelper(px + rel.x, py + rel.y, pz + rel.z);
    }

    /** Inverse of {@link #roomToWorld}: a world position expressed in room space. */
    public Vector3f worldToRoom(double wx, double wy, double wz, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return new Vector3f((float) wx, (float) wy, (float) wz);
        }
        double px = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partialTicks;
        double py = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * partialTicks;
        double pz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partialTicks;
        return OpenXRRuntime.cameraRelativeToRoom(0,
                new Vector3f((float) (wx - px), (float) (wy - py), (float) (wz - pz)));
    }

    /** Rotates a room-space direction into the Minecraft world frame. */
    public Vector3f roomDirectionToWorld(Vector3f roomDir) {
        float baseYawRad = (float) Math.toRadians(HeadPoseController.getBaseYaw());
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);
        return new Vector3f(-roomDir.x * cos + roomDir.z * sin, roomDir.y, -roomDir.x * sin - roomDir.z * cos);
    }

    public Vector3f getRightHandWorldPos(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return rightHandPos;

        net.minecraft.util.Vec3 v = roomToWorld(rightHandPos, partialTicks);
        tempHandWorldPos.set((float) v.xCoord, (float) v.yCoord, (float) v.zCoord);

        // Calculate world pitch and yaw of hand aim directly from hand world direction (1:1 Visor VRAimPicker)
        Vector3f dir = getRightHandWorldDir();
        float dirLen = (float) Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
        rightHandWorldPitch = (dirLen > 1e-6F) ? (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, dir.y / dirLen)))) : 0.0F;
        rightHandWorldYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

        return tempHandWorldPos;
    }

    public float getRightHandWorldPitch() {
        return rightHandWorldPitch;
    }

    public float getRightHandWorldYaw() {
        return rightHandWorldYaw;
    }

    private final Vector3f tempHandWorldDir = new Vector3f();

    public Vector3f getRightHandWorldDir() {
        Vector3f fwd = rightHandRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        float baseYawRad = (float) Math.toRadians(HeadPoseController.getBaseYaw());
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);

        // Room forward (-Z) rotates into Minecraft world:
        float worldDx = -fwd.x * cos + fwd.z * sin;
        float worldDz = -fwd.x * sin - fwd.z * cos;

        tempHandWorldDir.set(worldDx, fwd.y, worldDz);
        if (tempHandWorldDir.lengthSquared() > 1e-4F) {
            tempHandWorldDir.normalize();
        } else {
            tempHandWorldDir.set(0.0F, 0.0F, 1.0F);
        }
        return tempHandWorldDir;
    }

    private final Vector3f tempLeftHandWorldDir = new Vector3f();

    public Vector3f getLeftHandWorldDir() {
        Vector3f fwd = leftHandRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        float baseYawRad = (float) Math.toRadians(HeadPoseController.getBaseYaw());
        float cos = (float) Math.cos(baseYawRad);
        float sin = (float) Math.sin(baseYawRad);

        float worldDx = -fwd.x * cos + fwd.z * sin;
        float worldDz = -fwd.x * sin - fwd.z * cos;

        tempLeftHandWorldDir.set(worldDx, fwd.y, worldDz);
        if (tempLeftHandWorldDir.lengthSquared() > 1e-4F) {
            tempLeftHandWorldDir.normalize();
        } else {
            tempLeftHandWorldDir.set(0.0F, 0.0F, 1.0F);
        }
        return tempLeftHandWorldDir;
    }

    private final Vector3f tempLeftHandWorldPos = new Vector3f();
    private float leftHandWorldPitch = 0.0F;

    public Vector3f getLeftHandWorldPos(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return leftHandPos;

        net.minecraft.util.Vec3 v = roomToWorld(leftHandPos, partialTicks);
        tempLeftHandWorldPos.set((float) v.xCoord, (float) v.yCoord, (float) v.zCoord);

        Vector3f dir = getLeftHandWorldDir();
        float dirLen = (float) Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
        leftHandWorldPitch = (dirLen > 1e-6F) ? (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, dir.y / dirLen)))) : 0.0F;
        return tempLeftHandWorldPos;
    }

    public float getLeftHandWorldPitch() {
        return leftHandWorldPitch;
    }

    /** Hand currently using an item for look-direction purposes: 0 none, 1 main hand, 2 off hand. */
    public int getUsingHand() {
        if (inGameAttackHeld || (rightPrimaryValue && VRClientSettings.controllerRightAKeyCode <= 0)) return 1;
        if (VRClientSettings.worldTwoHanded && VRClientSettings.controllerLeftTriggerKeyCode <= 0
                && leftTriggerValue > 0.35F) return 2;
        return 0;
    }

    public float getLeftHandWorldYaw() {
        Vector3f dir = getLeftHandWorldDir();
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private boolean leftHandActive = false;

    public boolean isLeftHandActive() {
        return VRClientSettings.leftHanded ? !leftHandActive : leftHandActive;
    }

    public void triggerHaptic(boolean leftHand, long durationNanos, float amplitude, float frequency) {
        if (!initialized || session == null || hapticAction == null) return;
        if (!VRClientSettings.hapticsEnabled) return;
        // Logical hands are swapped in left-handed mode, so route the pulse to the physical controller.
        final boolean physicalLeft = VRClientSettings.leftHanded ? !leftHand : leftHand;
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.openxr.XrHapticVibration vibration = org.lwjgl.openxr.XrHapticVibration.calloc(stack)
                    .type(org.lwjgl.openxr.XR10.XR_TYPE_HAPTIC_VIBRATION)
                    .duration(durationNanos)
                    .amplitude(Math.max(0.0F, Math.min(1.0F, amplitude)))
                    .frequency(frequency > 0 ? frequency : org.lwjgl.openxr.XR10.XR_FREQUENCY_UNSPECIFIED);
            org.lwjgl.openxr.XrHapticActionInfo info = org.lwjgl.openxr.XrHapticActionInfo.calloc(stack)
                    .type(org.lwjgl.openxr.XR10.XR_TYPE_HAPTIC_ACTION_INFO)
                    .action(hapticAction)
                    .subactionPath(physicalLeft ? leftSubactionPath : rightSubactionPath);
            org.lwjgl.openxr.XR10.xrApplyHapticFeedback(session, info, org.lwjgl.openxr.XrHapticBaseHeader.create(vibration.address()));
        } catch (Throwable ignored) {}
    }

    public static void clearAllGameplayTasks(Minecraft mc) {
        com.gtnhvr.gameplay.tasks.VRTaskSwing.getInstance().clear();
        com.gtnhvr.gameplay.tasks.VRTaskRoomConsume.getInstance().clear(mc);
        com.gtnhvr.gameplay.tasks.VRTaskRoomClimb.getInstance().clear(mc);
        com.gtnhvr.gameplay.tasks.VRTaskRoomJump.getInstance().clear();
        com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().clear(mc);
        com.gtnhvr.gameplay.tasks.VRTaskRoomSwim.getInstance().clear();
        com.gtnhvr.gameplay.tasks.VRTaskVehicle.getInstance().clear();
        com.gtnhvr.gameplay.tasks.VRTaskBoat.getInstance().clear();
        com.gtnhvr.gameplay.tasks.VRTaskTeleport.getInstance().clear();
        com.gtnhvr.gameplay.tasks.VRTaskBow.getInstance().clear();
        com.gtnhvr.gameplay.VRShoulderGestures.getInstance().clear();
    }
}
