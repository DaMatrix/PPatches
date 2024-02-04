package net.daporkchop.ppatches.util.mixin.ext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>For internal use only!</b> Contains small parts. Keep out of reach of
 * children.
 * <p>
 * Decoration annotation used by the PPatches mixin extension to mark fields and methods in a
 * class which have been made final by a mixin.
 *
 * @author DaPorkchop_
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ /* No targets allowed */})
public @interface MixinMadeFinal {
}
