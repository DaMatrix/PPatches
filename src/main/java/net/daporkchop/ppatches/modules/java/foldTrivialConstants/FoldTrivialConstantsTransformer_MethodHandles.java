package net.daporkchop.ppatches.modules.java.foldTrivialConstants;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.VarargsParameterDecoder;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class FoldTrivialConstantsTransformer_MethodHandles implements ITreeClassTransformer.IndividualMethod.Analyzed, ITreeClassTransformer.ExactInterested, ITreeClassTransformer.OptimizationPass {
    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesClass("java/lang/invoke/MethodType");
        //TODO: narrow this test to only check for some subset of the methods we're testing for
        //TODO: also check for MethodHandles$Lookup
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            switch (insn.getOpcode()) {
                case INVOKESTATIC: {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("java/lang/invoke/MethodType".equals(methodInsn.owner) && !instructions.isUnreachable(methodInsn)) {
                        //body moved to separate method to help JIT optimize the main loop, which is supposed to be fast
                        changeFlags |= tryTransformMethodType(classNode, methodNode, methodInsn, instructions);
                    }
                    break;
                }
                case INVOKEVIRTUAL: {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("java/lang/invoke/MethodHandles$Lookup".equals(methodInsn.owner) && !instructions.isUnreachable(methodInsn)) {
                        //body moved to separate method to help JIT optimize the main loop, which is supposed to be fast
                        changeFlags |= tryTransformMethodHandle(classNode, methodNode, methodInsn, instructions);
                    }
                    break;
                }
            }
        }
        return changeFlags;
    }

    private static int tryTransformMethodType(ClassNode classNode, MethodNode methodNode, MethodInsnNode methodInsn, AnalyzedInsnList instructions) {
        List<AbstractInsnNode> methodTypeSources = instructions.getSingleStackOperandSources(methodInsn);
        if (methodTypeSources == null) {
            return 0;
        }

        List<AbstractInsnNode> additionalInsnsToRemove = null;

        Type returnType = null;
        Type[] argumentTypes = null;
        switch (methodInsn.name) {
            case "methodType": {
                boolean hasLeadingSingleClass = false;
                switch (methodInsn.desc) {
                    case "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;":
                        returnType = decodeConstantTypeFromClass(methodTypeSources.get(0));
                        argumentTypes = new Type[0];
                        break;
                    case "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;":
                        returnType = decodeConstantTypeFromClass(methodTypeSources.get(0));
                        argumentTypes = decodeConstantTypesFromClasses(methodTypeSources.subList(1, 2));
                        break;
                    case "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;":
                        hasLeadingSingleClass = true;
                        //fallthrough
                    case "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;": {
                        returnType = decodeConstantTypeFromClass(methodTypeSources.get(0));

                        List<Type> argumentTypesList = new ArrayList<>();
                        if (hasLeadingSingleClass) {
                            Type leadingSingleType = decodeConstantTypeFromClass(methodTypeSources.get(1));
                            if (leadingSingleType == null) {
                                return 0;
                            }
                            argumentTypesList.add(leadingSingleType);
                        }

                        VarargsParameterDecoder.Result varargs = VarargsParameterDecoder.tryDecode(methodInsn, instructions).orElse(null);
                        if (varargs == null) {
                            break;
                        }

                        methodTypeSources.remove(methodTypeSources.size() - 1); //we're gonna handle removing the arguments ourself
                        additionalInsnsToRemove = new ArrayList<>();
                        additionalInsnsToRemove.add(varargs.loadLengthInsn);
                        additionalInsnsToRemove.add(varargs.newArrayInsn);
                        for (VarargsParameterDecoder.Element element : varargs.elements) {
                            if (element.valueSourceInsns.size() != 1) {
                                return 0;
                            }
                            Type argumentType = decodeConstantTypeFromClass(element.valueSourceInsns.iterator().next());
                            if (argumentType == null) {
                                return 0;
                            }
                            argumentTypesList.add(argumentType);
                            additionalInsnsToRemove.addAll(element.valueSourceInsns);

                            additionalInsnsToRemove.add(element.dupInsn);
                            additionalInsnsToRemove.add(element.loadIndexInsn);
                            additionalInsnsToRemove.add(element.astoreInsn);
                        }

                        argumentTypes = argumentTypesList.toArray(new Type[0]);
                        break;
                    }
                }
                break;
            }
            case "genericMethodType":
                break;
        }

        if (returnType == null || argumentTypes == null) {
            return 0;
        }
        Type constantMethodType = Type.getMethodType(returnType, argumentTypes);

        List<AbstractInsnNode> deleteSources = new ArrayList<>(methodTypeSources.size());
        List<AbstractInsnNode> popSources = new ArrayList<>(methodTypeSources.size());
        for (AbstractInsnNode source : methodTypeSources) {
            if (instructions.getSoleResultSingleStackUsage(source) == methodInsn) {
                deleteSources.add(source);
            } else {
                popSources.add(source);
            }
        }

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            if (additionalInsnsToRemove != null) {
                for (AbstractInsnNode insn : additionalInsnsToRemove) {
                    batch.remove(insn);
                }
            }
            for (AbstractInsnNode source : deleteSources) { //delete all the sources we can
                batch.remove(source);
            }
            for (int i = popSources.size(); i > 0; i--) { //pop all the sources we can't
                batch.insertBefore(methodInsn, new InsnNode(POP));
            }

            PPatchesMod.LOGGER.info("Folding call to L{};{}{} at L{};{}{} {} into constant MethodType {}",
                    methodInsn.owner, methodInsn.name, methodInsn.desc,
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(methodInsn),
                    constantMethodType);

            batch.set(methodInsn, loadConstantMethodTypeInsn(constantMethodType));
        }

        return CHANGED;
    }

    private static int tryTransformMethodHandle(ClassNode classNode, MethodNode methodNode, MethodInsnNode methodInsn, AnalyzedInsnList instructions) {
        //TODO: not implemented yet
        return 0;
    }

    private static Type[] decodeConstantTypesFromClasses(List<AbstractInsnNode> src) {
        Type[] result = new Type[src.size()];
        for (int i = 0; i < result.length; i++) {
            Type type = decodeConstantTypeFromClass(src.get(i));
            if (type != null) {
                result[i] = type;
            } else {
                return null;
            }
        }
        return result;
    }

    private static Type decodeConstantTypeFromClass(AbstractInsnNode src) {
        if (src instanceof LdcInsnNode) {
            return (Type) ((LdcInsnNode) src).cst;
        } else if (src instanceof FieldInsnNode && src.getOpcode() == GETSTATIC) {
            FieldInsnNode fieldSrc = (FieldInsnNode) src;
            if ("TYPE".equals(fieldSrc.name) && "Ljava/lang/Class;".equals(fieldSrc.desc)) {
                return BytecodeHelper.unboxedPrimitiveType(fieldSrc.owner).orElse(null);
            }
        }
        return null;
    }

    private static AbstractInsnNode loadConstantMethodTypeInsn(Type type) {
        if (!PPatchesTransformerRoot.DUMP_CLASSES) { //disabled because constant MethodHandle arguments break decompilers
            return new LdcInsnNode(type);
        } else {
            return new InvokeDynamicInsnNode("ppatches_foldTrivalConstants_constantMethodType", Type.getMethodDescriptor(Type.getType(MethodType.class)),
                    new Handle(H_INVOKESTATIC,
                            Type.getInternalName(FoldTrivialConstantsTransformer_MethodHandles.class),
                            "bootstrapIndirectConstant",
                            Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(Object.class)),
                            false),
                    type);
        }
    }

    private static AbstractInsnNode loadConstantMethodHandleInsn(Handle handle) {
        if (!PPatchesTransformerRoot.DUMP_CLASSES) { //disabled because constant MethodHandle arguments break decompilers
            return new LdcInsnNode(handle);
        } else {
            return new InvokeDynamicInsnNode("ppatches_foldTrivalConstants_constantMethodHandle", Type.getMethodDescriptor(Type.getType(MethodType.class)),
                    new Handle(H_INVOKESTATIC,
                            Type.getInternalName(FoldTrivialConstantsTransformer_MethodHandles.class),
                            "bootstrapIndirectConstant",
                            Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(Object.class)),
                            false),
                    handle);
        }
    }

    public static CallSite bootstrapIndirectConstant(MethodHandles.Lookup lookup, String name, MethodType type, Object result) {
        return new ConstantCallSite(MethodHandles.constant(type.returnType(), result));
    }
}
