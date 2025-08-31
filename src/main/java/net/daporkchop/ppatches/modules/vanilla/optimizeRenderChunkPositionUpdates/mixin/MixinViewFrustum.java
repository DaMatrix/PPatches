package net.daporkchop.ppatches.modules.vanilla.optimizeRenderChunkPositionUpdates.mixin;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.util.MCConstants;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.BooleanSupplier;

/**
 * @author DaPorkchop_
 */
@Mixin(ViewFrustum.class)
abstract class MixinViewFrustum {
    @Shadow
    protected int countChunksX;
    @Shadow
    protected int countChunksY;
    @Shadow
    protected int countChunksZ;

    @Shadow
    public RenderChunk[] renderChunks;

    @Unique
    private int ppatches_optimizeRenderChunkPositionUpdates_oldViewX = Integer.MAX_VALUE; //sufficiently large default value that it can never intersect with real values
    @Unique
    private int ppatches_optimizeRenderChunkPositionUpdates_oldViewZ = Integer.MAX_VALUE;

    @Unique
    private static int ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(int index, int countChunks, int origin, int min) {
        int coord = origin + index;
        if (coord < min) {
            coord += countChunks;
        }
        return coord * MCConstants.CHUNK_SIZE;
    }

    @Inject(method = "updateChunkPositions(DD)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/ViewFrustum;countChunksX:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_optimizeRenderChunkPositionUpdates_updateChunkPositions_tryFast(double viewEntityX, double viewEntityZ, CallbackInfo ci) {
        int countChunksX = this.countChunksX;
        int countChunksY = this.countChunksY;
        int countChunksZ = this.countChunksZ;
        RenderChunk[] renderChunks = this.renderChunks;

        //Since we know the chunk count will always be positive and odd, we can do all the math below in chunk coordinates instead of block coordinates
        //  because the 8-block offset used by vanilla ends up cancelling out with the subsequent addition by (countChunks * 16 / 2).

        Preconditions.checkState(
                ((countChunksX & countChunksZ) & 1) != 0,
                "ppatches.optimizeRenderChunkPositionUpdates: countChunksX (%s) and countChunksZ (%s) must both be odd",
                countChunksX, countChunksZ);

        //divide while rounding up
        int viewX = MathHelper.intFloorDiv(MathHelper.floor(viewEntityX) - 1, MCConstants.CHUNK_SIZE) + 1;
        int viewZ = MathHelper.intFloorDiv(MathHelper.floor(viewEntityZ) - 1, MCConstants.CHUNK_SIZE) + 1;

        int minX = viewX - ((countChunksX + 1) >> 1);
        int minZ = viewZ - ((countChunksZ + 1) >> 1);

        int originX = MathHelper.intFloorDiv(minX, countChunksX) * countChunksX;
        int originZ = MathHelper.intFloorDiv(minZ, countChunksZ) * countChunksZ;

        //use longs here just in case the int values overflow (they shouldn't ever, but i want to play it safe)
        long changeX = (long) viewX - this.ppatches_optimizeRenderChunkPositionUpdates_oldViewX;
        long changeZ = (long) viewZ - this.ppatches_optimizeRenderChunkPositionUpdates_oldViewZ;
        this.ppatches_optimizeRenderChunkPositionUpdates_oldViewX = viewX;
        this.ppatches_optimizeRenderChunkPositionUpdates_oldViewZ = viewZ;

        if (Math.abs(changeX) <= 1 && Math.abs(changeZ) <= 1) {
            //fast-path: the camera has moved by at most one chunk so we only need to perform updates along a 2d plane

            if (changeX != 0) {
                //we'll need to update one layer of RenderChunks perpendicular to the YZ plane
                int xIndex = Math.floorMod(changeX < 0 ? minX - originX : minX - originX - 1, countChunksX);
                int blockX = ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(xIndex, countChunksX, originX, minX);

                for (int zIndex = 0; zIndex < countChunksZ; ++zIndex) {
                    int idxZ = zIndex * countChunksY * countChunksX;
                    int blockZ = ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(zIndex, countChunksZ, originZ, minZ);

                    for (int yIndex = 0; yIndex < countChunksY; ++yIndex) {
                        int idxYZ = idxZ + yIndex * countChunksX;
                        int blockY = yIndex * MCConstants.CHUNK_SIZE;

                        renderChunks[idxYZ + xIndex].setPosition(blockX, blockY, blockZ);
                    }
                }
            }

            if (changeZ != 0) { //we'll need to update one layer of RenderChunks perpendicular to the XY plane
                int zIndex = Math.floorMod(changeZ < 0 ? minZ - originZ : minZ - originZ - 1, countChunksZ);
                int blockZ = ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(zIndex, countChunksZ, originZ, minZ);
                int idxZ = zIndex * countChunksY * countChunksX;

                for (int yIndex = 0; yIndex < countChunksY; ++yIndex) {
                    int idxYZ = idxZ + yIndex * countChunksX;
                    int blockY = yIndex * MCConstants.CHUNK_SIZE;

                    for (int xIndex = 0; xIndex < countChunksX; ++xIndex) {
                        int blockX = ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(xIndex, countChunksX, originX, minX);

                        renderChunks[idxYZ + xIndex].setPosition(blockX, blockY, blockZ);
                    }
                }
            }

            ci.cancel();

            //run the original loop to double-check that all RenderChunks are in the correct position
            //  (doing this cancels out any benefits from skipping unchanged RenderChunks, but only runs with assertions enabled)
            assert ((BooleanSupplier) () -> {
                int i = MathHelper.floor(viewEntityX) - MCConstants.CHUNK_SIZE / 2;
                int j = MathHelper.floor(viewEntityZ) - MCConstants.CHUNK_SIZE / 2;
                int d = this.countChunksX * MCConstants.CHUNK_SIZE;

                for (int xIndex = 0; xIndex < this.countChunksX; ++xIndex) {
                    int blockX = this.getBaseCoordinate(i, d, xIndex);

                    for (int zIndex = 0; zIndex < this.countChunksZ; ++zIndex) {
                        int blockZ = this.getBaseCoordinate(j, d, zIndex);

                        for (int yIndex = 0; yIndex < this.countChunksY; ++yIndex) {
                            int blockY = yIndex * MCConstants.CHUNK_SIZE;
                            BlockPos pos = this.renderChunks[(zIndex * this.countChunksY + yIndex) * this.countChunksX + xIndex].getPosition();

                            if (pos.getX() != blockX || pos.getY() != blockY || pos.getZ() != blockZ) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }).getAsBoolean() : "Not all RenderChunks are in the correct position!";
        } else {
            //do nothing and don't cancel the callback, we'll fall through to the regular vanilla code
        }

        if (false) { // slow path, equivalent to vanilla code
            for (int zIndex = 0; zIndex < countChunksZ; ++zIndex) {
                int idxZ = zIndex * countChunksY * countChunksX;
                int blockZ = ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(zIndex, countChunksZ, originZ, minZ);

                for (int yIndex = 0; yIndex < countChunksY; ++yIndex) {
                    int idxYZ = idxZ + yIndex * countChunksX;
                    int blockY = yIndex * MCConstants.CHUNK_SIZE;

                    for (int xIndex = 0; xIndex < countChunksX; ++xIndex) {
                        int blockX = ppatches_optimizeRenderChunkPositionUpdates_getBlockCoord(xIndex, countChunksX, originX, minX);

                        renderChunks[idxYZ + xIndex].setPosition(blockX, blockY, blockZ);
                    }
                }
            }
            ci.cancel();
        }

        /*{ // slow path, original vanilla code
            int i = MathHelper.floor(viewEntityX) - 8;
            int j = MathHelper.floor(viewEntityZ) - 8;
            int d = this.countChunksX * 16;

            for (int xIndex = 0; xIndex < this.countChunksX; ++xIndex) {
                int i1 = this.getBaseCoordinate(i, d, xIndex);

                for (int zIndex = 0; zIndex < this.countChunksZ; ++zIndex) {
                    int k1 = this.getBaseCoordinate(j, d, zIndex);

                    for (int yIndex = 0; yIndex < this.countChunksY; ++yIndex) {
                        int i2 = yIndex * 16;
                        RenderChunk renderchunk = this.renderChunks[(zIndex * this.countChunksY + yIndex) * this.countChunksX + xIndex];
                        renderchunk.setPosition(i1, i2, k1);
                    }
                }
            }
            ci.cancel();
            return;
        }*/
    }

    @Shadow
    private int getBaseCoordinate(int basePositionBlocks, int countChunksInBlocks, int index) {
        int offsetInBlocks = index * 16;
        int j = offsetInBlocks - basePositionBlocks + countChunksInBlocks / 2;
        if (j < 0) {
            j -= countChunksInBlocks - 1;
        }

        return offsetInBlocks - j / countChunksInBlocks * countChunksInBlocks;
    }
}
