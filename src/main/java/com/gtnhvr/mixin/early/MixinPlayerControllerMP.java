package com.gtnhvr.mixin.early;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.gameplay.tasks.VRTaskSwing;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1:1 Visor Mining Keep (LevelRendererSwingDamageMixin / isKeepingVanillaMining):
 * Prevents Minecraft from instantly wiping curBlockDamageMP = 0 between physical VR swings.
 * Allows slow tools (axes, pickaxes) to break blocks across multiple physical swings without
 * block destruction progress resetting to 0 between hits.
 */
@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Shadow
    private int blockHitDelay;

    @Inject(method = {"resetBlockRemoving", "func_78767_c"}, at = @At("HEAD"), cancellable = true)
    private void gtnhvr$onResetBlockRemoving(CallbackInfo ci) {
        if (OpenXRRuntime.isSessionRunning() && VRTaskSwing.getInstance().isKeepingMining()) {
            ci.cancel();
        }
    }

    @Inject(method = {"onPlayerDamageBlock", "func_78759_a"}, at = @At("HEAD"))
    private void gtnhvr$onPlayerDamageBlock(int x, int y, int z, int side, CallbackInfoReturnable<Boolean> cir) {
        if (OpenXRRuntime.isSessionRunning() && VRTaskSwing.getInstance().isKeepingMining()) {
            this.blockHitDelay = 0;
        }
    }
}
