package dev.rstminecraft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.rstminecraft.RustElytraClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(targets = {"baritone.command.defaults.ExecutionControlCommands", "baritone.al"}, remap = false)
public class PausedTestMixin {
    @Inject(method = "<init>", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void captureFirstBooleanArray(@Coerce Object par1, CallbackInfo ci, @Local(ordinal = 0) boolean[] firstBoolArray) {
        RustElytraClient.paused = firstBoolArray;
        RustElytraClient.isPausedMixinSuccess = true;
    }
}