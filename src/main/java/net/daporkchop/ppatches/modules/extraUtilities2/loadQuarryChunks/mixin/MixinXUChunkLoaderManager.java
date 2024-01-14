package net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.mixin;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.chunkloading.XUChunkLoaderManager", remap = false)
abstract class MixinXUChunkLoaderManager implements ForgeChunkManager.OrderedLoadingCallback {
    @Override
    public List<ForgeChunkManager.Ticket> ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world, int maxTicketCount) {
        return tickets.stream()
                .filter(ticket -> ticket.world != IMixinWorldProviderSpecialDim.callGetWorld())
                .collect(Collectors.toList());
    }
}
