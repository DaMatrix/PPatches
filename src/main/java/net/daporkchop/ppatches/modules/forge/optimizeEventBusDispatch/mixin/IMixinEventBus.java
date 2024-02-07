package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.mixin;

import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @author DaPorkchop_
 */
@Mixin(EventBus.class)
public interface IMixinEventBus {
    @Accessor
    int getBusID();
}
