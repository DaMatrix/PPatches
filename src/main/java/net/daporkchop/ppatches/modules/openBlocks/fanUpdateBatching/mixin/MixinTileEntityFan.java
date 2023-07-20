package net.daporkchop.ppatches.modules.openBlocks.fanUpdateBatching.mixin;

import com.google.common.base.Predicate;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.modules.openBlocks.fanUpdateBatching.FanUpdateBatchGroup;
import net.daporkchop.ppatches.util.compat.cubicChunks.CubicChunksCompatHelper;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openblocks.common.tileentity.TileEntityFan", remap = false)
abstract class MixinTileEntityFan extends MixinSyncedTileEntity {
    private static final MethodHandle TILEENTITYFAN_GETPOWER;

    static {
        try {
            Class<?> tileEntityFanClass = Class.forName("openblocks.common.tileentity.TileEntityFan");
            Class<?> syncableByteClass = Class.forName("openmods.sync.SyncableByte");

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle tileEntityFan_power_getter = lookup.findGetter(tileEntityFanClass, "power", syncableByteClass);
            MethodHandle syncableByte_get = lookup.findVirtual(syncableByteClass, "get", MethodType.methodType(byte.class));
            TILEENTITYFAN_GETPOWER = MethodHandles.filterReturnValue(tileEntityFan_power_getter, syncableByte_get);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("PPatches: openblocks.fanUpdateBatching failed to initialize", e);
        }
    }

    @Dynamic
    @Shadow
    private AxisAlignedBB getEntitySearchBoundingBox() {
        return null;
    }

    @Unique
    private transient FanUpdateBatchGroup<MixinTileEntityFan> ppatches_fanUpdateBatching_group;

    @Dynamic
    @Redirect(
            method = {
                    "Lopenblocks/common/tileentity/TileEntityFan;update()V",
                    "Lopenblocks/common/tileentity/TileEntityFan;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At(value = "INVOKE",
                    target = "Lopenblocks/common/tileentity/TileEntityFan;getEntitySearchBoundingBox()Lnet/minecraft/util/math/AxisAlignedBB;"),
            allow = 1, require = 1)
    @SuppressWarnings("unchecked")
    private AxisAlignedBB ppatches_fanUpdateBatching_update_avoidComputingBoundingBoxIfPrecomputed(MixinTileEntityFan _this) {
        return this.ppatches_fanUpdateBatching_group != null
                ? this.ppatches_fanUpdateBatching_group
                : (this.ppatches_fanUpdateBatching_group = (FanUpdateBatchGroup<MixinTileEntityFan>) this.getEntitySearchBoundingBox());
    }

    @Dynamic
    @Group(name = "tryReturnCachedEntitiesOrCache", min = 1, max = 1)
    @Redirect(
            method = {
                    "Lopenblocks/common/tileentity/TileEntityFan;update()V",
                    "Lopenblocks/common/tileentity/TileEntityFan;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getEntitiesWithinAABB(Ljava/lang/Class;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;", remap = true))
    private List<Entity> ppatches_fanUpdateBatching_update_tryReturnCachedEntitiesOrCache(World world, Class<? extends Entity> entityClass, AxisAlignedBB aabb) {
        long currentTime = this.world.getTotalWorldTime();

        if (!this.ppatches_fanUpdateBatching_group.hasCurrentTickEntities(currentTime)) {
            this.ppatches_fanUpdateBatching_group.setCurrentTickEntities(world.getEntitiesWithinAABB(entityClass, aabb), currentTime);
        }

        return this.ppatches_fanUpdateBatching_group.consumeCurrentTickEntities(currentTime);
    }

