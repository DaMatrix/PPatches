package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.util.COWArrayUtils;
import net.daporkchop.ppatches.util.asm.analysis.IReverseDataflowProvider;
import net.daporkchop.ppatches.util.asm.analysis.ResultUsageGraph;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.Printer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class BytecodeHelper {
    public static boolean isPrimitive(Type type) {
        return type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.DOUBLE;
    }

    public static boolean isPrimitiveOrVoid(Type type) {
        return type.getSort() <= Type.DOUBLE;
    }

    public static boolean isReference(Type type) {
        return type.getSort() == Type.ARRAY || type.getSort() == Type.OBJECT;
    }

    public static boolean isVoid(Type type) {
        return type == Type.VOID_TYPE;
    }

    public static boolean isStatic(MethodNode methodNode) {
        return (methodNode.access & ACC_STATIC) != 0;
    }

    public static boolean isNormalCodeInstruction(AbstractInsnNode insn) {
        return !(insn instanceof LabelNode || insn instanceof FrameNode || insn instanceof LineNumberNode);
    }

    public static AbstractInsnNode previousNormalCodeInstruction(AbstractInsnNode insn) {
        do {
            insn = insn.getPrevious();
        } while (insn != null && !isNormalCodeInstruction(insn));
        return insn;
    }

    public static AbstractInsnNode nextNormalCodeInstruction(AbstractInsnNode insn) {
        do {
            insn = insn.getNext();
        } while (insn != null && !isNormalCodeInstruction(insn));
        return insn;
    }

    public static AbstractInsnNode nextNormalCodeInstructionOrCurrent(AbstractInsnNode insn) {
        while (insn != null && !isNormalCodeInstruction(insn)) {
            insn = insn.getNext();
        }
        return insn;
    }

    public static boolean isFunctionalInstruction(AbstractInsnNode insn) {
        return !(insn instanceof FrameNode || insn instanceof LineNumberNode);
    }

    public static AbstractInsnNode previousFunctionalInstruction(AbstractInsnNode insn) {
        do {
            insn = insn.getPrevious();
        } while (insn != null && !isFunctionalInstruction(insn));
        return insn;
    }

    public static AbstractInsnNode nextFunctionalInstruction(AbstractInsnNode insn) {
        do {
            insn = insn.getNext();
        } while (insn != null && !isFunctionalInstruction(insn));
        return insn;
    }

    public static int findLineNumber(AbstractInsnNode insn) {
        while (!(insn instanceof LineNumberNode)) {
            insn = insn.getPrevious();
            if (insn == null) {
                return -1;
            }
        }
        return ((LineNumberNode) insn).line;
    }

    public static String findLineNumberForLog(AbstractInsnNode insn) {
        while (!(insn instanceof LineNumberNode)) {
            insn = insn.getPrevious();
            if (insn == null) {
                return "(unknown line)";
            }
        }
        return "(line " + ((LineNumberNode) insn).line + ')';
    }

    //
    // <instruction equality checks>
    //

    private static boolean isMethod(MethodInsnNode insn, String owner, String name, String desc) {
        return owner.equals(insn.owner) && name.equals(insn.name) && desc.equals(insn.desc);
    }

    public static boolean isINVOKEVIRTUAL(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == INVOKEVIRTUAL && isMethod((MethodInsnNode) insn, owner, name, desc);
    }

    public static boolean isINVOKESPECIAL(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == INVOKESPECIAL && isMethod((MethodInsnNode) insn, owner, name, desc);
    }

    public static boolean isINVOKESTATIC(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == INVOKESTATIC && isMethod((MethodInsnNode) insn, owner, name, desc);
    }

    public static boolean isINVOKEINTERFACE(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == INVOKEINTERFACE && isMethod((MethodInsnNode) insn, owner, name, desc);
    }

    public static boolean isCHECKCAST(AbstractInsnNode insn, String internalName) {
        return insn.getOpcode() == CHECKCAST && internalName.equals(((TypeInsnNode) insn).desc);
    }

    public static boolean isCHECKCAST(AbstractInsnNode insn, Type type) {
        return isCHECKCAST(insn, type.getInternalName());
    }

    private static boolean isField(FieldInsnNode insn, String owner, String name, String desc) {
        return owner.equals(insn.owner) && name.equals(insn.name) && desc.equals(insn.desc);
    }

    public static boolean isGETSTATIC(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == GETSTATIC && isField((FieldInsnNode) insn, owner, name, desc);
    }

    public static boolean isGETFIELD(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == GETFIELD && isField((FieldInsnNode) insn, owner, name, desc);
    }

    public static boolean isPUTSTATIC(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == PUTSTATIC && isField((FieldInsnNode) insn, owner, name, desc);
    }

    public static boolean isPUTFIELD(AbstractInsnNode insn, String owner, String name, String desc) {
        return insn.getOpcode() == PUTFIELD && isField((FieldInsnNode) insn, owner, name, desc);
    }

    public static boolean isHandle(Handle handle, int tag, String owner, String name) {
        return handle.getTag() == tag && owner.equals(handle.getOwner()) && name.equals(handle.getName());
    }

    public static boolean isHandle(Handle handle, int tag, String owner, String name, String desc) {
        return isHandle(handle, tag, owner, desc) && desc.equals(handle.getDesc());
    }

    public static boolean isReturnInsn(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            case RETURN:
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
                return true;
            default:
                return false;
        }
    }

    //
    // </instruction equality checks>
    //

    public static boolean isConstant(AbstractInsnNode insn) {
        if (insn instanceof LdcInsnNode) {
            return true;
        }

        switch (insn.getOpcode()) {
            case BIPUSH: //byte immediate
            case SIPUSH: //short immediate
                assert insn instanceof IntInsnNode;
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case LCONST_0:
            case LCONST_1:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case DCONST_0:
            case DCONST_1:
                return true;
            default:
                return false;
        }
    }

    public static Object decodeConstant(AbstractInsnNode insn) {
        if (insn instanceof LdcInsnNode) {
            return ((LdcInsnNode) insn).cst;
        }

        switch (insn.getOpcode()) {
            case ACONST_NULL:
                return null;
            case ICONST_M1:
                return -1;
            case ICONST_0:
                return 0;
            case ICONST_1:
                return 1;
            case ICONST_2:
                return 2;
            case ICONST_3:
                return 3;
            case ICONST_4:
                return 4;
            case ICONST_5:
                return 5;
            case LCONST_0:
                return 0L;
            case LCONST_1:
                return 1L;
            case FCONST_0:
                return 0.0f;
            case FCONST_1:
                return 1.0f;
            case FCONST_2:
                return 2.0f;
            case DCONST_0:
                return 0.0d;
            case DCONST_1:
                return 1.0d;
            case BIPUSH: //byte immediate
            case SIPUSH: //short immediate
                return ((IntInsnNode) insn).operand;
            default:
                throw new IllegalArgumentException("not a constant value: " + Printer.OPCODES[insn.getOpcode()]);
        }
    }

    public static InsnNode loadConstantDefaultValueInsn(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                return new InsnNode(ICONST_0);
            case Type.LONG:
                return new InsnNode(LCONST_0);
            case Type.FLOAT:
                return new InsnNode(FCONST_0);
            case Type.DOUBLE:
                return new InsnNode(DCONST_0);
            case Type.OBJECT:
            case Type.ARRAY:
                return new InsnNode(ACONST_NULL);
            default:
                throw new IllegalArgumentException(type.toString());
        }
    }

    public static AbstractInsnNode loadConstantInsn(Object cst) {
        if (cst == null) {
            return new InsnNode(ACONST_NULL);
        } else if (cst instanceof Integer) {
            int intVal = (Integer) cst;
            if (intVal >= -1 && intVal <= 5) {
                return new InsnNode(ICONST_0 + intVal);
            } else if (intVal >= Byte.MIN_VALUE && intVal <= Byte.MAX_VALUE) {
                return new IntInsnNode(BIPUSH, intVal);
            } else if (intVal >= Short.MIN_VALUE && intVal <= Short.MAX_VALUE) {
                return new IntInsnNode(SIPUSH, intVal);
            }
        } else if (cst instanceof Long) {
            long longVal = (Long) cst;
            if (longVal >= 0L && longVal <= 1L) {
                return new InsnNode(LCONST_0 + (int) longVal);
            }
        } else if (cst instanceof Float) {
            int floatBits = Float.floatToRawIntBits((Float) cst);
            if (floatBits == Float.floatToRawIntBits(0.0f)) {
                return new InsnNode(FCONST_0);
            } else if (floatBits == Float.floatToRawIntBits(1.0f)) {
                return new InsnNode(FCONST_1);
            } else if (floatBits == Float.floatToRawIntBits(2.0f)) {
                return new InsnNode(FCONST_2);
            }
        } else if (cst instanceof Double) {
            long doubleBits = Double.doubleToRawLongBits((Double) cst);
            if (doubleBits == Double.doubleToRawLongBits(0.0f)) {
                return new InsnNode(DCONST_0);
            } else if (doubleBits == Double.doubleToRawLongBits(1.0f)) {
                return new InsnNode(DCONST_1);
            }
        }
        return new LdcInsnNode(cst);
    }

    public static AbstractInsnNode dup(Type type) {
        switch (type.getSize()) {
            case 1:
                return new InsnNode(DUP);
            case 2:
                return new InsnNode(DUP2);
        }
        throw new IllegalStateException();
    }

    public static AbstractInsnNode dup_x1(Type type) {
        switch (type.getSize()) {
            case 1:
                return new InsnNode(DUP_X1);
            case 2:
                return new InsnNode(DUP2_X1);
        }
        throw new IllegalStateException();
    }

    public static AbstractInsnNode dup_x2(Type type) {
        switch (type.getSize()) {
            case 1:
                return new InsnNode(DUP_X2);
            case 2:
                return new InsnNode(DUP2_X2);
        }
        throw new IllegalStateException();
    }

    public static AbstractInsnNode dup_below(Type belowType, Type type) {
        switch (belowType.getSize()) {
            case 1:
                return dup_x1(type);
            case 2:
                return dup_x2(type);
        }
        throw new IllegalStateException();
    }

    public static InsnList swap(Type t0, Type t1) {
        int s0 = t0.getSize();
        int s1 = t1.getSize();
        if (s0 == 1 && s1 == 1) {
            return makeInsnList(new InsnNode(SWAP));
        } else if (s0 == 1 && s1 == 2) {
            return makeInsnList(new InsnNode(DUP2_X1), new InsnNode(POP2));
        } else if (s0 == 2 && s1 == 1) {
            return makeInsnList(new InsnNode(DUP_X2), new InsnNode(POP));
        } else if (s0 == 2 && s1 == 2) {
            return makeInsnList(new InsnNode(DUP2_X2), new InsnNode(POP2));
        } else {
            throw new IllegalStateException();
        }
    }

    public static AbstractInsnNode pop(Type type) {
        switch (type.getSize()) {
            case 1:
                return new InsnNode(POP);
            case 2:
                return new InsnNode(POP2);
        }
        throw new IllegalStateException();
    }

    private static final String[] BOXED_INTERNAL_TYPE_NAMES_BY_SORT = {
            "java/lang/Void",
            "java/lang/Boolean",
            "java/lang/Character",
            "java/lang/Byte",
            "java/lang/Short",
            "java/lang/Integer",
            "java/lang/Float",
            "java/lang/Long",
            "java/lang/Double",
    };

    public static String boxedInternalName(Type primitiveType) {
        assert isPrimitive(primitiveType) : "not a primitive type: " + primitiveType;
        return BOXED_INTERNAL_TYPE_NAMES_BY_SORT[primitiveType.getSort()];
    }

    private static final String[] BOXED_INTERNAL_BASE_TYPE_NAMES_BY_SORT = {
            "java/lang/Void",
            "java/lang/Boolean",
            "java/lang/Character",
            "java/lang/Number",
            "java/lang/Number",
            "java/lang/Number",
            "java/lang/Number",
            "java/lang/Number",
            "java/lang/Number",
    };

    public static String boxedInternalBaseName(Type primitiveType) {
        assert isPrimitive(primitiveType) : "not a primitive type: " + primitiveType;
        return BOXED_INTERNAL_BASE_TYPE_NAMES_BY_SORT[primitiveType.getSort()];
    }

    public static Optional<Type> unboxedPrimitiveType(String wrapperTypeInternalName) {
        for (int sort = 0; sort < BOXED_INTERNAL_TYPE_NAMES_BY_SORT.length; sort++) {
            if (wrapperTypeInternalName.equals(BOXED_INTERNAL_TYPE_NAMES_BY_SORT[sort])) {
                return Optional.of(TypeUtils.primitiveTypeBySort(sort));
            }
        }
        return Optional.empty();
    }

    public static MethodInsnNode generateBoxingConversion(Type primitiveType) {
        String internalName = boxedInternalName(primitiveType);
        return new MethodInsnNode(INVOKESTATIC, internalName, "valueOf", '(' + primitiveType.getDescriptor() + ")L" + internalName + ';', false);
    }

    public static MethodInsnNode generateUnboxingConversion(Type primitiveType) {
        return new MethodInsnNode(INVOKEVIRTUAL, boxedInternalName(primitiveType), primitiveType.getClassName() + "Value", "()" + primitiveType, false);
    }

    public static MethodInsnNode generateUnboxingFromBaseConversion(Type primitiveType) {
        return new MethodInsnNode(INVOKEVIRTUAL, boxedInternalBaseName(primitiveType), primitiveType.getClassName() + "Value", "()" + primitiveType, false);
    }

    public static AbstractInsnNode generateNewArray(Type elementType) {
        int primitiveType;
        switch (elementType.getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
                return new TypeInsnNode(ANEWARRAY, elementType.getInternalName());
            case Type.BOOLEAN:
                primitiveType = T_BOOLEAN;
                break;
            case Type.CHAR:
                primitiveType = T_CHAR;
                break;
            case Type.BYTE:
                primitiveType = T_BYTE;
                break;
            case Type.SHORT:
                primitiveType = T_SHORT;
                break;
            case Type.INT:
                primitiveType = T_INT;
                break;
            case Type.LONG:
                primitiveType = T_LONG;
                break;
            case Type.FLOAT:
                primitiveType = T_FLOAT;
                break;
            case Type.DOUBLE:
                primitiveType = T_DOUBLE;
                break;
            default:
                throw new IllegalArgumentException("can't make array of type " + elementType);
        }
        return new IntInsnNode(NEWARRAY, primitiveType);
    }

    public static Optional<Type> decodeNewArrayType(AbstractInsnNode newArrayInsn) {
        switch (newArrayInsn.getOpcode()) {
            case ANEWARRAY:
                return Optional.of(Type.getType('[' + ((TypeInsnNode) newArrayInsn).desc));
            case MULTIANEWARRAY:
                return Optional.of(Type.getType(((MultiANewArrayInsnNode) newArrayInsn).desc));
            case NEWARRAY:
                switch (((IntInsnNode) newArrayInsn).operand) {
                    case T_BOOLEAN:
                        return Optional.of(TypeUtils.getArrayType(Type.BOOLEAN_TYPE));
                    case T_CHAR:
                        return Optional.of(TypeUtils.getArrayType(Type.CHAR_TYPE));
                    case T_BYTE:
                        return Optional.of(TypeUtils.getArrayType(Type.BYTE_TYPE));
                    case T_SHORT:
                        return Optional.of(TypeUtils.getArrayType(Type.SHORT_TYPE));
                    case T_INT:
                        return Optional.of(TypeUtils.getArrayType(Type.INT_TYPE));
                    case T_LONG:
                        return Optional.of(TypeUtils.getArrayType(Type.LONG_TYPE));
                    case T_FLOAT:
                        return Optional.of(TypeUtils.getArrayType(Type.FLOAT_TYPE));
                    case T_DOUBLE:
                        return Optional.of(TypeUtils.getArrayType(Type.DOUBLE_TYPE));
                }
        }
        return Optional.empty();
    }

    public static InsnList flattenHandlePre(Handle handle) {
        String owner = handle.getOwner();
        String name = handle.getName();
        String desc = handle.getDesc();
        boolean itf = handle.isInterface();

        switch (handle.getTag()) {
            case H_GETFIELD:
            case H_GETSTATIC:
            case H_PUTFIELD:
            case H_PUTSTATIC:
            case H_INVOKEVIRTUAL:
            case H_INVOKESTATIC:
            case H_INVOKESPECIAL:
            case H_INVOKEINTERFACE:
                return makeInsnList();
            case H_NEWINVOKESPECIAL:
                //create the instance, its constructor will be invoked by the instructions generated by flattenHandlePost()
                return makeInsnList(
                        new TypeInsnNode(NEW, owner),
                        new InsnNode(DUP));
        }
        throw new IllegalStateException("illegal handle tag " + handle.getTag());
    }

    public static InsnList flattenHandlePost(Handle handle) {
        String owner = handle.getOwner();
        String name = handle.getName();
        String desc = handle.getDesc();
        boolean itf = handle.isInterface();

        switch (handle.getTag()) {
            case H_GETFIELD:
                return makeInsnList(new FieldInsnNode(GETFIELD, owner, name, desc));
            case H_GETSTATIC:
                return makeInsnList(new FieldInsnNode(GETSTATIC, owner, name, desc));
            case H_PUTFIELD:
                return makeInsnList(new FieldInsnNode(PUTFIELD, owner, name, desc));
            case H_PUTSTATIC:
                return makeInsnList(new FieldInsnNode(PUTSTATIC, owner, name, desc));
            case H_INVOKEVIRTUAL:
                return makeInsnList(new MethodInsnNode(INVOKEVIRTUAL, owner, name, desc, itf));
            case H_INVOKESTATIC:
                return makeInsnList(new MethodInsnNode(INVOKESTATIC, owner, name, desc, itf));
            case H_INVOKESPECIAL:
                return makeInsnList(new MethodInsnNode(INVOKESPECIAL, owner, name, desc, itf));
            case H_INVOKEINTERFACE:
                return makeInsnList(new MethodInsnNode(INVOKEINTERFACE, owner, name, desc, itf));
            case H_NEWINVOKESPECIAL:
                //invoke the constructor, the new instance was created and pushed onto the stack by the code generated by flattenHandlePre()
                return makeInsnList(new MethodInsnNode(INVOKESPECIAL, owner, name, desc, itf));
        }
        throw new IllegalStateException("illegal handle tag " + handle.getTag());
    }

    public static Type getEffectiveHandleMethodType(Handle handle) {
        String owner = handle.getOwner();
        String desc = handle.getDesc();

        switch (handle.getTag()) {
            case H_GETFIELD:
                return Type.getMethodType(Type.getType(desc), Type.getObjectType(owner));
            case H_GETSTATIC:
                return Type.getMethodType(Type.getType(desc));
            case H_PUTFIELD:
                return Type.getMethodType(Type.VOID_TYPE, Type.getObjectType(owner), Type.getType(desc));
            case H_PUTSTATIC:
                return Type.getMethodType(Type.VOID_TYPE, Type.getType(desc));
            case H_INVOKESTATIC:
                return Type.getMethodType(desc);
            case H_INVOKEVIRTUAL:
            case H_INVOKESPECIAL:
            case H_INVOKEINTERFACE:
                return Type.getMethodType(Type.getReturnType(desc), COWArrayUtils.insert(Type.getArgumentTypes(desc), 0, Type.getObjectType(owner)));
            case H_NEWINVOKESPECIAL:
                return Type.getMethodType(Type.getObjectType(owner), Type.getArgumentTypes(desc));
            default:
                throw new IllegalStateException("illegal handle tag " + handle.getTag());
        }
    }

    public static InsnList generateNonNullAssertion(boolean preserveOnStack, Object... optionalStaticMethodComponents) {
        InsnList seq = new InsnList();
        LabelNode tailLbl = new LabelNode();
        seq.add(InvokeDynamicUtils.makeLoadAssertionStateInsn());
        seq.add(new JumpInsnNode(IFEQ, tailLbl));
        if (preserveOnStack) {
            seq.add(new InsnNode(DUP));
        }
        seq.add(new JumpInsnNode(IFNONNULL, tailLbl));
        seq.add(InvokeDynamicUtils.makeNewException(NullPointerException.class, optionalStaticMethodComponents));
        seq.add(new InsnNode(ATHROW));
        seq.add(tailLbl);
        return seq;
    }

    public static InsnList generateBooleanNegation() {
        InsnList seq = new InsnList();
        LabelNode trueLbl = new LabelNode();
        LabelNode tailLbl = new LabelNode();
        seq.add(new JumpInsnNode(IFNE, trueLbl));
        seq.add(new InsnNode(ICONST_1));
        seq.add(new JumpInsnNode(GOTO, tailLbl));
        seq.add(trueLbl);
        seq.add(new InsnNode(ICONST_0));
        seq.add(tailLbl);
        return seq;
    }

    public static Optional<InsnList> tryGenerateWideningConversion(Type from, Type to) {
        assert !isReference(from) && !isReference(to);

        if (from == to) {
            return Optional.of(makeInsnList());
        }

        switch (from.getSort()) {
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                switch (to.getSort()) {
                    case Type.CHAR:
                        return Optional.of(makeInsnList(new InsnNode(I2C)));
                    case Type.BYTE:
                        return Optional.of(makeInsnList(new InsnNode(I2B)));
                    case Type.SHORT:
                        return Optional.of(makeInsnList(new InsnNode(I2S)));
                    case Type.INT:
                        return Optional.of(makeInsnList());
                    case Type.LONG:
                        return Optional.of(makeInsnList(new InsnNode(I2L)));
                    case Type.FLOAT:
                        return Optional.of(makeInsnList(new InsnNode(I2F)));
                    case Type.DOUBLE:
                        return Optional.of(makeInsnList(new InsnNode(I2D)));
                }
                break;
            case Type.LONG:
                switch (to.getSort()) {
                    case Type.FLOAT:
                        return Optional.of(makeInsnList(new InsnNode(L2F)));
                    case Type.DOUBLE:
                        return Optional.of(makeInsnList(new InsnNode(L2D)));
                }
                break;
            case Type.FLOAT:
                if (to.getSort() == Type.DOUBLE) {
                    return Optional.of(makeInsnList(new InsnNode(F2D)));
                }
                break;
        }

        return Optional.empty();
    }

    public static List<MethodNode> findMethod(ClassNode classNode, String name) {
        List<MethodNode> out = null;
        for (MethodNode methodNode : classNode.methods) {
            if (name.equals(methodNode.name)) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(methodNode);
            }
        }
        return out != null ? out : Collections.emptyList();
    }

    public static Optional<MethodNode> findMethod(ClassNode classNode, String name, String desc) {
        Optional<MethodNode> result = Optional.empty();
        for (MethodNode methodNode : classNode.methods) {
            if (name.equals(methodNode.name) && desc.equals(methodNode.desc)) {
                if (result.isPresent()) {
                    throw new IllegalStateException("already found a method named " + name + " with desc " + desc);
                }
                result = Optional.of(methodNode);
            }
        }
        return result;
    }

    public static MethodNode findMethodOrThrow(ClassNode classNode, String name, String desc) {
        Optional<MethodNode> result = findMethod(classNode, name, desc);
        if (!result.isPresent()) {
            throw new IllegalStateException("couldn't find method named " + name + " with desc " + desc + " in class " + classNode.name);
        }
        return result.get();
    }

    public static List<MethodNode> findObfuscatedMethod(ClassNode classNode, Collection<String> names) {
        List<MethodNode> out = null;
        for (MethodNode methodNode : classNode.methods) {
            if (names.contains(methodNode.name)) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(methodNode);
            }
        }
        return out != null ? out : Collections.emptyList();
    }

    public static Optional<MethodNode> findObfuscatedMethod(ClassNode classNode, Collection<String> names, String desc) {
        Optional<MethodNode> result = Optional.empty();
        for (MethodNode methodNode : classNode.methods) {
            if (desc.equals(methodNode.desc) && names.contains(methodNode.name)) {
                if (result.isPresent()) {
                    throw new IllegalStateException("already found a method named " + names + " with desc " + desc);
                }
                result = Optional.of(methodNode);
            }
        }
        return result;
    }

    public static MethodNode findObfuscatedMethodOrThrow(ClassNode classNode, Collection<String> names, String desc) {
        Optional<MethodNode> result = findObfuscatedMethod(classNode, names, desc);
        if (!result.isPresent()) {
            throw new IllegalStateException("couldn't find method named " + names + " with desc " + desc + " in class " + classNode.name);
        }
        return result.get();
    }

    public static Optional<FieldNode> findField(ClassNode classNode, String name, String desc) {
        Optional<FieldNode> result = Optional.empty();
        for (FieldNode fieldNode : classNode.fields) {
            if (name.equals(fieldNode.name) && desc.equals(fieldNode.desc)) {
                if (result.isPresent()) {
                    throw new IllegalStateException("already found a field named " + name + " with desc " + desc);
                }
                result = Optional.of(fieldNode);
            }
        }
        return result;
    }

    public static MethodNode getOrCreateClinit(ClassNode classNode) {
        for (MethodNode methodNode : classNode.methods) {
            if ("<clinit>".equals(methodNode.name)) {
                return methodNode;
            }
        }

        MethodNode methodNode = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        methodNode.instructions.add(new InsnNode(RETURN));
        classNode.methods.add(methodNode);
        return methodNode;
    }

    public static boolean methodDescriptorContainsMatchingArgument(String methodDesc, Type searchArgumentType) {
        return methodDescriptorCountMatchingArguments(methodDesc, searchArgumentType) > 0;
    }

    public static int methodDescriptorCountMatchingArguments(String methodDesc, Type searchArgumentType) {
        int result = 0;
        for (Type argumentType : Type.getArgumentTypes(methodDesc)) {
            if (argumentType.equals(searchArgumentType)) {
                result++;
            }
        }
        return result;
    }

    public static OptionalInt methodDescriptorIndexOfMatchingArgument(String methodDesc, Type searchArgumentType) {
        Type[] argumentTypes = Type.getArgumentTypes(methodDesc);
        for (int i = 0; i < argumentTypes.length; i++) {
            if (argumentTypes[i].equals(searchArgumentType)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public static OptionalInt methodDescriptorOffsetOfMatchingArgument(String methodDesc, Type searchArgumentType) {
        int offset = 0;
        for (Type argumentType : Type.getArgumentTypes(methodDesc)) {
            if (argumentType.equals(searchArgumentType)) {
                return OptionalInt.of(offset);
            }
            offset += argumentType.getSize();
        }
        return OptionalInt.empty();
    }

    public static Optional<LocalVariableNode> findLocalVariable(MethodNode methodNode, String name, String desc) {
        if (methodNode.localVariables != null) {
            for (LocalVariableNode localVariableNode : methodNode.localVariables) {
                if (name.equals(localVariableNode.name) && desc.equals(localVariableNode.desc)) {
                    return Optional.of(localVariableNode);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean hasAnnotationWithDesc(List<AnnotationNode> annotations, String desc) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (desc.equals(annotation.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Optional<AnnotationNode> findAnnotationByDesc(List<AnnotationNode> annotations, String desc) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (desc.equals(annotation.desc)) {
                    return Optional.of(annotation);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<Object> findAnnotationValueByName(AnnotationNode annotationNode, String name) {
        if (annotationNode.values != null) {
            for (int i = 0; i < annotationNode.values.size(); i += 2) {
                if (name.equals(annotationNode.values.get(i))) {
                    return Optional.of(annotationNode.values.get(i + 1));
                }
            }
        }
        return Optional.empty();
    }

    public static Frame<SourceValue>[] analyzeSources(String ownerName, MethodNode methodNode) {
        return analyze(new Analyzer<>(new SourceInterpreter()), ownerName, methodNode);
    }

    @SneakyThrows(AnalyzerException.class)
    public static <V extends Value> Frame<V>[] analyze(Analyzer<V> analyzer, String ownerName, MethodNode methodNode) {
        do {
            try {
                return analyzer.analyze(ownerName, methodNode);
            } catch (AnalyzerException e) {
                if (e.getMessage().endsWith("Insufficient maximum stack size.")) {
                    //the method has probably been modified by another transformer and its maximum stack size needs to be
                    //recomputed, so we'll fix it here (very inefficiently)
                    methodNode.maxStack++;
                    continue;
                }
                throw e;
            }
        } while (true);
    }

    public static Set<AbstractInsnNode> analyzeUsages(String ownerName, MethodNode methodNode, Frame<SourceValue>[] sources, AbstractInsnNode sourceInsn) {
        if (sources[methodNode.instructions.indexOf(sourceInsn)] == null) { //source instruction is unreachable
            return null;
        }

        ImmutableSet.Builder<AbstractInsnNode> usages = ImmutableSet.builder();

        for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            Frame<SourceValue> sourceFrame = sources[methodNode.instructions.indexOf(insn)];
            if (sourceFrame == null //unreachable code
                || !isNormalCodeInstruction(insn)) { //ignore labels, frames and line numbers
                continue;
            }

            for (int i = getConsumedStackOperandCount(insn, sourceFrame) - 1; i >= 0; i--) {
                if (getStackValueFromTop(sourceFrame, i).insns.contains(sourceInsn)) {
                    usages.add(insn);
                    break;
                }
            }
        }

        return usages.build();
    }

    public static ResultUsageGraph analyzeUsages(String ownerName, MethodNode methodNode, Frame<SourceValue>[] sources) {
        return new ResultUsageGraph(methodNode, sources);

        /*Map<SourceValue, UsageValue> sourceInstancesToUsageInstances = new IdentityHashMap<>(sources.length * 3);
        Function<SourceValue, UsageValue> sourceInstanceToUsageInstanceMapper = sourceValue -> {
            return null; //TODO
        };

        @SuppressWarnings("unchecked")
        Frame<UsageValue>[] usages = (Frame<UsageValue>[]) new Frame[sources.length];

        Type methodReturnType = Type.getReturnType(methodNode.desc);
        UsageValue newReturnValue = methodReturnType == Type.VOID_TYPE ? null : new UsageValue(methodReturnType.getSize());

        for (int i = 0; i < sources.length; i++) {
            Frame<SourceValue> sourceFrame = sources[i];
            if (sourceFrame == null) { //unreachable code
                continue;
            }

            Frame<UsageValue> usageFrame = new Frame<>(sourceFrame.getLocals(), sourceFrame.getMaxStackSize());
            usageFrame.setReturn(newReturnValue);

            for (int localIndex = 0; localIndex < sourceFrame.getLocals(); localIndex++) {
                if (sourceFrame.getLocal(localIndex) != null) {
                    usageFrame.setLocal(localIndex, sourceInstancesToUsageInstances.computeIfAbsent(sourceFrame.getLocal(localIndex), sourceInstanceToUsageInstanceMapper));
                }
            }
            for (int stackIndex = 0; stackIndex < sourceFrame.getStackSize(); stackIndex++) {
                if (sourceFrame.getStack(stackIndex) != null) {
                    usageFrame.push(sourceInstancesToUsageInstances.computeIfAbsent(sourceFrame.getStack(stackIndex), sourceInstanceToUsageInstanceMapper));
                }
            }
        }*/
    }

    public static <V extends Value> V getStackValueFromTop(Frame<V> frame, int indexFromTop) {
        int stackSize = frame.getStackSize();
        Preconditions.checkElementIndex(indexFromTop, stackSize, "indexFromTop");
        return frame.getStack(stackSize - 1 - indexFromTop);
    }

    public static <V extends Value> V getStackValueFromTop(MethodNode methodNode, Frame<V>[] frames, AbstractInsnNode position, int indexFromTop) {
        Frame<V> frame = frames[methodNode.instructions.indexOf(position)];
        return frame != null
                ? getStackValueFromTop(frame, indexFromTop)
                : null; //unreachable instruction, ignore
    }

    public static AbstractInsnNode getSingleSourceInsnFromTop(Frame<SourceValue> sourceFrame, int indexFromTop) {
        Set<AbstractInsnNode> insns = getStackValueFromTop(sourceFrame, indexFromTop).insns;
        return insns.size() == 1 ? insns.iterator().next() : null;
    }

    public static AbstractInsnNode getSingleSourceInsnFromTop(MethodNode methodNode, Frame<SourceValue>[] sourceFrames, AbstractInsnNode position, int indexFromTop) {
        Frame<SourceValue> sourceFrame = sourceFrames[methodNode.instructions.indexOf(position)];
        return sourceFrame != null
                ? getSingleSourceInsnFromTop(sourceFrame, indexFromTop)
                : null; //unreachable instruction, ignore
    }

    public static <V extends Value> int getUsedStackSlots(Frame<V> frame) {
        int slots = 0;
        for (int i = 0; i < frame.getStackSize(); i++) {
            slots += frame.getStack(i).getSize();
        }
        return slots;
    }

    public static <V extends Value> int getConsumedStackOperandCount(AbstractInsnNode insn, Frame<V> frame) {
        //the number of stack operands consumed, with long and double values taking up only a single value
        int consumedStackOperands;

        switch (insn.getOpcode()) {
            case NOP:
                consumedStackOperands = 0;
                break;
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case LCONST_0:
            case LCONST_1:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case DCONST_0:
            case DCONST_1:
            case BIPUSH:
            case SIPUSH:
            case LDC:
                consumedStackOperands = 0;
                break;
            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD:
                consumedStackOperands = 0;
                break;
            case IALOAD:
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
                consumedStackOperands = 2;
                break;
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
                consumedStackOperands = 1;
                break;
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
                consumedStackOperands = 3;
                break;
            case POP: //pop a single category 1 type
                assert getStackValueFromTop(frame, 0).getSize() == 1;
                consumedStackOperands = 1;
                break;
            case POP2: //pop either two category 1 types or a single category two type
                if (getStackValueFromTop(frame, 0).getSize() == 1) {
                    assert getStackValueFromTop(frame, 1).getSize() == 1;
                    consumedStackOperands = 2;
                } else {
                    consumedStackOperands = 1;
                }
                break;
            case DUP:
            case DUP_X1:
            case DUP_X2: //a single category 1 type is "consumed"
                assert getStackValueFromTop(frame, 0).getSize() == 1;
                consumedStackOperands = 1;
                break;
            case DUP2:
            case DUP2_X1:
            case DUP2_X2: //either two category 1 types or a single category two type are "consumed"
                if (getStackValueFromTop(frame, 0).getSize() == 1) {
                    assert getStackValueFromTop(frame, 1).getSize() == 1;
                    consumedStackOperands = 2;
                } else {
                    consumedStackOperands = 1;
                }
                break;
            case SWAP: //two category 1 types are "consumed"
                assert getStackValueFromTop(frame, 0).getSize() == 1;
                assert getStackValueFromTop(frame, 1).getSize() == 1;
                consumedStackOperands = 2;
                break;
            case IADD:
            case LADD:
            case FADD:
            case DADD:
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
            case IREM:
            case LREM:
            case FREM:
            case DREM:
                consumedStackOperands = 2;
                break;
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG:
                consumedStackOperands = 1;
                break;
            case ISHL:
            case LSHL:
            case ISHR:
            case LSHR:
            case IUSHR:
            case LUSHR:
            case IAND:
            case LAND:
            case IOR:
            case LOR:
            case IXOR:
            case LXOR:
                consumedStackOperands = 2;
                break;
            case IINC:
                consumedStackOperands = 0;
                break;
            case I2L:
            case I2F:
            case I2D:
            case L2I:
            case L2F:
            case L2D:
            case F2I:
            case F2L:
            case F2D:
            case D2I:
            case D2L:
            case D2F:
            case I2B:
            case I2C:
            case I2S:
                consumedStackOperands = 1;
                break;
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                consumedStackOperands = 2;
                break;
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
                consumedStackOperands = 1;
                break;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
                consumedStackOperands = 2;
                break;
            case GOTO:
            case JSR:
            case RET:
                consumedStackOperands = 0;
                break;
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
                consumedStackOperands = 1;
                break;
            case RETURN:
            case GETSTATIC:
                consumedStackOperands = 0;
                break;
            case PUTSTATIC:
            case GETFIELD:
                consumedStackOperands = 1;
                break;
            case PUTFIELD:
                consumedStackOperands = 2;
                break;
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                consumedStackOperands = Type.getArgumentTypes(((MethodInsnNode) insn).desc).length + (insn.getOpcode() != INVOKESTATIC ? 1 : 0);
                break;
            }
            case INVOKEDYNAMIC: {
                consumedStackOperands = Type.getArgumentTypes(((InvokeDynamicInsnNode) insn).desc).length;
                break;
            }
            case NEW:
                consumedStackOperands = 0;
                break;
            case NEWARRAY:
            case ANEWARRAY:
            case ARRAYLENGTH:
            case ATHROW:
            case CHECKCAST:
            case INSTANCEOF:
            case MONITORENTER:
            case MONITOREXIT:
                consumedStackOperands = 1;
                break;
            case MULTIANEWARRAY:
                consumedStackOperands = ((MultiANewArrayInsnNode) insn).dims;
                break;
            case IFNULL:
            case IFNONNULL:
                consumedStackOperands = 1;
                break;
            default:
                throw new RuntimeException("Illegal opcode " + Printer.OPCODES[insn.getOpcode()]);
        }

        return consumedStackOperands;
    }

    public static Optional<AbstractInsnNode> tryFindExpressionStart(MethodNode methodNode, IReverseDataflowProvider dataflowProvider, AbstractInsnNode expressionConsumer, int expressionIndexFromTop) {
        //BFS through instructions producing stack operands until we find the first one. we assume the first one of them
        AbstractInsnNode firstInsn = expressionConsumer;
        int firstInsnIndex = methodNode.instructions.indexOf(firstInsn);

        Queue<AbstractInsnNode> queue = new ArrayDeque<>(dataflowProvider.getStackOperandSourcesFromTop(expressionConsumer, expressionIndexFromTop).insns);
        if (queue.size() != 1) {
            //TODO: this code can't work for conditionals...
            return Optional.empty();
        }

        Set<AbstractInsnNode> visitedExprs = new HashSet<>();

        for (AbstractInsnNode curr; (curr = queue.poll()) != null; ) {
            if (visitedExprs.add(curr)) {
                int currInsnIndex = methodNode.instructions.indexOf(curr);

                if (currInsnIndex < firstInsnIndex) {
                    firstInsn = curr;
                    firstInsnIndex = currInsnIndex;
                }

                for (SourceValue consumedOperandValue : dataflowProvider.getStackOperandSources(curr)) {
                    if (consumedOperandValue.insns.size() > 1) {
                        //TODO: this code can't work for conditionals...
                        return Optional.empty();
                    }
                    queue.addAll(consumedOperandValue.insns);
                }
            }
        }
        Preconditions.checkState(firstInsn != expressionConsumer, "stack operand has no source instructions?");
        return Optional.of(firstInsn);
    }

    public static List<? extends AbstractInsnNode> possibleNextInstructions(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            case RETURN: //return instructions can't advance to any instruction, as they're at the end of a control flow chain
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ATHROW: //ATHROW instructions can't advance to any instruction, as they're at the end of a control flow chain
                return ImmutableList.of();
            case GOTO: //unlike other jump instructions, GOTO can only advance to the label
                return ImmutableList.of(((JumpInsnNode) insn).label);

            //TABLESWITCH and LOOKUPSWITCH can advance to any of the target labels or to the default label
            case TABLESWITCH: {
                TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) insn;
                return tableSwitchInsn.labels.contains(tableSwitchInsn.dflt) ? tableSwitchInsn.labels : ImmutableList.<LabelNode>builder().addAll(tableSwitchInsn.labels).add(tableSwitchInsn.dflt).build();
            }
            case LOOKUPSWITCH: {
                LookupSwitchInsnNode lookupSwitchInsnNode = (LookupSwitchInsnNode) insn;
                return lookupSwitchInsnNode.labels.contains(lookupSwitchInsnNode.dflt) ? lookupSwitchInsnNode.labels : ImmutableList.<LabelNode>builder().addAll(lookupSwitchInsnNode.labels).add(lookupSwitchInsnNode.dflt).build();
            }
        }

        AbstractInsnNode next = insn.getNext();
        return insn instanceof JumpInsnNode
                ? ImmutableList.of(((JumpInsnNode) insn).label, next) //conditional jump instructions can advance to the label or the next instruction
                : ImmutableList.of(next); //normal instructions can only advance to the next instruction
    }

    public static boolean canAdvanceJumpingToLabel(AbstractInsnNode insn) {
        return insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode;
    }

    public static List<LabelNode> possibleNextLabels(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            //TABLESWITCH and LOOKUPSWITCH can advance to any of the target labels or to the default label
            case TABLESWITCH: {
                TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) insn;
                return tableSwitchInsn.labels.contains(tableSwitchInsn.dflt) ? tableSwitchInsn.labels : ImmutableList.<LabelNode>builder().addAll(tableSwitchInsn.labels).add(tableSwitchInsn.dflt).build();
            }
            case LOOKUPSWITCH: {
                LookupSwitchInsnNode lookupSwitchInsnNode = (LookupSwitchInsnNode) insn;
                return lookupSwitchInsnNode.labels.contains(lookupSwitchInsnNode.dflt) ? lookupSwitchInsnNode.labels : ImmutableList.<LabelNode>builder().addAll(lookupSwitchInsnNode.labels).add(lookupSwitchInsnNode.dflt).build();
            }
        }

        return insn instanceof JumpInsnNode
                ? ImmutableList.of(((JumpInsnNode) insn).label) //conditional jump instructions can advance to the label or the next instruction
                : ImmutableList.of(); //normal instructions can only advance to the next instruction
    }

    public static boolean canAdvanceNormallyToNextInstruction(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            case RETURN: //return instructions can't advance to any instruction, as they're at the end of a control flow chain
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ATHROW: //ATHROW instructions can't advance to any instruction, as they're at the end of a control flow chain
            case GOTO: //unlike other jump instructions, GOTO can only advance to the label
            case TABLESWITCH: //TABLESWITCH and LOOKUPSWITCH can advance to any of the target labels or to the default label
            case LOOKUPSWITCH:
                return false;
            default:
                return true;
        }
    }

    public static boolean equals(AbstractInsnNode insn0, AbstractInsnNode insn1) {
        if (insn0 == insn1) {
            return true;
        }

        int opcode = insn0.getOpcode();
        if (opcode != insn1.getOpcode() || insn0.getClass() != insn1.getClass()) {
            return false;
        }

        //...we could compare annotations here?

        //if we got this far, both instructions have the same opcode and are of the same type

        switch (insn0.getType()) {
            case AbstractInsnNode.INSN:
                return true;
            case AbstractInsnNode.INT_INSN:
                return ((IntInsnNode) insn0).operand == ((IntInsnNode) insn1).operand;
            case AbstractInsnNode.VAR_INSN:
                return ((VarInsnNode) insn0).var == ((VarInsnNode) insn1).var;
            case AbstractInsnNode.TYPE_INSN:
                return ((TypeInsnNode) insn0).desc.equals(((TypeInsnNode) insn1).desc);
            case AbstractInsnNode.FIELD_INSN: {
                FieldInsnNode fieldInsn0 = (FieldInsnNode) insn0;
                FieldInsnNode fieldInsn1 = (FieldInsnNode) insn1;
                return fieldInsn0.owner.equals(fieldInsn1.owner) && fieldInsn0.name.equals(fieldInsn1.name) && fieldInsn0.desc.equals(fieldInsn1.desc);
            }
            case AbstractInsnNode.METHOD_INSN: {
                MethodInsnNode methodInsn0 = (MethodInsnNode) insn0;
                MethodInsnNode methodInsn1 = (MethodInsnNode) insn1;
                return methodInsn0.owner.equals(methodInsn1.owner) && methodInsn0.name.equals(methodInsn1.name) && methodInsn0.desc.equals(methodInsn1.desc);
            }
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
                InvokeDynamicInsnNode invokeDynamicInsn0 = (InvokeDynamicInsnNode) insn0;
                InvokeDynamicInsnNode invokeDynamicInsn1 = (InvokeDynamicInsnNode) insn1;
                Object[] args0 = invokeDynamicInsn0.bsmArgs;
                Object[] args1 = invokeDynamicInsn1.bsmArgs;
                if (!invokeDynamicInsn0.name.equals(invokeDynamicInsn1.name) || !invokeDynamicInsn0.desc.equals(invokeDynamicInsn1.desc)
                    || !equals(invokeDynamicInsn0.bsm, invokeDynamicInsn1.bsm) || args0.length != args1.length) {
                    return false;
                }
                for (int i = 0; i < args0.length; i++) {
                    if (!args0[i].equals(args1[i])) {
                        return false;
                    }
                }
                return true;
            }
            case AbstractInsnNode.JUMP_INSN:
                return equals(((JumpInsnNode) insn0).label, ((JumpInsnNode) insn1).label);
            case AbstractInsnNode.LABEL: //TODO: how should we compare labels?
                return ((LabelNode) insn0).equals((LabelNode) insn1);
            case AbstractInsnNode.LDC_INSN:
                return ((LdcInsnNode) insn0).cst.equals(((LdcInsnNode) insn1).cst);
            case AbstractInsnNode.IINC_INSN: {
                IincInsnNode iincInsn0 = (IincInsnNode) insn0;
                IincInsnNode iincInsn1 = (IincInsnNode) insn1;
                return iincInsn0.var == iincInsn1.var && iincInsn0.incr == iincInsn1.incr;
            }
            case AbstractInsnNode.TABLESWITCH_INSN: {
                TableSwitchInsnNode tableSwitchInsn0 = (TableSwitchInsnNode) insn0;
                TableSwitchInsnNode tableSwitchInsn1 = (TableSwitchInsnNode) insn1;
                if (tableSwitchInsn0.min != tableSwitchInsn1.min || tableSwitchInsn0.max != tableSwitchInsn1.max || !equals(tableSwitchInsn0.dflt, tableSwitchInsn1.dflt)) {
                    return false;
                }
                assert tableSwitchInsn0.labels.size() == tableSwitchInsn1.labels.size(); //the min/max values are the same, so the number of labels must be identical as well
                for (Iterator<LabelNode> itr0 = tableSwitchInsn0.labels.iterator(), itr1 = tableSwitchInsn1.labels.iterator(); itr0.hasNext(); ) {
                    if (!equals(itr0.next(), itr1.next())) {
                        return false;
                    }
                }
                return true;
            }
            case AbstractInsnNode.LOOKUPSWITCH_INSN: {
                LookupSwitchInsnNode lookupSwitchInsn0 = (LookupSwitchInsnNode) insn0;
                LookupSwitchInsnNode lookupSwitchInsn1 = (LookupSwitchInsnNode) insn1;
                assert lookupSwitchInsn0.keys.size() == lookupSwitchInsn0.labels.size() && lookupSwitchInsn1.keys.size() == lookupSwitchInsn1.labels.size();
                if (!equals(lookupSwitchInsn0.dflt, lookupSwitchInsn1.dflt) || !lookupSwitchInsn0.keys.equals(lookupSwitchInsn1.keys)) {
                    return false;
                }
                //the keys lists are equal, so the number of keys/labels must be identical
                for (Iterator<LabelNode> itr0 = lookupSwitchInsn0.labels.iterator(), itr1 = lookupSwitchInsn1.labels.iterator(); itr0.hasNext(); ) {
                    if (!equals(itr0.next(), itr1.next())) {
                        return false;
                    }
                }
                return true;
            }
            case AbstractInsnNode.MULTIANEWARRAY_INSN: {
                MultiANewArrayInsnNode multiANewArrayInsn0 = (MultiANewArrayInsnNode) insn0;
                MultiANewArrayInsnNode multiANewArrayInsn1 = (MultiANewArrayInsnNode) insn1;
                return multiANewArrayInsn0.dims == multiANewArrayInsn1.dims && multiANewArrayInsn0.desc.equals(multiANewArrayInsn1.desc);
            }
            case AbstractInsnNode.FRAME: {
                FrameNode frame0 = (FrameNode) insn0;
                FrameNode frame1 = (FrameNode) insn1;
                return frame0.type == frame1.type && equalsForFrame(frame0.local, frame1.local) && equalsForFrame(frame0.stack, frame1.stack);
            }
            case AbstractInsnNode.LINE: {
                LineNumberNode lineNumber0 = (LineNumberNode) insn0;
                LineNumberNode lineNumber1 = (LineNumberNode) insn1;
                return lineNumber0.line == lineNumber1.line && equals(lineNumber0.start, lineNumber1.start);
            }
        }
        throw new IllegalArgumentException(String.valueOf(insn0.getType()));
    }

    private static boolean equals(Handle handle0, Handle handle1) {
        return handle0.getTag() == handle1.getTag() && handle0.isInterface() == handle1.isInterface()
               && handle0.getOwner().equals(handle1.getOwner()) && handle0.getName().equals(handle1.getName()) && handle0.getDesc().equals(handle1.getDesc());
    }

    private static boolean equalsForFrame(List<Object> list0, List<Object> list1) {
        if (list0 == list1) { //likely case: both are null
            return true;
        } else if (list0 == null || list1 == null //only one is non-null
                   || list0.size() != list1.size()) { //size mismatch
            return false;
        }

        for (Iterator<Object> itr0 = list0.iterator(), itr1 = list1.iterator(); itr0.hasNext(); ) {
            Object v0 = itr0.next();
            Object v1 = itr1.next();
            if (v0 instanceof Integer) {
                if (v0 != v1) { //this is one of the Integer instances from Opcodes, and must be compared by reference
                    return false;
                }
            } else if (v0 instanceof String) {
                    if (!v0.equals(v1)) {
                        return false;
                    }
            } else if (v0 instanceof LabelNode) {
                if (!(v1 instanceof LabelNode) || !equals((LabelNode) v0, (LabelNode) v1)) {
                    return false;
                }
            } else {
                throw new IllegalArgumentException();
            }
        }
        return true;
    }

    public static String toString(AbstractInsnNode insn) {
        if (insn == null) {
            return "null";
        }

        int opcode = insn.getOpcode();
        switch (insn.getType()) {
            case AbstractInsnNode.INSN:
                return Printer.OPCODES[opcode];
            case AbstractInsnNode.INT_INSN:
                return Printer.OPCODES[opcode] + ' ' + ((IntInsnNode) insn).operand;
            case AbstractInsnNode.VAR_INSN:
                return Printer.OPCODES[opcode] + ' ' + ((VarInsnNode) insn).var;
            case AbstractInsnNode.TYPE_INSN:
                return Printer.OPCODES[opcode] + ' ' + ((TypeInsnNode) insn).desc;
            case AbstractInsnNode.FIELD_INSN: {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                return Printer.OPCODES[opcode] + ' ' + fieldInsn.owner + '.' + fieldInsn.name + " : " + fieldInsn.desc;
            }
            case AbstractInsnNode.METHOD_INSN: {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                return Printer.OPCODES[opcode] + ' ' + methodInsn.owner + '.' + methodInsn.name + methodInsn.desc;
            }
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
                InvokeDynamicInsnNode invokeDynamicInsn = (InvokeDynamicInsnNode) insn;
                StringBuilder builder = new StringBuilder();
                builder.append(Printer.OPCODES[opcode]).append(' ').append(invokeDynamicInsn.name).append(invokeDynamicInsn.desc).append(" [\n");
                append(builder, invokeDynamicInsn.bsm);
                if (invokeDynamicInsn.bsmArgs.length != 0) {
                    builder.append("    // arguments:\n");
                    for (Object arg : invokeDynamicInsn.bsmArgs) {
                        if (arg instanceof Handle) {
                            append(builder, (Handle) arg);
                        } else if (arg instanceof String) {
                            builder.append("    \n").append(arg).append('\"');
                        } else {
                            builder.append("    ").append(arg);
                        }
                        builder.append(",\n");
                    }
                    assert builder.charAt(builder.length() - 2) == ',' : builder.charAt(builder.length() - 2);
                    builder.setCharAt(builder.length() - 2, '\n');
                    builder.setCharAt(builder.length() - 1, ']');
                } else {
                    builder.append(']');
                }
                return builder.toString();
            }
            case AbstractInsnNode.JUMP_INSN:
                return Printer.OPCODES[opcode] + ' ' + ((JumpInsnNode) insn).label.getLabel();
            case AbstractInsnNode.LABEL:
                return "LABEL " + ((LabelNode) insn).getLabel();
            case AbstractInsnNode.LDC_INSN: {
                Object cst = ((LdcInsnNode) insn).cst;
                return cst instanceof String
                        ? Printer.OPCODES[opcode] + " \"" + cst + '\"'
                        : Printer.OPCODES[opcode] + ' ' + cst;
            }
            case AbstractInsnNode.IINC_INSN: {
                IincInsnNode iincInsn = (IincInsnNode) insn;
                return Printer.OPCODES[opcode] + ' ' + iincInsn.var + ' ' + iincInsn.incr;
            }
            case AbstractInsnNode.TABLESWITCH_INSN:
            case AbstractInsnNode.LOOKUPSWITCH_INSN: {
                LabelNode dflt;
                List<Integer> keys;
                List<LabelNode> labels;
                if (insn instanceof TableSwitchInsnNode) {
                    TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) insn;
                    dflt = tableSwitchInsn.dflt;
                    keys = IntStream.rangeClosed(tableSwitchInsn.min, tableSwitchInsn.max).boxed().collect(Collectors.toList());
                    labels = tableSwitchInsn.labels;
                } else {
                    LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode) insn;
                    dflt = lookupSwitchInsn.dflt;
                    keys = lookupSwitchInsn.keys;
                    labels = lookupSwitchInsn.labels;
                }
                StringBuilder builder = new StringBuilder();
                builder.append(Printer.OPCODES[opcode]);
                for (int i = 0; i < keys.size(); i++) {
                    builder.append("\n  ").append(keys.get(i)).append(": ").append(labels.get(i).getLabel());
                }
                builder.append("\n  default: ").append(dflt.getLabel());
                return builder.toString();
            }
            case AbstractInsnNode.MULTIANEWARRAY_INSN: {
                MultiANewArrayInsnNode multiANewArrayInsn = (MultiANewArrayInsnNode) insn;
                return Printer.OPCODES[opcode] + ' ' + multiANewArrayInsn.desc + ' ' + multiANewArrayInsn.dims;
            }
            case AbstractInsnNode.FRAME: {
                FrameNode frameNode = (FrameNode) insn;
                StringBuilder builder = new StringBuilder();
                builder.append("FRAME ");
                switch (frameNode.type) {
                    case F_NEW:
                    case F_FULL:
                        builder.append("FULL [");
                        appendFrameTypes(builder, frameNode.local);
                        builder.append("] [");
                        appendFrameTypes(builder, frameNode.stack);
                        builder.append(']');
                        break;
                    case F_APPEND:
                        builder.append("APPEND [");
                        appendFrameTypes(builder, frameNode.local);
                        builder.append(']');
                        break;
                    case F_CHOP:
                        builder.append("CHOP ").append(frameNode.local.size());
                        break;
                    case F_SAME:
                        builder.append("SAME");
                        break;
                    case F_SAME1:
                        builder.append("SAME1 ");
                        assert frameNode.stack.size() == 1 : frameNode.stack;
                        appendFrameTypes(builder, frameNode.stack);
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
                return builder.toString();
            }
            case AbstractInsnNode.LINE: {
                LineNumberNode lineNumberNode = (LineNumberNode) insn;
                return "LINENUMBER " + lineNumberNode.line + ' ' + lineNumberNode.start.getLabel();
            }
        }
        return insn.toString();
    }

    private static void append(StringBuilder builder, Handle handle) {
        builder.append("    // handle kind 0x").append(Integer.toHexString(handle.getTag())).append(" : ").append(Printer.HANDLE_TAG[handle.getTag()])
                .append("\n    ").append(handle.getOwner()).append('.').append(handle.getName());
        switch (handle.getTag()) {
            case H_GETFIELD:
            case H_GETSTATIC:
            case H_PUTFIELD:
            case H_PUTSTATIC:
                builder.append(" : ");
        }
        builder.append(handle.getDesc());
        if (handle.isInterface()) {
            builder.append(" <interface>");
        }
    }

    private static void appendFrameTypes(StringBuilder builder, List<Object> elements) {
        boolean first = true;
        for (Object element : elements) {
            if (!first) {
                builder.append(' ');
            }
            first = false;

            if (element instanceof String) {
                builder.append(element);
            } else if (element instanceof Integer) {
                builder.append("TIFDJNU".charAt((int) element));
            } else {
                builder.append((LabelNode) element);
            }
        }
    }

    public static String stripStdlibPackageFromInternalName(String internalName) {
        return internalName.startsWith("java/") ? stripPackageFromInternalName(internalName) : internalName;
    }

    public static String stripPackageFromInternalName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    public static String methodDescriptorToPrettyString(String desc, boolean stripStdlibPackages, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder(desc.length());
        builder.append('(');
        for (Type argumentType : Type.getArgumentTypes(desc)) {
            if (builder.length() != 1) {
                builder.append(", ");
            }
            String className = argumentType.getClassName();
            if (stripStdlibPackages && className.startsWith("java.")) {
                builder.append(className, className.lastIndexOf('.') + 1, className.length());
            } else {
                builder.append(className);
            }
        }
        builder.append(')');

        if (includeReturnType) {
            String className = Type.getReturnType(desc).getClassName();
            if (stripStdlibPackages && className.startsWith("java.")) {
                builder.append(className, className.lastIndexOf('.') + 1, className.length());
            } else {
                builder.append(className);
            }
        }

        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T extends AbstractInsnNode> T cloneInsn(T srcInsn) {
        return (T) srcInsn.clone(IdentityLabelMap.INSTANCE);
    }

    private static final class IdentityLabelMap extends AbstractMap<LabelNode, LabelNode> {
        public static final IdentityLabelMap INSTANCE = new IdentityLabelMap();

        @Override
        public Set<Entry<LabelNode, LabelNode>> entrySet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LabelNode get(Object key) {
            return (LabelNode) key;
        }
    }

    public static MethodNode cloneMethod(MethodNode srcMethod) {
        MethodNode copiedMethod = new MethodNode(ASM5, srcMethod.access, srcMethod.name, srcMethod.desc, srcMethod.signature, srcMethod.exceptions.toArray(new String[0]));
        copiedMethod.maxStack = srcMethod.maxStack;
        copiedMethod.maxLocals = srcMethod.maxLocals;

        //run the source method through a visitor which will duplicate all mutable objects
        Map<Label, Label> labelMappings = new IdentityHashMap<>();
        srcMethod.accept(new MethodVisitor(ASM5, copiedMethod) {
            @Override
            public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                super.visitFrame(type, nLocal, local == null ? null : this.getLabels(local), nStack, stack == null ? null : this.getLabels(stack));
            }

            private Handle duplicateHandle(Handle handle) {
                return new Handle(handle.getTag(), handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                bsmArgs = bsmArgs.clone();
                for (int i = 0; i < bsmArgs.length; i++) {
                    if (bsmArgs[i] instanceof Handle) {
                        bsmArgs[i] = this.duplicateHandle((Handle) bsmArgs[i]);
                    }
                }
                super.visitInvokeDynamicInsn(name, desc, this.duplicateHandle(bsm), bsmArgs);
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                super.visitJumpInsn(opcode, this.getLabel(label));
            }

            @Override
            public void visitLabel(Label label) {
                super.visitLabel(this.getLabel(label));
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                super.visitTableSwitchInsn(min, max, this.getLabel(dflt), this.getLabels(labels));
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                super.visitLookupSwitchInsn(this.getLabel(dflt), keys.clone(), this.getLabels(labels));
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                super.visitTryCatchBlock(this.getLabel(start), this.getLabel(end), this.getLabel(handler), type);
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                super.visitLocalVariable(name, desc, signature, this.getLabel(start), this.getLabel(end), index);
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
                return super.visitLocalVariableAnnotation(typeRef, typePath, this.getLabels(start), this.getLabels(end), index.clone(), desc, visible);
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                super.visitLineNumber(line, this.getLabel(start));
            }

            private Label getLabel(Label l) {
                Label mapped = labelMappings.get(l);
                if (mapped == null) {
                    labelMappings.put(l, mapped = new Label());
                }
                return mapped;
            }

            private Label[] getLabels(final Label[] l) {
                Label[] nodes = new Label[l.length];
                for (int i = 0; i < l.length; ++i) {
                    nodes[i] = this.getLabel(l[i]);
                }
                return nodes;
            }

            private Object[] getLabels(final Object[] objs) {
                Object[] nodes = new Object[objs.length];
                for (int i = 0; i < objs.length; ++i) {
                    Object o = objs[i];
                    if (o instanceof Label) {
                        o = this.getLabel((Label) o);
                    }
                    nodes[i] = o;
                }
                return nodes;
            }
        });

        return copiedMethod;
    }

    public static LabelNode findMethodStartLabel(MethodNode methodNode) {
        return (LabelNode) methodNode.instructions.getFirst();
    }

    public static LabelNode findMethodEndLabel(MethodNode methodNode) {
        return (LabelNode) methodNode.instructions.getLast();
    }

    public static InsnList makeInsnList() {
        return new InsnList();
    }

    public static InsnList makeInsnList(AbstractInsnNode insn) {
        InsnList seq = new InsnList();
        seq.add(insn);
        return seq;
    }

    public static InsnList makeInsnList(AbstractInsnNode... insns) {
        InsnList seq = new InsnList();
        for (AbstractInsnNode insn : insns) {
            seq.add(insn);
        }
        return seq;
    }

    public static <I extends AbstractInsnNode> Set<I> makeInsnSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public static void addFirst(InsnList list, AbstractInsnNode... insns) {
        for (int i = insns.length - 1; i >= 0; i--) {
            list.insert(insns[i]);
        }
    }

    public static void addFirst(InsnList list, List<AbstractInsnNode> insns) {
        for (int i = insns.size() - 1; i >= 0; i--) {
            list.insert(insns.get(i));
        }
    }

    public static void addLast(InsnList list, AbstractInsnNode... insns) {
        for (AbstractInsnNode insn : insns) {
            list.add(insn);
        }
    }

    public static void addLast(InsnList list, List<AbstractInsnNode> insns) {
        for (AbstractInsnNode insn : insns) {
            list.add(insn);
        }
    }

    public static void insertBefore(AbstractInsnNode location, InsnList list, AbstractInsnNode... insns) {
        for (AbstractInsnNode insn : insns) {
            list.insertBefore(location, insn);
        }
    }

    public static void insertBefore(AbstractInsnNode location, InsnList list, List<AbstractInsnNode> insns) {
        for (AbstractInsnNode insn : insns) {
            list.insertBefore(location, insn);
        }
    }

    public static void insertAfter(AbstractInsnNode location, InsnList list, AbstractInsnNode... insns) {
        for (int i = insns.length - 1; i >= 0; i--) {
            list.insert(location, insns[i]);
        }
    }

    public static void insertAfter(AbstractInsnNode location, InsnList list, List<AbstractInsnNode> insns) {
        for (int i = insns.size() - 1; i >= 0; i--) {
            list.insert(location, insns.get(i));
        }
    }

    public static void replace(AbstractInsnNode location, InsnList list, AbstractInsnNode... insns) {
        if (insns.length == 0) {
            list.remove(location);
        } else {
            for (int i = insns.length - 1; i > 0; i--) {
                list.insert(location, insns[i]);
            }
            list.set(location, insns[0]);
        }
    }

    public static void replaceAndClear(AbstractInsnNode location, InsnList list, InsnList insns) {
        list.insert(location, insns);
        list.remove(location);
    }

    public static void removeAllAndClear(InsnList list, Collection<AbstractInsnNode> insns) {
        for (AbstractInsnNode insn : insns) {
            list.remove(insn);
        }
        insns.clear();
    }

    public static int findUnusedLvtSlot(MethodNode methodNode, Type type, boolean markUsed) {
        BitSet usedEntries = new BitSet();

        //mark all method arguments as used
        usedEntries.set(0, (Type.getArgumentsAndReturnSizes(methodNode.desc) >> 2) + (isStatic(methodNode) ? 0 : 1));

        //scan instructions for references to the local variable table
        for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
            AbstractInsnNode insn = itr.next();
            if (insn instanceof VarInsnNode) {
                int var = ((VarInsnNode) insn).var;
                switch (insn.getOpcode()) {
                    default:
                        usedEntries.set(var);
                        break;
                    case LLOAD:
                    case LSTORE:
                    case DLOAD:
                    case DSTORE:
                        usedEntries.set(var, var + 2);
                        break;
                }
            } else if (insn instanceof IincInsnNode) {
                usedEntries.set(((IincInsnNode) insn).var);
            }
        }

        //scan the local variable entries
        if (methodNode.localVariables != null) {
            for (LocalVariableNode localVariable : methodNode.localVariables) {
                usedEntries.set(localVariable.index, localVariable.index + Type.getType(localVariable.desc).getSize());
            }
        }

        int i = usedEntries.nextClearBit(0);
        if (type.getSize() == 2) {
            while (usedEntries.get(i + 1)) { //we need two empty bits in a row
                i = usedEntries.nextClearBit(i + 2);
            }
        }

        if (markUsed) {
            methodNode.maxLocals = Math.max(methodNode.maxLocals, i + type.getSize());
        }

        return i;
    }

    public static List<ParameterNode> getParameters(MethodNode methodNode) {
        if (methodNode.parameters == null) {
            methodNode.parameters = new ArrayList<>();
        }
        return methodNode.parameters;
    }

    public static List<AnnotationNode> getVisibleAnnotations(MethodNode methodNode) {
        if (methodNode.visibleAnnotations == null) {
            methodNode.visibleAnnotations = new ArrayList<>();
        }
        return methodNode.visibleAnnotations;
    }

    public static List<AnnotationNode> getInvisibleAnnotations(MethodNode methodNode) {
        if (methodNode.invisibleAnnotations == null) {
            methodNode.invisibleAnnotations = new ArrayList<>();
        }
        return methodNode.invisibleAnnotations;
    }

    public static List<TypeAnnotationNode> getVisibleTypeAnnotations(MethodNode methodNode) {
        if (methodNode.visibleTypeAnnotations == null) {
            methodNode.visibleTypeAnnotations = new ArrayList<>();
        }
        return methodNode.visibleTypeAnnotations;
    }

    public static List<TypeAnnotationNode> getInvisibleTypeAnnotations(MethodNode methodNode) {
        if (methodNode.invisibleTypeAnnotations == null) {
            methodNode.invisibleTypeAnnotations = new ArrayList<>();
        }
        return methodNode.invisibleTypeAnnotations;
    }

    public static List<Attribute> getAttrs(MethodNode methodNode) {
        if (methodNode.attrs == null) {
            methodNode.attrs = new ArrayList<>();
        }
        return methodNode.attrs;
    }

    /*public static List<AnnotationNode>[] getVisibleParameterAnnotations(MethodNode methodNode) {
        if (methodNode.visibleParameterAnnotations == null) {
            //noinspection unchecked
            methodNode.visibleParameterAnnotations = new List[0];
        }
        return methodNode.visibleParameterAnnotations;
    }

    public static List<AnnotationNode>[] getInvisibleParameterAnnotations(MethodNode methodNode) {
        if (methodNode.invisibleParameterAnnotations == null) {
            //noinspection unchecked
            methodNode.invisibleParameterAnnotations = new List[0];
        }
        return methodNode.invisibleParameterAnnotations;
    }*/

    public static List<TryCatchBlockNode> getTryCatchBlocks(MethodNode methodNode) {
        if (methodNode.tryCatchBlocks == null) {
            methodNode.tryCatchBlocks = new ArrayList<>();
        }
        return methodNode.tryCatchBlocks;
    }

    public static List<LocalVariableNode> getLocalVariables(MethodNode methodNode) {
        if (methodNode.localVariables == null) {
            methodNode.localVariables = new ArrayList<>();
        }
        return methodNode.localVariables;
    }

    public static List<LocalVariableAnnotationNode> getVisibleLocalVariableAnnotations(MethodNode methodNode) {
        if (methodNode.visibleLocalVariableAnnotations == null) {
            methodNode.visibleLocalVariableAnnotations = new ArrayList<>();
        }
        return methodNode.visibleLocalVariableAnnotations;
    }

    public static List<LocalVariableAnnotationNode> getInvisibleLocalVariableAnnotations(MethodNode methodNode) {
        if (methodNode.invisibleLocalVariableAnnotations == null) {
            methodNode.invisibleLocalVariableAnnotations = new ArrayList<>();
        }
        return methodNode.invisibleLocalVariableAnnotations;
    }

    public static void offsetLvtIndicesGreaterThan(MethodNode methodNode, int threshold, int offset) {
        if (offset == 0) {
            return;
        }

        //TODO: compute maxLocals properly?
        int maxLocal = -1;

        for (AbstractInsnNode currentInsn = methodNode.instructions.getFirst(); currentInsn != null; currentInsn = currentInsn.getNext()) {
            if (currentInsn instanceof VarInsnNode && ((VarInsnNode) currentInsn).var > threshold) {
                int d = currentInsn.getOpcode() == LLOAD || currentInsn.getOpcode() == DLOAD ? 1 : 0;
                maxLocal = Math.max(maxLocal, (((VarInsnNode) currentInsn).var += offset) + d);
            } else if (currentInsn instanceof IincInsnNode && ((IincInsnNode) currentInsn).var > threshold) {
                maxLocal = Math.max(maxLocal, ((IincInsnNode) currentInsn).var += offset);
            }
        }

        //offset local variable indices if necessary
        if (methodNode.localVariables != null) {
            for (LocalVariableNode variableNode : methodNode.localVariables) {
                if (variableNode.index > threshold) {
                    variableNode.index += offset;
                    maxLocal = Math.max(maxLocal, variableNode.index + Type.getType(variableNode.desc).getSize() - 1);
                }
            }
        }

        if (maxLocal >= 0) {
            methodNode.maxLocals = maxLocal + 1;
        }

        //offset local variable annotation indices if necessary
        for (List<LocalVariableAnnotationNode> localVariableAnnotations : Arrays.asList(methodNode.visibleLocalVariableAnnotations, methodNode.invisibleLocalVariableAnnotations)) {
            if (localVariableAnnotations != null) {
                for (LocalVariableAnnotationNode variableAnnotationNode : localVariableAnnotations) {
                    for (ListIterator<Integer> itr = variableAnnotationNode.index.listIterator(); itr.hasNext(); ) {
                        int value = itr.next();
                        if (value > threshold) {
                            itr.set(value + offset);
                        }
                    }
                }
            }
        }
    }

    public static void insertMethodArgumentAtLvtIndex(MethodNode methodNode, int newArgumentLvtIndex, String newArgumentName, Type newArgumentType, int newArgumentAccess) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        for (int parameter = 0, currentLvtIndex = isStatic(methodNode) ? 0 : 1; parameter <= argumentTypes.length; parameter++) {
            if (currentLvtIndex != newArgumentLvtIndex) {
                if (parameter < argumentTypes.length) {
                    currentLvtIndex += argumentTypes[parameter].getSize();
                }
                continue;
            }

            //we found an existing argument to insert the new argument before!

            //shift all local variable indices up by the number of LVT entries the new argument will occupy
            offsetLvtIndicesGreaterThan(methodNode, newArgumentLvtIndex - 1, newArgumentType.getSize());

            //add the new argument type to the method descriptor
            methodNode.desc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), COWArrayUtils.insert(argumentTypes, parameter, newArgumentType));

            //add a new local variable entry
            getLocalVariables(methodNode).add(new LocalVariableNode(newArgumentName, newArgumentType.getDescriptor(), null,
                    findMethodStartLabel(methodNode), findMethodEndLabel(methodNode), newArgumentLvtIndex));

            //insert a new parameter entry
            if (methodNode.parameters != null) {
                methodNode.parameters.add(parameter, new ParameterNode(newArgumentName, newArgumentAccess));
            }

            //insert null element into visibleParameterAnnotations array
            if (methodNode.visibleParameterAnnotations != null) {
                methodNode.visibleParameterAnnotations = COWArrayUtils.insert(methodNode.visibleParameterAnnotations, parameter, (List<AnnotationNode>) null);
            }

            //insert null element into visibleParameterAnnotations array
            if (methodNode.invisibleParameterAnnotations != null) {
                methodNode.invisibleParameterAnnotations = COWArrayUtils.insert(methodNode.invisibleParameterAnnotations, parameter, (List<AnnotationNode>) null);
            }
            return;
        }

        throw new IllegalStateException("couldn't find any arguments at LVT index " + newArgumentLvtIndex + " in method " + methodNode.name + methodNode.desc);
    }

    public static void removeMethodArgumentByLvtIndex(MethodNode methodNode, int oldArgumentLvtIndex) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        for (int parameter = 0, currentLvtIndex = isStatic(methodNode) ? 0 : 1; parameter <= argumentTypes.length; parameter++) {
            if (currentLvtIndex != oldArgumentLvtIndex) {
                if (parameter < argumentTypes.length) {
                    currentLvtIndex += argumentTypes[parameter].getSize();
                }
                continue;
            }

            //we found an existing argument to insert the new argument before!

            //remove the old argument type from the method descriptor
            methodNode.desc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), COWArrayUtils.remove(argumentTypes, parameter));

            //remove the corresponding local variable entry
            if (methodNode.localVariables != null) {
                for (Iterator<LocalVariableNode> itr = methodNode.localVariables.iterator(); itr.hasNext(); ) {
                    LocalVariableNode localVariable = itr.next();
                    if (localVariable.index == oldArgumentLvtIndex) {
                        Preconditions.checkState(argumentTypes[parameter].getDescriptor().equals(localVariable.desc),
                                "local variable at %d has descriptor %s (expected %s)", oldArgumentLvtIndex, argumentTypes[parameter], localVariable.desc);
                        itr.remove();
                    }
                }
            }

            //remove the corresponding parameter entry
            if (methodNode.parameters != null) {
                methodNode.parameters.remove(parameter);
            }

            //remove the corresponding element from the visibleParameterAnnotations array
            if (methodNode.visibleParameterAnnotations != null) {
                methodNode.visibleParameterAnnotations = COWArrayUtils.remove(methodNode.visibleParameterAnnotations, parameter);
            }

            //remove the corresponding element from the visibleParameterAnnotations array
            if (methodNode.invisibleParameterAnnotations != null) {
                methodNode.invisibleParameterAnnotations = COWArrayUtils.remove(methodNode.invisibleParameterAnnotations, parameter);
            }

            //shift all local variable indices up by the number of LVT entries the new argument will occupy
            offsetLvtIndicesGreaterThan(methodNode, oldArgumentLvtIndex - 1, -argumentTypes[parameter].getSize());
            return;
        }

        throw new IllegalStateException("couldn't find any arguments at LVT index " + oldArgumentLvtIndex + " in method " + methodNode.name + methodNode.desc);
    }

    public static Optional<MethodInsnNode> findSuperCtorInvocationInCtor(ClassNode classNode, MethodNode ctor) {
        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, ctor);
        for (AbstractInsnNode insn = ctor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            MethodInsnNode methodInsn;
            if (insn.getOpcode() == INVOKESPECIAL && classNode.superName.equals((methodInsn = (MethodInsnNode) insn).owner) && "<init>".equals(methodInsn.name)) { //found super constructor invocation
                Frame<SourceValue> sourceFrame = sourceFrames[ctor.instructions.indexOf(insn)];
                AbstractInsnNode sourceInsn = BytecodeHelper.getStackValueFromTop(sourceFrame, BytecodeHelper.getConsumedStackOperandCount(methodInsn, sourceFrame) - 1).insns.iterator().next();
                if (sourceInsn.getOpcode() == ALOAD && ((VarInsnNode) sourceInsn).var == 0) { //the target of the constructor is actually this
                    return Optional.of(methodInsn);
                }
            }
        }
        return Optional.empty();
    }

    public static String mangleSignature(String name) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                builder.append(c);
            } else switch (c) {
                case '_':
                    builder.append("_1");
                    break;
                case ';':
                    builder.append("_2");
                    break;
                case '[':
                    builder.append("_3");
                    break;
                default:
                    builder.append("_0").append(Integer.toHexString(c | 0x10000), 1, 5);
                    break;
            }
        }
        return builder.toString();
    }
}
