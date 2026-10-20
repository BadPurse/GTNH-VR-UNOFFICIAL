package com.gtnhvr.mixin.early;

import com.gtnhvr.input.VRMouseBridge;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * cpw.mods.fml.client.GuiScrollingList (Forge mod list) polls Mouse.isButtonDown(0) from drawScreen to detect clicks and drags, which never
 * sees the VR pointer. lwjgl3ify may rewrite org.lwjgl.input.Mouse to org.lwjglx.input.Mouse
 * before or after Mixin runs, so both call owners are redirected (require = 0 keeps the one
 * that is absent from failing the config).
 */
@Pseudo
@Mixin(targets = "cpw.mods.fml.client.GuiScrollingList")
public abstract class MixinGuiScrollingList {

    @Redirect(method = {"drawScreen"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$vrMouseDownLwjgl(int button) {
        return VRMouseBridge.isButtonDown(button);
    }

    @Redirect(method = {"drawScreen"},
            at = @At(value = "INVOKE", target = "Lorg/lwjglx/input/Mouse;isButtonDown(I)Z", remap = false),
            require = 0)
    private boolean gtnhvr$vrMouseDownLwjglx(int button) {
        return VRMouseBridge.isButtonDown(button);
    }
}
