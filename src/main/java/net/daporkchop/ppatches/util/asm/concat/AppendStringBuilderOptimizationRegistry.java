package net.daporkchop.ppatches.util.asm.concat;

import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class AppendStringBuilderOptimizationRegistry {
    private static final Map<String, InsnList> INTERNAL_NAMES_TO_APPEND_INSTRUCTIONS = new TreeMap<>();

    public synchronized static void register(String internalName, InsnList appendInstructions) {
        INTERNAL_NAMES_TO_APPEND_INSTRUCTIONS.put(internalName.intern(), appendInstructions);
    }

    public static void visitAppend(MethodVisitor mv, Type type) {
        if (type.getSort() == Type.OBJECT) {
            InsnList appendInstructions = INTERNAL_NAMES_TO_APPEND_INSTRUCTIONS.get(type.getInternalName());
            if (appendInstructions != null) { //an optimization is registered for this type, copy the instructions
                appendInstructions.accept(mv);
                return;
            }
        }

        String desc = type.getDescriptor();
        if ((type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) && !"Ljava/lang/String;".equals(desc)) {
            desc = "Ljava/lang/Object;";
        }

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", '(' + desc + ")Ljava/lang/StringBuilder;", false);
    }

    public static InsnList makeAppend(Type type) {
        if (type.getSort() == Type.OBJECT) {
            InsnList appendInstructions = INTERNAL_NAMES_TO_APPEND_INSTRUCTIONS.get(type.getInternalName());
            if (appendInstructions != null) { //an optimization is registered for this type, copy the instructions
                InsnList result = BytecodeHelper.makeInsnList();
                for (AbstractInsnNode insn = appendInstructions.getFirst(); insn != null; insn = insn.getNext()) {
                    result.add(insn.clone(null));
                }
                return result;
            }
        }

        String desc = type.getDescriptor();
        if ((type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) && !"Ljava/lang/String;".equals(desc)) {
            desc = "Ljava/lang/Object;";
        }

        return BytecodeHelper.makeInsnList(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", '(' + desc + ")Ljava/lang/StringBuilder;", false));
    }
}
