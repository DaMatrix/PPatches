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
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.invoke.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeEventInstanceAllocationTransformer_Events implements ITreeClassTransformer {
    @Override
    public int priority() {
        return 1500; //we want this to run after OptimzeCallbackInfoTransformer
    }

    static boolean isEventClass(String transformedName) {
        for (ClassInfo classInfo = ClassInfo.forName(transformedName); classInfo != null; classInfo = classInfo.getSuperClass()) {
            switch (classInfo.getName()) {
                case "java/lang/Object":
                    return false;
                case "net/minecraftforge/fml/common/eventhandler/Event":
                    return true;
            }
        }
        return false;
    }

    static boolean isLikelyEventClass(String internalName) {
        return internalName.contains("Event");
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changeFlags = 0;

        try {
            //use the same logic as Forge's EventSubscriptionTransformer to determine if the class in question is an event
            if (classNode.superName != null && !transformedName.startsWith("net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation.")
                && !name.startsWith("net.minecraft.") && name.indexOf('.') >= 0
                && isEventClass(transformedName)) {
                changeFlags |= examineAndTransformEventClass(classNode);
            }
        } catch (Throwable e) {
            //Forge's EventSubscriptionTransformer silently ignores these exceptions, we'll do the same
        }

        //try to transform non-event classes to make the event optimizations more effective
        changeFlags |= makeMethodsMoreOptimizable(classNode);

        return changeFlags;
    }

    private static int makeMethodsMoreOptimizable(ClassNode classNode) {
        int changeFlags = 0;

        for (ListIterator<MethodNode> methodItr = classNode.methods.listIterator(); methodItr.hasNext(); ) {
            MethodNode method = methodItr.next();
            Type returnType = Type.getReturnType(method.desc);
            if ((method.access & ACC_STATIC) == 0 //we're only targetting static utility methods for now
                || returnType.getSort() != Type.OBJECT || !isLikelyEventClass(returnType.getInternalName())) {
                continue;
            }

            changeFlags |= CHANGED_MANDATORY;

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
                    || !isLikelyEventClass(returnType.getInternalName())) {
                    continue;
                }

                AbstractInsnNode nextInsn = BytecodeHelper.nextNormalCodeInstruction(callInsn);
                switch (nextInsn.getOpcode()) {
                    case POP: //the returned event instance is immediately discarded
                        PPatchesMod.LOGGER.info("Redirecting INVOKESTATIC L{};{}{} to clone $ppatches_void_{}", callInsn.owner, callInsn.name, callInsn.desc, callInsn.name);
                        callInsn.name = "$ppatches_void_" + callInsn.name;
                        callInsn.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(callInsn.desc));
                        methodNode.instructions.remove(nextInsn);
                        changeFlags |= CHANGED;
                        break;
                    case INVOKEVIRTUAL:
                        MethodInsnNode nextCallInsn = (MethodInsnNode) nextInsn;
                        if ("isCanceled".equals(nextCallInsn.name) && "()Z".equals(nextCallInsn.desc)) {
                            PPatchesMod.LOGGER.info("Redirecting INVOKESTATIC L{};{}{} to clone $ppatches_isCanceled_{}", callInsn.owner, callInsn.name, callInsn.desc, callInsn.name);
                            callInsn.name = "$ppatches_isCanceled_" + callInsn.name;
                            callInsn.desc = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getArgumentTypes(callInsn.desc));
                            methodNode.instructions.remove(nextInsn);
                            changeFlags |= CHANGED;
                        }
                        break;
                }
            }
        }

        return changeFlags;
    }

    private static String getResetMethodDesc(Type eventClass, String origCtorDesc) {
        assert origCtorDesc.endsWith(")V") : origCtorDesc;
        Type[] oldArgumentTypes = Type.getArgumentTypes(origCtorDesc);
        Type[] newArgumentTypes = new Type[oldArgumentTypes.length + 1];
        newArgumentTypes[0] = eventClass;
        System.arraycopy(oldArgumentTypes, 0, newArgumentTypes, 1, oldArgumentTypes.length);
        return Type.getMethodDescriptor(Type.VOID_TYPE, newArgumentTypes);
    }

    private static int examineAndTransformEventClass(ClassNode classNode) {
        PPatchesMod.LOGGER.info("adding instance cache to event class {}", classNode.name);

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

            PPatchesMod.LOGGER.info("cloning constructor {}{} in event class {}", ctor.name, ctor.desc, classNode.name);

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
            PPatchesMod.LOGGER.info("overriding clone method in event class {}", classNode.name);

            MethodNode cloneMethod = new MethodNode(ACC_PUBLIC, "$ppatches_clone", Type.getMethodDescriptor(Type.getObjectType(classNode.name)), null, null);
            classNode.methods.add(cloneMethod);
            cloneMethod.instructions.add(new VarInsnNode(ALOAD, 0));
            cloneMethod.instructions.add(new MethodInsnNode(INVOKESPECIAL, classNode.superName, "clone", "()Ljava/lang/Object;", false));
            cloneMethod.instructions.add(new TypeInsnNode(CHECKCAST, classNode.name));
            cloneMethod.instructions.add(new InsnNode(ARETURN));
        }

        if ("net/minecraftforge/fml/common/eventhandler/Event".equals(classNode.name)) {
            PPatchesMod.LOGGER.info("adding field $ppatches_usedUnsafely:Z to event class {}", classNode.name);

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

        return CHANGED_MANDATORY;
    }
}
