package net.daporkchop.ppatches.util.asm.analysis;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Set;

/**
 * @author DaPorkchop_
 */
public class UsageValue extends SourceValue {
    public static UsageValue merge(UsageValue a, UsageValue b) {
        //TODO: avoid allocating a temporary SourceValue instance by moving the SmallSet merging code to a separate method
        SourceValue merged = AnalyzedInsnList.SOURCE_INTERPRETER.merge(a, b);
        if (merged == a) {
            return a;
        } else if (merged == b) {
            return b;
        } else {
            return new UsageValue(merged.size, merged.insns);
        }
    }

    public static UsageValue mergeNullable(UsageValue a, UsageValue b) {
        return a != null ? (b != null ? merge(a, b) : a) : b;
    }

    public UsageValue(int size) {
        super(size);
    }

    public UsageValue(int size, AbstractInsnNode insn) {
        super(size, insn);
    }

    public UsageValue(int size, Set<AbstractInsnNode> insns) {
        super(size, insns);
    }
}
