package net.daporkchop.ppatches.util.asm.concat;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public final class PreparedConcatGenerator {
    private final List<Type> argumentTypes = new ArrayList<>();
    private final List<Object> components = new ArrayList<>();
    private int[] argumentUsageCount;

    private int nextConsumedArgumentIndex = 0;
    private int nextComponentIndex = -1;

    private boolean allLiteral = true;
    private boolean allArgumentsOrdered = true;
    private boolean usingDynamic = false;

    private int preservedArgumentIndex = -1;

    public PreparedConcatGenerator prepareAppendArgument(Type argumentType) {
        Preconditions.checkArgument(this.nextComponentIndex < 0, "already prepared?");

        this.components.add(this.argumentTypes.size());
        this.argumentTypes.add(argumentType);

        this.allLiteral = false;
        return this;
    }

    public int prepareUnorderedArgument(Type argumentType) {
        Preconditions.checkArgument(this.nextComponentIndex < 0, "already prepared?");

        int argumentIndex = this.argumentTypes.size();
        this.argumentTypes.add(argumentType);
        return argumentIndex;
    }

    public PreparedConcatGenerator prepareAppendArgumentByIndex(int argumentIndex) {
        Preconditions.checkArgument(this.nextComponentIndex < 0, "already prepared?");

        this.components.add(argumentIndex);

        this.allLiteral = false;
        if (argumentIndex != this.argumentTypes.size() - 1) { //the argument was appended out-of-order
            this.allArgumentsOrdered = false;
        }
        return this;
    }

    public PreparedConcatGenerator prepareAppendLiteral(String literal) {
        Preconditions.checkArgument(this.nextComponentIndex < 0, "already prepared?");

        if (!literal.isEmpty()) {
            int nComponents = this.components.size();
            if (nComponents > 0 && this.components.get(nComponents - 1) instanceof String) { //the previous component was also a string literal, merge them
                this.components.set(nComponents - 1, ((String) this.components.get(nComponents - 1)).concat(literal));
            } else {
                this.components.add(literal);
            }
        }
        return this;
    }

    public PreparedConcatGenerator prepareAppendConstant(Type cst) {
        Preconditions.checkArgument(this.nextComponentIndex < 0, "already prepared?");

        this.components.add(new LdcInsnNode(cst));

        this.allLiteral = false;
        return this;
    }

    public InsnList generateSetup() {
        Preconditions.checkArgument(this.nextComponentIndex < 0, "already prepared?");
        this.nextComponentIndex = 0;

        this.argumentUsageCount = new int[this.argumentTypes.size()];
        for (Object component : this.components) {
            if (component instanceof Integer) {
                this.argumentUsageCount[(Integer) component]++;
            }
        }

        InsnList result = BytecodeHelper.makeInsnList();
        if (this.allLiteral) { //the string consists entirely of string literals, we'll simply load a constant string
            Preconditions.checkState(this.components.size() <= 1);
            this.nextComponentIndex = this.components.size();
        } else if (!this.allArgumentsOrdered) {
            this.usingDynamic = true;
            this.nextComponentIndex = this.components.size();
        } else {
            result.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
            result.add(new InsnNode(DUP));
            result.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
            this.generateAppendNextConstants(result); //append starting constants
        }
        return result;
    }

    public InsnList generateConsumeArgument() {
        Preconditions.checkArgument(this.nextComponentIndex >= 0, "not prepared?");
        Preconditions.checkArgument(this.nextConsumedArgumentIndex < this.argumentTypes.size(), "all arguments have already been consumed?");

        InsnList result = BytecodeHelper.makeInsnList();
        if (this.usingDynamic) { //we're using DynamicConcatGenerator to generate this concatenation
            return result;
        }

        int consumedArgumentIndex = this.nextConsumedArgumentIndex++;
        Type consumedArgumentType = this.argumentTypes.get(consumedArgumentIndex);

        if (this.argumentUsageCount[consumedArgumentIndex] == 0) { //the argument value isn't actually used, it can be safely discarded
            result.add(BytecodeHelper.pop(consumedArgumentType));
            return result;
        }

        boolean preservedConsumedArgument = false;
        do {
            int currentAppendedArgumentIndex = (int) this.components.get(this.nextComponentIndex);
            if (currentAppendedArgumentIndex == consumedArgumentIndex) { //the argument being consumed is the value to append
                if (--this.argumentUsageCount[consumedArgumentIndex] > 0) { //the consumed value needs to be preserved on the stack for later use, as it'll be needed again
                    if (this.preservedArgumentIndex >= 0) {
                        throw new UnsupportedOperationException("append operands are too far out-of-order!");
                    }

                    result.add(BytecodeHelper.dup_x1(consumedArgumentType));
                    this.preservedArgumentIndex = consumedArgumentIndex;
                }

                result.add(AppendStringBuilderOptimizationRegistry.makeAppend(consumedArgumentType));
            } else if (!preservedConsumedArgument && this.argumentUsageCount[consumedArgumentIndex] > 0) {
                preservedConsumedArgument = true;

                if (this.preservedArgumentIndex >= 0) {
                    throw new UnsupportedOperationException("append operands are too far out-of-order!");
                }

                result.add(BytecodeHelper.swap(Type.getObjectType("java/lang/StringBuilder"), consumedArgumentType));
                this.preservedArgumentIndex = consumedArgumentIndex;
                continue; //go around without appending anything
            } else if (this.preservedArgumentIndex >= 0 && currentAppendedArgumentIndex == this.preservedArgumentIndex) { //the preserved value is the one to append
                Type preservedArgumentType = this.argumentTypes.get(this.preservedArgumentIndex);
                if (--this.argumentUsageCount[this.preservedArgumentIndex] == 0) { //the preserved value only needs to be appended this one time, we can swap it with the StringBuilder
                    result.add(BytecodeHelper.swap(preservedArgumentType, Type.getObjectType("java/lang/StringBuilder")));
                    this.preservedArgumentIndex = -1;
                } else { //the preserved value needs to keep being preserved
                    result.add(BytecodeHelper.swap(preservedArgumentType, Type.getObjectType("java/lang/StringBuilder")));
                    result.add(BytecodeHelper.dup_x1(preservedArgumentType));
                }
                result.add(AppendStringBuilderOptimizationRegistry.makeAppend(preservedArgumentType));
            } else {
                break;
            }

            this.nextComponentIndex++;
            this.generateAppendNextConstants(result); //append trailing constants
        } while (this.nextComponentIndex < this.components.size());

        return result;
    }

    public InsnList generateFinish() {
        Preconditions.checkArgument(this.nextComponentIndex == this.components.size(), "not all arguments have been consumed?");

        InsnList result = BytecodeHelper.makeInsnList();
        if (this.allLiteral) {
            result.add(new LdcInsnNode(this.components.isEmpty() ? "" : (String) this.components.get(0)));
        } else if (this.usingDynamic) {
            result.add(DynamicConcatGenerator.makeUnorderedDynamicStringConcatenation(this.argumentTypes.toArray(new Type[0]), this.components.toArray()));
        } else {
            result.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
        }
        return result;
    }

    private void generateAppendNextConstants(InsnList list) { //append the next literals and constants to the StringBuilder, which we assume is on top of the stack
        while (this.nextComponentIndex < this.components.size()) {
            Object component = this.components.get(this.nextComponentIndex);
            if (component instanceof String) { //literal
                this.nextComponentIndex++;
                list.add(new LdcInsnNode(component));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
            } else if (component instanceof LdcInsnNode) { //constant
                this.nextComponentIndex++;
                list.add(((LdcInsnNode) component).clone(null));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false));
            } else { //the next argument is a method argument, stop here
                return;
            }
        }
    }
}
