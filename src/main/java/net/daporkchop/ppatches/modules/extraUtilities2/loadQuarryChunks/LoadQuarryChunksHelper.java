package net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks;

import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.mixin.IMixinWorldProviderSpecialDim;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.Loader;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class LoadQuarryChunksHelper {
    public static ForgeChunkManager.Ticket loadQuarryChunks(ChunkPos chunkPos) {
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(Loader.instance().getIndexedModList().get("extrautils2").getMod(), IMixinWorldProviderSpecialDim.callGetWorld(), ForgeChunkManager.Type.NORMAL);
        if (ticket != null) {
            //we successfully acquired a chunkloading ticket, use it to load the quarry chunks

            final int r = 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    ForgeChunkManager.forceChunk(ticket, new ChunkPos(chunkPos.x + dx, chunkPos.z + dz));
                }
            }
        }
        return ticket;
    }

    public static ForgeChunkManager.Ticket unloadQuarryChunks(ChunkPos chunkPos, ForgeChunkManager.Ticket ticket) {
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
        }
        return null;
    }
}
