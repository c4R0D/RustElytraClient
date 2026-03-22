package dev.rstminecraft.mixin;

import baritone.Baritone;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rstminecraft.RustElytraClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

//@Pseudo
@Mixin(value = baritone.command.defaults.ExecutionControlCommands.class, remap = false)
public class PausedTestMixin {
    @Inject(method = "<init>", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void captureFirstBooleanArray(Baritone par1, CallbackInfo ci, @Local(ordinal = 0) boolean[] firstBoolArray) {
        RustElytraClient.paused = firstBoolArray;
        RustElytraClient.isPausedMixinSuccess = true;
    }
}