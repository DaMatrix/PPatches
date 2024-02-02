package net.daporkchop.ppatches.util;

import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * @author DaPorkchop_
 */
public class HackyIsolatedClassLoader extends ClassLoader {
    private final ClassLoader dataSource;
    private final String className;

    public HackyIsolatedClassLoader(ClassLoader parent, ClassLoader dataSource, String className) {
        super(parent);
        this.dataSource = dataSource;
        this.className = className;
    }

    @Override
    @SneakyThrows(IOException.class)
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.startsWith(this.className)) {
            throw new ClassNotFoundException(name);
        }

        try (InputStream in = this.dataSource.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }

            byte[] arr = new byte[4096];
            int j = 0;
            for (int i; (i = in.read(arr, j, arr.length - j)) >= 0; ) {
                j += i;
                if (j == arr.length) {
                    arr = Arrays.copyOf(arr, arr.length * 2);
                }
            }
            return this.defineClass(name, arr, 0, j);
        }
    }
}
