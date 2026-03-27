package dev.rstminecraft.mixin;

import dev.rstminecraft.RustElytraClient;
import dev.rstminecraft.utils.BaritoneControlChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Pseudo
@Mixin(targets = "baritone.f", remap = false)
public class BaritoneUpdateTarget2 {
    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "updateTarget", at = @At("HEAD"),require = 0)
    private void updateTarget(CallbackInfo ci) {
        BaritoneControlChecker.lookFlag = true;
    }

    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "<init>", at = @At("TAIL"),require = 0)
    private void init(CallbackInfo ci) {
        RustElytraClient.isLookMixinSuccess = true;
    }

}


