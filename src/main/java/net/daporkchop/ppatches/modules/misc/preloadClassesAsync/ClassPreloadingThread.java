package net.daporkchop.ppatches.modules.misc.preloadClassesAsync;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.launchwrapper.Launch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

/**
 * @author DaPorkchop_
 */
public final class ClassPreloadingThread extends Thread {
    private static final MethodHandle FIND_LOADED_CLASS;

    static {
        try {
            Method findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            findLoadedClassMethod.setAccessible(true);
            FIND_LOADED_CLASS = MethodHandles.publicLookup().unreflect(findLoadedClassMethod);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private final byte[] listData;

    public ClassPreloadingThread(byte[] listData) {
        super("PPatches Class Preloading Thread");
        this.setDaemon(true);
        this.setPriority(MIN_PRIORITY);
        this.listData = listData;
    }

    @Override
    @SneakyThrows
    public void run() {
        LinkedHashSet<String> classes = new LinkedHashSet<>(Arrays.asList(new String(this.listData, StandardCharsets.UTF_8).split(System.lineSeparator())));

        PPatchesMod.LOGGER.info("Preloading {} classes...", classes.size());
        long startTime = System.nanoTime();

        for (String className : classes) {
            if ((Class<?>) FIND_LOADED_CLASS.invokeExact((ClassLoader) Launch.classLoader, className) != null) continue;

            try {
                Launch.classLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                PPatchesMod.LOGGER.warn("Failed to preload class: " + className, e);
            }
        }

        long endTime = System.nanoTime();
        PPatchesMod.LOGGER.info(String.format("Class preloading complete, took %.3fs", (endTime - startTime) / (double) TimeUnit.SECONDS.toNanos(1L)));
    }
}
