package net.daporkchop.ppatches.modules.asm.foldTypeConstants;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.TypeUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.ConcatGenerator;
import net.daporkchop.ppatches.util.asm.VarargsParameterDecoder;
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
public class FoldTypeConstantsTransformer implements ITreeClassTransformer {
    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changedFlags = 0;
        for (MethodNode methodNode : classNode.methods) {
            //repeatedly attempt to transform the class until no more optimizations can be applied
            while (tryTransformMethod(classNode.name, methodNode)) {
                changedFlags |= CHANGED;
            }
        }
        return changedFlags;
    }

    private static boolean tryTransformMethod(String ownerName, MethodNode methodNode) {
        Frame<SourceValue>[] sourceFrames = null;

        //set to true if a change is made which doesn't invalidate the source frame info
        boolean compatibleChange = false;

        for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != INVOKESTATIC) {
                continue;
            }

            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            if (!"org/objectweb/asm/Type".equals(methodInsn.owner)) {
                continue;
            }

            if (sourceFrames == null) {
                sourceFrames = BytecodeHelper.analyzeSources(ownerName, methodNode);
            }
            Frame<SourceValue> sourceFrame = sourceFrames[methodNode.instructions.indexOf(insn)];
            if (sourceFrame == null) { //unreachable instruction, ignore
                continue;
            }

            switch (methodInsn.name) {
                case "getDescriptor":
                case "getInternalName":
                    if ("(Ljava/lang/Class;)Ljava/lang/String;".equals(methodInsn.desc)) {
                        AbstractInsnNode src = BytecodeHelper.getSingleSourceInsnFromTop(sourceFrame, 0);
                        if (src instanceof LdcInsnNode) {
                            Type arg = (Type) ((LdcInsnNode) src).cst;
                            ((LdcInsnNode) src).cst = "getDescriptor".equals(methodInsn.name) ? arg.getDescriptor() : arg.getInternalName();
                            methodNode.instructions.remove(methodInsn);
                            compatibleChange = true;
                        }
                    }
                    continue;
                case "getType":
                    if ("(Ljava/lang/Class;)Lorg/objectweb/asm/Type;".equals(methodInsn.desc)) {
                        AbstractInsnNode src = BytecodeHelper.getSingleSourceInsnFromTop(sourceFrame, 0);
                        if (src instanceof LdcInsnNode) {
                            //getType() on a constant class, redirect to a constant string to avoid loading classes
                            LdcInsnNode ldcSrc = (LdcInsnNode) src;
                            ldcSrc.cst = ((Type) ldcSrc.cst).getDescriptor();
                            methodInsn.desc = "(Ljava/lang/String;)Lorg/objectweb/asm/Type;";
                            return true;
                        } else if (src instanceof FieldInsnNode && src.getOpcode() == GETSTATIC) {
                            FieldInsnNode fieldSrc = (FieldInsnNode) src;
                            Type primitiveType;
                            if ("TYPE".equals(fieldSrc.name) && "Ljava/lang/Class;".equals(fieldSrc.desc)
                                && (primitiveType = BytecodeHelper.unboxedPrimitiveType(fieldSrc.owner).orElse(null)) != null) {
                                //getType() on a constant primitive field, redirect to the static fields in Type
                                fieldSrc.owner = "org/objectweb/asm/Type";
                                fieldSrc.name = primitiveType.getClassName().toUpperCase(Locale.ROOT) + "_TYPE";
                                fieldSrc.desc = "Lorg/objectweb/asm/Type;";
                                methodNode.instructions.remove(methodInsn);
                                return true;
                            }
                        }
                    }
                    if ("(Ljava/lang/String;)Lorg/objectweb/asm/Type;".equals(methodInsn.desc)) {
                        AbstractInsnNode src = BytecodeHelper.getSingleSourceInsnFromTop(sourceFrame, 0);
                        if (src instanceof LdcInsnNode) {
                            //getType() on a constant string, turn it into an INVOKEDYNAMIC to avoid repeatedly allocating the same type
                            //TODO: this could be CONSTANT_DYNAMIC when available
                            AbstractInsnNode invokedynamic = new InvokeDynamicInsnNode("$ppatches_getType", "()Lorg/objectweb/asm/Type;",
                                    new Handle(H_INVOKESTATIC, "net/daporkchop/ppatches/modules/asm/foldTypeConstants/FoldTypeConstantsTransformer",
                                            "bootstrapConstantType", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false),
                                    ((LdcInsnNode) src).cst);
                            methodNode.instructions.remove(src);
                            methodNode.instructions.set(methodInsn, invokedynamic);
                            return true;
                        }
                    }
                    continue;
                case "getMethodDescriptor": {
                    if (!"(Lorg/objectweb/asm/Type;[Lorg/objectweb/asm/Type;)Ljava/lang/String;".equals(methodInsn.desc)) {
                        continue;
                    }

                    AbstractInsnNode returnTypeExprFirstInsn = BytecodeHelper.tryFindExpressionStart(methodNode, sourceFrames, methodInsn, 1).orElse(null);
                    if (returnTypeExprFirstInsn == null) {
                        continue;
                    }

                    VarargsParameterDecoder.Result varargs = VarargsParameterDecoder.tryDecode(ownerName, methodNode, methodInsn, sourceFrames).orElse(null);
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

                        return true;
                    }
                    continue;
                }
            }
        }

        return compatibleChange;
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
