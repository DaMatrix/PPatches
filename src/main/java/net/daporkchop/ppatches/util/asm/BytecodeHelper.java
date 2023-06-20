package net.daporkchop.ppatches.util.asm;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class BytecodeHelper {
    public static boolean isConstant(AbstractInsnNode insn) {
        if (insn instanceof LdcInsnNode) {
            return true;
        }

        switch (insn.getOpcode()) {
            case BIPUSH: //byte immediate
            case SIPUSH: //short immediate
                assert insn instanceof IntInsnNode;
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case LCONST_0:
            case LCONST_1:
            case FCONST_0:
            case FCONST_1:
            case DCONST_0:
            case DCONST_1:
                return true;
            default:
                return false;
        }
    }

    public static Object decodeConstant(AbstractInsnNode insn) {
        if (insn instanceof LdcInsnNode) {
            return ((LdcInsnNode) insn).cst;
        }

        switch (insn.getOpcode()) {
            case ACONST_NULL:
                return null;
            case ICONST_M1:
                return -1;
            case ICONST_0:
                return 0;
            case ICONST_1:
                return 1;
            case ICONST_2:
                return 2;
            case ICONST_3:
                return 3;
            case ICONST_4:
                return 4;
            case ICONST_5:
                return 5;
            case LCONST_0:
                return 0L;
            case LCONST_1:
                return 1L;
            case FCONST_0:
                return 0.0f;
            case FCONST_1:
                return 1.0f;
            case DCONST_0:
                return 0.0d;
            case DCONST_1:
                return 1.0d;
            case BIPUSH: //byte immediate
            case SIPUSH: //short immediate
                return ((IntInsnNode) insn).operand;
            default:
                throw new IllegalArgumentException("not a constant value: " + Printer.OPCODES[insn.getOpcode()]);
        }
    }

    public static List<MethodNode> findMethod(ClassNode classNode, String name) {
        List<MethodNode> out = null;
        for (MethodNode methodNode : classNode.methods) {
            if (name.equals(methodNode.name)) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(methodNode);
            }
        }
        return out != null ? out : Collections.emptyList();
    }

    public static Optional<MethodNode> findMethod(ClassNode classNode, String name, String desc) {
        Optional<MethodNode> result = Optional.empty();
        for (MethodNode methodNode : classNode.methods) {
            if (name.equals(methodNode.name) && desc.equals(methodNode.desc)) {
                if (result.isPresent()) {
                    throw new IllegalStateException("already found a method named " + name + " with desc " + desc);
                }
                result = Optional.of(methodNode);
            }
        }
        return result;
    }

    public static MethodNode findMethodOrThrow(ClassNode classNode, String name, String desc) {
        Optional<MethodNode> result = findMethod(classNode, name, desc);
        if (!result.isPresent()) {
            throw new IllegalStateException("couldn't find method named " + name + " with desc " + desc + " in class " + classNode.name);
        }
        return result.get();
    }

    public static List<MethodNode> findObfuscatedMethod(ClassNode classNode, Collection<String> names) {
        List<MethodNode> out = null;
        for (MethodNode methodNode : classNode.methods) {
            if (names.contains(methodNode.name)) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(methodNode);
            }
        }
        return out != null ? out : Collections.emptyList();
    }

    public static Optional<MethodNode> findObfuscatedMethod(ClassNode classNode, Collection<String> names, String desc) {
        Optional<MethodNode> result = Optional.empty();
        for (MethodNode methodNode : classNode.methods) {
            if (desc.equals(methodNode.desc) && names.contains(methodNode.name)) {
                if (result.isPresent()) {
                    throw new IllegalStateException("already found a method named " + names + " with desc " + desc);
                }
                result = Optional.of(methodNode);
            }
        }
        return result;
    }

    public static MethodNode findObfuscatedMethodOrThrow(ClassNode classNode, Collection<String> names, String desc) {
        Optional<MethodNode> result = findObfuscatedMethod(classNode, names, desc);
        if (!result.isPresent()) {
            throw new IllegalStateException("couldn't find method named " + names + " with desc " + desc + " in class " + classNode.name);
        }
        return result.get();
    }
}
