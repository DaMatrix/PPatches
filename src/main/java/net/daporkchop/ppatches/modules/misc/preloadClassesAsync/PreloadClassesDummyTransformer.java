package net.daporkchop.ppatches.modules.misc.preloadClassesAsync;

import com.google.common.eventbus.Subscribe;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.HackyIsolatedClassLoader;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author DaPorkchop_
 */
public final class PreloadClassesDummyTransformer implements ITreeClassTransformer {
    public static PreloadClassesDummyTransformer INSTANCE;

    public final BufferedOutputStream loadedClassesOutput;

    private byte[] oldLoadedClassListData;

    //private PPatchesBootstrap.Phase currentPhase;
    //private OutputStream dst;

    @SneakyThrows
    public PreloadClassesDummyTransformer() {
        INSTANCE = this;

        Path listFile = Paths.get("config", "ppatches", "misc", "preloadClassesAsync", "loadedClassesList.txt");
        PPatchesMod.LOGGER.info("Prefetching class list: {}", listFile.toAbsolutePath());
        if (Files.isRegularFile(listFile)) {
            this.oldLoadedClassListData = Files.readAllBytes(listFile);
        } else {
            PPatchesMod.LOGGER.warn("File {} doesn't exist, we won't preload any classes!", listFile);
            Files.createDirectories(listFile.getParent());
        }
        this.loadedClassesOutput = new BufferedOutputStream(Files.newOutputStream(listFile));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                this.loadedClassesOutput.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));

        try {
            //TODO: lmao this is hilarious, but also really gross
            PPatchesMod.LOGGER.info("Making LaunchClassLoader parallel capable...");

            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);

            Field parallelLockMap = ClassLoader.class.getDeclaredField("parallelLockMap");
            parallelLockMap.setAccessible(true);
            modifiers.setInt(parallelLockMap, modifiers.getInt(parallelLockMap) & ~Modifier.FINAL);

            Field package2certs = ClassLoader.class.getDeclaredField("package2certs");
            package2certs.setAccessible(true);
            modifiers.setInt(package2certs, modifiers.getInt(package2certs) & ~Modifier.FINAL);

            Field assertionLock = ClassLoader.class.getDeclaredField("assertionLock");
            assertionLock.setAccessible(true);
            modifiers.setInt(assertionLock, modifiers.getInt(assertionLock) & ~Modifier.FINAL);

            Field invalidClasses = LaunchClassLoader.class.getDeclaredField("invalidClasses");
            invalidClasses.setAccessible(true);

            if (parallelLockMap.get(Launch.classLoader) == null) {
                parallelLockMap.set(Launch.classLoader, new HackyIsolatedClassLoader(Launch.classLoader.getParent(), Launch.classLoader, "net.daporkchop.ppatches.modules.misc.preloadClassesAsync.HackyParallelLockMap")
                        .loadClass("net.daporkchop.ppatches.modules.misc.preloadClassesAsync.HackyParallelLockMap")
                        .getConstructor(ClassLoader.class, Class.class, BufferedOutputStream.class)
                        .newInstance(Launch.classLoader, ClassPreloadingThread.class, this.loadedClassesOutput));

                package2certs.set(Launch.classLoader, new ConcurrentHashMap<>((Hashtable<?, ?>) package2certs.get(Launch.classLoader)));
                assertionLock.set(Launch.classLoader, new Object());

                Set<String> newInvalidClasses = ConcurrentHashMap.newKeySet();
                //noinspection unchecked
                newInvalidClasses.addAll((Set<String>) invalidClasses.get(Launch.classLoader));
                invalidClasses.set(Launch.classLoader, newInvalidClasses);
            }
        } catch (Exception e) {
            PPatchesMod.LOGGER.warn("Failed to make LaunchClassLoader parallel capable! Class loading will run sequentially!", e);
        }

        PPatchesBootstrap.EVENT_BUS.register(this);

        for (String name : new String[]{ "classLoaderExceptions", "transformerExceptions" }) {
            Field field = LaunchClassLoader.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(Launch.classLoader, new CopyOnWriteArraySet<>((Set<?>) field.get(Launch.classLoader)));
        }
    }

    @Override
    @SneakyThrows(IOException.class)
    public boolean interestedInClass(String name, String transformedName) {
        this.loadedClassesOutput.write((name + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        return false;
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        return 0;
    }

    @Subscribe
    @SneakyThrows
    public void completedStateTransition(PPatchesBootstrap.CompletedStateTransitionEvent event) {
        if (event.phase == PPatchesBootstrap.Phase.MODS_ON_CLASSPATH) {
            for (String name : new String[]{ "rawFieldMaps", "rawMethodMaps" }) {
                Field field = FMLDeobfuscatingRemapper.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(FMLDeobfuscatingRemapper.INSTANCE, new ConcurrentHashMap<>((Map<?, ?>) field.get(FMLDeobfuscatingRemapper.INSTANCE)));
            }

            if (this.oldLoadedClassListData != null) {
                new ClassPreloadingThread(this.oldLoadedClassListData).start();
                this.oldLoadedClassListData = null;
            }
            PPatchesBootstrap.EVENT_BUS.unregister(this);
        }
    }
}
