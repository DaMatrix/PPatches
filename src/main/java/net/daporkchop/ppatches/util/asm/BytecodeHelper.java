package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.Printer;

import java.util.*;

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

    //
    // <instruction equality checks>
    //

    public static boolean isCHECKCAST(AbstractInsnNode insn, String internalName) {
        return insn.getOpcode() == CHECKCAST && internalName.equals(((TypeInsnNode) insn).desc);
    }

    public static boolean isCHECKCAST(AbstractInsnNode insn, Type type) {
        return isCHECKCAST(insn, type.getInternalName());
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

    public static MethodInsnNode generateBoxingConversion(Type primitiveType) {
        String internalName = boxedInternalName(primitiveType);
        return new MethodInsnNode(INVOKESTATIC, internalName, "valueOf", '(' + primitiveType.getDescriptor() + ")L" + internalName + ';', false);
    }

    public static MethodInsnNode generateUnboxingConversion(Type primitiveType) {
        return new MethodInsnNode(INVOKEVIRTUAL, boxedInternalName(primitiveType), primitiveType.getClassName() + "Value", "()" + primitiveType, false);
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

    @SneakyThrows(AnalyzerException.class)
    public static Frame<SourceValue>[] analyzeSources(String ownerName, MethodNode methodNode) {
        return new Analyzer<>(new SourceInterpreter()).analyze(ownerName, methodNode);
    }

    public static <V extends Value> V getStackValueFromTop(Frame<V> frame, int indexFromTop) {
        int stackSize = frame.getStackSize();
        Preconditions.checkElementIndex(indexFromTop, stackSize, "indexFromTop");
        return frame.getStack(stackSize - 1 - indexFromTop);
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

    public static LabelNode findMethodStartLabel(MethodNode methodNode) {
        return (LabelNode) methodNode.instructions.getFirst();
    }

    public static LabelNode findMethodEndLabel(MethodNode methodNode) {
        return (LabelNode) methodNode.instructions.getLast();
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

    public static int findUnusedLvtSlot(MethodNode methodNode, Type type) {
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
        return i;
    }
}
