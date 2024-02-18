package net.daporkchop.ppatches.modules.vanilla.optimizeWorldIsRemoteOnDedicatedServer;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.TypeUtils;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.POP;

/**
 * @author DaPorkchop_
 */
public class OptimizeWorldIsRemoteOnDedicatedServerTransformer implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.ExactInterested {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return FMLLaunchHandler.side() == Side.SERVER && ITreeClassTransformer.IndividualMethod.super.interestedInClass(name, transformedName);
    }

    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesField("net/minecraft/world/World", "field_72995_K", "Z")
                || cpIndex.referencesField("net/minecraft/world/World", "isRemote", "Z");
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;

        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() == GETFIELD) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                if ("Z".equals(fieldInsnNode.desc)
                        && ("field_72995_K".equals(fieldInsnNode.name) || "isRemote".equals(fieldInsnNode.name))
                        && TypeUtils.hasSuperClass(fieldInsnNode.owner, "net/minecraft/world/World")) {
                    //pop the world instance off the stack again, it's not actually going to be used
                    instructions.insertBefore(fieldInsnNode, new InsnNode(POP));

                    //load a constant "false", since we're on the dedicated server and therefore there can never be a remote world
                    instructions.set(fieldInsnNode, new LdcInsnNode(0));

                    changeFlags |= CHANGED;
                }
            }
        }
        return changeFlags;
    }
}
