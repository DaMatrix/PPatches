package net.daporkchop.ppatches.modules.vanilla.useFasterRandom;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.daporkchop.ppatches.util.UnsafeWrapper;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of {@link Random} which is implemented the same, but is not thread-safe.
 *
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FasterJavaRandom extends Random {
    private static final boolean DEBUG = FasterJavaRandom.class.desiredAssertionStatus();

    //hard-coded constants are copied from the javadocs for java.util.Random

    private static final long RANDOM_haveNextNextGaussian_OFFSET = UnsafeWrapper.objectFieldOffset(Random.class, "haveNextNextGaussian");

    public static FasterJavaRandom newInstance() {
        return newInstance(ThreadLocalRandom.current().nextLong());
    }

    public static FasterJavaRandom newInstance(long seed) {
        if (DEBUG) {
            return new FasterJavaRandom(seed);
        }

        //use unsafe to create a new instance, in order to avoid allocating a new AtomicLong in the java.util.Random constructor
        FasterJavaRandom instance = UnsafeWrapper.allocateInstance(FasterJavaRandom.class);
        instance.setSeed(seed);
        return instance;
    }

    private FasterJavaRandom(long seed) {
        super(seed);
    }

    private long seed;

    @Override
    public void setSeed(long seed) {
        if (DEBUG) {
            super.setSeed(seed);
        }

        this.seed = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        UnsafeWrapper.putBoolean(this, RANDOM_haveNextNextGaussian_OFFSET, false);
    }

    @Override
    protected int next(int bits) {
        int result = (int) ((this.seed = (this.seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1)) >>> (48 - bits));

        if (DEBUG && result != super.next(bits)) {
            throw new AssertionError();
        }

        return result;
    }

    //the following methods are copied here to ensure the call sites remain monomorphic and can be inlined

    @Override
    public int nextInt() {
        return this.next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }

        int r = this.next(31);
        int m = bound - 1;
        if ((bound & m) == 0)  // i.e., bound is a power of 2
            r = (int) ((bound * (long) r) >> 31);
        else {
            for (int u = r; u - (r = u % bound) + m < 0; u = this.next(31)) {
                //empty body
            }
        }
        return r;
    }

    @Override
    public long nextLong() {
        return ((long) (this.next(32)) << 32) + this.next(32);
    }

    @Override
    public boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    public float nextFloat() {
        return this.next(24) / ((float) (1 << 24));
    }

    @Override
    public double nextDouble() {
        return (((long) this.next(26) << 27) + this.next(27)) / (double) (1L << 53);
    }
}
