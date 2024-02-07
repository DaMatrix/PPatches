package net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler;

import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.mixin.IMixinASMEventHandler;
import net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.util.ISpecializedASMEventHandler;
import net.daporkchop.ppatches.util.asm.AnonymousClassWriter;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IGenericEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.ThreadContext;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public abstract class SpecializedASMEventHandler extends ASMEventHandler implements ISpecializedASMEventHandler {
    private final Class<?> exactEventClass;

    public SpecializedASMEventHandler(Object target, Method method, ModContainer owner, boolean isGeneric) throws Exception {
        super(target, method, owner, isGeneric);

        this.exactEventClass = method.getParameterTypes()[0];
    }

    @Override
    public final Class<?> createWrapper(Method callback) {
        return null;
    }

    @Override
    public abstract void invoke(Event event);

    @Override
    public final Class<?> exactEventClass() {
        return this.exactEventClass;
    }

    public static ASMEventHandler specialize(Object target, Method method, ModContainer owner, boolean isGeneric) throws Throwable {
        SubscribeEvent subInfo = method.getAnnotation(SubscribeEvent.class);

        java.lang.reflect.Type filter = null;
        if (isGeneric) {
            java.lang.reflect.Type type = method.getGenericParameterTypes()[0];
            if (type instanceof ParameterizedType) {
                filter = ((ParameterizedType) type).getActualTypeArguments()[0];
            }
        }

        AnonymousClassWriter cw = AnonymousClassWriter.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        String specializedClassName = Type.getInternalName(method.getDeclaringClass()) + "/SpecializedASMEventHandler/" + method.getName() + '$' + BytecodeHelper.mangleSignature(Type.getMethodDescriptor(method));
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, specializedClassName, null, Type.getInternalName(SpecializedASMEventHandler.class), null);

        { //constructor
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class), Type.getType(Method.class), Type.getType(ModContainer.class), Type.BOOLEAN_TYPE), null, null);
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitVarInsn(ILOAD, 4);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(SpecializedASMEventHandler.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class), Type.getType(Method.class), Type.getType(ModContainer.class), Type.BOOLEAN_TYPE), false);

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        Class<?> eventClass = method.getParameterTypes()[0];

        { //static void invokeSpecialized(eventClass)
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "invokeSpecialized", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(eventClass)), null, null);
            mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true).visitEnd();
            mv.visitCode();

            if (IMixinASMEventHandler.getGETCONTEXT()) {
                mv.visitLdcInsn("mod");
                mv.visitLdcInsn(owner == null ? "" : owner.getName());
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(ThreadContext.class), "put", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(String.class)), ThreadContext.class.isInterface());
            }

            Label tailLbl = new Label();

            if (!subInfo.receiveCanceled()) {
                //if subInfo.receiveCanceled() is false, we'll need to manually check to see if the event was cancelled before invoking the handler

                Label proceedLbl = new Label();

                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(eventClass), "isCancelable", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
                mv.visitJumpInsn(IFEQ, proceedLbl);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(eventClass), "isCanceled", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
                mv.visitJumpInsn(IFNE, tailLbl);

                mv.visitLabel(proceedLbl);
            }

            if (filter != null) {
                //this is a generic event, check if the type matches
                cw.addConstant(mv, filter, Type.getInternalName(java.lang.reflect.Type.class));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(IGenericEvent.class));
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(IGenericEvent.class), "getGenericType", Type.getMethodDescriptor(Type.getType(java.lang.reflect.Type.class)), true);
                mv.visitJumpInsn(IF_ACMPNE, tailLbl);
            }

            if (!Modifier.isStatic(method.getModifiers())) {
                cw.addConstant(mv, target, Type.getInternalName(method.getDeclaringClass()));
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(Modifier.isStatic(method.getModifiers()) ? INVOKESTATIC : INVOKEVIRTUAL, Type.getInternalName(method.getDeclaringClass()), method.getName(), Type.getMethodDescriptor(method), method.getDeclaringClass().isInterface());

            mv.visitLabel(tailLbl);

            if (IMixinASMEventHandler.getGETCONTEXT()) {
                mv.visitLdcInsn("mod");
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(ThreadContext.class), "remove", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), ThreadContext.class.isInterface());
            }

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        { //void invoke(Event) [virtual]
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "invoke", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Event.class)), null, null);
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(eventClass));
            mv.visitMethodInsn(INVOKESTATIC, specializedClassName, "invokeSpecialized", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(eventClass)), false);
            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        { //MethodHandle getExactInvoker()
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "getExactInvoker", Type.getMethodDescriptor(Type.getType(MethodHandle.class)), null, null);
            mv.visitCode();

            mv.visitLdcInsn(new Handle(H_INVOKESTATIC, specializedClassName, "invokeSpecialized", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(eventClass)), false));
            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();

        PPatchesTransformerRoot.dumpClass(Type.getInternalName(SpecializedASMEventHandler.class) + '/' + specializedClassName, cw);

        Class<?> clazz = cw.defineAnonymousClass(method.getDeclaringClass());
        MethodHandle ctor = MethodHandles.lookup().findConstructor(clazz, MethodType.methodType(void.class, Object.class, Method.class, ModContainer.class, boolean.class));
        return (ASMEventHandler) ctor.invoke(target, method, owner, isGeneric);
    }
}
