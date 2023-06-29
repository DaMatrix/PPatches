package net.daporkchop.ppatches.modules.mixin.optimizeCallbackInfoAllocation;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.OptionalBytecodeType;
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

    @AllArgsConstructor
    private static class CallbackInfoCreation {
        public final MethodNode creatingMethod;
        public final TypeInsnNode newInsn;

        public Optional<VarInsnNode> storeCapturedReturnValueToLvtInsn;
        public final List<AbstractInsnNode> creationInsns;
        public InsnNode loadCancellableInsn; //this is also contained by creationInsns
        public Optional<VarInsnNode> captureReturnValueInsn; //this is also contained by creationInsns
        public final MethodInsnNode invokeCtorInsn;
        public final Optional<VarInsnNode> storeToLvtInsn;

        public String id;
        public boolean cancellable;

        public final boolean callbackInfoIsReturnable;

        public final List<CallbackInvocation> consumedByInvocations = new ArrayList<>();

        public void setCancellable(boolean cancellable) {
            if (this.cancellable != cancellable) {
                //replace constant value instruction
                for (ListIterator<AbstractInsnNode> itr = this.creationInsns.listIterator(); itr.hasNext(); ) {
                    AbstractInsnNode insn = itr.next();
                    if (insn == this.loadCancellableInsn) {
                        this.loadCancellableInsn = new InsnNode(cancellable ? ICONST_1 : ICONST_0);
                        this.cancellable = cancellable;
                        itr.set(this.loadCancellableInsn);
                        this.creatingMethod.instructions.set(insn, this.loadCancellableInsn);
                        return;
                    }
                }

                throw new IllegalStateException("couldn't find cancellable load instruction!");
            }
        }

        public void changeId(String newId) {
            if (!Objects.equals(this.id, newId)) {
                //replace constant value instruction
                for (ListIterator<AbstractInsnNode> itr = this.creationInsns.listIterator(); itr.hasNext(); ) {
                    AbstractInsnNode insn = itr.next();
                    if (BytecodeHelper.isConstant(insn) && Objects.equals(this.id, BytecodeHelper.decodeConstant(insn))) {
                        AbstractInsnNode replacementInsn = newId == null ? new InsnNode(ACONST_NULL) : new LdcInsnNode(newId);
                        itr.set(replacementInsn);
                        this.creatingMethod.instructions.set(insn, replacementInsn);
                        this.id = newId;
                        return;
                    }
                }

                throw new IllegalStateException("couldn't find ID load instruction!");
            }
        }

        public void removeInvocation(CallbackInvocation invocation) {
            PPatchesMod.LOGGER.info("removed {} from {}", invocation, this);

            if (!this.consumedByInvocations.remove(invocation)) {
                throw new IllegalStateException();
            } else if (this.consumedByInvocations.isEmpty()) {
                BytecodeHelper.removeAllAndClear(this.creatingMethod.instructions, this.creationInsns);

                if (this.storeToLvtInsn.isPresent()) {
                    this.creatingMethod.instructions.remove(this.storeToLvtInsn.get());
                    //TODO: i should replace all the optionals with an empty optional

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

    @AllArgsConstructor
    private static class CallbackInvocation {
        public final MethodNode callingMethod;
        public final CallbackInfoCreation callbackInfoCreation;

        public Optional<VarInsnNode> loadCallbackInfoFromLvtInsn;
        public final MethodInsnNode invokeCallbackMethodInsn;
        public final List<AbstractInsnNode> checkCancelledInsns;
        
        public final boolean unsupported;

        public AbstractInsnNode finalCallbackInfoLoadForInvokeInsn() {
            if (this.callbackInfoCreation.storeToLvtInsn.isPresent()) { //the callbackinfo is stored on the LVT, there's a corresponding ALOAD instruction somewhere before the actual callback invocation
                int callbackInfoLvtIndex = this.callbackInfoCreation.storeToLvtInsn.get().var;
                for (AbstractInsnNode currentInsn = this.invokeCallbackMethodInsn.getPrevious(); ; currentInsn = currentInsn.getPrevious()) {
                    if (currentInsn.getOpcode() == ALOAD && ((VarInsnNode) currentInsn).var == callbackInfoLvtIndex) {
                        return currentInsn;
                    }
                }
            } else {
                return this.callbackInfoCreation.creationInsns.get(this.callbackInfoCreation.creationInsns.size() - 1);
            }
        }
    }

    @RequiredArgsConstructor
    private static class CallbackMethod {
        public final MethodNode callbackMethod;
        public final int callbackInfoLvtIndex;
        public final boolean callbackInfoIsReturnable;

        public final List<InfoUsage> callsGetId = new ArrayList<>();
        public final List<InfoUsage> callsIsCancellable = new ArrayList<>();
        public final List<InfoUsage> callsIsCancelled = new ArrayList<>();

        public final List<InfoUsage> callsCancel = new ArrayList<>();
        public final List<InfoUsage> alwaysCallsCancel = new ArrayList<>();

        public final List<InfoUsage> callsGetReturnValue = new ArrayList<>();
        public final List<InfoUsage> callsSetReturnValue = new ArrayList<>();
        public boolean alwaysCallsSetReturnValue;

        public boolean usesCallbackInfoInstanceInUnknownWay;

        public final List<CallbackInvocation> usedByInvocations = new ArrayList<>();

        public String callbackInfoInternalName() {
            return OptimizeCallbackInfoAllocationTransformer.callbackInfoInternalName(this.callbackInfoIsReturnable);
        }

        public String callbackInfoTypeDesc() {
            return OptimizeCallbackInfoAllocationTransformer.callbackInfoTypeDesc(this.callbackInfoIsReturnable);
        }

        public boolean usesCallbackInfoInstanceAtAll() {
            return !this.callsGetId.isEmpty() || !this.callsIsCancellable.isEmpty() || !this.callsIsCancelled.isEmpty()
                    || !this.callsCancel.isEmpty()
                    || !this.callsGetReturnValue.isEmpty() || !this.callsSetReturnValue.isEmpty()
                    || this.usesCallbackInfoInstanceInUnknownWay;
        }
    }

    @RequiredArgsConstructor
    private static class InfoUsage {
        public final CallbackMethod callback;
        public final VarInsnNode loadCallbackInfoInsn;
        public final MethodInsnNode useCallbackInfoInsn;
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

        Type creatingMethodReturnType = Type.getReturnType(creatingMethod.desc);

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
        InsnNode loadCancellableInsn = (InsnNode) currentInsn;
        boolean cancellable = currentInsn.getOpcode() == ICONST_1;

        currentInsn = currentInsn.getNext();

        VarInsnNode captureReturnValueInsn = null;
        VarInsnNode storeCapturedReturnValueToLvtInsn = null;
        if (callbackInfoIsReturnable && currentInsn.getOpcode() == creatingMethodReturnType.getOpcode(ILOAD)) {
            //the return value is being captured into the CallbackInfoReturnable instance
            captureReturnValueInsn = (VarInsnNode) currentInsn;

            creationInsns.add(currentInsn);
            currentInsn = currentInsn.getNext();

            storeCapturedReturnValueToLvtInsn = (VarInsnNode) newInsn.getPrevious();
            Preconditions.checkState(storeCapturedReturnValueToLvtInsn.getOpcode() == creatingMethodReturnType.getOpcode(ISTORE), "expected %s, got %s", Printer.OPCODES[creatingMethodReturnType.getOpcode(ISTORE)], Printer.OPCODES[storeCapturedReturnValueToLvtInsn.getOpcode()]);
        }

        Preconditions.checkState(currentInsn.getOpcode() == INVOKESPECIAL, "expected %s, got %s", Printer.OPCODES[INVOKESPECIAL], Printer.OPCODES[currentInsn.getOpcode()]);
        MethodInsnNode ctorInsn = (MethodInsnNode) currentInsn;
        String expectedCtorOwner = callbackInfoInternalName(callbackInfoIsReturnable);
        String expectedCtorName = "<init>";
        String expectedCtorDesc = "(Ljava/lang/String;Z" + (captureReturnValueInsn != null
                ? (BytecodeHelper.isPrimitive(Type.getReturnType(creatingMethod.desc)) ? creatingMethodReturnType.getDescriptor() : "Ljava/lang/Object;") : "") + ")V";
        Preconditions.checkState(expectedCtorOwner.equals(ctorInsn.owner) && expectedCtorName.equals(ctorInsn.name) && expectedCtorDesc.equals(ctorInsn.desc),
                "expected call to L%s;%s%s, found L%s;%s%s", expectedCtorOwner, expectedCtorName, expectedCtorDesc, ctorInsn.owner, ctorInsn.name, ctorInsn.desc);
        creationInsns.add(currentInsn);

        currentInsn = currentInsn.getNext();

        VarInsnNode storeToLvtInsn = null;
        if (currentInsn.getOpcode() == ASTORE) { //the newly created CallbackInfo instance is being stored in a local variable
            storeToLvtInsn = (VarInsnNode) currentInsn;
        }

        return new CallbackInfoCreation(creatingMethod, newInsn,
                Optional.ofNullable(storeCapturedReturnValueToLvtInsn),
                creationInsns, loadCancellableInsn, Optional.ofNullable(captureReturnValueInsn), ctorInsn, Optional.ofNullable(storeToLvtInsn),
                id, cancellable, callbackInfoIsReturnable);
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
                    if (callbackInfoCreation == null) {
                        PPatchesMod.LOGGER.warn("LVT index {} isn't a valid CallbackInfo value at L{};{}{}", loadCallbackInfoFromLvtInsn.var, classNode.name, callingMethod.name, callingMethod.desc);
                        return new CallbackInvocation(callingMethod, null, Optional.empty(), callInsn, Collections.emptyList(), true);
                    }
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

                Type callingMethodReturnType = Type.getReturnType(callingMethod.desc);

                if (callingMethodReturnType.getSort() >= Type.ARRAY) { //essentially checks for a reference type
                    checkCancelledInsns.add(currentInsn = currentInsn.getNext());
                    Preconditions.checkState(currentInsn.getOpcode() == CHECKCAST, "expected %s, got %s", Printer.OPCODES[CHECKCAST], Printer.OPCODES[currentInsn.getOpcode()]);
                }

                checkCancelledInsns.add(currentInsn = currentInsn.getNext());
                Preconditions.checkState(currentInsn.getOpcode() == callingMethodReturnType.getOpcode(IRETURN), "expected %s, got %s", Printer.OPCODES[callingMethodReturnType.getOpcode(IRETURN)], Printer.OPCODES[currentInsn.getOpcode()]);
            } else { //a void callback, mixin simply injects a RETURN
                Preconditions.checkState(currentInsn.getOpcode() == RETURN, "expected %s, got %s", Printer.OPCODES[RETURN], Printer.OPCODES[currentInsn.getOpcode()]);
            }
        }

        CallbackInvocation invocation = new CallbackInvocation(callingMethod, callbackInfoCreation, Optional.ofNullable(loadCallbackInfoFromLvtInsn), callInsn, checkCancelledInsns, false);
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

        BitSet stackOperandsWhichReferenceCallbackInfo = new BitSet();

        INSTRUCTIONS:
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
                    if (sourceInsns.size() != 1) {
                        //the instruction doesn't always refer to our CallbackInfo instance, which results in some extra complicated logic that can't be easily optimized away
                        PPatchesMod.LOGGER.warn("mixin injector callback method L{};{}{} uses the given CallbackInfo argument conditionally! (instruction {} @{})",
                                classNode.name, callbackMethod.name, callbackMethod.desc, Printer.OPCODES[insn.getOpcode()], insnIndex);
                        result.usesCallbackInfoInstanceInUnknownWay = true;
                        continue INSTRUCTIONS;
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

                InfoUsage usage = new InfoUsage(result, (VarInsnNode) BytecodeHelper.getStackValueFromTop(sourceFrame, consumedStackOperands - 1).insns.iterator().next(), methodInsn);

                if (result.callbackInfoInternalName().equals(methodInsn.owner)) {
                    switch (methodInsn.name) {
                        case "getId":
                            assert "()Ljava/lang/String;".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsGetId.add(usage);
                            continue;
                        case "isCancellable":
                            assert "()Z".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsIsCancellable.add(usage);
                            continue;
                        case "isCancelled":
                            assert "()Z".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsIsCancelled.add(usage);
                            continue;
                        case "cancel":
                            assert "()V".equals(methodInsn.desc) : methodInsn.desc;
                            result.callsCancel.add(usage);
                            continue;
                    }

                    if (result.callbackInfoIsReturnable) {
                        switch (methodInsn.name) {
                            case "getReturnValue":
                                assert "()Ljava/lang/Object;".equals(methodInsn.desc) : methodInsn.desc;
                                result.callsGetReturnValue.add(usage);
                                continue;
                            case "setReturnValue":
                                assert "(Ljava/lang/Object;)V".equals(methodInsn.desc) : methodInsn.desc;
                                //calling setReturnValue(Object) implicitly cancels the CallbackInfo as well
                                result.callsCancel.add(usage);
                                result.callsSetReturnValue.add(usage);
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
            if (sourceInsn.getOpcode() == ALOAD && ((VarInsnNode) sourceInsn).var == callbackInfoLvtIndex) {
                return true;
            }
        }
        return false;
    }

    private static void bypassCallbackInfoForCancelAndReturnValue(ClassNode classNode, CallbackMethod callbackMethodMeta, MethodNode callbackMethod, Type invocationReturnType) {
        //cancel is called at least once, so because the invocation return type is a void we'll modify the callback to return a boolean
        PPatchesMod.LOGGER.info("bypassing CallbackInfo when cancelling for mixin injection L{};{}{}", classNode.name, callbackMethod.name, callbackMethod.desc);

        boolean returnValueIsCaptured = false;
        boolean returnValueIsAlwaysCaptured = true;
        for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
            if (invocation.callbackInfoCreation.captureReturnValueInsn.isPresent()) {
                returnValueIsCaptured = true;
            } else {
                returnValueIsAlwaysCaptured = false;
            }
        }

        //if a return value is captured by at least one of the callback's invokers, we'll add a new argument to the callback method
        //  which contains the captured return value.
        //if the return value is always captured, it'll simply be a value, otherwise it'll be an 'optional return value' as defined
        //  by the helper methods above.
        OptionalBytecodeType capturedReturnValueArgumentType = null;
        Integer capturedReturnValueLvtIndex = null;
        if (returnValueIsCaptured) {
            //this optimization should have been done by the time this function is called
            Preconditions.checkState(!callbackMethodMeta.callsGetReturnValue.isEmpty(), "invocation return value is captured, but not used?");

            PPatchesMod.LOGGER.info("moving captured return value for L{};{}{} from CallbackInfo constructor to new callback method parameter", classNode.name, callbackMethod.name, callbackMethod.desc);

            capturedReturnValueArgumentType = returnValueIsAlwaysCaptured
                    ? OptionalBytecodeType.alwaysPresent(invocationReturnType)
                    : OptionalBytecodeType.emulateReference_NullIsUnset(invocationReturnType);

            //add a new argument to the callback method which the return value will be passed in, and then redirect calls to getReturnValue()
            capturedReturnValueLvtIndex = callbackMethodMeta.callbackInfoLvtIndex + 1;
            BytecodeHelper.insertMethodArgumentAtLvtIndex(callbackMethod, capturedReturnValueLvtIndex, "$ppatches_captured_returnValue", capturedReturnValueArgumentType.optionalType(), ACC_FINAL | ACC_SYNTHETIC);

            //modify all invocation points to pass a return value argument
            for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                //update the signature at the invocation point
                invocation.invokeCallbackMethodInsn.desc = callbackMethod.desc;

                if (invocation.callbackInfoCreation.captureReturnValueInsn.isPresent()) {
                    //don't pass the captured return value to the CallbackInfoReturnable constructor
                    AbstractInsnNode loadReturnValueArgumentInsn = removeCapturedReturnValueFromCallbackInfoConstructor(invocation, invocationReturnType);

                    //load the return value as an argument to the callback method
                    invocation.callingMethod.instructions.insert(invocation.finalCallbackInfoLoadForInvokeInsn(), loadReturnValueArgumentInsn);

                    if (!returnValueIsAlwaysCaptured) {
                        //if there isn't always a captured return value, we should convert the captured return value to an optional value
                        invocation.callingMethod.instructions.insert(loadReturnValueArgumentInsn, capturedReturnValueArgumentType.makeOptionalFromValue(false));
                    }
                } else {
                    //load a value onto the stack indicating that the value is absent
                    assert !returnValueIsAlwaysCaptured;
                    invocation.callingMethod.instructions.insert(invocation.finalCallbackInfoLoadForInvokeInsn(), capturedReturnValueArgumentType.loadUnsetValue());
                }
            }
        }

        OptionalBytecodeType returnValueType;
        String returnValueVariableName;
        AbstractInsnNode loadDefaultReturnValueInsn;
        if (callbackMethodMeta.callsCancel.isEmpty()) {
            returnValueType = null;
            returnValueVariableName = null;
            loadDefaultReturnValueInsn = null;
        } else if (BytecodeHelper.isVoid(invocationReturnType)) {
            //we only have to return true/false to indicate whether or not cancel() was called
            returnValueType = OptionalBytecodeType.alwaysPresent(Type.BOOLEAN_TYPE);
            returnValueVariableName = "$ppatches_isCancelled";

            //isCancelled defaults to false
            loadDefaultReturnValueInsn = new InsnNode(ICONST_0);
        } else {
            //if cancelled, the callback method will return an 'optional return value'
            returnValueType = OptionalBytecodeType.emulateReferenceWithFlag_NullIsDefault(invocationReturnType);
            returnValueVariableName = "$ppatches_returnValue";

            //return value defaults to the unset return value, indicating that it isn't cancelled
            loadDefaultReturnValueInsn = returnValueType.loadUnsetValue();
        }

        //add a local variable to store the current cancellation state (and return value)
        Integer returnValueLvtIndex = null;
        if (returnValueType != null) {
            returnValueLvtIndex = Type.getArgumentsAndReturnSizes(callbackMethod.desc) >> 2;
            BytecodeHelper.offsetLvtIndicesGreaterThan(callbackMethod, returnValueLvtIndex - 1, returnValueType.optionalType().getSize());
            LabelNode newStartLabel = new LabelNode();
            BytecodeHelper.addFirst(callbackMethod.instructions, newStartLabel, loadDefaultReturnValueInsn, new VarInsnNode(returnValueType.optionalType().getOpcode(ISTORE), returnValueLvtIndex));
            BytecodeHelper.getLocalVariables(callbackMethod).add(new LocalVariableNode(returnValueVariableName, returnValueType.optionalType().getDescriptor(), null,
                    newStartLabel, BytecodeHelper.findMethodEndLabel(callbackMethod), returnValueLvtIndex));
            loadDefaultReturnValueInsn = null;
        }

        //redirect all references to getReturnValue() to the newly added local variable
        //  this must run before redirecting setReturnValue() or cancel()
        for (InfoUsage usage : callbackMethodMeta.callsGetReturnValue) {
            assert !BytecodeHelper.isVoid(invocationReturnType) : invocationReturnType;
            assert "java/lang/Object".equals(Type.getReturnType(usage.useCallbackInfoInsn.desc).getInternalName()) : usage.useCallbackInfoInsn.name;

            //if getReturnValue() is called, we know the invoking method's return type can't be void, so we don't have to deal with the '$ppatches_isCancelled' local variable
            InsnList seq = new InsnList();

            LabelNode tailLbl = new LabelNode(); //jump target at the end of the getReturnValue() replacement sequence (by here, the value on top of the stack must be an object)
            boolean loadNullBeforeTailLabel = false;
            boolean addTailLabel = false;

            if (!callbackMethodMeta.callsSetReturnValue.isEmpty()) { //setReturnValue() is called at least once, we need to take '$ppatches_returnValue' into account
                LabelNode notCancelledLbl = new LabelNode(); //jump target if the callback hasn't been cancelled

                seq.add(new VarInsnNode(returnValueType.optionalType().getOpcode(ILOAD), returnValueLvtIndex));
                seq.add(returnValueType.ifOptionalValueSet(notCancelledLbl)); //jump to notCancelledLbl if '$ppatches_returnValue' is present (i.e. the callback has been cancelled)

                //the callback has already been cancelled (which we know because the optional's value is set), we'll use the value from '$ppatches_returnValue' as a reference value
                //  (which may incur boxing)
                seq.add(new VarInsnNode(returnValueType.optionalType().getOpcode(ILOAD), returnValueLvtIndex));
                seq.add(returnValueType.extractValueAsNullableReference(false));
                seq.add(new JumpInsnNode(GOTO, tailLbl));

                seq.add(notCancelledLbl);

                loadNullBeforeTailLabel = true;
                addTailLabel = true;
            }

            if (returnValueIsCaptured) { //a return value is captured, we should try to use it
                //if a return value was captured, we need to load it onto the stack as a reference value (which may require boxing), otherwise the captured return value is null
                seq.add(new VarInsnNode(capturedReturnValueArgumentType.optionalType().getOpcode(ILOAD), capturedReturnValueLvtIndex));
                seq.add(capturedReturnValueArgumentType.extractValueAsNullableReference_NullIfUnset());

                //we don't need to load a constant null
                loadNullBeforeTailLabel = false;
            }

            assert !(loadNullBeforeTailLabel && !addTailLabel);
            if (addTailLabel) {
                if (loadNullBeforeTailLabel) { //a branch was necessary, we should load null and append the tail label
                    seq.add(new InsnNode(ACONST_NULL));
                }
                seq.add(tailLbl);
            }

            if (false && BytecodeHelper.isCHECKCAST(usage.useCallbackInfoInsn.getNext(), invocationReturnType)) { //eliminate redundant cast
                callbackMethod.instructions.remove(usage.useCallbackInfoInsn.getNext());
            }

            callbackMethod.instructions.remove(usage.loadCallbackInfoInsn);
            BytecodeHelper.replaceAndClear(usage.useCallbackInfoInsn, callbackMethod.instructions, seq);
        }
        callbackMethodMeta.callsGetReturnValue.clear();

        if (!callbackMethodMeta.callsSetReturnValue.isEmpty()) {
            //replace all calls to setReturnValue() with setting the local return value variable to the given return value
            for (InfoUsage usage : callbackMethodMeta.callsSetReturnValue) {
                InsnList seq = new InsnList();
                seq.add(returnValueType.makeOptionalFromNullableReference_PreserveNull());
                seq.add(new VarInsnNode(returnValueType.optionalType().getOpcode(ISTORE), returnValueLvtIndex));

                callbackMethod.instructions.remove(usage.loadCallbackInfoInsn);
                BytecodeHelper.replaceAndClear(usage.useCallbackInfoInsn, callbackMethod.instructions, seq);
            }

            //our replacement for setReturnValue() deals with implicitly calling cancel(), so we can remove them here in order to only handle explicit cancel()s below
            callbackMethodMeta.callsCancel.removeAll(callbackMethodMeta.callsSetReturnValue);
            callbackMethodMeta.callsSetReturnValue.clear();
        }

        //replace all calls to cancel()
        for (InfoUsage usage : callbackMethodMeta.callsCancel) {
            callbackMethod.instructions.remove(usage.loadCallbackInfoInsn);

            if (BytecodeHelper.isVoid(invocationReturnType)) {
                //'$ppatches_isCancelled = true;'
                BytecodeHelper.replace(usage.useCallbackInfoInsn, callbackMethod.instructions, new InsnNode(ICONST_1), new VarInsnNode(ISTORE, returnValueLvtIndex));
            } else {
                //'if (isSet($ppatches_returnValue)) $ppatches_returnValue = null;'
                //  this check ensures correct behavior when setReturnValue(Object) is called before cancel(), because calling cancel() doesn't actually set the return value to null, but rather
                //  sets CallbackInfo#cancelled to true, which will cause the invoker to return the value of CallbackInfoReturnable#returnValue, which is null unless CallbackInfoReturnable#setReturnValue(Object)
                //  is called separately.
                //if the original return value was captured, '$ppatches_captured_returnValue' will be used in place of 'null', as CallbackInfoReturnable#returnValue is always set to the captured return value
                //  if a return value has been captured.

                InsnList seq = new InsnList();
                LabelNode skipLbl = new LabelNode();
                seq.add(new VarInsnNode(returnValueType.optionalType().getOpcode(ILOAD), returnValueLvtIndex));
                seq.add(returnValueType.ifOptionalValueSet(skipLbl));
                if (returnValueIsCaptured) {
                    seq.add(new VarInsnNode(capturedReturnValueArgumentType.optionalType().getOpcode(ILOAD), capturedReturnValueLvtIndex));
                    seq.add(capturedReturnValueArgumentType.extractValueAsNullableReference_NullIfUnset());
                    seq.add(returnValueType.makeOptionalFromNullableReference_PreserveNull());
                } else {
                    seq.add(returnValueType.loadNullValue());
                }
                seq.add(new VarInsnNode(returnValueType.optionalType().getOpcode(ISTORE), returnValueLvtIndex));
                seq.add(skipLbl);

                BytecodeHelper.replaceAndClear(usage.useCallbackInfoInsn, callbackMethod.instructions, seq);
            }
        }
        callbackMethodMeta.callsCancel.clear();

        if (returnValueType != null) {
            //replace all RETURN instructions
            for (ListIterator<AbstractInsnNode> itr = callbackMethod.instructions.iterator(); itr.hasNext(); ) {
                if (itr.next().getOpcode() == RETURN) {
                    if (BytecodeHelper.isVoid(invocationReturnType)) {
                        //'return $ppatches_isCancelled;'
                        itr.set(new VarInsnNode(ILOAD, returnValueLvtIndex));
                        itr.add(new InsnNode(IRETURN));
                    } else {
                        //'return $ppatches_returnValue;'
                        itr.set(new VarInsnNode(returnValueType.optionalType().getOpcode(ILOAD), returnValueLvtIndex));
                        itr.add(new InsnNode(returnValueType.optionalType().getOpcode(IRETURN)));
                    }
                }
            }

            //change the callback method's return type to the correct type
            callbackMethod.desc = Type.getMethodDescriptor(returnValueType.optionalType(), Type.getArgumentTypes(callbackMethod.desc));

            //fix up the invocations (make them use the correct method descriptor and use the callback method's return value to check if it was cancelled)
            for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                assert invocation.callbackInfoCreation.cancellable;

                invocation.invokeCallbackMethodInsn.desc = callbackMethod.desc;

                //delete the original cancellation code and replace with a new one
                BytecodeHelper.removeAllAndClear(invocation.callingMethod.instructions, invocation.checkCancelledInsns);

                LabelNode notCancelled = new LabelNode();
                if (BytecodeHelper.isVoid(invocationReturnType)) {
                    invocation.checkCancelledInsns.add(new JumpInsnNode(IFEQ, notCancelled));
                    invocation.checkCancelledInsns.add(new InsnNode(RETURN));
                    invocation.checkCancelledInsns.add(notCancelled);
                } else {
                    InsnList seq = new InsnList();
                    seq.add(BytecodeHelper.dup(returnValueType.optionalType()));
                    seq.add(returnValueType.ifOptionalValueUnset(notCancelled));
                    seq.add(returnValueType.extractValueAsContainedType_DefaultIfNull(false));
                    seq.add(new InsnNode(invocationReturnType.getOpcode(IRETURN)));
                    seq.add(notCancelled);
                    seq.add(BytecodeHelper.pop(returnValueType.optionalType()));

                    invocation.checkCancelledInsns.addAll(Arrays.asList(seq.toArray()));
                }
                BytecodeHelper.insertAfter(invocation.invokeCallbackMethodInsn, invocation.callingMethod.instructions, invocation.checkCancelledInsns);
            }
        }
    }

    private static void removeCallbackInfoArgument(ClassNode classNode, CallbackMethod callbackMethodMeta, MethodNode callbackMethod) {
        PPatchesMod.LOGGER.info("completely removing CallbackInfo argument from L{};{}{}", classNode.name, callbackMethod.name, callbackMethod.desc);

        BytecodeHelper.removeMethodArgumentByLvtIndex(callbackMethod, callbackMethodMeta.callbackInfoLvtIndex);

        for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
            //this will delete all the corresponding instructions if this was the last invocation to reference the CallbackInfo instance
            invocation.callbackInfoCreation.removeInvocation(invocation);

            //if the CallbackInfo was cancellable, there are some instructions afterwards to check if it was cancelled - remove them!
            if (invocation.callbackInfoCreation.cancellable) {
                BytecodeHelper.removeAllAndClear(invocation.callingMethod.instructions, invocation.checkCancelledInsns);
            }

            if (invocation.loadCallbackInfoFromLvtInsn.isPresent()) { //the CallbackInfo instance is loaded onto the call stack by loading it from a local variable
                //simply delete the corresponding ALOAD instruction
                invocation.callingMethod.instructions.remove(invocation.loadCallbackInfoFromLvtInsn.get());
                invocation.loadCallbackInfoFromLvtInsn = Optional.empty();
            } else {
                //if the CallbackInfo only ever existed on the stack, this should have been the only thing that used it and so it should already be entirely removed
                Preconditions.checkState(invocation.callbackInfoCreation.consumedByInvocations.isEmpty());
            }

            //the callback method now has a new descriptor since the CallbackInfo has been removed
            invocation.invokeCallbackMethodInsn.desc = callbackMethod.desc;
        }
    }

    private static AbstractInsnNode removeCapturedReturnValueFromCallbackInfoConstructor(CallbackInvocation invocation, Type invocationReturnType) {
        CallbackInfoCreation creation = invocation.callbackInfoCreation;

        AbstractInsnNode loadReturnValueArgumentInsn;
        if (creation.captureReturnValueInsn.isPresent()) { //a return value is captured when the CallbackInfo is created
            //it's safe to modify the CallbackInfo creation, since CallbackInfoReturnables created with a return value are only used once

            loadReturnValueArgumentInsn = creation.captureReturnValueInsn.get();

            //don't pass the captured return value to the CallbackInfoReturnable constructor
            creation.captureReturnValueInsn = Optional.empty();
            creation.creationInsns.remove(loadReturnValueArgumentInsn);
            invocation.callingMethod.instructions.remove(loadReturnValueArgumentInsn);

            Type originalCtorDesc = Type.getMethodType(creation.invokeCtorInsn.desc);
            Type[] originalCtorArgs = originalCtorDesc.getArgumentTypes();
            creation.invokeCtorInsn.desc = Type.getMethodDescriptor(originalCtorDesc.getReturnType(), Arrays.copyOf(originalCtorArgs, originalCtorArgs.length - 1));

            //mixin duplicates the original return value into a local variable before passing it to the CallbackInfo constructor.
            //  unfortunately it re-uses the same LVT entry as the CallbackInfo, so we'll make a new temporary local variable and use that instead
            int newLvtIndex = BytecodeHelper.findUnusedLvtSlot(creation.creatingMethod, Type.getType(Object.class));
            creation.storeCapturedReturnValueToLvtInsn.get().var = newLvtIndex;
            ((VarInsnNode) loadReturnValueArgumentInsn).var = newLvtIndex;
        } else { //the CallbackInfo is created without a return value
            //the primitive variants of getReturnValue always return 0 if getReturnValue() would return null
            loadReturnValueArgumentInsn = BytecodeHelper.loadConstantDefaultValueInsn(invocationReturnType);
        }
        return loadReturnValueArgumentInsn;
    }

    private static boolean doFinalTransform(ClassNode classNode, List<CallbackInfoCreation> callbackInfoCreations, List<CallbackInvocation> callbackInvocations, Map<MethodNode, CallbackMethod> callbackMethods) {
        //TODO: what if a callbackinfo is used elsewhere? we don't want to delete too aggressively
        callbackInfoCreations.removeIf(creation -> creation.consumedByInvocations.isEmpty());

        //assert that no creation instance is used for more than one callback method
        for (CallbackInfoCreation creation : callbackInfoCreations) {
            if (creation.consumedByInvocations.stream()
                        .map(invocation -> invocation.invokeCallbackMethodInsn.name + invocation.invokeCallbackMethodInsn.desc)
                        .distinct().count() != 1L) {
                PPatchesMod.LOGGER.warn("{} created at L{};{}{} with id \"{}\" is passed to more than one callback method instance! this is very bad, the class won't be optimized.",
                        callbackInfoInternalName(creation.callbackInfoIsReturnable), classNode.name, creation.creatingMethod.name, creation.creatingMethod.desc, creation.id);
                return false;
            }
        }

        //assert that each callback is either always called as cancellable or not
        for (CallbackMethod callbackMethodMeta : callbackMethods.values()) {
            if (callbackMethodMeta.usedByInvocations.stream()
                        .filter(invocation -> invocation.callbackInfoCreation != null) //can be null if the invocation is unsupported
                        .map(invocation -> invocation.callbackInfoCreation.cancellable)
                        .distinct().count() != 1L) {
                PPatchesMod.LOGGER.warn("mixin callback method L{};{}{} is inconsistently cancellable! this is very bad, the class won't be optimized.",
                        classNode.name, callbackMethodMeta.callbackMethod.name, callbackMethodMeta.callbackMethod.desc);
                return false;
            }
        }

        boolean anyChanged = false;
        METHOD:
        for (CallbackMethod callbackMethodMeta : callbackMethods.values()) {
            Preconditions.checkState(!callbackMethodMeta.usedByInvocations.isEmpty());

            MethodNode callbackMethod = callbackMethodMeta.callbackMethod;

            if ((callbackMethod.access & ACC_PRIVATE) == 0) {
                PPatchesMod.LOGGER.warn("mixin callback method L{};{}{} isn't private, skipping...", classNode.name, callbackMethod.name, callbackMethod.desc);
                continue;
            }

            //make sure we don't transform mixin callbacks which are called using INVOKEVIRTUAL: this indicates the method callback isn't private, and could therefore be overridden
            //  (which would be immensely difficult to transform correctly)
            for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                if (invocation.unsupported) {
                    PPatchesMod.LOGGER.warn("mixin callback method L{};{}{} is invoked in an unknown way, skipping...", classNode.name, callbackMethod.name, callbackMethod.desc);
                    continue METHOD;
                } else if (invocation.invokeCallbackMethodInsn.getOpcode() == INVOKEVIRTUAL) {
                    PPatchesMod.LOGGER.warn("mixin callback method L{};{}{} is referenced by a virtual call, skipping...", classNode.name, callbackMethod.name, callbackMethod.desc);
                    continue METHOD;
                }
            }

            if (!callbackMethodMeta.usesCallbackInfoInstanceAtAll()) { //the CallbackInfo instance is never accessed at all, there's literally no reason to keep it around
                removeCallbackInfoArgument(classNode, callbackMethodMeta, callbackMethod);
                anyChanged = true;
                continue;
            } else if (callbackMethodMeta.usesCallbackInfoInstanceInUnknownWay) {
                //transforming the code to work around this would be too much hassle, so for now we'll leave it as-is
                continue;
            }

            if (!callbackMethodMeta.callsGetId.isEmpty()
                && callbackMethodMeta.usedByInvocations.stream().map(invocation -> invocation.callbackInfoCreation.id).distinct().count() == 1L) {
                //getId is called at least once, and the given CallbackInfo instances all have the same id

                String id = callbackMethodMeta.usedByInvocations.get(0).callbackInfoCreation.id;
                PPatchesMod.LOGGER.info("replacing getId() invocations in L{};{}{} with constant id \"{}\"", classNode.name, callbackMethod.name, callbackMethod.desc, id);

                for (InfoUsage usage : callbackMethodMeta.callsGetId) {
                    callbackMethod.instructions.remove(usage.loadCallbackInfoInsn);
                    callbackMethod.instructions.set(usage.useCallbackInfoInsn, new LdcInsnNode(id));
                }
                callbackMethodMeta.callsGetId.clear();
                anyChanged = true;
            }

            Type invocationReturnType = null;
            for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                Type currentInvocationReturnType = Type.getReturnType(invocation.callingMethod.desc);
                if (invocationReturnType == null) {
                    invocationReturnType = currentInvocationReturnType;
                } else if (!invocationReturnType.equals(currentInvocationReturnType)) {
                    PPatchesMod.LOGGER.warn("mixin injector callback L{};{}{} is called from methods with incompatible return types {} and {}",
                            classNode.name, callbackMethod.name, callbackMethod.desc, invocationReturnType, currentInvocationReturnType);
                    continue;
                }
            }
            if (invocationReturnType == null) {
                throw new IllegalStateException("callback method has no invocations?!?");
            }

            if (callbackMethodMeta.callsGetReturnValue.isEmpty()) { //getReturnValue*() is never called, so we can try to avoid capturing the return value
                for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                    if (invocation.callbackInfoCreation.captureReturnValueInsn.isPresent()) { //a return value is being captured, we don't want to do that
                        PPatchesMod.LOGGER.info("not capturing return value from L{};{}{} for mixin injection L{};{}{}",
                                classNode.name, invocation.callingMethod.name, invocation.callingMethod.desc, classNode.name, callbackMethod.name, callbackMethod.desc);

                        removeCapturedReturnValueFromCallbackInfoConstructor(invocation, invocationReturnType);
                        anyChanged = true;
                    }
                }
            }

            if (callbackMethodMeta.callsCancel.isEmpty()) {
                //if cancel() is never called, we can remove the instructions to check if the callback was cancelled
                for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                    if (!invocation.checkCancelledInsns.isEmpty()) {
                        PPatchesMod.LOGGER.info("removing cancellation check from L{};{}{} for mixin injection L{};{}{}",
                                classNode.name, invocation.callingMethod.name, invocation.callingMethod.desc, classNode.name, callbackMethod.name, callbackMethod.desc);
                        BytecodeHelper.removeAllAndClear(invocation.callingMethod.instructions, invocation.checkCancelledInsns);
                        anyChanged = true;
                    }
                }
            }

            if (!callbackMethodMeta.callsCancel.isEmpty() || !callbackMethodMeta.callsGetReturnValue.isEmpty()) {
                bypassCallbackInfoForCancelAndReturnValue(classNode, callbackMethodMeta, callbackMethod, invocationReturnType);
                anyChanged = true;
            }

            if (!callbackMethodMeta.callsIsCancellable.isEmpty()
                    && callbackMethodMeta.usedByInvocations.stream().map(invocation -> invocation.callbackInfoCreation.cancellable).distinct().count() == 1L) {
                //isCancellable is called at least once, and the given CallbackInfo instances are always either all cancellable or all not cancellable

                PPatchesMod.LOGGER.info("replacing isCancellable() checks in L{};{}{} with constants", classNode.name, callbackMethod.name, callbackMethod.desc);

                int opcode = callbackMethodMeta.usedByInvocations.get(0).callbackInfoCreation.cancellable ? ICONST_1 : ICONST_0;
                for (InfoUsage usage : callbackMethodMeta.callsIsCancellable) {
                    callbackMethod.instructions.remove(usage.loadCallbackInfoInsn);
                    callbackMethod.instructions.set(usage.useCallbackInfoInsn, new InsnNode(opcode));
                }
                callbackMethodMeta.callsIsCancellable.clear();
                anyChanged = true;
            }

            if (callbackMethodMeta.callsCancel.isEmpty() && callbackMethodMeta.callsIsCancellable.isEmpty()) {
                //if neither cancel() nor isCancellable() are ever called, we can make any CallbackInfo instances passed to this method uncancellable to allow them to be pooled by the
                //  INVOKEDYNAMIC pass (see the end of this method)
                for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                    if (invocation.callbackInfoCreation.cancellable) {
                        PPatchesMod.LOGGER.info("making {} L{};{}{} for mixin injection L{};{}{} uncancellable", callbackInfoInternalName(callbackMethodMeta.callbackInfoIsReturnable),
                                classNode.name, invocation.callingMethod.name, invocation.callingMethod.desc, classNode.name, callbackMethod.name, callbackMethod.desc);
                        invocation.callbackInfoCreation.setCancellable(false);
                        anyChanged = true;
                    }
                }
            }

            if (!callbackMethodMeta.usesCallbackInfoInstanceAtAll()) { //check again if we can remove the CallbackInfo instance (references to it may have been removed by other stages)
                removeCallbackInfoArgument(classNode, callbackMethodMeta, callbackMethod);
                anyChanged = true;
                continue;
            }

            if (callbackMethodMeta.callsGetId.isEmpty()) {
                //change the callback ID to an empty string if possible, which will allow the instance count optimization using INVOKEDYNAMIC (see the end of this method) to
                //  have fewer global instances lying around which differ only in their ID
                for (CallbackInvocation invocation : callbackMethodMeta.usedByInvocations) {
                    PPatchesMod.LOGGER.info("changing id from \"{}\" to empty string in {} L{};{}{}", invocation.callbackInfoCreation.id,
                            callbackInfoInternalName(callbackMethodMeta.callbackInfoIsReturnable), classNode.name, callbackMethod.name, callbackMethod.desc);

                    invocation.callbackInfoCreation.changeId("");
                    anyChanged = true;
                }
            }
        }

        for (CallbackInfoCreation creation : callbackInfoCreations) {
            if (!creation.consumedByInvocations.isEmpty() //the CallbackInfo is still being used by at least one callback method (hasn't been optimized away entirely)
                    && !creation.cancellable && !creation.captureReturnValueInsn.isPresent()) { //the CallbackInfo's state is immutable
                PPatchesMod.LOGGER.info("replacing new {} in L{};{}{} with id \"{}\" with INVOKEDYNAMIC", callbackInfoInternalName(creation.callbackInfoIsReturnable),
                        classNode.name, creation.creatingMethod.name, creation.creatingMethod.desc, creation.id);

                //replace the new CallbackInstance construction with an invokedynamic to a single static instance
                creation.creatingMethod.instructions.insertBefore(creation.creationInsns.get(0),
                        new InvokeDynamicInsnNode("$ppatches_constantCallbackInfoInstance", "()" + callbackInfoTypeDesc(creation.callbackInfoIsReturnable),
                        new Handle(H_INVOKESTATIC,
                                "net/daporkchop/ppatches/modules/mixin/optimizeCallbackInfoAllocation/OptimizeCallbackInfoAllocationTransformer",
                                "bootstrapSimpleConstantCallbackInfo",
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
