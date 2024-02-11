package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.mixin;

import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.QuarryGroup;
import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.util.IMixinMinecraftServer_AllQuarriesMineFromSameChunk;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * @author DaPorkchop_
 */
@Mixin(MinecraftServer.class)
abstract class MixinMinecraftServer implements IMixinMinecraftServer_AllQuarriesMineFromSameChunk {
    @Unique
    private Map<Biome, QuarryGroup> ppatches_allQuarriesMineFromSameChunk_biomesToQuarryGroups;

    @Inject(method = "<init>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_allQuarriesMineFromSameChunk_$init$_initBiomesToQuarryGroups(CallbackInfo ci) {
        //needs to support null keys
        this.ppatches_allQuarriesMineFromSameChunk_biomesToQuarryGroups = new HashMap<>();
    }

    @Override
    public final QuarryGroup ppatches_allQuarriesMineFromSameChunk_quarryGroupFor(Biome targetBiome) {
        //return this.ppatches_allQuarriesMineFromSameChunk_biomesToQuarryGroups.computeIfAbsent(targetBiome, QuarryGroup::new);
        return null;
    }
}
