package vazkii.quark.base.asm;

import org.spongepowered.asm.transformers.MixinClassWriter;

/**
 * This is an absolutely hilarious way to overwrite an inner class used by the Quark transformer. For some reason it recursively loads classes to resolve the
 * common superclass, which breaks everything in horrible ways when mixin tries to inspect mixin target classes.
 *
 * @author DaPorkchop_
 */
public class ClassTransformer$SafeClassWriter extends MixinClassWriter {
    public ClassTransformer$SafeClassWriter(int flags) {
        super(flags);
    }
}
