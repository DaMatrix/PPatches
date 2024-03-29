package net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.OptimizeEventBusDispatchTransformer;
import net.daporkchop.ppatches.util.MethodHandleUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation.OptimizeEventInstanceAllocationTransformer_Events.isLikelyEventClass;
import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeEventInstanceAllocationTransformer_IndividualMethods implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.ExactInterested {
    @Override
    public int priority() {
        return 1600; //we want this to run after OptimzeCallbackInfoTransformer and OptimizeEventInstanceAllocationTransformer_Events
    }

    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesMethod("net/minecraftforge/fml/common/eventhandler/EventBus", "post", "(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z");
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;

        //try to transform non-event classes to make the event optimizations more effective
        changeFlags |= makeMethodsMoreOptimizable(classNode, methodNode, instructions);

        changeFlags |= checkForUnsafeEventHandlers(classNode, methodNode, instructions);

        List<MethodInsnNode> invokePostInsns = null;
        //first pass: scan for calls to EventBus#post(Object)
        for (ListIterator<AbstractInsnNode> itr = instructions.iterator(); itr.hasNext(); ) {
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
                changeFlags |= transformEventBusPost(classNode, methodNode, instructions, invokePostInsn);
            }
            invokePostInsns.clear();
        }

        return changeFlags;
    }

    private static int makeMethodsMoreOptimizable(ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;

        //redirect calls to methods duplicated above to their more optimized duplicate forms, if possible
        for (AbstractInsnNode currentInsn = instructions.getFirst(), next; currentInsn != null; currentInsn = next) {
            next = currentInsn.getNext();

            MethodInsnNode callInsn;
            Type returnType;
            if (currentInsn.getOpcode() != INVOKESTATIC
                || (returnType = Type.getReturnType((callInsn = (MethodInsnNode) currentInsn).desc)).getSort() != Type.OBJECT
                || !isLikelyEventClass(returnType.getInternalName())) {
                continue;
            }

            AbstractInsnNode nextInsn = BytecodeHelper.nextNormalCodeInstruction(callInsn);
            switch (nextInsn.getOpcode()) {
                case POP: //the returned event instance is immediately discarded
                    PPatchesMod.LOGGER.info("Redirecting INVOKESTATIC L{};{}{} to clone $ppatches_void_{}", callInsn.owner, callInsn.name, callInsn.desc, callInsn.name);
                    callInsn.name = "$ppatches_void_" + callInsn.name;
                    callInsn.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(callInsn.desc));
                    instructions.remove(nextInsn);
                    changeFlags |= CHANGED;
                    break;
                case INVOKEVIRTUAL:
                    MethodInsnNode nextCallInsn = (MethodInsnNode) nextInsn;
                    if ("isCanceled".equals(nextCallInsn.name) && "()Z".equals(nextCallInsn.desc)) {
                        PPatchesMod.LOGGER.info("Redirecting INVOKESTATIC L{};{}{} to clone $ppatches_isCanceled_{}", callInsn.owner, callInsn.name, callInsn.desc, callInsn.name);
                        callInsn.name = "$ppatches_isCanceled_" + callInsn.name;
                        callInsn.desc = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(callInsn.desc));
                        instructions.remove(nextInsn);
                        changeFlags |= CHANGED;
                    }
                    break;
            }
        }

        return changeFlags;
    }

    private static int checkForUnsafeEventHandlers(ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;

        if (!BytecodeHelper.hasAnnotationWithDesc(methodNode.visibleAnnotations, "Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;")) {
            return 0;
        }

        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);
        int eventInstanceLvtIndex = BytecodeHelper.isStatic(methodNode) ? 0 : 1;

        List<AbstractInsnNode> insertMarkUnsafeBeforeInsns = new ArrayList<>();

        //check if the event instance is used in some way which would prevent us from optimizing it
        INSTRUCTION:
        for (ListIterator<AbstractInsnNode> itr = instructions.iterator(); itr.hasNext(); ) {
            AbstractInsnNode consumingInsn = itr.next();
            if (!BytecodeHelper.isNormalCodeInstruction(consumingInsn)) {
                continue;
            }

            int insnIndex = instructions.indexOf(consumingInsn);
            Frame<SourceValue> sourceFrame = sourceFrames[insnIndex];
            if (sourceFrame == null) { //unreachable instruction, ignore
                continue;
            }

            int consumedStackOperands = BytecodeHelper.getConsumedStackOperandCount(consumingInsn, sourceFrame);
            for (int i = 0; i < consumedStackOperands; i++) {
                Set<AbstractInsnNode> sourceInsns = BytecodeHelper.getStackValueFromTop(sourceFrame, i).insns;
                if (sourceInsns.stream().anyMatch(sourceInsn -> sourceInsn.getOpcode() == ALOAD && ((VarInsnNode) sourceInsn).var == eventInstanceLvtIndex)) {
                    switch (consumingInsn.getOpcode()) {
                        default:
                            insertMarkUnsafeBeforeInsns.add(consumingInsn);
                            continue INSTRUCTION;
                        case INVOKEVIRTUAL:
                        case INVOKESPECIAL:
                            if (i != consumedStackOperands - 1) {
                                insertMarkUnsafeBeforeInsns.add(consumingInsn);
                                continue INSTRUCTION;
                            }
                            break;
                        case GETFIELD:
                        case IFNULL:
                        case IFNONNULL:
                        case IF_ACMPEQ:
                        case IF_ACMPNE:
                            break;
                    }
                }
            }
        }

        //TODO: don't re-use instances if they've been used unsafely
        for (AbstractInsnNode insn : insertMarkUnsafeBeforeInsns) {
            instructions.insertBefore(insn, new VarInsnNode(ALOAD, eventInstanceLvtIndex));
            instructions.insertBefore(insn, new MethodInsnNode(INVOKEVIRTUAL, Type.getArgumentTypes(methodNode.desc)[0].getInternalName(), "$ppatches_markUnsafe", "()V", false));
            changeFlags |= CHANGED_MANDATORY;
        }

        return changeFlags;
    }

    private static int transformEventBusPost(ClassNode classNode, MethodNode methodNode, InsnList instructions, MethodInsnNode invokePostInsn) {
        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);

        Frame<SourceValue> invokePostSources = sourceFrames[instructions.indexOf(invokePostInsn)];
        if (invokePostSources == null) { //unreachable instruction, ignore
            return 0;
        }

        Set<AbstractInsnNode> eventBusSources = BytecodeHelper.getStackValueFromTop(invokePostSources, 1).insns;
        Set<AbstractInsnNode> eventInstanceSources = BytecodeHelper.getStackValueFromTop(invokePostSources, 0).insns;

        boolean isConstantEventBus = false; //TODO: enable this (it works fine, but H_GETSTATIC seems to break decompilers)
        FieldInsnNode getEventBusInsn = null;
        if (eventBusSources.size() != 1
            || eventBusSources.iterator().next().getOpcode() != GETSTATIC
            || !"net/minecraftforge/common/MinecraftForge".equals((getEventBusInsn = (FieldInsnNode) eventBusSources.iterator().next()).owner)) {
            isConstantEventBus = false;
        }

        if (eventInstanceSources.size() != 1) {
            return 0;
        }

        AbstractInsnNode eventInstanceSource = eventInstanceSources.iterator().next();

        if (eventInstanceSource.getOpcode() == NEW) {
            TypeInsnNode newEventInsn = (TypeInsnNode) eventInstanceSources.iterator().next();

            //the event is being fired in the form:
            //    MinecraftForge.SOME_EVENT_BUS.post(new EventClass(<ctor_args...>))
            //we'll redirect the entire thing into a single invokedynamic instruction which consumes the event constructor arguments and returns the return value of
            //  calling EventBus#post(Event) with an instance of the event class configured as if it were initialized using the given constructor.

            AbstractInsnNode dupInsn = BytecodeHelper.nextNormalCodeInstruction(newEventInsn);
            Preconditions.checkState(dupInsn.getOpcode() == DUP);

            MethodInsnNode invokeCtorInsn = (MethodInsnNode) BytecodeHelper.previousNormalCodeInstruction(invokePostInsn);
            Preconditions.checkState(invokeCtorInsn.getOpcode() == INVOKESPECIAL && newEventInsn.desc.equals(invokeCtorInsn.owner) && "<init>".equals(invokeCtorInsn.name));

            instructions.remove(newEventInsn);
            instructions.remove(dupInsn);
            instructions.remove(invokeCtorInsn);
            if (isConstantEventBus) {
                instructions.remove(getEventBusInsn);
                instructions.set(invokePostInsn, new InvokeDynamicInsnNode("simpleInitAndPostToConstantBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(invokeCtorInsn.desc)),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapSimpleInitAndPostToConstantBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                        new Handle(H_GETSTATIC, getEventBusInsn.owner, getEventBusInsn.name, getEventBusInsn.desc, false),
                        Type.getObjectType(newEventInsn.desc)));
            } else {
                instructions.set(invokePostInsn, new InvokeDynamicInsnNode("simpleInitAndPostToDynamicBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Stream.concat(Stream.of(Type.getObjectType("net/minecraftforge/fml/common/eventhandler/EventBus")), Stream.of(Type.getArgumentTypes(invokeCtorInsn.desc))).toArray(Type[]::new)),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapSimpleInitAndPostToDynamicBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                        Type.getObjectType(newEventInsn.desc)));
            }
            return CHANGED;
        }

        if (eventInstanceSource.getOpcode() == ALOAD) { //the event instance passed to EventBus#post(Event) comes from a local variable
            int eventInstanceLvtIndex = ((VarInsnNode) eventInstanceSource).var;

            //find instruction(s) which store the event instance into the local variable
            Set<AbstractInsnNode> eventInstanceVariableSources = sourceFrames[instructions.indexOf(eventInstanceSource)].getLocal(eventInstanceLvtIndex).insns;
            AbstractInsnNode eventInstanceVariableSource;
            if (eventInstanceVariableSources.size() != 1
                || (eventInstanceVariableSource = eventInstanceVariableSources.iterator().next()).getOpcode() != ASTORE) {
                return 0;
            }

            //find instruction(s) which push the event instance onto the stack before it can be stored to the local variable
            //  (ideally a NEW)
            Set<AbstractInsnNode> eventInstanceCreationSources = BytecodeHelper.getStackValueFromTop(sourceFrames[instructions.indexOf(eventInstanceVariableSource)], 0).insns;
            AbstractInsnNode eventInstanceCreationSource;
            if (eventInstanceCreationSources.size() != 1
                || (eventInstanceCreationSource = eventInstanceCreationSources.iterator().next()).getOpcode() != NEW) {
                return 0;
            }

            //check if the event instance is used in some way which would prevent us from optimizing it
            for (ListIterator<AbstractInsnNode> itr = instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode consumingInsn = itr.next();
                if (!BytecodeHelper.isNormalCodeInstruction(consumingInsn) || consumingInsn == invokePostInsn) {
                    continue;
                }

                int insnIndex = instructions.indexOf(consumingInsn);
                Frame<SourceValue> sourceFrame = sourceFrames[insnIndex];
                if (sourceFrame == null) { //unreachable instruction, ignore
                    continue;
                }

                int consumedStackOperands = BytecodeHelper.getConsumedStackOperandCount(consumingInsn, sourceFrame);
                for (int i = 0; i < consumedStackOperands; i++) {
                    Set<AbstractInsnNode> sourceInsns = BytecodeHelper.getStackValueFromTop(sourceFrame, i).insns;
                    if (sourceInsns.stream().anyMatch(sourceInsn -> sourceInsn.getOpcode() == ALOAD && ((VarInsnNode) sourceInsn).var == eventInstanceLvtIndex
                                                                    && sourceFrames[instructions.indexOf(sourceInsn)].getLocal(eventInstanceLvtIndex).insns.contains(eventInstanceVariableSource))) {
                        if (sourceInsns.size() != 1) {
                            //the instruction doesn't always refer to our event instance, which results in some extra complicated logic that can't be easily optimized away
                            return 0;
                        }
                        switch (consumingInsn.getOpcode()) {
                            default:
                                return 0;
                            case INVOKEVIRTUAL:
                            case INVOKESPECIAL:
                                if (i != consumedStackOperands - 1) {
                                    return 0;
                                }
                                break;
                            case GETFIELD:
                            case IFNULL:
                            case IFNONNULL:
                            case IF_ACMPEQ:
                            case IF_ACMPNE:
                                break;
                        }
                    }
                }
            }

            TypeInsnNode newEventInsn = (TypeInsnNode) eventInstanceCreationSource;
            Type eventType = Type.getObjectType(newEventInsn.desc);

            AbstractInsnNode dupInsn = BytecodeHelper.nextNormalCodeInstruction(newEventInsn);
            Preconditions.checkState(dupInsn.getOpcode() == DUP);

            MethodInsnNode invokeCtorInsn = (MethodInsnNode) BytecodeHelper.previousNormalCodeInstruction(eventInstanceVariableSource);
            Preconditions.checkState(invokeCtorInsn.getOpcode() == INVOKESPECIAL && newEventInsn.desc.equals(invokeCtorInsn.owner) && "<init>".equals(invokeCtorInsn.name));

            List<AbstractInsnNode> exitScopeInsns = new ArrayList<>();

            LocalVariableNode eventLocalVariable = null;
            for (LocalVariableNode localVariableNode : methodNode.localVariables) {
                if (localVariableNode.index == eventInstanceLvtIndex
                    && instructions.indexOf(localVariableNode.start) <= instructions.indexOf(eventInstanceSource)
                    && instructions.indexOf(localVariableNode.end) >= instructions.indexOf(eventInstanceSource)) {
                    Preconditions.checkState(eventLocalVariable == null);
                    eventLocalVariable = localVariableNode;
                }
            }
            Preconditions.checkState(eventLocalVariable != null);

            int returnOpcode = Type.getReturnType(methodNode.desc).getOpcode(IRETURN);
            for (AbstractInsnNode currentInsn = eventInstanceVariableSource.getNext(); currentInsn != eventLocalVariable.end; currentInsn = currentInsn.getNext()) {
                if (currentInsn.getOpcode() == returnOpcode) { //method return
                    exitScopeInsns.add(currentInsn);
                } else if (currentInsn instanceof JumpInsnNode) { //jumping out of the scope
                    LabelNode target = ((JumpInsnNode) currentInsn).label;
                    if (instructions.indexOf(eventLocalVariable.start) > instructions.indexOf(target)
                        || instructions.indexOf(eventLocalVariable.end) < instructions.indexOf(target)) {
                        exitScopeInsns.add(currentInsn);
                    }
                } //TODO: handle switches (they could also jump out of scope, although not with normal java syntax)
            }
            exitScopeInsns.add(eventLocalVariable.end);

            //TODO: somehow figure out the smallest possible scope in which the event is accessed
            Type cacheType = eventCacheType(eventType);
            int cacheLvtIndex = BytecodeHelper.findUnusedLvtSlot(methodNode, cacheType, true);
            methodNode.localVariables.add(new LocalVariableNode("$ppatches_cache", cacheType.getDescriptor(), eventCacheTypeSignature(eventType),
                    eventLocalVariable.start, eventLocalVariable.end, cacheLvtIndex));

            BytecodeHelper.replace(newEventInsn, instructions,
                    new InvokeDynamicInsnNode("getEventCache", Type.getMethodDescriptor(cacheType),
                            new Handle(H_INVOKESTATIC,
                                    Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapGetEventCache",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                            eventType),
                    new VarInsnNode(ASTORE, cacheLvtIndex),
                    new VarInsnNode(ALOAD, cacheLvtIndex));
            instructions.remove(dupInsn);

            instructions.set(invokeCtorInsn,
                    new InvokeDynamicInsnNode("allocateFromCacheAndInitializeEvent", Type.getMethodDescriptor(eventType, Stream.concat(Stream.of(cacheType), Stream.of(Type.getArgumentTypes(invokeCtorInsn.desc))).toArray(Type[]::new)),
                            new Handle(H_INVOKESTATIC,
                                    Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapAllocateFromCacheAndInitializeEvent",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                            eventType));

            if (isConstantEventBus) {
                instructions.remove(getEventBusInsn);
                instructions.set(invokePostInsn, new InvokeDynamicInsnNode("postToConstantBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, eventType),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapPostToConstantBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                        new Handle(H_GETSTATIC, getEventBusInsn.owner, getEventBusInsn.name, getEventBusInsn.desc, false)));
            } else {
                instructions.set(invokePostInsn, new InvokeDynamicInsnNode("postToDynamicBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getObjectType("net/minecraftforge/fml/common/eventhandler/EventBus"), eventType),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapPostToDynamicBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false)));
            }

            for (AbstractInsnNode exitScopeInsn : exitScopeInsns) {
                BytecodeHelper.insertBefore(exitScopeInsn, instructions,
                        new VarInsnNode(ALOAD, cacheLvtIndex),
                        new VarInsnNode(ALOAD, eventInstanceLvtIndex),
                        new InvokeDynamicInsnNode("releaseToCache", Type.getMethodDescriptor(Type.VOID_TYPE, cacheType, eventType),
                                new Handle(H_INVOKESTATIC,
                                        Type.getInternalName(OptimizeEventInstanceAllocationTransformer_IndividualMethods.class), "bootstrapReleaseToCache",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                                eventType));
            }

            return CHANGED;
        }

        return 0;
    }

    public static CallSite bootstrapPostToConstantBus(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle busGetter) throws Throwable {
        Preconditions.checkArgument(type.parameterCount() == 1 && type.returnType() == boolean.class, type);

        if (PPatchesConfig.forge_optimizeEventBusDispatch.isEnabled()) {
            return OptimizeEventBusDispatchTransformer.bootstrapEventBusPost(lookup, name, type, busGetter);
        }

        return new ConstantCallSite(MethodHandles.collectArguments(
                lookup.findVirtual(EventBus.class, "post", MethodType.methodType(boolean.class, Event.class)).asType(type.insertParameterTypes(0, EventBus.class)),
                0, busGetter));
    }

    public static CallSite bootstrapPostToDynamicBus(MethodHandles.Lookup lookup, String name, MethodType type) throws Throwable {
        Preconditions.checkArgument(type.parameterCount() == 2 && type.parameterType(0) == EventBus.class && type.returnType() == boolean.class, type);

        return new ConstantCallSite(lookup.findVirtual(EventBus.class, "post", MethodType.methodType(boolean.class, Event.class)).asType(type));
    }

    private static Type eventCacheType(Type eventType) {
        return Type.getType(ArrayDeque.class);
    }

    private static String eventCacheTypeSignature(Type eventType) {
        SignatureWriter writer = new SignatureWriter();
        writer.visitClassType(eventCacheType(eventType).getInternalName());
        writer.visitTypeArgument();
        writer.visitClassType(eventType.getInternalName());
        writer.visitEnd();
        return writer.toString();
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
     *     if (!eventInstance.$ppatches_usedUnsafely) {
     *         cache.push(eventInstance);
     *     }
     * }
     * </pre>
     */
    public static CallSite bootstrapReleaseToCache(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> eventClass) throws Throwable {
        assert eventCacheClass(eventClass) == ArrayDeque.class : eventCacheClass(eventClass); //make sure this crashes if i change the cache type
        Preconditions.checkArgument(MethodType.methodType(void.class, eventCacheClass(eventClass), eventClass).equals(type), type);

        return new ConstantCallSite(MethodHandles.guardWithTest(
                MethodHandles.dropArguments(lookup.findGetter(eventClass, "$ppatches_usedUnsafely", boolean.class), 0, eventCacheClass(eventClass)),
                MethodHandles.dropArguments(MethodHandleUtils.identity(void.class), 0, type.parameterArray()),
                lookup.findVirtual(ArrayDeque.class, "push", MethodType.methodType(void.class, Object.class)).asType(type)));
    }

    /**
     * Gets a {@link CallSite} equivalent to the following code:
     * <pre>
     * static boolean simpleInitAndPostToConstantBus(ctor_args...) {
     *     ArrayDeque&lt;EventClass&gt; cache = getEventCache();
     *     EventClass eventInstance = allocateFromCacheAndInitializeEvent(cache, ctor_args...);
     *     boolean result = ((EventBus) busGetter.invokeExact()).post(eventInstance);
     *     releaseToCache(cache, eventInstance);
     *     return result;
     * }
     * </pre>
     *
     * @param busGetter a {@link MethodHandle} which accepts no arguments and returns an {@link EventBus} instance for the event to be posted
     *                  to (see {@link #bootstrapPostToConstantBus(MethodHandles.Lookup, String, MethodType, MethodHandle)})
     */
    public static CallSite bootstrapSimpleInitAndPostToConstantBus(MethodHandles.Lookup lookup, String name, MethodType type,
                                                                   MethodHandle busGetter, Class<? extends Event> eventClass) throws Throwable {
        Class<?> cacheClass = eventCacheClass(eventClass);

        MethodHandle getCache = bootstrapGetEventCache(lookup, name, MethodType.methodType(cacheClass), eventClass).dynamicInvoker();
        MethodHandle popAndResetOrAllocateNew = bootstrapAllocateFromCacheAndInitializeEvent(lookup, name, type.insertParameterTypes(0, cacheClass).changeReturnType(eventClass), eventClass).dynamicInvoker();
        MethodHandle post = bootstrapPostToConstantBus(lookup, name, MethodType.methodType(boolean.class, eventClass), busGetter).dynamicInvoker();
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

    /**
     * Gets a {@link CallSite} equivalent to the following code:
     * <pre>
     * static boolean simpleInitAndPostToDynamicBus(EventBus bus, ctor_args...) {
     *     ArrayDeque&lt;EventClass&gt; cache = getEventCache();
     *     EventClass eventInstance = allocateFromCacheAndInitializeEvent(cache, ctor_args...);
     *     boolean result = bus.post(eventInstance);
     *     releaseToCache(cache, eventInstance);
     *     return result;
     * }
     * </pre>
     */
    public static CallSite bootstrapSimpleInitAndPostToDynamicBus(MethodHandles.Lookup lookup, String name, MethodType type, Class<? extends Event> eventClass) throws Throwable {
        Class<?> cacheClass = eventCacheClass(eventClass);

        MethodHandle getCache = bootstrapGetEventCache(lookup, name, MethodType.methodType(cacheClass), eventClass).dynamicInvoker();
        MethodHandle popAndResetOrAllocateNew = bootstrapAllocateFromCacheAndInitializeEvent(lookup, name, type.changeParameterType(0, cacheClass).changeReturnType(eventClass), eventClass).dynamicInvoker();
        MethodHandle post = bootstrapPostToDynamicBus(lookup, name, MethodType.methodType(boolean.class, EventBus.class, eventClass)).dynamicInvoker();
        MethodHandle release = bootstrapReleaseToCache(lookup, name, MethodType.methodType(void.class, cacheClass, eventClass), eventClass).dynamicInvoker();

        MethodHandle postAndRelease = MethodHandles.permuteArguments(
                MethodHandles.collectArguments(MethodHandles.collectArguments(MethodHandles.identity(boolean.class), 1, release), 0, post),
                MethodType.methodType(boolean.class, EventBus.class, cacheClass, eventClass), 0, 2, 1, 2);

        return new ConstantCallSite(MethodHandles.collectArguments(
                MethodHandles.permuteArguments(
                        MethodHandles.collectArguments(postAndRelease, 2, popAndResetOrAllocateNew),
                        type.insertParameterTypes(1, cacheClass), IntStream.concat(IntStream.of(0, 1), IntStream.range(1, type.parameterCount() + 1)).toArray()),
                1, getCache));
    }
}
