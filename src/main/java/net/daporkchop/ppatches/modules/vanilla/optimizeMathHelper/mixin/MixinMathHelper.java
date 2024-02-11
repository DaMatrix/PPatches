package net.daporkchop.ppatches.modules.vanilla.optimizeMathHelper.mixin;

import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * @author DaPorkchop_
 */
@Mixin(MathHelper.class)
abstract class MixinMathHelper {
    //for some reason there's no overload of MathHelper.abs which accepts a double, which is unfortunate since that's the only one for which the version in Math is actually an intrinsic

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Math#abs(float)} is likely to be optimized more aggressively than a manually written-out version of the code
     */
    @Overwrite
    public static float abs(float value) {
        return Math.abs(value);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Math#abs(int)} is likely to be optimized more aggressively than a manually written-out version of the code
     */
    @Overwrite
    public static int abs(int value) {
        return Math.abs(value);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Math#min(int, int)} and {@link Math#max(int, int)} are compiler intrinsics and are likely to be optimized more aggressively than a manually written-out version of the code
     */
    @Overwrite
    public static int clamp(int num, int min, int max) {
        return Math.min(Math.max(num, min), max);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Integer#numberOfLeadingZeros(int)} is an intrinsic and will compile into much fewer instructions than the original implementation
     */
    @Overwrite
    public static int smallestEncompassingPowerOfTwo(int value) {
        //functionally equivalent to the original code for all possible int values
        return (int) (1L << (32 - Integer.numberOfLeadingZeros(value - 1)));
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Integer#bitCount(int)} is an intrinsic and will compile into fewer instructions than the original implementation on basically all relevant microarchitectures
     */
    @Overwrite
    private static boolean isPowerOfTwo(int value) {
        //functionally equivalent to the original code for all possible int values
        return Integer.bitCount(value) == 1;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Integer#numberOfLeadingZeros(int)} is an intrinsic and will compile into much fewer instructions than the original implementation
     */
    @Overwrite
    public static int log2DeBruijn(int value) {
        //functionally equivalent to the original code for all possible int values
        return (32 - Integer.numberOfLeadingZeros(value - 1)) & 0x1F;
    }

    //TODO: test this
    /*
     * @author DaPorkchop_
     * @reason we're replacing the whole method, since {@link Math#atan2(double, double)} is an intrinsic and will almost certainly be faster and more precise than whatever this nonsense is
     */
    /*@Overwrite
    public static double atan2(double p_181159_0_, double p_181159_2_) {
        return Math.atan2(p_181159_0_, p_181159_2_);
    }*/
}
