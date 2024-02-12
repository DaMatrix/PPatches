package net.daporkchop.ppatches.modules.vanilla.optimizeResourceLocationComparison.mixin;

import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Locale;

/**
 * @author DaPorkchop_
 */
@Mixin(ResourceLocation.class)
abstract class MixinResourceLocation {
    @Shadow
    @Final
    protected String namespace;

    @Shadow
    @Final
    protected String path;

    @Redirect(method = "<init>(I[Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/String;toLowerCase(Ljava/util/Locale;)Ljava/lang/String;"),
            allow = 2, require = 2)
    private String ppatches_optimizeResourceLocationComparison_$init$_internStrings(String path, Locale locale) {
        return path.toLowerCase(locale).intern();
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite
    public boolean equals(Object obj) {
        if (!(obj instanceof ResourceLocation)) {
            return false;
        }
        MixinResourceLocation other = (MixinResourceLocation) obj;

        //we can get away with only comparing object identity here because the namespace and path are always interned
        //noinspection StringEquality
        return this.namespace == other.namespace && this.path == other.path;
    }
}
