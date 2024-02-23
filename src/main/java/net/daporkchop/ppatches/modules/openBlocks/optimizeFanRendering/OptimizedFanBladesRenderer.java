package net.daporkchop.ppatches.modules.openBlocks.optimizeFanRendering;

import net.daporkchop.ppatches.util.client.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

/**
 * @author DaPorkchop_
 */
@SideOnly(Side.CLIENT)
public class OptimizedFanBladesRenderer {
    private static final Matrix4f CACHED_MATRIX = new Matrix4f();
    private static final Vector3f CACHED_VEC3 = new Vector3f();
    private static final Vector4f CACHED_VEC4 = new Vector4f();

    private static TextureAtlasSprite BLADES_SPRITE;

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Post event) {
        BLADES_SPRITE = event.getMap().getAtlasSprite("openblocks:blocks/fan_blades");
    }

    public static void renderFanBladesFast(TileEntity te, float x, float y, float z, float fanAngle, float bladeAngle, BufferBuilder dst) {
        int combinedLight = te.getWorld().getCombinedLight(te.getPos(), 0);

        Matrix4f matrix = computeTransformMatrix(x, y, z, fanAngle, bladeAngle);

        drawFans(dst, combinedLight, matrix);
    }

    private static Matrix4f computeTransformMatrix(float x, float y, float z, float fanAngle, float bladeAngle) {
        Matrix4f matrix = CACHED_MATRIX;
        matrix.setIdentity();

        final float bladesMinX = 3.5f / 16.0f;
        final float bladesMinY = (3.5f + 2.75f) / 16.0f;
        final float bladesMinZ = 6.5f / 16.0f;
        final float bladesMaxX = 12.5f / 16.0f;
        final float bladesMaxY = (12.5f + 2.75f) / 16.0f;
        final float bladesMaxZ = 7.5f / 16.0f;

        if (false) {
            //move to relative position
            matrix.translate(new Vector3f((float) x, (float) y, (float) z));

            //rotate around the fan base
            matrix.translate(new Vector3f(0.5f, 0.0f, 0.5f));
            matrix.rotate(fanAngle, new Vector3f(0f, -1f, 0f));
            matrix.translate(new Vector3f(-0.5f, 0.0f, -0.5f));

            //translate into the fan blades position
            matrix.translate(new Vector3f(bladesMinX, bladesMinY, bladesMinZ));

            //scale to the correct size of the fan blades
            matrix.scale(new Vector3f(bladesMaxX - bladesMinX, bladesMaxY - bladesMinY, bladesMaxZ - bladesMinZ));

            //rotate the blades according to the current state
            matrix.translate(new Vector3f(0.5f, 0.5f, 0.0f));
            matrix.rotate(bladeAngle, new Vector3f(0f, 0f, 1f));
            matrix.translate(new Vector3f(-0.5f, -0.5f, 0.0f));
        } else if (true) { //this is the same code as above, except the Vector3f instances are re-used and consecutive translations are merged together
            Vector3f vec3 = CACHED_VEC3;

            //move to relative position
            matrix.translate(RenderUtils.set(vec3, (float) x + 0.5f, (float) y, (float) z + 0.5f));

            //rotate around the fan base
            matrix.rotate(fanAngle, RenderUtils.set(vec3, 0f, -1f, 0f));

            //translate into the fan blades position
            matrix.translate(RenderUtils.set(vec3, bladesMinX - 0.5f, bladesMinY, bladesMinZ - 0.5f));

            //scale to the correct size of the fan blades
            matrix.scale(RenderUtils.set(vec3, bladesMaxX - bladesMinX, bladesMaxY - bladesMinY, bladesMaxZ - bladesMinZ));

            //rotate the blades according to the current state
            matrix.translate(RenderUtils.set(vec3, 0.5f, 0.5f, 0.0f));
            matrix.rotate(bladeAngle, RenderUtils.set(vec3, 0f, 0f, 1f));
            matrix.translate(RenderUtils.set(vec3, -0.5f, -0.5f, 0.0f));
        }

        return matrix;
    }

    private static void drawFans(BufferBuilder dst, int combinedLight, Matrix4f matrix) {
        TextureAtlasSprite sprite = BLADES_SPRITE;
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();

        int skyLight = combinedLight >>> 16;
        int blockLight = combinedLight & 0xFF;

        Vector4f vec = CACHED_VEC4;
        vec.set(0f, 0f, 0f, 1f);
        Matrix4f.transform(matrix, vec, vec);
        dst.pos(vec.x, vec.y, vec.z).color(0xFF, 0xFF, 0xFF, 0xFF).tex(minU, minV).lightmap(skyLight, blockLight).endVertex();
        vec.set(1f, 0f, 0f, 1f);
        Matrix4f.transform(matrix, vec, vec);
        dst.pos(vec.x, vec.y, vec.z).color(0xFF, 0xFF, 0xFF, 0xFF).tex(maxU, minV).lightmap(skyLight, blockLight).endVertex();
        vec.set(1f, 1f, 0f, 1f);
        Matrix4f.transform(matrix, vec, vec);
        dst.pos(vec.x, vec.y, vec.z).color(0xFF, 0xFF, 0xFF, 0xFF).tex(maxU, maxV).lightmap(skyLight, blockLight).endVertex();
        vec.set(0f, 1f, 0f, 1f);
        Matrix4f.transform(matrix, vec, vec);
        dst.pos(vec.x, vec.y, vec.z).color(0xFF, 0xFF, 0xFF, 0xFF).tex(minU, maxV).lightmap(skyLight, blockLight).endVertex();
    }
}
