package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.util.ISpecializedASMEventHandler;
import net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util.IMixinListenerList_OptimizeEventBusDispatch;
import net.daporkchop.ppatches.util.asm.AnonymousClassWriter;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.daporkchop.ppatches.util.asm.analysis.IReverseDataflowProvider;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeEventBusDispatchTransformer implements ITreeClassTransformer.IndividualMethod.Analyzed {
    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            //if (BytecodeHelper.isINVOKEVIRTUAL(insn, Type.getInternalName(EventBus.class), "post", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(Event.class)))) {
            if (BytecodeHelper.isINVOKEVIRTUAL(insn, "net/minecraftforge/fml/common/eventhandler/EventBus", "post", "(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z")) {
                changeFlags |= transformPostInvocation(classNode, methodNode, (MethodInsnNode) insn, instructions);
            }
        }
        return changeFlags;
    }

    private static int transformPostInvocation(ClassNode classNode, MethodNode methodNode, MethodInsnNode invokePostInsn, AnalyzedInsnList instructions) {
        AbstractInsnNode eventBustSourceInsn = instructions.getSingleStackOperandSourceFromBottom(invokePostInsn, 0);
        if (eventBustSourceInsn == null || eventBustSourceInsn.getOpcode() != GETSTATIC) {
            return 0;
        }
        FieldInsnNode getEventBusInsn = (FieldInsnNode) eventBustSourceInsn;

        //we can only constant fold the event bus instance if it's stored in a static final field
        ClassInfo classInfo;
        ClassInfo.Field field;
        if ((classInfo = ClassInfo.forName(getEventBusInsn.owner)) == null || (field = classInfo.findFieldInHierarchy(getEventBusInsn.name, getEventBusInsn.desc, ClassInfo.SearchType.ALL_CLASSES, ClassInfo.Traversal.NONE, ClassInfo.INCLUDE_ALL)) == null) {
            PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: couldn't find event bus source field",
                    getEventBusInsn.owner, getEventBusInsn.name,
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn));
            return 0;
        } else if (!field.isStatic() || !field.isFinal()) {
            PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: event bus source field L{};{} isn't static final",
                    getEventBusInsn.owner, getEventBusInsn.name,
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                    getEventBusInsn.owner, getEventBusInsn.name);
            return 0;
        }

        //TODO: benchmarking
        //TODO: i'd like to factor this out into a separate helper method which proves that a value has a specific type, but maybe this should wait until i finish
        //  the SSA translation system
        String exactEventType = null;
        for (AbstractInsnNode eventInstanceSourceInsn : instructions.getStackOperandSourcesFromBottom(invokePostInsn, 1).insns) {
            String foundExactEventType;
            switch (eventInstanceSourceInsn.getOpcode()) {
                case NEW:
                    foundExactEventType = ((TypeInsnNode) eventInstanceSourceInsn).desc;
                    break;
                case ALOAD: {
                    AbstractInsnNode storeInsn = instructions.getSingleLocalSource(eventInstanceSourceInsn, ((VarInsnNode) eventInstanceSourceInsn).var);
                    if (storeInsn == null) {
                        PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: local variable {} has {} possible sources",
                                getEventBusInsn.owner, getEventBusInsn.name,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                                ((VarInsnNode) eventInstanceSourceInsn).var, instructions.getLocalSources(eventInstanceSourceInsn, ((VarInsnNode) eventInstanceSourceInsn).var).insns.size());
                        return 0;
                    } else if (storeInsn == IReverseDataflowProvider.ARGUMENT_SOURCE) {
                        PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: event instance comes from a method argument",
                                getEventBusInsn.owner, getEventBusInsn.name,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn));
                        return 0;
                    }

                    AbstractInsnNode storeSourceInsn = instructions.getSingleStackOperandSourceFromBottom(storeInsn, 0);
                    if (storeSourceInsn == null) {
                        PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: instruction {} has {} possible sources",
                                getEventBusInsn.owner, getEventBusInsn.name,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                                BytecodeHelper.toString(storeInsn), instructions.getStackOperandSourcesFromBottom(storeInsn, 0).insns.size());
                        return 0;
                    } else if (storeSourceInsn.getOpcode() != NEW) {
                        PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: store source instruction was {}",
                                getEventBusInsn.owner, getEventBusInsn.name,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                                BytecodeHelper.toString(storeSourceInsn));
                        return 0;
                    }

                    foundExactEventType = ((TypeInsnNode) storeSourceInsn).desc;
                    break;
                }
                default:
                    PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: event instance source was {}",
                            getEventBusInsn.owner, getEventBusInsn.name,
                            classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                            BytecodeHelper.toString(eventInstanceSourceInsn));
                    return 0;
            }

            if (exactEventType == null) {
                exactEventType = foundExactEventType;
            } else if (!exactEventType.equals(foundExactEventType)) {
                PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: event instance has inconsistent possible types {} and {}",
                        getEventBusInsn.owner, getEventBusInsn.name,
                        classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                        exactEventType, foundExactEventType);
                return 0;
            }
        }
        if (exactEventType == null) {
            PPatchesMod.LOGGER.warn("Not optimizing call to L{};{}.post() at L{};{}{} {}: failed to determine event instance type",
                    getEventBusInsn.owner, getEventBusInsn.name,
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn));
            return 0;
        }

        PPatchesMod.LOGGER.info("Optimizing call to L{};{}.post() at L{};{}{} {} with event type {}",
                getEventBusInsn.owner, getEventBusInsn.name,
                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokePostInsn),
                exactEventType);

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            batch.remove(getEventBusInsn);

            batch.set(invokePostInsn, new InvokeDynamicInsnNode("post", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getObjectType(exactEventType)),
                    new Handle(H_INVOKESTATIC,
                            Type.getInternalName(OptimizeEventBusDispatchTransformer.class),
                            "bootstrapEventBusPost",
                            Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(MethodHandle.class)),
                            OptimizeEventBusDispatchTransformer.class.isInterface()),
                    new Handle(H_GETSTATIC,
                            getEventBusInsn.owner, getEventBusInsn.name, getEventBusInsn.desc, ClassInfo.forName(getEventBusInsn.owner).isInterface())));
        }

        return CHANGED;
    }

    public static CallSite bootstrapEventBusPost(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle eventBusGetter) throws Throwable {
        Class<?> exactEventType = type.parameterType(0);
        ListenerList listenerList = ((Event) exactEventType.newInstance()).getListenerList();

        EventBus eventBus = (EventBus) eventBusGetter.invokeExact();

        PPatchesMod.LOGGER.info("Bootstrapping post for {}", exactEventType);

        return ((IMixinListenerList_OptimizeEventBusDispatch) listenerList).ppatches_optimizeEventBusDispatch_getCallSite(eventBus, type);
    }

    public static boolean postEventSlowPath(MethodHandle rebuildAndGetListenersMethod, MethodHandle populateCallSiteMethod, Object listenerListImpl, Event event) throws Throwable {
        //TODO: acquire this more cleanly?
        Class<?> exactEventType = event.getClass();

        PPatchesMod.LOGGER.info("Optimizing event dispatch pipeline for {}", exactEventType);

        IEventListener[] listeners = (IEventListener[]) rebuildAndGetListenersMethod.invoke(listenerListImpl);

        AnonymousClassWriter cw = AnonymousClassWriter.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, Type.getInternalName(OptimizeEventBusDispatchTransformer.class) + "__optimizedFor__" + Type.getInternalName(exactEventType), null, Type.getInternalName(Object.class), null);

        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "post", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(exactEventType)), null, null);
            mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true).visitEnd();
            mv.visitCode();

            Label startLbl = new Label();
            Label endLbl = new Label();
            Label handlerLbl = new Label();

            mv.visitLabel(startLbl);
            for (IEventListener listener : listeners) {
                if (listener instanceof ISpecializedASMEventHandler) {
                    //optimization: if forge.optimizeAsmEventHandler is enabled, we bypass the ASMEventHandler entirely and delegate directly to the actual target method
                    ISpecializedASMEventHandler specializedListener = (ISpecializedASMEventHandler) listener;
                    cw.addConstant(mv, specializedListener.getExactInvoker(), Type.getInternalName(MethodHandle.class));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invokeExact", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(specializedListener.exactEventClass())), false);
                } else {
                    cw.addConstant(mv, listener, Type.getInternalName(IEventListener.class));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(IEventListener.class), "invoke", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Event.class)), IEventListener.class.isInterface());
                }
            }

            {
                Label falseLbl = new Label();
                Label tailLbl = new Label();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(exactEventType), "isCancelable", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
                mv.visitJumpInsn(IFEQ, falseLbl);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(exactEventType), "isCanceled", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
                mv.visitJumpInsn(IFEQ, falseLbl);
                mv.visitInsn(ICONST_1);
                mv.visitJumpInsn(GOTO, tailLbl);
                mv.visitLabel(falseLbl);
                mv.visitInsn(ICONST_0);
                mv.visitLabel(tailLbl);
                mv.visitInsn(IRETURN);
            }
            mv.visitLabel(endLbl);

            //exception handler
            mv.visitLabel(handlerLbl);
            //TODO: implement this
            mv.visitInsn(ATHROW);

            mv.visitTryCatchBlock(startLbl, endLbl, handlerLbl, Type.getInternalName(Throwable.class));

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();

        PPatchesTransformerRoot.dumpClass(Type.getInternalName(OptimizeEventBusDispatchTransformer.class) + '/' + Type.getInternalName(exactEventType), cw);

        Class<?> clazz = cw.defineAnonymousClass(OptimizeEventBusDispatchTransformer.class);
        MethodHandle optimizedCallSite = MethodHandles.publicLookup().findStatic(clazz, "post", MethodType.methodType(boolean.class, exactEventType));

        populateCallSiteMethod.invoke(listenerListImpl, listeners, optimizedCallSite);
        return (boolean) optimizedCallSite.invoke(event);
    }
}
