package net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.mixin;

import net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.SpecializedASMEventHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

/**
 * @author DaPorkchop_
 */
@Mixin(value = EventBus.class, remap = false)
abstract class MixinEventBus {
    @Redirect(method = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;register(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/reflect/Method;Lnet/minecraftforge/fml/common/ModContainer;)V",
            at = @At(value = "NEW",
                    target = "(Ljava/lang/Object;Ljava/lang/reflect/Method;Lnet/minecraftforge/fml/common/ModContainer;Z)Lnet/minecraftforge/fml/common/eventhandler/ASMEventHandler;"),
            allow = 1, require = 1)
    private ASMEventHandler ppatches_optimizeAsmEventHandler_register_redirectAsmEventHandlerCtor(Object target, Method method, ModContainer owner, boolean isGeneric) throws Throwable {
        return SpecializedASMEventHandler.specialize(target, method, owner, isGeneric);
    }
}
