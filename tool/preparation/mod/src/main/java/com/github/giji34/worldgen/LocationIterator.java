package com.github.giji34.worldgen;

import java.util.Iterator;

class LocationIterator implements Iterator<Loc> {
  final Loc min;
  final Loc max;

  int x;
  int z;

  LocationIterator(Loc min, Loc max) {
    this.min = min;
    this.max = max;
    this.x = min.x;
    this.z = min.z;
  }

  @Override
  public boolean hasNext() {
    return x <= max.x;
  }

  @Override
  public Loc next() {
    if (!hasNext()) {
      return null;
    }
    Loc v = new Loc(x, z);
    z += 1;
    if (z > max.z) {
      z = min.z;
      x += 1;
    }
    return v;
  }
}
