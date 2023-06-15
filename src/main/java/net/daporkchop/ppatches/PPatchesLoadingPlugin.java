/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2018-2019 DaPorkchop_ and contributors
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from DaPorkchop_.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.ppatches;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PPatchesLoadingPlugin implements IFMLLoadingPlugin {
    public static boolean isStarted;
    public static boolean isObfuscatedEnvironment;

    @SneakyThrows
    public static void loadMixins(MixinEnvironment.Phase phase) {
        if (phase == MixinEnvironment.Phase.DEFAULT) { //TODO: find a nicer place to put this
            Field field = LaunchClassLoader.class.getDeclaredField("transformerExceptions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> transformerExclusions = (Set<String>) field.get(PPatchesLoadingPlugin.class.getClassLoader());

            //allow transforming non-core FoamFix classes (FoamFix adds an exclusion for the entire mod)
            if (transformerExclusions.remove("pl.asie.foamfix")) {
                transformerExclusions.add("pl.asie.foamfix.coremod");
            }
        }

        for (Map.Entry<String, PPatchesConfig.ModuleConfigBase> entry : PPatchesConfig.listModules().entrySet()) {
            String name = entry.getKey();
            PPatchesConfig.ModuleConfigBase module = entry.getValue();

            if (!module.descriptor.hasMixins() || MixinEnvironment.Phase.forName(module.descriptor.mixinRegisterPhase()) != phase) {
                continue;
            }

            LOAD_MODULE:
            switch (module.state) {
                case DISABLED:
                    PPatchesMod.LOGGER.info("Not enabling mixins for module {} (disabled by config)", name);
                    break;
                case AUTO:
                    for (String className : module.descriptor.requiredClasses()) {
                        if (Launch.classLoader.getResource(className.replace('.', '/') + ".class") == null) {
                            PPatchesMod.LOGGER.info("Not enabling mixins for module {} (dependency class {} can't be found)", name, className);
                            break LOAD_MODULE;
                        }
                    }

                    //fall through
                case ENABLED:
                    PPatchesMod.LOGGER.info("Enabling mixins for module {}", name);
                    Mixins.addConfiguration("net/daporkchop/ppatches/modules/" + name.replace('.', '/') + "/mixins.json");
                    break;
            }
        }

        if (phase == MixinEnvironment.Phase.DEFAULT) {
            isStarted = true;
        }
    }

    public PPatchesLoadingPlugin() {
        PPatchesMod.LOGGER.info("Initializing Mixin...");
        MixinBootstrap.init();

        PPatchesMod.LOGGER.info("Adding root loader mixin...");
        Mixins.addConfiguration("mixins.ppatches.json");

        loadMixins(MixinEnvironment.Phase.PREINIT);
    }

    @Override
    @SneakyThrows(ReflectiveOperationException.class)
    public String[] getASMTransformerClass() {
        List<ITreeClassTransformer> transformers = new ArrayList<>();

        for (Map.Entry<String, PPatchesConfig.ModuleConfigBase> entry : PPatchesConfig.listModules().entrySet()) {
            String name = entry.getKey();
            PPatchesConfig.ModuleConfigBase module = entry.getValue();

            String transformerClass = module.descriptor.transformerClass();
            if (transformerClass.isEmpty()) { //module has no transformer, ignore it
                continue;
            }

            LOAD_MODULE:
            switch (entry.getValue().state) {
                case DISABLED:
                    PPatchesMod.LOGGER.info("Not registering transformer for module {} (disabled by config)", name);
                    break;
                case AUTO:
                    for (String className : module.descriptor.requiredClasses()) {
                        if (Launch.classLoader.getResource(className.replace('.', '/') + ".class") == null) {
                            PPatchesMod.LOGGER.info("Not registering transformer for module {} (dependency class {} can't be found)", name, className);
                            break LOAD_MODULE;
                        }
                    }

                    //fall through
                case ENABLED:
                    PPatchesMod.LOGGER.info("Registering transformer for module {}", name);
                    transformers.add((ITreeClassTransformer) Class.forName(transformerClass).newInstance());
                    break;
            }
        }

        if (transformers.isEmpty()) {
            return new String[0];
        } else {
            PPatchesTransformerRoot.TRANSFORMERS = transformers.toArray(new ITreeClassTransformer[0]);
            return new String[] { "net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot" };
        }
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        isObfuscatedEnvironment = (boolean) (Boolean) data.get("runtimeDeobfuscationEnabled");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
