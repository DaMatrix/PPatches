package net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.mixin;

import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ASMEventHandler.class, remap = false)
public interface IMixinASMEventHandler {
    @Accessor
    static boolean getGETCONTEXT() {
        throw new AssertionError();
    }
}
