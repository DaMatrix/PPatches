package net.daporkchop.ppatches.modules.java.optimizeStringFormat;

import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.VarargsParameterDecoder;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.daporkchop.ppatches.util.asm.analysis.IDataflowProvider;
import net.daporkchop.ppatches.util.asm.concat.PreparedConcatGenerator;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.objectweb.asm.Opcodes.POP;

/**
 * @author DaPorkchop_
 */
public class OptimizeStringFormatTransformer implements ITreeClassTransformer.IndividualMethod.Analyzed, ITreeClassTransformer.ExactInterested {
    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesMethod("java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions) {
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (BytecodeHelper.isINVOKESTATIC(insn, "java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) {
                //body moved to separate method to help JIT optimize the main loop, which is supposed to be fast
                changeFlags |= tryTransformStringFormat(classNode, methodNode, (MethodInsnNode) insn, instructions);
            }
        }
        return changeFlags;
    }

    /*private static int tryTransformStringFormat(ClassNode classNode, MethodNode methodNode, MethodInsnNode invokeFormatInsn, AnalyzedInsnList instructions) {
        List<AbstractInsnNode> formatSources = instructions.getSingleStackOperandSources(invokeFormatInsn);
        if (formatSources == null || formatSources.size() != 2) {
            return 0;
        }

        AbstractInsnNode loadFormatStringInsn = formatSources.get(0);
        if (!(loadFormatStringInsn instanceof LdcInsnNode) || !(((LdcInsnNode) loadFormatStringInsn).cst instanceof String)) {
            return 0;
        }
        String formatString = (String) ((LdcInsnNode) loadFormatStringInsn).cst;

        VarargsParameterDecoder.Result varargs = VarargsParameterDecoder.tryDecode(invokeFormatInsn, instructions).orElse(null);
        if (varargs == null) {
            return 0;
        }

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            Type[] dynamicArgs = new Type[varargs.elements.size()];
            for (int i = 0; i < dynamicArgs.length; i++) {
                VarargsParameterDecoder.Element element = varargs.elements.get(i);
                Type dynamicType = Type.getType(Object.class);

                AbstractInsnNode makeElementInsn = instructions.getSingleStackOperandSourceFromTop(element.astoreInsn, 0);
                if (makeElementInsn != null) {
                    //TODO: make a cleaner method to check if something is a boxing conversion
                    if (BytecodeHelper.isINVOKESTATIC(makeElementInsn, Type.getInternalName(Integer.class), "valueOf", Type.getMethodDescriptor(Type.getType(Integer.class), Type.INT_TYPE))) {
                        dynamicType = Type.INT_TYPE;
                        batch.remove(makeElementInsn);
                    } else if (BytecodeHelper.isINVOKESTATIC(makeElementInsn, Type.getInternalName(Long.class), "valueOf", Type.getMethodDescriptor(Type.getType(Long.class), Type.LONG_TYPE))) {
                        dynamicType = Type.LONG_TYPE;
                        batch.remove(makeElementInsn);
                    }
                }

                dynamicArgs[i] = dynamicType;
            }

            batch.remove(loadFormatStringInsn);
            batch.remove(varargs.loadLengthInsn);
            batch.remove(varargs.newArrayInsn);
            for (VarargsParameterDecoder.Element element : varargs.elements) {
                batch.remove(element.dupInsn);
                batch.remove(element.loadIndexInsn);
                batch.remove(element.astoreInsn);
            }

            batch.set(invokeFormatInsn, new InvokeDynamicInsnNode("format", Type.getMethodDescriptor(Type.getType(String.class), dynamicArgs),
                    new Handle(
                            H_INVOKESTATIC,
                            Type.getInternalName(OptimizeStringFormatTransformer.class),
                            "bootstrapFormat",
                            Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(String.class), Type.getType(Object[].class)), false),
                    formatString));

            return CHANGED;
        }
    }

    public static CallSite bootstrapFormat(MethodHandles.Lookup lookup, String name, MethodType type, String format, Object... args) throws Throwable {
        List<FormatSpecifier> parsed = parse(format);
        if (parsed == null) {
            return new ConstantCallSite(lookup.findStatic(String.class, format, MethodType.methodType(String.class, String.class, Object[].class)).asType(type));
        }

        AnonymousClassWriter cw = AnonymousClassWriter.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, "Formatter/" + format, null, Type.getInternalName(Object.class), null);

        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, type.toMethodDescriptorString(), null, null);
            mv.visitCode();

            PreparedConcatGenerator concat = new PreparedConcatGenerator();
            for (FormatSpecifier specifier : parsed) {
                specifier.prepare(concat, type);
            }

            concat.generateSetup().accept(mv);
            for (FormatSpecifier specifier : parsed) {
                specifier
            }

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();
        Class<?> clazz = cw.defineAnonymousClass(lookup.lookupClass());
        return new ConstantCallSite(lookup.findStatic(clazz, name, type));
    }

    private static List<FormatSpecifier> parse(String format) {
        List<FormatSpecifier> components = new ArrayList<>();
        for (int i = 0; i < format.length(); i++) {
            if (format.charAt(i) == '%') {
                i++;
                if (i >= format.length()) {
                    return null;
                }

                switch (format.charAt(i)) {
                    case 'd':
                    case 's':
                        components.add(new BasicFormat(components.size()));
                        break;
                    default:
                        return null;
                }
            } else {
                int begin = i;
                while (i + 1 < format.length() && format.charAt(i + 1) != '%') {
                    i++;
                }
                components.add(new Literal(format.substring(begin, i)));
            }
        }
        return components;
    }

    private static abstract class FormatSpecifier {
        public abstract void prepare(PreparedConcatGenerator concat, MethodType type);
        public abstract InsnList generate(PreparedConcatGenerator concat, MethodType type, int[] lvtIndices);
    }

    @RequiredArgsConstructor
    private static class Literal extends FormatSpecifier {
        private final String text;

        @Override
        public void prepare(PreparedConcatGenerator concat, MethodType type) {
            concat.prepareAppendLiteral(this.text);
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    @RequiredArgsConstructor
    private static class BasicFormat extends FormatSpecifier {
        private final int index;

        @Override
        public void prepare(PreparedConcatGenerator concat, MethodType type) {
            concat.prepareAppendArgument(Type.getType(type.parameterType(this.index)));
        }
    }*/

