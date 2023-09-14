package net.daporkchop.ppatches.modules.java.separatedExceptionConstruction;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.COWArrayUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Printer;

import java.lang.invoke.*;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class SeparatedExceptionConstructionTransformer implements ITreeClassTransformer.IndividualMethod {
    @Override
    public boolean interestedInMethod(String className, String classTransformedName, MethodNode method) {
        return !"<clinit>".equals(method.name);
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() == ATHROW && !instructions.isUnreachable(insn)) {
                changeFlags |= transformThrow(classNode, methodNode, instructions, (InsnNode) insn);
            }
        }
        return changeFlags;
    }

    private static int transformThrow(ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions, InsnNode throwInsn) {
        int changeFlags = 0;
        for (AbstractInsnNode newInsn : instructions.getStackOperandSourcesFromBottom(throwInsn, 0).insns) {
            if (newInsn.getOpcode() == NEW) {
                changeFlags |= transformNewThrowable(classNode, methodNode, instructions, (TypeInsnNode) newInsn);
            }
        }
        return changeFlags;
    }

    private static int transformNewThrowable(ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions, TypeInsnNode newInsn) {
        Preconditions.checkState(newInsn.getNext().getOpcode() == DUP, "expected %s, got %s", Printer.OPCODES[DUP], Printer.OPCODES[newInsn.getNext().getOpcode()]);
        InsnNode dupInsn = (InsnNode) newInsn.getNext();

        //find the INVOKESPECIAL instruction which calls <init> on the new exception instance
        MethodInsnNode invokeCtorInsn = (MethodInsnNode) instructions.getSoleResultSingleStackUsage(dupInsn);
        if (invokeCtorInsn == null || invokeCtorInsn.getOpcode() != INVOKESPECIAL || !newInsn.desc.equals(invokeCtorInsn.owner) || !"<init>".equals(invokeCtorInsn.name)) {
            return 0;
        }

        Type[] ctorArgumentTypes = Type.getArgumentTypes(invokeCtorInsn.desc);
        Type throwableType = Type.getObjectType(newInsn.desc);

        if (!"()V".equals(invokeCtorInsn.desc)) {
            List<SourceValue> allSources = instructions.getStackOperandSources(invokeCtorInsn);

            for (int i = 1; i < allSources.size(); i++) {
                Set<AbstractInsnNode> sources = allSources.get(i).insns;
                AbstractInsnNode insn;
                Object cst;
                if (sources.size() != 1 || !BytecodeHelper.isConstant(insn = sources.iterator().next()) || (cst = BytecodeHelper.decodeConstant(insn)) == null) {
                    continue;
                }

                PPatchesMod.LOGGER.debug("replacing Throwable constructor with constant L{};{}{} at L{};{}{} (line {}) with INVOKEDYNAMIC",
                        newInsn.desc, invokeCtorInsn.name, invokeCtorInsn.desc, classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumber(newInsn));

                //we found an argument which is a constant, let's merge it into the invokedynamic instruction
                try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
                    batch.remove(newInsn);
                    batch.remove(dupInsn);
                    batch.remove(insn);
                    batch.set(invokeCtorInsn, new InvokeDynamicInsnNode("newThrowable", Type.getMethodDescriptor(throwableType, COWArrayUtils.remove(ctorArgumentTypes, i - 1)),
                            new Handle(H_INVOKESTATIC, Type.getInternalName(SeparatedExceptionConstructionTransformer.class), "bootstrapExceptionCtor",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                            new Handle(H_NEWINVOKESPECIAL, newInsn.desc, "<init>", invokeCtorInsn.desc, false),
                            i - 1, cst));
                }
                return CHANGED;
            }
        }

        PPatchesMod.LOGGER.debug("replacing Throwable constructor L{};{}{} at L{};{}{} (line {}) with INVOKEDYNAMIC",
                newInsn.desc, invokeCtorInsn.name, invokeCtorInsn.desc, classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumber(newInsn));

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            batch.remove(newInsn);
            batch.remove(dupInsn);
            batch.set(invokeCtorInsn, new InvokeDynamicInsnNode("newThrowable", Type.getMethodDescriptor(throwableType, ctorArgumentTypes),
                    new Handle(H_INVOKESTATIC, Type.getInternalName(SeparatedExceptionConstructionTransformer.class), "bootstrapExceptionCtor",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                    new Handle(H_NEWINVOKESPECIAL, newInsn.desc, "<init>", invokeCtorInsn.desc, false),
                    0));
        }
        return CHANGED;
    }

    public static CallSite bootstrapExceptionCtor(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle ctor, int pos, Object... values) {
        return new ConstantCallSite(MethodHandles.insertArguments(ctor, pos, values));
    }
}
