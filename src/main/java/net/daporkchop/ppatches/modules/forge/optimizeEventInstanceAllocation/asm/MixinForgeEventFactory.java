package net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation.asm;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.village.Village;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nullable;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ForgeEventFactory.class, remap = false)
abstract class MixinForgeEventFactory {
    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    public static CapabilityDispatcher gatherCapabilities(TileEntity tileEntity) {
        AttachCapabilitiesEvent<TileEntity> event = new AttachCapabilitiesEvent<>(TileEntity.class, tileEntity);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.getCapabilities().isEmpty() ? new CapabilityDispatcher(event.getCapabilities(), null) : null;
    }

    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    public static CapabilityDispatcher gatherCapabilities(Entity entity) {
        AttachCapabilitiesEvent<Entity> event = new AttachCapabilitiesEvent<>(Entity.class, entity);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.getCapabilities().isEmpty() ? new CapabilityDispatcher(event.getCapabilities(), null) : null;
    }

    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    public static CapabilityDispatcher gatherCapabilities(Village village) {
        AttachCapabilitiesEvent<Village> event = new AttachCapabilitiesEvent<>(Village.class, village);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.getCapabilities().isEmpty() ? new CapabilityDispatcher(event.getCapabilities(), null) : null;
    }

    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    public static CapabilityDispatcher gatherCapabilities(ItemStack stack, ICapabilityProvider parent) {
        AttachCapabilitiesEvent<ItemStack> event = new AttachCapabilitiesEvent<>(ItemStack.class, stack);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.getCapabilities().isEmpty() || parent != null ? new CapabilityDispatcher(event.getCapabilities(), parent) : null;
    }

    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    public static CapabilityDispatcher gatherCapabilities(World world, ICapabilityProvider parent) {
        AttachCapabilitiesEvent<World> event = new AttachCapabilitiesEvent<>(World.class, world);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.getCapabilities().isEmpty() || parent != null ? new CapabilityDispatcher(event.getCapabilities(), parent) : null;
    }

    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    public static CapabilityDispatcher gatherCapabilities(Chunk chunk) {
        AttachCapabilitiesEvent<Chunk> event = new AttachCapabilitiesEvent<>(Chunk.class, chunk);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.getCapabilities().isEmpty() ? new CapabilityDispatcher(event.getCapabilities(), null) : null;
    }

    /**
     * @author DaPorkchop_
     * @reason completely replacing the method to make the event allocation optimizations be able to target this
     */
    @Overwrite
    @Nullable
    private static CapabilityDispatcher gatherCapabilities(AttachCapabilitiesEvent<?> event, @Nullable ICapabilityProvider parent) {
        throw new AbstractMethodError("method removed by PPatches!");
    }
}
