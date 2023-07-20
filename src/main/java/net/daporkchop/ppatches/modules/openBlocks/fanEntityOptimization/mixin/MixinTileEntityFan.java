package net.daporkchop.ppatches.modules.openBlocks.fanEntityOptimization.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openblocks.common.tileentity.TileEntityFan", remap = false, priority = 999)
abstract class MixinTileEntityFan extends MixinSyncedTileEntity {
    private static final MethodHandle CONFIG_FANRANGE_GETTER;

    static {
        try {
            Class<?> configClass = Class.forName("openblocks.Config");

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            CONFIG_FANRANGE_GETTER = lookup.findStaticGetter(configClass, "fanRange", double.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("PPatches: openblocks.fanEntityOptimization failed to initialize", e);
        }
    }

    @Dynamic
    @Shadow
    @Final
    private static double CONE_HALF_APERTURE;

    //FanEntityOptimizationTransformer will call this from a lambda using invokedynamic
    private static boolean isPushableEntity(Entity entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !player.isSpectator() //this check is inherited from EntitySelectors.NOT_SPECTATING, which is used by default
                   && !player.capabilities.isCreativeMode;
        }

        return true;
    }

    @Dynamic
    @Inject(
            method = {
                    "Lopenblocks/common/tileentity/TileEntityFan;update()V",
                    "Lopenblocks/common/tileentity/TileEntityFan;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At(value = "INVOKE_ASSIGN",
                    target = "Ljava/lang/Math;toRadians(D)D"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_fanEntityOptimization_fasterUpdateLoopInjector(CallbackInfo ci, float redstonePower, double maxForce, List<Entity> entities, double angle) throws Throwable {
        //copy main loop body into separate method to allow this method to be inlined into the injection point and allow JIT to optimize away the CallbackInfo allocation
        this.ppatches_fanEntityOptimization_fasterUpdateLoop(entities, maxForce, angle);

        ci.cancel();
    }

    @Unique
    private void ppatches_fanEntityOptimization_fasterUpdateLoop(List<Entity> entities, double maxForce, double angle) throws Throwable {
        BlockPos pos = this.pos;
        double posX = pos.getX();
        double posY = pos.getY();
        double posZ = pos.getZ();

        double fanRange = (double) CONFIG_FANRANGE_GETTER.invokeExact();
        double reciprocalFanRange = 1.0d / fanRange;

        double angleSin = Math.sin(angle);
        double angleCos = Math.cos(angle);

        //Vec3d blockPos = getConeApex(angle);
        double blockPosX = posX + 0.5d - angleCos * 1.1d;
        double blockPosY = posY + 0.5d;
        double blockPosZ = posZ + 0.5d - angleSin * 1.1d;

        //Vec3d basePos = getConeBaseCenter(angle);
        double basePosX = posX + angleCos * fanRange;
        double basePosY = posY + 0.5d;
        double basePosZ = posZ + angleSin * fanRange;

        //Vec3d coneAxis = new Vec3d(basePos.x - blockPos.x, basePos.y - blockPos.y, basePos.z - blockPos.z);
        double coneAxisX = basePosX - blockPosX;
        double coneAxisY = basePosY - blockPosY;
        double coneAxisZ = basePosZ - blockPosZ;
        double coneAxisInverseLength = MathHelper.fastInvSqrt(coneAxisX * coneAxisX + coneAxisY * coneAxisY + coneAxisZ * coneAxisZ);

        for (Entity entity : entities) {
            //Vec3d directionVec = new Vec3d(entity.posX - blockPos.x, entity.posY - blockPos.y, entity.posZ - blockPos.z);
            double directionVecX = entity.posX - blockPosX;
            double directionVecY = entity.posY - blockPosY;
            double directionVecZ = entity.posZ - blockPosZ;
            double directionVecLengthSq = directionVecX * directionVecX + directionVecY * directionVecY + directionVecZ * directionVecZ;
            double directionVecInverseLength = MathHelper.fastInvSqrt(directionVecLengthSq);

            double coneAxis_dot_directionVec = coneAxisX * directionVecX + coneAxisY * directionVecY + coneAxisZ * directionVecZ;

            //branchless implementation (seems to be slower)
            //if (isLyingInSphericalCone(coneAxisX, coneAxisY, coneAxisZ, directionVecX, directionVecY, directionVecZ)) {
            /*double initialFactor = Math.max(Math.signum(coneAxis_dot_directionVec * (coneAxisInverseLength * directionVecInverseLength) - Math.cos(CONE_HALF_APERTURE)), 0.0d);

            //final double distToOrigin = directionVec.lengthVector();
            double distToOrigin = Math.sqrt(directionVecLengthSq);

            //final double force = (1.0 - distToOrigin / Config.fanRange) * maxForce;
            double force = (1.0d - distToOrigin * reciprocalFanRange) * maxForce;

            //if (force <= 0) continue;
            force = Math.max(force, 0.0d); //should be able to be compiled into an SSE max instruction, i think

            //Vec3d normal = directionVec.normalize();
            //entity.motionX += force * normal.x;
            //entity.motionZ += force * normal.z;
            double scaledForce = initialFactor * force * directionVecInverseLength;
            entity.motionX += scaledForce * directionVecX;
            entity.motionZ += scaledForce * directionVecZ;*/

            //if (isLyingInSphericalCone(coneAxisX, coneAxisY, coneAxisZ, directionVecX, directionVecY, directionVecZ)) {
            if (coneAxis_dot_directionVec * (coneAxisInverseLength * directionVecInverseLength) > Math.cos(CONE_HALF_APERTURE)) {
                //final double distToOrigin = directionVec.lengthVector();
                double distToOrigin = Math.sqrt(directionVecLengthSq);

                //final double force = (1.0 - distToOrigin / Config.fanRange) * maxForce;
                double force = (1.0d - distToOrigin * reciprocalFanRange) * maxForce;

                //if (force <= 0) continue;
                if (force <= 0.0d) {
                    continue;
                }

                //Vec3d normal = directionVec.normalize();
                //entity.motionX += force * normal.x;
                //entity.motionZ += force * normal.z;
                double scaledForce = force * directionVecInverseLength;
                entity.motionX += scaledForce * directionVecX;
                entity.motionZ += scaledForce * directionVecZ;
            }
        }
    }
}
