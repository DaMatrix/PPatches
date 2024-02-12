package net.daporkchop.ppatches.util.mixin.ext;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * @author DaPorkchop_
 */
public final class PPatchesMixinExtension implements IExtension {
    static {
        ((Extensions) ((IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer()).getExtensions()).add(new PPatchesMixinExtension());
    }

    public static void register() {
        //no-op, handled in static initializer
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
        //no-op
    }

    @Override
    public void postApply(ITargetClassContext context) {
        List<FieldNode> deletedFields = null;
        for (Iterator<FieldNode> itr = context.getClassNode().fields.iterator(); itr.hasNext(); ) {
            FieldNode fieldNode = itr.next();
            if (fieldNode.visibleAnnotations != null) {
                if (BytecodeHelper.findAnnotationByDesc(fieldNode.visibleAnnotations, "Lnet/daporkchop/ppatches/util/mixin/ext/Delete;").isPresent()) {
                    PPatchesMod.LOGGER.info("Deleting field L{};{}:{}", context.getClassInfo().getName(), fieldNode.name, fieldNode.desc);
                    itr.remove();
                    (deletedFields != null ? deletedFields : (deletedFields = new ArrayList<>())).add(fieldNode);
                }
                Optional<AnnotationNode> annotationNode = BytecodeHelper.findAnnotationByDesc(fieldNode.visibleAnnotations, "Lnet/daporkchop/ppatches/util/mixin/ext/MakeFinal;");
                if (annotationNode.isPresent()) {
                    PPatchesMod.LOGGER.info("Making field L{};{}:{} final", context.getClassInfo().getName(), fieldNode.name, fieldNode.desc);
                    fieldNode.access |= Opcodes.ACC_FINAL;
                    annotationNode.get().desc = "Lnet/daporkchop/ppatches/util/mixin/ext/MixinMadeFinal;";
                }
            }
        }

        List<MethodNode> deletedMethods = null;
        for (Iterator<MethodNode> itr = context.getClassNode().methods.iterator(); itr.hasNext(); ) {
            MethodNode methodNode = itr.next();
            if (methodNode.visibleAnnotations != null) {
                if (BytecodeHelper.findAnnotationByDesc(methodNode.visibleAnnotations, "Lnet/daporkchop/ppatches/util/mixin/ext/Delete;").isPresent()) {
                    PPatchesMod.LOGGER.info("Deleting method L{};{}{}", context.getClassInfo().getName(), methodNode.name, methodNode.desc);
                    itr.remove();
                    (deletedMethods != null ? deletedMethods : (deletedMethods = new ArrayList<>())).add(methodNode);
                }
                Optional<AnnotationNode> annotationNode = BytecodeHelper.findAnnotationByDesc(methodNode.visibleAnnotations, "Lnet/daporkchop/ppatches/util/mixin/ext/MakeFinal;");
                if (annotationNode.isPresent()) {
                    PPatchesMod.LOGGER.info("Making method L{};{}{} final", context.getClassInfo().getName(), methodNode.name, methodNode.desc);
                    methodNode.access |= Opcodes.ACC_FINAL;
                    annotationNode.get().desc = "Lnet/daporkchop/ppatches/util/mixin/ext/MixinMadeFinal;";
                }
            }
        }

        if (deletedFields != null || deletedMethods != null) {
            AnnotationVisitor visitor = context.getClassNode().visitAnnotation("Lnet/daporkchop/ppatches/util/mixin/ext/MixinDeleted;", true);

            if (deletedFields != null) {
                removeInitializers(context.getClassNode(), deletedFields);

                AnnotationVisitor fields = visitor.visitArray("fields");
                for (FieldNode fieldNode : deletedFields) {
                    AnnotationVisitor field = fields.visitAnnotation(null, "Lnet/daporkchop/ppatches/util/mixin/ext/MixinDeleted$Field;");
                    field.visit("name", fieldNode.name);
                    field.visit("type", Type.getType(fieldNode.desc));
                    field.visitEnd();
                }
                fields.visitEnd();
            }

            if (deletedMethods != null) {
                AnnotationVisitor methods = visitor.visitArray("methods");
                for (MethodNode methodNode : deletedMethods) {
                    AnnotationVisitor method = methods.visitAnnotation(null, "Lnet/daporkchop/ppatches/util/mixin/ext/MixinDeleted$Method;");
                    method.visit("name", methodNode.name);

                    Type methodType = Type.getMethodType(methodNode.desc);
                    AnnotationVisitor parameterTypes = method.visitArray("parameterTypes");
                    for (Type argumentType : methodType.getArgumentTypes()) {
                        parameterTypes.visit(null, argumentType);
                    }
                    parameterTypes.visitEnd();
                    method.visit("returnType", methodType.getReturnType());
                    method.visitEnd();
                }
                methods.visitEnd();
            }

            visitor.visitEnd();
        }
    }

    private static void removeInitializers(ClassNode classNode, List<FieldNode> deletedFields) {
        List<FieldNode> removeStaticInitializersFor = new ArrayList<>(deletedFields.size());
        List<FieldNode> removeInstanceInitializersFor = new ArrayList<>(deletedFields.size());
        for (FieldNode deletedField : deletedFields) {
            AnnotationNode deleteAnnotation = BytecodeHelper.findAnnotationByDesc(deletedField.visibleAnnotations, "Lnet/daporkchop/ppatches/util/mixin/ext/Delete;").get();
            if ((Boolean) BytecodeHelper.findAnnotationValueByName(deleteAnnotation, "removeStaticInitializer").orElse(false)) {
                Preconditions.checkArgument((deletedField.access & Opcodes.ACC_STATIC) != 0, "non-static field L%s;%s:%s was requested for static initializer removal!",
                        classNode.name, deletedField.name, deletedField.desc);
                removeStaticInitializersFor.add(deletedField);
            }
            if ((Boolean) BytecodeHelper.findAnnotationValueByName(deleteAnnotation, "removeInstanceInitializer").orElse(false)) {
                Preconditions.checkArgument((deletedField.access & Opcodes.ACC_STATIC) == 0, "static field L%s;%s:%s was requested for instance initializer removal!",
                        classNode.name, deletedField.name, deletedField.desc);
                removeInstanceInitializersFor.add(deletedField);
            }
        }

        MethodNode clinit;
        if (!removeStaticInitializersFor.isEmpty() && (clinit = BytecodeHelper.findMethod(classNode, "<clinit>", "()V").orElse(null)) != null) {
            for (AbstractInsnNode insn = clinit.instructions.getFirst(), next; insn != null; insn = next) {
                next = insn.getNext();

                FieldInsnNode fieldInsn;
                if (insn.getOpcode() == Opcodes.PUTSTATIC && classNode.name.equals((fieldInsn = (FieldInsnNode) insn).owner)) {
                    for (FieldNode deletedField : removeStaticInitializersFor) {
                        if (fieldInsn.name.equals(deletedField.name) && fieldInsn.desc.equals(deletedField.desc)) {
                            PPatchesMod.LOGGER.info("Disabling static initializer for L{};{}:{} at L{};{}{} {}",
                                    fieldInsn.owner, fieldInsn.name, fieldInsn.desc,
                                    classNode.name, clinit.name, clinit.desc, BytecodeHelper.findLineNumberForLog(fieldInsn));
                            clinit.instructions.set(fieldInsn, BytecodeHelper.pop(Type.getType(fieldInsn.desc)));
                            break;
                        }
                    }
                }
            }
        }

        if (!removeInstanceInitializersFor.isEmpty()) {
            for (MethodNode methodNode : BytecodeHelper.findMethod(classNode, "<init>")) {
                for (AbstractInsnNode insn = methodNode.instructions.getFirst(), next; insn != null; insn = next) {
                    next = insn.getNext();

                    FieldInsnNode fieldInsn;
                    if (insn.getOpcode() == Opcodes.PUTFIELD && classNode.name.equals((fieldInsn = (FieldInsnNode) insn).owner)) {
                        for (FieldNode deletedField : removeInstanceInitializersFor) {
                            if (fieldInsn.name.equals(deletedField.name) && fieldInsn.desc.equals(deletedField.desc)) {
                                PPatchesMod.LOGGER.info("Disabling instance initializer for L{};{}:{} at L{};{}{} {}",
                                        fieldInsn.owner, fieldInsn.name, fieldInsn.desc,
                                        classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(fieldInsn));
                                methodNode.instructions.set(fieldInsn, BytecodeHelper.pop(Type.getType(fieldInsn.desc)));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
        //no-op
    }
}