    /**
     * This is required if the {@code fanEntityOptimization} module is also enabled.
     */
    @Dynamic
    @Group(name = "tryReturnCachedEntitiesOrCache")
    @Redirect(
            method = {
                    "Lopenblocks/common/tileentity/TileEntityFan;update()V",
                    "Lopenblocks/common/tileentity/TileEntityFan;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getEntitiesWithinAABB(Ljava/lang/Class;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;", remap = true))
    private List<Entity> ppatches_fanUpdateBatching_update_tryReturnCachedEntitiesOrCache(World world, Class<? extends Entity> entityClass, AxisAlignedBB aabb, Predicate<? super Entity> predicate) {
        long currentTime = this.world.getTotalWorldTime();

        if (!this.ppatches_fanUpdateBatching_group.hasCurrentTickEntities(currentTime)) {
            this.ppatches_fanUpdateBatching_group.setCurrentTickEntities(world.getEntitiesWithinAABB(entityClass, aabb, predicate), currentTime);
        }

        return this.ppatches_fanUpdateBatching_group.consumeCurrentTickEntities(currentTime);
    }

    @Dynamic
    @Redirect(method = "Lopenblocks/common/tileentity/TileEntityFan;getEntitySearchBoundingBox()Lnet/minecraft/util/math/AxisAlignedBB;",
            at = @At(value = "INVOKE",
                    target = "Lopenmods/utils/BlockUtils;aabbOffset(Lnet/minecraft/util/math/BlockPos;DDDDDD)Lnet/minecraft/util/math/AxisAlignedBB;"),
            allow = 1, require = 1)
    private AxisAlignedBB ppatches_fanUpdateBatching_getEntitySearchBoundingBox_findFansInBatch(BlockPos ownPos, double x1, double y1, double z1, double x2, double y2, double z2) throws Throwable {
        assert this.ppatches_fanUpdateBatching_group == null : "getEntitySearchBoundingBox was called even though a batch group was already computed!";
        assert (byte) TILEENTITYFAN_GETPOWER.invokeExact(this) != 0 : "getEntitySearchBoundingBox was called even though this fan has no power!";

        int minX = ownPos.getX();
        int maxX = ownPos.getX();
        int minY = ownPos.getY();
        int maxY = ownPos.getY();
        int minZ = ownPos.getZ();
        int maxZ = ownPos.getZ();

        List<MixinTileEntityFan> fansInGroup = new ArrayList<>();
        fansInGroup.add(this);

        for (TileEntity tileEntity : this.ppatches_fanUpdateBatching_candidateGroupTileEntities()) {
            if (!(tileEntity instanceof MixinTileEntityFan) || tileEntity == this) {
                continue;
            }

            MixinTileEntityFan fanTileEntity = (MixinTileEntityFan) tileEntity;
            if (fanTileEntity.ppatches_fanUpdateBatching_group != null
                || (byte) TILEENTITYFAN_GETPOWER.invokeExact(fanTileEntity) == 0) { //unpowered fans can't be included in the group
                continue;
            }

            //expand bounding box to contain the newly discovered fan
            BlockPos fanPos = fanTileEntity.getPos();
            minX = Math.min(minX, fanPos.getX());
            maxX = Math.max(maxX, fanPos.getX());
            minY = Math.min(minY, fanPos.getY());
            maxY = Math.max(maxY, fanPos.getY());
            minZ = Math.min(minZ, fanPos.getZ());
            maxZ = Math.max(maxZ, fanPos.getZ());

            fansInGroup.add(fanTileEntity);
        }

        return new FanUpdateBatchGroup<>(fansInGroup.toArray(new MixinTileEntityFan[0]),
                minX + x1, minY + y1, minZ + z1, maxX + x2, maxY + y2, maxZ + z2);
    }

