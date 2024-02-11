package net.daporkchop.ppatches.modules.iChunUtil.fixClientClassAccess.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "me.ichun.mods.ichunutil.client.core.event.EventHandlerClient", remap = false)
abstract class MixinEventHandlerClient {
    @Delete
    @Dynamic
    @Shadow
    public abstract void onRenderTick(TickEvent.RenderTickEvent event);
}
