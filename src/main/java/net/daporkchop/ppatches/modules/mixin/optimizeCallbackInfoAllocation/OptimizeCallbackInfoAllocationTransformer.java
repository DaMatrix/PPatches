package net.daporkchop.ppatches.modules.mixin.optimizeCallbackInfoAllocation;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Printer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.*;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class OptimizeCallbackInfoAllocationTransformer implements ITreeClassTransformer {
    private static String callbackInfoInternalName(boolean callbackInfoIsReturnable) {
        return callbackInfoIsReturnable
                ? "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"
                : "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    }

    private static String callbackInfoTypeDesc(boolean callbackInfoIsReturnable) {
        return callbackInfoIsReturnable
                ? "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"
                : "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    }

    @RequiredArgsConstructor
    private static class CallbackInfoCreation {
        public final MethodNode creatingMethod;
        public final TypeInsnNode newInsn;

        public final List<AbstractInsnNode> creationInsns;
        public final Optional<VarInsnNode> storeToLvtInsn;

        public final String id;
        public final boolean cancellable;

        public final boolean callbackInfoIsReturnable;
        public final boolean capturesReturnValue;

        public final List<CallbackInvocation> consumedByInvocations = new ArrayList<>();

        public void removeInvocation(CallbackInvocation invocation) {
            PPatchesMod.LOGGER.info("removed {} from {}", invocation, this);

            if (!this.consumedByInvocations.remove(invocation)) {
                throw new IllegalStateException();
            } else if (this.consumedByInvocations.isEmpty()) {
                for (AbstractInsnNode insn : this.creationInsns) {
                    this.creatingMethod.instructions.remove(insn);
                }

                if (this.storeToLvtInsn.isPresent()) {
                    this.creatingMethod.instructions.remove(this.storeToLvtInsn.get());

                    //remove the corresponding local variable
                    for (Iterator<LocalVariableNode> itr = this.creatingMethod.localVariables.iterator(); itr.hasNext(); ) {
                        if (itr.next().index == this.storeToLvtInsn.get().var) {
                            itr.remove();
                            break;
                        }
                    }
                }

            }
        }
    }

    @RequiredArgsConstructor
    private static class CallbackInvocation {
        public final MethodNode callingMethod;
        public final CallbackInfoCreation callbackInfoCreation;

        public final Optional<VarInsnNode> loadCallbackInfoFromLvtInsn;
        public final MethodInsnNode invokeCallbackMethodInsn;
        public final List<AbstractInsnNode> checkCancelledInsns;
    }

    @RequiredArgsConstructor
    private static class CallbackMethod {
        public final MethodNode callbackMethod;
        public final int callbackInfoLvtIndex;
        public final boolean callbackInfoIsReturnable;

        public boolean callsGetId;
        public boolean callsIsCancellable;
        public boolean callsIsCancelled;

        public boolean callsCancel;
        public boolean alwaysCallsCancel;

        public boolean callsGetReturnValue;
        public boolean callsSetReturnValue;
        public boolean alwaysCallsSetReturnValue;

        public boolean usesCallbackInfoInstanceInUnknownWay;
        public boolean forwardsCallbackInfoInstance;
        public boolean replacesCallbackInfoInstance;

        public final List<CallbackInvocation> usedByInvocations = new ArrayList<>();

        public String callbackInfoInternalName() {
            return OptimizeCallbackInfoAllocationTransformer.callbackInfoInternalName(this.callbackInfoIsReturnable);
        }

        public String callbackInfoTypeDesc() {
            return OptimizeCallbackInfoAllocationTransformer.callbackInfoTypeDesc(this.callbackInfoIsReturnable);
        }

        public boolean usesCallbackInfoInstanceAtAll() {
            return this.callsGetId | this.callsIsCancellable | this.callsIsCancelled | this.callsCancel
                    | this.callsGetReturnValue | this.callsSetReturnValue
                    | this.usesCallbackInfoInstanceInUnknownWay
                    | this.forwardsCallbackInfoInstance | this.replacesCallbackInfoInstance;
        }
    }

    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        List<CallbackInfoCreation> callbackInfoCreations = null;
        Int2ObjectMap<CallbackInfoCreation> currentCallbackCreationsByLvt = null;
        List<CallbackInvocation> callbackInvocations = null;
        Map<MethodNode, CallbackMethod> callbackMethods = null;

        //first pass: scan for CallbackInfo allocations, then trace them to the invocations they're used by and
        //  finally the actual callback methods
        for (MethodNode methodNode : classNode.methods) {
            if (currentCallbackCreationsByLvt != null) { //clear this as it's only valid for the current method
                currentCallbackCreationsByLvt.clear();
            }

            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                switch (insn.getOpcode()) {
                    case NEW: {
                        TypeInsnNode newInsn = (TypeInsnNode) insn;
                        switch (newInsn.desc) {
                            default: //we don't care about these
                                continue;
                            case "org/spongepowered/asm/mixin/injection/callback/CallbackInfo":
                            case "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable":
                                break;
                        }

                        if (callbackInfoCreations == null) { //create local datastructures now that we know there'll be a hit
                            callbackInfoCreations = new ArrayList<>();
                            currentCallbackCreationsByLvt = new Int2ObjectArrayMap<>(); //won't be very big, a hash table would be overkill
                            callbackInvocations = new ArrayList<>();
                            callbackMethods = new IdentityHashMap<>();
                        }

                        CallbackInfoCreation callbackInfoCreation = wrapCallbackInfoCreation(classNode, methodNode, newInsn);
                        callbackInfoCreations.add(callbackInfoCreation);
                        if (callbackInfoCreation.storeToLvtInsn.isPresent() && currentCallbackCreationsByLvt.putIfAbsent(callbackInfoCreation.storeToLvtInsn.get().var, callbackInfoCreation) != null) {
                            throw new IllegalStateException(newInsn.desc + " LVT slot was reassigned?!?");
                        }
                        break;
                    }
                    case INVOKEVIRTUAL: //used for non-private callback methods
                    case INVOKESPECIAL: //used for private callback methods
                    case INVOKESTATIC: { //used for static callback methods, regardless of access (duh)
                        MethodInsnNode invokeCallbackInsn = (MethodInsnNode) insn;
                        if (!classNode.name.equals(invokeCallbackInsn.owner) || !invokeCallbackInsn.desc.contains("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")) {
                            continue;
                        }

                        if (callbackInfoCreations == null) { //create local datastructures now that we know there'll be a hit
                            callbackInfoCreations = new ArrayList<>();
                            currentCallbackCreationsByLvt = new Int2ObjectArrayMap<>(); //won't be very big, a hash table would be overkill
                            callbackInvocations = new ArrayList<>();
                            callbackMethods = new IdentityHashMap<>();
                        }

                        CallbackInvocation invocation = wrapCallbackInvocation(classNode, methodNode, invokeCallbackInsn, callbackInfoCreations, currentCallbackCreationsByLvt);
                        if (invocation != null) {
                            callbackInvocations.add(invocation);

                            MethodNode callbackMethodNode = BytecodeHelper.findMethodOrThrow(classNode, invokeCallbackInsn.name, invokeCallbackInsn.desc);
                            CallbackMethod callbackMethod = callbackMethods.get(callbackMethodNode);
                            if (callbackMethod == null) {
                                callbackMethods.put(callbackMethodNode, callbackMethod = wrapCallbackMethod(classNode, callbackMethodNode));
                            }
                            callbackMethod.usedByInvocations.add(invocation);
                        }
                        break;
                    }
                }
            }
        }

        if (callbackInfoCreations == null) { //no CallbackInfos were created in this class
            return false;
        }

        return doFinalTransform(classNode, callbackInfoCreations, callbackInvocations, callbackMethods);
    }

    private static boolean transformCtor(ClassNode classNode, MethodNode methodNode, MethodInsnNode ctorInsn, ListIterator<AbstractInsnNode> itr, boolean captureReturnValue) {
        CALLBACK_METHOD_SEARCH:
        for (AbstractInsnNode invokeCallbackInsn = ctorInsn.getNext(); ; invokeCallbackInsn = invokeCallbackInsn.getNext()) {
            switch (invokeCallbackInsn.getOpcode()) {
                default:
                    throw new IllegalStateException(Printer.OPCODES[invokeCallbackInsn.getOpcode()]);
                case ASTORE: //TODO: do something when callbackinfos are stored in a local variable
                    break CALLBACK_METHOD_SEARCH;
                case ALOAD:
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                    continue;
                case INVOKESPECIAL:
                    MethodInsnNode methodInsnNode = (MethodInsnNode) invokeCallbackInsn;
                    assert methodInsnNode.owner.equals(classNode.name);
                    CallbackMethod wrapper = wrapCallbackMethod(classNode, BytecodeHelper.findMethodOrThrow(classNode, methodInsnNode.name, methodInsnNode.desc));
                    break CALLBACK_METHOD_SEARCH;
            }
        }

        AbstractInsnNode insn = ctorInsn;

        VarInsnNode loadReturnValueInsn = null;
        if (captureReturnValue) {
            loadReturnValueInsn = (VarInsnNode) (insn = insn.getPrevious());
        }

        AbstractInsnNode loadCancellableValueInsn = insn.getPrevious();
        boolean cancellable;
        switch (loadCancellableValueInsn.getOpcode()) {
            case ICONST_0: //cancellable = false, we can optimize this occurrence
                cancellable = false;
                break;
            case ICONST_1: //cancellable = true, this occurrence can't be optimized (yet)
                cancellable = true;
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
                        "bootstrapSimple",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false),
                new Handle(H_NEWINVOKESPECIAL, ctorInsn.owner, ctorInsn.name, ctorInsn.desc, false),
                id));
        return true;
    }

    private static CallbackInfoCreation wrapCallbackInfoCreation(ClassNode classNode, MethodNode creatingMethod, TypeInsnNode newInsn) {
        boolean callbackInfoIsReturnable;
        switch (newInsn.desc) {
            case "org/spongepowered/asm/mixin/injection/callback/CallbackInfo":
                callbackInfoIsReturnable = false;
                break;
            case "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable":
                callbackInfoIsReturnable = true;
                break;
            default:
                throw new IllegalArgumentException(newInsn.desc);
        }

        List<AbstractInsnNode> creationInsns = new ArrayList<>();
        creationInsns.add(newInsn);

        AbstractInsnNode currentInsn = newInsn.getNext();

        Preconditions.checkState(currentInsn.getOpcode() == DUP, "expected %s, got %s", Printer.OPCODES[DUP], Printer.OPCODES[currentInsn.getOpcode()]);
        creationInsns.add(currentInsn);

        currentInsn = currentInsn.getNext();
        Preconditions.checkState(currentInsn.getOpcode() == LDC, "expected %s, got %s", Printer.OPCODES[LDC], Printer.OPCODES[currentInsn.getOpcode()]);
        creationInsns.add(currentInsn);
        String id = (String) ((LdcInsnNode) currentInsn).cst;

        currentInsn = currentInsn.getNext();
        Preconditions.checkState(currentInsn.getOpcode() == ICONST_0 || currentInsn.getOpcode() == ICONST_1, "expected %s or %s, got %s", Printer.OPCODES[ICONST_0], Printer.OPCODES[ICONST_1], Printer.OPCODES[currentInsn.getOpcode()]);
        creationInsns.add(currentInsn);
        boolean cancellable = currentInsn.getOpcode() == ICONST_1;

        currentInsn = currentInsn.getNext();

        boolean capturesReturnValue = false;
        if (callbackInfoIsReturnable && currentInsn.getOpcode() == Type.getReturnType(creatingMethod.desc).getOpcode(ILOAD)) {
            //the return value is being captured into the CallbackInfoReturnable instance
            capturesReturnValue = true;

            creationInsns.add(currentInsn);
            currentInsn = currentInsn.getNext();
        }

        Preconditions.checkState(currentInsn.getOpcode() == INVOKESPECIAL, "expected %s, got %s", Printer.OPCODES[INVOKESPECIAL], Printer.OPCODES[currentInsn.getOpcode()]);
        MethodInsnNode ctorInsn = (MethodInsnNode) currentInsn;
        String expectedCtorOwner = callbackInfoInternalName(callbackInfoIsReturnable);
        String expectedCtorName = "<init>";
        String expectedCtorDesc = "(Ljava/lang/String;Z" + (capturesReturnValue ? Type.getReturnType(creatingMethod.desc).getDescriptor() : "") + ")V";
        Preconditions.checkState(expectedCtorOwner.equals(ctorInsn.owner) && expectedCtorName.equals(ctorInsn.name) && expectedCtorDesc.equals(ctorInsn.desc),
                "expected call to L%s;%s%s, found L%s;%s%s", expectedCtorOwner, expectedCtorName, expectedCtorDesc, ctorInsn.owner, ctorInsn.name, ctorInsn.desc);
        creationInsns.add(currentInsn);

        currentInsn = currentInsn.getNext();

        VarInsnNode storeToLvtInsn = null;
        if (currentInsn.getOpcode() == ASTORE) { //the newly created CallbackInfo instance is being stored in a local variable
            storeToLvtInsn = (VarInsnNode) currentInsn;
        }

        return new CallbackInfoCreation(creatingMethod, newInsn, creationInsns, Optional.ofNullable(storeToLvtInsn), id, cancellable, callbackInfoIsReturnable, capturesReturnValue);
    }

    private static CallbackInvocation wrapCallbackInvocation(ClassNode classNode, MethodNode callingMethod, MethodInsnNode callInsn, List<CallbackInfoCreation> callbackInfoCreations, Int2ObjectMap<CallbackInfoCreation> currentCallbackCreationsByLvt) {
        assert callInsn.owner.equals(classNode.name); //checked above in main transformer loop

        Type callbackMethodDescType = Type.getMethodType(callInsn.desc);
        Type callbackMethodReturnType = callbackMethodDescType.getReturnType();
        Type[] callbackMethodArgumentTypes = callbackMethodDescType.getArgumentTypes();

        if (callbackMethodReturnType.getSort() != Type.VOID) {
            PPatchesMod.LOGGER.warn("non-void return type {} on mixin injector callback method L{};{}{}", callbackMethodReturnType, callInsn.owner, callInsn.name, callInsn.desc);
            return null;
        } else if (BytecodeHelper.isStatic(callingMethod) != (callInsn.getOpcode() == INVOKESTATIC)) {
            PPatchesMod.LOGGER.warn("invalid static-ness when L{};{}{} tries to invoke mixin callback method L{};{}{}", classNode.name, callingMethod.name, callingMethod.desc, callInsn.owner, callInsn.name, callInsn.desc);
            return null;
        }

        boolean callbackInfoIsReturnable = false;
        CallbackInfoCreation callbackInfoCreation = null;
        VarInsnNode loadCallbackInfoFromLvtInsn = null;

        AbstractInsnNode currentInsn = callInsn.getPrevious();
        for (int i = callbackMethodArgumentTypes.length - 1; i >= 0; currentInsn = currentInsn.getPrevious(), i--) {
            Type callbackMethodArgumentType = callbackMethodArgumentTypes[i];
            if (BytecodeHelper.isReference(callbackMethodArgumentType)) {
                switch (callbackMethodArgumentType.getInternalName()) {
                    default:
                        Preconditions.checkState(currentInsn.getOpcode() == ALOAD, "expected %s, got %s", Printer.OPCODES[ALOAD], Printer.OPCODES[currentInsn.getOpcode()]);
                        continue;
                    case "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable":
                        callbackInfoIsReturnable = true;
                    case "org/spongepowered/asm/mixin/injection/callback/CallbackInfo":
                        break;
                }

                if (currentInsn.getOpcode() == ALOAD) { //the CallbackInfo instance is being loaded from a local variable
                    loadCallbackInfoFromLvtInsn = (VarInsnNode) currentInsn;
                    callbackInfoCreation = currentCallbackCreationsByLvt.get(loadCallbackInfoFromLvtInsn.var);
                    Preconditions.checkState(callbackInfoCreation != null, "LVT index %s isn't a valid CallbackInfo value at L%s;%s%s", loadCallbackInfoFromLvtInsn.var, classNode.name, callingMethod.name, callingMethod.desc);
                    break;
                } else if (currentInsn.getOpcode() == INVOKESPECIAL) { //we'll assume it's a constructor
                    MethodInsnNode ctorInsn = (MethodInsnNode) currentInsn;
                    Preconditions.checkState(callbackInfoInternalName(callbackInfoIsReturnable).equals(ctorInsn.owner) && "<init>".equals(ctorInsn.name),
                            "not a valid CallbackInfo constructor at L%s;%s%s", classNode.name, callingMethod.name, callingMethod.desc);

                    //TODO: we should improve this using Analyzer
                    for (CallbackInfoCreation potentialCreationInstance : callbackInfoCreations) {
                        if (potentialCreationInstance.creatingMethod == callingMethod
                                && potentialCreationInstance.creationInsns.get(potentialCreationInstance.creationInsns.size() - 1) == ctorInsn) {
                            callbackInfoCreation = potentialCreationInstance;
                        }
                    }
                    Preconditions.checkState(callbackInfoCreation != null, "can't find creation instance corresponding to CallbackInfo constructor at L%s;%s%s", classNode.name, callingMethod.name, callingMethod.desc);
                    break;
                } else {
                    throw new IllegalStateException(Printer.OPCODES[currentInsn.getOpcode()]);
                }
            } else {
                Preconditions.checkState(currentInsn.getOpcode() == callbackMethodArgumentType.getOpcode(ILOAD), "expected %s, got %s", Printer.OPCODES[callbackMethodArgumentType.getOpcode(ILOAD)], Printer.OPCODES[currentInsn.getOpcode()]);
            }
        }
        Preconditions.checkState(callbackInfoCreation != null, "couldn't find CallbackInfo creation when L%s;%s%s tries to invoke mixin callback method L%s;%s%s", classNode.name, callingMethod.name, callingMethod.desc, callInsn.owner, callInsn.name, callInsn.desc);

        List<AbstractInsnNode> checkCancelledInsns = Collections.emptyList();
        if (callbackInfoCreation.cancellable) {
            checkCancelledInsns = new ArrayList<>();

            checkCancelledInsns.add(currentInsn = callInsn.getNext());
            Preconditions.checkState(currentInsn.getOpcode() == ALOAD, "expected %s, got %s", Printer.OPCODES[ALOAD], Printer.OPCODES[currentInsn.getOpcode()]);
            Preconditions.checkState(((VarInsnNode) currentInsn).var == loadCallbackInfoFromLvtInsn.var, "expected %s #%s, got #%s", Printer.OPCODES[ALOAD], loadCallbackInfoFromLvtInsn.var, ((VarInsnNode) currentInsn).var);

            checkCancelledInsns.add(currentInsn = currentInsn.getNext());
            Preconditions.checkState(currentInsn.getOpcode() == INVOKEVIRTUAL, "expected %s, got %s", Printer.OPCODES[INVOKEVIRTUAL], Printer.OPCODES[currentInsn.getOpcode()]);
            Preconditions.checkState("isCancelled".equals(((MethodInsnNode) currentInsn).name), "expected %s isCancelled, got %s", Printer.OPCODES[ALOAD], ((MethodInsnNode) currentInsn).name);

            checkCancelledInsns.add(currentInsn = currentInsn.getNext());
            Preconditions.checkState(currentInsn.getOpcode() == IFEQ, "expected %s, got %s", Printer.OPCODES[IFEQ], Printer.OPCODES[currentInsn.getOpcode()]);

            checkCancelledInsns.add(currentInsn = currentInsn.getNext());
            if (callbackInfoIsReturnable) { //a returnable callbackinfo, mixin generates code here to get retrieve the actual return value
                Preconditions.checkState(currentInsn.getOpcode() == ALOAD, "expected %s, got %s", Printer.OPCODES[ALOAD], Printer.OPCODES[currentInsn.getOpcode()]);
                Preconditions.checkState(((VarInsnNode) currentInsn).var == loadCallbackInfoFromLvtInsn.var, "expected %s #%s, got #%s", Printer.OPCODES[ALOAD], loadCallbackInfoFromLvtInsn.var, ((VarInsnNode) currentInsn).var);

                checkCancelledInsns.add(currentInsn = currentInsn.getNext());
                Preconditions.checkState(currentInsn.getOpcode() == INVOKEVIRTUAL, "expected %s, got %s", Printer.OPCODES[INVOKEVIRTUAL], Printer.OPCODES[currentInsn.getOpcode()]);
                //i am too lazy to add proper validation for the rest of this

                if (callbackMethodReturnType.getSort() >= Type.ARRAY) { //essentially checks for a reference type
                    checkCancelledInsns.add(currentInsn = currentInsn.getNext());
                    Preconditions.checkState(currentInsn.getOpcode() == CHECKCAST, "expected %s, got %s", Printer.OPCODES[CHECKCAST], Printer.OPCODES[currentInsn.getOpcode()]);
                }

                checkCancelledInsns.add(currentInsn = currentInsn.getNext());
                Preconditions.checkState(currentInsn.getOpcode() == callbackMethodReturnType.getOpcode(IRETURN), "expected %s, got %s", Printer.OPCODES[callbackMethodReturnType.getOpcode(IRETURN)], Printer.OPCODES[currentInsn.getOpcode()]);
            } else { //a void callback, mixin simply injects a RETURN
                Preconditions.checkState(currentInsn.getOpcode() == RETURN, "expected %s, got %s", Printer.OPCODES[RETURN], Printer.OPCODES[currentInsn.getOpcode()]);
            }
        }

        CallbackInvocation invocation = new CallbackInvocation(callingMethod, callbackInfoCreation, Optional.ofNullable(loadCallbackInfoFromLvtInsn), callInsn, checkCancelledInsns);
        callbackInfoCreation.consumedByInvocations.add(invocation);
        return invocation;
    }

    private static CallbackMethod wrapCallbackMethod(ClassNode classNode, MethodNode callbackMethod) {
        Type callbackMethodDescType = Type.getMethodType(callbackMethod.desc);
        Type callbackMethodReturnType = callbackMethodDescType.getReturnType();
        Type[] callbackMethodArgumentTypes = callbackMethodDescType.getArgumentTypes();

        Preconditions.checkState(callbackMethodReturnType.getSort() == Type.VOID,
                "non-void return type %s on mixin injector callback method L%s;%s%s", callbackMethodReturnType, classNode.name, callbackMethod.name, callbackMethod.desc);

        int callbackInfoLvtIndex = BytecodeHelper.isStatic(callbackMethod) ? 0 : 1;
        Boolean callbackInfoIsReturnable = null;
        for (Type callbackMethodArgumentType : callbackMethodArgumentTypes) {
            if (BytecodeHelper.isReference(callbackMethodArgumentType)) {
                switch (callbackMethodArgumentType.getInternalName()) {
                    case "org/spongepowered/asm/mixin/injection/callback/CallbackInfo":
                        Preconditions.checkState(callbackInfoIsReturnable == null,
                                "multiple CallbackInfo arguments to mixin injector callback method L%s;%s%s", classNode.name, callbackMethod.name, callbackMethod.desc);
                        callbackInfoIsReturnable = false;
                        continue;
                    case "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable":
                        Preconditions.checkState(callbackInfoIsReturnable == null,
                                "multiple CallbackInfo arguments to mixin injector callback method L%s;%s%s", classNode.name, callbackMethod.name, callbackMethod.desc);
                        callbackInfoIsReturnable = true;
                        continue;
                }
            }

            if (callbackInfoIsReturnable == null) {
                //we haven't found a CallbackInfo argument yet, so the current argument is one of the target method's
                //  arguments. we need to keep searching!
                callbackInfoLvtIndex += callbackMethodArgumentType.getSize();
            }
        }
        Preconditions.checkState(callbackInfoIsReturnable != null,
                "no CallbackInfo arguments to what was assumed to be a mixin injector callback method L%s;%s%s", classNode.name, callbackMethod.name, callbackMethod.desc);

        CallbackMethod result = new CallbackMethod(callbackMethod, callbackInfoLvtIndex, callbackInfoIsReturnable);

        Frame<SourceValue>[] sources = BytecodeHelper.analyzeSources(classNode.name, callbackMethod);
        //Frame<UsageValue>[] usages = BytecodeHelper.analyzeUsages(classNode.name, callbackMethod);

        BitSet stackOperandsWhichReferenceCallbackInfo = new BitSet();
        for (ListIterator<AbstractInsnNode> itr = callbackMethod.instructions.iterator(); itr.hasNext(); ) {
            AbstractInsnNode insn = itr.next();
            if (!BytecodeHelper.isNormalCodeInstruction(insn)) {
                continue;
            }

            int insnIndex = callbackMethod.instructions.indexOf(insn);
            Frame<SourceValue> sourceFrame = sources[insnIndex];

            int consumedStackOperands = BytecodeHelper.getConsumedStackOperandCount(insn, sourceFrame);

            //set flags for each operand consumed from the stack which could refer to our CallbackInfo instance
            stackOperandsWhichReferenceCallbackInfo.clear();
            for (int i = 0; i < consumedStackOperands; i++) {
                Set<AbstractInsnNode> sourceInsns = BytecodeHelper.getStackValueFromTop(sourceFrame, i).insns;
                if (couldReferenceCallbackInfo(sourceInsns, callbackInfoLvtIndex)) {
                    if (!alwaysReferencesCallbackInfo(sourceInsns, callbackInfoLvtIndex)) {
                        //the instruction doesn't always refer to our CallbackInfo instance, which results in some extra complicated logic that can't be easily optimized away
                        PPatchesMod.LOGGER.warn("mixin injector callback method L{};{}{} uses the given CallbackInfo argument conditionally! (instruction {} @{})",
                                classNode.name, callbackMethod.name, callbackMethod.desc, Printer.OPCODES[insn.getOpcode()], insnIndex);
                        result.usesCallbackInfoInstanceInUnknownWay = true;
                        continue;
                    }

                    stackOperandsWhichReferenceCallbackInfo.set(consumedStackOperands - 1 - i);
                }
            }

            if (stackOperandsWhichReferenceCallbackInfo.isEmpty()) {
                //none of the operands could refer directly to the CallbackInfo instance, we can ignore it
                continue;
            }

            if (insn.getOpcode() == INVOKEVIRTUAL && stackOperandsWhichReferenceCallbackInfo.get(0)) {
                //this is a virtual method call, and the object instance could refer to the CallbackInfo instance! we'll check for all of the commonly used CallbackInfo methods
                MethodInsnNode methodInsn = (MethodInsnNode) insn;

                if (result.callbackInfoInternalName().equals(methodInsn.owner)) {
                    switch (methodInsn.name) {
                        case "getId":
                            assert "()Ljava/lang/String;".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsGetId = true;
                            continue;
                        case "isCancellable":
                            assert "()Z".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsIsCancellable = true;
                            continue;
                        case "isCancelled":
                            assert "()Z".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsIsCancelled = true;
                            continue;
                        case "cancel":
                            assert "()V".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsCancel = true;
                            continue;
                    }

                    if (result.callbackInfoIsReturnable) {
                        switch (methodInsn.name) {
                            case "getReturnValue":
                                assert "()Ljava/lang/Object;".equals(methodInsn.desc) : methodInsn.desc;
                                result.callsGetReturnValue = true;
                                continue;
                            case "setReturnValue":
                                assert "(Ljava/lang/Object;)V".equals(methodInsn.desc) : methodInsn.desc;
                                //calling setReturnValue(Object) implicitly cancels the CallbackInfo as well
                                result.callsSetReturnValue = result.callsCancel = true;
                                continue;
                        }
                    }
                }
            }

            //if we get this far, whichever instruction this is is using the CallbackInfo in a way we don't know how to optimize.
            PPatchesMod.LOGGER.warn("mixin injector callback method L{};{}{} uses the given CallbackInfo argument in an unknown way! (instruction {} @{})",
                    classNode.name, callbackMethod.name, callbackMethod.desc, Printer.OPCODES[insn.getOpcode()], insnIndex);
            result.usesCallbackInfoInstanceInUnknownWay = true;
        }

        return result;
    }

    private static boolean couldReferenceCallbackInfo(Collection<AbstractInsnNode> sourceInsns, int callbackInfoLvtIndex) {
        for (AbstractInsnNode sourceInsn : sourceInsns) {
            if (referencesCallbackInfo(sourceInsn, callbackInfoLvtIndex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean alwaysReferencesCallbackInfo(Collection<AbstractInsnNode> sourceInsns, int callbackInfoLvtIndex) {
        for (AbstractInsnNode sourceInsn : sourceInsns) {
            if (!referencesCallbackInfo(sourceInsn, callbackInfoLvtIndex)) {
                return false;
            }
        }
        return true;
    }

    private static boolean referencesCallbackInfo(AbstractInsnNode sourceInsn, int callbackInfoLvtIndex) {
        return sourceInsn.getOpcode() == ALOAD && ((VarInsnNode) sourceInsn).var == callbackInfoLvtIndex;
    }

    private static void offsetLvtIndicesGreaterThan(MethodNode methodNode, int threshold, int offset) {
        if (offset == 0) {
            return;
        }

        for (AbstractInsnNode currentInsn = methodNode.instructions.getFirst(); currentInsn != null; currentInsn = currentInsn.getNext()) {
            if (currentInsn instanceof VarInsnNode && ((VarInsnNode) currentInsn).var > threshold) {
                ((VarInsnNode) currentInsn).var += offset;
            } else if (currentInsn instanceof IincInsnNode && ((IincInsnNode) currentInsn).var > threshold) {
                ((IincInsnNode) currentInsn).var += offset;
            }
        }

        for (LocalVariableNode variableNode : methodNode.localVariables) {
            if (variableNode.index > threshold) {
                variableNode.index += offset;
            }
        }
    }

    private static void removeCallbackInfoArgument(ClassNode classNode, List<CallbackInfoCreation> callbackInfoCreations, List<CallbackInvocation> callbackInvocations, CallbackMethod callbackMethodMeta, MethodNode callbackMethod) {
        PPatchesMod.LOGGER.info("completely removing CallbackInfo argument from L{};{}{}", classNode.name, callbackMethod.name, callbackMethod.desc);

        String modifiedCallbackDesc = callbackMethod.desc.replace(callbackInfoTypeDesc(callbackMethodMeta.callbackInfoIsReturnable), "");
        callbackMethod.desc = modifiedCallbackDesc;

        //removing CallbackInfo from the method signature means that all other LVT entries need to be shifted down by one
        offsetLvtIndicesGreaterThan(callbackMethod, callbackMethodMeta.callbackInfoLvtIndex, -1);

        for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
            //this will delete all the corresponding instructions if this was the last invocation to reference the CallbackInfo instance
            invocation.callbackInfoCreation.removeInvocation(invocation);

            if (invocation.loadCallbackInfoFromLvtInsn.isPresent()) { //the CallbackInfo instance is loaded onto the call stack by loading it from a local variable
                //simply delete the corresponding ALOAD instruction
                invocation.callingMethod.instructions.remove(invocation.loadCallbackInfoFromLvtInsn.get());
            } else {
                //if the CallbackInfo only ever existed on the stack, this should have been the only thing that used it and so it should already be entirely removed
                Preconditions.checkState(invocation.callbackInfoCreation.consumedByInvocations.isEmpty());
            }

            //the callback method now has a new descriptor since the CallbackInfo has been removed
            invocation.invokeCallbackMethodInsn.desc = modifiedCallbackDesc;
        }
    }

    private static boolean doFinalTransform(ClassNode classNode, List<CallbackInfoCreation> callbackInfoCreations, List<CallbackInvocation> callbackInvocations, Map<MethodNode, CallbackMethod> callbackMethods) {
        //TODO: what if a callbackinfo is used elsewhere? we don't want to delete too aggressively
        callbackInfoCreations.removeIf(creation -> creation.consumedByInvocations.isEmpty());

        boolean anyChanged = false;
        for (CallbackMethod callbackMethodMeta : callbackMethods.values()) {
            Preconditions.checkState(!callbackMethodMeta.usedByInvocations.isEmpty());

            MethodNode callbackMethod = callbackMethodMeta.callbackMethod;

            if (!callbackMethodMeta.usesCallbackInfoInstanceAtAll()) { //the CallbackInfo instance is never accessed at all, there's literally no reason to keep it around
                removeCallbackInfoArgument(classNode, callbackInfoCreations, callbackInvocations, callbackMethodMeta, callbackMethod);
                anyChanged = true;
                continue;
            } else if (callbackMethodMeta.usesCallbackInfoInstanceInUnknownWay | callbackMethodMeta.forwardsCallbackInfoInstance | callbackMethodMeta.replacesCallbackInfoInstance) {
                //transforming the code to work around this would be too much hassle, so for now we'll leave it as-is
                continue;
            }

            if (callbackMethodMeta.callsCancel) {
                if (callbackMethodMeta.callsSetReturnValue) {
                }
                //TODO
                continue;
            }
        }

        for (CallbackInfoCreation creation : callbackInfoCreations) {
            if (!creation.consumedByInvocations.isEmpty() //the CallbackInfo is still being used by at least one callback method (hasn't been optimized away entirely)
                    && !creation.cancellable && !creation.capturesReturnValue) { //the CallbackInfo's state is immutable
                PPatchesMod.LOGGER.info("replacing new {} in L{};{}{} with id {} with INVOKEDYNAMIC", callbackInfoInternalName(creation.callbackInfoIsReturnable),
                        classNode.name, creation.creatingMethod.name, creation.creatingMethod.desc, creation.id);

                //replace the new CallbackInstance construction with an invokedynamic to a single static instance
                creation.creatingMethod.instructions.insertBefore(creation.creationInsns.get(0),
                        new InvokeDynamicInsnNode("constantCallbackInfoInstance", "()" + callbackInfoTypeDesc(creation.callbackInfoIsReturnable),
                        new Handle(H_INVOKESTATIC,
                                "net/daporkchop/ppatches/modules/mixin/optimizeCallbackInfoAllocation/OptimizeCallbackInfoAllocationTransformer",
                                "bootstrapSimple",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false),
                        new Handle(H_NEWINVOKESPECIAL, callbackInfoInternalName(creation.callbackInfoIsReturnable), "<init>", "(Ljava/lang/String;Z)V", false),
                        creation.id));

                for (AbstractInsnNode insn : creation.creationInsns) {
                    creation.creatingMethod.instructions.remove(insn);
                }
            }
        }

        return anyChanged;
    }

    private static final Map<Class<?>, Map<String, CallSite>> CALL_SITES_BY_TYPE = ImmutableMap.of(CallbackInfo.class, new TreeMap<>(), CallbackInfoReturnable.class, new TreeMap<>());

    public static CallSite bootstrapSimpleConstantCallbackInfo(MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle ctor, String id) throws Throwable {
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
