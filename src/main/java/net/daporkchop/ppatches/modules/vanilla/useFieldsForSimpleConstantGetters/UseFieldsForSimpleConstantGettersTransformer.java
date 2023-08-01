package net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import jdk.internal.org.objectweb.asm.Type;
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

import java.nio.file.Files;
import java.nio.file.Paths;
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
            //TODO: remove overrides of Biome#getBiomeClass() in BiomeForest and BiomeSavanna, they're redundant
            ((Biome) null).ignorePlayerSpawnSuitability();

            //TODO: there are some more in block which could be implemented better if we could make some more assumptions about Material and IBlockState methods
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
            ((Entity) null).getCollisionBoundingBox(); //TODO: decide whether or not to keep this
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

            //TODO: there are some more in block which could be implemented better if we could make some more assumptions about the overloads which add an ItemStack argument
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
            ((WorldProvider) null).hasSkyLight();
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
        ClassReader reader = new ClassReader(Files.readAllBytes(Paths.get(UseFieldsForSimpleConstantGettersTransformer.class.getResource("UseFieldsForSimpleConstantGettersTransformer$" + name + ".class").toURI())));
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

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
    private final Map<String, Set<String>> alreadyImplementedWithFieldMethods = readStaticMethodList("Dummy_AlreadyImplementedWithFieldMethods");

    @SuppressWarnings("UnstableApiUsage")
    private final Set<String> rootClasses = Stream.concat(this.canBeReplacedWithFieldMethods.keySet().stream(), this.alreadyImplementedWithFieldMethods.keySet().stream()).collect(ImmutableSet.toImmutableSet());

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
        int changedFlags = 0;

        if (this.rootClasses.contains(classNode.name)) {
            changedFlags |= this.transformRootClass(classNode);
        } else {
            String rootClassName = this.getParentRootClass(classNode.name);
            if (rootClassName != null) {
                changedFlags |= this.transformDerivedClass(classNode, rootClassName);
            }
        }

        return changedFlags;
    }

    private synchronized int transformRootClass(ClassNode classNode) {
        int changedFlags = 0;

        Set<String> canBeReplacedWithFieldMethods = this.canBeReplacedWithFieldMethods.getOrDefault(classNode.name, Collections.emptySet());
        Set<String> alreadyImplementedWithFieldMethods = this.alreadyImplementedWithFieldMethods.getOrDefault(classNode.name, Collections.emptySet());

        for (MethodNode methodNode : classNode.methods) {
            String combinedDesc = methodNode.name + methodNode.desc;
            if (canBeReplacedWithFieldMethods.contains(combinedDesc)) {
                Optional<AbstractInsnNode> loadConstantReturnValueInsn = getConstantReturnValue(methodNode);
                if (loadConstantReturnValueInsn.isPresent()) { //the method is still implemented with a constant return value
                    String returnTypeDesc = Type.getReturnType(methodNode.desc).getDescriptor();
                    String fieldName = "$ppatches_" + methodNode.name;

                    //add a new field to store the value
                    classNode.visitField(ACC_PROTECTED, fieldName, returnTypeDesc, null, null);

                    //add code to all constructors which initializes the new field to the constant value
                    for (MethodNode ctor : BytecodeHelper.findMethod(classNode, "<init>")) {
                        MethodInsnNode superCtorInvocation = BytecodeHelper.findSuperCtorInvocationInCtor(classNode, ctor).orElse(null);
                        if (superCtorInvocation != null) {
                            ctor.maxStack = Math.max(ctor.maxStack, 2); //extend the stack if necessary
                            BytecodeHelper.insertAfter(superCtorInvocation, ctor.instructions,
                                    new VarInsnNode(ALOAD, 0),
                                    loadConstantReturnValueInsn.get().clone(null),
                                    new FieldInsnNode(PUTFIELD, classNode.name, fieldName, returnTypeDesc));
                        }
                    }

                    //rewrite the original method to return the value of the newly added field instead of the constant value
                    BytecodeHelper.replace(loadConstantReturnValueInsn.get(), methodNode.instructions,
                            new VarInsnNode(ALOAD, 0),
                            new FieldInsnNode(GETFIELD, classNode.name, fieldName, returnTypeDesc));

                    changedFlags |= CHANGED_MANDATORY;
                } else {
                    PPatchesMod.LOGGER.error("Expected method L{};{}{} to return a constant value, but it doesn't! Trivial overrides won't be optimized away.",
                            classNode.name, methodNode.name, methodNode.desc);

                    //we can safely modify this set, as we're currently synchronized on the entire transformer, and since we ensure that candidate subclasses will cause their
                    //  superclass to be loaded before transforming anything themselves, we know that no incompatible changes will be made)
                    Preconditions.checkState(canBeReplacedWithFieldMethods.remove(methodNode.name + methodNode.desc));
                }
            } else if (alreadyImplementedWithFieldMethods.contains(combinedDesc)) {
                //TODO
            }
        }

        return changedFlags;
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

        int changedFlags = 0;

        Set<String> canBeReplacedWithFieldMethods = this.canBeReplacedWithFieldMethods.getOrDefault(rootClassName, Collections.emptySet());
        Set<String> alreadyImplementedWithFieldMethods = this.alreadyImplementedWithFieldMethods.getOrDefault(rootClassName, Collections.emptySet());

        ImmutableSet.Builder<String> notRemovedMethods = ImmutableSet.builder();

        for (Iterator<MethodNode> itr = classNode.methods.iterator(); itr.hasNext(); ) {
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

                        String returnTypeDesc = Type.getReturnType(methodNode.desc).getDescriptor();
                        String fieldName = "$ppatches_" + methodNode.name;

                        //add code to all constructors which initializes the new field to the constant value
                        for (MethodNode ctor : BytecodeHelper.findMethod(classNode, "<init>")) {
                            MethodInsnNode superCtorInvocation = BytecodeHelper.findSuperCtorInvocationInCtor(classNode, ctor).orElse(null);
                            if (superCtorInvocation != null) {
                                ctor.maxStack = Math.max(ctor.maxStack, 2); //extend the stack if necessary
                                BytecodeHelper.insertAfter(superCtorInvocation, ctor.instructions,
                                        new VarInsnNode(ALOAD, 0),
                                        loadConstantReturnValueInsn.get().clone(null),
                                        new FieldInsnNode(PUTFIELD, rootClassName, fieldName, returnTypeDesc));
                            }
                        }

                        //remove the overridden method
                        itr.remove();

                        changedFlags |= CHANGED_MANDATORY;
                    } else {
                        PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it wouldn't inherit the implementation from {})", methodNode.name, methodNode.desc, classNode.name, rootClassName);
                        notRemovedMethods.add(combinedDesc.intern());
                    }
                } else { //the overridden method doesn't return a simple constant, we'll leave it be
                    PPatchesMod.LOGGER.info("not removing override of {}{} from class {} (it doesn't return a trivial constant)", methodNode.name, methodNode.desc, classNode.name);
                    notRemovedMethods.add(combinedDesc.intern());
                }
            } else if (alreadyImplementedWithFieldMethods.contains(combinedDesc)) {
                //TODO
            }
        }

        this.notRemovedMethods.put(classNode.name.intern(), notRemovedMethods.build());

        return changedFlags;
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
}
