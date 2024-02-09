package net.daporkchop.ppatches.modules.vanilla.useFasterRandom;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * @author DaPorkchop_
 */
public class UseFasterRandomTransformer implements ITreeClassTransformer.IndividualMethod {
    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() == INVOKESTATIC) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if ("java/lang/Math".equals(methodInsn.owner) && "random".equals(methodInsn.name) && "()D".equals(methodInsn.desc)) {
                    PPatchesMod.LOGGER.info("replacing Math.random() with ThreadLocalRandom.current().nextDouble() in L{};{}{}", classNode.name, methodNode.name, methodNode.desc);
                    instructions.insertBefore(methodInsn, new MethodInsnNode(INVOKESTATIC, "java/util/concurrent/ThreadLocalRandom", "current", "()Ljava/util/concurrent/ThreadLocalRandom;", false));
                    instructions.set(methodInsn, new MethodInsnNode(INVOKEVIRTUAL, "java/util/concurrent/ThreadLocalRandom", "nextDouble", "()D", false));
                    changeFlags |= CHANGED;
                }
            }
        }
        return changeFlags;
    }
}
