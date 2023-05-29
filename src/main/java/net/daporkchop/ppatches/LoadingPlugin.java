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

import net.daporkchop.ppatches.config.Config;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.util.Map;

public class LoadingPlugin implements IFMLLoadingPlugin {
    public static boolean isObfuscatedEnvironment;

    private static Map<String, Boolean> moduleStates;

    private static MixinEnvironment.Phase moduleLoadPhase(String moduleName) {
        //TODO: improve this
        return moduleName.startsWith("vanilla.") ? MixinEnvironment.Phase.PREINIT : MixinEnvironment.Phase.DEFAULT;
    }

    public static void loadModules(MixinEnvironment.Phase phase) {
        for (Map.Entry<String, Boolean> entry : moduleStates.entrySet()) {
            if (entry.getValue() && moduleLoadPhase(entry.getKey()) == phase) {
                Mixins.addConfiguration("mixins.ppatches." + entry.getKey() + ".json");
            }
        }

        if (phase == MixinEnvironment.Phase.DEFAULT) { //allow garbage collection now that all mixins have been loaded
            moduleStates = null;
        }
    }

    public LoadingPlugin() {
        FMLLog.log.info("\n\n\nPPatches Mixin init\n\n");
        MixinBootstrap.init();

        Map<String, Boolean> moduleStates = Config.load();
        for (Map.Entry<String, Boolean> entry : moduleStates.entrySet()) {
            if (entry.getValue()) { //at least one module is enabled, we can register stuff to start loading
                LoadingPlugin.moduleStates = moduleStates;

                LogManager.getLogger("PPatches").info("Adding root loader mixin...");
                Mixins.addConfiguration("mixins.ppatches.json");
                loadModules(MixinEnvironment.Phase.PREINIT);

                MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
                FMLLog.log.info(MixinEnvironment.getDefaultEnvironment().getObfuscationContext());
                break;
            }
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
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
