package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.mixin;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import net.minecraft.init.Biomes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.quarry.TileQuarry$2", remap = false)
abstract class MixinTileQuarry_BiomeHandler {
    @Dynamic
    @Redirect(method = "onContentsChanged()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry$2;getBiome()Lnet/minecraft/world/biome/Biome;"),
            allow = 1, require = 1)
    private Biome ppatches_allQuarriesMineFromSameChunk_onContentsChanged_useRandomBiomeObject(@Coerce Object handler) {
        return Biomes.OCEAN; //random biome which isn't null
    }

    @Dynamic
    @Redirect(method = "onContentsChanged()V",
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;lastBiome:Lnet/minecraft/world/biome/Biome;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0),
            allow = 1, require = 1)
    private Biome ppatches_allQuarriesMineFromSameChunk_onContentsChanged_useRandomLastBiomeObject0(@Coerce TileEntity quarry) {
        return Biomes.OCEAN; //random biome which isn't null
    }

    @Dynamic
    @Redirect(method = "onContentsChanged()V",
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;lastBiome:Lnet/minecraft/world/biome/Biome;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 1),
            allow = 1, require = 1)
    private Biome ppatches_allQuarriesMineFromSameChunk_onContentsChanged_useRandomLastBiomeObject1(@Coerce TileEntity quarry) {
        return null; //random biome which isn't equal to Biomes.OCEAN
    }

    @Dynamic
    @Redirect(method = "onContentsChanged()V",
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;chunkPos:Lnet/minecraft/util/math/ChunkPos;",
                    opcode = Opcodes.GETFIELD),
            allow = 1, require = 1)
    private ChunkPos ppatches_allQuarriesMineFromSameChunk_onContentsChanged_useRandomChunkPos(@Coerce TileEntity quarry) {
        return new ChunkPos(0, 0); //random ChunkPos which isn't null
    }

    @Dynamic
    @Redirect(method = "onContentsChanged()V",
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;posKey:Lnet/minecraft/util/math/ChunkPos;",
                    opcode = Opcodes.GETFIELD),
            allow = 1, require = 1)
    private ChunkPos ppatches_allQuarriesMineFromSameChunk_onContentsChanged_useRandomPosKey(@Coerce TileEntity quarry) {
        return new ChunkPos(0, 0); //random ChunkPos which isn't null
    }

    @Dynamic
    @ModifyVariable(method = "onContentsChanged()V",
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;posKey:Lnet/minecraft/util/math/ChunkPos;",
                    opcode = Opcodes.GETFIELD,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    @SneakyThrows
    private Biome ppatches_allQuarriesMineFromSameChunk_onContentsChanged_getActualBiomeObjectNow(Biome biome) {
        Preconditions.checkState(biome == Biomes.OCEAN);
        return (Biome) MethodHandles.lookup().findVirtual(this.getClass(), "getBiome", MethodType.methodType(Biome.class)).invokeWithArguments(this);
    }

    /*@Dynamic
    @Redirect(method = "onContentsChanged()V",
            at = @At(value = "FIELD",
                    target = "Lcom/rwtema/extrautils2/quarry/TileQuarry;lastBiome:Lnet/minecraft/world/biome/Biome;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 1),
            allow = 1, require = 1)
    @SneakyThrows
    private Biome ppatches_allQuarriesMineFromSameChunk_onContentsChanged_redirectUpdateCapturingQuarryInstance(@Coerce TileEntity quarry) {
        Biome biome = (Biome) MethodHandles.lookup().findVirtual(this.getClass(), "getBiome", MethodType.methodType(Biome.class)).invokeWithArguments(this);

        MethodHandles.lookup().findSetter(quarry.getClass(), "lastBiome", Biome.class).invoke(quarry, biome);
        MethodHandles.lookup().findSpecial(quarry.getClass(), "getNewChunk", MethodType.methodType(void.class), this.getClass()).invoke(quarry);

        return null; //must return an object which is not equal to the one returned above for the check to fail, we'll continue at markDirty()
    }*/
}
