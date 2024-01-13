package net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType.util;

import net.minecraft.tileentity.TileEntity;

/**
 * @author DaPorkchop_
 */
public interface IMixinTileEntity_GroupTileEntityUpdatesByType {
    Object ppatches_groupTileEntityUpdatesByType_currList();

    void ppatches_groupTileEntityUpdatesByType_currList(Object list);
    
    TileEntity ppatches_groupTileEntityUpdatesByType_prevTickable();

    void ppatches_groupTileEntityUpdatesByType_prevTickable(TileEntity tileEntity);
    
    TileEntity ppatches_groupTileEntityUpdatesByType_nextTickable();

    void ppatches_groupTileEntityUpdatesByType_nextTickable(TileEntity tileEntity);
}
