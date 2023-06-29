package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.NoSuchElementException;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public abstract class OptionalBytecodeType {
    /**
     * Gets an {@link OptionalBytecodeType} which supports the same states as the contained type, where the value is always considered to be "set".
     */
    public static OptionalBytecodeType alwaysPresent(Type containedType) { //TODO: we should add a variant of this which also supports null values for all types
        Preconditions.checkArgument(BytecodeHelper.isPrimitive(containedType) || BytecodeHelper.isReference(containedType), containedType);
        return new AlwaysPresent(containedType);
    }

    /**
     * Gets an {@link OptionalBytecodeType} which will behave as if the optional is implemented using a single object reference. A value of {@code null} would indicate that the value is unset.
     * <p>
     * This is similar to Java's {@link java.util.Optional}.
     */
    public static OptionalBytecodeType emulateReference_NullIsUnset(Type containedType) {
        Preconditions.checkArgument(BytecodeHelper.isPrimitive(containedType) || BytecodeHelper.isReference(containedType), containedType);
        return new Reference_NullIsUnset(containedType);
    }

    /**
     * Gets an {@link OptionalBytecodeType} which will behave as if the optional is implemented using a single object reference alongside a boolean flag which is {@code true} if and only if the value
     * is set. It is possible to set the value to {@code null}.
     * <p>
     * Primitive types will behave as if they are boxed, allowing them to take on null values as well.
     */
    public static OptionalBytecodeType emulateReferenceWithFlag_NullIsDefault(Type containedType) {
        Preconditions.checkArgument(BytecodeHelper.isPrimitive(containedType) || BytecodeHelper.isReference(containedType), containedType);
        return new NullableReferenceWithFlag(containedType);
    }

    protected final Type containedType;

    /**
     * Gets the reference type for this optional scheme's {@link #containedType() contained type}.
     * <p>
     * If the {@link #containedType() contained type} is a reference type, this is the same as the {@link #containedType() contained type}. Otherwise, if the {@link #containedType() contained type}
     * is a primitive type, its reference type is an {@link Type#OBJECT object type} of the corresponding boxed type.
     */
    public final Type referenceType() {
        return BytecodeHelper.isPrimitive(this.containedType) ? Type.getObjectType(BytecodeHelper.boxedInternalName(this.containedType)) : this.containedType;
    }

    public abstract Type optionalType();

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #containedType() contained type} from the stack and push a single value of the
     * {@link #optionalType() optional type} to the stack, where the pushed value represents an optional whose value is set to the original value.
     * <p>
     * Note that if the {@link #containedType() contained type} is a reference type, the input value is {@code null} and this optional scheme treats {@code null} values as unset (such
     * as {@link #emulateReference_NullIsUnset(Type)}), the resulting optional value may actually be an unset optional. If {@code assertNonNullIfUnsupported} is {@code true}, a Java
     * assertion will be added to ensure that the value is non-null.
     */
    public abstract InsnList makeOptionalFromValue(boolean assertNonNullIfUnsupported);

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #referenceType() reference type} from the stack and push a single value of the
     * {@link #optionalType() optional type} to the stack, where the pushed value represents an optional whose value is set to the original value, or an unset optional
     * if the original value was {@code null}.
     */
    public abstract InsnList makeOptionalFromNullableReference_UnsetIfNull();

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #referenceType() reference type} from the stack and push a single value of the
     * {@link #optionalType() optional type} to the stack, where the pushed value represents an optional whose value is set to the original value.
     *
     * @throws UnsupportedOperationException if this optional scheme treats {@code null} values as unset (such as {@link #emulateReference_NullIsUnset(Type)})
     */
    public abstract InsnList makeOptionalFromNullableReference_PreserveNull();

    /**
     * Generates an instruction which will push a single value of the {@link #optionalType() optional type} onto the stack, where the pushed value represents an optional
     * whose value is unset.
     */
    public abstract AbstractInsnNode loadUnsetValue();

    /**
     * Generates an instruction which will push a single value of the {@link #optionalType() optional type} onto the stack, where the pushed value represents an optional
     * whose value is set to {@code null}.
     *
     * @throws UnsupportedOperationException if this optional scheme treats {@code null} values as unset (such as {@link #emulateReference_NullIsUnset(Type)})
     */
    public abstract AbstractInsnNode loadNullValue();

    protected abstract InsnList branchOnPresence(LabelNode dst, boolean jumpIfPresent);

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #optionalType() optional type} from the stack and jump to the given {@link LabelNode label}
     * if the popped optional's value is set.
     */
    public final InsnList ifOptionalValueSet(LabelNode dst) {
        return this.branchOnPresence(dst, true);
    }

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #optionalType() optional type} from the stack and jump to the given {@link LabelNode label}
     * if the popped optional's value is unset.
     */
    public final InsnList ifOptionalValueUnset(LabelNode dst) {
        return this.branchOnPresence(dst, false);
    }

    protected final InsnList preExtractValue(boolean assertSet) {
        InsnList seq = new InsnList();
        if (assertSet) {
            LabelNode tailLbl = new LabelNode();
            seq.add(InvokeDynamicUtils.makeLoadAssertionStateInsn());
            seq.add(new JumpInsnNode(IFEQ, tailLbl));
            seq.add(BytecodeHelper.dup(this.optionalType()));
            seq.add(this.ifOptionalValueSet(tailLbl));
            seq.add(InvokeDynamicUtils.makeNewException(NoSuchElementException.class, "No value present"));
            seq.add(new InsnNode(ATHROW));
            seq.add(tailLbl);
        }
        return seq;
    }

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #optionalType() optional type} from the stack and push a single value of the
     * {@link #containedType() contained type} onto the stack, where the pushed value represents the value contained by the popped optional.
     * <p>
     * Note that if the popped optional's value is unset, the behavior is undefined. If {@code assertNonNull} is {@code true}, a Java assertion will be added to ensure that
     * the optional's value is set.
     */
    public abstract InsnList extractValueAsContainedType_DefaultIfNull(boolean assertSet);

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #optionalType() optional type} from the stack and push a single value of the
     * {@link #referenceType() reference type} onto the stack, where the pushed value represents the value contained by the popped optional.
     * <p>
     * Note that if the popped optional's value is unset, the behavior is undefined. If {@code assertNonNull} is {@code true}, a Java assertion will be added to ensure that
     * the optional's value is set.
     */
    public abstract InsnList extractValueAsNullableReference(boolean assertSet);

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #optionalType() optional type} from the stack and push a single value of the
     * {@link #containedType() contained type} onto the stack, where the pushed value represents the value contained by the popped optional, or the {@link #containedType() contained type}'s
     * {@link BytecodeHelper#loadConstantDefaultValueInsn(Type) default value} if the optional's value is unset or is set to {@code null}.
     */
    public abstract InsnList extractValueAsContainedType_DefaultIfUnsetOrNull();

    /**
     * Generates a sequence of instructions which will pop a single value of the {@link #optionalType() optional type} from the stack and push a single value of the
     * {@link #referenceType() reference type} onto the stack, where the pushed value represents the value contained by the popped optional, or {@code null} if the
     * optional's value is unset.
     */
    public abstract InsnList extractValueAsNullableReference_NullIfUnset();

    /**
     * @author DaPorkchop_
     * @see #alwaysPresent(Type)
     */
    private static class AlwaysPresent extends OptionalBytecodeType {
        protected AlwaysPresent(Type containedType) {
            super(containedType);
        }

        @Override
        public Type optionalType() {
            return this.containedType;
        }

        @Override
        public InsnList makeOptionalFromValue(boolean assertNonNullIfUnsupported) {
            return BytecodeHelper.makeInsnList();
        }

        @Override
        public InsnList makeOptionalFromNullableReference_UnsetIfNull() {
            return BytecodeHelper.makeInsnList();
        }

        @Override
        public InsnList makeOptionalFromNullableReference_PreserveNull() {
            return BytecodeHelper.makeInsnList();
        }

        @Override
        public AbstractInsnNode loadUnsetValue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AbstractInsnNode loadNullValue() {
            if (BytecodeHelper.isReference(this.containedType)) {
                return new InsnNode(ACONST_NULL);
            }
            throw new IllegalStateException("primitive type can't store null: " + this.containedType);
        }

        @Override
        protected InsnList branchOnPresence(LabelNode dst, boolean jumpIfPresent) {
            InsnList seq = BytecodeHelper.makeInsnList();
            if (jumpIfPresent) { //the value is always present, so we should always jump
                seq.add(new JumpInsnNode(GOTO, dst));
            }
            return seq;
        }

        @Override
        public InsnList extractValueAsContainedType_DefaultIfNull(boolean assertSet) {
            return BytecodeHelper.makeInsnList();
        }

        @Override
        public InsnList extractValueAsNullableReference(boolean assertSet) {
            return BytecodeHelper.isPrimitive(this.containedType)
                    ? BytecodeHelper.makeInsnList(BytecodeHelper.generateBoxingConversion(this.containedType))
                    : BytecodeHelper.makeInsnList();
        }

        @Override
        public InsnList extractValueAsContainedType_DefaultIfUnsetOrNull() {
            return BytecodeHelper.makeInsnList();
        }

        @Override
        public InsnList extractValueAsNullableReference_NullIfUnset() {
            return this.extractValueAsNullableReference(false);
        }
    }

    /**
     * byte, short and char and int are promoted to the next largest type <strong>without sign extension</strong>, and use the value {@link Integer#MIN_VALUE}/{@link Long#MIN_VALUE} to
     * indicate "unset".
     * <p>
     * boolean, long, float and double use boxed types, or a value of null if the value is "unset".
     * <p>
     * Object and array types use the value null to indicate the value is "unset".
     *
     * @author DaPorkchop_
     * @see #emulateReference_NullIsUnset(Type)
     */
    private static class Reference_NullIsUnset extends OptionalBytecodeType {
        protected Reference_NullIsUnset(Type containedType) {
            super(containedType);
        }

        @Override
        public Type optionalType() {
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                    return Type.INT_TYPE;
                case Type.INT:
                    return Type.LONG_TYPE;
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    return Type.getObjectType(BytecodeHelper.boxedInternalName(this.containedType));
                case Type.ARRAY:
                case Type.OBJECT:
                    return this.containedType;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
        }

        @Override
        public InsnList makeOptionalFromValue(boolean assertNonNullIfUnsupported) {
            InsnList seq = new InsnList();
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                    seq.add(new MethodInsnNode(INVOKESTATIC, BytecodeHelper.boxedInternalName(this.containedType), "toUnsignedInt", Type.getMethodDescriptor(Type.INT_TYPE, this.containedType), false));
                    break;
                case Type.CHAR:
                    //no-op, it's already an unsigned int
                    break;
                case Type.INT:
                    seq.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "toUnsignedLong", "(I)J", false));
                    break;
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    seq.add(BytecodeHelper.generateBoxingConversion(this.containedType));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    if (assertNonNullIfUnsupported) {
                        seq.add(BytecodeHelper.generateNonNullAssertion(true));
                    }
                    //no-op, it's already a valid optional value (if it's null it'll be an unset optional, but this is how the method is defined)
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }

        @Override
        public InsnList makeOptionalFromNullableReference_UnsetIfNull() {
            InsnList seq = new InsnList();
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT: { //unbox the boxed value, or load the unset value if the boxed value is null
                    LabelNode nullLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.referenceType()));
                    seq.add(new JumpInsnNode(IFNULL, nullLbl));
                    seq.add(BytecodeHelper.generateUnboxingConversion(this.containedType));
                    seq.add(this.makeOptionalFromValue(false));
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(nullLbl);
                    seq.add(BytecodeHelper.pop(this.referenceType()));
                    seq.add(this.loadUnsetValue());
                    seq.add(tailLbl);
                    break;
                }
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    //no-op, it's already a valid optional value (with null values being treated as unset)
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }

        @Override
        public InsnList makeOptionalFromNullableReference_PreserveNull() {
            throw new UnsupportedOperationException(); //this scheme can't preserve nulls
        }

        @Override
        public AbstractInsnNode loadUnsetValue() {
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                    return new LdcInsnNode(Integer.MIN_VALUE);
                case Type.INT:
                    return new LdcInsnNode(Long.MIN_VALUE);
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    return new InsnNode(ACONST_NULL);
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
        }

        @Override
        public AbstractInsnNode loadNullValue() {
            throw new UnsupportedOperationException("can't store null values");
        }

        @Override
        protected InsnList branchOnPresence(LabelNode dst, boolean jumpIfPresent) {
            InsnList seq = new InsnList();
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                    seq.add(new JumpInsnNode(jumpIfPresent ? IFGE : IFLT, dst));
                    break;
                case Type.INT:
                    seq.add(new InsnNode(LCONST_0));
                    seq.add(new InsnNode(LCMP));
                    seq.add(new JumpInsnNode(jumpIfPresent ? IFGE : IFLT, dst));
                    break;
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    seq.add(new JumpInsnNode(jumpIfPresent ? IFNONNULL : IFNULL, dst));
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }

        @Override
        public InsnList extractValueAsContainedType_DefaultIfNull(boolean assertSet) {
            InsnList seq = this.preExtractValue(assertSet);
            switch (this.containedType.getSort()) {
                case Type.BYTE: //these 4 types can just be cast down to their actual types, discarding the original MSB (which indicated whether or not the value was set)
                    seq.add(new InsnNode(I2B));
                    break;
                case Type.SHORT:
                    seq.add(new InsnNode(I2S));
                    break;
                case Type.CHAR:
                    seq.add(new InsnNode(I2C));
                    break;
                case Type.INT:
                    seq.add(new InsnNode(L2I));
                    break;
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE: //unbox the value (we assume the value is set and therefore non-null)
                    seq.add(BytecodeHelper.generateUnboxingConversion(this.containedType));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    //no-op, we assume the value is non-null and therefore already a reference to the real value
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }

        @Override
        public InsnList extractValueAsNullableReference(boolean assertSet) {
            InsnList seq = this.preExtractValue(assertSet);
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    seq.add(this.extractValueAsContainedType_DefaultIfNull(false)); //the value is set, extract the real value and box it. (won't be null)
                    seq.add(BytecodeHelper.generateBoxingConversion(this.containedType));
                    break;
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //no-op: if the optional is set, the value on the stack is a non-null value of the boxed type. since we assume the value is set, we know it won't be null
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    //no-op: if the optional value is set, it's non-null and can be returned directly. since we assume the value is set, we know it won't be null
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }

        @Override
        public InsnList extractValueAsContainedType_DefaultIfUnsetOrNull() { //we don't care about the 'null' case here, because this scheme treats nulls as though they were unset
            InsnList seq = new InsnList();
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    //because the 'unset' value is Integer/Long.MIN_VALUE, the least significant bits are all zero so a simple cast to the smaller 'real' type will leave us with all zeros. this
                    //  makes computing '(isUnset(optionalValue) ? 0 : extractValue(optionalValue))' practically free!
                    return this.extractValueAsContainedType_DefaultIfNull(false);
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE: {
                    LabelNode notPresentLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(this.ifOptionalValueUnset(notPresentLbl));
                    seq.add(BytecodeHelper.generateUnboxingConversion(this.containedType)); //the value is set (non-null), so we can proceed by unboxing it
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(notPresentLbl);
                    seq.add(BytecodeHelper.pop(this.optionalType())); //the value is unset, we'll pop the (known null) value off the stack and load the default value instead
                    seq.add(BytecodeHelper.loadConstantDefaultValueInsn(this.containedType));
                    seq.add(tailLbl);
                    break;
                }
                case Type.ARRAY:
                case Type.OBJECT:
                    //no-op: if the optional value is set, it's non-null and can be returned directly, otherwise it's unset and therefore already null
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }

        @Override
        public InsnList extractValueAsNullableReference_NullIfUnset() {
            InsnList seq = new InsnList();
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT: {
                    LabelNode notPresentLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(this.ifOptionalValueUnset(notPresentLbl));
                    seq.add(this.extractValueAsContainedType_DefaultIfNull(false)); //the value is set, extract the real value and box it
                    seq.add(BytecodeHelper.generateBoxingConversion(this.containedType));
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(notPresentLbl);
                    seq.add(BytecodeHelper.pop(this.optionalType())); //the value is unset, we should return null
                    seq.add(new InsnNode(ACONST_NULL));
                    seq.add(tailLbl);
                    break;
                }
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //no-op: if the optional is set, the value on the stack is a non-null value of the boxed type, otherwise it's unset and therefore already null
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    //no-op: if the optional value is set, it's non-null and can be returned directly, otherwise it's unset and therefore already null
                    break;
                default:
                    throw new IllegalArgumentException(this.containedType.toString());
            }
            return seq;
        }
    }

    /**
     * byte, short and char and int are promoted to the next largest type <strong>without sign extension</strong>. If only the most significant bit is set, the value is unset; if only the two most
     * significant bits are set, the value is null.
     * <p>
     * boolean, long, float and double use boxed types, which can be set to {@link InvokeDynamicUtils#makeDummyObjectValueInsn(Type) a dummy object instance} to indicate that the value is "unset".
     * <p>
     * Object and array types use {@link InvokeDynamicUtils#makeDummyObjectValueInsn(Type) a dummy object instance} to indicate that the value is "unset".
     *
     * @author DaPorkchop_
     * @see #emulateReferenceWithFlag_NullIsDefault(Type)
     */
    private static class NullableReferenceWithFlag extends Reference_NullIsUnset {
        protected NullableReferenceWithFlag(Type containedType) {
            super(containedType);
        }

        @Override
        public InsnList makeOptionalFromValue(boolean assertNonNullIfUnsupported) {
            switch (this.containedType.getSort()) {
                case Type.ARRAY:
                case Type.OBJECT:
                    //we assume the value isn't the unique dummy value, so therefore it's a valid set value regardless of whether it's null or non-null
                    return BytecodeHelper.makeInsnList();
            }
            return super.makeOptionalFromValue(assertNonNullIfUnsupported);
        }

        @Override
        public InsnList makeOptionalFromNullableReference_UnsetIfNull() {
            switch (this.containedType.getSort()) {
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT: { //if the input value is null, we should return the dummy value, otherwise return the input value
                    InsnList seq = new InsnList();

                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.referenceType()));
                    seq.add(new JumpInsnNode(IFNONNULL, tailLbl));
                    seq.add(BytecodeHelper.pop(this.referenceType())); //pop the original value (which is null) and replace it with the dummy value
                    seq.add(this.loadUnsetValue());
                    seq.add(tailLbl);
                    return seq;
                }
            }
            return super.makeOptionalFromNullableReference_UnsetIfNull();
        }

        @Override
        public InsnList makeOptionalFromNullableReference_PreserveNull() {
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT: { //unbox the boxed value, or load the null value if the boxed value is null
                    InsnList seq = new InsnList();
                    LabelNode nullLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.referenceType()));
                    seq.add(new JumpInsnNode(IFNULL, nullLbl));
                    seq.add(BytecodeHelper.generateUnboxingConversion(this.containedType));
                    seq.add(this.makeOptionalFromValue(false));
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(nullLbl);
                    seq.add(BytecodeHelper.pop(this.referenceType()));
                    seq.add(this.loadNullValue());
                    seq.add(tailLbl);
                    return seq;
                }
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    //we assume the value isn't the unique dummy value, so therefore it's a valid set value regardless of whether it's null or non-null
                    return new InsnList();
            }
            return super.makeOptionalFromNullableReference_PreserveNull();
        }

        @Override
        public AbstractInsnNode loadUnsetValue() {
            switch (this.containedType.getSort()) {
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    return InvokeDynamicUtils.makeDummyObjectValueInsn(this.referenceType());
            }
            return super.loadUnsetValue();
        }

        @Override
        public AbstractInsnNode loadNullValue() {
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                    return new LdcInsnNode(Integer.MIN_VALUE >> 1); //sign-extend in order to have both upper bits set
                case Type.INT:
                    return new LdcInsnNode(Long.MIN_VALUE >> 1);
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    return new InsnNode(ACONST_NULL);
            }
            return super.loadNullValue();
        }

        @Override
        protected InsnList branchOnPresence(LabelNode dst, boolean jumpIfPresent) {
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                    return BytecodeHelper.makeInsnList( //if the current value is less than the null value, it's unset
                            this.loadNullValue(),
                            new JumpInsnNode(jumpIfPresent ? IF_ICMPGE : IF_ICMPLT, dst));
                case Type.INT:
                    return BytecodeHelper.makeInsnList(
                            this.loadNullValue(),
                            new InsnNode(LCMP),
                            new JumpInsnNode(jumpIfPresent ? IFGE : IFLT, dst));
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE:
                    //fallthrough
                case Type.ARRAY:
                case Type.OBJECT:
                    return BytecodeHelper.makeInsnList( //if the current value is the dummy instance, it's unset
                            InvokeDynamicUtils.makeDummyObjectValueInsn(this.referenceType()),
                            new JumpInsnNode(jumpIfPresent ? IF_ACMPNE : IF_ACMPEQ, dst));
            }
            return super.branchOnPresence(dst, jumpIfPresent);
        }

        @Override
        public InsnList extractValueAsContainedType_DefaultIfNull(boolean assertSet) {
            switch (this.containedType.getSort()) {
                    //for the smaller integer primitives which aren't boxed: the null flag will be truncated away when downcast to the proper type, leaving only zeroes (so it will return the default
                    //  value if the null flag is set)
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE: { //we assume the value is set (i.e. it isn't the dummy instance). however, if it's non-null we need to unbox it, otherwise we need to load the default value
                    InsnList seq = this.preExtractValue(assertSet);
                    LabelNode nullLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(new JumpInsnNode(IFNULL, nullLbl));
                    seq.add(BytecodeHelper.generateUnboxingConversion(this.containedType));
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(nullLbl);
                    seq.add(BytecodeHelper.pop(this.optionalType()));
                    seq.add(BytecodeHelper.loadConstantDefaultValueInsn(this.containedType));
                    seq.add(tailLbl);
                    return seq;
                }
            }
            return super.extractValueAsContainedType_DefaultIfNull(assertSet);
        }

        @Override
        public InsnList extractValueAsNullableReference(boolean assertSet) {
            switch (this.containedType.getSort()) {
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT: {
                    InsnList seq = this.preExtractValue(assertSet);
                    LabelNode unsetLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(super.branchOnPresence(unsetLbl, false)); //we use the super implementation here, which compares with 0 (this is fine, because we assume the value is set, so it can only be negative if the null flag is set)
                    seq.add(this.extractValueAsContainedType_DefaultIfNull(false));
                    seq.add(BytecodeHelper.generateBoxingConversion(this.containedType));
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(unsetLbl);
                    seq.add(BytecodeHelper.pop(this.optionalType()));
                    seq.add(new InsnNode(ACONST_NULL));
                    seq.add(tailLbl);
                    return seq;
                }
            }
            return super.extractValueAsNullableReference(assertSet);
        }

        @Override
        public InsnList extractValueAsContainedType_DefaultIfUnsetOrNull() {
            switch (this.containedType.getSort()) {
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE: { //we assume the value is set (i.e. it isn't the dummy instance). however, if it's non-null we need to unbox it, otherwise we need to load the default value
                    InsnList seq = new InsnList();
                    LabelNode nullOrUnsetLbl = new LabelNode();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(new JumpInsnNode(IFNULL, nullOrUnsetLbl));
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(this.ifOptionalValueUnset(nullOrUnsetLbl));
                    seq.add(BytecodeHelper.generateUnboxingConversion(this.containedType));
                    seq.add(new JumpInsnNode(GOTO, tailLbl));
                    seq.add(nullOrUnsetLbl);
                    seq.add(BytecodeHelper.pop(this.optionalType()));
                    seq.add(BytecodeHelper.loadConstantDefaultValueInsn(this.containedType));
                    seq.add(tailLbl);
                    return seq;
                }
                case Type.ARRAY:
                case Type.OBJECT: {
                    InsnList seq = new InsnList();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(this.ifOptionalValueSet(tailLbl));
                    seq.add(BytecodeHelper.pop(this.optionalType())); //the value is unset, we'll pop the (known unset) value off the stack and replace it with null
                    seq.add(new InsnNode(ACONST_NULL));
                    seq.add(tailLbl);
                    return seq;
                }
            }
            return super.extractValueAsContainedType_DefaultIfUnsetOrNull();
        }

        @Override
        public InsnList extractValueAsNullableReference_NullIfUnset() {
            switch (this.containedType.getSort()) {
                case Type.BOOLEAN:
                case Type.LONG:
                case Type.FLOAT:
                case Type.DOUBLE: {
                    InsnList seq = new InsnList();
                    LabelNode tailLbl = new LabelNode();
                    seq.add(BytecodeHelper.dup(this.optionalType()));
                    seq.add(this.ifOptionalValueSet(tailLbl));
                    seq.add(BytecodeHelper.pop(this.optionalType())); //the value is unset, we'll pop the (known unset) value off the stack and replace it with null
                    seq.add(new InsnNode(ACONST_NULL));
                    seq.add(tailLbl);
                    return seq;
                }
                case Type.ARRAY:
                case Type.OBJECT:
                    //see above
                    return this.extractValueAsContainedType_DefaultIfUnsetOrNull();
            }
            return super.extractValueAsNullableReference_NullIfUnset();
        }
    }
}
