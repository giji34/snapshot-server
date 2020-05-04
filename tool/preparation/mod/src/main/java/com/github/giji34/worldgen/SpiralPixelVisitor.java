package com.github.giji34.worldgen;

import java.awt.*;
import java.util.Iterator;

class SpiralPixelVisitor  implements Iterator<Point> {
    private static final Point[] kDirections = new Point[]{new Point(1, 0), new Point(0, 1), new Point(-1, 0), new Point(0, -1)};

    private int x;
    private int y;
    private int directionIndex = 0;
    private Point direction = kDirections[0];
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;

    public SpiralPixelVisitor(Point center) {
        x = center.x;
        y = center.y;
        minX = x;
        maxX = x;
        minY = y;
        maxY = y;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Point next() {
        int nx = x + direction.x;
        int ny = y + direction.y;
        if ((direction.x == 1 && nx == maxX + 2) || (direction.x == -1 && nx == minX - 2) || (direction.y == 1 && ny == maxY + 2) || (direction.y == -1 && ny == minY - 2)) {
            directionIndex = (directionIndex + 1) % 4;
            direction = kDirections[directionIndex];
            nx = x + direction.x;
            ny = y + direction.y;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        x = nx;
        y = ny;
        return new Point(x, y);
    }
}
