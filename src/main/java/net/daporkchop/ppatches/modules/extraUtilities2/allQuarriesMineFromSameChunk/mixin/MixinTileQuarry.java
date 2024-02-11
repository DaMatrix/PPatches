package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.mixin;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.QuarryGroup;
import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.util.IMixinTileQuarry_AllQuarriesMineFromSameChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.quarry.TileQuarry", remap = false)
abstract class MixinTileQuarry extends MixinTilePower implements IMixinTileQuarry_AllQuarriesMineFromSameChunk {
    /**
     * This is the ID of the biome we last mined from.
     */
    @Dynamic
    @Shadow
    Biome lastBiome;
    /**
     * This is basically just a key used for acquiring and releasing chunks from WorldProviderSpecialDim, it's never actually used directly.
     */
    @Dynamic
    @Shadow
    ChunkPos posKey;
    /**
     * This is the actual chunk we're mining from.
     * <p>
     * It's computed by {@code WorldProviderSpecialDim.adjustChunkRef(posKey)} during {@code getNextChunk()}, and used in {@code setBlockPos()} to
     * compute the coordinates for {@code this.digPos}.
     */
    @Dynamic
    @Shadow
    ChunkPos chunkPos;
    /**
     * This is the block we're mining at
     */
    @Dynamic
    @Shadow
    BlockPos.MutableBlockPos digPos;

    @Unique
    private static final MethodHandle ppatches_allQuarriesMineFromSameChunk_getCurBlockLocation_handle;
    @Unique
    private static final MethodHandle ppatches_allQuarriesMineFromSameChunk_setCurBlockLocation_handle;
    @Unique
    private static final MethodHandle ppatches_allQuarriesMineFromSameChunk_getBiomeHandlerBiome_handle;

