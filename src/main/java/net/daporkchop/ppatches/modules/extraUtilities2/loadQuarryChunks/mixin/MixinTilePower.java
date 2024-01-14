package net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Dummy mixin required by {@link MixinTileQuarry} to add {@link Shadow} elements and extend from the actual superclass.
 *
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.tile.TilePower", remap = false)
abstract class MixinTilePower extends MixinXUTile {
}
