package net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.Type;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.util.*;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class UseFieldsForSimpleConstantGettersTransformer implements ITreeClassTransformer {
    /*
     * The following static classes are never actually loaded into the JVM. Instead, we load their bytecode in order to determine the exact name and signature of the methods
     * being transformed, without having to implement our own mixin-esque refmap system.
     */

    @SuppressWarnings({"deprecation", "DataFlowIssue"})
    private static final class Dummy_CanBeReplacedWithFieldMethods {
        private static void COMMON() {
            ((Biome) null).getSpawningChance();
            ((Biome) null).ignorePlayerSpawnSuitability();

            //TODO: there are some more methods in Block which could be implemented better if we could make some more assumptions about Material and IBlockState methods
            ((Block) null).canEntitySpawn(null, null);
            ((Block) null).isFullCube(null);
            ((Block) null).getRenderType(null);
            ((Block) null).getBoundingBox(null, null, null);
            ((Block) null).getBlockFaceShape(null, null, null, null);
            ((Block) null).isOpaqueCube(null);
            ((Block) null).isCollidable();
            ((Block) null).tickRate(null);
            ((Block) null).quantityDropped(null);
            ((Block) null).getWeakPower(null, null, null, null);
            ((Block) null).canProvidePower(null);
            ((Block) null).getStrongPower(null, null, null, null);
            ((Block) null).requiresUpdates();
            ((Block) null).canDropFromExplosion(null);
            ((Block) null).hasComparatorInputOverride(null);
            ((Block) null).getOffsetType();
            ((Block) null).isBurning(null, null);
            ((Block) null).canSustainLeaves(null, null, null);
            ((Block) null).isWood(null, null);
            ((Block) null).isFoliage(null, null);
            ((Block) null).getExpDrop(null, null, null, 0);
            ((Block) null).getWeakChanges(null, null);
            //((Block) null).isEntityInsideMaterial(null, null, null, null, 0.0d, null, false);
            //((Block) null).isAABBInsideMaterial(null, null, null, null);
            //((Block) null).isAABBInsideLiquid(null, null, null);
            ((Block) null).isStickyBlock(null); //this method is rewritten using mixins to make it suitable for this transformation

            ((Enchantment) null).getMinLevel();
            ((Enchantment) null).getMaxLevel();
            ((Enchantment) null).calcModifierDamage(0, null);
            ((Enchantment) null).calcDamageByCreature(0, null);
            ((Enchantment) null).isTreasureEnchantment();
            ((Enchantment) null).isCurse();
            ((Enchantment) null).isAllowedOnBooks();

            ((Entity) null).getMaxInPortalTime();
            //((Entity) null).getSwimSound();
            //((Entity) null).getSplashSound();
            //((Entity) null).makeFlySound();
            //((Entity) null).canTriggerWalking();
            //((Entity) null).getCollisionBoundingBox(); //TODO: decide whether or not to keep this
            ((Entity) null).canBeCollidedWith();
            ((Entity) null).canBePushed();
            //((Entity) null).shouldSetPosAfterLoading();
            ((Entity) null).getYOffset();
            //TODO: this is similar to getEyeHeight(), see below: ((Entity) null).getMountedYOffset();
            ((Entity) null).getCollisionBorderSize();
            ((Entity) null).getPortalCooldown();
            ((Entity) null).canBeAttackedWithItem();
            ((Entity) null).hitByEntity(null);
            ((Entity) null).isNonBoss();
            ((Entity) null).getMaxFallHeight();
            ((Entity) null).doesEntityNotTriggerPressurePlate();
            ((Entity) null).isPushedByWater();
            //TODO: this one isn't actually a constant, but most implementations can be described as a multiply-add of this.height: ((Entity) null).getEyeHeight();
            ((Entity) null).shouldRiderSit();
            ((Entity) null).canRiderInteract();
            //TODO: this can be optimized away as well: ((Entity) null).shouldDismountInWater(null);
            ((Entity) null).ignoreItemEntityData();
            ((Entity) null).getPushReaction();
            ((Entity) null).getSoundCategory();
            //((Entity) null).getFireImmuneTicks();

            //TODO: there are some more methods in Item which could be implemented better if we could make some more assumptions about the overloads which add an ItemStack argument
            ((Item) null).getDestroySpeed(null, null);
            ((Item) null).shouldRotateAroundWhenRendering();
            ((Item) null).getShareTag();
            ((Item) null).isMap();
            ((Item) null).getItemUseAction(null);
            ((Item) null).getMaxItemUseDuration(null);
            ((Item) null).canItemEditBlocks();
            ((Item) null).getXpRepairRatio(null);
            ((Item) null).getEntityLifespan(null, null);
            ((Item) null).hasCustomEntity(null);
            ((Item) null).getSmeltingExperience(null);
            ((Item) null).doesSneakBypassUse(null, null, null, null);
            ((Item) null).getEquipmentSlot(null);
            ((Item) null).getItemBurnTime(null);

            ((Material) null).isLiquid();
            ((Material) null).isSolid();
            ((Material) null).blocksLight();
            ((Material) null).blocksMovement();

            ((Potion) null).isInstant();
            ((Potion) null).shouldRender(null);
            ((Potion) null).shouldRenderInvText(null);
            ((Potion) null).shouldRenderHUD(null);

            ((TileEntity) null).onlyOpsCanSetNbt();
            ((TileEntity) null).hasFastRenderer();

            ((WorldProvider) null).isSurfaceWorld();
            ((WorldProvider) null).canRespawnHere();
            ((WorldProvider) null).isSkyColored();
            //TODO: make this optimizable: ((WorldProvider) null).getMovementFactor();
            //TODO: ditto: ((WorldProvider) null).shouldClientCheckLighting();
            ((WorldProvider) null).canDoLightning(null);
            ((WorldProvider) null).canDoRainSnowIce(null);
        }

        @SideOnly(Side.CLIENT)
        private static void CLIENT() {
            ((Block) null).hasCustomBreakingProgress(null);
            ((Block) null).getRenderLayer();

            ((Item) null).getFontRenderer(null);
            ((Item) null).getArmorModel(null, null, null, null);

            ((TileEntity) null).getMaxRenderDistanceSquared();

            ((WorldProvider) null).doesXZShowFog(0, 0);
            ((WorldProvider) null).getMusicType();
        }

        @SideOnly(Side.SERVER)
        private static void SERVER() {
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private static final class Dummy_CanBeReplacedWithFMAFieldMethods {
        private static void COMMON() {
            ((Entity) null).getMountedYOffset();
            ((Entity) null).getEyeHeight();
        }

        @SideOnly(Side.CLIENT)
        private static void CLIENT() {
        }

        @SideOnly(Side.SERVER)
        private static void SERVER() {
        }
    }

    @SuppressWarnings({"deprecation", "DataFlowIssue"})
    private static final class Dummy_AlreadyImplementedWithFieldMethods {
        private static void COMMON() {
            ((Block) null).isFullBlock(null);
            ((Block) null).getLightOpacity(null);
            ((Block) null).getLightValue(null);
            ((Block) null).getUseNeighborBrightness(null);
            ((Block) null).getMaterial(null);
            ((Block) null).getMapColor(null, null, null);

            ((Item) null).getItemStackLimit();
            ((Item) null).getHasSubtypes();
            ((Item) null).getMaxDamage();
            ((Item) null).getContainerItem();
            ((Item) null).getCreativeTab();

            ((Material) null).getCanBurn();
            ((Material) null).isReplaceable();
            ((Material) null).isToolNotRequired();
            ((Material) null).getPushReaction();
            ((Material) null).getMaterialMapColor();

            ((WorldProvider) null).doesWaterVaporize();
            ((WorldProvider) null).hasSkyLight(); //TODO: this is assigned by init(), not the class constructor! (should also check other methods)
            ((WorldProvider) null).isNether();
        }

        @SideOnly(Side.CLIENT)
        private static void CLIENT() {
            ((Block) null).isTranslucent(null);

            ((Item) null).isFull3D();

            ((Potion) null).isBeneficial();
        }

        @SideOnly(Side.SERVER)
        private static void SERVER() {
        }
    }

    @SneakyThrows
    private static Map<String, Set<String>> readStaticMethodList(String name) {
        //noinspection UnstableApiUsage
        ClassReader reader = new ClassReader(Resources.toByteArray(Resources.getResource(UseFieldsForSimpleConstantGettersTransformer.class, "UseFieldsForSimpleConstantGettersTransformer$" + name + ".class")));
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

        Map<String, Set<String>> result = new Object2ObjectAVLTreeMap<>();
        for (MethodNode methodNode : classNode.methods) {
            if ("COMMON".equals(methodNode.name) || FMLLaunchHandler.side().name().equals(methodNode.name)) {
                for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;

                        Set<String> methods = result.get(methodInsn.owner);
                        if (methods == null) {
                            result.put(methodInsn.owner.intern(), methods = new ObjectAVLTreeSet<>());
                        }
                        methods.add((methodInsn.name + methodInsn.desc).intern());
                    }
                }
            }
        }
        return result;
    }

    private final Map<String, Set<String>> canBeReplacedWithFieldMethods = readStaticMethodList("Dummy_CanBeReplacedWithFieldMethods");
    private final Map<String, Set<String>> canBeReplacedWithFMAFieldMethods = readStaticMethodList("Dummy_CanBeReplacedWithFMAFieldMethods");
    private final Map<String, Set<String>> alreadyImplementedWithFieldMethods = readStaticMethodList("Dummy_AlreadyImplementedWithFieldMethods");

    @SuppressWarnings("UnstableApiUsage")
    private final Set<String> rootClasses = Stream.of(this.canBeReplacedWithFieldMethods, this.canBeReplacedWithFMAFieldMethods, this.alreadyImplementedWithFieldMethods)
            .map(Map::keySet).flatMap(Set::stream).collect(ImmutableSet.toImmutableSet());

    private String getParentRootClass(String transformedName) {
        for (ClassInfo classInfo = ClassInfo.forName(transformedName); classInfo != null; classInfo = classInfo.getSuperClass()) {
            if ("java/lang/Object".equals(classInfo.getName())) {
                return null;
            } else if (this.rootClasses.contains(classInfo.getName())) {
                return classInfo.getName();
            }
        }
        return null;
    }

    @Override
    @SneakyThrows
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changeFlags = 0;

        if (this.rootClasses.contains(classNode.name)) {
            changeFlags |= this.transformRootClass(classNode);
        } else {
            String rootClassName = this.getParentRootClass(classNode.name);
            if (rootClassName != null) {
                changeFlags |= this.transformDerivedClass(classNode, rootClassName);
            }
        }

        return changeFlags;
    }

    private final Map<String, Map<String, String>> replacedWithFieldFMAFields = new Object2ObjectAVLTreeMap<>();

    private synchronized int transformRootClass(ClassNode classNode) {
        int changeFlags = 0;

        Set<String> canBeReplacedWithFieldMethods = this.canBeReplacedWithFieldMethods.getOrDefault(classNode.name, Collections.emptySet());
        Set<String> canBeReplacedWithFMAFieldMethods = this.canBeReplacedWithFMAFieldMethods.getOrDefault(classNode.name, Collections.emptySet());
        Set<String> alreadyImplementedWithFieldMethods = this.alreadyImplementedWithFieldMethods.getOrDefault(classNode.name, Collections.emptySet());

        Map<String, String> replacedWithFieldFMAFields = new Object2ObjectAVLTreeMap<>();

        for (ListIterator<MethodNode> itr = classNode.methods.listIterator(); itr.hasNext(); ) {
            MethodNode methodNode = itr.next();
            String combinedDesc = methodNode.name + methodNode.desc;
            if (canBeReplacedWithFieldMethods.contains(combinedDesc)) {
                Optional<AbstractInsnNode> loadConstantReturnValueInsn = getConstantReturnValue(methodNode);
                if (loadConstantReturnValueInsn.isPresent()) { //the method is still implemented with a constant return value
                    Type returnType = Type.getReturnType(methodNode.desc);
                    String returnTypeDesc = returnType.getDescriptor();
                    String fieldName = "$ppatches_constant_" + methodNode.name;

                    //add a new field to store the value
                    classNode.visitField(ACC_PRIVATE | ACC_FINAL, fieldName, returnTypeDesc, null, null);

                    //add a new method which will return the actual constant value
                    //  (in theory, we could avoid doing this and set the constant value directly in the constructor, however if we did that then accessing any properties before all constructors
                    //  have completed could yield incorrect results)
                    MethodNode constantMethodNode = new MethodNode(ASM5, ACC_PROTECTED, fieldName, Type.getMethodDescriptor(returnType), null, null);
                    itr.add(constantMethodNode);
                    constantMethodNode.instructions.add(loadConstantReturnValueInsn.get().clone(null));
                    constantMethodNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));

                    //add code to all constructors which initializes the new field to the constant value
                    for (MethodNode ctor : BytecodeHelper.findMethod(classNode, "<init>")) {
                        MethodInsnNode superCtorInvocation = BytecodeHelper.findSuperCtorInvocationInCtor(classNode, ctor).orElse(null);
                        if (superCtorInvocation != null) {
                            ctor.maxStack = Math.max(ctor.maxStack, 2); //extend the stack if necessary
                            BytecodeHelper.insertAfter(superCtorInvocation, ctor.instructions,
                                    new VarInsnNode(ALOAD, 0),
                                    new VarInsnNode(ALOAD, 0),
                                    new MethodInsnNode(INVOKEVIRTUAL, classNode.name, constantMethodNode.name, constantMethodNode.desc, false),
                                    new FieldInsnNode(PUTFIELD, classNode.name, fieldName, returnTypeDesc));
                        }
                    }

                    //rewrite the original method to return the value of the newly added field instead of the constant value
                    BytecodeHelper.replace(loadConstantReturnValueInsn.get(), methodNode.instructions,
                            new VarInsnNode(ALOAD, 0),
                            new FieldInsnNode(GETFIELD, classNode.name, fieldName, returnTypeDesc));

                    changeFlags |= CHANGED_MANDATORY;
                } else {
                    PPatchesMod.LOGGER.error("Expected method L{};{}{} to return a constant value, but it doesn't! Trivial overrides won't be optimized away.",
                            classNode.name, methodNode.name, methodNode.desc);

                    //we can safely modify this set, as we're currently synchronized on the entire transformer, and since we ensure that candidate subclasses will cause their
                    //  superclass to be loaded before transforming anything themselves, we know that no incompatible changes will be made)
                    Preconditions.checkState(canBeReplacedWithFieldMethods.remove(methodNode.name + methodNode.desc));
                }
            } else if (canBeReplacedWithFMAFieldMethods.contains(combinedDesc)) {
                Optional<FieldFMAInfo> optionalFieldFMAInfo = getFieldFMAReturnValue(methodNode);
                if (optionalFieldFMAInfo.isPresent() && optionalFieldFMAInfo.get().field != null) {
                    FieldFMAInfo fieldFMAInfo = optionalFieldFMAInfo.get();

                    Type returnType = Type.getReturnType(methodNode.desc);
                    String returnTypeDesc = returnType.getDescriptor();
                    String factorFieldName = "$ppatches_factor_" + methodNode.name;
                    String offsetFieldName = "$ppatches_offset_" + methodNode.name;

                    //add a new field to store the value
                    classNode.visitField(ACC_PRIVATE | ACC_FINAL, factorFieldName, returnTypeDesc, null, null);
                    classNode.visitField(ACC_PRIVATE | ACC_FINAL, offsetFieldName, returnTypeDesc, null, null);

                    //add new methods which will return the actual constant factor and offset values
                    MethodNode constantFactorMethodNode = new MethodNode(ASM5, ACC_PROTECTED, factorFieldName, Type.getMethodDescriptor(returnType), null, null);
                    itr.add(constantFactorMethodNode);
                    constantFactorMethodNode.instructions.add(new LdcInsnNode(returnType.getSort() == Type.FLOAT ? (Object) fieldFMAInfo.factor.floatValue() : (Object) fieldFMAInfo.factor.doubleValue()));
                    constantFactorMethodNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));

                    MethodNode constantOffsetMethodNode = new MethodNode(ASM5, ACC_PROTECTED, offsetFieldName, Type.getMethodDescriptor(returnType), null, null);
                    itr.add(constantOffsetMethodNode);
                    constantOffsetMethodNode.instructions.add(new LdcInsnNode(returnType.getSort() == Type.FLOAT ? (Object) fieldFMAInfo.offset.floatValue() : (Object) fieldFMAInfo.offset.doubleValue()));
                    constantOffsetMethodNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));

                    //add code to all constructors which initializes the new field to the constant value
                    for (MethodNode ctor : BytecodeHelper.findMethod(classNode, "<init>")) {
                        MethodInsnNode superCtorInvocation = BytecodeHelper.findSuperCtorInvocationInCtor(classNode, ctor).orElse(null);
                        if (superCtorInvocation != null) {
                            ctor.maxStack = Math.max(ctor.maxStack, 2); //extend the stack if necessary
                            BytecodeHelper.insertAfter(superCtorInvocation, ctor.instructions,
                                    new VarInsnNode(ALOAD, 0),
                                    new VarInsnNode(ALOAD, 0),
                                    new MethodInsnNode(INVOKEVIRTUAL, classNode.name, constantFactorMethodNode.name, constantFactorMethodNode.desc, false),
                                    new FieldInsnNode(PUTFIELD, classNode.name, factorFieldName, returnTypeDesc),
                                    new VarInsnNode(ALOAD, 0),
                                    new VarInsnNode(ALOAD, 0),
                                    new MethodInsnNode(INVOKEVIRTUAL, classNode.name, constantOffsetMethodNode.name, constantOffsetMethodNode.desc, false),
                                    new FieldInsnNode(PUTFIELD, classNode.name, offsetFieldName, returnTypeDesc));
                        }
                    }

                    //rewrite the original method to return an FMA of the base field with the values of the factor and offset fields
                    methodNode.instructions.clear();
                    methodNode.visitVarInsn(ALOAD, 0);
                    methodNode.visitFieldInsn(GETFIELD, classNode.name, fieldFMAInfo.field.name, fieldFMAInfo.field.desc);
                    if (!returnTypeDesc.equals(fieldFMAInfo.field.desc)) {
                        methodNode.visitInsn(returnType.getSort() == Type.FLOAT ? D2F : F2D);
                    }
                    methodNode.visitVarInsn(ALOAD, 0);
                    methodNode.visitFieldInsn(GETFIELD, classNode.name, factorFieldName, returnTypeDesc);
                    methodNode.visitInsn(returnType.getOpcode(IMUL));
                    methodNode.visitVarInsn(ALOAD, 0);
                    methodNode.visitFieldInsn(GETFIELD, classNode.name, offsetFieldName, returnTypeDesc);
                    methodNode.visitInsn(returnType.getOpcode(IADD));
                    methodNode.visitInsn(returnType.getOpcode(IRETURN));

                    replacedWithFieldFMAFields.put((methodNode.name + methodNode.desc).intern(), (fieldFMAInfo.field.name + ':' + fieldFMAInfo.field.desc).intern());

                    changeFlags |= CHANGED_MANDATORY;
                } else {
                    PPatchesMod.LOGGER.error("Expected method L{};{}{} to return an FMA transform of a field, but it doesn't! Trivial overrides won't be optimized away.",
                            classNode.name, methodNode.name, methodNode.desc);

                    //we can safely modify this set, as we're currently synchronized on the entire transformer, and since we ensure that candidate subclasses will cause their
                    //  superclass to be loaded before transforming anything themselves, we know that no incompatible changes will be made)
                    Preconditions.checkState(canBeReplacedWithFMAFieldMethods.remove(methodNode.name + methodNode.desc));
                }
            } else if (alreadyImplementedWithFieldMethods.contains(combinedDesc)) {
                //TODO
            }
        }

        if (!replacedWithFieldFMAFields.isEmpty()) {
            this.replacedWithFieldFMAFields.put(classNode.name.intern(), ImmutableMap.copyOf(replacedWithFieldFMAFields));
        }

        return changeFlags;
    }

    private final Map<String, Set<String>> notRemovedMethods = new Object2ObjectAVLTreeMap<>();

    @SneakyThrows
    private boolean hasNonDefaultOverride(String transformedName, String rootClassName, String methodCombinedDesc) {
        //load the superclass to ensure that the NOT_REMOVED_METHODS array is populated
        //  (this is technically redundant, as it's always triggered by transformDerivedClass() before anything actually gets transformed)
        UseFieldsForSimpleConstantGettersTransformer.class.getClassLoader().loadClass(transformedName.replace('/', '.'));

        for (ClassInfo classInfo = ClassInfo.forName(transformedName); classInfo != null; classInfo = classInfo.getSuperClass()) {
            if (rootClassName.equals(classInfo.getName())) {
                return false;
            } else if (this.notRemovedMethods.get(classInfo.getName()).contains(methodCombinedDesc)) {
                return true;
            }
        }
        return false;
    }

    @SneakyThrows
    private synchronized int transformDerivedClass(ClassNode classNode, String rootClassName) {
        //load the superclass to ensure that the NOT_REMOVED_METHODS array is populated and that un-optimizable methods have been removed from the canBeReplacedWithFieldMethods set
        UseFieldsForSimpleConstantGettersTransformer.class.getClassLoader().loadClass(classNode.superName.replace('/', '.'));

        int changeFlags = 0;

        Set<String> canBeReplacedWithFieldMethods = this.canBeReplacedWithFieldMethods.getOrDefault(rootClassName, Collections.emptySet());
        Set<String> canBeReplacedWithFMAFieldMethods = this.canBeReplacedWithFMAFieldMethods.getOrDefault(rootClassName, Collections.emptySet());
        Set<String> alreadyImplementedWithFieldMethods = this.alreadyImplementedWithFieldMethods.getOrDefault(rootClassName, Collections.emptySet());

        Map<String, String> replacedWithFieldFMAFields = this.replacedWithFieldFMAFields.getOrDefault(rootClassName, Collections.emptyMap());

        ImmutableSet.Builder<String> notRemovedMethods = ImmutableSet.builder();

        for (ListIterator<MethodNode> itr = classNode.methods.listIterator(); itr.hasNext(); ) {
            MethodNode methodNode = itr.next();

            String combinedDesc = methodNode.name + methodNode.desc;
            if (canBeReplacedWithFieldMethods.contains(combinedDesc)) {
                Optional<AbstractInsnNode> loadConstantReturnValueInsn = getConstantReturnValue(methodNode);
                if (loadConstantReturnValueInsn.isPresent()) {
                    //the method overrides the default implementation with one that returns a constant value
                    //  we can remove it and set the value in the parent class' newly added field instead, but only if there isn't another non-constant method in some superclass between us
                    //  and the parent which would become the new virtual dispatch target if this method were removed
                    if (rootClassName.equals(classNode.superName) || !this.hasNonDefaultOverride(classNode.superName, rootClassName, combinedDesc)) {
                        PPatchesMod.LOGGER.info("removing override of {}{} from class {} and putting the constant return value in a field instead", methodNode.name, methodNode.desc, classNode.name);

                        //rename the original method so that the base constructor can store the result into the field
                        methodNode.access = ACC_PROTECTED;
                        methodNode.name = "$ppatches_constant_" + methodNode.name;
                        methodNode.desc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc));

                        changeFlags |= CHANGED_MANDATORY;
                    } else {
                        PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it wouldn't inherit the implementation from {})", methodNode.name, methodNode.desc, classNode.name, rootClassName);
                        notRemovedMethods.add(combinedDesc.intern());
                    }
                } else { //the overridden method doesn't return a simple constant, we'll leave it be
                    PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it doesn't return a trivial constant)", methodNode.name, methodNode.desc, classNode.name);
                    notRemovedMethods.add(combinedDesc.intern());
                }
            } else if (canBeReplacedWithFMAFieldMethods.contains(combinedDesc)) {
                Optional<FieldFMAInfo> optionalFieldFMAInfo = getFieldFMAReturnValue(methodNode);
                if (optionalFieldFMAInfo.isPresent()) {
                    FieldFMAInfo fieldFMAInfo = optionalFieldFMAInfo.get();

                    if (fieldFMAInfo.field == null || (fieldFMAInfo.field.name + ':' + fieldFMAInfo.field.desc).equals(replacedWithFieldFMAFields.get(combinedDesc))) {
                        //the method overrides the default implementation with one that returns a constant value
                        //  we can remove it and set the value in the parent class' newly added field instead, but only if there isn't another non-constant method in some superclass between us
                        //  and the parent which would become the new virtual dispatch target if this method were removed
                        if (rootClassName.equals(classNode.superName) || !this.hasNonDefaultOverride(classNode.superName, rootClassName, combinedDesc)) {
                            PPatchesMod.LOGGER.info("removing override of {}{} from class {} and putting the constant FMA factor and offset values in fields instead", methodNode.name, methodNode.desc, classNode.name);

                            Type returnType = Type.getReturnType(methodNode.desc);

                            itr.remove();

                            //add new methods which will return the actual constant factor and offset values
                            MethodNode constantFactorMethodNode = new MethodNode(ASM5, ACC_PROTECTED, "$ppatches_factor_" + methodNode.name, Type.getMethodDescriptor(returnType), null, null);
                            itr.add(constantFactorMethodNode);
                            constantFactorMethodNode.instructions.add(new LdcInsnNode(returnType.getSort() == Type.FLOAT ? (Object) fieldFMAInfo.factor.floatValue() : (Object) fieldFMAInfo.factor.doubleValue()));
                            constantFactorMethodNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));

                            MethodNode constantOffsetMethodNode = new MethodNode(ASM5, ACC_PROTECTED, "$ppatches_offset_" + methodNode.name, Type.getMethodDescriptor(returnType), null, null);
                            itr.add(constantOffsetMethodNode);
                            constantOffsetMethodNode.instructions.add(new LdcInsnNode(returnType.getSort() == Type.FLOAT ? (Object) fieldFMAInfo.offset.floatValue() : (Object) fieldFMAInfo.offset.doubleValue()));
                            constantOffsetMethodNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));

                            changeFlags |= CHANGED_MANDATORY;
                        } else {
                            PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it wouldn't inherit the implementation from {})", methodNode.name, methodNode.desc, classNode.name, rootClassName);
                            notRemovedMethods.add(combinedDesc.intern());
                        }
                    } else {
                        PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it doesn't use the same FMA target field as {})", methodNode.name, methodNode.desc, classNode.name, rootClassName);
                        notRemovedMethods.add(combinedDesc.intern());
                    }
                } else { //the overridden method doesn't return a simple constant, we'll leave it be
                    PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it doesn't return an FMA transformation of a single field)", methodNode.name, methodNode.desc, classNode.name);
                    notRemovedMethods.add(combinedDesc.intern());
                }
            } else if (alreadyImplementedWithFieldMethods.contains(combinedDesc)) {
                //TODO
            }
        }

        this.notRemovedMethods.put(classNode.name.intern(), notRemovedMethods.build());

        return changeFlags;
    }

    private static Optional<AbstractInsnNode> getConstantReturnValue(MethodNode methodNode) {
        AbstractInsnNode firstInstruction = BytecodeHelper.nextNormalCodeInstructionOrCurrent(methodNode.instructions.getFirst());

        boolean isFirstInstructionConstant = false;
        if (BytecodeHelper.isConstant(firstInstruction)) {
            isFirstInstructionConstant = true;
        } else if (firstInstruction.getOpcode() == GETSTATIC) { //check if it's accessing a static final field
            FieldInsnNode getStaticInsn = (FieldInsnNode) firstInstruction;
            ClassInfo classInfo = ClassInfo.forName(getStaticInsn.owner);
            ClassInfo.Field fieldInfo = classInfo.findField(getStaticInsn, ClassInfo.INCLUDE_ALL);
            if (fieldInfo == null && (fieldInfo = classInfo.findFieldInHierarchy(getStaticInsn, ClassInfo.SearchType.ALL_CLASSES, ClassInfo.INCLUDE_ALL)) == null) { //can't find the field
                return Optional.empty();
            } else if (!fieldInfo.isFinal()) { //not a static final field, we can't rely on it not changing
                return Optional.empty();
            }
            isFirstInstructionConstant = true;
        }

        if (!isFirstInstructionConstant) {
            return Optional.empty();
        }

        AbstractInsnNode secondInsn = BytecodeHelper.nextNormalCodeInstruction(firstInstruction);
        if (secondInsn.getOpcode() != Type.getReturnType(methodNode.desc).getOpcode(IRETURN)) { //the constant value isn't immediately returned
            return Optional.empty();
        }

        return Optional.of(firstInstruction);
    }

    @RequiredArgsConstructor
    private static final class FieldFMAInfo {
        private final FieldInsnNode field;
        private final Number factor;
        private final Number offset;
    }

    private static Optional<FieldFMAInfo> getFieldFMAReturnValue(MethodNode methodNode) {
        Type returnType = Type.getReturnType(methodNode.desc);
        int returnOpcode = returnType.getOpcode(IRETURN);
        if (returnOpcode != FRETURN && returnOpcode != DRETURN) {
            return Optional.empty();
        }

        FieldInsnNode field = null;
        Number factor = returnOpcode == FRETURN ? (Number) 0.0f : (Number) 0.0d;
        Number offset = factor;

        Number lastCst = null;

        AbstractInsnNode currentInsn = BytecodeHelper.nextNormalCodeInstructionOrCurrent(methodNode.instructions.getFirst());
        if (currentInsn.getOpcode() == ALOAD && ((VarInsnNode) currentInsn).var == 0) {
            currentInsn = BytecodeHelper.nextNormalCodeInstruction(currentInsn);
            if (currentInsn.getOpcode() == GETFIELD) {
                field = (FieldInsnNode) currentInsn;
            } else {
                return Optional.empty();
            }
        } else if (BytecodeHelper.isConstant(currentInsn)) {
            Object cst = BytecodeHelper.decodeConstant(currentInsn);
            if (!(cst instanceof Float || cst instanceof Double)) {
                return Optional.empty();
            }
            lastCst = (Number) cst;
        } else {
            return Optional.empty();
        }

        currentInsn = nextFMAInsn(currentInsn);
        if (currentInsn.getOpcode() == returnOpcode) {
            return Optional.of(new FieldFMAInfo(field, factor, lastCst != null ? lastCst : offset));
        } else if (field == null && currentInsn.getOpcode() == ALOAD && ((VarInsnNode) currentInsn).var == 0) {
            currentInsn = BytecodeHelper.nextNormalCodeInstruction(currentInsn);
            if (currentInsn.getOpcode() == GETFIELD) {
                field = (FieldInsnNode) currentInsn;
            } else {
                return Optional.empty();
            }
        } else if (BytecodeHelper.isConstant(currentInsn)) {
            Object cst = BytecodeHelper.decodeConstant(currentInsn);
            if (!(cst instanceof Float || cst instanceof Double)) {
                return Optional.empty();
            }
            lastCst = (Number) cst;
        } else {
            return Optional.empty();
        }

        currentInsn = nextFMAInsn(currentInsn);
        if (currentInsn.getOpcode() == FMUL || currentInsn.getOpcode() == DMUL) {
            factor = Objects.requireNonNull(lastCst);
            lastCst = null;
        } else if (currentInsn.getOpcode() == FDIV || currentInsn.getOpcode() == DDIV) {
            if (!BytecodeHelper.isConstant(BytecodeHelper.previousNormalCodeInstruction(currentInsn))) {
                //constant / height ???
                return Optional.empty();
            }
            factor = 1.0d / lastCst.doubleValue();
            lastCst = null;
        } else if (currentInsn.getOpcode() == FADD || currentInsn.getOpcode() == DADD) {
            offset = Objects.requireNonNull(lastCst);
            lastCst = null;
        } else {
            return Optional.empty();
        }

        currentInsn = nextFMAInsn(currentInsn);
        if (currentInsn.getOpcode() == returnOpcode) {
            return Optional.of(new FieldFMAInfo(field, factor, offset));
        } else if (BytecodeHelper.isConstant(currentInsn)) {
            Object cst = BytecodeHelper.decodeConstant(currentInsn);
            if (!(cst instanceof Float || cst instanceof Double)) {
                return Optional.empty();
            }
            lastCst = (Number) cst;
        } else {
            return Optional.empty();
        }

        currentInsn = nextFMAInsn(currentInsn);
        if (currentInsn.getOpcode() == FADD || currentInsn.getOpcode() == DADD) {
            offset = Objects.requireNonNull(lastCst);
            lastCst = null;
        } else {
            return Optional.empty();
        }

        currentInsn = nextFMAInsn(currentInsn);
        if (currentInsn.getOpcode() == returnOpcode) {
            return Optional.of(new FieldFMAInfo(field, factor, offset));
        } else {
            return Optional.empty();
        }
    }

    private static AbstractInsnNode nextFMAInsn(AbstractInsnNode currentInsn) {
        do {
            currentInsn = BytecodeHelper.nextNormalCodeInstruction(currentInsn);
        } while (currentInsn.getOpcode() == F2D || currentInsn.getOpcode() == D2F);
        return currentInsn;
    }
}
