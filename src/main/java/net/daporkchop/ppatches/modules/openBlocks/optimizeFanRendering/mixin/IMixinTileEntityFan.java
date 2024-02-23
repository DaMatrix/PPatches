package net.daporkchop.ppatches.modules.openBlocks.optimizeFanRendering.mixin;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openblocks.common.tileentity.TileEntityFan", remap = false)
public interface IMixinTileEntityFan {
    @Dynamic
    @Invoker
    float callGetAngle();

    @Dynamic
    @Invoker
    float callGetBladeRotation(float partialTickTime);
}
