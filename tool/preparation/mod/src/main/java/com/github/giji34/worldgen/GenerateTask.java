package com.github.giji34.worldgen;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.logging.Logger;

class GenerateTask implements Task {
    private final World world;
    private final ArrayList<Loc> chunks;
    private final double maxRunSeconds;
    private final long startMillis;

    private boolean cancelSignaled = false;
    private int idx = 0;

    GenerateTask(World world, ArrayList<Loc> chunks, int maxTicks) {
        this.world = world;
        this.chunks = chunks;
        this.maxRunSeconds = maxTicks / 20.0;
        this.startMillis = System.currentTimeMillis();
    }

    public void run() {
        if (isFinished()) {
            return;
        }

        final long start = System.currentTimeMillis();
        while (!cancelSignaled && !isFinished()) {
            final Loc loc = chunks.get(idx);
            final Chunk chunk = world.getChunkAt(loc.x, loc.z);
            chunk.load(true);
            chunk.unload(true);

            idx++;

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
        return idx >= chunks.size();
    }

    public void printLog(Logger logger) {
        final float sec = (System.currentTimeMillis() - startMillis) / 1000.0f;
        final float generatePerSec = idx / sec;
        final float progress = idx / (float)chunks.size() * 100;
        final int remaining = chunks.size() - idx;
        final long estimatedRemainingSeconds = (long)Math.ceil(remaining / generatePerSec);
        final LocalDateTime etc = LocalDateTime.now().plusSeconds(estimatedRemainingSeconds);
        logger.info("[generate] " + idx + "/" + chunks.size() + "(" + progress + " %, ETC " + etc.toString() + ") " + generatePerSec + " [chunk/sec]");
    }
}
