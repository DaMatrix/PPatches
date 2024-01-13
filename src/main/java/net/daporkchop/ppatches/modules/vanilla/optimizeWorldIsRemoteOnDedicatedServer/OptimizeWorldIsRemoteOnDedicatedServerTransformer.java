package net.daporkchop.ppatches.modules.vanilla.optimizeWorldIsRemoteOnDedicatedServer;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.TypeUtils;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.POP;

/**
 * @author DaPorkchop_
 */
public class OptimizeWorldIsRemoteOnDedicatedServerTransformer implements ITreeClassTransformer {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return FMLLaunchHandler.side() == Side.SERVER && ITreeClassTransformer.super.interestedInClass(name, transformedName);
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changeFlags = 0;

        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == GETFIELD) {
                    FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    if ("Z".equals(fieldInsnNode.desc)
                        && ("field_72995_K".equals(fieldInsnNode.name) || "isRemote".equals(fieldInsnNode.name))
                        && TypeUtils.hasSuperClass(fieldInsnNode.owner, "net/minecraft/world/World")) {
                        //pop the world instance off the stack again, it's not actually going to be used
                        itr.set(new InsnNode(POP));

                        //load a constant "false", since we're on the dedicated server and therefore there can never be a remote world
                        itr.add(new LdcInsnNode(0));

                        changeFlags |= CHANGED;
                    }
                }
            }
        }
        return changeFlags;
    }
}
