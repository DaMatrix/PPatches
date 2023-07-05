package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.asm.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Mixin({
        net.minecraft.block.Block.class,
        net.minecraft.inventory.InventoryHelper.class,
        net.minecraft.item.Item.class,
        net.minecraft.server.network.NetHandlerLoginServer.class,
        net.minecraft.tileentity.TileEntityDispenser.class,
        net.minecraft.tileentity.TileEntityEnchantmentTable.class,
        net.minecraft.util.datafix.fixes.ZombieProfToType.class,
        net.minecraft.util.math.MathHelper.class,
})
abstract class Mixin_UseThreadLocalRandomInStaticInitializer_NoSeed {
    @Redirect(method = "<clinit>",
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private static Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return ThreadLocalRandom.current(); //this is safe to use here because setSeed() is never called
    }
}
