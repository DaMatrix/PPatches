package net.daporkchop.ppatches.modules.vanilla.disableMutableBlockPosPooling.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * This deletes all of the synthetic bridge methods in {@link BlockPos.PooledMutableBlockPos} which are generated since it overrides methods from
 * {@link BlockPos.MutableBlockPos} while changing their return type. Since the overridden methods have no possible side effects (the only one which
 * does have side effects is {@link BlockPos.PooledMutableBlockPos#setPos(int, int, int)}, and those side effects are removed by {@link MixinPooledMutableBlockPos_RemovePool}),
 * we can safely remove the bridge methods. This means that the methods in {@link BlockPos.MutableBlockPos} will no longer be overridden anywhere and
 * can therefore be inlined much more aggressively.
 *
 * @author DaPorkchop_
 */
@Mixin(value = BlockPos.PooledMutableBlockPos.class, priority = 1001) //we want these transformations to be applied after the ones from the _RemovePool mixin
abstract class MixinPooledMutableBlockPos_RemoveBridges extends BlockPos.MutableBlockPos {
    @Delete
    @Shadow
    public abstract BlockPos.MutableBlockPos setPos(int xIn, int yIn, int zIn);

    @Delete
    @Shadow
    @SideOnly(Side.CLIENT)
    public abstract BlockPos.MutableBlockPos setPos(Entity entityIn);

    @Delete
    @Shadow
    public abstract BlockPos.MutableBlockPos setPos(double xIn, double yIn, double zIn);

    @Delete
    @Shadow
    public abstract BlockPos.MutableBlockPos setPos(Vec3i vec);

    @Delete
    @Shadow
    public abstract BlockPos.MutableBlockPos move(EnumFacing facing);

    @Delete
    @Shadow
    public abstract BlockPos.MutableBlockPos move(EnumFacing facing, int n);
}
