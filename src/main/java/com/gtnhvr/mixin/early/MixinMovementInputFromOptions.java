package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps VR sneak state synchronized with vanilla movement input every player tick.
 * Several Forge mods inspect EntityPlayer.isSneaking() / the sneak key when deciding
 * whether a tool should use its area-mining mode, so setting the state only from the
 * render/controller loop is not reliable enough.
 */
@Mixin(MovementInputFromOptions.class)
public abstract class MixinMovementInputFromOptions extends MovementInput {

    @Inject(method = {"updatePlayerMoveState", "func_78898_a"}, at = @At("TAIL"))
    private void gtnhvr$applyVRMovementState(CallbackInfo ci) {
        if (!OpenXRRuntime.isSessionRunning()) return;

        VRControllerHandler handler = VRControllerHandler.getInstance();
        Minecraft mc = Minecraft.getMinecraft();

        // Preserve vanilla keyboard sneak, while also latching physical VR crouch,
        // controller-toggle sneak, and a controller remapped to the vanilla sneak key.
        this.sneak = this.sneak || handler.isSneakActive();
        if (this.sneak && mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            if (mc.thePlayer != null) {
                mc.thePlayer.setSneaking(true);
            }
        }

        // Apply analog movement after sneak is restored so movement is slowed correctly.
        handler.applyVRMovement(this);
    }
}
