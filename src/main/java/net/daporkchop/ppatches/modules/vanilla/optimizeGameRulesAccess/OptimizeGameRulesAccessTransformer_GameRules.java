package net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.COWArrayUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeGameRulesAccessTransformer_GameRules implements ITreeClassTransformer {
    public static final String RULE_FIELD_SUFFIX = "$ppatches_optimizeGameRulesAccess_optimized";
    public static final String VANILLARULES_FIELD = "ppatches_optimizeGameRulesAccess_vanillaRules";
    public static final String ALLRULES_FIELD = "ppatches_optimizeGameRulesAccess_allRules";

    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return "net.minecraft.world.GameRules".equals(transformedName);
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        if (!this.interestedInClass(name, transformedName)) {
            return 0;
        }

        Preconditions.checkState(BytecodeHelper.findMethod(classNode, "<init>").size() == 1, "GameRules class has more than one constructor!");

        Set<String> discoveredGameRules = new TreeSet<>();

        //find all the game rules
        MethodNode ctor = BytecodeHelper.findMethodOrThrow(classNode, "<init>", "()V");
        for (AbstractInsnNode insn = ctor.instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof String)) {
                continue;
            }

            AbstractInsnNode prev = insn.getPrevious();
            if (prev.getOpcode() != ALOAD || ((VarInsnNode) prev).var != 0
                    || !(next instanceof LdcInsnNode) || !(((LdcInsnNode) next).cst instanceof String)
                    || next.getNext().getOpcode() != GETSTATIC) {
                continue;
            }

            discoveredGameRules.add((String) ((LdcInsnNode) insn).cst);
        }

        //add new fields for all the game rules
        PPatchesMod.LOGGER.info("Adding quick access fields for {} vanilla game rules: {}", discoveredGameRules.size(), discoveredGameRules);

        Set<String> moddedGameRules = new TreeSet<>(Arrays.asList(PPatchesConfig.vanilla_optimizeGameRulesAccess.effectiveModdedGameRules));
        moddedGameRules.removeAll(discoveredGameRules);
        PPatchesMod.LOGGER.info("Adding quick access fields for {} modded game rules: {}", moddedGameRules.size(), moddedGameRules);

        String[] allGameRules = COWArrayUtils.concat(discoveredGameRules.toArray(new String[0]), moddedGameRules.toArray(new String[0]));
        for (String gameRule : allGameRules) {
            classNode.fields.add(new FieldNode(ACC_PUBLIC, gameRule + RULE_FIELD_SUFFIX, "Lnet/minecraft/world/GameRules$Value;", null, null));
        }

        { //pre-initialize all the new fields in the constructor with a dummy GameRules$Value instance which returns the default value
            InsnList insns = new InsnList();
            for (String gameRule : allGameRules) {
                insns.add(new VarInsnNode(ALOAD, 0));
                insns.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/GameRules", "ppatches_optimizeGameRulesAccess_dummyValue", "Lnet/minecraft/world/GameRules$Value;"));
                insns.add(new FieldInsnNode(PUTFIELD, "net/minecraft/world/GameRules", gameRule + RULE_FIELD_SUFFIX, "Lnet/minecraft/world/GameRules$Value;"));
            }

            for (AbstractInsnNode insn = ctor.instructions.getFirst(), next; insn != null; insn = next) {
                next = insn.getNext();

                if (BytecodeHelper.isINVOKESPECIAL(insn, Type.getInternalName(Object.class), "<init>", "()V")) {
                    ctor.instructions.insert(insn, insns);
                    break;
                }
            }
        }

        //add a static field which will contain all the vanilla rule names
        addConstantGameRulesNameSetField(classNode, VANILLARULES_FIELD, discoveredGameRules.toArray(new String[0]));

        //add a static field which will contain all the rule names
        addConstantGameRulesNameSetField(classNode, ALLRULES_FIELD, allGameRules);

        return CHANGED_MANDATORY;
    }

    private static void addConstantGameRulesNameSetField(ClassNode classNode, String fieldName, String[] gameRules) {
        InsnList insns = new InsnList();
        insns.add(BytecodeHelper.loadConstantInsn(gameRules.length));
        insns.add(new TypeInsnNode(ANEWARRAY, Type.getInternalName(String.class)));
        for (int i = 0; i < gameRules.length; i++) {
            insns.add(new InsnNode(DUP));
            insns.add(BytecodeHelper.loadConstantInsn(i));
            insns.add(new LdcInsnNode(gameRules[i]));
            insns.add(new InsnNode(AASTORE));
        }
        insns.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(ImmutableSet.class), "copyOf", Type.getMethodDescriptor(Type.getType(ImmutableSet.class), Type.getType(Object[].class)), ImmutableSet.class.isInterface()));
        insns.add(new FieldInsnNode(PUTSTATIC, "net/minecraft/world/GameRules", fieldName, Type.getDescriptor(ImmutableSet.class)));
        BytecodeHelper.getOrCreateClinit(classNode).instructions.insert(insns);
    }
}
