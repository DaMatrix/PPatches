package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.mixin;

import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntComparator;
import net.minecraft.client.util.SuffixArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * @author DaPorkchop_
 */
@Mixin(SuffixArray.class)
abstract class MixinSuffixArray_ParallelQuickSort<T> {
    private static final MethodHandle ppatches_optimzeSearchTree_ParallelQuickSort;

    static {
        MethodHandle parallelQuickSort;
        try {
            Class<?> forkJoinGenericQuickSortClass = Class.forName("it.unimi.dsi.fastutil.Arrays$ForkJoinGenericQuickSort");

            Constructor<?> forkJoinGenericQuickSort_reflectedCtor = forkJoinGenericQuickSortClass.getDeclaredConstructor(Integer.TYPE, Integer.TYPE, IntComparator.class, Swapper.class);
            forkJoinGenericQuickSort_reflectedCtor.setAccessible(true);
            MethodHandle forkJoinGenericQuickSort_ctor = MethodHandles.publicLookup().unreflectConstructor(forkJoinGenericQuickSort_reflectedCtor);

            parallelQuickSort = MethodHandles.filterReturnValue(
                    forkJoinGenericQuickSort_ctor.asType(forkJoinGenericQuickSort_ctor.type().changeReturnType(ForkJoinTask.class)),
                    MethodHandles.publicLookup().findVirtual(ForkJoinPool.class, "invoke", MethodType.methodType(Object.class, ForkJoinTask.class)).bindTo(ForkJoinPool.commonPool()));
        } catch (Exception e) {
            try {
                parallelQuickSort = MethodHandles.publicLookup().findStatic(Arrays.class, "parallelQuickSort", MethodType.methodType(Void.TYPE, Integer.TYPE, Integer.TYPE, IntComparator.class, Swapper.class));
            } catch (Exception e1) {
                e1.addSuppressed(e);
                throw new AssertionError("PPatches: vanilla.optimizeSearchTree: failed to initialize", e1);
            }
        }
        ppatches_optimzeSearchTree_ParallelQuickSort = parallelQuickSort;
    }

    @Redirect(method = "Lnet/minecraft/client/util/SuffixArray;generate()V",
            at = @At(value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/Arrays;quickSort(IILit/unimi/dsi/fastutil/ints/IntComparator;Lit/unimi/dsi/fastutil/Swapper;)V"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_generate_parallelQuickSort(int from, int to, IntComparator comp, Swapper swapper) throws Throwable {
        Object unused = ppatches_optimzeSearchTree_ParallelQuickSort.invokeExact(from, to, comp, swapper);
    }
}
