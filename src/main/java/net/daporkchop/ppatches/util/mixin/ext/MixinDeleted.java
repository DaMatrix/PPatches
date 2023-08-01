package net.daporkchop.ppatches.util.mixin.ext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>For internal use only!</b> Contains small parts. Keep out of reach of
 * children.
 * <p>
 * Decoration annotation used by the PPatches mixin extension to mark fields and methods in a
 * class which have been added or overwritten by a mixin.
 *
 * @author DaPorkchop_
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ /* No targets allowed */})
public @interface MixinDeleted {
    Field[] fields() default {};

    Method[] methods() default {};

    /**
     * @author DaPorkchop_
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ /* No targets allowed */})
    @interface Field {
        String name();

        Class<?> type();
    }

    /**
     * @author DaPorkchop_
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ /* No targets allowed */})
    @interface Method {
        String name();

        Class<?>[] parameterTypes() default {};

        Class<?> returnType();
    }
}
