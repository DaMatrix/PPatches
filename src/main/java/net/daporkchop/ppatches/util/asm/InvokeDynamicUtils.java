package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.util.MethodHandleUtils;
import net.daporkchop.ppatches.util.ReflectionUtils;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.lang.invoke.*;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * General-purpose functionality which uses {@code invokedynamic}.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class InvokeDynamicUtils {
    private static final Cache<Class<?>, CallSite> DUMMY_OBJECT_RETURN_VALUES_BY_TYPE = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .weakKeys() //this has the convenient side effect of forcing keys to be compared by identity
            //TODO: referencing the values strongly here will prevent the class from being garbage-collected
            .build();

    public static InvokeDynamicInsnNode makeDummyObjectValueInsn(Type type) {
        Preconditions.checkArgument(BytecodeHelper.isReference(type), "not a reference type: %s", type);
        return new InvokeDynamicInsnNode("$ppatches_dummyValue", Type.getMethodDescriptor(type),
                new Handle(H_INVOKESTATIC,
                        Type.getInternalName(InvokeDynamicUtils.class), "bootstrapDummyObjectValue",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false));
    }

    public static CallSite bootstrapDummyObjectValue(MethodHandles.Lookup lookup, String name, MethodType type) throws Throwable {
        return DUMMY_OBJECT_RETURN_VALUES_BY_TYPE.get(type.returnType(), () -> {
            Class<?> returnType = type.returnType();

            Object dummyInstance;
            if (returnType.isArray()) {
                dummyInstance = Array.newInstance(returnType.getComponentType(), 0);
            } else if (returnType.isInterface() || (returnType.getModifiers() & Modifier.ABSTRACT) != 0) {
                dummyInstance = UnsafeWrapper.allocateInstance(makeDummyNonAbstractImplementation(returnType, lookup.lookupClass()));
            } else {
                dummyInstance = UnsafeWrapper.allocateInstance(returnType);
            }

            return new ConstantCallSite(MethodHandles.constant(returnType, dummyInstance));
        });
    }

    private static Class<?> makeDummyNonAbstractImplementation(Class<?> interfaceOrSuperclass, @NonNull Class<?> context) {
        PPatchesMod.LOGGER.info("generating dummy class which extends/implements {} from context {}", interfaceOrSuperclass, context);

        String superInternalName = Type.getInternalName(interfaceOrSuperclass);
        String internalName = superInternalName + "$PPatches__dummy__";

        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL, internalName, null,
                interfaceOrSuperclass.isInterface() ? "java/lang/Object" : superInternalName,
                interfaceOrSuperclass.isInterface() ? new String[]{superInternalName} : null);
        writer.visitEnd();
        return UnsafeWrapper.defineAnonymousClass(interfaceOrSuperclass, writer.toByteArray(), null);
    }

    public static InvokeDynamicInsnNode makeNewException(Class<? extends Throwable> exceptionClass, Object... optionalStaticMethodComponents) {
        return makeNewException(Type.getType(exceptionClass), optionalStaticMethodComponents);
    }

    public static InvokeDynamicInsnNode makeNewException(Type exceptionType, Object... optionalStaticMethodComponents) {
        return new InvokeDynamicInsnNode("newException", Type.getMethodDescriptor(exceptionType),
                new Handle(H_INVOKESTATIC,
                        Type.getInternalName(InvokeDynamicUtils.class), "bootstrapNewException",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                Stream.concat(
                        Stream.of(new Handle(H_NEWINVOKESPECIAL, exceptionType.getInternalName(), "<init>", optionalStaticMethodComponents.length == 0 ? "()V" : "(Ljava/lang/String;)V", false)),
                        Stream.of(optionalStaticMethodComponents)).toArray());
    }

    public static CallSite bootstrapNewException(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle ctor, Object... optionalStaticMessageComponents) {
        if (optionalStaticMessageComponents.length != 0) {
            String msg = Stream.of(optionalStaticMessageComponents).map(Object::toString).collect(Collectors.joining()).intern();
            ctor = ctor.bindTo(msg);
        }
        return new ConstantCallSite(ctor);
    }

    public static InvokeDynamicInsnNode makeLoadAssertionStateInsn() {
        return new InvokeDynamicInsnNode("desiredAssertionStatus", "()Z",
                new Handle(H_INVOKESTATIC,
                        Type.getInternalName(InvokeDynamicUtils.class), "bootstrapAssertionState",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false));
    }

    public static CallSite bootstrapAssertionState(MethodHandles.Lookup lookup, String name, MethodType type) throws Throwable {
        return new ConstantCallSite(MethodHandleUtils.constant(boolean.class, lookup.lookupClass().desiredAssertionStatus()));
    }

    public static InvokeDynamicInsnNode makeInaccessibleHandle(Handle handle) {
        Handle bsm = new Handle(H_INVOKESTATIC, Type.getInternalName(InvokeDynamicUtils.class), "bootstrapInaccessibleAccessor",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);

        Handle unreflectMethod;
        Handle resolveMethod;

        List<Object> bsmArgs = new ArrayList<>();
        bsmArgs.add(null); //unreflectMethod
        bsmArgs.add(null); //resolveMethod
        bsmArgs.add(Type.getObjectType(handle.getOwner()));

        int tag = handle.getTag();
        switch (tag) {
            case H_GETFIELD:
            case H_GETSTATIC:
            case H_PUTFIELD:
            case H_PUTSTATIC:
                unreflectMethod = new Handle(H_INVOKEVIRTUAL, Type.getInternalName(MethodHandles.Lookup.class),
                        tag == H_GETFIELD || tag == H_GETSTATIC ? "unreflectGetter" : "unreflectSetter",
                        "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false);
                resolveMethod = new Handle(H_INVOKESTATIC, Type.getInternalName(ReflectionUtils.class), "getDeclaredFieldExact", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/reflect/Field;", false);
                bsmArgs.add(handle.getName());
                bsmArgs.add(Type.getType(handle.getDesc()));
                break;
            case H_INVOKEVIRTUAL:
            case H_INVOKESTATIC:
            case H_INVOKESPECIAL: //TODO: this should be calling lookup.unreflectSpecial()
            case H_INVOKEINTERFACE:
                unreflectMethod = new Handle(H_INVOKEVIRTUAL, Type.getInternalName(MethodHandles.Lookup.class), "unreflect", "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;", false);
                resolveMethod = new Handle(H_INVOKESTATIC, Type.getInternalName(ReflectionUtils.class), "getDeclaredMethodExact", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/reflect/Method;", false);
                bsmArgs.add(handle.getName());
                bsmArgs.add(Type.getMethodType(handle.getDesc()));
                break;
            case H_NEWINVOKESPECIAL:
                unreflectMethod = new Handle(H_INVOKEVIRTUAL, Type.getInternalName(MethodHandles.Lookup.class), "unreflectConstructor", "(Ljava/lang/reflect/Constructor;)Ljava/lang/invoke/MethodHandle;", false);
                resolveMethod = new Handle(H_INVOKEVIRTUAL, "java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false);
                bsmArgs.addAll(Arrays.asList(Type.getArgumentTypes(handle.getDesc())));
                break;
            default:
                throw new IllegalStateException("illegal handle tag " + handle.getTag());
        }

        bsmArgs.set(0, unreflectMethod);
        bsmArgs.set(1, resolveMethod);
        return new InvokeDynamicInsnNode(handle.getName(), BytecodeHelper.getEffectiveHandleMethodType(handle).getDescriptor(), bsm, bsmArgs.toArray());
    }

    public static CallSite bootstrapInaccessibleAccessor(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle unreflectMethod, MethodHandle resolveMethod, Object... resolveArgs) throws Throwable {
        AccessibleObject element = (AccessibleObject) resolveMethod.invokeWithArguments(resolveArgs);
        element.setAccessible(true);
        return new ConstantCallSite(((MethodHandle) unreflectMethod.invoke(lookup, element)).asType(type));
    }
}
