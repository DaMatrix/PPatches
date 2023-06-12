package net.daporkchop.ppatches.modules.optifine.optimizeReflector;

import jdk.internal.org.objectweb.asm.util.Printer;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeReflectorTransformer implements ITreeClassTransformer {
    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        if ("net.optifine.reflect.Reflector".equals(transformedName)) {
            //TODO: this.transformReflector(classNode);
            return true;
        }

        //some other class, we want to check for references to reflector so we can get rid of them
        boolean anyChanged = false;
        for (MethodNode methodNode : classNode.methods) {
            OUTER_INSN_LOOP:
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() != INVOKEVIRTUAL && insn.getOpcode() != INVOKESTATIC) {
                    continue;
                }

                MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
                if (!methodInsnNode.owner.startsWith("net/optifine/reflect/Reflector")) {
                    continue;
                }

                FieldInsnNode reflectorFieldLoadInsn;
                for (AbstractInsnNode node = methodInsnNode.getPrevious(); ; node = node.getPrevious()) {
                    if (node == null) {
                        PPatchesMod.LOGGER.warn("{}#{}: couldn't find GETSTATIC corresponding to {} {}.{}{}", classNode.name, methodNode.name, Printer.OPCODES[methodInsnNode.getOpcode()], methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                        continue OUTER_INSN_LOOP;
                    }

                    if (node.getOpcode() == GETSTATIC && ((FieldInsnNode) node).desc.startsWith("net/optifine/reflect/Reflector", 1)) {
                        reflectorFieldLoadInsn = (FieldInsnNode) node;
                        assert methodInsnNode.getOpcode() == INVOKESTATIC || reflectorFieldLoadInsn.desc.substring(1, reflectorFieldLoadInsn.desc.length() - 1).equals(methodInsnNode.owner)
                                : "field type " + reflectorFieldLoadInsn.desc + " != method owner type " + methodInsnNode.owner;
                        break;
                    }
                }

                AbstractInsnNode replacementInsn = null;

                switch (methodInsnNode.owner) {
                    default:
                        continue;
                    case "net/optifine/reflect/Reflector":
                        //this is disabled since it's not easy to test and isn't even remotely performance-critical
                        /*if (methodInsnNode.name.startsWith("setFieldValue")) {
                            replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorField;", ""),
                                    new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_setFieldValue",
                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                    Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                    reflectorFieldLoadInsn.name,
                                    Type.getType(reflectorFieldLoadInsn.desc),
                                    new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorField", "getTargetField", "()Ljava/lang/reflect/Field;", false));
                        }*/

                        if (methodInsnNode.name.startsWith("getFieldValue")) {
                            if (methodInsnNode.desc.contains("Lnet/optifine/reflect/ReflectorFields;")) {
                                assert methodInsnNode.desc.endsWith("Lnet/optifine/reflect/ReflectorFields;I)Ljava/lang/Object;") : methodInsnNode.desc;

                                AbstractInsnNode prev = methodInsnNode.getPrevious();
                                if (!BytecodeHelper.isConstant(prev)) {
                                    //we can only optimize this away if the index is constant, if it isn't we'll just leave the normal reflective lookup in there
                                    PPatchesMod.LOGGER.warn("{}#{}: non-constant index in call to {} {}.{}{}", classNode.name, methodNode.name, Printer.OPCODES[methodInsnNode.getOpcode()], methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                                    continue;
                                }

                                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorFields;I)", ")"),
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
                                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorField;", ""),
                                        new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_getFieldValue",
                                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                        Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                        reflectorFieldLoadInsn.name,
                                        Type.getType(reflectorFieldLoadInsn.desc),
                                        new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorField", "getTargetField", "()Ljava/lang/reflect/Field;", false));
                            }
                        } else if (methodInsnNode.name.startsWith("call")) {
                            replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorMethod;", ""),
                                    new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_call",
                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                    Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                    reflectorFieldLoadInsn.name,
                                    Type.getType(reflectorFieldLoadInsn.desc),
                                    new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorMethod", "getTargetMethod", "()Ljava/lang/reflect/Method;", false));
                        } else if ("newInstance".equals(methodInsnNode.name)) {
                            replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorConstructor;", ""),
                                    new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_newInstance",
                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                    Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                    reflectorFieldLoadInsn.name,
                                    Type.getType(reflectorFieldLoadInsn.desc),
                                    new Handle(H_INVOKEVIRTUAL, "net/optifine/reflect/ReflectorConstructor", "getTargetConstructor", "()Ljava/lang/reflect/Constructor;", false));
                        } else if ("postForgeBusEvent".equals(methodInsnNode.name)) {
                            Handle eventBusPostMethod = new Handle(H_INVOKEVIRTUAL, "net/minecraftforge/fml/common/eventhandler/EventBus", "post", "(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", false);
                            if ("(Ljava/lang/Object;)Z".equals(methodInsnNode.desc)) {
                                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorConstructor;", ""),
                                        new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrap_postForgeBusEvent",
                                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                        eventBusPostMethod);
                            } else {
                                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc.replace("Lnet/optifine/reflect/ReflectorConstructor;", ""),
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
                                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc,
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
                                replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc,
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
                        replacementInsn = new InvokeDynamicInsnNode(methodInsnNode.name, methodInsnNode.desc,
                                new Handle(H_INVOKESTATIC, Type.getInternalName(OptimizeReflectorBootstrap.class), "bootstrapSimple",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;", false),
                                Type.getType('L' + reflectorFieldLoadInsn.owner + ';'),
                                reflectorFieldLoadInsn.name,
                                Type.getType(reflectorFieldLoadInsn.desc),
                                new Handle(H_INVOKEVIRTUAL, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, false));
                        break;
                }

                if (replacementInsn != null) {
                    itr.set(replacementInsn);
                    //the method invocation is being replaced, remove the original GETSTATIC
                    //  (this is safe, assuming the InsnList implementation doesn't change)
                    methodNode.instructions.remove(reflectorFieldLoadInsn);

                    anyChanged = true;
                }
            }
        }
        return anyChanged;
    }

    private void transformReflector(ClassNode classNode) {
        List<MethodNode> newMethods = new ArrayList<>();

        for (MethodNode method : classNode.methods) {
            if (!"<clinit>".equals(method.name)) {
                continue;
            }

            List<AbstractInsnNode> toRemove = new ArrayList<>();
            for (ListIterator<AbstractInsnNode> itr = method.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode newInsn = itr.next();
                if (newInsn.getOpcode() == NEW) {
                    String reflectorType = ((TypeInsnNode) newInsn).desc;

                    AbstractInsnNode dupInsn = itr.next();
                    if (dupInsn.getOpcode() != DUP) {
                        throw new IllegalStateException();
                    }

                    FieldInsnNode putstaticInsn;
                    boolean lazyResolve;
                    boolean assumeExists;

                    boolean isClass = false;
                    boolean isField = false;
                    boolean isMethod = false;

                    switch (reflectorType) {
                        default:
                            continue;
                        case "net/optifine/reflect/ReflectorClass": {
                            isClass = true;

                            Object classNameOrType = ((LdcInsnNode) itr.next()).cst;
                            if (!(classNameOrType instanceof String) && !(classNameOrType instanceof Type)) {
                                throw new IllegalStateException();
                            }

                            assumeExists = classNameOrType instanceof Type;

                            AbstractInsnNode ctorOrLazyResolveInsn = itr.next();
                            lazyResolve = false;
                            switch (ctorOrLazyResolveInsn.getOpcode()) {
                                case ICONST_1:
                                    lazyResolve = true;
                                    ctorOrLazyResolveInsn = itr.next();
                                    break;
                                case INVOKESPECIAL:
                                    break;
                                default:
                                    throw new IllegalStateException(String.valueOf(ctorOrLazyResolveInsn.getOpcode()));
                            }

                            MethodInsnNode ctorInsn = (MethodInsnNode) ctorOrLazyResolveInsn;
                            if (ctorInsn.getOpcode() != INVOKESPECIAL) {
                                throw new IllegalStateException();
                            }

                            putstaticInsn = (FieldInsnNode) ctorInsn.getNext();
                            break;
                        }
                        case "net/optifine/reflect/ReflectorField":
                            isField = true;
                        case "net/optifine/reflect/ReflectorMethod":
                            isMethod = !isField;

                            while (true) {
                                AbstractInsnNode possibleCtorInsn = itr.next();
                                if (possibleCtorInsn.getOpcode() == INVOKESPECIAL && reflectorType.equals(((MethodInsnNode) possibleCtorInsn).owner)) {
                                    MethodInsnNode ctorInsn = (MethodInsnNode) possibleCtorInsn;

                                    lazyResolve = ctorInsn.desc.endsWith("Z)V") && ctorInsn.getPrevious().getOpcode() == ICONST_1;
                                    assumeExists = "(Ljava/lang/reflect/Field;)V".equals(ctorInsn.desc); //this will obviously always be false for ReflectorMethod

                                    putstaticInsn = (FieldInsnNode) ctorInsn.getNext();
                                    break;
                                }
                            }
                            break;
                    }

                    if (lazyResolve && assumeExists) {
                        throw new IllegalStateException();
                    }

                    //we assume that itr.next() == putstaticInsn;

                    if (false && isClass) {
                        MethodNode getReflectorMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, putstaticInsn.name + "__reflector", "()L" + reflectorType + ';', null, null);
                        getReflectorMethod.visitCode();
                        getReflectorMethod.visitFieldInsn(GETSTATIC, "net/optifine/reflect/Reflector", putstaticInsn.name, putstaticInsn.desc);
                        getReflectorMethod.visitInsn(ARETURN);
                        newMethods.add(getReflectorMethod);
                    }

                    { //generate __exists methods
                        MethodNode existsMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, putstaticInsn.name + "__exists", "()Z", null, null);
                        existsMethod.visitCode();
                        if (assumeExists) {
                            existsMethod.visitLdcInsn(1);
                        } else if (!lazyResolve) {
                            classNode.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, existsMethod.name, "Z", null, null);
                            existsMethod.visitFieldInsn(GETSTATIC, "net/optifine/reflect/Reflector", existsMethod.name, "Z");

                            //precompute exists() and store into field
                            itr.add(new InsnNode(DUP));
                            itr.add(new MethodInsnNode(INVOKEVIRTUAL, reflectorType, "exists", "()Z", false));
                            itr.add(new FieldInsnNode(PUTSTATIC, "net/optifine/reflect/Reflector", existsMethod.name, "Z"));
                        } else { //delegate to the actual reflector value
                            existsMethod.visitFieldInsn(GETSTATIC, "net/optifine/reflect/Reflector", putstaticInsn.name, putstaticInsn.desc);
                            existsMethod.visitMethodInsn(INVOKEVIRTUAL, reflectorType, "exists", "()Z", false);
                        }
                        existsMethod.visitInsn(IRETURN);
                        newMethods.add(existsMethod);
                    }

                    if (false && isField) { //generate MethodHandle stuff for getters and setters
                        classNode.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, putstaticInsn.name + "__get", "Ljava/lang/invoke/MethodHandle;", null, null);
                        classNode.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, putstaticInsn.name + "__set", "Ljava/lang/invoke/MethodHandle;", null, null);

                        if (assumeExists || !lazyResolve) {
                            //precompute exists() and store into field
                            itr.add(new InsnNode(DUP));
                            itr.add(new MethodInsnNode(INVOKEVIRTUAL, reflectorType, "getTargetField", "()Ljava/lang/reflect/Field;", false));
                            itr.add(new InsnNode(DUP));

                            itr.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodHandles", "publicLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false));
                            itr.add(new InsnNode(DUP_X2));
                            itr.add(new InsnNode(DUP_X1));
                            itr.add(new InsnNode(POP));

                            itr.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectGetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false));
                            itr.add(new InsnNode(DUP_X2));
                            itr.add(new InsnNode(POP));
                            itr.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectSetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false));
                        } else { //delegate to the actual reflector value
                            itr.add(new InsnNode(ACONST_NULL));
                            itr.add(new InsnNode(ACONST_NULL));
                        }

                        itr.add(new FieldInsnNode(PUTSTATIC, "net/optifine/reflect/Reflector", putstaticInsn.name + "__set", "Ljava/lang/invoke/MethodHandle;"));
                        itr.add(new FieldInsnNode(PUTSTATIC, "net/optifine/reflect/Reflector", putstaticInsn.name + "__get", "Ljava/lang/invoke/MethodHandle;"));
                    }
                }
            }

            for (AbstractInsnNode remove : toRemove) {
                method.instructions.remove(remove);
            }
            break;
        }

        classNode.methods.addAll(newMethods);

        /*{
            MethodVisitor mv = classNode.visitMethod(ACC_PUBLIC | ACC_STATIC, "existsCallSite", "(Lnet/optifine/reflect/ReflectorClass;)Ljava/lang/invoke/CallSite;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/optifine/reflect/ReflectorClass", "existsCallSite", "()Ljava/lang/invoke/CallSite;", false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }*/
    }
}
