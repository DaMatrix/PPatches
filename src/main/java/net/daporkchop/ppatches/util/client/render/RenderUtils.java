package net.daporkchop.ppatches.util.client.render;

import lombok.experimental.UtilityClass;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.util.List;

/**
 * @author DaPorkchop_
 */
@UtilityClass
@SideOnly(Side.CLIENT)
public class RenderUtils {
    public static void prepareVertexAttributesFromVBO(VertexFormat format) {
        int stride = format.getSize();
        List<VertexFormatElement> list = format.getElements();
        for (int j = 0; j < list.size(); ++j) {
            VertexFormatElement attr = list.get(j);
            int offset = format.getOffset(j);

            //moved to VertexFormatElement.preDraw
            int count = attr.getElementCount();
            int constant = attr.getType().getGlConstant();
            switch (attr.getUsage()) {
                case POSITION:
                    GlStateManager.glVertexPointer(count, constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case NORMAL:
                    if (count != 3) {
                        throw new IllegalArgumentException("Normal attribute should have the size 3: " + attr);
                    }
                    GL11.glNormalPointer(constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                    break;
                case COLOR:
                    GlStateManager.glColorPointer(count, constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
                    break;
                case UV:
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
                    GlStateManager.glTexCoordPointer(count, constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                    break;
                case PADDING:
                    break;
                case GENERIC:
                    GL20.glEnableVertexAttribArray(attr.getIndex());
                    GL20.glVertexAttribPointer(attr.getIndex(), count, constant, false, stride, offset);
                    break;
                default:
                    FMLLog.log.fatal("Unimplemented vanilla attribute upload: {}", attr.getUsage().getDisplayName());
            }
        }
    }

    public static Vector3f set(Vector3f vec, float x, float y, float z) {
        vec.set(x, y, z);
        return vec;
    }

    public static Vector4f set(Vector4f vec, float x, float y, float z, float w) {
        vec.set(x, y, z, w);
        return vec;
    }

    /**
     * Draws a rectangle to a BufferBuilder using {@link net.minecraft.client.renderer.vertex.DefaultVertexFormats#POSITION}.
     *
     * @see net.minecraft.client.gui.Gui#drawRect(int, int, int, int, int)
     */
    public static void drawRect(BufferBuilder buffer, int left, int top, int right, int bottom) {
        if (left < right) {
            int i = left;
            left = right;
            right = i;
        }

        if (top < bottom) {
            int j = top;
            top = bottom;
            bottom = j;
        }

        buffer.pos(left, bottom, 0.0d).endVertex();
        buffer.pos(right, bottom, 0.0d).endVertex();
        buffer.pos(right, top, 0.0d).endVertex();
        buffer.pos(left, top, 0.0d).endVertex();
    }

    /**
     * Draws a colored rectangle to a BufferBuilder using {@link net.minecraft.client.renderer.vertex.DefaultVertexFormats#POSITION_COLOR}.
     *
     * @see net.minecraft.client.gui.Gui#drawRect(int, int, int, int, int)
     */
    public static void drawColoredRect(BufferBuilder buffer, int left, int top, int right, int bottom, int color) {
        if (left < right) {
            int i = left;
            left = right;
            right = i;
        }

        if (top < bottom) {
            int j = top;
            top = bottom;
            bottom = j;
        }

        int a = color >>> 24;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        buffer.pos(left, bottom, 0.0d).color(r, g, b, a).endVertex();
        buffer.pos(right, bottom, 0.0d).color(r, g, b, a).endVertex();
        buffer.pos(right, top, 0.0d).color(r, g, b, a).endVertex();
        buffer.pos(left, top, 0.0d).color(r, g, b, a).endVertex();
    }
}
