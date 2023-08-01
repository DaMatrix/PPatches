package net.daporkchop.ppatches.modules.vanilla.useFasterRandom;

import net.daporkchop.ppatches.util.mixin.MixinConfigPluginAdapter;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * @author DaPorkchop_
 */
public class UseFasterRandomMixinConfigPlugin extends MixinConfigPluginAdapter {
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if ("net.minecraft.block.Block".equals(targetClassName)) { //make static field Block.RANDOM (added by forge, so not obfuscated) final in order to help JIT
            //  (i would like to use an access transformer for this, but ATs can't target stuff added by forge)
            BytecodeHelper.findField(targetClass, "RANDOM", "Ljava/util/Random;").get().access |= Opcodes.ACC_FINAL;
        }

        super.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }
}
