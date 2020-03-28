package com.github.giji34.worldgen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;

public class SparseIntRange implements Iterable<Integer> {
    final ArrayList<ContinuousIntRange> values = new ArrayList<>();
    int _size = 0;

    public SparseIntRange() {
    }

    public void add(int v) {
        int low = 0;
        int high = values.size() - 1;
        int idx = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            ContinuousIntRange midVal = values.get(mid);
            if (midVal.max < v) {
                low = mid + 1;
            } else if (v < midVal.min) {
                high = mid - 1;
            } else {
                if (midVal.min <= v && v <= midVal.max) {
                    return;
                }
                idx = mid;
                values.add(mid, new ContinuousIntRange(v, v));
                break;
            }
        }
        _size++;
        if (idx < 0) {
            values.add(low, new ContinuousIntRange(v, v));
            idx = low;
        }
        ContinuousIntRange added = values.get(idx);
        if (idx > 0) {
            ContinuousIntRange prev = values.get(idx - 1);
            if (prev.max + 1 == added.min) {
                values.remove(idx);
                prev.max = added.max;
                idx -= 1;
                added = prev;
            }
        }
        if (idx + 1 < values.size()) {
            ContinuousIntRange next = values.get(idx + 1);
            if (added.max + 1 == next.min) {
                values.remove(idx + 1);
                added.max = next.max;
            }
        }
    }

    public boolean forEach(Function<Integer, Boolean> cb) {
        for (ContinuousIntRange cir : values) {
            for (int v = cir.min; v <= cir.max; v++) {
                if (!cb.apply(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean contains(int v) {
        int low = 0;
        int high = values.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            ContinuousIntRange midVal = values.get(mid);
            if (midVal.max < v) {
                low = mid + 1;
            } else if (v < midVal.min) {
                high = mid - 1;
            } else {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return _size;
    }

    public Iterator<Integer> iterator() {
        return new SparseIntRangeIterator(this);
    }

    class SparseIntRangeIterator implements Iterator<Integer> {
        final SparseIntRange range;
        int index;
        int value;

        SparseIntRangeIterator(SparseIntRange range) {
            this.range = range;
            index = 0;
            if (!range.values.isEmpty()) {
                value = range.values.get(0).min;
            }
        }

        @Override
        public boolean hasNext() {
            return index < range.values.size();
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                return null;
            }
            ContinuousIntRange v = range.values.get(index);
            Integer i = value;
            value++;
            if (v.max < value) {
                index++;
                if (index < range.values.size()) {
                    value = range.values.get(index).min;
                }
            }
            return i;
        }
    }
}

class ContinuousIntRange {
    public int min;
    public int max;

    public ContinuousIntRange(int min, int max) {
        this.min = min;
        this.max = max;
    }
}
