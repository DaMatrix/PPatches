package net.daporkchop.ppatches.modules.ppatches.tagLogMessages;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

/**
 * @author DaPorkchop_
 */
public class TagLogMessagesTransformer implements ITreeClassTransformer {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        int firstDotIndex;
        return name.startsWith("net.daporkchop.ppatches.modules.")
                && (firstDotIndex = name.indexOf('.', "net.daporkchop.ppatches.modules.".length())) >= 0
                && name.indexOf('.', firstDotIndex + 1) >= 0;
    }

    //TODO: this could be a method transformer, except it doesn't actually need an AnalyzedInsnList
    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changeFlags = 0;
        for (MethodNode methodNode : classNode.methods) {
            changeFlags |= transformMethod(name, methodNode.instructions);
        }
        return changeFlags;
    }

    private static int transformMethod(String name, InsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() != GETSTATIC) {
                continue;
            }

            FieldInsnNode fieldInsn = (FieldInsnNode) insn;
            if ("net/daporkchop/ppatches/PPatchesMod".equals(fieldInsn.owner) && "LOGGER".equals(fieldInsn.name) && "Lorg/apache/logging/log4j/Logger;".equals(fieldInsn.desc)) {
                String moduleName = name.substring("net.daporkchop.ppatches.modules.".length(), name.indexOf('.', name.indexOf('.', "net.daporkchop.ppatches.modules.".length()) + 1));
                instructions.set(fieldInsn, new InvokeDynamicInsnNode(
                        "getLogger", "()Lorg/apache/logging/log4j/Logger;",
                        new Handle(H_INVOKESTATIC,
                                "net/daporkchop/ppatches/modules/ppatches/tagLogMessages/TagLogMessagesTransformer",
                                "bootstrapGetLoggerForModule",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false),
                        "PPatches - " + moduleName));
                changeFlags |= CHANGED;
            }
        }
        return changeFlags;
    }

    public static CallSite bootstrapGetLoggerForModule(MethodHandles.Lookup lookup, String name, MethodType type, String moduleName) {
        return new ConstantCallSite(MethodHandles.constant(type.returnType(), LogManager.getLogger(moduleName)));
    }
}
