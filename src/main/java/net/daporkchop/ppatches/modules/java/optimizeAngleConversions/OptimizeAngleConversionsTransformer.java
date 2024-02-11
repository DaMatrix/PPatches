package net.daporkchop.ppatches.modules.java.optimizeAngleConversions;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.DMUL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * @author DaPorkchop_
 */
public class OptimizeAngleConversionsTransformer implements ITreeClassTransformer.IndividualMethod {
    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() == INVOKESTATIC) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (Type.getInternalName(Math.class).equals(methodInsn.owner) && Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE).equals(methodInsn.desc)) {
                    Double constantFactor = null;
                    switch (methodInsn.name) {
                        case "toDegrees":
                            constantFactor = 180.0d / Math.PI;
                            break;
                        case "toRadians":
                            constantFactor = Math.PI / 180.0d;
                            break;
                    }
                    if (constantFactor != null) {
                        PPatchesMod.LOGGER.info("Optimizing call to L{};{}{} at L{};{}{} {} into multiplication",
                                methodInsn.owner, methodInsn.name, methodInsn.desc,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(methodInsn));
                        BytecodeHelper.replace(methodInsn, instructions,
                                new LdcInsnNode(constantFactor),
                                new InsnNode(DMUL));
                        changeFlags |= CHANGED;
                    }
                }
            }
        }
        return changeFlags;
    }
}
