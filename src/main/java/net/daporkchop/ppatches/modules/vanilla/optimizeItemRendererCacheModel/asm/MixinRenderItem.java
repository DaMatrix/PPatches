package net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.asm;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.ItemDisplayListCacheKey;
import net.daporkchop.ppatches.util.client.render.DrawableVertexBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

/**
 * @author DaPorkchop_
 */
@Mixin(RenderItem.class)
abstract class MixinRenderItem {
    @Unique
    private Object2ObjectLinkedOpenHashMap<ItemDisplayListCacheKey, DrawableVertexBuffer> forgeLitItemVertexBufferCache;
    @Unique
    private Object2ObjectLinkedOpenHashMap<ItemDisplayListCacheKey, DrawableVertexBuffer> vanillaItemVertexBufferCache;

    @Inject(method = "<init>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeModelRendererDisplayLists_$init$_createDisplayListCache(CallbackInfo ci) {
        this.forgeLitItemVertexBufferCache = new Object2ObjectLinkedOpenHashMap<>();
        this.vanillaItemVertexBufferCache = new Object2ObjectLinkedOpenHashMap<>();
    }

    @Inject(method = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;renderLitItem(Lnet/minecraft/client/renderer/RenderItem;Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V"),
            cancellable = true,
            allow = 1, require = 0) //this injection point will fail if OptiFine is present
    private void ppatches_optimizeModelRendererDisplayLists_renderModel_tryDrawForgeLitFromBuffer(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        DrawableVertexBuffer vertexBuffer = this.forgeLitItemVertexBufferCache.getAndMoveToFirst(ItemDisplayListCacheKey.forGet(model, color, stack));
        if (vertexBuffer != null) {
            vertexBuffer.draw();
            ci.cancel();
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;renderLitItem(Lnet/minecraft/client/renderer/RenderItem;Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
                    shift = At.Shift.AFTER),
            allow = 1, require = 0) //this injection point will fail if OptiFine is present
    private void ppatches_optimizeModelRendererDisplayLists_renderModel_saveLitItemToBuffer(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        //check if the model could contain any custom lighting data; if so we can't cache it using the current system.
        //  this is okay performance-wise because only a small fraction of items will actually use emissive textures, so they'll only pass through this code once.
        List<BakedQuad> allquads = new ArrayList<>();
        for (EnumFacing enumfacing : EnumFacing.VALUES) {
            allquads.addAll(model.getQuads(null, enumfacing, 0));
        }
        allquads.addAll(model.getQuads(null, null, 0));

        for (BakedQuad q : allquads) {
            if (q.getFormat() != DefaultVertexFormats.ITEM && q.getFormat().hasUvOffset(1)) { //the quad contains light data
                return;
            } else if (!q.shouldApplyDiffuseLighting()) {
                return;
            }
        }

        this.forgeLitItemVertexBufferCache.put(ItemDisplayListCacheKey.forPut(model, color, stack),
                DrawableVertexBuffer.fromResetBuffer(Tessellator.getInstance().getBuffer()));

        while (this.forgeLitItemVertexBufferCache.size() > 4096) {
            this.forgeLitItemVertexBufferCache.removeLast().dispose();
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;getInstance()Lnet/minecraft/client/renderer/Tessellator;"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_optimizeModelRendererDisplayLists_renderModel_tryDrawVanillaFromBuffer(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        DrawableVertexBuffer vertexBuffer = this.vanillaItemVertexBufferCache.getAndMoveToFirst(ItemDisplayListCacheKey.forGet(model, color, stack));
        if (vertexBuffer != null) {
            vertexBuffer.draw();
            ci.cancel();
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private void ppatches_optimizeModelRendererDisplayLists_renderModel_saveVanillaToBuffer(IBakedModel model, int color, ItemStack stack, CallbackInfo ci, Tessellator tessellator, BufferBuilder builder) {
        this.vanillaItemVertexBufferCache.put(ItemDisplayListCacheKey.forPut(model, color, stack), DrawableVertexBuffer.fromUnfinishedBuffer(builder));

        while (this.vanillaItemVertexBufferCache.size() > 4096) {
            this.vanillaItemVertexBufferCache.removeLast().dispose();
        }
    }
}
