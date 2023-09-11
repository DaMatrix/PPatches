package net.daporkchop.ppatches.util.asm;

import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.util.Objects;

/**
 * @author DaPorkchop_
 */
public class TypeUtils {
    private static final Type[] PRIMITIVE_TYPES_BY_SORT = {
            Type.VOID_TYPE,
            Type.BOOLEAN_TYPE,
            Type.CHAR_TYPE,
            Type.BYTE_TYPE,
            Type.SHORT_TYPE,
            Type.INT_TYPE,
            Type.FLOAT_TYPE,
            Type.LONG_TYPE,
            Type.DOUBLE_TYPE,
    };

    private static final String[] PRIMITIVE_NAMES_BY_SORT = {
            "void",
            "boolean",
            "char",
            "byte",
            "short",
            "int",
            "float",
            "long",
            "double",
    };

    public static Type primitiveTypeBySort(int sort) {
        return PRIMITIVE_TYPES_BY_SORT[sort];
    }

    public static Type primitiveTypeByName(String primitiveName) {
        for (int sort = 0; sort < PRIMITIVE_NAMES_BY_SORT.length; sort++) {
            if (primitiveName.equals(PRIMITIVE_NAMES_BY_SORT[sort])) {
                return primitiveTypeBySort(sort);
            }
        }
        throw new IllegalArgumentException(primitiveName);
    }

    public static Type getArrayType(Type type) {
        return Type.getType('[' + type.getDescriptor());
    }

    public static boolean hasSuperClass(String subClassInternalName, String superClassInternalName) {
        if (subClassInternalName.equals(superClassInternalName)) {
            return true;
        }

        ClassInfo subClassInfo = Objects.requireNonNull(ClassInfo.forName(subClassInternalName), subClassInternalName);
        ClassInfo superClassInfo = Objects.requireNonNull(ClassInfo.forName(superClassInternalName), superClassInternalName);
        boolean checkInterfaces = superClassInfo.isInterface();

        return subClassInfo.hasSuperClass(superClassInfo, ClassInfo.Traversal.ALL, checkInterfaces);
    }

    /**
     * Equivalent to {@code Type.getType(desc).getSize()}.
     *
     * @param desc the type descriptor
     * @return the {@link Type#getSize() size} of the {@link Type} described by the given descriptor
     */
    public static int getTypeSize(String desc) {
        return Type.getType(desc).getSize();
    }

    /**
     * Extracts the size of the method arguments from the packed arguments-and-return-sizes value as returned by {@link Type#getArgumentsAndReturnSizes(String)} and
     * {@link Type#getArgumentsAndReturnSizes()}.
     *
     * @param argumentsAndReturnSizes the packed arguments-and-return-sizes value as returned by {@link Type#getArgumentsAndReturnSizes(String)} and {@link Type#getArgumentsAndReturnSizes()}
     * @return the size of the method arguments
     */
    public static int extractArgumentsSizes(int argumentsAndReturnSizes) {
        return (argumentsAndReturnSizes >> 2) - 1;
    }

    /**
     * Extracts the size of the return value from the packed arguments-and-return-sizes value as returned by {@link Type#getArgumentsAndReturnSizes(String)} and
     * {@link Type#getArgumentsAndReturnSizes()}.
     *
     * @param argumentsAndReturnSizes the packed arguments-and-return-sizes value as returned by {@link Type#getArgumentsAndReturnSizes(String)} and {@link Type#getArgumentsAndReturnSizes()}
     * @return the size of the return value
     */
    public static int extractReturnSize(int argumentsAndReturnSizes) {
        return argumentsAndReturnSizes & 0x3;
    }
}