    @Dynamic
    @Redirect(method = "Lopenblocks/common/tileentity/TileEntityFan;getEntitySearchBoundingBox()Lnet/minecraft/util/math/AxisAlignedBB;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/AxisAlignedBB;grow(D)Lnet/minecraft/util/math/AxisAlignedBB;", remap = true),
            allow = 1, require = 1)
    private AxisAlignedBB ppatches_fanUpdateBatching_getEntitySearchBoundingBox_growBatchGroup(AxisAlignedBB aabb, double amount) {
        @SuppressWarnings("unchecked")
        FanUpdateBatchGroup<MixinTileEntityFan> group = new FanUpdateBatchGroup<>(((FanUpdateBatchGroup<MixinTileEntityFan>) aabb).fans,
                aabb.minX - amount, aabb.minY - amount, aabb.minZ - amount,
                aabb.maxX + amount, aabb.maxY + amount, aabb.maxZ + amount);

        //now that the group has been fully constructed, we can mark all the fans as being part of the group
        for (MixinTileEntityFan fan : group.fans) {
            assert fan.ppatches_fanUpdateBatching_group == null : "fan cannot be a group member since it's already been attached to a group!";
            fan.ppatches_fanUpdateBatching_group = group;
        }

        return group;
    }

    @Unique
    @SneakyThrows
    private Collection<TileEntity> ppatches_fanUpdateBatching_candidateGroupTileEntities() {
        Map<BlockPos, TileEntity> tileEntityMap;
        if (CubicChunksCompatHelper.ICUBICWORLD != null && CubicChunksCompatHelper.ICUBICWORLD.isInstance(this.world)
            && (boolean) CubicChunksCompatHelper.ICUBICWORLD_ISCUBICWORLD.invokeExact(this.world)) { //special handling for cubic chunks!
            //noinspection unchecked
            tileEntityMap = (Map<BlockPos, TileEntity>) CubicChunksCompatHelper.ICUBICWORLD_GETCUBEFROMBLOCKCOORDS_THEN_GETTILEENTITYMAP.invokeExact(this.world, this.getPos());
        } else {
            tileEntityMap = this.world.getChunk(this.getPos()).getTileEntityMap();
        }

        return tileEntityMap.values();
    }

    @Unique
    private void ppatches_fanUpdateBatching_destroyGroup() {
        if (this.ppatches_fanUpdateBatching_group != null) { //remove all fans from the group
            for (MixinTileEntityFan fan : this.ppatches_fanUpdateBatching_group.fans) {
                fan.ppatches_fanUpdateBatching_group = null;
            }
        }

        //destroy groups of any neighboring fans as well, in order to allow them to be merged with the current fan
        for (TileEntity tileEntity : this.ppatches_fanUpdateBatching_candidateGroupTileEntities()) {
            if (tileEntity instanceof MixinTileEntityFan) {
                MixinTileEntityFan fanTileEntity = (MixinTileEntityFan) tileEntity;
                if (fanTileEntity.ppatches_fanUpdateBatching_group != null) {
                    for (MixinTileEntityFan fan : fanTileEntity.ppatches_fanUpdateBatching_group.fans) {
                        fan.ppatches_fanUpdateBatching_group = null;
                    }

                    //if we managed to remove one other fan group from this candidate group, there will be no more to remove (since the candidate group covers all tile
                    //  entities in this chunk/cube)
                    break;
                }
            }
        }
    }

    @Dynamic
    @Inject(method = "Lopenblocks/common/tileentity/TileEntityFan;updateRedstone()V",
            at = @At(value = "INVOKE",
                    target = "Lopenblocks/common/tileentity/TileEntityFan;sync()V"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private void ppatches_fanUpdateBatching_updateRedstone_destroyGroup(CallbackInfo ci, int power) {
        this.ppatches_fanUpdateBatching_destroyGroup();
    }

    /**
     * This method serves as a dummy injection point; it will be silently discarded if another mixin targeting the same class adds the same override.
     */
    @Unique
    @Override
    public void onChunkUnload() {
        //no-op
    }

    @Dynamic
    @Inject(method = "Lopenblocks/common/tileentity/TileEntityFan;onChunkUnload()V", //don't need to explicitly add an obfuscated method name here, since the base method is added by Forge
            at = @At(value = "HEAD"),
            allow = 1, require = 1)
    private void ppatches_fanUpdateBatching_onChunkUnload_destroyGroup(CallbackInfo ci) {
        this.ppatches_fanUpdateBatching_destroyGroup();
    }
}
