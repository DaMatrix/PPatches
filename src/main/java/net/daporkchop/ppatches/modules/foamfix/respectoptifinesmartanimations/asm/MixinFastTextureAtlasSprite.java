package net.daporkchop.ppatches.modules.foamfix.respectoptifinesmartanimations.asm;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "pl.asie.foamfix.client.FastTextureAtlasSprite", remap = false)
public abstract class MixinFastTextureAtlasSprite extends TextureAtlasSprite {
    private static final MethodHandle SMARTANIMATIONS_ISACTIVE;
    private static final MethodHandle SMARTANIMATIONS_ISSPRITERENDERED;

    private static final MethodHandle TEXTUREATLASSPRITE_GETANIMATIONINDEX;
    private static final MethodHandle TEXTUREATLASSPRITE_SETANIMATIONACTIVE;

    static {
        try {
            Class<?> smartAnimationsClass = Class.forName("net.optifine.SmartAnimations");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            SMARTANIMATIONS_ISACTIVE = lookup.findStatic(smartAnimationsClass, "isActive", MethodType.methodType(boolean.class));
            SMARTANIMATIONS_ISSPRITERENDERED = lookup.findStatic(smartAnimationsClass, "isSpriteRendered", MethodType.methodType(boolean.class, int.class));

            //noinspection JavaLangInvokeHandleSignature
            TEXTUREATLASSPRITE_GETANIMATIONINDEX = lookup.findVirtual(TextureAtlasSprite.class, "getAnimationIndex", MethodType.methodType(int.class));

            //noinspection JavaReflectionMemberAccess
            Field animationActive = TextureAtlasSprite.class.getDeclaredField("animationActive");
            animationActive.setAccessible(true);
            TEXTUREATLASSPRITE_SETANIMATIONACTIVE = lookup.unreflectSetter(animationActive);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("PPatches: unable to find OptiFine \"Smart Animations\" methods", e);
        }
    }

    protected MixinFastTextureAtlasSprite(String spriteName) {
        super(spriteName);
    }

    @Inject(
            method = {
                    "Lpl/asie/foamfix/client/FastTextureAtlasSprite;updateAnimation()V",
                    "Lpl/asie/foamfix/client/FastTextureAtlasSprite;func_94219_l()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_respectOptiFineSmartAnimations_checkAnimationActiveState(CallbackInfo ci) throws Throwable {
        boolean animationActive = (boolean) SMARTANIMATIONS_ISACTIVE.invokeExact()
                ? (boolean) SMARTANIMATIONS_ISSPRITERENDERED.invokeExact((int) TEXTUREATLASSPRITE_GETANIMATIONINDEX.invokeExact((TextureAtlasSprite) this))
                : true;

        TEXTUREATLASSPRITE_SETANIMATIONACTIVE.invokeExact((TextureAtlasSprite) this, animationActive);
        if (!animationActive) {
            ci.cancel();
        }
    }
}
