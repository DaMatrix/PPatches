package net.daporkchop.ppatches.core.transform;

import org.objectweb.asm.tree.ClassNode;

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

    int transformClass(String name, String transformedName, ClassNode classNode);
}
