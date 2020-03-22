package com.github.giji34.worldgen;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.logging.Logger;

class InspectionTask implements Task {
    private final LocationIterator locIterator;
    private final File indexDirectory;
    private final double maxRunSeconds;
    private final long startMillis;
    private final int totalChunks;

    private boolean cancelSignaled = false;
    private ArrayList<Loc> chunks = new ArrayList<>();
    int count;

    InspectionTask(File indexDirectory, Loc min, Loc max, int maxTicks) {
        locIterator = new LocationIterator(min, max);
        this.indexDirectory = indexDirectory;
        this.maxRunSeconds = maxTicks / 20.0;
        this.startMillis = System.currentTimeMillis();
        this.totalChunks = (max.x - min.x + 1) * (max.z - min.z + 1);
    }

    public void run() {
        if (isFinished()) {
            return;
        }
        final long start = System.currentTimeMillis();
        while (!cancelSignaled && !isFinished()) {
            Loc loc = locIterator.next();
            File idx = getIndexFile(indexDirectory, loc);
            if (!idx.exists()) {
              chunks.add(loc);
            }
            count++;
            final long elapsed = System.currentTimeMillis() - start;
            final double sec = elapsed / 1000.0;
            if (sec >= maxRunSeconds) {
                break;
            }
        }
    }

    public void cancel() {
        this.cancelSignaled = true;
    }

    public boolean isFinished() {
        return !locIterator.hasNext();
    }

    @Nullable
    ArrayList<Loc> getChunks() {
        if (isFinished()) {
            return this.chunks;
        } else {
            return null;
        }
    }

    public void printLog(Logger logger) {
        final float sec = (System.currentTimeMillis() - startMillis) / 1000.0f;
        final float inspectionsPerSec = count / sec;
        final float progress = count / (float)totalChunks * 100;
        final int remaining = totalChunks - count;
        final long estimatedRemainingSeconds = (long)Math.ceil(remaining / inspectionsPerSec);
        final LocalDateTime etc = LocalDateTime.now().plusSeconds(estimatedRemainingSeconds);
        logger.info("[inspection] " + chunks.size() + "/" + count + "/" + totalChunks + " (" + progress + " %, ETC " + etc.toString() +") " + inspectionsPerSec + " [chunk/sec]");
    }

    static File getIndexFile(File indexDirectory, Loc chunk) {
        String name = "c." + chunk.x + "." + chunk.z + ".idx";
        return new File(indexDirectory, name);
    }
}
