package net.daporkchop.ppatches.util.asm;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceRBTreeMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import lombok.NonNull;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author DaPorkchop_
 */
public abstract class AnonymousClassWriter extends ClassWriter {
    //this may have alternative implementations in the future to support JVMs without support for Unsafe.defineAnonymousClass

    public static AnonymousClassWriter create(int flags) {
        return new Unsafe(flags);
    }

    public static AnonymousClassWriter create(ClassReader reader, int flags) {
        return new Unsafe(reader, flags);
    }

    protected AnonymousClassWriter(int flags) {
        super(flags);
    }

    protected AnonymousClassWriter(ClassReader classReader, int flags) {
        super(classReader, flags);
    }

    /**
     * Adds the given object to the given class as a constant and adds instructions to the given {@link MethodVisitor} to load the constant onto the stack.
     *
     * @param mv  a {@link MethodVisitor} to write the class to
     * @param cst the constant value to add
     */
    public abstract void addConstant(MethodVisitor mv, @NonNull Object cst, String typeInternalName);

    /**
     * Defines this class as an anonymous class within the context of the given host class.
     *
     * @param hostClass the host class
     * @return the defined anonymous class
     */
    public abstract Class<?> defineAnonymousClass(Class<?> hostClass);

    /**
     * @author DaPorkchop_
     */
    private static final class Unsafe extends AnonymousClassWriter {
        private static final String CONSTANT_KEY_PREFIX = "##DUMMY_CONSTANT_POOL_PATCH## ";

        private final ReferenceArrayList<Object> constants = new ReferenceArrayList<>();
        private final Int2ReferenceRBTreeMap<Object> sparseCpPatches = new Int2ReferenceRBTreeMap<>();

        public Unsafe(int flags) {
            super(flags);
        }

        public Unsafe(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        public void addConstant(MethodVisitor mv, @NonNull Object cst, String typeInternalName) {
            if (cst instanceof String) { //no custom handling necessary
                mv.visitLdcInsn(cst);
                return;
            }

            int index = this.constants.indexOf(cst);
            if (index < 0) { //we haven't encountered this constant yet!
                index = this.constants.size();
                this.constants.add(cst);

                int cpIndex = super.newConst(CONSTANT_KEY_PREFIX + index);
                this.sparseCpPatches.put(cpIndex, cst);
            }

            mv.visitLdcInsn(CONSTANT_KEY_PREFIX + index);
            mv.visitTypeInsn(Opcodes.CHECKCAST, typeInternalName);
        }

        @Override
        public Class<?> defineAnonymousClass(Class<?> hostClass) {
            Object[] cpPatches = null;
            if (!this.sparseCpPatches.isEmpty()) {
                cpPatches = new Object[this.sparseCpPatches.lastIntKey() + 1];
                for (Int2ReferenceMap.Entry<Object> entry : this.sparseCpPatches.int2ReferenceEntrySet()) {
                    cpPatches[entry.getIntKey()] = entry.getValue();
                }
            }

            return UnsafeWrapper.defineAnonymousClass(hostClass, super.toByteArray(), cpPatches);
        }
    }
}
