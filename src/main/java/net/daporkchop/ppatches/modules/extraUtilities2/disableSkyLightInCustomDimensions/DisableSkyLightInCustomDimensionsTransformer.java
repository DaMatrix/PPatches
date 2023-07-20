package net.daporkchop.ppatches.modules.extraUtilities2.disableSkyLightInCustomDimensions;

import com.google.common.collect.ImmutableList;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.minecraft.world.WorldProvider;
import org.objectweb.asm.tree.ClassNode;

/**
 * Prevents {@code WorldProviderDeepDark} from overriding {@link WorldProvider#hasSkyLight()} in order to allow it to use the default implementation, thus preventing
 * unnecessary dynamic dispatch. The existing behavior is emulated thanks to a mixin setting {@link WorldProvider#hasSkyLight} to {@code false} during {@link WorldProvider#init()}.
 *
 * @author DaPorkchop_
 */
//TODO: this kind of trivial transformation could be simplified, maybe we could use some kind of data-driven system to describe them?
public class DisableSkyLightInCustomDimensionsTransformer implements ITreeClassTransformer {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return "com.rwtema.extrautils2.dimensions.deep_dark.WorldProviderDeepDark".equals(transformedName);
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        classNode.methods.remove(BytecodeHelper.findObfuscatedMethodOrThrow(classNode, ImmutableList.of("hasSkyLight", "func_191066_m"), "()Z"));
        return CHANGED;
    }
}
