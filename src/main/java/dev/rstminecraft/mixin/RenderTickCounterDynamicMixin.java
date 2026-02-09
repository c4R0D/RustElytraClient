package dev.rstminecraft.mixin;

import net.minecraft.client.render.RenderTickCounter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.rstminecraft.RustElytraClient.timerMultiplier;

@Mixin(RenderTickCounter.Dynamic.class)
public abstract class RenderTickCounterDynamicMixin {
    @Shadow
    private float lastFrameDuration;

    /**
     * 目标方法: private int beginRenderTick(long timeMillis)
     * 注入点: 在更新 prevTimeMillis 字段时注入（此时 lastFrameDuration 刚刚计算完成）
     */
    @Inject(method = "beginRenderTick(J)I", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;prevTimeMillis:J", opcode = Opcodes.PUTFIELD))
    private void onBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> info) {
        this.lastFrameDuration *= timerMultiplier;
    }
}