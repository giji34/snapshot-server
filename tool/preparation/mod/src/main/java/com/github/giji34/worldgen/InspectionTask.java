package com.github.giji34.worldgen;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

class InspectionTask implements Task {
    private final File indexDirectory;
    private final double maxRunSeconds;
    private final Thread thread;

    private SparseBlockRange2D chunks;
    private boolean finished = false;

    InspectionTask(File indexDirectory, Loc min, Loc max, int maxTicks) {
        this.indexDirectory = indexDirectory;
        this.maxRunSeconds = maxTicks / 20.0;
        this.chunks = new SparseBlockRange2D(min, max);
        this.thread = this.startTasks(min, max);
    }

    private Thread startTasks(Loc min, Loc max) {
        Thread t = new Thread(() -> {
            try {
                File indexFile = new File(indexDirectory, "index.txt");
                SparseBlockRange2D existing = new SparseBlockRange2D(min, max);
                if (indexFile.exists()) {
                    System.out.println("reading " + indexFile + " ...");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(indexFile)));
                    String line;
                    long lines = 0;
                    while ((line = reader.readLine()) != null) {
                        String[] tokens = line.split("\t");
                        int x = Integer.parseInt(tokens[0]);
                        int z = Integer.parseInt(tokens[1]);
                        if (min.x <= x && x <= max.x && min.z <= z && z <= max.z) {
                            existing.add(new Loc(x, z));
                        }
                        lines++;
                    }
                    System.out.println(lines + " lines read");
                }
                long cnt = 0;
                for (LocationIterator it = new LocationIterator(min, max); it.hasNext(); ) {
                    Loc loc = it.next();
                    if (!existing.contains(loc)) {
                        this.chunks.add(loc);
                        cnt++;
                    }
                }
                System.out.println(cnt + " chunks to be generated");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });
        t.start();
        return t;
    }

    public void run() {
        if (isFinished()) {
            return;
        }
        try {
            long millis = Math.max(1, (long)(maxRunSeconds * 1000));
            thread.join(millis);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        if (!thread.isAlive()) {
            this.finished = true;
        }
    }

    public void cancel() {
    }

    public boolean isFinished() {
        return this.finished;
    }

    @Nullable
    SparseBlockRange2D getChunks() {
        if (isFinished()) {
            return this.chunks;
        } else {
            return null;
        }
    }

    public void printLog(Logger logger) {
        logger.info("[inspection] finished = " + isFinished());
    }
}