    private static int tryTransformStringFormat(ClassNode classNode, MethodNode methodNode, MethodInsnNode invokeFormatInsn, AnalyzedInsnList instructions) {
        List<AbstractInsnNode> formatSources = instructions.getSingleStackOperandSources(invokeFormatInsn);
        if (formatSources == null || formatSources.size() != 2) {
            return 0;
        }

        AbstractInsnNode loadFormatStringInsn = formatSources.get(0);
        if (!(loadFormatStringInsn instanceof LdcInsnNode) || !(((LdcInsnNode) loadFormatStringInsn).cst instanceof String)) {
            return 0;
        }
        String formatString = (String) ((LdcInsnNode) loadFormatStringInsn).cst;

        VarargsParameterDecoder.Result varargs = VarargsParameterDecoder.tryDecode(invokeFormatInsn, instructions).orElse(null);
        if (varargs == null) {
            return 0;
        }

        List<FormatSpecifier> format = parse(formatString);
        if (format == null) {
            return 0;
        }

        try (AnalyzedInsnList.ChangeBatch batch = instructions.beginChanges()) {
            List<AbstractInsnNode> toRemove = new ArrayList<>();
            PreparedConcatGenerator concat = new PreparedConcatGenerator();

            boolean[] specifiersConsumed = new boolean[format.size()];
            Iterator<VarargsParameterDecoder.Element> valueSourceItr = varargs.elements.iterator();
            for (int specifierIndex = 0; specifierIndex < format.size(); specifierIndex++) {
                specifiersConsumed[specifierIndex] = format.get(specifierIndex).prepareFormat(concat, valueSourceItr, instructions, toRemove);
            }

            //remove any leftover varargs parameters
            while (valueSourceItr.hasNext()) {
                batch.insertBefore(valueSourceItr.next().astoreInsn, new InsnNode(POP));
            }

            batch.insertAfter(loadFormatStringInsn, concat.generateSetup());
            batch.insertAfter(loadFormatStringInsn, new InsnNode(POP)); //keep the format string in the bytecode, just so we can see what the original format string was

            for (int specifierIndex = 0, argumentIndex = 0; specifierIndex < format.size(); specifierIndex++) {
                if (specifiersConsumed[specifierIndex]) {
                    batch.insertBefore(varargs.elements.get(argumentIndex).astoreInsn, concat.generateConsumeArgument());
                    argumentIndex++;
                }
            }

            batch.insertBefore(invokeFormatInsn, concat.generateFinish());
            batch.remove(invokeFormatInsn);

            batch.remove(varargs.loadLengthInsn);
            batch.remove(varargs.newArrayInsn);
            for (VarargsParameterDecoder.Element element : varargs.elements) {
                batch.remove(element.dupInsn);
                batch.remove(element.loadIndexInsn);
                batch.remove(element.astoreInsn);
            }
            for (AbstractInsnNode insn : toRemove) {
                batch.remove(insn);
            }

            PPatchesMod.LOGGER.info("Transformed String.format() at L{};{}{} {}: \"{}\"",
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.findLineNumberForLog(invokeFormatInsn), formatString);

            return CHANGED;
        }
    }

