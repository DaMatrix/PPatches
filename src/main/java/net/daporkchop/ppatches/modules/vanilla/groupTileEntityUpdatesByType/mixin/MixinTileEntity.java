package net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType.mixin;

import net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType.util.IMixinTileEntity_GroupTileEntityUpdatesByType;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * @author DaPorkchop_
 */
@Mixin(TileEntity.class)
abstract class MixinTileEntity implements IMixinTileEntity_GroupTileEntityUpdatesByType {
    @Unique
    private Object ppatches_groupTileEntityUpdatesByType_currList;
    @Unique
    private TileEntity ppatches_groupTileEntityUpdatesByType_prevTickable;
    @Unique
    private TileEntity ppatches_groupTileEntityUpdatesByType_nextTickable;

    @Override
    public final Object ppatches_groupTileEntityUpdatesByType_currList() {
        return this.ppatches_groupTileEntityUpdatesByType_currList;
    }

    @Override
    public final void ppatches_groupTileEntityUpdatesByType_currList(Object list) {
        this.ppatches_groupTileEntityUpdatesByType_currList = list;
    }

    @Override
    public final TileEntity ppatches_groupTileEntityUpdatesByType_prevTickable() {
        return this.ppatches_groupTileEntityUpdatesByType_prevTickable;
    }

    @Override
    public final void ppatches_groupTileEntityUpdatesByType_prevTickable(TileEntity tileEntity) {
        this.ppatches_groupTileEntityUpdatesByType_prevTickable = tileEntity;
    }

    @Override
    public final TileEntity ppatches_groupTileEntityUpdatesByType_nextTickable() {
        return this.ppatches_groupTileEntityUpdatesByType_nextTickable;
    }

    @Override
    public final void ppatches_groupTileEntityUpdatesByType_nextTickable(TileEntity tileEntity) {
        this.ppatches_groupTileEntityUpdatesByType_nextTickable = tileEntity;
    }
}
