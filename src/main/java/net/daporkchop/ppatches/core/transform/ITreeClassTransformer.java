package net.daporkchop.ppatches.core.transform;

import org.objectweb.asm.tree.ClassNode;

/**
 * @author DaPorkchop_
 */
public interface ITreeClassTransformer {
    default boolean interestedInClass(String name, String transformedName) {
        return true;
    }

    boolean transformClass(String name, String transformedName, ClassNode classNode);
}
