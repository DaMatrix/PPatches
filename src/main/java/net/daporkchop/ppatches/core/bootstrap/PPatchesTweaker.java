package net.daporkchop.ppatches.core.bootstrap;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * @author DaPorkchop_
 */
public class PPatchesTweaker implements ITweaker {
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    @SneakyThrows
    public String[] getLaunchArguments() {
        Preconditions.checkState(MixinEnvironment.getCurrentEnvironment().getPhase() == MixinEnvironment.Phase.DEFAULT, "attempted to begin Mixin phase %s when it isn't the current Mixin phase! (expected %s)", MixinEnvironment.Phase.DEFAULT, MixinEnvironment.getCurrentEnvironment().getPhase());

        //Mixin has a tweaker which switches the whole mixin environment to the DEFAULT state when getLaunchArguments()
        //  is called. because our tweaker is added afterwards, we can now do PPatches' initialization here (which will
        //  ensure that we can re-add our class transformer so that it always runs after the mixin transformer)

        //reflective method call because tweakers seem to be loaded by the system class loader, but everything else is
        //  supposed to be loaded by Launch.classLoader
        MethodHandles.publicLookup().findStatic(
                Class.forName("net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap", false, Launch.classLoader),
                "afterMixinDefault", MethodType.methodType(void.class)).invokeExact();

        return new String[0];
    }
}
