package net.daporkchop.ppatches.modules.vanilla.optimizeWorldIsRemoteOnDedicatedServer.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(World.class)
abstract class MixinWorld {
    @Delete //delete isRemote field
    @Shadow
    @Final
    @SuppressWarnings("unused")
    public boolean isRemote;

    @Redirect(method = "<init>*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isRemote:Z", opcode = Opcodes.PUTFIELD),
            allow = -1, require = 1)
    private void ppatches_optimizeWorldIsRemoteOnDedicatedServer_crashIfRemoteWorld(World instance, boolean isRemote) {
        if (isRemote) {
            throw new IllegalArgumentException("PPatches: vanilla.optimizeWorldIsRemoteOnDedicatedServer: attempted to create a new instance of World with isRemote == true");
        }
    }
}
