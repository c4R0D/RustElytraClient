package dev.rstminecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.rstminecraft.RustElytraClient.cameraMixinSwitch;

@Environment(EnvType.CLIENT)
@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hideFlightParticles(CallbackInfo ci) {
        if (cameraMixinSwitch) ci.cancel();
    }

}