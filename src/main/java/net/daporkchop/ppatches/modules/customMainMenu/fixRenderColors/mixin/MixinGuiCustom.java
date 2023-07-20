package net.daporkchop.ppatches.modules.customMainMenu.fixRenderColors.mixin;

import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "lumien.custommainmenu.gui.GuiCustom", remap = false)
public abstract class MixinGuiCustom {
    @Dynamic
    @Redirect(method = "*",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glColor3f(FFF)V"),
            allow = -1, require = 4)
    private void ppatches_fixRenderColors_useGlStateManager(float r, float g, float b) {
        GlStateManager.color(r, g, b);
    }
}
