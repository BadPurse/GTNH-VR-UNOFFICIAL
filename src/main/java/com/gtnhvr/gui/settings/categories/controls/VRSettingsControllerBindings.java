package com.gtnhvr.gui.settings.categories.controls;

import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.VRVirtualKeyboard;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * In-world bindings for every standard digital controller control, stick direction,
 * and the two additional shoulder gestures. Zero keeps the built-in control mapping.
 * A selected keyboard key is emitted as a normal Minecraft/Forge key binding.
 */
@Lwjgl3Aware
public final class VRSettingsControllerBindings extends VROptionsSet {
    private static final int NO_CAPTURE = -1;

    private static final class Binding {
        final int id;
        final String labelKey;
        final IntSupplier getter;
        final IntConsumer setter;
        final boolean hasDefaultAction;

        Binding(int id, String labelKey, IntSupplier getter, IntConsumer setter, boolean hasDefaultAction) {
            this.id = id;
            this.labelKey = labelKey;
            this.getter = getter;
            this.setter = setter;
            this.hasDefaultAction = hasDefaultAction;
        }
    }

    private static final Binding[] BINDINGS = new Binding[] {
        new Binding(0, "gtnhvr.bindings.right_trigger",
                () -> VRClientSettings.controllerRightTriggerKeyCode, v -> VRClientSettings.controllerRightTriggerKeyCode = v, true),
        new Binding(1, "gtnhvr.bindings.left_trigger",
                () -> VRClientSettings.controllerLeftTriggerKeyCode, v -> VRClientSettings.controllerLeftTriggerKeyCode = v, true),
        new Binding(2, "gtnhvr.bindings.right_a",
                () -> VRClientSettings.controllerRightAKeyCode, v -> VRClientSettings.controllerRightAKeyCode = v, true),
        new Binding(3, "gtnhvr.bindings.right_b",
                () -> VRClientSettings.controllerRightBKeyCode, v -> VRClientSettings.controllerRightBKeyCode = v, true),
        new Binding(4, "gtnhvr.bindings.left_x",
                () -> VRClientSettings.controllerLeftXKeyCode, v -> VRClientSettings.controllerLeftXKeyCode = v, true),
        new Binding(5, "gtnhvr.bindings.left_y",
                () -> VRClientSettings.controllerLeftYKeyCode, v -> VRClientSettings.controllerLeftYKeyCode = v, true),
        new Binding(6, "gtnhvr.bindings.left_menu",
                () -> VRClientSettings.controllerLeftMenuKeyCode, v -> VRClientSettings.controllerLeftMenuKeyCode = v, true),
        new Binding(7, "gtnhvr.bindings.right_stick_click",
                () -> VRClientSettings.controllerRightStickClickKeyCode, v -> VRClientSettings.controllerRightStickClickKeyCode = v, true),
        new Binding(8, "gtnhvr.bindings.left_stick_click",
                () -> VRClientSettings.controllerLeftStickClickKeyCode, v -> VRClientSettings.controllerLeftStickClickKeyCode = v, true),
        new Binding(9, "gtnhvr.bindings.right_grip",
                () -> VRClientSettings.controllerRightGripKeyCode, v -> VRClientSettings.controllerRightGripKeyCode = v, true),
        new Binding(10, "gtnhvr.bindings.left_grip",
                () -> VRClientSettings.controllerLeftGripKeyCode, v -> VRClientSettings.controllerLeftGripKeyCode = v, true),
        new Binding(11, "gtnhvr.controls.shoulder.right_to_left",
                () -> VRClientSettings.shoulderRightToLeftKeyCode, v -> VRClientSettings.shoulderRightToLeftKeyCode = v, false),
        new Binding(12, "gtnhvr.controls.shoulder.left_to_right",
                () -> VRClientSettings.shoulderLeftToRightKeyCode, v -> VRClientSettings.shoulderLeftToRightKeyCode = v, false),
        new Binding(13, "gtnhvr.bindings.left_stick_up",
                () -> VRClientSettings.controllerLeftStickUpKeyCode, v -> VRClientSettings.controllerLeftStickUpKeyCode = v, true),
        new Binding(14, "gtnhvr.bindings.left_stick_down",
                () -> VRClientSettings.controllerLeftStickDownKeyCode, v -> VRClientSettings.controllerLeftStickDownKeyCode = v, true),
        new Binding(15, "gtnhvr.bindings.left_stick_left",
                () -> VRClientSettings.controllerLeftStickLeftKeyCode, v -> VRClientSettings.controllerLeftStickLeftKeyCode = v, true),
        new Binding(16, "gtnhvr.bindings.left_stick_right",
                () -> VRClientSettings.controllerLeftStickRightKeyCode, v -> VRClientSettings.controllerLeftStickRightKeyCode = v, true),
        new Binding(17, "gtnhvr.bindings.right_stick_up",
                () -> VRClientSettings.controllerRightStickUpKeyCode, v -> VRClientSettings.controllerRightStickUpKeyCode = v, true),
        new Binding(18, "gtnhvr.bindings.right_stick_down",
                () -> VRClientSettings.controllerRightStickDownKeyCode, v -> VRClientSettings.controllerRightStickDownKeyCode = v, true),
        new Binding(19, "gtnhvr.bindings.right_stick_left",
                () -> VRClientSettings.controllerRightStickLeftKeyCode, v -> VRClientSettings.controllerRightStickLeftKeyCode = v, true),
        new Binding(20, "gtnhvr.bindings.right_stick_right",
                () -> VRClientSettings.controllerRightStickRightKeyCode, v -> VRClientSettings.controllerRightStickRightKeyCode = v, true)
    };

