package net.daporkchop.ppatches.modules.java.separatedExceptionConstruction;

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.InvokeDynamicUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Printer;

import java.lang.invoke.*;
import java.util.*;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class SeparatedExceptionConstructionTransformer implements ITreeClassTransformer {
    private boolean isThrowableClass(String internalName) {
        try {
            //TODO: this could cause cyclic class loading errors if an exception class references a subtype of itself
            return Throwable.class.isAssignableFrom(this.getClass().getClassLoader().loadClass(internalName));
        } catch (ClassNotFoundException e) {
            //silently swallow exception and ignore
            return false;
        }
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changeFlags = 0;

        List<InsnNode> throwInsns = null;
        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == ATHROW) {
                    if (throwInsns == null) {
                        throwInsns = new ArrayList<>();
                    }
                    throwInsns.add((InsnNode) insn);
                }
            }

            if (throwInsns != null && !throwInsns.isEmpty()) {
                for (InsnNode throwInsn : throwInsns) {
                    changeFlags |= this.transformThrow(classNode, methodNode, throwInsn);
                }
                throwInsns.clear();
            }
        }

        return changeFlags;
    }

    private int transformThrow(ClassNode classNode, MethodNode methodNode, InsnNode throwInsn) {
        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);
        TypeInsnNode newInsn;

        {
            Frame<SourceValue> sourceFrame = sourceFrames[methodNode.instructions.indexOf(throwInsn)];
            if (sourceFrame == null) { //unreachable instruction, ignore
                return 0;
            }
            Set<AbstractInsnNode> sources = BytecodeHelper.getStackValueFromTop(sourceFrame, 0).insns;
            if (sources.size() != 1) {
                PPatchesMod.LOGGER.trace("ATHROW in L{};{}{} has multiple possible sources!", classNode.name, methodNode.name, methodNode.desc);
                return 0;
            } else if (sources.iterator().next().getOpcode() != NEW) {
                PPatchesMod.LOGGER.trace("ATHROW in L{};{}{} doesn't come from a NEW instruction!", classNode.name, methodNode.name, methodNode.desc);
                return 0;
            }
            newInsn = (TypeInsnNode) sources.iterator().next();
        }

        Preconditions.checkState(newInsn.getNext().getOpcode() == DUP, "expected %s, got %s", Printer.OPCODES[DUP], Printer.OPCODES[newInsn.getNext().getOpcode()]);
        InsnNode dupInsn = (InsnNode) newInsn.getNext();

        //find the INVOKESPECIAL instruction which calls <init> on the new exception instance
        MethodInsnNode invokeCtorInsn = null;
        for (AbstractInsnNode currentInsn = dupInsn.getNext(); ; currentInsn = currentInsn.getNext()) {
            if (currentInsn.getOpcode() != INVOKESPECIAL) {
                continue;
            }

            Frame<SourceValue> sourceFrame = sourceFrames[methodNode.instructions.indexOf(currentInsn)];
            Set<AbstractInsnNode> sources = BytecodeHelper.getStackValueFromTop(sourceFrame, BytecodeHelper.getConsumedStackOperandCount(currentInsn, sourceFrame) - 1).insns;
            if (sources.size() == 1 && sources.contains(dupInsn)) {
                MethodInsnNode methodInsn = (MethodInsnNode) currentInsn;
                if (newInsn.desc.equals(methodInsn.owner) && "<init>".equals(methodInsn.name)) {
                    invokeCtorInsn = methodInsn;
                    break;
                }
            }
        }

        /*Frame<SourceValue> invokeCtorSourceFrame = sourceFrames[methodNode.instructions.indexOf(invokeCtorInsn)];
        CtorArgument[] ctorArguments = new CtorArgument[Type.getArgumentTypes(invokeCtorInsn.desc).length];
        for (int i = 0; i < ctorArguments.length; i++) {
            SourceValue ctorArgumentSource = BytecodeHelper.getStackValueFromTop(invokeCtorSourceFrame, ctorArguments.length - 1 - i);
        }*/

        STRING:
        if ("(Ljava/lang/String;)V".equals(invokeCtorInsn.desc)) {
            Set<AbstractInsnNode> sources = BytecodeHelper.getStackValueFromTop(sourceFrames[methodNode.instructions.indexOf(invokeCtorInsn)], 0).insns;
            if (sources.size() != 1) {
                break STRING;
            }
            switch (sources.iterator().next().getOpcode()) {
                case LDC: {
                    LdcInsnNode constantArgInsn = (LdcInsnNode) sources.iterator().next();

                    PPatchesMod.LOGGER.info("replacing Throwable constructor with constant string L{};{}{} in L{};{}{} with INVOKEDYNAMIC",
                            newInsn.desc, invokeCtorInsn.name, invokeCtorInsn.desc, classNode.name, methodNode.name, methodNode.desc);
                    methodNode.instructions.remove(newInsn);
                    methodNode.instructions.remove(dupInsn);
                    methodNode.instructions.remove(constantArgInsn);
                    methodNode.instructions.set(invokeCtorInsn, new InvokeDynamicInsnNode("newThrowableWithString", Type.getMethodDescriptor(Type.getObjectType(newInsn.desc)),
                            new Handle(H_INVOKESTATIC, Type.getInternalName(SeparatedExceptionConstructionTransformer.class), "bootstrapExceptionCtorWithString",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                            new Handle(H_NEWINVOKESPECIAL, newInsn.desc, "<init>", invokeCtorInsn.desc, false),
                            true, //isConstant
                            constantArgInsn.cst));
                    return CHANGED;
                }
                case INVOKEVIRTUAL: {
                    MethodInsnNode toStringInsn = (MethodInsnNode) sources.iterator().next();
                    if (!"java/lang/StringBuilder".equals(toStringInsn.owner) || !"toString".equals(toStringInsn.name) || !"()Ljava/lang/String;".equals(toStringInsn.desc)) {
                        break STRING;
                    }

                    Optional<InvokeDynamicUtils.GeneratedStringConcatenation> concatenation = InvokeDynamicUtils.tryGenerateStringConcatenation(methodNode, sourceFrames, toStringInsn);
                    if (!concatenation.isPresent()) {
                        PPatchesMod.LOGGER.info("Throwable constructor with string concatenation L{};{}{} in L{};{}{} can't have its concatenation optimized away",
                                newInsn.desc, invokeCtorInsn.name, invokeCtorInsn.desc, classNode.name, methodNode.name, methodNode.desc);
                        break STRING;
                    }

                    PPatchesMod.LOGGER.info("replacing Throwable constructor with string concatenation L{};{}{} in L{};{}{} with INVOKEDYNAMIC",
                            newInsn.desc, invokeCtorInsn.name, invokeCtorInsn.desc, classNode.name, methodNode.name, methodNode.desc);
                    methodNode.instructions.remove(newInsn);
                    methodNode.instructions.remove(dupInsn);
                    methodNode.instructions.set(invokeCtorInsn, new InvokeDynamicInsnNode("newThrowableWithString", Type.getMethodDescriptor(Type.getObjectType(newInsn.desc), concatenation.get().invokeDynamicArgumentTypes.toArray(new Type[0])),
                            new Handle(H_INVOKESTATIC, Type.getInternalName(SeparatedExceptionConstructionTransformer.class), "bootstrapExceptionCtorWithString",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                            Stream.concat(Stream.of(new Handle(H_NEWINVOKESPECIAL, newInsn.desc, "<init>", invokeCtorInsn.desc, false), false), concatenation.get().bootstrapArgs.stream()).toArray()));

                    for (AbstractInsnNode removeInsn : concatenation.get().removeInsns) {
                        methodNode.instructions.remove(removeInsn);
                    }
                    return CHANGED;
                }
            }
        }

        PPatchesMod.LOGGER.debug("replacing Throwable constructor L{};{}{} in L{};{}{} with INVOKEDYNAMIC",
                newInsn.desc, invokeCtorInsn.name, invokeCtorInsn.desc, classNode.name, methodNode.name, methodNode.desc);
        methodNode.instructions.remove(newInsn);
        methodNode.instructions.remove(dupInsn);
        methodNode.instructions.set(invokeCtorInsn, new InvokeDynamicInsnNode("newThrowable", Type.getMethodDescriptor(Type.getObjectType(newInsn.desc), Type.getArgumentTypes(invokeCtorInsn.desc)),
                new Handle(H_INVOKESTATIC, Type.getInternalName(SeparatedExceptionConstructionTransformer.class), "bootstrapExceptionCtor",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                new Handle(H_NEWINVOKESPECIAL, newInsn.desc, "<init>", invokeCtorInsn.desc, false)));
        return CHANGED;
    }

    public static CallSite bootstrapExceptionCtor(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle ctor) {
        return new ConstantCallSite(ctor);
    }

    public static CallSite bootstrapExceptionCtorWithString(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle ctor, int isConstant, Object... args) throws Throwable {
        MethodHandle target;
        if (isConstant != 0) {
            target = MethodHandles.insertArguments(ctor, 0, args);
        } else {
            target = MethodHandles.collectArguments(ctor, 0,
                InvokeDynamicUtils.bootstrapStringConcatenation(lookup, name, type.changeReturnType(ctor.type().parameterType(0)), args).dynamicInvoker());
        }
        return new ConstantCallSite(target);
    }
}
