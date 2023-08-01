package net.daporkchop.ppatches.util.mixin.ext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to indicate a mixin class member which already exists in and must be removed from the target class.
 * <p>
 * To delete a field or method, simply {@link org.spongepowered.asm.mixin.Shadow} it and add the {@link Delete} annotation to the shadow member in the mixin class.
 * <p>
 * If any members are deleted, a {@link MixinDeleted} annotation will be placed on the target class describing the deleted members.
 *
 * @author DaPorkchop_
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Delete {
}
