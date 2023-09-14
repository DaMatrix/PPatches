package net.daporkchop.ppatches.util;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Primitives;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static org.objectweb.asm.Opcodes.*;

/**
 * Helper methods for working with {@link MethodHandle}.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class MethodHandleUtils {
    /**
     * Produces a method handle which returns its sole argument when invoked.
     * <p>
     * Unlike {@link MethodHandles#identity(Class)}, identity methods of type {@code void} are permitted. An identity method of type {@code void} accepts no arguments and
     * returns {@code void}.
     *
     * @see MethodHandles#identity(Class)
     */
    public static MethodHandle identity(Class<?> type) {
        return Identity.CACHE.getUnchecked(type);
    }

    private static final class Identity extends CacheLoader<Class<?>, MethodHandle> {
        public static final LoadingCache<Class<?>, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakKeys().weakValues().build(new Identity());

        private static void voidIdentity() {
            //no-op
        }

        @Override
        public MethodHandle load(Class<?> type) throws Exception {
            return type == void.class
                    ? MethodHandles.lookup().findStatic(this.getClass(), "voidIdentity", MethodType.methodType(void.class)) //special handling for void
                    : MethodHandles.identity(type);
        }
    }

    /**
     * Produces a method handle of the requested return type which returns the given constant value every time it is invoked.
     *
     * @param type the return type of the desired method handle
     * @param value the value to return
     * @see MethodHandles#constant(Class, Object)
     */
    public static MethodHandle constant(Class<?> type, Object value) {
        return Constant.constant(type, value);
    }

    private static final class Constant extends CacheLoader<Constant.Key, MethodHandle> {
        //not using weak keys, since the Key object itself is never referenced by the value (no way around that, it's a MethodHandle) and we need the
        //  Key instances to remain in the cache as long as the value is still around.
        //  since guava caches don't evict the entire entry immediately when a weakly referenced key/value is garbage collected, this could technically result in
        //  the actual class and/or value instance remaining in memory indefinitely (until the next time the cache is accessed) even if the MethodHandle instance has
        //  already been garbage-collected. i might attempt to solve this in the future, but for now i'll treat this as a non-issue by assuming that if a user is providing
        //  garbage-collectable class instances to this method, they're likely to call this method again at some point when loading another class.
        private static final LoadingCache<Key, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakValues().build(new Constant());

        @SneakyThrows
        public static MethodHandle constant(Class<?> type, Object value) {
            Preconditions.checkArgument(type != void.class, "expected non-void type");

            if ((type.isPrimitive() || Primitives.isWrapperType(type)) && value != null && Primitives.wrap(type) != value.getClass()) {
                //ensure the value is of the correct wrapper type, widening it if not.
                //  this is necessary because otherwise, invoking 'constant(int.class, (byte) 0)' would result in a different cache key than 'constant(int.class, 0)', even
                //  though the resulting MethodHandles are equivalent.

                //we delegate all the boilerplate code for unboxing, widening and re-boxing to java and let it handle everything for us
                value = box(Primitives.unwrap(type)).invoke(value);
            }

            return CACHE.getUnchecked(new Key(type, value));
        }

        @Override
        public MethodHandle load(Key key) throws Exception {
            return MethodHandles.constant(key.type, key.value);
        }

        @RequiredArgsConstructor
        private static final class Key {
            private final Class<?> type;
            private final Object value;

            @Override
            public int hashCode() {
                return this.type.hashCode() * 31 + System.identityHashCode(this.value);
            }

            @Override
            public boolean equals(Object obj) {
                return this == obj || (obj instanceof Key && this.type == ((Key) obj).type && this.value == ((Key) obj).value);
            }
        }
    }

    /**
     * Produces a method handle which accepts a primitive value of the given primitive type, and returns the value after undergoing a boxing conversion.
     */
    public static MethodHandle box(Class<?> primitive) {
        return Box.CACHE.getUnchecked(primitive);
    }

    private static final class Box extends CacheLoader<Class<?>, MethodHandle> {
        //not using weak keys, since the only keys which could be used are primitive classes which can never be unloaded
        public static final LoadingCache<Class<?>, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakValues().build(new Box());

        @Override
        public MethodHandle load(Class<?> primitive) throws Exception {
            Preconditions.checkArgument(primitive.isPrimitive(), "expected primitive type: %s", primitive);

            Class<?> boxed = MethodType.methodType(primitive).wrap().returnType();
            return MethodHandles.lookup().findStatic(boxed, "valueOf", MethodType.methodType(boxed, primitive));
        }
    }

    /**
     * Produces a method handle which accepts a primitive value of the boxed type corresponding to the given primitive type, and returns the value after undergoing an unboxing conversion.
     */
    public static MethodHandle unbox(Class<?> primitive) {
        return Unbox.CACHE.getUnchecked(primitive);
    }

    private static final class Unbox extends CacheLoader<Class<?>, MethodHandle> {
        //not using weak keys, since the only keys which could be used are primitive classes which can never be unloaded
        public static final LoadingCache<Class<?>, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakValues().build(new Unbox());

        @Override
        public MethodHandle load(Class<?> primitive) throws Exception {
            Preconditions.checkArgument(primitive.isPrimitive(), "expected primitive type: %s", primitive);

            Class<?> boxed = MethodType.methodType(primitive).wrap().returnType();
            return MethodHandles.lookup().findVirtual(boxed, primitive.getName() + "Value", MethodType.methodType(primitive));
        }
    }

    /**
     * Produces a method handle which accepts two arguments of the given reference type and returns a {@code boolean} value indicating the result of performing an identity comparison on
     * the two arguments.
     *
     * @param type the type of objects to be compared
     */
    public static MethodHandle identityEquals(Class<?> type) {
        return identityEquals(type, type);
    }

    /**
     * Produces a method handle which accepts two arguments of the given reference types and returns a {@code boolean} value indicating the result of performing an identity comparison on
     * the two arguments.
     *
     * @param type1 the type of the first object to be compared
     * @param type2 the type of the second object to be compared
     * @throws IllegalArgumentException if neither of the given types is assignable to the other
     */
    public static MethodHandle identityEquals(Class<?> type1, Class<?> type2) {
        return IdentityEquals.CACHE.getUnchecked(MethodType.methodType(boolean.class, type1, type2));
    }

    private static final class IdentityEquals extends CacheLoader<MethodType, MethodHandle> {
        public static final LoadingCache<MethodType, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakKeys().weakValues().build(new IdentityEquals());
        private static final MethodHandle RAW;

        static {
            try {
                RAW = MethodHandles.lookup().findStatic(IdentityEquals.class, "identityEquals", MethodType.methodType(boolean.class, Object.class, Object.class));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private static boolean identityEquals(Object a, Object b) {
            return a == b;
        }

        @Override
        public MethodHandle load(MethodType type) throws Exception {
            Preconditions.checkArgument(type.returnType() == boolean.class && type.parameterCount() == 2, type);

            Class<?> param0 = type.parameterType(0);
            Class<?> param1 = type.parameterType(1);
            Preconditions.checkArgument(!param0.isPrimitive(), "expected non-primitive type: %s", param0);
            Preconditions.checkArgument(!param1.isPrimitive(), "expected non-primitive type: %s", param1);
            Preconditions.checkArgument(param0.isAssignableFrom(param1) || param1.isAssignableFrom(param0), "neither type is assignable to the other: %s, %s", param0, param1);

            return RAW.asType(type);
        }
    }

    /**
     * Produces a method handle which accepts two arguments of the given reference type and returns a {@code boolean} value indicating the negated result of performing an identity comparison on
     * the two arguments.
     *
     * @param type the type of objects to be compared
     */
    public static MethodHandle identityNotEquals(Class<?> type) {
        return identityNotEquals(type, type);
    }

    /**
     * Produces a method handle which accepts two arguments of the given reference types and returns a {@code boolean} value indicating the negated result of performing an identity comparison on
     * the two arguments.
     *
     * @param type1 the type of the first object to be compared
     * @param type2 the type of the second object to be compared
     * @throws IllegalArgumentException if neither of the given types is assignable to the other
     */
    public static MethodHandle identityNotEquals(Class<?> type1, Class<?> type2) {
        return IdentityNotEquals.CACHE.getUnchecked(MethodType.methodType(boolean.class, type1, type2));
    }

    private static final class IdentityNotEquals extends CacheLoader<MethodType, MethodHandle> {
        public static final LoadingCache<MethodType, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakKeys().weakValues().build(new IdentityNotEquals());
        private static final MethodHandle RAW;

        static {
            try {
                RAW = MethodHandles.lookup().findStatic(IdentityNotEquals.class, "identityNotEquals", MethodType.methodType(boolean.class, Object.class, Object.class));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private static boolean identityNotEquals(Object a, Object b) {
            return a != b;
        }

        @Override
        public MethodHandle load(MethodType type) throws Exception {
            Preconditions.checkArgument(type.returnType() == boolean.class && type.parameterCount() == 2, type);

            Class<?> param0 = type.parameterType(0);
            Class<?> param1 = type.parameterType(1);
            Preconditions.checkArgument(!param0.isPrimitive(), "expected non-primitive type: %s", param0);
            Preconditions.checkArgument(!param1.isPrimitive(), "expected non-primitive type: %s", param1);
            Preconditions.checkArgument(param0.isAssignableFrom(param1) || param1.isAssignableFrom(param0), "neither type is assignable to the other: %s, %s", param0, param1);

            return RAW.asType(type);
        }
    }

    @RequiredArgsConstructor
    @SuppressWarnings({"unchecked", "unused"})
    private static final class PrimitiveComparisons extends CacheLoader<Class<?>, MethodHandle> {
        private static final LoadingCache<Class<?>, MethodHandle>[] CACHES;

        private static final int OP_BASE = IFEQ;

        static {
            //not using weak keys, since the only keys which could be used are primitive classes which can never be unloaded
            CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().concurrencyLevel(1).weakValues().maximumSize(Type.DOUBLE);

            CACHES = (LoadingCache<Class<?>, MethodHandle>[]) new LoadingCache[] {
                    builder.build(new PrimitiveComparisons("eq")),
                    builder.build(new PrimitiveComparisons("ne")),
                    builder.build(new PrimitiveComparisons("lt")),
                    builder.build(new PrimitiveComparisons("ge")),
                    builder.build(new PrimitiveComparisons("gt")),
                    builder.build(new PrimitiveComparisons("le")),
            };
        }

        private final String op;

        @Override
        public MethodHandle load(Class<?> primitive) throws Exception {
            Preconditions.checkArgument(primitive.isPrimitive(), "expected primitive type: %s", primitive);

            try {
                return MethodHandles.lookup().findStatic(this.getClass(), this.op, MethodType.methodType(boolean.class, primitive, primitive));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("type " + primitive + " doesn't support this operation", e);
            }
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
    }

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
            return PrimitiveComparisons.CACHES[IFEQ - PrimitiveComparisons.OP_BASE].getUnchecked(type);
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
            return PrimitiveComparisons.CACHES[IFNE - PrimitiveComparisons.OP_BASE].getUnchecked(type);
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
        return PrimitiveComparisons.CACHES[IFLT - PrimitiveComparisons.OP_BASE].getUnchecked(type);
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code <=} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle lessThanOrEqual(Class<?> type) {
        return PrimitiveComparisons.CACHES[IFLE - PrimitiveComparisons.OP_BASE].getUnchecked(type);
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code >} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle greaterThan(Class<?> type) {
        return PrimitiveComparisons.CACHES[IFGT - PrimitiveComparisons.OP_BASE].getUnchecked(type);
    }

    /**
     * Produces a method handle which accepts two arguments of the given type and returns a {@code boolean} value indicating the result of comparing the two arguments using
     * the Java {@code >=} operator.
     *
     * @param type the type of the values which will be compared
     * @throws IllegalArgumentException if this comparison operation is not applicable to the given type
     */
    public static MethodHandle greaterThanOrEqual(Class<?> type) {
        return PrimitiveComparisons.CACHES[IFGE - PrimitiveComparisons.OP_BASE].getUnchecked(type);
    }

    /**
     * Produces a method handle which returns the result of calling {@link String#valueOf} on its sole argument when invoked.
     *
     * @throws IllegalArgumentException if the given type is {@code void}
     */
    public static MethodHandle stringValueOf(Class<?> type) {
        return StringValueOf.CACHE.getUnchecked(type);
    }

    private static final class StringValueOf extends CacheLoader<Class<?>, MethodHandle> {
        public static final LoadingCache<Class<?>, MethodHandle> CACHE = CacheBuilder.newBuilder().concurrencyLevel(1).weakKeys().weakValues().build(new StringValueOf());
        private static final MethodHandle RAW;

        static {
            try {
                RAW = MethodHandles.publicLookup().findStatic(String.class, "valueOf", MethodType.methodType(String.class, Object.class));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public MethodHandle load(Class<?> type) throws Exception {
            MethodHandle result;
            if (type.isPrimitive()) {
                try {
                    //String.valueOf doesn't have overloads for byte and short, they need to be forwarded to the int variant
                    Class<?> effectiveArgumentType = type == byte.class || type == short.class ? int.class : type;
                    result = MethodHandles.publicLookup().findStatic(String.class, "valueOf", MethodType.methodType(String.class, effectiveArgumentType));
                } catch (NoSuchMethodException e) { //can happen if type is void
                    throw new IllegalArgumentException("type " + type + " doesn't support this operation", e);
                }
            } else {
                result = RAW;
            }
            return result.asType(MethodType.methodType(String.class, type));
        }
    }
}
