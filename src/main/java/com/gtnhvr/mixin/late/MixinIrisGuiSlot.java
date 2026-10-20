package com.gtnhvr.mixin.late;

import com.gtnhvr.input.VRMouseBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.coderbot.iris.gui.element.IrisGuiSlot", remap = false)
public abstract class MixinIrisGuiSlot {

    @Redirect(method = {"mouseClicked", "drawScreen", "func_148128_a"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$irisMouseDownLwjgl(int button) {
        return VRMouseBridge.isButtonDown(button);
    }

    @Redirect(method = {"mouseClicked", "drawScreen", "func_148128_a"},
            at = @At(value = "INVOKE", target = "Lorg/lwjglx/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$irisMouseDownLwjglx(int button) {
        return VRMouseBridge.isButtonDown(button);
    }
}
