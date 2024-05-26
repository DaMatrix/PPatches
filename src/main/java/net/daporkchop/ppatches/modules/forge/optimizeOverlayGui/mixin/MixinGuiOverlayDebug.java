package net.daporkchop.ppatches.modules.forge.optimizeOverlayGui.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(GuiOverlayDebug.class)
abstract class MixinGuiOverlayDebug {
    @Unique
    private long ppatches_optimizeOverlayGui_maxMemoryCache;

    @Unique
    private String[] ppatches_optimizeOverlayGui_glStringCache;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeOverlayGui_$init$_populateCachedValues(Minecraft mc, CallbackInfo ci) {
        this.ppatches_optimizeOverlayGui_maxMemoryCache = Runtime.getRuntime().maxMemory();
        this.ppatches_optimizeOverlayGui_glStringCache = new String[]{
                GlStateManager.glGetString(7936),
                GlStateManager.glGetString(7937),
                GlStateManager.glGetString(7938),
        };
    }

    @Redirect(method = "getDebugInfoRight()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Runtime;maxMemory()J", remap = false),
            allow = 1, require = 1)
    private long ppatches_optimizeOverlayGui_getDebugInfoRight_cacheMaxMemory(Runtime runtime) {
        return this.ppatches_optimizeOverlayGui_maxMemoryCache;
    }

    @Redirect(method = "getDebugInfoRight()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;glGetString(I)Ljava/lang/String;"),
            allow = 3, require = 3)
    private String ppatches_optimizeOverlayGui_getDebugInfoRight_getGlStringCached(int glId) {
        return this.ppatches_optimizeOverlayGui_glStringCache[glId - 7936];
    }
}
