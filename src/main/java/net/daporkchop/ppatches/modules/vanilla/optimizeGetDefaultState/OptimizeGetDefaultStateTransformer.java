package net.daporkchop.ppatches.modules.vanilla.optimizeGetDefaultState;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeGetDefaultStateTransformer implements ITreeClassTransformer.IndividualMethod.Analyzed {
    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = methodNode.instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            MethodInsnNode methodInsn;
            if (insn.getOpcode() == INVOKEVIRTUAL
                    && Type.getInternalName(Block.class).equals((methodInsn = (MethodInsnNode) insn).owner)
                    && Type.getMethodDescriptor(Type.getType(IBlockState.class)).equals(methodInsn.desc)
                    && ("getDefaultState".equals(methodInsn.name) || "func_176223_P".equals(methodInsn.name))) {
                changeFlags |= transformGetDefaultState(classNode, methodNode, instructions, methodInsn);
            }
        }
        return changeFlags;
    }

    private static int transformGetDefaultState(ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions, MethodInsnNode getDefaultStateMethod) {
        AbstractInsnNode blockSourceInsn = instructions.getSingleStackOperandSourceFromBottom(getDefaultStateMethod, 0);
        if (blockSourceInsn == null || blockSourceInsn.getOpcode() != GETSTATIC) {
            return 0;
        }

        FieldInsnNode fieldBlockSourceInsn = (FieldInsnNode) blockSourceInsn;
        if (!"net/minecraft/init/Blocks".equals(fieldBlockSourceInsn.owner) || !Type.getDescriptor(Block.class).equals(fieldBlockSourceInsn.desc)) {
            return 0;
        }

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            PPatchesMod.LOGGER.info("Optimizing constant call to L{};{}.{}() at L{};{}{} {}",
                    fieldBlockSourceInsn.owner, fieldBlockSourceInsn.name, getDefaultStateMethod.name,
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(getDefaultStateMethod));

            batch.insertBefore(getDefaultStateMethod, new InsnNode(POP));
            batch.set(getDefaultStateMethod, new InvokeDynamicInsnNode("ppatches_optimizeGetDefaultState", getDefaultStateMethod.desc,
                    new Handle(H_INVOKESTATIC,
                            Type.getInternalName(OptimizeGetDefaultStateTransformer.class),
                            "bootstrapGetDefaultState",
                            Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(MethodHandle.class)),
                            false),
                    new Handle(H_GETSTATIC,
                            fieldBlockSourceInsn.owner, fieldBlockSourceInsn.name, fieldBlockSourceInsn.desc,
                            false)));
        }
        return CHANGED;
    }

    @SneakyThrows
    public static CallSite bootstrapGetDefaultState(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle blockGetter) {
        PPatchesMod.LOGGER.info("Bootstrapping call to constant Block.getBlockState() from {}", lookup.lookupClass());
        return new ConstantCallSite(MethodHandles.constant(type.returnType(), ((Block) blockGetter.invokeExact()).getDefaultState()));
    }
}
