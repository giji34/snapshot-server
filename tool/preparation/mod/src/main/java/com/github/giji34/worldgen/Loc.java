package com.github.giji34.worldgen;

import java.util.Objects;

class Loc {
    final int x;
    final int z;

    Loc(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof  Loc) {
            Loc other = (Loc)obj;
            return other.x == this.x && other.z == this.z;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
