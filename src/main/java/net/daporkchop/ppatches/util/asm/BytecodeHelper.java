package net.daporkchop.ppatches.util.asm;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Printer;
import scala.tools.nsc.doc.model.ModelFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class BytecodeHelper {
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

    @SneakyThrows(AnalyzerException.class)
    public static Frame<SourceValue>[] analyzeSources(String ownerInternalName, MethodNode methodNode) {
        return new Analyzer<>(new SourceInterpreter()).analyze(ownerInternalName, methodNode);
    }

    public static SourceValue[] findInputSources(String ownerInternalName, MethodNode methodNode, AbstractInsnNode insn) {
        return findInputSources(analyzeSources(ownerInternalName, methodNode), ownerInternalName, methodNode, insn);
    }

    public static SourceValue[] findInputSources(Frame<SourceValue>[] frames, String ownerInternalName, MethodNode methodNode, AbstractInsnNode insn) {
        int consumedStackElements = 0;
        switch (insn.getOpcode()) {
            default:
                throw new UnsupportedOperationException("unsupported instruction type: " + Printer.OPCODES[insn.getOpcode()]);
            case INVOKEINTERFACE:
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
                consumedStackElements = 1;
            case INVOKESTATIC: {
                //we use the number of arguments here, since Frame reports all stack elements as occupying exactly one slot
                consumedStackElements += Type.getMethodType(((MethodInsnNode) insn).desc).getArgumentTypes().length;
                break;
            }
        }

        Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(insn)];

        SourceValue[] inputSources = new SourceValue[consumedStackElements];
        for (int i = 0; i < consumedStackElements; i++) {
            inputSources[i] = frame.getStack(frame.getStackSize() - consumedStackElements + i);
        }
        return inputSources;
    }

    public static Type findEffectiveType(Frame<SourceValue>[] frames, String ownerInternalName, MethodNode methodNode, SourceValue value) {
        return findEffectiveType(frames, ownerInternalName, methodNode, value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Type findEffectiveType(Frame<SourceValue>[] frames, String ownerInternalName, MethodNode methodNode, SourceValue value, Set<AbstractInsnNode> visitedInstructions) {
        assert !value.insns.isEmpty();

        Type type = null;
        for (AbstractInsnNode sourceInsn : value.insns) {
            if (!visitedInstructions.add(sourceInsn)) {
                continue;
            }

            Type currentType = null;
            if (sourceInsn instanceof MethodInsnNode) {
                currentType = Type.getReturnType(((MethodInsnNode) sourceInsn).desc);
            } else if (sourceInsn instanceof FieldInsnNode) {
                currentType = Type.getType(((FieldInsnNode) sourceInsn).desc);
            } else if (sourceInsn instanceof VarInsnNode) {
                int lvtIndex = ((VarInsnNode) sourceInsn).var;

                //TODO: maybe i could use the local variable data for this?

                VAR_SWITCH:
                switch (sourceInsn.getOpcode()) {
                    case ALOAD: {
                        if (methodNode.localVariables != null) { //try to find a matching local variable entry
                            int sourceInsnIndex = methodNode.instructions.indexOf(sourceInsn);
                            for (LocalVariableNode localVariable : methodNode.localVariables) {
                                if (localVariable.index == lvtIndex
                                    && methodNode.instructions.indexOf(localVariable.start) <= sourceInsnIndex
                                    && methodNode.instructions.indexOf(localVariable.end) >= sourceInsnIndex) {
                                    currentType = Type.getType(localVariable.desc);
                                    break VAR_SWITCH;
                                }
                            }
                        }

                        SourceValue lvtValue = frames[methodNode.instructions.indexOf(sourceInsn)].getLocal(lvtIndex);
                        if (lvtValue.insns.isEmpty() && lvtIndex == 0 && (methodNode.access & ACC_STATIC) == 0) { //this is an instance method, so the LVT entry 0 is implicitly this
                            currentType = Type.getObjectType(ownerInternalName);
                        } else {
                            currentType = findEffectiveType(frames, ownerInternalName, methodNode, lvtValue, visitedInstructions);
                        }
                        break;
                    }
                    case ILOAD: //TODO: how will this work with boolean/byte/short/char?
                        currentType = Type.INT_TYPE;
                        break;
                    case LLOAD:
                        currentType = Type.LONG_TYPE;
                        break;
                    case FLOAD:
                        currentType = Type.FLOAT_TYPE;
                        break;
                    case DLOAD:
                        currentType = Type.DOUBLE_TYPE;
                        break;
                    case ASTORE: { //we can get here if a value was previously stored on the stack and was then loaded again
                        currentType = findEffectiveType(frames, ownerInternalName, methodNode, frames[methodNode.instructions.indexOf(sourceInsn)].getLocal(lvtIndex), visitedInstructions);
                        if (currentType == null) {
                            throw new UnsupportedOperationException(Printer.OPCODES[sourceInsn.getOpcode()] + " can't be resolved!");
                        }
                        break;
                    }
                }
            } else if (sourceInsn instanceof TypeInsnNode) {
                String desc = ((TypeInsnNode) sourceInsn).desc;
                switch (sourceInsn.getOpcode()) {
                    case NEW:
                    case CHECKCAST:
                        currentType = Type.getType(desc);
                        break;
                    case INSTANCEOF:
                        currentType = Type.BOOLEAN_TYPE;
                        break;
                    case ANEWARRAY:
                        currentType = Type.getType('[' + desc);
                        break;
                }
            } else if (isConstant(sourceInsn)) {
                Object constantValue = decodeConstant(sourceInsn);
                if (constantValue instanceof Number) {
                    if (constantValue instanceof Integer) {
                        currentType = Type.INT_TYPE;
                    } else if (constantValue instanceof Long) {
                        currentType = Type.LONG_TYPE;
                    } else if (constantValue instanceof Float) {
                        currentType = Type.FLOAT_TYPE;
                    } else if (constantValue instanceof Double) {
                        currentType = Type.DOUBLE_TYPE;
                    }
                } else if (constantValue != null) { //constantValue should be either String or Class
                    currentType = Type.getType(constantValue.getClass());
                } else {
                    currentType = Type.getType(Object.class);
                }
            } else if (sourceInsn.getClass() == InsnNode.class) {
                switch (sourceInsn.getOpcode()) {
                    case I2B:
                        currentType = Type.BYTE_TYPE;
                        break;
                    case I2S:
                        currentType = Type.SHORT_TYPE;
                        break;
                    case I2C:
                        currentType = Type.CHAR_TYPE;
                        break;
                    case L2I:
                    case F2I:
                    case D2I:
                    case ARRAYLENGTH:
                        currentType = Type.INT_TYPE;
                        break;
                    case I2L:
                    case F2L:
                    case D2L:
                        currentType = Type.LONG_TYPE;
                        break;
                    case I2F:
                    case L2F:
                    case D2F:
                        currentType = Type.FLOAT_TYPE;
                        break;
                    case I2D:
                    case L2D:
                    case F2D:
                        currentType = Type.DOUBLE_TYPE;
                        break;
                        //TODO: primitive array types
                }
            }

            if (currentType == null) {
                throw new UnsupportedOperationException("unsupported instruction type: " + Printer.OPCODES[sourceInsn.getOpcode()] + " (" + sourceInsn + ')');
            }

            if (type != null && !type.equals(currentType)) {
                throw new IllegalStateException();
            }
            type = currentType;

            visitedInstructions.remove(sourceInsn);
        }

        return type;
    }

    public static Type[] findEffectiveTypes(Frame<SourceValue>[] frames, String ownerInternalName, MethodNode methodNode, AbstractInsnNode consumerInsn, SourceValue[] values) {
        Type[] effectiveTypes = new Type[values.length];
        int i = 0;

        switch (consumerInsn.getOpcode()) {
            default:
                throw new UnsupportedOperationException("unsupported instruction type: " + Printer.OPCODES[consumerInsn.getOpcode()]);
            case INVOKEINTERFACE:
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
                effectiveTypes[i++] = Type.getObjectType(ownerInternalName);
            case INVOKESTATIC:
                //we use the number of arguments here, since Frame reports all stack elements as occupying exactly one slot
                System.arraycopy(Type.getMethodType(((MethodInsnNode) consumerInsn).desc).getArgumentTypes(), 0, effectiveTypes, i, effectiveTypes.length - i);
                break;
        }

        for (; i < values.length; i++) {
            effectiveTypes[i] = findEffectiveType(frames, ownerInternalName, methodNode, values[i]);
        }
        return effectiveTypes;
    }
}
