package net.daporkchop.ppatches.modules.mixin.optimizeCallbackInfoAllocation;

import com.google.common.collect.ImmutableMap;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeCallbackInfoAllocationTransformer implements ITreeClassTransformer {
    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        boolean anyChanged = false;
        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                if (insn.getOpcode() == INVOKESPECIAL) {
                    MethodInsnNode ctorInsn = (MethodInsnNode) insn;
                    switch (ctorInsn.owner) {
                        default:
                            continue;
                        case "org/spongepowered/asm/mixin/injection/callback/CallbackInfo":
                        case "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable":
                            if (!"<init>".equals(ctorInsn.name) || !"(Ljava/lang/String;Z)V".equals(ctorInsn.desc)) {
                                continue;
                            }
                    }

                    //body moved to separate method to help JIT optimize the main loop, which is supposed to be fast
                    anyChanged |= transformCtor(classNode, methodNode, ctorInsn, itr);
                }
            }
        }
        return anyChanged;
    }

    private static boolean transformCtor(ClassNode classNode, MethodNode methodNode, MethodInsnNode ctorInsn, ListIterator<AbstractInsnNode> itr) {
        AbstractInsnNode loadCancellableValueInsn = ctorInsn.getPrevious();
        switch (loadCancellableValueInsn.getOpcode()) {
            case ICONST_0: //cancellable = false, we can optimize this occurrence
                break;
            case ICONST_1: //cancellable = true, this occurrence can't be optimized (yet)
                return false;
            default:
                PPatchesMod.LOGGER.warn("at {}.{}{}: call to {}.{}{} has an unexpected argument {} for 'cancellable' skipping...", classNode.name, methodNode.name, methodNode.desc, ctorInsn.owner, ctorInsn.name, ctorInsn.desc, Printer.OPCODES[loadCancellableValueInsn.getOpcode()]);
                return false;
        }

        LdcInsnNode loadIdInsn = (LdcInsnNode) loadCancellableValueInsn.getPrevious();
        String id = (String) loadIdInsn.cst;

        AbstractInsnNode dupInsn = loadIdInsn.getPrevious();
        assert dupInsn.getOpcode() == DUP : "expected " + Printer.OPCODES[DUP] + ", found " + Printer.OPCODES[dupInsn.getOpcode()];

        AbstractInsnNode newInsn = dupInsn.getPrevious();
        assert newInsn.getOpcode() == NEW : "expected " + Printer.OPCODES[NEW] + ", found " + Printer.OPCODES[newInsn.getOpcode()];

        methodNode.instructions.remove(newInsn);
        methodNode.instructions.remove(dupInsn);
        methodNode.instructions.remove(loadIdInsn);
        methodNode.instructions.remove(loadCancellableValueInsn);
        itr.set(new InvokeDynamicInsnNode("constantCallbackInfoInstance", "()L" + ctorInsn.owner + ';',
                new Handle(H_INVOKESTATIC,
                        "net/daporkchop/ppatches/modules/mixin/optimizeCallbackInfoAllocation/OptimizeCallbackInfoAllocationTransformer",
                        "bootstrap",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false),
                new Handle(H_NEWINVOKESPECIAL, ctorInsn.owner, ctorInsn.name, ctorInsn.desc, false),
                id));
        return true;
    }

    private static final Map<Class<?>, Map<String, CallSite>> CALL_SITES_BY_TYPE = ImmutableMap.of(CallbackInfo.class, new TreeMap<>(), CallbackInfoReturnable.class, new TreeMap<>());

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle ctor, String id) throws Throwable {
        Map<String, CallSite> callSitesByName = CALL_SITES_BY_TYPE.get(type.returnType());
        assert callSitesByName != null : "unknown CallbackInfo type: " + type.returnType().getTypeName();

        synchronized (callSitesByName) {
            CallSite callSite = callSitesByName.get(id);
            if (callSite == null) {
                callSite = new ConstantCallSite(MethodHandles.constant(type.returnType(), (CallbackInfo) ctor.invoke(id, false)));
                callSitesByName.put(id, callSite);
            }
            return callSite;
        }
    }
}
