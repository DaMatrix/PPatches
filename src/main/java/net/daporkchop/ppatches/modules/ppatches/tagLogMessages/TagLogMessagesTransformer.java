package net.daporkchop.ppatches.modules.ppatches.tagLogMessages;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

/**
 * @author DaPorkchop_
 */
public class TagLogMessagesTransformer implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.ExactInterested {
    private static String getModuleName(String name) {
        int firstDotIndex;
        int lastDotIndex;
        return name.startsWith("net.daporkchop.ppatches.modules.")
                && (firstDotIndex = name.indexOf('.', "net.daporkchop.ppatches.modules.".length())) >= 0
                && (lastDotIndex = name.indexOf('.', firstDotIndex + 1)) >= 0
                ? name.substring("net.daporkchop.ppatches.modules.".length(), lastDotIndex)
                : null;
    }

    @Override
    public int cpIndexFlags() {
        return ConstantPoolIndex.INCLUDE_ANNOTATIONS_IN_REFERENCED_CLASSES;
    }

    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesField("net/daporkchop/ppatches/PPatchesMod", "LOGGER", "Lorg/apache/logging/log4j/Logger;")
                && (name.startsWith("net.daporkchop.ppatches.modules.") || cpIndex.referencesClass("org/spongepowered/asm/mixin/transformer/meta/MixinMerged"));
    }

    @Override
    public boolean interestedInMethod(String className, String classTransformedName, MethodNode methodNode) {
        return className.startsWith("net.daporkchop.ppatches.modules.") || BytecodeHelper.hasAnnotationWithDesc(methodNode.visibleAnnotations, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        String moduleName = getModuleName(name);
        if (moduleName == null) { //not a piece of regular PPatches module code, presumably a mixin
            Optional<AnnotationNode> optionalAnnotation = BytecodeHelper.findAnnotationByDesc(methodNode.visibleAnnotations, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
            if (!optionalAnnotation.isPresent()) {
                return 0;
            }

            Optional<Object> optionalMixinName = BytecodeHelper.findAnnotationValueByName(optionalAnnotation.get(), "mixin");
            if (!optionalMixinName.isPresent() || !(optionalMixinName.get() instanceof String)) {
                return 0;
            }

            moduleName = getModuleName((String) optionalMixinName.get());
            if (moduleName == null) {
                return 0;
            }
        }

        int changeFlags = 0;
        for (AbstractInsnNode insn = methodNode.instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (BytecodeHelper.isGETSTATIC(insn, "net/daporkchop/ppatches/PPatchesMod", "LOGGER", "Lorg/apache/logging/log4j/Logger;")) {
                methodNode.instructions.set(insn, new InvokeDynamicInsnNode(
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
