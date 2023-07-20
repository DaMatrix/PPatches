package net.daporkchop.ppatches.core.transform;

import org.objectweb.asm.ClassReader;
import org.spongepowered.asm.transformers.MixinClassWriter;

/**
 * @author DaPorkchop_
 */
public class PPatchesClassWriter extends MixinClassWriter {
    public PPatchesClassWriter(int flags) {
        super(flags);
    }

    public PPatchesClassWriter(ClassReader classReader, int flags) {
        super(classReader, flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (Exception e) {
            throw new UnknownCommonSuperClassException(e, type1, type2);
        }
    }

    public static class UnknownCommonSuperClassException extends RuntimeException {
        public final String type1;
        public final String type2;

        public UnknownCommonSuperClassException(Throwable cause, String type1, String type2) {
            super("Unable to determine common superclass of " + type1 + " and " + type2, cause);
            this.type1 = type1;
            this.type2 = type2;
        }
    }
}
