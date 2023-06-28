package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.lang.invoke.*;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
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
                interfaceOrSuperclass.isInterface() ? new String[]{ superInternalName } : null);
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

    private static final CallSite[] CONSTANT_BOOLEAN_CALL_SITES = {
            new ConstantCallSite(MethodHandles.constant(boolean.class, false)),
            new ConstantCallSite(MethodHandles.constant(boolean.class, true)),
    };

    public static CallSite bootstrapAssertionState(MethodHandles.Lookup lookup, String name, MethodType type) throws Throwable {
        return CONSTANT_BOOLEAN_CALL_SITES[lookup.lookupClass().desiredAssertionStatus() ? 1 : 0];
    }
}
