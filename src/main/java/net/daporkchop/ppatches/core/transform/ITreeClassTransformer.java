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
        return Integer.compare(this.priority(), o.priority());
    }

    default boolean interestedInClass(String name, String transformedName) {
        return true;
    }

    //TODO: removed transformedName
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
        @Override
        default int transformClass(String name, String transformedName, ClassNode classNode) {
            int changedFlags = 0;
            boolean optimization = this instanceof OptimizationPass;

            for (MethodNode methodNode : classNode.methods) {
                try (AnalyzedInsnList analyzedList = new AnalyzedInsnList(classNode.name, methodNode)) {
                    int transformResult;
                    do {
                        transformResult = this.transformMethod(name, classNode, methodNode, analyzedList);
                        changedFlags |= transformResult;
                    } while (optimization && transformResult != 0); //if this is an optimization pass, loop until no more changes can be applied
                } catch (UnsupportedOperationException e) { //TODO: this is a gross hack
                    if (!optimization) { //optimization passes don't need to worry about stuff failing
                        throw e;
                    }
                }
            }
            return changedFlags;
        }

        int transformMethod(String name, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions);
    }
}
