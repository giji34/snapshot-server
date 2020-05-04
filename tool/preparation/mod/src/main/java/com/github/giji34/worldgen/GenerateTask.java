package com.github.giji34.worldgen;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.logging.Logger;

class GenerateTask implements Task {
    private final World world;
    private final Iterator<Loc> chunks;
    private final double maxRunSeconds;
    private final int volume;
    private final Throttle throttle;

    private boolean cancelSignaled = false;
    private int idx = 0;
    private long totalMillis = 0;
    private double lastCPUCreditBalanceLogged = 0;

    GenerateTask(World world, SparseBlockRange2D chunks, int maxTicks) {
        this.world = world;
        this.chunks = chunks.iterator();
        this.maxRunSeconds = maxTicks / 20.0;
        this.volume = chunks.size();
        this.throttle = new Throttle();
    }

    public void run() {
        if (isFinished()) {
            return;
        }

        if (!this.throttle.isThrottleOn()) {
            return;
        }

        final long start = System.currentTimeMillis();
        long elapsed = 0;
        while (!cancelSignaled && chunks.hasNext()) {
            final Loc loc = chunks.next();
            world.loadChunk(loc.x, loc.z, true);
            world.unloadChunkRequest(loc.x, loc.z);

            idx++;

            elapsed = System.currentTimeMillis() - start;
            final double sec = elapsed / 1000.0;
            if (sec >= maxRunSeconds) {
                break;
            }
        }

        this.totalMillis += elapsed;

        if (isFinished()) {
            System.out.println("[generate] finished");
        }
    }

    public void cancel() {
        this.cancelSignaled = true;
    }

    public boolean isFinished() {
        return !chunks.hasNext() || cancelSignaled;
    }

    public void printLog(Logger logger) {
        final float sec = totalMillis / 1000.0f;
        final float generatePerSec = idx / sec;
        final float progress = idx / (float)volume * 100;
        final int remaining = volume - idx;
        final long estimatedRemainingSeconds = (long)Math.ceil(remaining / generatePerSec);
        final double credit = throttle.getCPUCreditBalance();
        final double maxCredit = throttle.getMaxCPUCreditBalance();
        String etcStr;
        try {
            LocalDateTime etc = LocalDateTime.now().plusSeconds(estimatedRemainingSeconds);
            etcStr = etc.toString();
        } catch (Exception e) {
            etcStr = "N/A";
        }
        if (throttle.isRunningOnEC2() && !throttle.isThrottleOn() && lastCPUCreditBalanceLogged == credit) {
            return;
        }
        logger.info("[generate] " + idx + "/" + volume + "(" + progress + " %, ETC " + etcStr + ") " + generatePerSec + " [chunk/sec]; cpu credit=" + credit + "/" + maxCredit);
        lastCPUCreditBalanceLogged = credit;
    }
}
