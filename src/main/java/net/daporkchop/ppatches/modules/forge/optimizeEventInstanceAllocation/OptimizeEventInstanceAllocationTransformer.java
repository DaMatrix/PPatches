package net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.MethodHandleUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.InvokeDynamicUtils;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.invoke.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
                && !name.startsWith("net.minecraft.") && name.indexOf('.') >= 0
                && ("net.minecraftforge.fml.common.eventhandler.Event".equals(transformedName) || Event.class.isAssignableFrom(this.getClass().getClassLoader().loadClass(classNode.superName.replace('/', '.'))))) {
                anyChanged |= examineAndTransformEventClass(classNode);
            }
        } catch (ClassNotFoundException e) {
            //Forge's EventSubscriptionTransformer silently ignores these exceptions, we'll do the same
        }

        //try to transform non-event classes to make the event optimizations more effective
        anyChanged |= this.makeMethodsMoreOptimizable(classNode);

        anyChanged |= checkForUnsafeEventHandlers(classNode);

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

    private boolean isLikelyEventClass(String internalName) {
        return internalName.contains("Event");
    }

    private boolean makeMethodsMoreOptimizable(ClassNode classNode) {
        boolean anyChanged = false;

        for (ListIterator<MethodNode> methodItr = classNode.methods.listIterator(); methodItr.hasNext(); ) {
            MethodNode method = methodItr.next();
            Type returnType = Type.getReturnType(method.desc);
            if ((method.access & ACC_STATIC) == 0 //we're only targetting static utility methods for now
                || returnType.getSort() != Type.OBJECT || !this.isLikelyEventClass(returnType.getInternalName())) {
                continue;
            }

            anyChanged = true;

            //the method returns an event instance, which is probably not necessary. we'll add a variant which returns nothing and a variant which returns the result of isCancelled,
            //  which other code can then be optimized to use.

            PPatchesMod.LOGGER.info("Adding clones of method L{};{}{} which return void and Event#isCanceled()", classNode.name, method.name, method.desc);

            MethodNode returnVoidMethod = BytecodeHelper.cloneMethod(method);
            methodItr.add(returnVoidMethod);
            returnVoidMethod.name = "$ppatches_void_" + returnVoidMethod.name;
            returnVoidMethod.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(returnVoidMethod.desc));
            for (ListIterator<AbstractInsnNode> itr = returnVoidMethod.instructions.iterator(); itr.hasNext(); ) {
                if (itr.next().getOpcode() == ARETURN) {
                    itr.set(new InsnNode(RETURN));
                }
            }

            MethodNode returnIsCancelledMethod = BytecodeHelper.cloneMethod(method);
            methodItr.add(returnIsCancelledMethod);
            returnIsCancelledMethod.name = "$ppatches_isCanceled_" + returnIsCancelledMethod.name;
            returnIsCancelledMethod.desc = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(returnIsCancelledMethod.desc));
            for (ListIterator<AbstractInsnNode> itr = returnIsCancelledMethod.instructions.iterator(); itr.hasNext(); ) {
                if (itr.next().getOpcode() == ARETURN) {
                    itr.set(new MethodInsnNode(INVOKEVIRTUAL, returnType.getInternalName(), "isCanceled", "()Z", false));
                    itr.add(new InsnNode(IRETURN));
                }
            }
        }

        //redirect calls to methods duplicated above to their more optimized duplicate forms, if possible
        for (MethodNode methodNode : classNode.methods) {
            for (AbstractInsnNode currentInsn = methodNode.instructions.getFirst(); currentInsn != null; currentInsn = currentInsn.getNext()) {
                MethodInsnNode callInsn;
                Type returnType;
                if (currentInsn.getOpcode() != INVOKESTATIC
                    || (returnType = Type.getReturnType((callInsn = (MethodInsnNode) currentInsn).desc)).getSort() != Type.OBJECT
                    || !this.isLikelyEventClass(returnType.getInternalName())) {
                    continue;
                }

                AbstractInsnNode nextInsn = BytecodeHelper.nextNormalCodeInstruction(callInsn);
                switch (nextInsn.getOpcode()) {
                    case POP: //the returned event instance is immediately discarded
                        PPatchesMod.LOGGER.info("Redirecting INVOKESTATIC L{};{}{} to clone $ppatches_void_{}", callInsn.owner, callInsn.name, callInsn.desc, callInsn.name);
                        callInsn.name = "$ppatches_void_" + callInsn.name;
                        callInsn.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(callInsn.desc));
                        methodNode.instructions.remove(nextInsn);
                        anyChanged = true;
                        break;
                    case INVOKEVIRTUAL:
                        MethodInsnNode nextCallInsn = (MethodInsnNode) nextInsn;
                        if ("isCanceled".equals(nextCallInsn.name) && "()Z".equals(nextCallInsn.desc)) {
                            PPatchesMod.LOGGER.info("Redirecting INVOKESTATIC L{};{}{} to clone $ppatches_isCanceled_{}", callInsn.owner, callInsn.name, callInsn.desc, callInsn.name);
                            callInsn.name = "$ppatches_isCanceled_" + callInsn.name;
                            callInsn.desc = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(callInsn.desc));
                            methodNode.instructions.remove(nextInsn);
                            anyChanged = true;
                        }
                        break;
                }
            }
        }

        return anyChanged;
    }

    private static boolean checkForUnsafeEventHandlers(ClassNode classNode) {
        boolean anyChanged = false;

        for (MethodNode methodNode : classNode.methods) {
            if (!BytecodeHelper.findAnnotationByDesc(methodNode.visibleAnnotations, "Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;").isPresent()) {
                continue;
            }

            Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);
            int eventInstanceLvtIndex = BytecodeHelper.isStatic(methodNode) ? 0 : 1;

            List<AbstractInsnNode> insertMarkUnsafeBeforeInsns = new ArrayList<>();

            //check if the event instance is used in some way which would prevent us from optimizing it
            INSTRUCTION:
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode consumingInsn = itr.next();
                if (!BytecodeHelper.isNormalCodeInstruction(consumingInsn)) {
                    continue;
                }

                int insnIndex = methodNode.instructions.indexOf(consumingInsn);
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
                methodNode.instructions.insertBefore(insn, new VarInsnNode(ALOAD, eventInstanceLvtIndex));
                methodNode.instructions.insertBefore(insn, new MethodInsnNode(INVOKEVIRTUAL, Type.getArgumentTypes(methodNode.desc)[0].getInternalName(), "$ppatches_markUnsafe", "()V", false));
                anyChanged = true;
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

                InsnList seq = new InsnList();
                LabelNode tailLbl = new LabelNode();
                seq.add(new LabelNode());
                seq.add(InvokeDynamicUtils.makeLoadAssertionStateInsn());
                seq.add(new JumpInsnNode(IFEQ, tailLbl));
                seq.add(new VarInsnNode(ALOAD, 0));
                seq.add(new FieldInsnNode(GETFIELD, classNode.name, "$ppatches_usedUnsafely", "Z"));
                seq.add(new JumpInsnNode(IFEQ, tailLbl));
                seq.add(InvokeDynamicUtils.makeNewException(AssertionError.class, "trying to reset event instance which was used unsafely!"));
                seq.add(new InsnNode(ATHROW));
                seq.add(tailLbl);
                resetMethod.instructions.insert(seq);
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
            }
            if (false && "net/minecraftforge/fml/common/eventhandler/Event".equals(classNode.name)) {
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

                //make the field not be final
                field.access &= ~ACC_FINAL;

                if (seq.size() == 0) {
                    seq.add(new LabelNode());
                }

                seq.add(new VarInsnNode(ALOAD, 0));
                seq.add(field.value != null ? new LdcInsnNode(field.value) : BytecodeHelper.loadConstantDefaultValueInsn(Type.getType(field.desc)));
                seq.add(new FieldInsnNode(PUTFIELD, classNode.name, field.name, field.desc)); //TODO: we'll need to have it overwrite final fields as well
            }
            resetMethod.instructions.insert(seq);
        }

        if ("net/minecraftforge/fml/common/eventhandler/Event".equals(classNode.name)) {
            (classNode.interfaces == null ? classNode.interfaces = new ArrayList<>() : classNode.interfaces).add(Type.getInternalName(Cloneable.class));
        }

        {
            MethodNode cloneMethod = new MethodNode(ACC_PUBLIC, "$ppatches_clone", Type.getMethodDescriptor(Type.getObjectType(classNode.name)), null, null);
            classNode.methods.add(cloneMethod);
            cloneMethod.instructions.add(new VarInsnNode(ALOAD, 0));
            cloneMethod.instructions.add(new MethodInsnNode(INVOKESPECIAL, classNode.superName, "clone", "()Ljava/lang/Object;", false));
            cloneMethod.instructions.add(new TypeInsnNode(CHECKCAST, classNode.name));
            cloneMethod.instructions.add(new InsnNode(ARETURN));
        }

        if ("net/minecraftforge/fml/common/eventhandler/Event".equals(classNode.name)) {
            classNode.fields.add(new FieldNode(ACC_PUBLIC, "$ppatches_usedUnsafely", "Z", null, null));

            MethodNode markUnsafeMethod = new MethodNode(ACC_PUBLIC | ACC_FINAL, "$ppatches_markUnsafe", "()V", null, null);
            classNode.methods.add(markUnsafeMethod);
            markUnsafeMethod.instructions.add(new VarInsnNode(ALOAD, 0));
            markUnsafeMethod.instructions.add(new InsnNode(ICONST_1));
            markUnsafeMethod.instructions.add(new FieldInsnNode(PUTFIELD, classNode.name, "$ppatches_usedUnsafely", "Z"));

            if (false) {
                markUnsafeMethod.instructions.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                markUnsafeMethod.instructions.add(new VarInsnNode(ALOAD, 0));
                markUnsafeMethod.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false));
                markUnsafeMethod.instructions.add(new LdcInsnNode(" was used unsafely!"));
                markUnsafeMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
                markUnsafeMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
            }

            markUnsafeMethod.instructions.add(new InsnNode(RETURN));
        }

        return true;
    }

    private static boolean transformEventBusPost(ClassNode classNode, MethodNode methodNode, MethodInsnNode invokePostInsn) {
        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);

        Frame<SourceValue> invokePostSources = sourceFrames[methodNode.instructions.indexOf(invokePostInsn)];
        if (invokePostSources == null) { //unreachable instruction, ignore
            return false;
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
            return false;
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

            methodNode.instructions.remove(newEventInsn);
            methodNode.instructions.remove(dupInsn);
            methodNode.instructions.remove(invokeCtorInsn);
            if (isConstantEventBus) {
                methodNode.instructions.remove(getEventBusInsn);
                methodNode.instructions.set(invokePostInsn, new InvokeDynamicInsnNode("simpleInitAndPostToConstantBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(invokeCtorInsn.desc)),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapSimpleInitAndPostToConstantBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                        new Handle(H_GETSTATIC, getEventBusInsn.owner, getEventBusInsn.name, getEventBusInsn.desc, false),
                        Type.getObjectType(newEventInsn.desc)));
            } else {
                methodNode.instructions.set(invokePostInsn, new InvokeDynamicInsnNode("simpleInitAndPostToDynamicBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Stream.concat(Stream.of(Type.getType(EventBus.class)), Stream.of(Type.getArgumentTypes(invokeCtorInsn.desc))).toArray(Type[]::new)),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapSimpleInitAndPostToDynamicBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                        Type.getObjectType(newEventInsn.desc)));
            }
            return true;
        }

        if (eventInstanceSource.getOpcode() == ALOAD) { //the event instance passed to EventBus#post(Event) comes from a local variable
            int eventInstanceLvtIndex = ((VarInsnNode) eventInstanceSource).var;

            //find instruction(s) which store the event instance into the local variable
            Set<AbstractInsnNode> eventInstanceVariableSources = sourceFrames[methodNode.instructions.indexOf(eventInstanceSource)].getLocal(eventInstanceLvtIndex).insns;
            AbstractInsnNode eventInstanceVariableSource;
            if (eventInstanceVariableSources.size() != 1
                || (eventInstanceVariableSource = eventInstanceVariableSources.iterator().next()).getOpcode() != ASTORE) {
                return false;
            }

            //find instruction(s) which push the event instance onto the stack before it can be stored to the local variable
            //  (ideally a NEW)
            Set<AbstractInsnNode> eventInstanceCreationSources = BytecodeHelper.getStackValueFromTop(sourceFrames[methodNode.instructions.indexOf(eventInstanceVariableSource)], 0).insns;
            AbstractInsnNode eventInstanceCreationSource;
            if (eventInstanceCreationSources.size() != 1
                || (eventInstanceCreationSource = eventInstanceCreationSources.iterator().next()).getOpcode() != NEW) {
                return false;
            }

            //check if the event instance is used in some way which would prevent us from optimizing it
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode consumingInsn = itr.next();
                if (!BytecodeHelper.isNormalCodeInstruction(consumingInsn) || consumingInsn == invokePostInsn) {
                    continue;
                }

                int insnIndex = methodNode.instructions.indexOf(consumingInsn);
                Frame<SourceValue> sourceFrame = sourceFrames[insnIndex];
                if (sourceFrame == null) { //unreachable instruction, ignore
                    continue;
                }

                int consumedStackOperands = BytecodeHelper.getConsumedStackOperandCount(consumingInsn, sourceFrame);
                for (int i = 0; i < consumedStackOperands; i++) {
                    Set<AbstractInsnNode> sourceInsns = BytecodeHelper.getStackValueFromTop(sourceFrame, i).insns;
                    if (sourceInsns.stream().anyMatch(sourceInsn -> sourceInsn.getOpcode() == ALOAD && ((VarInsnNode) sourceInsn).var == eventInstanceLvtIndex
                                                                    && sourceFrames[methodNode.instructions.indexOf(sourceInsn)].getLocal(eventInstanceLvtIndex).insns.contains(eventInstanceVariableSource))) {
                        if (sourceInsns.size() != 1) {
                            //the instruction doesn't always refer to our event instance, which results in some extra complicated logic that can't be easily optimized away
                            return false;
                        }
                        switch (consumingInsn.getOpcode()) {
                            default:
                                return false;
                            case INVOKEVIRTUAL:
                            case INVOKESPECIAL:
                                if (i != consumedStackOperands - 1) {
                                    return false;
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
                    && methodNode.instructions.indexOf(localVariableNode.start) <= methodNode.instructions.indexOf(eventInstanceSource)
                    && methodNode.instructions.indexOf(localVariableNode.end) >= methodNode.instructions.indexOf(eventInstanceSource)) {
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
                    if (methodNode.instructions.indexOf(eventLocalVariable.start) > methodNode.instructions.indexOf(target)
                        || methodNode.instructions.indexOf(eventLocalVariable.end) < methodNode.instructions.indexOf(target)) {
                        exitScopeInsns.add(currentInsn);
                    }
                } //TODO: handle switches (they could also jump out of scope, although not with normal java syntax)
            }
            exitScopeInsns.add(eventLocalVariable.end);

            //TODO: somehow figure out the smallest possible scope in which the event is accessed
            Type cacheType = eventCacheType(eventType);
            int cacheLvtIndex = BytecodeHelper.findUnusedLvtSlot(methodNode, cacheType);
            methodNode.localVariables.add(new LocalVariableNode("$ppatches_cache", cacheType.getDescriptor(), eventCacheTypeSignature(eventType),
                    eventLocalVariable.start, eventLocalVariable.end, cacheLvtIndex));
            methodNode.maxLocals = Math.max(methodNode.maxLocals, cacheLvtIndex + 1);

            BytecodeHelper.replace(newEventInsn, methodNode.instructions,
                    new InvokeDynamicInsnNode("getEventCache", Type.getMethodDescriptor(cacheType),
                            new Handle(H_INVOKESTATIC,
                                    Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapGetEventCache",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                            eventType),
                    new VarInsnNode(ASTORE, cacheLvtIndex),
                    new VarInsnNode(ALOAD, cacheLvtIndex));
            methodNode.instructions.remove(dupInsn);

            methodNode.instructions.set(invokeCtorInsn,
                    new InvokeDynamicInsnNode("allocateFromCacheAndInitializeEvent", Type.getMethodDescriptor(eventType, Stream.concat(Stream.of(cacheType), Stream.of(Type.getArgumentTypes(invokeCtorInsn.desc))).toArray(Type[]::new)),
                            new Handle(H_INVOKESTATIC,
                                    Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapAllocateFromCacheAndInitializeEvent",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                            eventType));

            if (isConstantEventBus) {
                methodNode.instructions.remove(getEventBusInsn);
                methodNode.instructions.set(invokePostInsn, new InvokeDynamicInsnNode("postToConstantBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, eventType),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapPostToConstantBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                        new Handle(H_GETSTATIC, getEventBusInsn.owner, getEventBusInsn.name, getEventBusInsn.desc, false)));
            } else {
                methodNode.instructions.set(invokePostInsn, new InvokeDynamicInsnNode("postToDynamicBus", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(EventBus.class), eventType),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapPostToDynamicBus",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false)));
            }

            for (AbstractInsnNode exitScopeInsn : exitScopeInsns) {
                BytecodeHelper.insertBefore(exitScopeInsn, methodNode.instructions,
                        new VarInsnNode(ALOAD, cacheLvtIndex),
                        new VarInsnNode(ALOAD, eventInstanceLvtIndex),
                        new InvokeDynamicInsnNode("releaseToCache", Type.getMethodDescriptor(Type.VOID_TYPE, cacheType, eventType),
                                new Handle(H_INVOKESTATIC,
                                        Type.getInternalName(OptimizeEventInstanceAllocationTransformer.class), "bootstrapReleaseToCache",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false),
                                eventType));
            }

            return true;
        }

        return false;
    }

    public static CallSite bootstrapPostToConstantBus(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle busGetter) throws Throwable {
        Preconditions.checkArgument(type.parameterCount() == 1 && type.returnType() == boolean.class, type);

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
