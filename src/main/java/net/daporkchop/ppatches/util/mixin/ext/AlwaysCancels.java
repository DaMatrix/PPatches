package net.daporkchop.ppatches.util.mixin.ext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If placed on an {@link org.spongepowered.asm.mixin.injection.Inject injector method}, this annotation serves as a hint that the injector will always cancel the callback.
 *
 * @author DaPorkchop_
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface AlwaysCancels {
}
