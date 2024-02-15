package net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess.mixin;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess.OptimizeGameRulesAccessTransformer_GameRules;
import net.daporkchop.ppatches.util.mixin.ext.MakeFinal;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandles;
import java.util.TreeMap;

/**
 * @author DaPorkchop_
 */
@Mixin(GameRules.class)
abstract class MixinGameRules {
    @Shadow
    @Final
    private TreeMap<String, GameRules.Value> rules;

    @MakeFinal
    private static ImmutableSet<String> ppatches_optimizeGameRulesAccess_vanillaRules;

    @MakeFinal
    private static ImmutableSet<String> ppatches_optimizeGameRulesAccess_allRules;

    private static final GameRules.Value ppatches_optimizeGameRulesAccess_dummyValue = new GameRules.Value("", null);

    static {
        //noinspection StringEquality
        Preconditions.checkState(ppatches_optimizeGameRulesAccess_dummyValue.getString() == "");
        Preconditions.checkState(ppatches_optimizeGameRulesAccess_dummyValue.getInt() == 0);
        Preconditions.checkState(!ppatches_optimizeGameRulesAccess_dummyValue.getBoolean());
    }

    @Redirect(method = "addGameRule(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/GameRules$ValueType;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/TreeMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            allow = 1, require = 1)
    @SneakyThrows
    private Object ppatches_optimizeGameRulesAccess_preventAddingDuplicateGameRules(TreeMap<Object, Object> rules, Object key, Object valueIn) {
        String name = (String) key;
        GameRules.Value value = (GameRules.Value) valueIn;
        if (rules.putIfAbsent(name, value) != null) {
            throw new IllegalStateException("attempted to add duplicate gamerule: " + name);
        }

        if (ppatches_optimizeGameRulesAccess_allRules.contains(name)) {
            GameRules.Value existingValue = this.ppatches_optimzeGameRules_getOptimizedFieldValue(name);
            Preconditions.checkState(existingValue == ppatches_optimizeGameRulesAccess_dummyValue, "game rule %s already had its field set to %s", name, existingValue);
            MethodHandles.publicLookup().findSetter(MixinGameRules.class, name + OptimizeGameRulesAccessTransformer_GameRules.RULE_FIELD_SUFFIX, GameRules.Value.class).invokeExact((GameRules) (Object) this, value);
        } else {
            PPatchesMod.LOGGER.warn("Unknown game rule '{}' was added, it will not be optimized!", name);
            PPatchesConfig.vanilla_optimizeGameRulesAccess.encounteredUnknownModdedGameRule(name);
        }

        return null;
    }

    @Inject(
            method = {
                    "addGameRule(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/GameRules$ValueType;)V",
                    "setOrCreateGameRule(Ljava/lang/String;Ljava/lang/String;)V",
                    "readFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V",
            },
            at = @At("HEAD"),
            allow = 3, require = 3)
    private void ppatches_optimizeGameRulesAccess_injectValidationPreV(CallbackInfo ci) {
        this.ppatches_optimzeGameRules_validateFields();
    }

    @Inject(
            method = {
                    "addGameRule(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/GameRules$ValueType;)V",
                    "setOrCreateGameRule(Ljava/lang/String;Ljava/lang/String;)V",
                    "readFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V",
            },
            at = @At("RETURN"),
            allow = 3, require = 3)
    private void ppatches_optimizeGameRulesAccess_injectValidationPostV(CallbackInfo ci) {
        this.ppatches_optimzeGameRules_validateFields();
    }

    @Inject(
            method = {
                    "writeToNBT()Lnet/minecraft/nbt/NBTTagCompound;",
                    "getRules()[Ljava/lang/String;",
                    "hasRule(Ljava/lang/String;)Z",
            },
            at = @At("HEAD"),
            allow = 3, require = 3)
    private void ppatches_optimizeGameRulesAccess_injectValidationPostOther(CallbackInfoReturnable<?> ci) {
        this.ppatches_optimzeGameRules_validateFields();
    }

    @Unique
    private void ppatches_optimzeGameRules_validateFields() {
        for (String name : ppatches_optimizeGameRulesAccess_allRules) {
            GameRules.Value existingValue = this.ppatches_optimzeGameRules_getOptimizedFieldValue(name);
            Preconditions.checkState(existingValue != null, "game rule %s has its field set to null!", name);
            if (existingValue == ppatches_optimizeGameRulesAccess_dummyValue) {
                Preconditions.checkState(!this.rules.containsKey(name), "game rule %s has its field unset, but the rule exists!", name);
            } else {
                Preconditions.checkState(this.rules.containsKey(name), "game rule %s has its field set, but the rule doesn't exist!", name);
                GameRules.Value expectedValue = this.rules.get(name);
                Preconditions.checkState(existingValue == expectedValue, "game rule %s has its rule set, but the actual rule is different: %s != %s", name, existingValue, expectedValue);
            }
        }
    }

    @Unique
    @SneakyThrows
    private GameRules.Value ppatches_optimzeGameRules_getOptimizedFieldValue(String name) {
        return (GameRules.Value) MethodHandles.publicLookup().findGetter(MixinGameRules.class, name + OptimizeGameRulesAccessTransformer_GameRules.RULE_FIELD_SUFFIX, GameRules.Value.class).invokeExact((GameRules) (Object) this);
    }
}
