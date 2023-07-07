package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.*;
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

    public static AbstractInsnNode dup(Type type) {
        switch (type.getSize()) {
            case 1:
                return new InsnNode(DUP);
            case 2:
                return new InsnNode(DUP2);
        }
        throw new IllegalStateException();
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

    public static MethodInsnNode generateBoxingConversion(Type primitiveType) {
        String internalName = boxedInternalName(primitiveType);
        return new MethodInsnNode(INVOKESTATIC, internalName, "valueOf", '(' + primitiveType.getDescriptor() + ")L" + internalName + ';', false);
    }

    public static MethodInsnNode generateUnboxingConversion(Type primitiveType) {
        return new MethodInsnNode(INVOKEVIRTUAL, boxedInternalName(primitiveType), primitiveType.getClassName() + "Value", "()" + primitiveType, false);
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

    @SneakyThrows(AnalyzerException.class)
    public static Frame<SourceValue>[] analyzeSources(String ownerName, MethodNode methodNode) {
        return new Analyzer<>(new SourceInterpreter()).analyze(ownerName, methodNode);
    }

    public static <V extends Value> V getStackValueFromTop(Frame<V> frame, int indexFromTop) {
        int stackSize = frame.getStackSize();
        Preconditions.checkElementIndex(indexFromTop, stackSize, "indexFromTop");
        return frame.getStack(stackSize - 1 - indexFromTop);
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

        for (AbstractInsnNode currentInsn = methodNode.instructions.getFirst(); currentInsn != null; currentInsn = currentInsn.getNext()) {
            if (currentInsn instanceof VarInsnNode && ((VarInsnNode) currentInsn).var > threshold) {
                ((VarInsnNode) currentInsn).var += offset;
            } else if (currentInsn instanceof IincInsnNode && ((IincInsnNode) currentInsn).var > threshold) {
                ((IincInsnNode) currentInsn).var += offset;
            }
        }

        //offset local variable indices if necessary
        if (methodNode.localVariables != null) {
            for (LocalVariableNode variableNode : methodNode.localVariables) {
                if (variableNode.index > threshold) {
                    variableNode.index += offset;
                }
            }
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
            Type[] modifiedArgumentTypes = Arrays.copyOf(argumentTypes, argumentTypes.length + 1);
            modifiedArgumentTypes[parameter] = newArgumentType;
            System.arraycopy(argumentTypes, parameter, modifiedArgumentTypes, parameter + 1, argumentTypes.length - parameter);
            methodNode.desc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), modifiedArgumentTypes);

            //add a new local variable entry
            getLocalVariables(methodNode).add(new LocalVariableNode(newArgumentName, newArgumentType.getDescriptor(), null,
                    findMethodStartLabel(methodNode), findMethodEndLabel(methodNode), newArgumentLvtIndex));

            //insert a new parameter entry
            if (methodNode.parameters != null) {
                methodNode.parameters.add(parameter, new ParameterNode(newArgumentName, newArgumentAccess));
            }

            //insert null element into visibleParameterAnnotations array
            if (methodNode.visibleParameterAnnotations != null) {
                List<AnnotationNode>[] resized = Arrays.copyOf(methodNode.visibleParameterAnnotations, methodNode.visibleParameterAnnotations.length + 1);
                System.arraycopy(methodNode.visibleParameterAnnotations, parameter, resized, parameter + 1, methodNode.visibleParameterAnnotations.length - parameter);
                resized[parameter] = null;
                methodNode.visibleParameterAnnotations = resized;
            }

            //insert null element into visibleParameterAnnotations array
            if (methodNode.invisibleParameterAnnotations != null) {
                List<AnnotationNode>[] resized = Arrays.copyOf(methodNode.invisibleParameterAnnotations, methodNode.invisibleParameterAnnotations.length + 1);
                System.arraycopy(methodNode.invisibleParameterAnnotations, parameter, resized, parameter + 1, methodNode.invisibleParameterAnnotations.length - parameter);
                resized[parameter] = null;
                methodNode.invisibleParameterAnnotations = resized;
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
            Type[] modifiedArgumentTypes = Arrays.copyOf(argumentTypes, argumentTypes.length - 1);
            System.arraycopy(argumentTypes, parameter + 1, modifiedArgumentTypes, parameter, modifiedArgumentTypes.length - parameter);
            methodNode.desc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), modifiedArgumentTypes);

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
                List<AnnotationNode>[] resized = Arrays.copyOf(methodNode.visibleParameterAnnotations, methodNode.visibleParameterAnnotations.length - 1);
                System.arraycopy(methodNode.visibleParameterAnnotations, parameter + 1, resized, parameter, resized.length - parameter);
                methodNode.visibleParameterAnnotations = resized;
            }

            //remove the corresponding element from the visibleParameterAnnotations array
            if (methodNode.invisibleParameterAnnotations != null) {
                List<AnnotationNode>[] resized = Arrays.copyOf(methodNode.invisibleParameterAnnotations, methodNode.invisibleParameterAnnotations.length - 1);
                System.arraycopy(methodNode.invisibleParameterAnnotations, parameter + 1, resized, parameter, resized.length - parameter);
                methodNode.invisibleParameterAnnotations = resized;
            }

            //shift all local variable indices up by the number of LVT entries the new argument will occupy
            offsetLvtIndicesGreaterThan(methodNode, oldArgumentLvtIndex - 1, -argumentTypes[parameter].getSize());
            return;
        }

        throw new IllegalStateException("couldn't find any arguments at LVT index " + oldArgumentLvtIndex + " in method " + methodNode.name + methodNode.desc);
    }
}
