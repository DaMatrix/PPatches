package net.daporkchop.ppatches.modules.openBlocks.fanAngleRounding.mixin;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openblocks.common.tileentity.TileEntityFan", remap = false)
abstract class MixinTileEntityFan extends MixinSyncedTileEntity {
    private static final MethodHandle TILEENTITYFAN_SETANGLE;

    static {
        try {
            Class<?> tileEntityFanClass = Class.forName("openblocks.common.tileentity.TileEntityFan");
            Class<?> syncableFloatClass = Class.forName("openmods.sync.SyncableFloat");

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle tileEntityFan_angle_getter = lookup.findGetter(tileEntityFanClass, "angle", syncableFloatClass);
            MethodHandle syncableFloat_set = lookup.findVirtual(syncableFloatClass, "set", MethodType.methodType(void.class, float.class));
            TILEENTITYFAN_SETANGLE = MethodHandles.filterArguments(syncableFloat_set, 0, tileEntityFan_angle_getter);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("PPatches: openblocks.fanAngleRounding failed to initialize", e);
        }
    }

    @Dynamic
    @Shadow
    public abstract float getAngle();

    @Unique
    private static float ppatches_fanAngleRounding_fixAngle(float angle) {
        return (float) (Math.floorMod(Math.round(angle / 10.0f), 36) * 10);
    }

    @Unique
    @SneakyThrows
    private void ppatches_fanAngleRounding_fixAngle() {
        if (!this.world.isRemote) {
            float angle = this.getAngle();
            float fixedAngle = ppatches_fanAngleRounding_fixAngle(angle);
            if (angle != fixedAngle) {
                PPatchesMod.LOGGER.trace("Fixed angle for {} at {} (old={}°, new={}°)", this.getClass().getTypeName(), this.getPos(), angle, fixedAngle);
                TILEENTITYFAN_SETANGLE.invokeExact(this, fixedAngle);
                this.sync();
            }
        }
    }

    @Dynamic
    @ModifyArg(method = "*",
            at = @At(value = "INVOKE",
                    target = "Lopenmods/sync/SyncableFloat;set(F)V"),
            allow = 2, require = 2)
    private float ppatches_fanAngleRounding_fixAngleFromSetters(float angle) {
        return ppatches_fanAngleRounding_fixAngle(angle);
    }

    /**
     * This method serves as a dummy injection point; it will be silently discarded if another mixin targeting the same class adds the same override.
     */
    @Unique
    @Override
    public void onLoad() {
        //no-op
    }

    @Dynamic
    @Inject(method = "Lopenblocks/common/tileentity/TileEntityFan;onLoad()V", //don't need to explicitly add an obfuscated method name here, since the base method is added by Forge
            at = @At(value = "HEAD"),
            allow = 1, require = 1)
    private void ppatches_fanAngleRounding_onLoad_fixFanAngle(CallbackInfo ci) {
        this.ppatches_fanAngleRounding_fixAngle();
    }
}
