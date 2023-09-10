package net.daporkchop.ppatches.modules.asm.foldTypeConstants;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.TypeUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.invoke.*;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class FoldTypeConstantsTransformer implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.OptimizationPass {
    @Override
    public int transformMethod(String name, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        //set to CHANGED if a change is made which doesn't invalidate the source frame info
        int changedFlags = 0;

        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() != INVOKESTATIC) {
                continue;
            }

            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            if (!"org/objectweb/asm/Type".equals(methodInsn.owner)) {
                continue;
            }

            if (instructions.isUnreachable(methodInsn)) { //unreachable instruction, ignore
                continue;
            }

            switch (methodInsn.name) {
                case "getDescriptor":
                case "getInternalName":
                    if ("(Ljava/lang/Class;)Ljava/lang/String;".equals(methodInsn.desc)) {
                        AbstractInsnNode src = instructions.singleStackSourceAtTop(methodInsn, Type.getType(Class.class));
                        if (src instanceof LdcInsnNode) {
                            try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
                                Type arg = (Type) ((LdcInsnNode) src).cst;
                                batch.set(src, new LdcInsnNode("getDescriptor".equals(methodInsn.name) ? arg.getDescriptor() : arg.getInternalName()));
                                batch.remove(methodInsn);
                            }
                            changedFlags = CHANGED;
                        }
                    }
                    continue;
                case "getType":
                    if ("(Ljava/lang/Class;)Lorg/objectweb/asm/Type;".equals(methodInsn.desc)) {
                        AbstractInsnNode src = instructions.singleStackSourceAtTop(methodInsn, Type.getType(Class.class));
                        if (src instanceof LdcInsnNode) {
                            //getType() on a constant class, redirect to a constant string to avoid loading classes
                            try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
                                LdcInsnNode ldcSrc = (LdcInsnNode) src;
                                batch.set(ldcSrc, new LdcInsnNode(((Type) ldcSrc.cst).getDescriptor()));
                                batch.set(methodInsn, methodInsn = new MethodInsnNode(INVOKESTATIC, "org/objectweb/asm/Type", "getType", "(Ljava/lang/String;)Lorg/objectweb/asm/Type;", false));
                            }
                            changedFlags = CHANGED;
                        } else if (src instanceof FieldInsnNode && src.getOpcode() == GETSTATIC) {
                            FieldInsnNode fieldSrc = (FieldInsnNode) src;
                            Type primitiveType;
                            if ("TYPE".equals(fieldSrc.name) && "Ljava/lang/Class;".equals(fieldSrc.desc)
                                && (primitiveType = BytecodeHelper.unboxedPrimitiveType(fieldSrc.owner).orElse(null)) != null) {
                                //getType() on a constant primitive field, redirect to the static fields in Type
                                try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
                                    batch.set(fieldSrc, new FieldInsnNode(GETSTATIC, "org/objectweb/asm/Type", primitiveType.getClassName().toUpperCase(Locale.ROOT) + "_TYPE", "Lorg/objectweb/asm/Type;"));
                                    batch.remove(methodInsn);
                                }
                                changedFlags = CHANGED;
                            }
                        }
                    }
                    if ("(Ljava/lang/String;)Lorg/objectweb/asm/Type;".equals(methodInsn.desc)) {
                        AbstractInsnNode src = instructions.singleStackSourceAtTop(methodInsn, Type.getType(String.class));
                        if (src instanceof LdcInsnNode) {
                            //getType() on a constant string, turn it into an INVOKEDYNAMIC to avoid repeatedly allocating the same type
                            //TODO: this could be CONSTANT_DYNAMIC when available

                            try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
                                batch.remove(src);
                                batch.set(methodInsn, new InvokeDynamicInsnNode("$ppatches_getType", "()Lorg/objectweb/asm/Type;",
                                        new Handle(H_INVOKESTATIC, "net/daporkchop/ppatches/modules/asm/foldTypeConstants/FoldTypeConstantsTransformer",
                                                "bootstrapConstantType", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false),
                                        ((LdcInsnNode) src).cst));
                            }
                            changedFlags = CHANGED;
                        }
                    }
                    continue;
                case "getMethodDescriptor": {
                    if (!"(Lorg/objectweb/asm/Type;[Lorg/objectweb/asm/Type;)Ljava/lang/String;".equals(methodInsn.desc)) {
                        continue;
                    }

                    /*AbstractInsnNode returnTypeExprFirstInsn = BytecodeHelper.tryFindExpressionStart(methodNode, sourceFrames, methodInsn, 1).orElse(null);
                    if (returnTypeExprFirstInsn == null) {
                        continue;
                    }

                    VarargsParameterDecoder.Result varargs = VarargsParameterDecoder.tryDecode(classNode.name, methodNode, methodInsn, sourceFrames).orElse(null);
                    if (varargs != null) {
                        //convert into a StringBuilder chain

                        //inspect the code for constant strings before modifying the code structure
                        List<AbstractInsnNode> toRemove = new ArrayList<>();
                        String returnTypeConstantString = tryExtractConstantTypeDesc(methodNode, sourceFrames, methodInsn, 1, toRemove);
                        String[] elementConstantStrings = new String[varargs.elements.size()];
                        for (int i = 0; i < elementConstantStrings.length; i++) {
                            elementConstantStrings[i] = tryExtractConstantTypeDesc(methodNode, sourceFrames, varargs.elements.get(i).astoreInsn, 0, toRemove);
                        }

                        ConcatGenerator concat = new ConcatGenerator();
                        methodNode.instructions.insertBefore(returnTypeExprFirstInsn, concat.begin());
                        methodNode.instructions.insertBefore(returnTypeExprFirstInsn, concat.appendConstant("("));

                        if (returnTypeConstantString == null) {
                            //swap the return type with the StringBuilder instance immediately before the varargs parameter array is created
                            methodNode.instructions.set(varargs.loadLengthInsn, new InsnNode(SWAP));
                        } else {
                            methodNode.instructions.remove(varargs.loadLengthInsn);
                        }
                        methodNode.instructions.remove(varargs.newArrayInsn);

                        //append each parameter type to the StringBuilder instead of inserting them into the generic array
                        for (int i = 0; i < elementConstantStrings.length; i++) {
                            VarargsParameterDecoder.Element element = varargs.elements.get(i);

                            methodNode.instructions.remove(element.dupInsn);
                            methodNode.instructions.remove(element.loadIndexInsn);

                            methodNode.instructions.insertBefore(element.astoreInsn, elementConstantStrings[i] == null
                                    ? concat.append(Type.getObjectType("java/lang/Object"))
                                    : concat.appendConstant(elementConstantStrings[i]));

                            methodNode.instructions.remove(element.astoreInsn);
                        }

                        methodNode.instructions.insertBefore(methodInsn, concat.appendConstant(")"));
                        if (returnTypeConstantString == null) {
                            //swap the return type with the StringBuilder instance, append it and then build the final string instead of calling getMethodDescriptor()
                            methodNode.instructions.insertBefore(methodInsn, new InsnNode(SWAP));
                            methodNode.instructions.insertBefore(methodInsn, concat.append(Type.getObjectType("java/lang/Object")));
                        } else {
                            methodNode.instructions.insertBefore(methodInsn, concat.appendConstant(returnTypeConstantString));
                        }
                        methodNode.instructions.insertBefore(methodInsn, concat.finish());
                        methodNode.instructions.remove(methodInsn);

                        for (AbstractInsnNode removeInsn : toRemove) {
                            methodNode.instructions.remove(removeInsn);
                        }

                        return CHANGED;
                    }*/
                    continue;
                }
            }
        }

        return changedFlags;
    }

    private static String tryExtractConstantTypeDesc(MethodNode methodNode, Frame<SourceValue>[] sourceFrames, AbstractInsnNode consumingInsn, int indexFromTop, List<AbstractInsnNode> toRemove) {
        AbstractInsnNode source = BytecodeHelper.getSingleSourceInsnFromTop(methodNode, sourceFrames, consumingInsn, indexFromTop);
        if (source instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode invokedynamic = (InvokeDynamicInsnNode) source;
            if ("$ppatches_getType".equals(invokedynamic.name)) {
                //appending a constant type, optimized into an INVOKEDYNAMIC above
                toRemove.add(invokedynamic);
                return (String) invokedynamic.bsmArgs[0];
            }
        } else if (source instanceof FieldInsnNode && source.getOpcode() == GETSTATIC) {
            FieldInsnNode getstatic = (FieldInsnNode) source;
            if ("org/objectweb/asm/Type".equals(getstatic.owner) && "Lorg/objectweb/asm/Type;".equals(getstatic.desc)) {
                toRemove.add(getstatic);
                return TypeUtils.primitiveTypeByName(getstatic.name.substring(0, getstatic.name.length() - "_TYPE".length()).toLowerCase(Locale.ROOT)).getDescriptor();
            }
        }
        return null;
    }

    private static final LoadingCache<String, MethodHandle> CONSTANT_TYPE_CACHE = CacheBuilder.newBuilder()
            .weakValues()
            .build(CacheLoader.from(key -> MethodHandles.constant(Type.class, Type.getType(key))));

    public static CallSite bootstrapConstantType(MethodHandles.Lookup lookup, String name, MethodType type, String arg) {
        return new ConstantCallSite(CONSTANT_TYPE_CACHE.getUnchecked(arg.intern()));
    }
}
