package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.mixin.IMixinWorldProviderSpecialDim;
import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.util.IMixinTileQuarry_AllQuarriesMineFromSameChunk;
import net.daporkchop.ppatches.modules.extraUtilities2.loadQuarryChunks.LoadQuarryChunksHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.ForgeChunkManager;

/**
 * A grouping of multiple Quantum Quarries which will all mine out of the same chunk.
 *
 * @author DaPorkchop_
 */
//TODO: i should randomize the quarry update order within a group to ensure that all quarries get an even chance at mining at a given Y level
public class QuarryGroup extends WorldSavedData {
    private static final boolean doChunkLoading = PPatchesConfig.extraUtilities2_allQuarriesMineFromSameChunk.isEnabled();

    public static QuarryGroup quarryGroupFor(WorldServer world, Biome targetBiome) {
        String name = "ppatches.extraUtilities2.allQuarriesMineFromSameChunk" + (targetBiome == null ? "" : "." + targetBiome.getRegistryName());

        QuarryGroup group = (QuarryGroup) world.getMapStorage().getOrLoadData(QuarryGroup.class, name);
        if (group == null) { //need to create a new quarry group
            group = new QuarryGroup(name);
            world.getMapStorage().setData(name, group);
        }
        if (group.server == null) {
            group.server = world.getMinecraftServer();
            group.targetBiome = targetBiome;
        }
        return group;
    }

    public transient MinecraftServer server;
    public transient Biome targetBiome; //nullable
    public transient final ReferenceArrayList<TileEntity> memberQuarries = new ReferenceArrayList<>();

    public ChunkPos posKey;
    public int curBlockLocation;
    public transient ChunkPos chunkPos;
    public transient final BlockPos.MutableBlockPos digPos = new BlockPos.MutableBlockPos();

    private transient boolean hasTicket = false;
    private transient ForgeChunkManager.Ticket activeTicket;

    public QuarryGroup(String name) {
        super(name);
    }

    public void addQuarry(TileEntity quarry) {
        Preconditions.checkState(!this.memberQuarries.contains(quarry), "quarry %s was already added!", quarry.getPos());
        QuarryGroup oldGroup = ((IMixinTileQuarry_AllQuarriesMineFromSameChunk) quarry).ppatches_allQuarriesMineFromSameChunk_cmpxchgGroup(null, this);
        Preconditions.checkArgument(oldGroup == null, "quarry %s was already added to group %s", quarry.getPos(), oldGroup);
        this.memberQuarries.add(quarry);

        if (doChunkLoading && this.memberQuarries.size() == 1) { //this group was previously empty
            assert !this.hasTicket;
            if (this.chunkPos != null) { //this group already owns a quarry chunk, we should acquire a ticket for it
                this.activeTicket = LoadQuarryChunksHelper.loadQuarryChunks(this.chunkPos);
                this.hasTicket = true;
            }
        }
    }

    public void removeQuarry(TileEntity quarry) {
        Preconditions.checkState(this.memberQuarries.remove(quarry), "quarry %s was not found in this group!", quarry.getPos());
        QuarryGroup oldGroup = ((IMixinTileQuarry_AllQuarriesMineFromSameChunk) quarry).ppatches_allQuarriesMineFromSameChunk_cmpxchgGroup(this, null);
        Preconditions.checkArgument(oldGroup == this, "quarry %s belonged to group %s after removal?!?", quarry.getPos(), oldGroup);

        if (doChunkLoading && this.memberQuarries.isEmpty()) { //this group is now empty
            if (this.chunkPos != null && this.hasTicket) { //this group owns a quarry chunk and has already chunkloaded it, we can stop chunkloading it now
                this.activeTicket = LoadQuarryChunksHelper.unloadQuarryChunks(this.chunkPos, this.activeTicket);
                this.hasTicket = false;
            }
        }
    }

    private static int getZ(int value) {
        return (value >> 12) & 15;
    }

    private static int getX(int value) {
        return (value >> 8) & 15;
    }

    private static int getY(int value) {
        return 255 - (value & 255);
    }

    public void setBlockPos() {
        int value = this.curBlockLocation;
        this.digPos.setPos((this.chunkPos.x << 4) + getX(value), getY(value), (this.chunkPos.z << 4) + getZ(value));
    }

    public void advance() {
        int value = this.curBlockLocation++;

        if (value < 0 || value >= ((15 << 12) | (15 << 8) | 255)) {
            this.getNewChunk();
        }

		/*int y = getY(value);
		if (y > 0) return;
		int x = getX(value);
		int z = getZ(value);

		if (y < 0 || (y == 0 && x == 15 && z == 15)) {
			getNewChunk();
		}*/
    }

    public void getNewChunk() {
        IMixinWorldProviderSpecialDim.callReleaseChunk(this.posKey);

        if (doChunkLoading && this.hasTicket) {
            this.activeTicket = LoadQuarryChunksHelper.unloadQuarryChunks(this.chunkPos, this.activeTicket);
            this.hasTicket = false;
        }
        this.posKey = null;
        this.chunkPos = null;
        this.curBlockLocation = -1;

        this.getNextChunk();
    }

    public void getNextChunk() {
        this.posKey = IMixinWorldProviderSpecialDim.callPrepareNewChunk(this.targetBiome);
        this.chunkPos = IMixinWorldProviderSpecialDim.callAdjustChunkRef(this.posKey);
        this.curBlockLocation = 0;
        if (doChunkLoading) {
            assert !this.hasTicket;
            this.activeTicket = LoadQuarryChunksHelper.loadQuarryChunks(this.chunkPos);
            this.hasTicket = true;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        Preconditions.checkState(this.posKey == null);
        Preconditions.checkState(this.memberQuarries.isEmpty());
        if (nbt.getBoolean("hasPosKey")) {
            this.posKey = new ChunkPos(nbt.getInteger("chunkX"), nbt.getInteger("chunkZ"));
            this.chunkPos = IMixinWorldProviderSpecialDim.callAdjustChunkRef(this.posKey);
            this.curBlockLocation = nbt.getInteger("curBlockLocation");
        } else {
            this.posKey = null;
            this.chunkPos = null;
            this.curBlockLocation = -1;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("hasPosKey", this.posKey != null);
        if (this.posKey != null) {
            nbt.setInteger("chunkX", this.posKey.x);
            nbt.setInteger("chunkZ", this.posKey.z);
            nbt.setInteger("curBlockLocation", this.curBlockLocation);
        }
        return nbt;
    }
}
