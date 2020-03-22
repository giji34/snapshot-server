package com.github.giji34.worldgen;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Logger;

class WorldGenerateTask extends BukkitRunnable {
    static final int kTimerIntervalTicks = 1;
    static final int kMaxRunningTicks = 5;
    static final long kLogIntervalMillis = 10000;

    private final World world;
    private final Logger logger;

    private InspectionTask inspectionTask;
    private GenerateTask generateTask;
    private long lastLoggedMillis;

    WorldGenerateTask(Logger logger, File dbDirectory, World world, Loc min, Loc max, String version) throws Exception {
        this.world = world;
        this.logger = logger;
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
        File indexDirectory = new File(new File(dbDirectory, version), Integer.toString(dimension));
        inspectionTask = new InspectionTask(indexDirectory, min, max, kMaxRunningTicks);
        lastLoggedMillis = System.currentTimeMillis();
    }

    @Override
    public void run() {
        if (inspectionTask != null) {
            inspectionTask.run();
            final long now = System.currentTimeMillis();
            final long elapsed = now - lastLoggedMillis;
            if (elapsed >= kLogIntervalMillis) {
                inspectionTask.printLog(logger);
                lastLoggedMillis = now;
            }
            if (inspectionTask.isFinished()) {
                inspectionTask.printLog(logger);
                ArrayList<Loc> chunks = inspectionTask.getChunks();
                generateTask = new GenerateTask(world, chunks, kMaxRunningTicks);
                inspectionTask = null;
            }
        } else if (generateTask != null) {
            generateTask.run();
            final long now = System.currentTimeMillis();
            final long elapsed = now - lastLoggedMillis;
            if (elapsed >= kLogIntervalMillis) {
                generateTask.printLog(logger);
                lastLoggedMillis = now;
            }
            if (generateTask.isFinished()) {
                generateTask.printLog(logger);
                generateTask = null;
            }
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        if (inspectionTask != null) {
            inspectionTask.cancel();
            inspectionTask = null;
        }
        if (generateTask != null) {
            generateTask.cancel();
            generateTask = null;
        }
    }
}
