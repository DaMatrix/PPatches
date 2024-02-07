package net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.mixin;

import net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.SpecializedASMEventHandler;
import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Modifier;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ASMEventHandler.class, remap = false)
abstract class MixinASMEventHandler {
    @Unique
    private MethodHandle ppatches_optimizeAsmEventHandler_invokerHandle;

    @Redirect(method = "Lnet/minecraftforge/fml/common/eventhandler/ASMEventHandler;<init>(Ljava/lang/Object;Ljava/lang/reflect/Method;Lnet/minecraftforge/fml/common/ModContainer;Z)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/reflect/Modifier;isStatic(I)Z"),
            allow = 1, require = 1)
    private boolean ppatches_optimizeAsmEventHandler_$init$_alwaysUseStaticVersionForSpecializedVariant(int mod) {
        return Modifier.isStatic(mod) || SpecializedASMEventHandler.class.isInstance(this);
    }

    @Redirect(method = "Lnet/minecraftforge/fml/common/eventhandler/ASMEventHandler;<init>(Ljava/lang/Object;Ljava/lang/reflect/Method;Lnet/minecraftforge/fml/common/ModContainer;Z)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Class;newInstance()Ljava/lang/Object;"),
            allow = 1, require = 1)
    private Object ppatches_optimizeAsmEventHandler_$init$_dontCreateInstanceForSpecializedVariant(Class<?> clazz) throws Exception {
        return clazz == null ? null : clazz.newInstance();
    }
}
