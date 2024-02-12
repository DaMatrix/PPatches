package net.daporkchop.ppatches.modules.forge.optimizeBlockCaptureDrops.mixin;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.InternalThreadLocalMap;
import net.daporkchop.ppatches.modules.forge.optimizeBlockCaptureDrops.ChainedNonNullList;
import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Instead of having two thread-locals (one to indicate whether or not we're currently capturing drops, and one containing the list of captured drops), I've
 * opted for a different approach where I simply have a single thread-local list which is {@code null} when drops aren't being captured. This allows us to
 * get a performance improvement from not having to clear the list every time we want to re-use it (which turns out to add some pretty substantial overhead
 * in certain edge cases), but also allows us to avoid multiple {@link ThreadLocal} lookups per item (which aren't particularly cheap).
 * <p>
 * Additionally, the lists are now also linked together into a stack, which allows us to capture drops recursively (which the Forge implementation didn't
 * support, and adds virtually no overhead).
 *
 * @author DaPorkchop_
 */
@Mixin(Block.class)
abstract class MixinBlock {
    @Delete(removeStaticInitializer = true)
    @Shadow(remap = false)
    protected static ThreadLocal<Boolean> captureDrops;

    @Delete(removeStaticInitializer = true)
    @Shadow(remap = false)
    protected static ThreadLocal<NonNullList<ItemStack>> capturedDrops;

    @Unique
    private static final FastThreadLocal<ChainedNonNullList<ItemStack>> ppatches_optimizeBlockCaptureDrops_capturedDrops = new FastThreadLocal<>();

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite(remap = false)
    protected NonNullList<ItemStack> captureDrops(boolean start) {
        if (start) {
            return ppatches_optimizeBlockCaptureDrops_startCapturing();
        } else {
            return ppatches_optimizeBlockCaptureDrops_stopCapturing();
        }
    }

    @Unique
    private static NonNullList<ItemStack> ppatches_optimizeBlockCaptureDrops_startCapturing() {
        InternalThreadLocalMap internalThreadLocalMap = InternalThreadLocalMap.get();
        ChainedNonNullList<ItemStack> previousCapturingDrops = ppatches_optimizeBlockCaptureDrops_capturedDrops.get(internalThreadLocalMap);
        ppatches_optimizeBlockCaptureDrops_capturedDrops.set(internalThreadLocalMap, new ChainedNonNullList<>(previousCapturingDrops));
        return null;
    }

    @Unique
    private static NonNullList<ItemStack> ppatches_optimizeBlockCaptureDrops_stopCapturing() {
        InternalThreadLocalMap internalThreadLocalMap = InternalThreadLocalMap.get();
        ChainedNonNullList<ItemStack> capturedDrops = ppatches_optimizeBlockCaptureDrops_capturedDrops.get(internalThreadLocalMap);
        ppatches_optimizeBlockCaptureDrops_capturedDrops.set(internalThreadLocalMap, capturedDrops.successor);
        capturedDrops.successor = null;
        return capturedDrops;
    }

    @Inject(method = "spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/block/Block;captureDrops:Ljava/lang/ThreadLocal;", remap = false,
                    opcode = Opcodes.GETSTATIC),
            cancellable = true,
            allow = 1, require = 1)
    private static void ppatches_optimizeBlockCaptureDrops_spawnAsEntity_doOptimizedCaptureDropsCheck(World worldIn, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        NonNullList<ItemStack> capturedDrops = ppatches_optimizeBlockCaptureDrops_capturedDrops.get();
        if (capturedDrops != null) {
            capturedDrops.add(stack);
            ci.cancel();
        }
    }

    @Redirect(method = "spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/block/Block;captureDrops:Ljava/lang/ThreadLocal;", remap = false,
                    opcode = Opcodes.GETSTATIC),
            allow = 1, require = 1)
    private static ThreadLocal<?> ppatches_optimizeBlockCaptureDrops_spawnAsEntity_dontAccessCaptureDrops() {
        return null;
    }

    @Redirect(method = "spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/block/Block;capturedDrops:Ljava/lang/ThreadLocal;", remap = false,
                    opcode = Opcodes.GETSTATIC),
            allow = 1, require = 1)
    private static ThreadLocal<?> ppatches_optimizeBlockCaptureDrops_spawnAsEntity_dontAccessCapturedDrops() {
        return null;
    }

    @Redirect(method = "spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;"),
            allow = 2, require = 2)
    private static Object ppatches_optimizeBlockCaptureDrops_spawnAsEntity_dontGetThreadLocals(ThreadLocal<?> tl) {
        return null;
    }

    @Redirect(method = "spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Boolean;booleanValue()Z"),
            allow = 1, require = 1)
    private static boolean ppatches_optimizeBlockCaptureDrops_spawnAsEntity_skipOriginalCaptureDropsBranch(Boolean value) {
        return false;
    }

    /*@Redirect(method = "spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/NonNullList;add(Ljava/lang/Object;)Z"),
            allow = 1, require = 1)
    private static boolean ppatches_optimizeBlockCaptureDrops_spawnAsEntity_dontAddToCapturedDropsList(NonNullList<?> list, Object stack) {
        return false;
    }*/
}
