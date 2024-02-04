package net.daporkchop.ppatches.util.asm;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class LambdaFlattener {
    public static Optional<LambdaFlattener> createFromSources(Frame<SourceValue> sources, int indexFromTop, LVTReference.Allocator lvtAllocator) {
        AbstractInsnNode insn = BytecodeHelper.getSingleSourceInsnFromTop(sources, indexFromTop);
        return insn instanceof InvokeDynamicInsnNode && Flattening.isMetafactory(((InvokeDynamicInsnNode) insn).bsm)
                ? Optional.of(new Flattening((InvokeDynamicInsnNode) insn, lvtAllocator))
                : Optional.empty();
    }

    public static LambdaFlattener createFromSources(Frame<SourceValue> sources, int indexFromTop, LVTReference.Allocator lvtAllocator, Handle interfaceMethodHandle) {
        AbstractInsnNode insn = BytecodeHelper.getSingleSourceInsnFromTop(sources, indexFromTop);
        if (insn instanceof InvokeDynamicInsnNode && Flattening.isMetafactory(((InvokeDynamicInsnNode) insn).bsm)) {
            return new Flattening((InvokeDynamicInsnNode) insn, lvtAllocator);
        }
        return new NotFlattening(interfaceMethodHandle, lvtAllocator);
    }

    public abstract LambdaFlattener visitCaptureState(InsnList out, List<AbstractInsnNode> removeInsns);

    public abstract void visitPreInvoke(InsnList out);

    public abstract void visitPostLoadInvokeArgument(InsnList out, int argumentIndex);

    public abstract void visitPostInvoke(InsnList out);

    public void visitClassNode(ClassNode classNode) {
        //no-op
    }

    //implements rules as defined by LambdaMetafactory
    protected void adaptType(InsnList out, Type q, Type s) {
        if (q.equals(s)) {
            return;
        }

        if (s.getSort() == Type.VOID) {
            if (q.getSort() != Type.VOID) {
                out.add(BytecodeHelper.pop(q));
            }
            return;
        }

        if (!BytecodeHelper.isReference(q) && !BytecodeHelper.isReference(s)) {
            out.add(BytecodeHelper.tryGenerateWideningConversion(q, s).get());
        } else if (!BytecodeHelper.isReference(q) && BytecodeHelper.isReference(s)) {
            out.add(BytecodeHelper.generateBoxingConversion(q));
            out.add(new TypeInsnNode(CHECKCAST, BytecodeHelper.boxedInternalName(q)));
        } else if (BytecodeHelper.isReference(q) && !BytecodeHelper.isReference(s)) {
            out.add(new TypeInsnNode(CHECKCAST, BytecodeHelper.boxedInternalBaseName(s)));
            out.add(BytecodeHelper.generateUnboxingFromBaseConversion(s));
        } else {
            out.add(new TypeInsnNode(CHECKCAST, s.getInternalName()));
        }
    }

    private static final class Flattening extends LambdaFlattener {
        private static boolean isMetafactory(Handle bsm) {
            return BytecodeHelper.isHandle(bsm, H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory");
        }

        private final InvokeDynamicInsnNode metafactory;
        private final Type[] capturedArgumentTypes;
        private final List<LVTReference> capturedArgumentValues;

        private final Type[] samArgumentTypes;
        private final Type samReturnType;

        private final Type[] instantiatedArgumentTypes;
        private final Type instantiatedReturnType;

        private final Handle implMethod;
        private final Type[] implMethodArgumentTypes;
        private final Type implMethodReturnType;

        private final Type[] invokeArgumentTypes;

        public Flattening(InvokeDynamicInsnNode metafactory, LVTReference.Allocator lvtAllocator) {
            this.metafactory = metafactory;
            this.capturedArgumentTypes = Type.getArgumentTypes(metafactory.desc);

            if (isMetafactory(metafactory.bsm)) {
                this.implMethod = (Handle) metafactory.bsmArgs[1];
                Type implMethodEffectiveArguments = BytecodeHelper.getEffectiveHandleMethodType(this.implMethod);
                this.implMethodArgumentTypes = implMethodEffectiveArguments.getArgumentTypes();
                this.implMethodReturnType = implMethodEffectiveArguments.getReturnType();

                Type samMethodType = (Type) metafactory.bsmArgs[0];
                this.samArgumentTypes = samMethodType.getArgumentTypes();
                this.samReturnType = samMethodType.getReturnType();

                Type instantiatedMethodType = (Type) metafactory.bsmArgs[2];
                this.instantiatedArgumentTypes = instantiatedMethodType.getArgumentTypes();
                this.instantiatedReturnType = instantiatedMethodType.getReturnType();

                this.capturedArgumentValues = new ArrayList<>(this.capturedArgumentTypes.length);
                for (Type capturedArgumentType : this.capturedArgumentTypes) {
                    this.capturedArgumentValues.add(lvtAllocator.allocate(capturedArgumentType));
                }

                this.invokeArgumentTypes = Arrays.copyOfRange(this.implMethodArgumentTypes, this.capturedArgumentTypes.length, this.implMethodArgumentTypes.length);
            } else {
                throw new IllegalArgumentException(BytecodeHelper.toString(metafactory));
            }
        }

        @Override
        public LambdaFlattener visitCaptureState(InsnList out, List<AbstractInsnNode> removeInsns) {
            for (int i = this.capturedArgumentValues.size() - 1; i >= 0; i--) {
                out.add(this.capturedArgumentValues.get(i).makeStore());
            }

            removeInsns.add(this.metafactory);
            return this;
        }

        @Override
        public void visitPreInvoke(InsnList out) {
            out.add(BytecodeHelper.flattenHandlePre(this.implMethod));
            for (LVTReference capturedArgumentValue : this.capturedArgumentValues) {
                out.add(capturedArgumentValue.makeLoad());
            }
        }

        @Override
        public void visitPostLoadInvokeArgument(InsnList out, int argumentIndex) {
            Type ui = this.samArgumentTypes[argumentIndex];
            Type ti = this.instantiatedArgumentTypes[argumentIndex];
            Type ai = this.implMethodArgumentTypes[argumentIndex + this.capturedArgumentTypes.length];
            if (!ui.equals(ti)) { //assume both are reference types, therefore ti must be a subtype of ui
                out.add(new TypeInsnNode(CHECKCAST, ti.getInternalName()));
            }
            this.adaptType(out, ti, ai);
        }

        @Override
        public void visitPostInvoke(InsnList out) {
            out.add(BytecodeHelper.flattenHandlePost(this.implMethod));

            Type ru = this.samReturnType;
            Type rt = this.instantiatedReturnType;
            Type ra = this.implMethodReturnType;
            this.adaptType(out, ra, rt);
        }

        @Override
        public void visitClassNode(ClassNode classNode) {
            if (classNode.name.equals(this.implMethod.getOwner())) {
                BytecodeHelper.findMethod(classNode, this.implMethod.getName(), this.implMethod.getDesc()).get().access &= ~ACC_SYNTHETIC;
            }
        }
    }

    private static final class NotFlattening extends LambdaFlattener {
        private final Handle interfaceMethodHandle;
        private final Type[] invokeArgumentTypes;

        private final LVTReference capturedInterfaceInstanceValue;

        public NotFlattening(Handle interfaceMethodHandle, LVTReference.Allocator lvtAlloc) {
            assert interfaceMethodHandle.getTag() == H_INVOKEINTERFACE;

            this.interfaceMethodHandle = interfaceMethodHandle;
            this.invokeArgumentTypes = Type.getArgumentTypes(interfaceMethodHandle.getDesc());

            this.capturedInterfaceInstanceValue = lvtAlloc.allocate(Type.getObjectType(interfaceMethodHandle.getOwner()));
        }

        @Override
        public LambdaFlattener visitCaptureState(InsnList out, List<AbstractInsnNode> removeInsns) {
            out.add(this.capturedInterfaceInstanceValue.makeStore());
            return this;
        }

        @Override
        public void visitPreInvoke(InsnList out) {
            out.add(BytecodeHelper.flattenHandlePre(this.interfaceMethodHandle));
            out.add(this.capturedInterfaceInstanceValue.makeLoad());
        }

        @Override
        public void visitPostLoadInvokeArgument(InsnList out, int argumentIndex) {
            Type argumentType = this.invokeArgumentTypes[argumentIndex];
            if (BytecodeHelper.isReference(argumentType) && !Type.getType(Object.class).equals(argumentType)) {
                out.add(new TypeInsnNode(CHECKCAST, argumentType.getInternalName()));
            }
        }

        @Override
        public void visitPostInvoke(InsnList out) {
            out.add(BytecodeHelper.flattenHandlePost(this.interfaceMethodHandle));
        }
    }
}
