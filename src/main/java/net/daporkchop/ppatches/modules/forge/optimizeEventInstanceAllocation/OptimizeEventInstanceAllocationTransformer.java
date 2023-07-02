package net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.invoke.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeEventInstanceAllocationTransformer implements ITreeClassTransformer {
    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        boolean anyChanged = false;

        try {
            //use the same logic as Forge's EventSubscriptionTransformer to determine if the class in question is an event
            if (classNode.superName != null && !transformedName.startsWith("net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation.")
                && ("net.minecraftforge.fml.common.eventhandler.Event".equals(transformedName) || Event.class.isAssignableFrom(this.getClass().getClassLoader().loadClass(classNode.superName.replace('.', '/'))))) {
                anyChanged |= examineAndTransformEventClass(classNode);
            }
        } catch (ClassNotFoundException e) {
            //Forge's EventSubscriptionTransformer silently ignores these exceptions, we'll do the same
        }

        List<MethodInsnNode> invokePostInsns = null;
        for (MethodNode methodNode : classNode.methods) {
            //first pass: scan for calls to EventBus#post(Object)
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == INVOKEVIRTUAL) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("net/minecraftforge/fml/common/eventhandler/EventBus".equals(methodInsn.owner) && "post".equals(methodInsn.name) && "(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z".equals(methodInsn.desc)) {
                        if (invokePostInsns == null) {
                            invokePostInsns = new ArrayList<>();
                        }
                        invokePostInsns.add(methodInsn);
                    }
                }
            }

            if (invokePostInsns != null && !invokePostInsns.isEmpty()) {
                for (MethodInsnNode invokePostInsn : invokePostInsns) {
                    anyChanged |= transformEventBusPost(classNode, methodNode, invokePostInsn);
                }
                invokePostInsns.clear();
            }
        }

        return anyChanged;
    }

    private static String getResetMethodDesc(Type eventClass, String origCtorDesc) {
        assert origCtorDesc.endsWith(")V") : origCtorDesc;
        Type[] oldArgumentTypes = Type.getArgumentTypes(origCtorDesc);
        Type[] newArgumentTypes = new Type[oldArgumentTypes.length + 1];
        newArgumentTypes[0] = eventClass;
        System.arraycopy(oldArgumentTypes, 0, newArgumentTypes, 1, oldArgumentTypes.length);
        return Type.getMethodDescriptor(Type.VOID_TYPE, newArgumentTypes);
    }

    private static boolean examineAndTransformEventClass(ClassNode classNode) {
        classNode.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "$ppatches_instanceCache", "Ljava/lang/ThreadLocal;", null, null));

        // $ppatches_instanceCache = ThreadLocal.withInitial(ArrayDeque::new)
        BytecodeHelper.getOrCreateClinit(classNode).instructions.insert(BytecodeHelper.makeInsnList(
                new InvokeDynamicInsnNode("get", Type.getMethodDescriptor(Type.getType(Supplier.class)),
                        new Handle(H_INVOKESTATIC, Type.getInternalName(LambdaMetafactory.class), "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                        Type.getMethodType(Type.getType(Object.class)),
                        new Handle(H_NEWINVOKESPECIAL, "java/util/ArrayDeque", "<init>", "()V", false),
                        Type.getMethodType(Type.getType(ArrayDeque.class))),
                new MethodInsnNode(INVOKESTATIC, "java/lang/ThreadLocal", "withInitial", "(Ljava/util/function/Supplier;)Ljava/lang/ThreadLocal;", false),
                new FieldInsnNode(PUTSTATIC, classNode.name, "$ppatches_instanceCache", "Ljava/lang/ThreadLocal;")));

        for (ListIterator<MethodNode> methodItr = classNode.methods.listIterator(); methodItr.hasNext(); ) { //find and analyze constructors
            MethodNode ctor = methodItr.next();
            if (!"<init>".equals(ctor.name)) {
                continue;
            }

            Type[] ctorArgumentTypes = Type.getArgumentTypes(ctor.desc);

            //duplicate the constructor and turn it into a static method which will reset the object instance
            MethodNode resetMethod = BytecodeHelper.cloneMethod(ctor);
            methodItr.add(resetMethod);

            resetMethod.name = "$ppatches_resetInstance";

            //make the reset method static
            resetMethod.access |= ACC_STATIC;
            resetMethod.desc = getResetMethodDesc(Type.getObjectType(classNode.name), resetMethod.desc);

            //remove call to java.lang.Object#<init>() and Event#setup() from Event's reset method
            if ("net/minecraftforge/fml/common/eventhandler/Event".equals(classNode.name)) {
                for (AbstractInsnNode insn = resetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == INVOKESPECIAL) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if ("java/lang/Object".equals(methodInsn.owner) && "<init>".equals(methodInsn.name)) {
                            Preconditions.checkState(insn.getPrevious().getOpcode() == ALOAD && ((VarInsnNode) insn.getPrevious()).var == 0);
                            resetMethod.instructions.remove(insn.getPrevious());
                            resetMethod.instructions.remove((insn = insn.getNext()).getPrevious());
                        }
                    } else if (insn.getOpcode() == INVOKEVIRTUAL) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if ("net/minecraftforge/fml/common/eventhandler/Event".equals(methodInsn.owner) && "setup".equals(methodInsn.name)) {
                            Preconditions.checkState(insn.getPrevious().getOpcode() == ALOAD && ((VarInsnNode) insn.getPrevious()).var == 0);
                            resetMethod.instructions.remove(insn.getPrevious());
                            resetMethod.instructions.remove((insn = insn.getNext()).getPrevious());
                        }
                    }
                }
            }

            //redirect other constructor delegation to the static reset method
            for (AbstractInsnNode insn = resetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == INVOKESPECIAL) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("<init>".equals(methodInsn.name) && (classNode.name.equals(methodInsn.owner) || classNode.superName.equals(methodInsn.owner))) {
                        methodInsn.setOpcode(INVOKESTATIC);
                        methodInsn.name = "$ppatches_resetInstance";
                        methodInsn.desc = getResetMethodDesc(Type.getObjectType(methodInsn.owner), methodInsn.desc);
                    }
                }
            }

            //at the start of the method, initialize all the fields to their default values (if they haven't been initialized already)
            InsnList seq = new InsnList();

            if (false && "net/minecraftforge/fml/common/eventhandler/Event".equals(classNode.name)) {
                seq.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                seq.add(new LdcInsnNode("constructing event instance: "));
                seq.add(new VarInsnNode(ALOAD, 0));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getTypeName", "()Ljava/lang/String;", false));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
                for (AbstractInsnNode insn = ctor.instructions.getLast(); ; insn = insn.getPrevious()) {
                    if (insn.getOpcode() == RETURN) {
                        ctor.instructions.insertBefore(insn, seq);
                        break;
                    }
                }

                seq.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                seq.add(new LdcInsnNode("resetting event instance: "));
                seq.add(new VarInsnNode(ALOAD, 0));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getTypeName", "()Ljava/lang/String;", false));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
                seq.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
            }

            for (FieldNode field : classNode.fields) {
                if ((field.access & ACC_STATIC) != 0 //static fields don't need to be default initialized in an instance constructor, lol
                    || ((field.access & ACC_FINAL) != 0 && field.value != null)) { //final field with a compile-time constant value, it doesn't need to be reset
                    continue;
                }

                if (seq.size() == 0) {
                    seq.add(new LabelNode());
                }

                seq.add(new VarInsnNode(ALOAD, 0));
                seq.add(field.value != null ? new LdcInsnNode(field.value) : BytecodeHelper.loadConstantDefaultValueInsn(Type.getType(field.desc)));
                seq.add(new FieldInsnNode(PUTFIELD, classNode.name, field.name, field.desc)); //TODO: we'll need to have it overwrite final fields as well
            }
            resetMethod.instructions.insert(seq);

            //add a new method which will create/reset an event instance with the given constructor arguments, fire it to the given event bus, release the allocated instance and return
            //  the event bus result
            /*MethodNode fireMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, "$ppatches_fire", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, ctorArgumentTypes), null, null);
            methodItr.add(fireMethod);
            int cacheLvtIndex =*/
        }
        return true;
    }

    private static boolean transformEventBusPost(ClassNode classNode, MethodNode methodNode, MethodInsnNode invokePostInsn) {
        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);

        Frame<SourceValue> invokePostSources = sourceFrames[methodNode.instructions.indexOf(invokePostInsn)];
        Set<AbstractInsnNode> eventBusSource = BytecodeHelper.getStackValueFromTop(invokePostSources, 1).insns;
        Set<AbstractInsnNode> eventInstanceSource = BytecodeHelper.getStackValueFromTop(invokePostSources, 0).insns;

        FieldInsnNode getEventBusInsn;
        if (eventBusSource.size() != 1
            || eventBusSource.iterator().next().getOpcode() != GETSTATIC
            || !"net/minecraftforge/common/MinecraftForge".equals((getEventBusInsn = (FieldInsnNode) eventBusSource.iterator().next()).owner)) {
            return false;
        }

        if (eventInstanceSource.size() == 1 && eventInstanceSource.iterator().next().getOpcode() == NEW) {
            TypeInsnNode newEventInsn = (TypeInsnNode) eventInstanceSource.iterator().next();

            //the event is being fired in the form:
            //    MinecraftForge.SOME_EVENT_BUS.post(new SomeEvent(<ctor args...>))
            //we'll redirect the entire thing into a single invokedynamic instruction which consumes the event constructor arguments and returns the return value of
            //  calling EventBus#post(Event) with an instance of the event class configured as if it were initialized using the given constructor.

            AbstractInsnNode dupInsn = newEventInsn.getNext();
            Preconditions.checkState(dupInsn.getOpcode() == DUP);

            MethodInsnNode invokeCtorInsn = (MethodInsnNode) invokePostInsn.getPrevious();
            Preconditions.checkState(invokeCtorInsn.getOpcode() == INVOKESPECIAL && newEventInsn.desc.equals(invokeCtorInsn.owner) && "<init>".equals(invokeCtorInsn.name));

            methodNode.instructions.remove(getEventBusInsn);
            methodNode.instructions.remove(newEventInsn);
            methodNode.instructions.remove(dupInsn);
            methodNode.instructions.remove(invokeCtorInsn);
            methodNode.instructions.set(invokePostInsn, new InvokeDynamicInsnNode("post", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(invokeCtorInsn.desc)),
                    new Handle(H_INVOKESTATIC,
                            Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapSimpleInitAndPost",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                    new Handle(H_GETSTATIC, getEventBusInsn.owner, getEventBusInsn.name, getEventBusInsn.desc, false),
                    Type.getObjectType(newEventInsn.desc)));
            return true;
        }

        return false;
    }

    public static CallSite bootstrapPost(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle busGetter) throws Throwable {
        return new ConstantCallSite(MethodHandles.collectArguments(
                lookup.findVirtual(EventBus.class, "post", MethodType.methodType(boolean.class, Event.class)).asType(type.insertParameterTypes(0, EventBus.class)),
                0, busGetter));
    }

    private static Class<?> eventCacheClass(Class<?> eventClass) {
        return ArrayDeque.class;
    }

    /**
     * Gets a {@link CallSite} equivalent to the following code:
     * <pre>
     * static ArrayDeque&lt;EventClass&gt; getEventCache() {
     *     return EventClass.$ppatches_instanceCache.get();
     * }
     * </pre>
     */
    public static CallSite bootstrapGetEventCache(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> eventClass) throws Throwable {
        Preconditions.checkArgument(type.parameterCount() == 0 && type.returnType() == eventCacheClass(eventClass), type);

        return new ConstantCallSite(MethodHandles.filterReturnValue(
                lookup.findStaticGetter(eventClass, "$ppatches_instanceCache", ThreadLocal.class),
                lookup.findVirtual(ThreadLocal.class, "get", MethodType.methodType(Object.class))).asType(MethodType.methodType(eventCacheClass(eventClass))));
    }

    /**
     * Gets a {@link CallSite} equivalent to the following code:
     * <pre>
     * static EventClass allocateFromCacheAndInitializeEvent(ArrayDeque&lt;EventClass&gt; cache, ctor_args...) {
     *     if (cache.isEmpty()) {
     *         return new EventClass(ctor_args...);
     *     } else {
     *         EventClass eventInstance = cache.pop();
     *         EventClass.$ppatches_resetInstance(eventInstance, ctor_args...);
     *         return eventInstance;
     *     }
     * }
     * </pre>
     */
    public static CallSite bootstrapAllocateFromCacheAndInitializeEvent(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> eventClass) throws Throwable {
        assert eventCacheClass(eventClass) == ArrayDeque.class : eventCacheClass(eventClass); //make sure this crashes if i change the cache type
        Preconditions.checkArgument(type.parameterCount() > 0 && type.parameterType(0) == eventCacheClass(eventClass), type);
        Preconditions.checkArgument(type.returnType() == eventClass, type);

        MethodType typeWithEvent = type.changeParameterType(0, eventClass);
        MethodType typeWithoutCache = type.dropParameterTypes(0, 1);

        MethodHandle pop = lookup.findVirtual(ArrayDeque.class, "pop", MethodType.methodType(Object.class)).asType(MethodType.methodType(eventClass, ArrayDeque.class));
        MethodHandle reset = lookup.findStatic(eventClass, "$ppatches_resetInstance", typeWithEvent.changeReturnType(void.class));
        MethodHandle popAndReset = MethodHandles.filterArguments(
                MethodHandles.permuteArguments(
                        MethodHandles.collectArguments(MethodHandles.identity(eventClass), 1, reset),
                        typeWithEvent, IntStream.concat(IntStream.of(0), IntStream.range(0, type.parameterCount())).toArray()),
                0, pop);

        return new ConstantCallSite(MethodHandles.guardWithTest(
                MethodHandles.dropArguments(lookup.findVirtual(ArrayDeque.class, "isEmpty", MethodType.methodType(boolean.class)), 1, typeWithoutCache.parameterArray()),
                MethodHandles.dropArguments(lookup.findConstructor(eventClass, typeWithoutCache.changeReturnType(void.class)), 0, ArrayDeque.class),
                popAndReset));
    }

    /**
     * Gets a {@link CallSite} equivalent to the following code:
     * <pre>
     * static void releaseToCache(ArrayDeque&lt;EventClass&gt; cache, EventClass eventInstance) {
     *     return cache.push(eventInstance);
     * }
     * </pre>
     */
    public static CallSite bootstrapReleaseToCache(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> eventClass) throws Throwable {
        assert eventCacheClass(eventClass) == ArrayDeque.class : eventCacheClass(eventClass); //make sure this crashes if i change the cache type
        Preconditions.checkArgument(MethodType.methodType(void.class, eventCacheClass(eventClass), eventClass).equals(type), type);

        return new ConstantCallSite(lookup.findVirtual(ArrayDeque.class, "push", MethodType.methodType(void.class, Object.class)).asType(type));
    }

    /**
     * Gets a {@link CallSite} equivalent to the following code:
     * <pre>
     * static boolean simpleInitAndPost(ctor_args...) {
     *     ArrayDeque&lt;EventClass&gt; cache = getEventCache();
     *     EventClass eventInstance = allocateFromCacheAndInitializeEvent(cache, ctor_args...);
     *     boolean result = ((EventBus) busGetter.invokeExact()).post(eventInstance);
     *     releaseToCache(cache, eventInstance);
     *     return result;
     * }
     * </pre>
     *
     * @param busGetter  a {@link MethodHandle} which accepts no arguments and returns an {@link EventBus} instance for the event to be posted
     *                   to (see {@link #bootstrapPost(MethodHandles.Lookup, String, MethodType, MethodHandle)})
     */
    public static CallSite bootstrapSimpleInitAndPost(MethodHandles.Lookup lookup, String name, MethodType type,
                                                      MethodHandle busGetter, Class<? extends Event> eventClass) throws Throwable {
        Class<?> cacheClass = eventCacheClass(eventClass);

        MethodHandle getCache = bootstrapGetEventCache(lookup, name, MethodType.methodType(cacheClass), eventClass).dynamicInvoker();
        MethodHandle popAndResetOrAllocateNew = bootstrapAllocateFromCacheAndInitializeEvent(lookup, name, type.insertParameterTypes(0, cacheClass).changeReturnType(eventClass), eventClass).dynamicInvoker();
        MethodHandle post = bootstrapPost(lookup, name, MethodType.methodType(boolean.class, eventClass), busGetter).dynamicInvoker();
        MethodHandle release = bootstrapReleaseToCache(lookup, name, MethodType.methodType(void.class, cacheClass, eventClass), eventClass).dynamicInvoker();

        MethodHandle postAndRelease = MethodHandles.permuteArguments(
                MethodHandles.filterArguments(MethodHandles.collectArguments(MethodHandles.identity(boolean.class), 1, release), 0, post),
                MethodType.methodType(boolean.class, cacheClass, eventClass), 1, 0, 1);

        return new ConstantCallSite(MethodHandles.collectArguments(
                MethodHandles.permuteArguments(
                        MethodHandles.collectArguments(postAndRelease, 1, popAndResetOrAllocateNew),
                        type.insertParameterTypes(0, cacheClass), IntStream.concat(IntStream.of(0), IntStream.range(0, type.parameterCount() + 1)).toArray()),
                0, getCache));
    }
}
