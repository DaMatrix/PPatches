package net.daporkchop.ppatches.modules.optifine.optimizeReflector;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeReflectorTransformer implements ITreeClassTransformer.IndividualMethod, ITreeClassTransformer.ExactInterested {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return transformedName.startsWith("net.minecraft.") || (transformedName.startsWith("net.optifine.") && !"net.optifine.reflect.Reflector".equals(transformedName));
    }

    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesClass("net/optifine/reflect/Reflector");
    }

    @Override
    public int transformMethod(String name, String transformedName, ClassNode classNode, MethodNode methodNode, InsnList instructions) {
        if ("net.optifine.reflect.Reflector".equals(transformedName)) {
            return 0;
        }

        //some other class, we want to check for references to reflector so we can get rid of them
        int changeFlags = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next) {
            next = insn.getNext();

            if (insn.getOpcode() != INVOKEVIRTUAL && insn.getOpcode() != INVOKESTATIC) {
                continue;
            }

            MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
            if (!methodInsnNode.owner.startsWith("net/optifine/reflect/Reflector")) {
                continue;
            }

            //body moved to separate method to help JIT optimize the main loop, which is supposed to be fast
            changeFlags |= transformCall(classNode, methodNode, methodInsnNode);
        }
        return changeFlags;
    }

    private static int transformCall(ClassNode classNode, MethodNode methodNode, MethodInsnNode methodInsnNode) {
        FieldInsnNode reflectorFieldLoadInsn;
        for (AbstractInsnNode node = methodInsnNode.getPrevious(); ; node = node.getPrevious()) {
            if (node == null) {
                PPatchesMod.LOGGER.warn("{}#{}: couldn't find GETSTATIC corresponding to {} {}.{}{}", classNode.name, methodNode.name, Printer.OPCODES[methodInsnNode.getOpcode()], methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                return 0;
            }

            if (node.getOpcode() == GETSTATIC && ((FieldInsnNode) node).desc.startsWith("net/optifine/reflect/Reflector", 1)) {
                reflectorFieldLoadInsn = (FieldInsnNode) node;
                assert methodInsnNode.getOpcode() == INVOKESTATIC
                       || reflectorFieldLoadInsn.desc.substring(1, reflectorFieldLoadInsn.desc.length() - 1).equals(methodInsnNode.owner)
                        : "field type " + reflectorFieldLoadInsn.desc + " != method owner type " + methodInsnNode.owner;
                break;
            }
        }

        String effectiveCallDesc = methodInsnNode.desc;
        List<AbstractInsnNode> insnsToRemoveOnReplacement = null;
        if (effectiveCallDesc.length() > 1 && methodInsnNode.getNext().getOpcode() == CHECKCAST) {
            TypeInsnNode castAfterCallInsn = (TypeInsnNode) methodInsnNode.getNext();
            effectiveCallDesc = Type.getMethodDescriptor(Type.getObjectType(castAfterCallInsn.desc), Type.getArgumentTypes(effectiveCallDesc));

            insnsToRemoveOnReplacement = new ArrayList<>();
            insnsToRemoveOnReplacement.add(castAfterCallInsn);
        }

        //if the method takes an object array as its last argument, it's probably a method call or a constructor. we'll undo the code for putting the arguments
        //  into the array in order to pass them individually, and avoid boxing them if possible.
        String spreadMethodDesc = effectiveCallDesc;
        List<AbstractInsnNode> insnsToRemoveIfSpread = null;
        if (BytecodeHelper.isConstant(reflectorFieldLoadInsn.getNext())) {
            AbstractInsnNode arrayLengthNode = reflectorFieldLoadInsn.getNext();
            Object arrayLengthConstant = BytecodeHelper.decodeConstant(arrayLengthNode);
            if (arrayLengthConstant instanceof Integer && arrayLengthNode.getNext().getOpcode() == ANEWARRAY) {
                //the first argument after the reflector value is an Object[]

                assert spreadMethodDesc.contains("[Ljava/lang/Object;)");

                int arrayLength = (Integer) arrayLengthConstant;
                AbstractInsnNode newArrayNode = arrayLengthNode.getNext();

                insnsToRemoveIfSpread = new ArrayList<>();
                insnsToRemoveIfSpread.add(arrayLengthNode);
                insnsToRemoveIfSpread.add(newArrayNode);

                StringBuilder descBuilder = new StringBuilder();

                AbstractInsnNode node = newArrayNode;
                for (int i = 0; i < arrayLength; i++) {
                    AbstractInsnNode dupNode = node = node.getNext();
                    assert dupNode.getOpcode() == DUP;

                    AbstractInsnNode putIndexConstantNode = node = node.getNext();
                    assert Integer.valueOf(i).equals(BytecodeHelper.decodeConstant(putIndexConstantNode));

                    do {
                        node = node.getNext();
                    } while (node.getOpcode()
                             != AASTORE); //technically there could be some weirdness if someone's passing an array to a method, but i don't think that'll actually happen

                    boolean appendedDesc = false;
                    if (node.getPrevious().getOpcode() == INVOKESTATIC) {
                        MethodInsnNode invokeStaticNode = (MethodInsnNode) node.getPrevious();
                        if ("valueOf".equals(invokeStaticNode.name)) {
                            switch (invokeStaticNode.owner) {
                                case "java/lang/Boolean":
                                    descBuilder.append('Z');
                                    appendedDesc = true;
                                    break;
                                case "java/lang/Integer":
                                    descBuilder.append('I');
                                    appendedDesc = true;
                                    break;
                                case "java/lang/Long":
                                    descBuilder.append('L');
                                    appendedDesc = true;
                                    break;
                                case "java/lang/Float":
                                    descBuilder.append('F');
                                    appendedDesc = true;
                                    break;
                                case "java/lang/Double":
                                    descBuilder.append('D');
                                    appendedDesc = true;
                                    break;
                            }
                            if (appendedDesc) {
                                insnsToRemoveIfSpread.add(invokeStaticNode);
                            }
                        }
                    }
                    if (!appendedDesc) {
                        descBuilder.append("Ljava/lang/Object;");
                    }

                    insnsToRemoveIfSpread.add(dupNode);
                    insnsToRemoveIfSpread.add(putIndexConstantNode);
                    insnsToRemoveIfSpread.add(node);
                }

                spreadMethodDesc = spreadMethodDesc.replace("[Ljava/lang/Object;)", descBuilder.append(')').toString());
            }
        }
        boolean shouldSpread = false;

        AbstractInsnNode replacementInsn = null;

        switch (methodInsnNode.owner) {
            default:
                return 0;
            case "net/optifine/reflect/Reflector":
                //this is disabled since it's not easy to test and isn't even remotely performance-critical
                /*if (methodInsnNode.name.startsWith("setFieldValue")) {
                    replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc.replace("Lnet/optifine/reflect/ReflectorField;", ""),
                            new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_setFieldValue",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                            Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                            reflectorFieldLoadInsn.name,
                            Type.getType(reflectorFieldLoadInsn.desc),
                            new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorField", "getTargetField", "()Ljava/lang/reflect/Field;", false));
                }*/

                if (methodInsnNode.name.startsWith("getFieldValue")) {
                    if (effectiveCallDesc.contains("Lnet/optifine/reflect/ReflectorFields;")) {
                        assert methodInsnNode.desc.endsWith("Lnet/optifine/reflect/ReflectorFields;I)Ljava/lang/Object;") : methodInsnNode.desc;

                        AbstractInsnNode prev = methodInsnNode.getPrevious();
                        if (!BytecodeHelper.isConstant(prev)) {
                            //we can only optimize this away if the index is constant, if it isn't we'll just leave the normal reflective lookup in there
                            PPatchesMod.LOGGER.warn("{}#{}: non-constant index in call to {} {}.{}{}", classNode.name, methodNode.name, Printer.OPCODES[methodInsnNode.getOpcode()], methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                            return 0;
                        }

                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc.replace("Lnet/optifine/reflect/ReflectorFields;I)", ")"),
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_getFieldValue",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;I)Ljava/lang/invoke/CallSite;", false),
                                Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                reflectorFieldLoadInsn.name,
                                Type.getType(reflectorFieldLoadInsn.desc),
                                new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorField", "getTargetField", "()Ljava/lang/reflect/Field;", false),
                                new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorFields", "getReflectorField", "(I)Lnet/optifine/reflect/ReflectorField;", false),
                                (Integer) BytecodeHelper.decodeConstant(prev));

                        //remove the constant index
                        methodNode.instructions.remove(prev);
                    } else {
                        //a normal field accessor
                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc.replace("Lnet/optifine/reflect/ReflectorField;", ""),
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_getFieldValue",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                reflectorFieldLoadInsn.name,
                                Type.getType(reflectorFieldLoadInsn.desc),
                                new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorField", "getTargetField", "()Ljava/lang/reflect/Field;", false));
                    }
                } else if (methodInsnNode.name.startsWith("call")) {
                    shouldSpread = true;
                    replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, spreadMethodDesc.replace("Lnet/optifine/reflect/ReflectorMethod;", ""),
                            new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_call",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                            Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                            reflectorFieldLoadInsn.name,
                            Type.getType(reflectorFieldLoadInsn.desc),
                            new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorMethod", "getTargetMethod", "()Ljava/lang/reflect/Method;", false));
                } else if ("newInstance".equals(methodInsnNode.name)) {
                    shouldSpread = true;
                    replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, spreadMethodDesc.replace("Lnet/optifine/reflect/ReflectorConstructor;", ""),
                            new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_newInstance",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                            Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                            reflectorFieldLoadInsn.name,
                            Type.getType(reflectorFieldLoadInsn.desc),
                            new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorConstructor", "getTargetConstructor", "()Ljava/lang/reflect/Constructor;", false));
                } else if ("postForgeBusEvent".equals(methodInsnNode.name)) {
                    Handle eventBusPostMethod = new Handle(H_INVOKEVIRTUAL, "net/minecraftforge/fml/common/eventhandler/EventBus", "post", "(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", false);
                    if ("(Ljava/lang/Object;)Z".equals(effectiveCallDesc)) {
                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc,
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_postForgeBusEvent",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                eventBusPostMethod);
                    } else {
                        shouldSpread = true;
                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, spreadMethodDesc.replace("Lnet/optifine/reflect/ReflectorConstructor;", ""),
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_postForgeBusEvent",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                reflectorFieldLoadInsn.name,
                                Type.getType(reflectorFieldLoadInsn.desc),
                                new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorConstructor", "getTargetConstructor", "()Ljava/lang/reflect/Constructor;", false),
                                eventBusPostMethod);
                    }
                }
                break;
            case "net/optifine/reflect/ReflectorClass":
                switch (methodInsnNode.name) {
                    case "isInstance":
                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc,
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrapClass_isInstance",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                reflectorFieldLoadInsn.name,
                                Type.getType(reflectorFieldLoadInsn.desc),
                                new Handle(H_INVOKEVIRTUAL, methodInsnNode.owner, "getTargetClass", "()Ljava/lang/Class;", false));
                        break;
                }
                break;
            case "net/optifine/reflect/ReflectorField":
                switch (methodInsnNode.name) {
                    case "getValue":
                    case "setValue":
                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc,
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrapField_" + methodInsnNode.name,
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                reflectorFieldLoadInsn.name,
                                Type.getType(reflectorFieldLoadInsn.desc),
                                new Handle(H_INVOKEVIRTUAL, methodInsnNode.owner, "getTargetField", "()Ljava/lang/reflect/Field;", false));
                        break;
                }
                break;
            case "net/optifine/reflect/ReflectorMethod":
            case "net/optifine/reflect/ReflectorConstructor":
                break;
        }

        switch (methodInsnNode.name) {
            case "exists":
            case "getTargetClass":
            case "getTargetClassName":
                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, effectiveCallDesc,
                        new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrapSimple",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                        Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                        reflectorFieldLoadInsn.name,
                        Type.getType(reflectorFieldLoadInsn.desc),
                        new Handle(H_INVOKEVIRTUAL, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, false));
                break;
        }

        if (replacementInsn != null) {
            methodNode.instructions.set(methodInsnNode, replacementInsn);
            //the method invocation is being replaced, remove the original GETSTATIC
            //  (this is safe, assuming the InsnList implementation doesn't change)
            methodNode.instructions.remove(reflectorFieldLoadInsn);
            if (insnsToRemoveOnReplacement != null) {
                for (AbstractInsnNode insnToRemove : insnsToRemoveOnReplacement) {
                    methodNode.instructions.remove(insnToRemove);
                }
            }
            if (shouldSpread) {
                for (AbstractInsnNode insnToRemove : insnsToRemoveIfSpread) {
                    methodNode.instructions.remove(insnToRemove);
                }
            }

            return CHANGED;
        }
        return 0;
    }
}
