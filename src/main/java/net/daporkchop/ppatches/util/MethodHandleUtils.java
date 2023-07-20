package net.daporkchop.ppatches.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.objectweb.asm.Opcodes.*;

/**
 * Helper methods for working with {@link MethodHandle}.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class MethodHandleUtils {
    private static final MethodHandle VOID_IDENTITY;
    private static final MethodHandle IDENTITY_EQUALS;
    private static final MethodHandle IDENTITY_NOT_EQUALS;

    private static final MethodHandle[][] PRIMITIVE_COMPARISONS = new MethodHandle[Type.DOUBLE + 1][6];

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            VOID_IDENTITY = lookup.findStatic(MethodHandleUtils.class, "voidIdentity", MethodType.methodType(void.class));
            IDENTITY_EQUALS = lookup.findStatic(MethodHandleUtils.class, "identityEquals", MethodType.methodType(boolean.class, Object.class, Object.class));
            IDENTITY_NOT_EQUALS = lookup.findStatic(MethodHandleUtils.class, "identityNotEquals", MethodType.methodType(boolean.class, Object.class, Object.class));

            String[] comparisonNames = {"eq", "ne", "lt", "ge", "gt", "le"};
            for (Type primitiveType : new Type[]{Type.BOOLEAN_TYPE, Type.BYTE_TYPE, Type.SHORT_TYPE, Type.CHAR_TYPE, Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.LONG_TYPE}) {
                MethodType methodType = MethodType.fromMethodDescriptorString(Type.getMethodDescriptor(Type.BOOLEAN_TYPE, primitiveType, primitiveType), ClassLoader.getSystemClassLoader());
                for (int comparisonIndex = 0, lim = primitiveType == Type.BOOLEAN_TYPE ? 2 : 6; comparisonIndex < lim; comparisonIndex++) {
                    PRIMITIVE_COMPARISONS[primitiveType.getSort()][comparisonIndex] = lookup.findStatic(MethodHandleUtils.class, comparisonNames[comparisonIndex], methodType);
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void voidIdentity() {
        //no-op
    }

    /**
     * Produces a method handle which returns its sole argument when invoked.
     * <p>
     * Unlike {@link MethodHandles#identity(Class)}, identity methods of type {@code void} are permitted. An identity method of type {@code void} accepts no arguments and
     * returns {@code void}.
     *
     * @see MethodHandles#identity(Class)
     */
    public static MethodHandle identity(Class<?> type) {
        return type == void.class ? VOID_IDENTITY : MethodHandles.identity(type);
    }

    /**
     * Produces a method handle which accepts a primitive value of the given primitive type, and returns the value after undergoing a boxing conversion.
     */
    @SneakyThrows
    public static MethodHandle box(MethodHandles.Lookup lookup, Class<?> primitive) {
        assert primitive.isPrimitive() : primitive;
        Class<?> boxed = MethodType.methodType(primitive).wrap().returnType();
        return lookup.findStatic(boxed, "valueOf", MethodType.methodType(boxed, primitive));
    }

    /**
     * Produces a method handle which accepts a primitive value of the boxed type corresponding to the given primitive type, and returns the value after undergoing an unboxing conversion.
     */
    @SneakyThrows
    public static MethodHandle unbox(MethodHandles.Lookup lookup, Class<?> primitive) {
        assert primitive.isPrimitive() : primitive;
        Class<?> boxed = MethodType.methodType(primitive).wrap().returnType();
        return lookup.findVirtual(boxed, primitive.getName() + "Value", MethodType.methodType(primitive));
    }

    private static boolean identityEquals(Object a, Object b) {
        return a == b;
    }

    /**
     * Produces a method handle which accepts two arguments of the given reference type and returns a {@code boolean} value indicating the result of performing an identity comparison on
     * the two arguments.
     *
     * @param type the type of objects to be compared
     */
    public static MethodHandle identityEquals(Class<?> type) {
        assert !type.isPrimitive() : type;
        return IDENTITY_EQUALS.asType(MethodType.methodType(boolean.class, type, type));
    }

    private static boolean identityNotEquals(Object a, Object b) {
        return a != b;
    }

    /**
     * Produces a method handle which accepts two arguments of the given reference type and returns a {@code boolean} value indicating the negated result of performing an identity comparison on
     * the two arguments.
     *
     * @param type the type of objects to be compared
     */
    public static MethodHandle identityNotEquals(Class<?> type) {
        assert !type.isPrimitive() : type;
        return IDENTITY_NOT_EQUALS.asType(MethodType.methodType(boolean.class, type, type));
    }

    // @formatter:off
    private static boolean eq(boolean a, boolean b) { return a == b; }
    private static boolean ne(boolean a, boolean b) { return a != b; }

    private static boolean eq(byte a, byte b) { return a == b; }
    private static boolean ne(byte a, byte b) { return a != b; }
    private static boolean lt(byte a, byte b) { return a < b; }
    private static boolean le(byte a, byte b) { return a <= b; }
    private static boolean gt(byte a, byte b) { return a > b; }
    private static boolean ge(byte a, byte b) { return a >= b; }

    private static boolean eq(short a, short b) { return a == b; }
    private static boolean ne(short a, short b) { return a != b; }
    private static boolean lt(short a, short b) { return a < b; }
    private static boolean le(short a, short b) { return a <= b; }
    private static boolean gt(short a, short b) { return a > b; }
    private static boolean ge(short a, short b) { return a >= b; }

    private static boolean eq(char a, char b) { return a == b; }
    private static boolean ne(char a, char b) { return a != b; }
    private static boolean lt(char a, char b) { return a < b; }
    private static boolean le(char a, char b) { return a <= b; }
    private static boolean gt(char a, char b) { return a > b; }
    private static boolean ge(char a, char b) { return a >= b; }

    private static boolean eq(int a, int b) { return a == b; }
    private static boolean ne(int a, int b) { return a != b; }
    private static boolean lt(int a, int b) { return a < b; }
    private static boolean le(int a, int b) { return a <= b; }
    private static boolean gt(int a, int b) { return a > b; }
    private static boolean ge(int a, int b) { return a >= b; }

    private static boolean eq(long a, long b) { return a == b; }
    private static boolean ne(long a, long b) { return a != b; }
    private static boolean lt(long a, long b) { return a < b; }
    private static boolean le(long a, long b) { return a <= b; }
    private static boolean gt(long a, long b) { return a > b; }
    private static boolean ge(long a, long b) { return a >= b; }

    private static boolean eq(float a, float b) { return a == b; }
    private static boolean ne(float a, float b) { return a != b; }
    private static boolean lt(float a, float b) { return a < b; }
    private static boolean le(float a, float b) { return a <= b; }
    private static boolean gt(float a, float b) { return a > b; }
    private static boolean ge(float a, float b) { return a >= b; }
    private static boolean eq(double a, double b) { return a == b; }
    private static boolean ne(double a, double b) { return a != b; }
    private static boolean lt(double a, double b) { return a < b; }
    private static boolean le(double a, double b) { return a <= b; }
    private static boolean gt(double a, double b) { return a > b; }
    private static boolean ge(double a, double b) { return a >= b; }
    // @formatter:on

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code ==} operator.
     * <p>
     * Note that for reference types, this will result in an identity comparison.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle equal(Class<?> type) {
        if (type.isPrimitive()) {
            MethodHandle handle = PRIMITIVE_COMPARISONS[Type.getType(type).getSort()][IFEQ - IFEQ];
            if (handle == null) {
                throw new IllegalArgumentException("type " + type + " doesn't support this operation");
            }
            return handle;
        } else {
            return identityEquals(type);
        }
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code !=} operator.
     * <p>
     * Note that for reference types, this will result in an identity comparison.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle notEqual(Class<?> type) {
        if (type.isPrimitive()) {
            MethodHandle handle = PRIMITIVE_COMPARISONS[Type.getType(type).getSort()][IFNE - IFEQ];
            if (handle == null) {
                throw new IllegalArgumentException("type " + type + " doesn't support this operation");
            }
            return handle;
        } else {
            return identityNotEquals(type);
        }
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code <} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle lessThan(Class<?> type) {
        MethodHandle handle;
        if (!type.isPrimitive() || (handle = PRIMITIVE_COMPARISONS[Type.getType(type).getSort()][IFLT - IFEQ]) == null) {
            throw new IllegalArgumentException("type " + type + " doesn't support this operation");
        }
        return handle;
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code <=} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle lessThanOrEqual(Class<?> type) {
        MethodHandle handle;
        if (!type.isPrimitive() || (handle = PRIMITIVE_COMPARISONS[Type.getType(type).getSort()][IFLE - IFEQ]) == null) {
            throw new IllegalArgumentException("type " + type + " doesn't support this operation");
        }
        return handle;
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code >} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle greaterThan(Class<?> type) {
        MethodHandle handle;
        if (!type.isPrimitive() || (handle = PRIMITIVE_COMPARISONS[Type.getType(type).getSort()][IFGT - IFEQ]) == null) {
            throw new IllegalArgumentException("type " + type + " doesn't support this operation");
        }
        return handle;
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code >=} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle greaterThanOrEqual(Class<?> type) {
        MethodHandle handle;
        if (!type.isPrimitive() || (handle = PRIMITIVE_COMPARISONS[Type.getType(type).getSort()][IFGE - IFEQ]) == null) {
            throw new IllegalArgumentException("type " + type + " doesn't support this operation");
        }
        return handle;
    }
}
