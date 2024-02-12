package net.daporkchop.ppatches.modules.forge.optimizeBlockCaptureDrops;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.util.NonNullList;

/**
 * A {@link NonNullList} which also has a reference to another list.
 *
 * @author DaPorkchop_
 */
@NoArgsConstructor
@AllArgsConstructor
public class ChainedNonNullList<E> extends NonNullList<E> {
    public ChainedNonNullList<E> successor;
}
