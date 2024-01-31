package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.mixin;

import com.google.common.collect.ImmutableSetMultimap;
import net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.util.IMixinWorld_OptimizeGetPersistentChunks;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Map;

/**
 * @author DaPorkchop_
 */
@SuppressWarnings("unchecked")
@Mixin(value = ForgeChunkManager.class, remap = false)
abstract class MixinForgeChunkManager {
    /*@Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/common/collect/MapMaker;makeMap()Ljava/util/concurrent/ConcurrentMap;"),
            allow = 2, require = 2)
    private static ConcurrentMap<?, ?> ppatches_optimizeGetPersistentChunks_$clinit$_initializeTicketsAndDormantChunkCacheMapsToNull(MapMaker mapMaker) {
        return null;
    }*/

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Collections;synchronizedMap(Ljava/util/Map;)Ljava/util/Map;"),
            allow = 1, require = 1)
    private static Map<?, ?> ppatches_optimizeGetPersistentChunks_$clinit$_initializeForcedChunksMapToNull(Map<?, ?> map) {
        return null;
    }

    /*@Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;loadWorld(Lnet/minecraft/world/World;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_loadWorld_storeTickets(Map<?, ?> map, Object world, Object newTickets) {
        ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_tickets((Multimap<String, ForgeChunkManager.Ticket>) newTickets);
        return null;
    }*/

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;loadWorld(Lnet/minecraft/world/World;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 1),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_loadWorld_storeForcedChunks(Map<?, ?> map, Object world, Object newForcedChunks) {
        ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks((ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>) newForcedChunks);
        return null;
    }

    /*@Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;loadWorld(Lnet/minecraft/world/World;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 2),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_loadWorld_storeDormantChunkCache(Map<?, ?> map, Object world, Object newDormantChunkCache) {
        ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_dormantChunkCache((Cache<Long, ?>) newDormantChunkCache);
        return null;
    }*/

    /*@Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;loadWorld(Lnet/minecraft/world/World;)V",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;tickets:Ljava/util/Map;",
                    opcode = Opcodes.GETSTATIC,
                    ordinal = 1)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_loadWorld_redirectTicketsAccessToWorld(Map<?, ?> map, Object world) {

        return null;
    }*/

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;unloadWorld(Lnet/minecraft/world/World;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_unloadWorld_clearForcedChunks(Map<?, ?> map, Object world) {
        ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks(null);
        return null;
    }

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;forceChunk(Lnet/minecraftforge/common/ForgeChunkManager$Ticket;Lnet/minecraft/util/math/ChunkPos;)V",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;forcedChunks:Ljava/util/Map;",
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_forceChunk_getForcedChunks(Map<?, ?> map, Object world) {
        return ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks();
    }

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;forceChunk(Lnet/minecraftforge/common/ForgeChunkManager$Ticket;Lnet/minecraft/util/math/ChunkPos;)V",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;forcedChunks:Ljava/util/Map;",
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_forceChunk_storeForcedChunks(Map<?, ?> map, Object world, Object newForcedChunks) {
        ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks((ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>) newForcedChunks);
        return null;
    }

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;unforceChunk(Lnet/minecraftforge/common/ForgeChunkManager$Ticket;Lnet/minecraft/util/math/ChunkPos;)V",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;forcedChunks:Ljava/util/Map;",
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_unforceChunk_getForcedChunks(Map<?, ?> map, Object world) {
        return ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks();
    }

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;unforceChunk(Lnet/minecraftforge/common/ForgeChunkManager$Ticket;Lnet/minecraft/util/math/ChunkPos;)V",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;forcedChunks:Ljava/util/Map;",
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_unforceChunk_storeForcedChunks(Map<?, ?> map, Object world, Object newForcedChunks) {
        ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks((ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>) newForcedChunks);
        return null;
    }

    @Redirect(method = "Lnet/minecraftforge/common/ForgeChunkManager;getPersistentChunksFor(Lnet/minecraft/world/World;)Lcom/google/common/collect/ImmutableSetMultimap;",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;forcedChunks:Ljava/util/Map;",
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            allow = 1, require = 1)
    private static Object ppatches_optimizeGetPersistentChunks_getPersistentChunksFor_getForcedChunks(Map<?, ?> map, Object world) {
        return ((IMixinWorld_OptimizeGetPersistentChunks) world).ppatches_optimizeGetPersistentChunks_forcedChunks();
    }
}
