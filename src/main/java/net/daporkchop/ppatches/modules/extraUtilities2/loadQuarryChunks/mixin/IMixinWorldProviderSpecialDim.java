package net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.mixin;

import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.dimensions.workhousedim.WorldProviderSpecialDim", remap = false)
public interface IMixinWorldProviderSpecialDim {
    @Dynamic
    @Invoker
    static WorldServer callGetWorld() {
        throw new AssertionError();
    }
}
