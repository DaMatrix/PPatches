package net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.mixin;

import net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.LoadQuarryChunksHelper;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.quarry.TileQuarry", remap = false)
abstract class MixinTileQuarry extends MixinTilePower {
    @Shadow
    ChunkPos chunkPos;

    @Unique
    private boolean ppatches_loadQuarryChunks_quarryChunksLoaded;

    @Unique
    private ForgeChunkManager.Ticket ppatches_loadQuarryChunks_activeTicket;

    @Unique
    private void ppatches_loadQuarryChunks_quarryInactive() {
        checkState(!this.world.isRemote, "invoked from client???");

        this.ppatches_loadQuarryChunks_activeTicket = LoadQuarryChunksHelper.unloadQuarryChunks(this.chunkPos, this.ppatches_loadQuarryChunks_activeTicket);
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
    private void ppatches_loadQuarryChunks_update_handleQuarryInactive(CallbackInfo ci) {
        if (this.ppatches_loadQuarryChunks_quarryChunksLoaded) {
            this.ppatches_loadQuarryChunks_quarryChunksLoaded = false;
            this.ppatches_loadQuarryChunks_quarryInactive();
        }
    }

    @Dynamic
    @Inject(
            method = "Lcom/rwtema/extrautils2/quarry/TileQuarry;getNewChunk()V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_loadQuarryChunks_getNewChunk_handleQuarryFinishChunk(CallbackInfo ci) {
        if (this.ppatches_loadQuarryChunks_quarryChunksLoaded) {
            this.ppatches_loadQuarryChunks_quarryChunksLoaded = false;
            this.ppatches_loadQuarryChunks_quarryInactive();
        }
    }

    @Dynamic
    @Inject(method = "Lcom/rwtema/extrautils2/quarry/TileQuarry;breakBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V", //don't need to explicitly add an obfuscated method name here, since the base method is added by Forge
            at = @At(value = "INVOKE",
                    target = "Lcom/rwtema/extrautils2/dimensions/workhousedim/WorldProviderSpecialDim;releaseChunk(Lnet/minecraft/util/math/ChunkPos;)V"),
            allow = 1, require = 1)
    private void ppatches_loadQuarryChunks_breakBlock_releaseTicket(CallbackInfo ci) {
        if (this.ppatches_loadQuarryChunks_quarryChunksLoaded) {
            this.ppatches_loadQuarryChunks_quarryChunksLoaded = false;
            this.ppatches_loadQuarryChunks_quarryInactive();
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
    private void ppatches_loadQuarryChunks_invalidate_releaseTicket(CallbackInfo ci) {
        if (this.ppatches_loadQuarryChunks_quarryChunksLoaded) {
            this.ppatches_loadQuarryChunks_quarryChunksLoaded = false;
            this.ppatches_loadQuarryChunks_quarryInactive();
        }
    }

    @Unique
    private void ppatches_loadQuarryChunks_quarryActive() {
        checkState(!this.world.isRemote, "invoked from client???");
        checkState(this.chunkPos != null, "quarry doesn't have an active position???");

        this.ppatches_loadQuarryChunks_activeTicket = LoadQuarryChunksHelper.loadQuarryChunks(this.chunkPos);
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
    private void ppatches_loadQuarryChunks_update_handleQuarryActive(CallbackInfo ci) {
        if (this.chunkPos != null && !this.ppatches_loadQuarryChunks_quarryChunksLoaded) {
            this.ppatches_loadQuarryChunks_quarryChunksLoaded = true;
            this.ppatches_loadQuarryChunks_quarryActive();
        }
    }

    @Dynamic
    @Inject(
            method = "Lcom/rwtema/extrautils2/quarry/TileQuarry;getNextChunk()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_loadQuarryChunks_getNextChunk_handleQuarryBeginNewChunk(CallbackInfo ci) {
        if (!this.ppatches_loadQuarryChunks_quarryChunksLoaded) {
            this.ppatches_loadQuarryChunks_quarryChunksLoaded = true;
            this.ppatches_loadQuarryChunks_quarryActive();
        }
    }
}
