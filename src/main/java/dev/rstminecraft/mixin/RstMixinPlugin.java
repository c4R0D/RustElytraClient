package dev.rstminecraft.mixin;

import baritone.api.BaritoneAPI;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public class RstMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
//        if (mixinClassName.equals("dev.rstminecraft.mixin.BaritoneUpdateTarget2") || mixinClassName.equals("dev.rstminecraft.mixin.PausedTestMixin2")) {
//            try {
//                Class<?> clazz = Class.forName(targetClassName, false,
//                        Thread.currentThread().getContextClassLoader());
//                return !clazz.isInterface();
//            } catch (ClassNotFoundException e) {
//                return false;
//            }
//
//        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}