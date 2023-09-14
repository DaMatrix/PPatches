package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.util.MethodHandleUtils;
import net.daporkchop.ppatches.util.ReflectionUtils;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Printer;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    public static CallSite bootstrapAssertionState(MethodHandles.Lookup lookup, String name, MethodType type) throws Throwable {
        return new ConstantCallSite(MethodHandleUtils.constant(boolean.class, lookup.lookupClass().desiredAssertionStatus()));
    }

    public static InvokeDynamicInsnNode makeInaccessibleHandle(Handle handle) {
        /*String desc = BytecodeHelper.getEffectiveHandleMethodType(handle).getDescriptor();

        switch (handle.getTag()) {
            case H_GETFIELD:
            case H_GETSTATIC:
                return new InvokeDynamicInsnNode(handle.getName(), desc,
                        new Handle(H_INVOKESTATIC, Type.getInternalName(InvokeDynamicUtils.class), "bootstrapInaccessibleFieldAccessor",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                        Type.getObjectType(handle.getOwner()),
                        Type.getType(handle.getDesc()),
                        new Handle(H_INVOKEVIRTUAL, Type.getInternalName(MethodHandles.Lookup.class),
                                "unreflectGetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false));
            case H_PUTFIELD:
            case H_PUTSTATIC:
                return new InvokeDynamicInsnNode(handle.getName(), desc,
                        new Handle(H_INVOKESTATIC, Type.getInternalName(InvokeDynamicUtils.class), "bootstrapInaccessibleFieldAccessor",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                        Type.getObjectType(handle.getOwner()),
                        Type.getType(handle.getDesc()),
                        new Handle(H_INVOKEVIRTUAL, Type.getInternalName(MethodHandles.Lookup.class),
                                "unreflectSetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false));
            case H_INVOKEVIRTUAL:
            case H_INVOKESTATIC:
            case H_INVOKESPECIAL: //TODO: this should be calling lookup.unreflectSpecial()
            case H_INVOKEINTERFACE:
                return new InvokeDynamicInsnNode(handle.getName(), desc,
                        new Handle(H_INVOKESTATIC, Type.getInternalName(InvokeDynamicUtils.class), "bootstrapInaccessibleMethodAccessor",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                        Type.getObjectType(handle.getOwner()),
                        Type.getMethodType(handle.getDesc()));
            case H_NEWINVOKESPECIAL:
                return new InvokeDynamicInsnNode(handle.getName(), desc,
                        new Handle(H_INVOKESTATIC, Type.getInternalName(InvokeDynamicUtils.class), "bootstrapInaccessibleMethodAccessor",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                        Type.getObjectType(handle.getOwner()),
                        Type.getMethodType(handle.getDesc()));
            default:
                throw new IllegalStateException("illegal handle tag " + handle.getTag());
        }*/

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

    /*public static CallSite bootstrapInaccessibleFieldAccessor(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> owner, Class<?> fieldType, MethodHandle unreflectMethod) throws Throwable {
        Field field = ReflectionUtils.getDeclaredFieldExact(owner, name, fieldType);
        field.setAccessible(true);
        return new ConstantCallSite(((MethodHandle) unreflectMethod.invokeExact(lookup, field)).asType(type));
    }

    public static CallSite bootstrapInaccessibleMethodAccessor(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> owner, MethodType methodType) throws Throwable {
        Method method = ReflectionUtils.getDeclaredMethodExact(owner, name, methodType);
        method.setAccessible(true);
        return new ConstantCallSite(lookup.unreflect(method).asType(type));
    }*/

    public static CallSite bootstrapInaccessibleAccessor(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle unreflectMethod, MethodHandle resolveMethod, Object... resolveArgs) throws Throwable {
        AccessibleObject element = (AccessibleObject) resolveMethod.invokeWithArguments(resolveArgs);
        element.setAccessible(true);
        return new ConstantCallSite(((MethodHandle) unreflectMethod.invoke(lookup, element)).asType(type));
    }

    public static CallSite bootstrapStringConcatenation(MethodHandles.Lookup lookup, String name, MethodType type, Object... args) throws Throwable {
        Iterator<Object> itr = Arrays.asList(args).iterator();
        MethodHandle concatenator = decodeStringConcatenation(lookup, type, itr);
        Preconditions.checkArgument(!itr.hasNext(), "not all arguments were used?!?");
        return new ConstantCallSite(concatenator);
    }

    private static final int CONCAT_SORT_LDC = 0;
    private static final int CONCAT_SORT_ARGUMENT = 1;
    private static final int CONCAT_SORT_INVOKE = 2;

    public static MethodHandle decodeStringConcatenation(MethodHandles.Lookup lookup, MethodType methodType, Iterator<Object> args) throws IllegalAccessException, NoSuchMethodException {
        Preconditions.checkArgument(methodType.returnType().isAssignableFrom(String.class));

        MethodHandle resultMethod = lookup.findVirtual(StringBuilder.class, "toString", MethodType.methodType(String.class));

        int elementCount = (int) args.next();
        Deque<MethodHandle> callStack = new ArrayDeque<>(elementCount); //we'll stage the method handles on a stack in order to reverse them
        for (int i = 0; i < elementCount; i++) {
            MethodHandle appendMethod = (MethodHandle) args.next();
            Class<?> type = appendMethod.type().parameterType(1); //the argument type for the call to StringBuilder#append
            int sort = (int) args.next();

            switch (sort) {
                case CONCAT_SORT_LDC: {
                    Object value = args.next();
                    if (type == boolean.class) {
                        value = ((int) value) != 0;
                    } else if (type == byte.class) {
                        value = (byte) (int) value;
                    } else if (type == short.class) {
                        value = (short) (int) value;
                    } else if (type == char.class) {
                        value = (char) (int) value;
                    }

                    callStack.push(MethodHandles.insertArguments(appendMethod, 1, value));
                    continue;
                }
                case CONCAT_SORT_ARGUMENT:
                    callStack.push(appendMethod);
                    continue;
                case CONCAT_SORT_INVOKE: {
                    MethodHandle invokeTarget = (MethodHandle) args.next();
                    Preconditions.checkArgument(invokeTarget.type().parameterCount() == 0 && type.isAssignableFrom(invokeTarget.type().returnType()), "expected type %s, target method is of type %s", type, invokeTarget.type());
                    callStack.push(MethodHandles.collectArguments(appendMethod, 1, invokeTarget));
                    continue;
                }
                default:
                    throw new IllegalArgumentException();
            }
        }

        while (!callStack.isEmpty()) {
            resultMethod = MethodHandles.collectArguments(resultMethod, 0, callStack.pop());
        }

        return MethodHandles.collectArguments(resultMethod, 0, lookup.findConstructor(StringBuilder.class, MethodType.methodType(void.class))).asType(methodType);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class GeneratedStringConcatenation {
        public final AbstractInsnNode insertLocation;
        public final List<Type> invokeDynamicArgumentTypes;
        public final List<Object> bootstrapArgs;

        public final Set<AbstractInsnNode> removeInsns;

        public void replaceIn(InsnList instructions) {
            instructions.insert(this.insertLocation, new InvokeDynamicInsnNode("concatenateStrings", Type.getMethodDescriptor(Type.getType(String.class), this.invokeDynamicArgumentTypes.toArray(new Type[0])),
                    new Handle(H_INVOKESTATIC, Type.getInternalName(InvokeDynamicUtils.class),
                            "bootstrapStringConcatenation", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                    this.bootstrapArgs.toArray()));

            for (AbstractInsnNode removeInsn : this.removeInsns) {
                instructions.remove(removeInsn);
            }
        }
    }

    public static Optional<GeneratedStringConcatenation> tryGenerateStringConcatenation(MethodNode method, Frame<SourceValue>[] sourceFrames, MethodInsnNode invokeToStringInsn) {
        Preconditions.checkArgument(invokeToStringInsn.getOpcode() == INVOKEVIRTUAL
                                    && "java/lang/StringBuilder".equals(invokeToStringInsn.owner) && "toString".equals(invokeToStringInsn.name) && "()Ljava/lang/String;".equals(invokeToStringInsn.desc), invokeToStringInsn);

        InsnList insertInsns = new InsnList();
        List<Type> invokedynamicArgumentTypes = new ArrayList<>();

        List<Object> bootstrapArgs = new ArrayList<>();
        bootstrapArgs.add(null); //will be replaced with the count
        int bootstrapElementCount = 0;

        Set<AbstractInsnNode> removeInsns = Collections.newSetFromMap(new IdentityHashMap<>());
        removeInsns.add(invokeToStringInsn);

        AbstractInsnNode lastInsn = invokeToStringInsn;
        LOOP:
        for (int lastInsnPopped = 1; ; ) {
            Set<AbstractInsnNode> sources = BytecodeHelper.getStackValueFromTop(sourceFrames[method.instructions.indexOf(lastInsn)], lastInsnPopped - 1).insns;
            if (sources.size() != 1) {
                return Optional.empty();
            }

            lastInsn = sources.iterator().next();
            switch (lastInsn.getOpcode()) {
                case INVOKEVIRTUAL: {
                    MethodInsnNode lastAppendInsn = (MethodInsnNode) lastInsn;
                    if (!"java/lang/StringBuilder".equals(lastAppendInsn.owner) || !"append".equals(lastAppendInsn.name)) {
                        return Optional.empty();
                    }

                    Type[] appendArgumentTypes = Type.getArgumentTypes(lastAppendInsn.desc);
                    if (appendArgumentTypes.length != 1) { //complex form, we don't know how to handle this
                        return Optional.empty();
                    }
                    Type appendArgumentType = appendArgumentTypes[0];
                    Handle appendHandle = new Handle(H_INVOKEVIRTUAL, lastAppendInsn.owner, lastAppendInsn.name, lastAppendInsn.desc, lastAppendInsn.itf);

                    lastInsnPopped = 2; //popped two stack values: the StringBuilder instance and the appended value

                    //examine the sources for the appended value
                    sources = BytecodeHelper.getStackValueFromTop(sourceFrames[method.instructions.indexOf(lastInsn)], 0).insns;
                    if (sources.size() == 1) {
                        AbstractInsnNode appendedValueSource = sources.iterator().next();
                        if (BytecodeHelper.isConstant(appendedValueSource)) {
                            Object value = BytecodeHelper.decodeConstant(appendedValueSource);
                            if (value != null) { //if the argument is null there are a few weird edge cases to handle, it's easiest to just ignore them
                                //  (technically we could replace with a constant value of the string literal "null" for all 4 append overloads which accept a reference type
                                //   argument other than char[], but meh)
                                removeInsns.add(lastInsn);
                                removeInsns.add(appendedValueSource);

                                bootstrapArgs.addAll(1, ImmutableList.of(appendHandle, CONCAT_SORT_LDC, value));
                                bootstrapElementCount++;
                                continue;
                            }
                        } else switch (appendedValueSource.getOpcode()) {
                            case GETSTATIC: { //GETSTATIC instruction can be converted to a zero-argument MethodHandle
                                FieldInsnNode getStaticInsn = (FieldInsnNode) appendedValueSource;

                                removeInsns.add(lastInsn);
                                removeInsns.add(appendedValueSource);

                                bootstrapArgs.addAll(1, ImmutableList.of(appendHandle, CONCAT_SORT_INVOKE,
                                        new Handle(H_GETSTATIC, getStaticInsn.owner, getStaticInsn.name, getStaticInsn.desc, false))); //'false' argument here isn't used
                                bootstrapElementCount++;
                                continue;
                            }
                            case INVOKESTATIC: { //INVOKESTATIC instruction to a method with no arguments can be converted to a zero-argument MethodHandle
                                MethodInsnNode invokeStaticInsn = (MethodInsnNode) appendedValueSource;

                                if (Type.getArgumentTypes(invokeStaticInsn.desc).length == 0) {
                                    removeInsns.add(lastInsn);
                                    removeInsns.add(appendedValueSource);

                                    bootstrapArgs.addAll(1, ImmutableList.of(appendHandle, CONCAT_SORT_INVOKE,
                                            new Handle(H_INVOKESTATIC, invokeStaticInsn.owner, invokeStaticInsn.name, invokeStaticInsn.desc, invokeStaticInsn.itf)));
                                    bootstrapElementCount++;
                                    continue;
                                }
                                break;
                            }
                        }
                    }

                    //the argument value can't be flattened into the constant invokedynamic arguments, pass them to the dynamic call site on the stack
                    removeInsns.add(lastInsn);

                    invokedynamicArgumentTypes.add(0, appendArgumentType);

                    bootstrapArgs.addAll(1, ImmutableList.of(appendHandle, CONCAT_SORT_ARGUMENT));
                    bootstrapElementCount++;
                    continue;
                }
                case NEW: {
                    TypeInsnNode newInsn = (TypeInsnNode) lastInsn;
                    Preconditions.checkState("java/lang/StringBuilder".equals(newInsn.desc), newInsn.desc);

                    AbstractInsnNode dupInsn = newInsn.getNext();
                    Preconditions.checkState(dupInsn.getOpcode() == DUP, "expected %s, got %s", Printer.OPCODES[DUP], Printer.OPCODES[dupInsn.getOpcode()]);

                    MethodInsnNode invokeCtorInsn;
                    if (dupInsn.getNext().getOpcode() != INVOKESPECIAL
                        || !"java/lang/StringBuilder".equals((invokeCtorInsn = (MethodInsnNode) dupInsn.getNext()).owner)
                        || !"<init>".equals(invokeCtorInsn.name) || !"()V".equals(invokeCtorInsn.desc)) {
                        //not a no-args StringBuilder constructor, ignore
                        return Optional.empty();
                    }

                    removeInsns.add(lastInsn);
                    removeInsns.add(dupInsn);
                    removeInsns.add(invokeCtorInsn);
                    break LOOP;
                }
            }

            return Optional.empty();
        }

        //we could be using far more stack space than the original function (as all of the appended values are now on the stack), so we may need to increase it
        //  (need to do this here since stack space won't be recomputed until later, and we may have to run the analyzer on this function again)
        int originalUsedStackSlots = BytecodeHelper.getUsedStackSlots(sourceFrames[method.instructions.indexOf(invokeToStringInsn)]);
        int newUsedStackSlots = originalUsedStackSlots - 1 + invokedynamicArgumentTypes.stream().mapToInt(Type::getSize).sum();
        if (newUsedStackSlots > method.maxStack) {
            method.maxStack = newUsedStackSlots;
        }

        //TODO: is this necessary?
        /*Preconditions.checkState(method.instructions.indexOf(lastInsn) < method.instructions.indexOf(invokeToStringInsn)); //NEW must come before toString() call
        for (AbstractInsnNode currentInsn = lastInsn; currentInsn != invokeToStringInsn; currentInsn = BytecodeHelper.nextNormalCodeInstruction(currentInsn)) {
            if (!removeInsns.contains(currentInsn) && !ignoredInsns.contains(currentInsn)) { //there's some instruction in between which isn't being replaced
                return Optional.empty();
            }
        }*/

        bootstrapArgs.set(0, bootstrapElementCount);
        return Optional.of(new GeneratedStringConcatenation(invokeToStringInsn, invokedynamicArgumentTypes, bootstrapArgs, removeInsns));
    }
}
