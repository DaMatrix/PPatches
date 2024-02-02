package net.daporkchop.ppatches.modules.misc.preloadClassesAsync;

import lombok.SneakyThrows;

import java.io.BufferedOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This gets injected into the LaunchClassLoader as the {@link ClassLoader#parallelLockMap} map, and allows us to optimize for the case where a class
 * is being loaded on one thread while another thread is simply trying to resolve a class by name.
 * <p>
 * The trick is that we have the LaunchClassLoader only synchronize on itself if the class isn't already loaded - if the class is already loaded, we'll
 * return a dummy object to be used as a lock, which will permit the resolving thread to break out of {@link ClassLoader#loadClass(String, boolean)} quickly
 * without waiting to acquire a lock. (which is fine because it would break out early anyway even if it had acquired the lock, and will never actually need
 * to access any LaunchClassLoader code)
 *
 * @author DaPorkchop_
 */
public final class HackyParallelLockMap extends ConcurrentHashMap<Object, Object> {
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

    private final ClassLoader parentClassLoader;
    private final ClassLoader launchClassLoader;
    private final Class<? extends Thread> excludedThreadClass;
    private final BufferedOutputStream loadedClassList;

    public HackyParallelLockMap(ClassLoader launchClassLoader, Class<? extends Thread> excludedThreadClass, BufferedOutputStream loadedClassList) {
        this.parentClassLoader = launchClassLoader.getParent();
        this.launchClassLoader = launchClassLoader;
        this.excludedThreadClass = excludedThreadClass;
        this.loadedClassList = loadedClassList;
    }

    @Override
    @SneakyThrows
    public Object putIfAbsent(Object key, Object value) {
        String className = (String) key;
        if (className.startsWith("java.")
            || (this.parentClassLoader != null && ((Class<?>) FIND_LOADED_CLASS.invokeExact(this.parentClassLoader, className)) != null)) {
            return value;
        }

        if (Thread.currentThread().getClass() != this.excludedThreadClass) {
            this.loadedClassList.write((className + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        }

        if (((Class<?>) FIND_LOADED_CLASS.invokeExact(this.launchClassLoader, className)) != null) {
            return value;
        }

        return this.launchClassLoader;
    }
}
