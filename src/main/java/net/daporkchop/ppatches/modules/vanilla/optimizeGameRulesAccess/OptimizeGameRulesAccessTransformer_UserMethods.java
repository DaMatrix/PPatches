package net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.minecraft.world.GameRules;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
public class OptimizeGameRulesAccessTransformer_UserMethods implements ITreeClassTransformer.IndividualMethod.Analyzed {
    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            MethodInsnNode methodInsn;
            if (insn.getOpcode() != INVOKEVIRTUAL || !"net/minecraft/world/GameRules".equals((methodInsn = (MethodInsnNode) insn).owner)) {
                continue;
            }

            String getMethodName;
            switch (methodInsn.name) { //TODO: manual deobfuscation is gross
                default:
                    continue;
                case "getString":
                case "getBoolean":
                case "getInt":
                    getMethodName = methodInsn.name;
                    break;
                case "func_82767_a": //"(Ljava/lang/String;)Ljava/lang/String;"
                    getMethodName = "func_82756_a";
                    break;
                case "func_82766_b": //"(Ljava/lang/String;)Z"
                    getMethodName = "func_82758_b";
                    break;
                case "func_180263_c": //"(Ljava/lang/String;)I"
                    getMethodName = "func_180255_c";
                    break;
            }
            String getMethodDesc = Type.getMethodDescriptor(Type.getReturnType(methodInsn.desc));

            AbstractInsnNode nameSourceInsn = instructions.getSingleStackOperandSourceFromBottom(methodInsn, 1);
            if (!(nameSourceInsn instanceof LdcInsnNode)) {
                continue;
            }

            String gameRule = (String) ((LdcInsnNode) nameSourceInsn).cst;

            PPatchesMod.LOGGER.info("Optimizing access to constant GameRule \"{}\" at L{};{}{} {}",
                    gameRule, classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(methodInsn));

            try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
                //don't load the GameRule name
                if (instructions.getStackUsages(nameSourceInsn).get(0).insns.size() == 1) {
                    batch.remove(nameSourceInsn);
                } else {
                    batch.insertBefore(methodInsn, new InsnNode(POP));
                }

                batch.insertBefore(methodInsn, new InvokeDynamicInsnNode(
                        "ppatches_optimizedGameRulesAccess_get", Type.getMethodDescriptor(Type.getReturnType(methodInsn.desc), Type.getObjectType("net/minecraft/world/GameRules")),
                        new Handle(H_INVOKESTATIC,
                                Type.getInternalName(OptimizeGameRulesAccessTransformer_UserMethods.class),
                                "bootstrapConstantGameRuleAccess",
                                Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(String.class), Type.getType(MethodHandle.class), Type.getType(MethodHandle.class)),
                                false),
                        gameRule,
                        new Handle(H_INVOKEVIRTUAL, methodInsn.owner, methodInsn.name, methodInsn.desc, false),
                        new Handle(H_INVOKEVIRTUAL, "net/minecraft/world/GameRules$Value", getMethodName, getMethodDesc, false)));

                batch.remove(methodInsn);

                changeFlags |= CHANGED;
            }
        }
        return changeFlags;
    }

    public static CallSite bootstrapConstantGameRuleAccess(MethodHandles.Lookup lookup, String name, MethodType type, String rule, MethodHandle slowGetter, MethodHandle fastGetter) throws Throwable {
        try {
            MethodHandle optimizedFieldGetter = lookup.findGetter(GameRules.class, rule + OptimizeGameRulesAccessTransformer_GameRules.RULE_FIELD_SUFFIX, GameRules.Value.class);
            PPatchesMod.LOGGER.info("Bootstrapping optimized accessor for game rule {} from {}", rule, lookup.lookupClass());
            return new ConstantCallSite(MethodHandles.filterReturnValue(optimizedFieldGetter, fastGetter));
        } catch (NoSuchFieldException e) {
            PPatchesMod.LOGGER.info("Bootstrapping slow accessor for game rule {} from {}", rule, lookup.lookupClass());
            return new ConstantCallSite(MethodHandles.insertArguments(slowGetter, 1, rule));
        }
    }
}