    private int captureTarget = NO_CAPTURE;

    public VRSettingsControllerBindings(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.CENTER, 0,
                I18n.format("gtnhvr.controls.shoulder.master"),
                () -> VRClientSettings.shoulderGesturesEnabled,
                value -> {
                    VRClientSettings.shoulderGesturesEnabled = value;
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.master.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.LEFT, 1,
                I18n.format("gtnhvr.controls.shoulder.right_to_left.enabled"),
                () -> VRClientSettings.shoulderRightToLeftEnabled,
                value -> {
                    VRClientSettings.shoulderRightToLeftEnabled = value;
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.right_to_left.enabled.tooltip")
        ));

        widgets.add(new OptionWidget.ToggleWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("gtnhvr.controls.shoulder.left_to_right.enabled"),
                () -> VRClientSettings.shoulderLeftToRightEnabled,
                value -> {
                    VRClientSettings.shoulderLeftToRightEnabled = value;
                    VRClientSettings.save();
                },
                I18n.format("gtnhvr.controls.shoulder.left_to_right.enabled.tooltip")
        ));

        // Two compact columns. The settings screen scrolls this list on either thumbstick.
        for (int i = 0; i < BINDINGS.length; i++) {
            final Binding binding = BINDINGS[i];
            OptionWidgetPosition position = (i % 2 == 0)
                    ? OptionWidgetPosition.LEFT : OptionWidgetPosition.RIGHT;
            int row = 2 + i / 2;
            widgets.add(new OptionWidget.ButtonWidget(
                    position, row,
                    () -> captureTarget == binding.id
                            ? I18n.format("gtnhvr.bindings.press_key")
                            : I18n.format(binding.labelKey) + ": " + formatKey(binding),
                    () -> beginCapture(binding.id),
                    I18n.format("gtnhvr.bindings.key.tooltip")
            ));
        }

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 13,
                I18n.format("gtnhvr.controls.shoulder.distance"),
                0.10F, 0.45F, 0.01F,
                () -> VRClientSettings.shoulderGestureDistance,
                value -> {
                    VRClientSettings.shoulderGestureDistance = value;
                    VRClientSettings.save();
                },
                value -> String.format("%.0f cm", value * 100.0F),
                I18n.format("gtnhvr.controls.shoulder.distance.tooltip")
        ));

        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 13,
                I18n.format("gtnhvr.controls.shoulder.hold"),
                0.25F, 2.50F, 0.25F,
                () -> VRClientSettings.shoulderGestureHoldSeconds,
                value -> {
                    VRClientSettings.shoulderGestureHoldSeconds = value;
                    VRClientSettings.save();
                },
                value -> String.format("%.2f s", value),
                I18n.format("gtnhvr.controls.shoulder.hold.tooltip")
        ));
    }

    private void beginCapture(int target) {
        captureTarget = target;
        // Open the existing in-world VR keyboard so the binding can be selected without
        // a physical keyboard. VRVirtualKeyboard dispatches captured presses straight back
        // to handleVirtualKeyboardKey instead of typing into the settings screen.
        VRVirtualKeyboard.getInstance().setVisible(true);
    }

    private static String formatKey(Binding binding) {
        int keyCode = binding.getter.getAsInt();
        if (keyCode <= 0) {
            return binding.hasDefaultAction
                    ? I18n.format("gtnhvr.bindings.default")
                    : I18n.format("gtnhvr.controls.shoulder.unassigned");
        }
        String name = Keyboard.getKeyName(keyCode);
        return name == null || name.isEmpty() ? "KEY " + keyCode : name;
    }

    public boolean isCapturingBinding() {
        return captureTarget != NO_CAPTURE;
    }

    /** Called by the existing virtual keyboard; Escape can be assigned from the overlay. */
    public boolean handleVirtualKeyboardKey(int keyCode) {
        if (captureTarget == NO_CAPTURE) return false;
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            assignKeyCode(0);
        } else if (keyCode > 0) {
            assignKeyCode(keyCode);
        }
        return true;
    }

    @Override
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        if (captureTarget == NO_CAPTURE) return false;

        // The physical Escape key cancels; the virtual keyboard's Escape key is assigned
        // through handleVirtualKeyboardKey(), so both operations remain possible.
        if (keyCode == Keyboard.KEY_ESCAPE) {
            finishCapture();
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            assignKeyCode(0);
            return true;
        }
        if (keyCode > 0) {
            assignKeyCode(keyCode);
        }
        return true;
    }

    private void assignKeyCode(int keyCode) {
        for (Binding binding : BINDINGS) {
            if (binding.id == captureTarget) {
                binding.setter.accept(Math.max(0, keyCode));
                break;
            }
        }
        VRClientSettings.save();
        finishCapture();
    }

    private void finishCapture() {
        captureTarget = NO_CAPTURE;
        VRVirtualKeyboard.getInstance().setVisible(false);
    }
}
