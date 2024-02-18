package net.daporkchop.ppatches.modules.vanilla.useFasterRandom;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.concurrent.ThreadLocalRandom;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * @author DaPorkchop_
 */
public class UseFasterRandomTransformer implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.ExactInterested {
    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesMethod(Type.getInternalName(Math.class), "random", Type.getMethodDescriptor(Type.DOUBLE_TYPE));
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (BytecodeHelper.isINVOKESTATIC(insn, Type.getInternalName(Math.class), "random", Type.getMethodDescriptor(Type.DOUBLE_TYPE))) {
                PPatchesMod.LOGGER.info("replacing Math.random() with ThreadLocalRandom.current().nextDouble() in L{};{}{}", classNode.name, methodNode.name, methodNode.desc);
                instructions.insertBefore(insn, new MethodInsnNode(INVOKESTATIC, Type.getInternalName(ThreadLocalRandom.class), "current", "()Ljava/util/concurrent/ThreadLocalRandom;", false));
                instructions.set(insn, new MethodInsnNode(INVOKEVIRTUAL, Type.getInternalName(ThreadLocalRandom.class), "nextDouble", "()D", false));
                changeFlags |= CHANGED;
            }
        }
        return changeFlags;
    }
}
