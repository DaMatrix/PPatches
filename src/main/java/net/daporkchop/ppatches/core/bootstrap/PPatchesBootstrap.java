package net.daporkchop.ppatches.core.bootstrap;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesLoadingPlugin;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.daporkchop.ppatches.util.compat.mixin.MixinCompatHelper;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class PPatchesBootstrap {
    static {
        if (PPatchesBootstrap.class.getClassLoader() != Launch.classLoader) {
            throw new IllegalStateException("loaded from wrong classloader: " + PPatchesBootstrap.class.getClassLoader());
        }
    }

    private static Phase EFFECTIVE_PHASE;

    public synchronized static void preinit() {
        Preconditions.checkState(MixinEnvironment.getCurrentEnvironment().getPhase() == MixinEnvironment.Phase.PREINIT, "current mixin phase is %s, expected %s", MixinEnvironment.getCurrentEnvironment().getPhase(), MixinEnvironment.Phase.PREINIT);
        Preconditions.checkState(EFFECTIVE_PHASE == null, "previous phase was %s, expected %s", EFFECTIVE_PHASE, null);
        EFFECTIVE_PHASE = Phase.PREINIT;

        PPatchesMod.LOGGER.info("Adding root loader mixin...");
        Mixins.addConfiguration("mixins.ppatches.json");

        notifyBeginPhase(EFFECTIVE_PHASE);
        addRootTransformer();
    }

    public synchronized static void afterMixinDefault() {
        Preconditions.checkState(MixinEnvironment.getCurrentEnvironment().getPhase() == MixinEnvironment.Phase.DEFAULT, "current mixin phase is %s, expected %s", MixinEnvironment.getCurrentEnvironment().getPhase(), MixinEnvironment.Phase.DEFAULT);
        Preconditions.checkState(EFFECTIVE_PHASE == Phase.PREINIT, "previous phase was %s, expected %s", EFFECTIVE_PHASE, Phase.PREINIT);
        EFFECTIVE_PHASE = Phase.AFTER_MIXIN_DEFAULT;

        notifyBeginPhase(EFFECTIVE_PHASE);

        //add a transformer here to make sure that our transformers always run after Mixin's transformers
        addRootTransformer();
    }

    @SneakyThrows
    public synchronized static void modsOnClasspath() {
        Preconditions.checkState(MixinEnvironment.getCurrentEnvironment().getPhase() == MixinEnvironment.Phase.DEFAULT, "current mixin phase is %s, expected %s", MixinEnvironment.getCurrentEnvironment().getPhase(), MixinEnvironment.Phase.DEFAULT);
        Preconditions.checkState(EFFECTIVE_PHASE == Phase.AFTER_MIXIN_DEFAULT, "previous phase was %s, expected %s", EFFECTIVE_PHASE, Phase.AFTER_MIXIN_DEFAULT);
        EFFECTIVE_PHASE = Phase.MODS_ON_CLASSPATH;

        {
            //fix transformer exclusion list
            Field field = LaunchClassLoader.class.getDeclaredField("transformerExceptions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> transformerExclusions = (Set<String>) field.get(PPatchesLoadingPlugin.class.getClassLoader());

            //allow transforming non-core FoamFix classes (FoamFix adds an exclusion for the entire mod)
            if (transformerExclusions.remove("pl.asie.foamfix")) {
                transformerExclusions.add("pl.asie.foamfix.coremod");
            }
        }

        notifyBeginPhase(EFFECTIVE_PHASE);
        MixinCompatHelper.forceSelectConfigs();
    }

    @SneakyThrows
    private static void notifyBeginPhase(Phase phase) {
        PPatchesMod.LOGGER.info("Preparing modules for phase: {}", phase);

        for (Map.Entry<String, PPatchesConfig.ModuleConfigBase> entry : PPatchesConfig.listModules().entrySet()) {
            String name = entry.getKey();
            PPatchesConfig.ModuleConfigBase module = entry.getValue();

            if (module.descriptor.registerPhase() != phase) {
                continue;
            } else if (!module.isEnabled()) {
                PPatchesMod.LOGGER.info("Skipping module {} ({})", name, module.getDisabledReason());
                continue;
            }

            if (module.descriptor.hasMixins()) {
                PPatchesMod.LOGGER.info("Enabling mixins for module {}", name);
                Mixins.addConfiguration("net/daporkchop/ppatches/modules/" + name.replace('.', '/') + "/mixins.json");
            }
            if (!module.descriptor.transformerClass().isEmpty()) {
                PPatchesMod.LOGGER.info("Registering transformer for module {}", name);
                PPatchesTransformerRoot.registerTransformer((ITreeClassTransformer) Class.forName(module.descriptor.transformerClass()).newInstance());
            }
        }
    }

    private static void addRootTransformer() {
        Launch.classLoader.registerTransformer("net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot");
    }

    public static Phase currentPhase() {
        return EFFECTIVE_PHASE;
    }

    /**
     * @author DaPorkchop_
     */
    public enum Phase {
        PREINIT,
        AFTER_MIXIN_DEFAULT,
        MODS_ON_CLASSPATH,
    }
}
