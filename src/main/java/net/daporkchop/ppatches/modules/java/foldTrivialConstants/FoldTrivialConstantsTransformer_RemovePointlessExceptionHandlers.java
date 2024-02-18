package net.daporkchop.ppatches.modules.java.foldTrivialConstants;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Iterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class FoldTrivialConstantsTransformer_RemovePointlessExceptionHandlers implements ITreeClassTransformer.IndividualMethod {
    @Override
    public boolean interestedInMethod(String className, String classTransformedName, MethodNode methodNode) {
        return !methodNode.tryCatchBlocks.isEmpty();
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
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
