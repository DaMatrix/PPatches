package net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType;

import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.modules.vanilla.groupTileEntityUpdatesByType.util.IMixinTileEntity_GroupTileEntityUpdatesByType;
import net.minecraft.tileentity.TileEntity;

import java.util.AbstractList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author DaPorkchop_
 */
public final class GroupedTileEntityList extends AbstractList<TileEntity> {
    private TileEntity totalFirst;
    private TileEntity totalLast;

    private int size;

    private final IdentityHashMap<Class<? extends TileEntity>, ImplicitLinkedList> tileEntitiesByType = new IdentityHashMap<>();

    @RequiredArgsConstructor
    private static final class ImplicitLinkedList {
        private final Class<? extends TileEntity> clazz;
        private TileEntity first;
        private TileEntity last;
    }

    @Override
    public TileEntity get(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    //methods actually used by World

    private static ImplicitLinkedList list(TileEntity te) {
        return (ImplicitLinkedList) ((IMixinTileEntity_GroupTileEntityUpdatesByType) te).ppatches_groupTileEntityUpdatesByType_currList();
    }

    private static void list(TileEntity te, ImplicitLinkedList list) {
        ((IMixinTileEntity_GroupTileEntityUpdatesByType) te).ppatches_groupTileEntityUpdatesByType_currList(list);
    }

    private static TileEntity prev(TileEntity te) {
        return ((IMixinTileEntity_GroupTileEntityUpdatesByType) te).ppatches_groupTileEntityUpdatesByType_prevTickable();
    }

    private static void prev(TileEntity te, TileEntity prev) {
        ((IMixinTileEntity_GroupTileEntityUpdatesByType) te).ppatches_groupTileEntityUpdatesByType_prevTickable(prev);
    }

    private static TileEntity next(TileEntity te) {
        return ((IMixinTileEntity_GroupTileEntityUpdatesByType) te).ppatches_groupTileEntityUpdatesByType_nextTickable();
    }

    private static void next(TileEntity te, TileEntity next) {
        ((IMixinTileEntity_GroupTileEntityUpdatesByType) te).ppatches_groupTileEntityUpdatesByType_nextTickable(next);
    }

    private boolean checkInvariantsGlobal() {
        if (this.totalFirst == null) {
            checkState(this.totalLast == null);
        } else {
            checkState(prev(this.totalFirst) == null);
            checkState(next(this.totalLast) == null);
        }

        int count = 0;
        for (TileEntity te : this) {
            count++;
        }
        checkState(count == this.size, "size mismatch...");

        return true;
    }

    private boolean checkInvariantsFor(TileEntity te) {
        if (te == null) {
            return true;
        }

        TileEntity prev = prev(te);
        if (prev == null) {
            checkState(te == this.totalFirst);
        } else {
            checkState(te == next(prev));
        }

        TileEntity next = next(te);
        if (next == null) {
            checkState(te == this.totalLast);
        } else {
            checkState(te == prev(next));
        }

        ImplicitLinkedList list = list(te);
        checkState(list.clazz == te.getClass());

        if (te == list.first) {
            checkState(prev == null || te.getClass() != prev.getClass());
        } else {
            checkState(te.getClass() == prev.getClass());
        }
        if (te == list.last) {
            checkState(next == null || te.getClass() != next.getClass());
        } else {
            checkState(te.getClass() == next.getClass());
        }

        return true;
    }

    @Override
    public boolean add(TileEntity tileEntity) {
        ImplicitLinkedList list = this.tileEntitiesByType.computeIfAbsent(tileEntity.getClass(), ImplicitLinkedList::new);

        checkState(list(tileEntity) == null, "tile entity is already in a list?!?");
        list(tileEntity, list);

        if (list.first != null) { //add the tile entity to the end of the sublist
            TileEntity prev = list.last;
            TileEntity next = next(prev);

            list.last = tileEntity;
            next(prev, tileEntity);
            prev(tileEntity, prev);
            next(tileEntity, next);
            if (next != null) {
                prev(next, tileEntity);
            } else {
                checkState(prev == this.totalLast);
                this.totalLast = tileEntity;
            }
        } else { //add a new sublist for this tile entity type
            list.first = list.last = tileEntity;

            if (this.totalLast == null) { //this is the first tile entity at all
                this.totalFirst = this.totalLast = tileEntity;
            } else { //there are already other non-empty sublists
                next(this.totalLast, tileEntity);
                prev(tileEntity, this.totalLast);
                this.totalLast = tileEntity;
            }
        }

        this.size++;

        assert this.checkInvariantsFor(tileEntity);
        assert this.checkInvariantsFor(prev(tileEntity));
        assert this.checkInvariantsFor(next(tileEntity));
        assert this.checkInvariantsGlobal();
        return true;
    }

    private boolean remove(TileEntity tileEntity) {
        ImplicitLinkedList list = list(tileEntity);
        if (list == null) {
            return false;
        }

        TileEntity prev = prev(tileEntity);
        TileEntity next = next(tileEntity);

        if (prev != null) {
            next(prev, next);
        }
        if (next != null) {
            prev(next, prev);
        }

        if (tileEntity == this.totalFirst) {
            this.totalFirst = next;
        }
        if (tileEntity == this.totalLast) {
            this.totalLast = prev;
        }

        if (list.first == list.last) { //the list only contains one tile entity, which we assume is the one being removed
            checkState(list.first == tileEntity, "the entity being removed isn't in the list???");
            list.first = list.last = null;
        } else if (list.first == tileEntity) {
            list.first = next;
        } else if (list.last == tileEntity) {
            list.last = prev;
        }

        list(tileEntity, null);
        prev(tileEntity, null);
        next(tileEntity, null);

        this.size--;

        assert this.checkInvariantsFor(prev);
        assert this.checkInvariantsFor(next);
        assert this.checkInvariantsGlobal();

        return true;
    }

    @Override
    public boolean remove(Object o) {
        return this.remove((TileEntity) o);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<TileEntity> iterator() {
        return new GroupedIterator();
    }

    //raw type to avoid generating synthetic bridge methods
    private final class GroupedIterator implements Iterator {
        private TileEntity currTile;
        private TileEntity nextTile;

        public GroupedIterator() {
            this.nextTile = GroupedTileEntityList.this.totalFirst;
        }

        @Override
        public boolean hasNext() {
            return this.nextTile != null;
        }

        @Override
        public Object next() {
            this.currTile = this.nextTile;
            this.nextTile = GroupedTileEntityList.next(this.currTile);
            return this.currTile;
        }

        @Override
        public void remove() {
            GroupedTileEntityList.this.remove(this.currTile);
            this.currTile = null;
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }
}
