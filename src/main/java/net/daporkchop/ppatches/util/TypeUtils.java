package net.daporkchop.ppatches.util;

import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.util.Objects;

/**
 * @author DaPorkchop_
 */
public class TypeUtils {
    public static final Type OBJECT_TYPE = Type.getType(Object.class);

    public static boolean hasSuperClass(String subClassInternalName, String superClassInternalName) {
        if (subClassInternalName.equals(superClassInternalName)) {
            return true;
        }

        ClassInfo subClassInfo = Objects.requireNonNull(ClassInfo.forName(subClassInternalName), subClassInternalName);
        ClassInfo superClassInfo = Objects.requireNonNull(ClassInfo.forName(superClassInternalName), superClassInternalName);
        boolean checkInterfaces = superClassInfo.isInterface();

        return subClassInfo.hasSuperClass(superClassInfo, ClassInfo.Traversal.ALL, checkInterfaces);
    }
}
