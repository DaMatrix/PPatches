package net.daporkchop.ppatches.modules.ppatches.tagLogMessages;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

/**
 * @author DaPorkchop_
 */
public class TagLogMessagesTransformer implements ITreeClassTransformer {
    private static String getModuleName(String name) {
        int firstDotIndex;
        int lastDotIndex;
        return name.startsWith("net.daporkchop.ppatches.modules.")
                && (firstDotIndex = name.indexOf('.', "net.daporkchop.ppatches.modules.".length())) >= 0
                && (lastDotIndex = name.indexOf('.', firstDotIndex + 1)) >= 0
                ? name.substring("net.daporkchop.ppatches.modules.".length(), lastDotIndex)
                : null;
    }

    //TODO: this could be a method transformer, except it doesn't actually need an AnalyzedInsnList
    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changeFlags = 0;

        String moduleName;
        if (name.startsWith("net.daporkchop.ppatches.modules.") && (moduleName = getModuleName(name)) != null) {
            for (MethodNode methodNode : classNode.methods) {
                changeFlags |= transformMethod(moduleName, methodNode.instructions);
            }
        } else {
            for (MethodNode methodNode : classNode.methods) {
                Optional<AnnotationNode> optionalAnnotation = BytecodeHelper.findAnnotationByDesc(methodNode.visibleAnnotations, Type.getDescriptor(MixinMerged.class));
                if (!optionalAnnotation.isPresent()) {
                    continue;
                }

                Optional<Object> optionalMixinName = BytecodeHelper.findAnnotationValueByName(optionalAnnotation.get(), "mixin");
                if (!optionalMixinName.isPresent() || !(optionalMixinName.get() instanceof String)) {
                    continue;
                }

                String mixinName = (String) optionalMixinName.get();
                if (mixinName.startsWith("net.daporkchop.ppatches.modules.") && (moduleName = getModuleName(mixinName)) != null) {
                    changeFlags |= transformMethod(moduleName, methodNode.instructions);
                }
            }
        }
        return changeFlags;
    }

    private static int transformMethod(String moduleName, InsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (BytecodeHelper.isGETSTATIC(insn, "net/daporkchop/ppatches/PPatchesMod", "LOGGER", "Lorg/apache/logging/log4j/Logger;")) {
                instructions.set(insn, new InvokeDynamicInsnNode(
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
