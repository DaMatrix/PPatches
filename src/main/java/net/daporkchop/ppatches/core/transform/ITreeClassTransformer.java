package net.daporkchop.ppatches.core.transform;

import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import org.objectweb.asm.tree.ClassNode;
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
     * Marker interface to indicate that a transformer is an optimization pass, and may be repeatedly invoked until no more changes are made.
     * <p>
     * Such transformers must only return {@code 0} or {@link #CHANGED}.
     *
     * @author DaPorkchop_
     */
    interface OptimizationPass {
    }

    interface IndividualMethod extends ITreeClassTransformer {
        default boolean interestedInMethod(String className, String classTransformedName, MethodNode method) {
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
                    transformResult = this.transformMethod(name, transformedName, classNode, methodNode);
                    changeFlags |= transformResult;
                } while (optimization && transformResult != 0); //if this is an optimization pass, loop until no more changes can be applied
            }
            return changeFlags;
        }

        int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode);

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
            default int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode) {
                try (AnalyzedInsnList analyzedList = new AnalyzedInsnList(classNode.name, methodNode)) {
                    return this.transformMethod(name, transformedName, classNode, methodNode, analyzedList);
                }
            }

            int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions);
        }
    }
}
