package net.daporkchop.ppatches.util.asm.concat;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.util.MethodHandleUtils;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.TypeUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class DynamicConcatGenerator {
    private static void convertEligibleConstantsToLiterals(Object[] recipe) {
        for (int i = 0; i < recipe.length; i++) {
            Object item = recipe[i];
            if (item instanceof AbstractInsnNode && BytecodeHelper.isConstant((AbstractInsnNode) item)) {
                Object cst = BytecodeHelper.decodeConstant((AbstractInsnNode) item);
                if (cst == null) {
                    recipe[i] = "null";
                } else if (cst instanceof String) {
                    recipe[i] = cst;
                } else if (cst instanceof Integer || cst instanceof Long || cst instanceof Float || cst instanceof Double) {
                    recipe[i] = cst.toString();
                } else if (cst instanceof Type) {
                    //no-op
                } else {
                    throw new IllegalArgumentException("constant " + cst.getClass().getName());
                }
            }
        }
    }

    /**
     * Returns an INVOKEDYNAMIC instruction which will consume one value of each of the given argument types and combine them using string concatenation.
     *
     * @param argumentTypes the types of values to concatenate
     */
    public static AbstractInsnNode makeDynamicStringConcatenation(Type... argumentTypes) {
        if (argumentTypes.length == 0) {
            //concatenating nothing returns empty string
            return new LdcInsnNode("");
        }
        if (argumentTypes.length == 1 && argumentTypes[0].getSort() == Type.OBJECT && "java/lang/String".equals(argumentTypes[0].getInternalName())) {
            //concatenating a single string with itself returns itself
            return new InsnNode(NOP);
        }

        return new InvokeDynamicInsnNode("concat", Type.getMethodDescriptor(Type.getType(String.class), argumentTypes),
                new Handle(H_INVOKESTATIC, Type.getInternalName(DynamicConcatGenerator.class), "makeConcat", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", SimpleConcatGenerator.class.isInterface()));
    }

    /**
     * Returns an INVOKEDYNAMIC instruction which will perform a string concatenation according to the given recipe.
     * <p>
     * Each recipe item may be one of the following:
     * <ul>
     *     <li>A {@link Type}, in which case the INVOKEDYNAMIC instruction will consume a value of this type at this point and append it to the result</li>
     *     <li>A {@link String} literal, which will be appended to the result as-is</li>
     *     <li>A {@link AbstractInsnNode} which is a {@link BytecodeHelper#isConstant(AbstractInsnNode) constant value}, which will be appended to the result as-is</li>
     * </ul>
     *
     * @param recipe the concatenation recipe
     */
    public static AbstractInsnNode makeDynamicStringConcatenation(Object... recipe) {
        recipe = recipe.clone();
        convertEligibleConstantsToLiterals(recipe);

        CONTAINS_NON_STRING_LITERAL:
        {
            for (Object item : recipe) {
                if (!(item instanceof String)) {
                    break CONTAINS_NON_STRING_LITERAL; //here be velociraptors
                }
            }

            //the recipe consist entirely of string literals, we can concatenate them here and return an LDC instruction
            StringBuilder literalBuilder = new StringBuilder();
            for (Object argument : recipe) {
                literalBuilder.append((String) argument);
            }
            return new LdcInsnNode(literalBuilder.toString());
        }

        StringBuilder descBuilder = new StringBuilder().append('(');
        StringBuilder recipeBuilder = new StringBuilder();
        List<Object> bsmArgsBuffer = new ArrayList<>();
        bsmArgsBuffer.add(null);

        for (Object item : recipe) {
            if (item instanceof Type) {
                descBuilder.append(item);
                recipeBuilder.append(RECIPE_ARG);
            } else if (item instanceof String) {
                String stringArgument = (String) item;
                if (stringArgument.indexOf(RECIPE_ARG) < 0 && stringArgument.indexOf(RECIPE_CST) < 0) { //append the string directly
                    recipeBuilder.append(stringArgument);
                } else { //the string contains some special symbols, append it indirectly as a constant
                    recipeBuilder.append(RECIPE_CST);
                    bsmArgsBuffer.add(stringArgument);
                }
            } else if (item instanceof AbstractInsnNode && BytecodeHelper.isConstant((AbstractInsnNode) item)) {
                Object cst = BytecodeHelper.decodeConstant((AbstractInsnNode) item);
                if (cst == null) {
                    recipeBuilder.append("null");
                } else { //append the constant as a constant
                    recipeBuilder.append(RECIPE_CST);
                    bsmArgsBuffer.add(cst);
                }
            } else {
                throw new IllegalArgumentException(String.valueOf(item));
            }
        }

        bsmArgsBuffer.set(0, recipeBuilder.toString());
        descBuilder.append(")Ljava/lang/String;");
        return new InvokeDynamicInsnNode("concatWithConstants", descBuilder.toString(),
                new Handle(H_INVOKESTATIC, Type.getInternalName(DynamicConcatGenerator.class), "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", SimpleConcatGenerator.class.isInterface()),
                bsmArgsBuffer.toArray());
    }

    /**
     * Returns an INVOKEDYNAMIC instruction which will perform a string concatenation according to the given recipe.
     * <p>
     * Each recipe item may be one of the following:
     * <ul>
     *     <li>An {@link Integer}, in which case the argument at the corresponding index will be appended to the result. Note that the same argument may be appended multiple
     *     times, and that the resulting instruction will still consume an argument value even if it is never used!</li>
     *     <li>A {@link String} literal, which will be appended to the result as-is</li>
     *     <li>A {@link AbstractInsnNode} which is a {@link BytecodeHelper#isConstant(AbstractInsnNode) constant value}, which will be appended to the result as-is</li>
     * </ul>
     *
     * @param argumentTypes the consumed argument types
     * @param recipe        the concatenation recipe
     */
    public static AbstractInsnNode makeUnorderedDynamicStringConcatenation(Type[] argumentTypes, Object... recipe) {
        recipe = recipe.clone();
        convertEligibleConstantsToLiterals(recipe);

        CONTAINS_NON_STRING_LITERAL:
        {
            for (Object argument : recipe) {
                if (!(argument instanceof String)) {
                    break CONTAINS_NON_STRING_LITERAL; //here be velociraptors
                }
            }

            //the recipe consist entirely of string literals, we can concatenate them here and return an LDC instruction
            StringBuilder literalBuilder = new StringBuilder();
            for (Object argument : recipe) {
                literalBuilder.append((String) argument);
            }
            return new LdcInsnNode(literalBuilder.toString());
        }

        StringBuilder recipeBuilder = new StringBuilder();
        List<Object> bsmArgsBuffer = new ArrayList<>();
        bsmArgsBuffer.add(null);

        for (Object argument : recipe) {
            if (argument instanceof Integer) {
                recipeBuilder.append(RECIPE_ARG).append((char) (RECIPE_ARG_UNORDERED_INDEX_OFFSET + (int) argument));
            } else if (argument instanceof String) {
                String stringArgument = (String) argument;
                if (stringArgument.indexOf(RECIPE_ARG) < 0 && stringArgument.indexOf(RECIPE_CST) < 0) { //append the string directly
                    recipeBuilder.append(stringArgument);
                } else { //the string contains some special symbols, append it indirectly as a constant
                    recipeBuilder.append(RECIPE_CST);
                    bsmArgsBuffer.add(stringArgument);
                }
            } else if (argument instanceof AbstractInsnNode && BytecodeHelper.isConstant((AbstractInsnNode) argument)) {
                Object cst = BytecodeHelper.decodeConstant((AbstractInsnNode) argument);
                if (cst == null) {
                    recipeBuilder.append("null");
                } else { //append the constant as a constant
                    recipeBuilder.append(RECIPE_CST);
                    bsmArgsBuffer.add(cst);
                }
            } else {
                throw new IllegalArgumentException(String.valueOf(argument));
            }
        }

        bsmArgsBuffer.set(0, recipeBuilder.toString());
        return new InvokeDynamicInsnNode("unorderedConcatWithConstants", Type.getMethodDescriptor(Type.getObjectType("java/lang/String"), argumentTypes),
                new Handle(H_INVOKESTATIC, Type.getInternalName(DynamicConcatGenerator.class), "makeUnorderedConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", SimpleConcatGenerator.class.isInterface()),
                bsmArgsBuffer.toArray());
    }

    /**
     * @apiNote This is functionally equivalent to {@code java.lang.invoke.StringConcatFactory#makeConcat} used by Java 9+.
     */
    @SneakyThrows({NoSuchMethodException.class, IllegalAccessException.class})
    public static CallSite makeConcat(MethodHandles.Lookup lookup, String name, MethodType type) {
        switch (type.parameterCount()) {
            case 0:
                return new ConstantCallSite(MethodHandleUtils.constant(type.returnType(), ""));
            case 1:
                return new ConstantCallSite(MethodHandleUtils.stringValueOf(type.parameterType(0)).asType(type));
            case 2:
                if (type.parameterType(0) == String.class && type.parameterType(1) == String.class) {
                    return new ConstantCallSite(lookup.findVirtual(String.class, "concat", MethodType.methodType(String.class, String.class)).asType(type));
                }
                break;
        }

        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, "StringConcat", null, "java/lang/Object", null);

        { //type.returnType name(type.parameters)
            String desc = type.toMethodDescriptorString();
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
            mv.visitCode();

            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            int maxs = 2;
            for (int argumentIndex = 0, argumentLvtIndex = 0; argumentIndex < type.parameterCount(); argumentIndex++) {
                //load and append the argument
                Type argumentType = Type.getType(type.parameterType(argumentIndex));
                mv.visitVarInsn(argumentType.getOpcode(ILOAD), argumentLvtIndex);
                AppendStringBuilderOptimizationRegistry.visitAppend(mv, argumentType);

                int argumentSize = argumentType.getSize();
                argumentLvtIndex += argumentSize;
                maxs = Math.max(maxs, 1 + argumentSize);
            }

            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);

            mv.visitMaxs(maxs, TypeUtils.extractArgumentsSizes(Type.getArgumentsAndReturnSizes(desc)));
            mv.visitEnd();
        }

        cw.visitEnd();

        return new ConstantCallSite(lookup.findStatic(UnsafeWrapper.defineAnonymousClass(lookup.lookupClass(), cw.toByteArray(), null), name, type));
    }

    private static final char RECIPE_ARG = '\u0001';
    private static final char RECIPE_CST = '\u0002';
    private static final char RECIPE_ARG_UNORDERED_INDEX_OFFSET = '\u0003';

    /**
     * @apiNote This is functionally equivalent to {@code java.lang.invoke.StringConcatFactory#makeConcatWithConstants} used by Java 9+.
     */
    @SneakyThrows({NoSuchMethodException.class, IllegalAccessException.class})
    public static CallSite makeConcatWithConstants(MethodHandles.Lookup lookup, String name, MethodType type, String recipe, Object... constants) {
        if (type.parameterCount() == recipe.length()) {
            char[] buf = new char[recipe.length()];
            Arrays.fill(buf, RECIPE_ARG);
            if (String.valueOf(buf).equals(recipe)) { //the arguments are effectively being concatenated together directly without anything extra
                return makeConcat(lookup, name, type);
            }
        }
        if (type.parameterCount() == 0) { //the method's arguments are never used for concatenation
            assert recipe.indexOf(RECIPE_ARG) < 0 : recipe;

            StringBuilder builder = new StringBuilder();
            int constantIndex = 0;
            for (int recipeIndex = 0; recipeIndex < recipe.length(); recipeIndex++) {
                char recipeChar = recipe.charAt(recipeIndex);

                if (recipeChar == RECIPE_CST) {
                    //convert the corresponding constant to a string and append it as a constant value
                    builder.append(constants[constantIndex++]);
                } else {
                    //append the char as-is
                    builder.append(recipeChar);
                }
            }
            return new ConstantCallSite(MethodHandleUtils.constant(type.returnType(), builder.toString().intern()));
        }

        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, "StringConcat", null, "java/lang/Object", null);

        {
            String desc = type.toMethodDescriptorString();
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
            mv.visitCode();

            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            StringBuilder bufferedConstant = new StringBuilder();

            int maxs = 2;
            for (int recipeIndex = 0, argumentIndex = 0, argumentLvtIndex = 0, constantIndex = 0; recipeIndex < recipe.length(); recipeIndex++) {
                char recipeChar = recipe.charAt(recipeIndex);
                switch (recipeChar) {
                    case RECIPE_ARG: {
                        if (bufferedConstant.length() > 0) { //some constant text is buffered, flush it before appending this argument
                            if (bufferedConstant.length() == 1) {
                                mv.visitLdcInsn(bufferedConstant.charAt(0));
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
                            } else {
                                mv.visitLdcInsn(bufferedConstant.toString());
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                            }

                            bufferedConstant.setLength(0);
                        }

                        //load and append the argument
                        Type argumentType = Type.getType(type.parameterType(argumentIndex));
                        mv.visitVarInsn(argumentType.getOpcode(ILOAD), argumentLvtIndex);
                        AppendStringBuilderOptimizationRegistry.visitAppend(mv, argumentType);

                        argumentIndex++;
                        int argumentSize = argumentType.getSize();
                        argumentLvtIndex += argumentSize;
                        maxs = Math.max(maxs, 1 + argumentSize);
                        break;
                    }
                    case RECIPE_CST:
                        //convert the corresponding constant to a string and append it as a constant value
                        bufferedConstant.append(constants[constantIndex++]);
                        break;
                    default:
                        //append the character as a constant value
                        bufferedConstant.append(recipeChar);
                        break;
                }
            }

            if (bufferedConstant.length() > 0) { //some constant text is buffered, flush it before finishing the string
                if (bufferedConstant.length() == 1) {
                    mv.visitLdcInsn(bufferedConstant.charAt(0));
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
                } else {
                    mv.visitLdcInsn(bufferedConstant.toString());
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                }
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);

            mv.visitMaxs(maxs, TypeUtils.extractArgumentsSizes(Type.getArgumentsAndReturnSizes(desc)));
            mv.visitEnd();
        }

        cw.visitEnd();

        return new ConstantCallSite(lookup.findStatic(UnsafeWrapper.defineAnonymousClass(lookup.lookupClass(), cw.toByteArray(), null), name, type));
    }

    @SneakyThrows({NoSuchMethodException.class, IllegalAccessException.class})
    public static CallSite makeUnorderedConcatWithConstants(MethodHandles.Lookup lookup, String name, MethodType type, String recipe, Object... constants) {
        if (type.parameterCount() == 0) { //the method's arguments are never used for concatenation
            assert recipe.indexOf(RECIPE_ARG) < 0;
            return makeConcatWithConstants(lookup, name, type, recipe, constants);
        }

        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, "StringConcat", null, "java/lang/Object", null);

        {
            String desc = type.toMethodDescriptorString();
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
            mv.visitCode();

            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            StringBuilder bufferedConstant = new StringBuilder();

            int[] argumentLvtIndices = new int[type.parameterCount()];
            for (int argumentIndex = 0, argumentLvtIndex = 0; argumentIndex < type.parameterCount(); argumentIndex++) {
                argumentLvtIndices[argumentIndex] = argumentLvtIndex;
                argumentLvtIndex += Type.getType(type.parameterType(argumentIndex)).getSize();
            }

            int maxs = 2;
            for (int recipeIndex = 0, constantIndex = 0; recipeIndex < recipe.length(); recipeIndex++) {
                char recipeChar = recipe.charAt(recipeIndex);
                switch (recipeChar) {
                    case RECIPE_ARG: {
                        if (bufferedConstant.length() > 0) { //some constant text is buffered, flush it before appending this argument
                            if (bufferedConstant.length() == 1) {
                                mv.visitLdcInsn(bufferedConstant.charAt(0));
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
                            } else {
                                mv.visitLdcInsn(bufferedConstant.toString());
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                            }

                            bufferedConstant.setLength(0);
                        }

                        //load and append the argument
                        int argumentIndex = recipe.charAt(++recipeIndex) - RECIPE_ARG_UNORDERED_INDEX_OFFSET;
                        Type argumentType = Type.getType(type.parameterType(argumentIndex));
                        mv.visitVarInsn(argumentType.getOpcode(ILOAD), argumentLvtIndices[argumentIndex]);
                        AppendStringBuilderOptimizationRegistry.visitAppend(mv, argumentType);

                        maxs = Math.max(maxs, 1 + argumentType.getSize());
                        break;
                    }
                    case RECIPE_CST:
                        //convert the corresponding constant to a string and append it as a constant value
                        bufferedConstant.append(constants[constantIndex++]);
                        break;
                    default:
                        //append the character as a constant value
                        bufferedConstant.append(recipeChar);
                        break;
                }
            }

            if (bufferedConstant.length() > 0) { //some constant text is buffered, flush it before finishing the string
                if (bufferedConstant.length() == 1) {
                    mv.visitLdcInsn(bufferedConstant.charAt(0));
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
                } else {
                    mv.visitLdcInsn(bufferedConstant.toString());
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                }
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);

            mv.visitMaxs(maxs, TypeUtils.extractArgumentsSizes(Type.getArgumentsAndReturnSizes(desc)));
            mv.visitEnd();
        }

        cw.visitEnd();

        return new ConstantCallSite(lookup.findStatic(UnsafeWrapper.defineAnonymousClass(lookup.lookupClass(), cw.toByteArray(), null), name, type));
    }
}
