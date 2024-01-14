package net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType.mixin;

import net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType.GroupedTileEntityList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * @author DaPorkchop_
 */
@Mixin(World.class)
abstract class MixinWorld {
    @Mutable
    @Shadow
    @Final
    public List<TileEntity> tickableTileEntities;

    @Redirect(method = "<init>",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/World;tickableTileEntities:Ljava/util/List;",
                    opcode = Opcodes.PUTFIELD),
            allow = 1, require = 1)
    private void ppatches_groupTileEntityUpdatesByType_$init$_replaceTickableTileEntityList(World instance, List<TileEntity> value) {
        ((MixinWorld) (Object) instance).tickableTileEntities = new GroupedTileEntityList();
    }
}
