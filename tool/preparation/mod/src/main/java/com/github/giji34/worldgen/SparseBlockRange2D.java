package com.github.giji34.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Function;

public class SparseBlockRange2D implements Iterable<Loc> {
    private final Loc min;
    private final Loc max;
    final SparseIntRange range = new SparseIntRange();

    private final int dx;

    public SparseBlockRange2D(Loc min, Loc max) {
        this.min = min;
        this.max = max;
        this.dx = max.x - min.x + 1;
    }

    public void add(Loc v)  {
        try {
            int index = IndexFromLoc(v);
            range.add(index);
        } catch (Exception e) {
        }
    }

    public boolean contains(Loc v) {
        try {
            int index = IndexFromLoc(v);
            return range.contains(index);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean forEach(Function<Loc, Boolean> cb) {
        return range.forEach((Integer index) -> {
            Loc loc = LocFromIndex(index);
            return cb.apply(loc);
        });
    }

    public int size() {
        return range.size();
    }

    int IndexFromLoc(Loc v) throws Exception {
        if (v.x < min.x || max.x < v.x || v.z < min.z || max.z < v.x) {
            throw new Exception("index out of range");
        }
        return (v.x - min.x) + (v.z - min.z) * dx;
    }

    Loc LocFromIndex(int index) {
        int x = index % dx;
        int z = (index - x) / dx;
        return new Loc(x + min.x, z + min.z);
    }

    @NotNull
    @Override
    public Iterator<Loc> iterator() {
        return new SparseBlockRange2DIterator(this);
    }

    class SparseBlockRange2DIterator implements Iterator<Loc> {
        private final Iterator<Integer> iterator;
        private final SparseBlockRange2D range;

        SparseBlockRange2DIterator(SparseBlockRange2D range) {
            this.range = range;
            iterator = range.range.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Loc next() {
            Integer index = iterator.next();
            if (index == null) {
                return null;
            }
            return range.LocFromIndex(index);
        }
    }
}
