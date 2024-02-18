package net.daporkchop.ppatches.core.transform;

import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/**
 * @author DaPorkchop_
 */
public interface ITreeClassTransformer extends Comparable<ITreeClassTransformer> {
    int CHANGED = 1;
    int CHANGED_MANDATORY = 2 | CHANGED;

    default int priority() { //higher values come last
        return 1000;
    }

    @Override
    default int compareTo(ITreeClassTransformer o) {
        int d = Integer.compare(this.priority(), o.priority());
        if (d == 0) {
            d = Boolean.compare(this instanceof IndividualMethod, o instanceof IndividualMethod);
            if (d == 0) {
                d = Boolean.compare(this instanceof OptimizationPass, o instanceof OptimizationPass);
            }
        }
        return d;
    }

    default boolean interestedInClass(String name, String transformedName) {
        return true;
    }

    int transformClass(String name, String transformedName, ClassNode classNode);

    /**
     * Indicates that an {@link ITreeClassTransformer} is able to do a more precise check to determine whether or not it's interested in a class
     * by checking if specific values are present in the class' constant pool.
     *
     * @author DaPorkchop_
     */
    interface ExactInterested {
        /**
         * @return a bitwise OR combination of flags from {@link ConstantPoolIndex} which will be used when constructing the constant pool index
         */
        default int cpIndexFlags() {
            return 0;
        }

        boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex);
    }

    /**
     * Marker interface to indicate that a transformer is an optimization pass, and may be repeatedly invoked until no more changes are made.
     * <p>
     * Such transformers must only return {@code 0} or {@link #CHANGED}.
     *
     * @author DaPorkchop_
     */
    interface OptimizationPass {
    }

    interface IndividualMethod extends ITreeClassTransformer {
        default boolean interestedInMethod(String className, String classTransformedName, MethodNode methodNode) {
            return true;
        }

        @Override
        default int transformClass(String name, String transformedName, ClassNode classNode) {
            int changeFlags = 0;
            boolean optimization = this instanceof OptimizationPass;

            for (MethodNode methodNode : classNode.methods) {
                if (!this.interestedInMethod(name, transformedName, methodNode)) {
                    continue;
                }

                int transformResult;
                do {
                    transformResult = this.transformMethod(name, transformedName, classNode, methodNode, methodNode.instructions);
                    changeFlags |= transformResult;
                } while (optimization && transformResult != 0); //if this is an optimization pass, loop until no more changes can be applied
            }
            return changeFlags;
        }

        int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions);

        interface Analyzed extends IndividualMethod {
            @Override
            default int transformClass(String name, String transformedName, ClassNode classNode) {
                int changeFlags = 0;
                boolean optimization = this instanceof OptimizationPass;

                for (MethodNode methodNode : classNode.methods) {
                    if (!this.interestedInMethod(name, transformedName, methodNode)) {
                        continue;
                    }

                    try (AnalyzedInsnList analyzedList = new AnalyzedInsnList(classNode.name, methodNode)) {
                        int transformResult;
                        do {
                            transformResult = this.transformMethod(name, transformedName, classNode, methodNode, analyzedList);
                            changeFlags |= transformResult;
                        } while (optimization && transformResult != 0); //if this is an optimization pass, loop until no more changes can be applied
                    }
                }
                return changeFlags;
            }

            @Override
            default int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
                try (AnalyzedInsnList analyzedList = new AnalyzedInsnList(classNode.name, methodNode)) {
                    return this.transformMethod(name, transformedName, classNode, methodNode, analyzedList);
                }
            }

            int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions);
        }
    }
}
