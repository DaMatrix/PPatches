package net.daporkchop.ppatches.modules.openBlocks.fanUpdateBatching.mixin;

import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Dummy mixin required by {@link MixinTileEntityFan} to add {@link Shadow} elements and extend from the actual superclass.
 *
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openmods.tileentity.OpenTileEntity", remap = false)
abstract class MixinOpenTileEntity extends TileEntity {
}
