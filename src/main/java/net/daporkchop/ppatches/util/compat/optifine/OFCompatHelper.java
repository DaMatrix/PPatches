package net.daporkchop.ppatches.util.compat.optifine;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.util.MethodHandleUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.launchwrapper.Launch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class OFCompatHelper {
    public static final boolean OPTIFINE = Launch.classLoader.getResource("net/optifine/SmartAnimations.class") != null;

    private static final MethodHandle SmartAnimations_checkAnimationActiveForSprite; // boolean(TextureAtlasSprite)
    private static final MethodHandle SmartAnimations_TextureAtlasSprite_setAnimationActive; // void(TextureAtlasSprite, boolean)
    private static final MethodHandle SmartAnimations_TextureAtlasSprite_getAnimationActive; // boolean(TextureAtlasSprite)

    static {
        if (OPTIFINE) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> smartAnimationsClass = Class.forName("net.optifine.SmartAnimations");

                MethodHandle SmartAnimations_isActive = lookup.findStatic(smartAnimationsClass, "isActive", MethodType.methodType(boolean.class));
                MethodHandle SmartAnimations_isSpriteRendered = lookup.findStatic(smartAnimationsClass, "isSpriteRendered", MethodType.methodType(boolean.class, int.class));

                //noinspection JavaLangInvokeHandleSignature
                MethodHandle SmartAnimations_TextureAtlasSprite_getAnimationIndex = lookup.findVirtual(TextureAtlasSprite.class, "getAnimationIndex", MethodType.methodType(int.class));

                //noinspection JavaReflectionMemberAccess
                Field animationActive = TextureAtlasSprite.class.getDeclaredField("animationActive");
                animationActive.setAccessible(true);
                SmartAnimations_TextureAtlasSprite_setAnimationActive = lookup.unreflectSetter(animationActive);
                SmartAnimations_TextureAtlasSprite_getAnimationActive = lookup.unreflectGetter(animationActive);

                // SmartAnimations.isActive() ? SmartAnimations.isSpriteRendered(sprite.animationIndex) : true
                SmartAnimations_checkAnimationActiveForSprite = MethodHandles.guardWithTest(
                        MethodHandles.dropArguments(SmartAnimations_isActive, 0, TextureAtlasSprite.class),
                        MethodHandles.filterReturnValue(SmartAnimations_TextureAtlasSprite_getAnimationIndex, SmartAnimations_isSpriteRendered),
                        MethodHandles.dropArguments(MethodHandleUtils.constant(boolean.class, true), 0, TextureAtlasSprite.class));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("PPatches: unable to find OptiFine \"Smart Animations\" methods", e);
            }
        } else {
            SmartAnimations_checkAnimationActiveForSprite = null;
            SmartAnimations_TextureAtlasSprite_setAnimationActive = null;
            SmartAnimations_TextureAtlasSprite_getAnimationActive = null;
        }
    }

    private static void checkOptiFine() {
        if (!OPTIFINE) {
            throw new UnsupportedOperationException("OptiFine is not present!");
        }
    }

    @SneakyThrows
    public static boolean SmartAnimations_checkAnimationActiveForSpriteAndUpdateAnimationActive(TextureAtlasSprite sprite) {
        checkOptiFine();

        boolean animationActive = (boolean) SmartAnimations_checkAnimationActiveForSprite.invokeExact(sprite);
        SmartAnimations_TextureAtlasSprite_setAnimationActive.invokeExact(sprite, animationActive);
        return animationActive;
    }

    @SneakyThrows
    public static boolean SmartAnimations_getAnimationActiveField(TextureAtlasSprite sprite) {
        checkOptiFine();

        return  (boolean) SmartAnimations_TextureAtlasSprite_getAnimationActive.invokeExact(sprite);
    }
}
