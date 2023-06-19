package net.daporkchop.ppatches.modules.vanilla.optimizeWorldIsRemoteOnDedicatedServer;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Iterator;
import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeWorldIsRemoteOnDedicatedServerTransformer implements ITreeClassTransformer {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return FMLLaunchHandler.side() == Side.SERVER && ITreeClassTransformer.super.interestedInClass(name, transformedName);
    }

    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        boolean anyChanged = false;

        if ("net/minecraft/world/World".equals(classNode.name)) {
            this.transformWorldClass(classNode);
            anyChanged = true;
        }

        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == GETFIELD) {
                    FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    if ("net/minecraft/world/World".equals(fieldInsnNode.owner)
                        && "Z".equals(fieldInsnNode.desc)
                        && ("field_72995_K".equals(fieldInsnNode.name) || "isRemote".equals(fieldInsnNode.name))) {
                        //pop the world instance off the stack again, it's not actually going to be used
                        itr.set(new InsnNode(POP));

                        //load a constant "false", since we're on the dedicated server and therefore there can never be a remote world
                        itr.add(new LdcInsnNode(0));

                        anyChanged = true;
                    }
                }
            }
        }
        return anyChanged;
    }

    private void transformWorldClass(ClassNode classNode) {
        //remove the "isRemote" field
        for (Iterator<FieldNode> itr = classNode.fields.iterator(); itr.hasNext(); ) {
            FieldNode field = itr.next();
            if ("Z".equals(field.desc)
                && ("field_72995_K".equals(field.name) || "isRemote".equals(field.name))) {
                itr.remove();
                break;
            }
        }

        for (MethodNode methodNode : classNode.methods) {
            if (!"<init>".equals(methodNode.name)) {
                continue;
            }

            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == PUTFIELD) {
                    FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    if ("net/minecraft/world/World".equals(fieldInsnNode.owner)
                        && "Z".equals(fieldInsnNode.desc)
                        && ("field_72995_K".equals(fieldInsnNode.name) || "isRemote".equals(fieldInsnNode.name))) {
                        //replace existing code:
                        //  this.isRemote = client;
                        //with:
                        //  if (client) throw new IllegalArgumentException();

                        LabelNode tailLbl = new LabelNode();
                        itr.set(new JumpInsnNode(IFEQ, tailLbl)); //if 'client == 0', then isRemote is false which is good and exactly what we want
                        itr.add(new TypeInsnNode(NEW, "java/lang/IllegalArgumentException"));
                        itr.add(new InsnNode(DUP));
                        itr.add(new LdcInsnNode("PPatches: vanilla.optimizeWorldIsRemoteOnDedicatedServer: attempted to create a new instance of World with isRemote == true"));
                        itr.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false));
                        itr.add(new InsnNode(ATHROW));
                        itr.add(tailLbl);

                        //pop the world instance off the stack again, it's not actually going to be used
                        itr.add(new InsnNode(POP));
                    }
                }
            }
        }
    }
}
