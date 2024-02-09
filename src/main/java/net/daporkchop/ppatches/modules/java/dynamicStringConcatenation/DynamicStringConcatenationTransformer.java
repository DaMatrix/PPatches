package net.daporkchop.ppatches.modules.java.dynamicStringConcatenation;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.daporkchop.ppatches.util.asm.concat.DynamicConcatGenerator;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class DynamicStringConcatenationTransformer implements ITreeClassTransformer.IndividualMethod.Analyzed {
    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();
            if (BytecodeHelper.isINVOKEVIRTUAL(insn, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;")) {
                changeFlags |= transformStringBuilderChain(classNode, methodNode, instructions, (MethodInsnNode) insn);
            }
        }
        return changeFlags;
    }

    private static int transformStringBuilderChain(ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions, MethodInsnNode toStringInsn) {
        List<AbstractInsnNode> toRemove = new ArrayList<>();
        List<Object> recipe = new LinkedList<>();

        MethodInsnNode prevStringBuilderMethod = toStringInsn;
        AbstractInsnNode src;
        while (true) {
            src = instructions.getSingleStackOperandSourceFromBottom(prevStringBuilderMethod, 0);
            if (src == null) {
                return 0;
            } else if (src.getOpcode() == NEW) {
                break;
            } else if (src.getOpcode() != INVOKEVIRTUAL) {
                return 0;
            }

            prevStringBuilderMethod = (MethodInsnNode) src;
            Type[] argumentTypes;
            if (!"java/lang/StringBuilder".equals(prevStringBuilderMethod.owner) || !"append".equals(prevStringBuilderMethod.name)
                || (argumentTypes = Type.getArgumentTypes(prevStringBuilderMethod.desc)).length != 1) {
                return 0;
            }

            toRemove.add(prevStringBuilderMethod);

            AbstractInsnNode valueSrc = instructions.getSingleStackOperandSourceFromBottom(prevStringBuilderMethod, 1);
            if (valueSrc != null && BytecodeHelper.isConstant(valueSrc)) {
                toRemove.add(valueSrc);

                Object cst = BytecodeHelper.decodeConstant(valueSrc);
                switch (argumentTypes[0].getSort()) {
                    case Type.BOOLEAN:
                        valueSrc = new LdcInsnNode(String.valueOf((int) cst != 0));
                        break;
                    case Type.CHAR:
                        valueSrc = new LdcInsnNode(String.valueOf((char) (int) cst));
                        break;
                }
                recipe.add(0, valueSrc);
            } else {
                recipe.add(0, argumentTypes[0]);
            }
        }

        TypeInsnNode newInsn;
        if (src.getOpcode() != NEW || !"java/lang/StringBuilder".equals((newInsn = (TypeInsnNode) src).desc)) {
            return 0;
        }

        Set<AbstractInsnNode> newUsages = instructions.getSoleResultStackUsages(newInsn).insns;
        if (newUsages.size() != 2 || !newUsages.remove(prevStringBuilderMethod)) {
            return 0;
        }

        AbstractInsnNode dupInsn = newUsages.iterator().next();
        if (dupInsn.getOpcode() != DUP) {
            return 0;
        }

        AbstractInsnNode invokeCtorInsn = instructions.getSoleResultSingleStackUsage(dupInsn);
        if (invokeCtorInsn == null || !BytecodeHelper.isINVOKESPECIAL(invokeCtorInsn, "java/lang/StringBuilder", "<init>", "()V")) {
            return 0;
        }

        PPatchesMod.LOGGER.trace("Replacing string concatenation in L{};{}{} (line {}) with INVOKEDYNAMIC",
                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumber(newInsn));

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            batch.remove(newInsn);
            batch.remove(dupInsn);
            batch.remove(invokeCtorInsn);

            for (AbstractInsnNode removeInsn : toRemove) {
                batch.remove(removeInsn);
            }

            batch.set(toStringInsn, DynamicConcatGenerator.makeDynamicStringConcatenation(recipe.toArray()));
        }
        return CHANGED;
    }
}
