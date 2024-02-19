package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch;

import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.cp.ConstantPoolIndex;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Objects;

import static org.objectweb.asm.Opcodes.*;

/**
 * Transforms event classes to make the per-event listener list field be static final, which will allow it to be optimized as a constant
 * if the entire body of {@link net.minecraftforge.fml.common.eventhandler.EventBus#post(Event)} gets inlined into the caller and the event type
 * can then be proven.
 * <p>
 * This will also make event construction slightly faster since events will no longer need to access the listener list field of all superclasses
 * every time they're constructed.
 *
 * @author DaPorkchop_
 */
public class OptimizeEventBusDispatchTransformer_ListenerList implements ITreeClassTransformer, ITreeClassTransformer.ExactInterested {
    @Override
    public boolean interestedInClass(String name, String transformedName, ClassReader reader, ConstantPoolIndex cpIndex) {
        return cpIndex.referencesMethod("net/minecraftforge/fml/common/eventhandler/ListenerList", "<init>", "(Lnet/minecraftforge/fml/common/eventhandler/ListenerList;)V");
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        try {
            FieldNode listenerListField = BytecodeHelper.findField(classNode, "LISTENER_LIST", Type.getDescriptor(ListenerList.class)).orElse(null);
            MethodNode getListenerListMethod = BytecodeHelper.findMethod(classNode, "getListenerList", Type.getMethodDescriptor(Type.getType(ListenerList.class))).orElse(null);
            MethodNode setupMethod = BytecodeHelper.findMethod(classNode, "setup", Type.getMethodDescriptor(Type.VOID_TYPE)).orElse(null);

            if (listenerListField == null || getListenerListMethod == null || setupMethod == null) {
                return 0;
            }

            listenerListField.access = ACC_PROTECTED | ACC_STATIC | ACC_FINAL;

            classNode.methods.remove(setupMethod);

            InsnList insns = new InsnList();
            insns.add(new TypeInsnNode(NEW, Type.getInternalName(ListenerList.class)));
            insns.add(new InsnNode(DUP));
            if (Type.getInternalName(Event.class).equals(classNode.superName)) {
                insns.add(new TypeInsnNode(NEW, classNode.superName));
                insns.add(new InsnNode(DUP));
                insns.add(new MethodInsnNode(INVOKESPECIAL, classNode.superName, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false));
                insns.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.superName, "getListenerList", Type.getMethodDescriptor(Type.getType(ListenerList.class)), false));
            } else {
                insns.add(new FieldInsnNode(GETSTATIC, classNode.superName, listenerListField.name, listenerListField.desc));
            }
            insns.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Objects.class), "requireNonNull", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class)), false));
            insns.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(ListenerList.class)));
            insns.add(new MethodInsnNode(INVOKESPECIAL, Type.getInternalName(ListenerList.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ListenerList.class)), false));
            insns.add(new FieldInsnNode(PUTSTATIC, classNode.name, listenerListField.name, listenerListField.desc));
            BytecodeHelper.getOrCreateClinit(classNode).instructions.insert(insns);

            return CHANGED_MANDATORY;
        } catch (Throwable t) {
            t.printStackTrace();
            return 0;
        }
    }
}
