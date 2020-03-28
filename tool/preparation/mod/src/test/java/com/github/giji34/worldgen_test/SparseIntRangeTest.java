package com.github.giji34.worldgen_test;

import com.github.giji34.worldgen.SparseIntRange;
import org.junit.Test;

import java.util.Iterator;

public class SparseIntRangeTest {
    @Test
    public void test() {
        SparseIntRange sir = new SparseIntRange();
        sir.add(1);
        sir.add(2);
        sir.add(0);
        sir.add(4);
        Iterator<Integer> itr = sir.iterator();
        while (itr.hasNext()) {
            System.out.println(itr.next());
        }
        sir.add(3);
        sir.add(-2);
        sir.add(-1);
        sir.add(0);
    }
}
