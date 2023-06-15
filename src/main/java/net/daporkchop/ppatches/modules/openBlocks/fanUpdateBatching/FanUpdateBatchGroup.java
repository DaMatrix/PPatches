package net.daporkchop.ppatches.modules.openBlocks.fanUpdateBatching;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

/**
 * @author DaPorkchop_
 */
public final class FanUpdateBatchGroup<FAN extends TileEntity> extends AxisAlignedBB {
    public final FAN[] fans;

    private List<Entity> currentTickEntities;
    private int currentTickRemaining;
    private long currentTickTime = -1L;

    public FanUpdateBatchGroup(FAN[] fans, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(x1, y1, z1, x2, y2, z2);
        this.fans = fans;
    }

    public void setCurrentTickEntities(List<Entity> entities, long currentTime) {
        assert this.currentTickEntities == null : "not all fans in this batch were ticked?!? (cached entity list is non-null)";

        this.currentTickEntities = entities;
        this.currentTickRemaining = this.fans.length;
        this.currentTickTime = currentTime;
    }

    public boolean hasCurrentTickEntities(long currentTickTime) {
        return this.currentTickEntities != null;
    }

    public List<Entity> consumeCurrentTickEntities(long currentTickTime) {
        assert this.currentTickEntities != null : "too many fans in this batch were ticked?!? (cached entity list is null)";

        List<Entity> currentTickEntities = this.currentTickEntities;
        if (--this.currentTickRemaining == 0) {
            this.currentTickEntities = null;
        }
        return currentTickEntities;
    }
}
