package com.github.giji34.worldgen;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Logger;

class WorldGenTask extends BukkitRunnable {
    static final long kPeriodTicks = 1; // game ticks
    static final long kWorkLimitTicks = 5; // game ticks

    final Logger logger;
    final int minX;
    final int minZ;
    final int maxX;
    final int maxZ;
    final World world;
    boolean cancelSignaled;
    final String version;
    final File indexDirectory;
    final Point spawn;

    int x;
    int z;
    int numInspectedAfterLogged;
    int numGeneratedAfterLogged;
    long lastLoggedTimeMillis;
    ArrayList<Point> chunks = new ArrayList<>();
    boolean inspectionCompleted = false;

    long start = -1;
    long numInspectedTotal = 0;
    long numGeneratedTotal = 0;

    WorldGenTask(Logger logger, File dbDirectory, World world, int minX, int minZ, int maxX, int maxZ, String version) throws Exception {
        this.logger = logger;
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.x = minX;
        this.z = minZ;
        this.cancelSignaled = false;
        this.version = version;
        this.numInspectedAfterLogged = 0;
        this.numGeneratedAfterLogged = 0;
        this.lastLoggedTimeMillis = 0;
        int dimension;
        switch (world.getEnvironment()) {
            case NETHER:
                dimension = -1;
                break;
            case THE_END:
                dimension = 1;
                break;
            case NORMAL:
                dimension = 0;
                break;
            default:
                throw new Exception("");
        }
        this.indexDirectory = new File(new File(dbDirectory, version), Integer.toString(dimension));
        Location spawn = world.getSpawnLocation();
        this.spawn = new Point(spawn.getBlockX(), spawn.getBlockZ());
    }

    @Override
    public void run() {
        if (x > maxX || this.cancelSignaled) {
            return;
        }
        if (inspectionCompleted) {
            runGeneration();
        } else {
            runInspection();
        }
    }

    private void runInspection() {
        final long start = System.currentTimeMillis();
        final long msPerTick = 1000 / 20;
        final long maxElapsedMS = kWorkLimitTicks * msPerTick;
        while (!cancelSignaled) {
            String name = "c." + x + "." + z + ".idx";
            File idxFile = new File(indexDirectory, name);
            if (idxFile.exists()) {
                continue;
            }
            chunks.add(new Point(x, z));
        }
    }

    private void runGeneration() {
        final long start = System.currentTimeMillis();
        if (this.start < 0) {
            this.start = start;
        }
        final long msPerTick = 1000 / 20;
        final long maxElapsedMS = kWorkLimitTicks * msPerTick;
        int count = 0;
        int generated = 0;
        while (!cancelSignaled) {
            String name = "c." + x + "." + z + ".idx";
            File idxFile = new File(indexDirectory, name);
            if (!idxFile.exists()) {
                Chunk chunk = world.getChunkAt(x, z);
                chunk.load(true);
                chunk.unload(true);
                generated++;
            }
            count++;
            long elapsed = System.currentTimeMillis() - start;
            z += 1;
            if (z > maxZ) {
                z = minZ;
                x += 1;
                if (x > maxX) {
                    logger.info("finished");
                    break;
                }
            }
            if (elapsed > maxElapsedMS) {
                break;
            }
        }
        this.numInspectedAfterLogged += count;
        this.numGeneratedAfterLogged += generated;
        this.numInspectedTotal += count;
        this.numGeneratedTotal += generated;
        long end = System.currentTimeMillis();
        if (end - this.lastLoggedTimeMillis > 10 * 1000) {
            this.logStatistics();
        }
    }

    private void logStatistics() {
        final long now = System.currentTimeMillis();
        final double sec = (now - this.start) / 1000.0;
        final double progress = this.numInspectedTotal * 100.0 / (double)((this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1));
        logger.info("[" + x + ", " + z + "] "
            + "(" + progress + "%) "
            + this.numInspectedTotal + " inspected (+" + this.numInspectedAfterLogged + ", " + (this.numInspectedTotal / sec) + " [chunk/s]), "
            + this.numGeneratedTotal + " generated (+" + this.numGeneratedAfterLogged + ", " + (this.numGeneratedTotal / sec) + " [chunk/s])");
        this.numInspectedAfterLogged = 0;
        this.numGeneratedAfterLogged = 0;
        this.lastLoggedTimeMillis = now;
    }

    @Override
    public void cancel() {
        logger.info("cancel");
        super.cancel();
        this.cancelSignaled = true;
        this.logStatistics();
    }
}