    private static List<FormatSpecifier> parse(String format) {
        List<FormatSpecifier> components = new ArrayList<>();

        for (int i = 0; i < format.length(); i++) {
            if (format.charAt(i) == '%') {
                i++;
                if (i >= format.length()) {
                    return null;
                }

                switch (format.charAt(i)) {
                    case 'd':
                        components.add(new IntegerFormat());
                        break;
                    case 's':
                        components.add(new StringFormat());
                        break;
                    default:
                        return null;
                }
            } else {
                int begin = i;
                int end = format.indexOf('%', begin);
                end = end < 0 ? format.length() : end;
                components.add(new Literal(format.substring(begin, end)));
                i = end - 1;
            }
        }
        return components;
    }

    private static abstract class FormatSpecifier {
        public abstract boolean prepareFormat(PreparedConcatGenerator generator, Iterator<VarargsParameterDecoder.Element> valueSourceItr, IDataflowProvider dataflowProvider, List<AbstractInsnNode> toRemove);
    }

    @RequiredArgsConstructor
    private static class Literal extends FormatSpecifier {
        private final String text;

        @Override
        public boolean prepareFormat(PreparedConcatGenerator generator, Iterator<VarargsParameterDecoder.Element> valueSourceItr, IDataflowProvider dataflowProvider, List<AbstractInsnNode> toRemove) {
            generator.prepareAppendLiteral(this.text);
            return false;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    private static class IntegerFormat extends FormatSpecifier {
        @Override
        public boolean prepareFormat(PreparedConcatGenerator generator, Iterator<VarargsParameterDecoder.Element> valueSourceItr, IDataflowProvider dataflowProvider, List<AbstractInsnNode> toRemove) {
            VarargsParameterDecoder.Element element = valueSourceItr.next();

            Type dynamicType = Type.getType(Object.class);

            AbstractInsnNode makeElementInsn = dataflowProvider.getSingleStackOperandSourceFromTop(element.astoreInsn, 0);
            if (makeElementInsn != null) {
                //TODO: make a cleaner method to check if something is a boxing conversion
                if (BytecodeHelper.isINVOKESTATIC(makeElementInsn, Type.getInternalName(Integer.class), "valueOf", Type.getMethodDescriptor(Type.getType(Integer.class), Type.INT_TYPE))) {
                    dynamicType = Type.INT_TYPE;
                    toRemove.add(makeElementInsn);
                } else if (BytecodeHelper.isINVOKESTATIC(makeElementInsn, Type.getInternalName(Long.class), "valueOf", Type.getMethodDescriptor(Type.getType(Long.class), Type.LONG_TYPE))) {
                    dynamicType = Type.LONG_TYPE;
                    toRemove.add(makeElementInsn);
                }
            }

            generator.prepareAppendArgument(dynamicType);
            return true;
        }
    }

    private static class StringFormat extends FormatSpecifier {
        @Override
        public boolean prepareFormat(PreparedConcatGenerator generator, Iterator<VarargsParameterDecoder.Element> valueSourceItr, IDataflowProvider dataflowProvider, List<AbstractInsnNode> toRemove) {
            valueSourceItr.next();
            generator.prepareAppendArgument(Type.getType(Object.class));
            return true;
        }
    }
}