    static {
        try {
            Class<?> NbtSerializable$Int = Class.forName("com.rwtema.extrautils2.utils.datastructures.NBTSerializable$Int");
            MethodHandle curBlockLocationGetter = MethodHandles.lookup().findGetter(MixinTileQuarry.class, "curBlockLocation", NbtSerializable$Int);
            ppatches_allQuarriesMineFromSameChunk_getCurBlockLocation_handle = MethodHandles.filterReturnValue(
                    curBlockLocationGetter,
                    MethodHandles.lookup().findGetter(NbtSerializable$Int, "value", int.class));
            ppatches_allQuarriesMineFromSameChunk_setCurBlockLocation_handle = MethodHandles.collectArguments(
                    MethodHandles.lookup().findSetter(NbtSerializable$Int, "value", int.class),
                    0, curBlockLocationGetter);

            Class<?> ItemBiomeMarker$ItemBiomeHandler = Class.forName("com.rwtema.extrautils2.items.ItemBiomeMarker$ItemBiomeHandler");
            MethodHandle biomeHandlerGetter = MethodHandles.lookup().findGetter(MixinTileQuarry.class, "biomeHandler", ItemBiomeMarker$ItemBiomeHandler);
            ppatches_allQuarriesMineFromSameChunk_getBiomeHandlerBiome_handle = MethodHandles.filterReturnValue(
                    biomeHandlerGetter,
                    MethodHandles.lookup().findVirtual(ItemBiomeMarker$ItemBiomeHandler, "getBiome", MethodType.methodType(Biome.class)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Unique
    @SneakyThrows
    private int ppatches_allQuarriesMineFromSameChunk_getCurBlockLocation() {
        return (int) ppatches_allQuarriesMineFromSameChunk_getCurBlockLocation_handle.invokeExact(this);
    }

    @Unique
    @SneakyThrows
    private void ppatches_allQuarriesMineFromSameChunk_setCurBlockLocation(int value) {
        ppatches_allQuarriesMineFromSameChunk_setCurBlockLocation_handle.invokeExact(this, value);
    }

    @Unique
    @SneakyThrows
    private Biome ppatches_allQuarriesMineFromSameChunk_getBiomeHandlerBiome() {
        return (Biome) ppatches_allQuarriesMineFromSameChunk_getBiomeHandlerBiome_handle.invokeExact(this);
    }

    @Unique
    private QuarryGroup ppatches_allQuarriesMineFromSameChunk_group;

    @Override
    public final QuarryGroup ppatches_allQuarriesMineFromSameChunk_cmpxchgGroup(QuarryGroup expect, QuarryGroup update) {
        QuarryGroup oldGroup = this.ppatches_allQuarriesMineFromSameChunk_group;
        if (oldGroup == expect) {
            this.ppatches_allQuarriesMineFromSameChunk_group = update;
        }
        return oldGroup;
    }

    @Dynamic
    @Redirect(
            method = {
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;update()V",
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a pseudo class
            },
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;posKey:Lnet/minecraft/util/math/ChunkPos;"),
            allow = 1, require = 1)
    private ChunkPos ppatches_allQuarriesMineFromSameChunk_update_usePosKeyFromGroup(MixinTileQuarry quarry) {
        return this.ppatches_allQuarriesMineFromSameChunk_group.posKey;
    }

    @Dynamic
    @Redirect(
            method = {
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;update()V",
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a pseudo class
            },
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;digPos:Lnet/minecraft/util/math/BlockPos$MutableBlockPos;"),
            allow = 7, require = 7)
    private BlockPos.MutableBlockPos ppatches_allQuarriesMineFromSameChunk_update_useDigPosFromGroup(MixinTileQuarry quarry) {
        return this.ppatches_allQuarriesMineFromSameChunk_group.digPos;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Dynamic
    @Overwrite
    private void setBlockPos() {
        this.ppatches_allQuarriesMineFromSameChunk_group.setBlockPos();
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Dynamic
    @Overwrite
    private void advance() {
        this.ppatches_allQuarriesMineFromSameChunk_group.advance();
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Dynamic
    @Overwrite
    private void getNewChunk() {
        //the only place this is called is when the biome marker is changed, so what we'll do is simply remove ourself from the current group (if any) and defer
        // the call to getNextChunk() until the quarry is actually ticked again
        this.ppatches_allQuarriesMineFromSameChunk_removeFromGroup();
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Dynamic
    @Overwrite
    private void getNextChunk() {
        this.ppatches_allQuarriesMineFromSameChunk_group.getNextChunk();
    }

    @Dynamic
    @ModifyVariable(method = "Lcom/rwtema/extrautils2/quarry/TileQuarry;getY(I)I",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private int patches_allQuarriesMineFromSameChunk_getY_useGroupY(int value) {
        if (this.ppatches_allQuarriesMineFromSameChunk_group != null) {
            value = this.ppatches_allQuarriesMineFromSameChunk_group.curBlockLocation;
        }
        return value;
    }

    @Dynamic
    @Inject(
            method = {
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;update()V",
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a pseudo class
            },
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;hasNearbyBlocks()Z"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getTotalWorldTime()J", remap = true)),
            at = @At("RETURN"),
            allow = 2, require = 2)
    private void ppatches_allQuarriesMineFromSameChunk_update_handleQuarryInactive(CallbackInfo ci) {
        if (this.ppatches_allQuarriesMineFromSameChunk_group != null) {
            this.ppatches_allQuarriesMineFromSameChunk_removeFromGroup();
        }
    }

    @Dynamic
    @Inject(method = "Lcom/rwtema/extrautils2/quarry/TileQuarry;breakBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V", //don't need to explicitly add an obfuscated method name here, since the base method is added by Forge
            at = @At(value = "INVOKE",
                    target = "Lcom/rwtema/extrautils2/dimensions/workhousedim/WorldProviderSpecialDim;releaseChunk(Lnet/minecraft/util/math/ChunkPos;)V"),
            allow = 1, require = 1)
    private void ppatches_allQuarriesMineFromSameChunk_breakBlock_removeFromGroup(CallbackInfo ci) {
        if (this.ppatches_allQuarriesMineFromSameChunk_group != null) {
            this.ppatches_allQuarriesMineFromSameChunk_removeFromGroup();
        }
    }

    /**
     * This method serves as a dummy injection point; it will be silently discarded if another mixin targeting the same class adds the same override.
     */
    @Unique
    @Override
    public void invalidate() {
        super.invalidate();
    }

    /**
     * This method serves as a dummy injection point; it will be silently discarded if another mixin targeting the same class adds the same override.
     */
    @Unique
    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
    }

    @Dynamic
    @Inject(
            method = {
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;invalidate()V",
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;func_145843_s()V",
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;onChunkUnload()V", //don't need to explicitly add an obfuscated method name here, since the base method is added by Forge
            },
            at = @At(value = "HEAD"),
            allow = 2, require = 2)
    private void ppatches_allQuarriesMineFromSameChunk_invalidate_removeFromGroup(CallbackInfo ci) {
        if (this.ppatches_allQuarriesMineFromSameChunk_group != null) {
            this.ppatches_allQuarriesMineFromSameChunk_removeFromGroup();
        }
    }

    @Dynamic
    @Inject(
            method = {
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;update()V",
                    "Lcom/rwtema/extrautils2/quarry/TileQuarry;func_73660_a()V", //mixin plugin can't automatically generate refmaps for this method, since it's a pseudo class
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getTotalWorldTime()J", remap = true,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    private void ppatches_allQuarriesMineFromSameChunk_update_handleQuarryActive(CallbackInfo ci) {
        if (this.ppatches_allQuarriesMineFromSameChunk_group == null) {
            this.ppatches_allQuarriesMineFromSameChunk_addToGroup();
        }
    }

    @Unique
    private void ppatches_allQuarriesMineFromSameChunk_addToGroup() {
        if (this.ppatches_allQuarriesMineFromSameChunk_group == null) {
            QuarryGroup.quarryGroupFor((WorldServer) this.world, this.ppatches_allQuarriesMineFromSameChunk_getBiomeHandlerBiome()).addQuarry(this);
        }
    }

    @Unique
    private void ppatches_allQuarriesMineFromSameChunk_removeFromGroup() {
        if (this.ppatches_allQuarriesMineFromSameChunk_group != null) {
            this.ppatches_allQuarriesMineFromSameChunk_group.removeQuarry(this);
        }
    }

}
