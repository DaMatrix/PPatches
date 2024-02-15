package net.daporkchop.ppatches.modules.java.foldTrivialConstants;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.util.Iterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class FoldTrivialConstantsTransformer implements ITreeClassTransformer.IndividualMethod {
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

        if (!methodNode.tryCatchBlocks.isEmpty()) {
            changeFlags |= transformMethod_RemoveUnnecessaryTryCatchBlocks(classNode, methodNode);
        }

        return changeFlags;
    }

    private static int transformMethod_RemoveUnnecessaryTryCatchBlocks(ClassNode classNode, MethodNode methodNode) {
        int changeFlags = 0;
        for (Iterator<TryCatchBlockNode> itr = methodNode.tryCatchBlocks.iterator(); itr.hasNext(); ) {
            TryCatchBlockNode tryCatchBlock = itr.next();

            LabelNode handlerLbl = tryCatchBlock.handler;
            AbstractInsnNode next = BytecodeHelper.nextNormalCodeInstruction(handlerLbl);
            if (next.getOpcode() == ATHROW) {
                PPatchesMod.LOGGER.info("Removing pointless try-catch block at L{};{}{} {}",
                        classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(next));
                itr.remove();
            } else if (next.getOpcode() == ASTORE) {
                VarInsnNode storeInsn = (VarInsnNode) next;

                next = BytecodeHelper.nextNormalCodeInstruction(storeInsn);
                VarInsnNode loadInsn;
                if (next.getOpcode() != ALOAD || (loadInsn = (VarInsnNode) next).var != storeInsn.var) {
                    continue;
                }

                next = BytecodeHelper.nextNormalCodeInstruction(loadInsn);
                if (next.getOpcode() != ATHROW) {
                    continue;
                }

                PPatchesMod.LOGGER.info("Removing pointless try-catch block at L{};{}{} {}",
                        classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberRangeForLog(storeInsn, loadInsn, next));
                itr.remove();
            }
        }
        return changeFlags;
    }
}
