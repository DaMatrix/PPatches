package net.daporkchop.ppatches.modules.vanilla.useFasterRandom;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * @author DaPorkchop_
 */
public class UseFasterRandomTransformer implements ITreeClassTransformer {
    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        boolean anyChanged = false;
        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == INVOKESTATIC) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("java/lang/Math".equals(methodInsn.owner) && "random".equals(methodInsn.name) && "()D".equals(methodInsn.desc)) {
                        PPatchesMod.LOGGER.info("replacing Math.random() with ThreadLocalRandom.current().nextDouble() in L{};{}{}", classNode.name, methodNode.name, methodNode.desc);
                        itr.set(new MethodInsnNode(INVOKESTATIC, "java/util/concurrent/ThreadLocalRandom", "current", "()Ljava/util/concurrent/ThreadLocalRandom;", false));
                        itr.add(new MethodInsnNode(INVOKEVIRTUAL, "java/util/concurrent/ThreadLocalRandom", "nextDouble", "()D", false));
                        anyChanged = true;
                    }
                }
            }
        }
        return anyChanged;
    }
}
