package net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Objects;

/**
 * @author DaPorkchop_
 */
@SideOnly(Side.CLIENT)
public final class ItemDisplayListCacheKey {
    public static ItemDisplayListCacheKey forGet(IBakedModel model, int color, ItemStack stack) {
        return new ItemDisplayListCacheKey(model, color, stack);
    }

    public static ItemDisplayListCacheKey forPut(IBakedModel model, int color, ItemStack stack) {
        return new ItemDisplayListCacheKey(model, color, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    private static int hashStackIgnoreCount(ItemStack stack) {
        return stack.getItem().hashCode() * 494121601 + stack.getMetadata();
    }

    private static boolean compareStackIgnoreCount(ItemStack stack1, ItemStack stack2) {
        return stack1.getItem() == stack2.getItem() && stack1.getMetadata() == stack2.getMetadata() && Objects.equals(stack1.getTagCompound(), stack2.getTagCompound());
    }

    private final IBakedModel model; //compared by reference
    private final int color;
    private final ItemStack stack;

    private transient final int hashCode;

    private ItemDisplayListCacheKey(IBakedModel model, int color, ItemStack stack) {
        this.model = model;
        this.color = color;
        this.stack = stack;

        this.hashCode = (System.identityHashCode(model) * 1246914979 + color) * 1612503517 + hashStackIgnoreCount(stack);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ItemDisplayListCacheKey)) {
            return false;
        }

        ItemDisplayListCacheKey other = (ItemDisplayListCacheKey) obj;
        return this.hashCode == other.hashCode
                && this.color == other.color
                && this.model == other.model
                && compareStackIgnoreCount(this.stack, other.stack);
    }
}
