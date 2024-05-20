package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.resources.data.AnimationFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author DaPorkchop_
 */
/*public final class AnimatedTextureBuilder {
    private final Object2IntMap<int[][]> framesData;
    private final List<AnimationFrame> animationFrames = new ArrayList<>();

    private int currFrameIndex;
    private int currFrameTime;

    private boolean done;

    public AnimatedTextureBuilder(int[][] initialFrameData) {
        this.framesData = new Object2IntLinkedOpenCustomHashMap<>(new Hash.Strategy<int[][]>() {
            @Override
            public int hashCode(int[][] o) {
                return Arrays.deepHashCode(o);
            }

            @Override
            public boolean equals(int[][] a, int[][] b) {
                return Arrays.deepEquals(a, b);
            }
        });
        this.framesData.defaultReturnValue(-1);

        this.framesData.put(initialFrameData, 0);
        this.currFrameIndex = 0;
        this.currFrameTime = 1;
    }

    private int addFrameData(int[][] frameData) {
        int frameIndex = this.framesData.getInt(frameData);
        if (frameIndex >= 0) { //the frame already exists
            return frameIndex;
        }

        frameIndex = this.framesData.size();
        this.framesData.put(frameData, frameIndex);
        return frameIndex;
    }

    public void addFrameWithData(int[][] frameData) {
        checkState(!this.done, "already done!");

        int frameIndex = this.framesData.getInt(frameData);
        if (frameIndex < 0) { //the frame doesn't exist yet
            frameIndex = this.framesData.size();
            this.framesData.put(frameData, frameIndex);
        }

        this.addFrameWithIndex(frameIndex);
    }

    public void addFrameWithIndex(int frameIndex) {
        checkState(!this.done, "already done!");
        checkElementIndex(frameIndex, this.framesData.size());

        if (frameIndex == this.currFrameIndex) { //still on the same frame
            this.currFrameTime = Math.incrementExact(this.currFrameTime);
        } else { //switching to a different frame
            this.animationFrames.add(new AnimationFrame(this.currFrameIndex, this.currFrameTime));
            this.currFrameIndex = frameIndex;
            this.currFrameTime = 1;
        }
    }

    public void finish() {
        checkState(!this.done, "already done!");
        this.done = true;

        this.animationFrames.add(new AnimationFrame(this.currFrameIndex, this.currFrameTime));
    }

    public List<AnimationFrame> getFrames() {
        checkState(this.done, "not done!");
        return new ArrayList<>(this.animationFrames);
    }

    public List<int[][]> getFramesData() {
        checkState(this.done, "not done!");
        return new ArrayList<>(this.framesData.keySet());
    }
}*/
