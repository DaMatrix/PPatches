package net.daporkchop.ppatches.modules.vanilla.disableMutableBlockPosPooling.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * @author DaPorkchop_
 */
@Mixin(BlockPos.PooledMutableBlockPos.class)
abstract class MixinPooledMutableBlockPos_RemovePool extends BlockPos.MutableBlockPos {
    @Delete
    @Shadow
    private boolean released;

    @Delete(removeStaticInitializer = true)
    @Shadow
    @Final
    private static List<PooledMutableBlockPos> POOL;

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite
    public static BlockPos.PooledMutableBlockPos retain(int xIn, int yIn, int zIn) {
        return new BlockPos.PooledMutableBlockPos(xIn, yIn, zIn);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite
    public void release() {
        //no-op
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite
    public BlockPos.PooledMutableBlockPos setPos(int xIn, int yIn, int zIn) {
        return (BlockPos.PooledMutableBlockPos) super.setPos(xIn, yIn, zIn);
    }

    @Delete
    @Shadow
    public abstract BlockPos.MutableBlockPos setPos(double xIn, double yIn, double zIn);
}
