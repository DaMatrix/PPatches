package net.daporkchop.ppatches.modules.extraUtilities2.optimizeDropsHandler.mixin;

import com.google.common.collect.HashMultimap;
import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.eventhandlers.DropsHandler", remap = false)
abstract class MixinDropsHandler {
    @Delete(removeStaticInitializer = true)
    @Dynamic
    @Shadow
    public static HashMultimap<IBlockState, Pair<ItemStack, Double>> drops2add;

    @Unique
    private static ItemStack ppatches_optimizeDropsHandler_redstoneOreStack;
    @Unique
    private static float ppatches_optimizeDropsHandler_redstoneOreProbability;
    @Unique
    private static ItemStack ppatches_optimizeDropsHandler_litRedstoneOreStack;
    @Unique
    private static float ppatches_optimizeDropsHandler_litRedstoneOreProbability;

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Dynamic
    @Overwrite
    public static void registerDrops(IBlockState state, ItemStack stack, double propability) {
        if (state == Blocks.REDSTONE_ORE.getDefaultState()) {
            ppatches_optimizeDropsHandler_redstoneOreStack = stack;
            ppatches_optimizeDropsHandler_redstoneOreProbability = (float) propability;
        } else if (state == Blocks.LIT_REDSTONE_ORE.getDefaultState()) {
            ppatches_optimizeDropsHandler_litRedstoneOreStack = stack;
            ppatches_optimizeDropsHandler_litRedstoneOreProbability = (float) propability;
        } else {
            throw new IllegalArgumentException(Objects.toString(state));
        }
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @SubscribeEvent
    @Dynamic
    @Overwrite
    public void onDrop(BlockEvent.HarvestDropsEvent event) {
        if (!event.isSilkTouching()) {
            IBlockState state = event.getState();
            if (state == Blocks.REDSTONE_ORE.getDefaultState() || state == Blocks.LIT_REDSTONE_ORE.getDefaultState()) {
                //separate method to try to ensure this method is small enough to be inlined
                ppatches_optimizeDropsHandler_addDrops(state, event.getDropChance(), event.getDrops());
            }
        }
    }

    @Unique
    private static void ppatches_optimizeDropsHandler_addDrops(IBlockState state, float dropChance, List<ItemStack> drops) {
        ItemStack item;
        float probability;
        if (state == Blocks.REDSTONE_ORE.getDefaultState()) {
            item = ppatches_optimizeDropsHandler_redstoneOreStack;
            probability = ppatches_optimizeDropsHandler_redstoneOreProbability;
        } else if (state == Blocks.LIT_REDSTONE_ORE.getDefaultState()) {
            item = ppatches_optimizeDropsHandler_litRedstoneOreStack;
            probability = ppatches_optimizeDropsHandler_litRedstoneOreProbability;
        } else {
            throw new IllegalArgumentException(Objects.toString(state));
        }

        probability *= dropChance;

        while (ThreadLocalRandom.current().nextFloat() < probability) {
            drops.add(item.copy());
        }
    }
}
