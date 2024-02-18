package net.daporkchop.ppatches.modules.java.foldTrivialConstants;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;

import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * @author DaPorkchop_
 */
public class FoldTrivialConstantsTransformer implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.ExactInterested {
    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesMethod(Type.getInternalName(System.class), "lineSeparator", Type.getMethodDescriptor(Type.getType(String.class)))
                || cpIndex.referencesClass(Type.getInternalName(File.class)); //a few different fields in File, this is faster than checking each one individually
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            switch (insn.getOpcode()) {
                case INVOKESTATIC: {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    Object cst = null;

                    //fold calls to System.lineSeparator() into a constant, as System.lineSeparator() accesses a non-final static field and thus couldn't be inlined by JIT
                    if (BytecodeHelper.isINVOKESTATIC(insn, Type.getInternalName(System.class), "lineSeparator", Type.getMethodDescriptor(Type.getType(String.class)))) {
                        cst = System.lineSeparator();
                    }

                    if (cst != null) {
                        PPatchesMod.LOGGER.info("Inlining call to L{};{}{} at L{};{}{} {} into constant",
                                methodInsn.owner, methodInsn.name, methodInsn.desc,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(methodInsn));
                        instructions.set(insn, BytecodeHelper.loadConstantInsn(cst));
                        changeFlags |= CHANGED;
                    }
                    break;
                }
                case GETSTATIC: {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    Object cst = null;
                    if (Type.getInternalName(File.class).equals(fieldInsn.owner)) { //we're accessing one of the static fields in File
                        //inline any static accesses to any of these four fields, as they're probably being used in a string concatenation and will probably be able
                        // to be merged with neighboring string constants
                        switch (fieldInsn.name) {
                            case "separatorChar":
                                cst = (int) File.separatorChar;
                                break;
                            case "pathSeparatorChar":
                                cst = (int) File.pathSeparatorChar;
                                break;
                            case "separator":
                                cst = File.separator;
                                break;
                            case "pathSeparator":
                                cst = File.pathSeparator;
                                break;
                        }
                    }

                    if (cst != null) {
                        PPatchesMod.LOGGER.info("Inlining field access to L{};{}:{} at L{};{}{} {} into constant",
                                fieldInsn.owner, fieldInsn.name, fieldInsn.desc,
                                classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(fieldInsn));
                        instructions.set(insn, BytecodeHelper.loadConstantInsn(cst));
                        changeFlags |= CHANGED;
                    }
                    break;
                }
            }
        }

        return changeFlags;
    }
}
