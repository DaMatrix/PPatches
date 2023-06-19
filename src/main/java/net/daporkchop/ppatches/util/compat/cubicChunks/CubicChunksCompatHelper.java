package net.daporkchop.ppatches.util.compat.cubicChunks;

import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class CubicChunksCompatHelper {
    public static final Class<?> ICUBICWORLD;
    public static final MethodHandle ICUBICWORLD_ISCUBICWORLD;
    public static final MethodHandle ICUBICWORLD_GETCUBEFROMBLOCKCOORDS_THEN_GETTILEENTITYMAP;

    static {
        Class<?> iCubicWorldClass;
        MethodHandle iCubicWorld_isCubicWorld;
        MethodHandle iCubicWorld_getCubeFromBlockCoords_then_getTileEntityMap;
        try {
            iCubicWorldClass = Class.forName("io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld");
            Class<?> iCubeClass = Class.forName("io.github.opencubicchunks.cubicchunks.api.world.ICube");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            iCubicWorld_isCubicWorld = lookup.findVirtual(iCubicWorldClass, "isCubicWorld", MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, World.class));

            MethodHandle iCubicWorld_getCubeFromBlockCoords = lookup.findVirtual(iCubicWorldClass, "getCubeFromBlockCoords", MethodType.methodType(iCubeClass, BlockPos.class));
            MethodHandle iCube_getTileEntityMap = lookup.findVirtual(iCubeClass, "getTileEntityMap", MethodType.methodType(Map.class));
            iCubicWorld_getCubeFromBlockCoords_then_getTileEntityMap = MethodHandles.filterReturnValue(iCubicWorld_getCubeFromBlockCoords, iCube_getTileEntityMap)
                    .asType(MethodType.methodType(Map.class, World.class, BlockPos.class));
        } catch (ReflectiveOperationException e) {
            PPatchesMod.LOGGER.warn("couldn't initialize cubic chunks stuff, assuming cubic chunks isn't present", e);
            iCubicWorldClass = null;
            iCubicWorld_isCubicWorld = null;
            iCubicWorld_getCubeFromBlockCoords_then_getTileEntityMap = null;
        }
        ICUBICWORLD = iCubicWorldClass;
        ICUBICWORLD_ISCUBICWORLD = iCubicWorld_isCubicWorld;
        ICUBICWORLD_GETCUBEFROMBLOCKCOORDS_THEN_GETTILEENTITYMAP = iCubicWorld_getCubeFromBlockCoords_then_getTileEntityMap;
    }
}
