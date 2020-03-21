package com.github.giji34.worldgen;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class Main extends JavaPlugin implements Listener {
    private GenerateTask task;

    @Override
    public void onLoad() {

    }

    @Override
    public void onDisable() {
        getLogger().info("onDisable");
        getServer().getScheduler().cancelTasks(this);
    }

    @Override
    public void onEnable() {
        getLogger().info("onEnable");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player)sender;
        if (invalidGameMode(player)) {
            return false;
        }
        switch (label) {
            case "generate":
                return this.onGenerateCommand(player, args);
            case "generate-stop":
                return this.onGenerateStopCommand(player);
            default:
                return false;
        }
    }

    private boolean onGenerateCommand(Player player, String[] args) {
        if (this.task != null) {
            player.sendMessage(ChatColor.RED + "別の generate コマンドが実行中です");
            return true;
        }
        if (args.length != 5) {
            player.sendMessage(ChatColor.RED + "引数が足りません /generate minX minZ maxX maxZ version");
            return false;
        }
        int x0 = Integer.parseInt(args[0]) >> 4;
        int z0 = Integer.parseInt(args[1]) >> 4;
        int x1 = Integer.parseInt(args[2]) >> 4;
        int z1 = Integer.parseInt(args[3]) >> 4;
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);
        String version = args[4];
        final World world = player.getWorld();
        GenerateTask task;
        try {
            task = new GenerateTask(world, minX, minZ, maxX, maxZ, version);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "タスクの起動エラー");
            return true;
        }
        task.runTaskTimer(this, 1, GenerateTask.kPeriod);
        this.task = task;
        player.sendMessage("タスクを起動しました");
        return true;
    }

    boolean onGenerateStopCommand(Player player) {
        if (this.task == null) {
            player.sendMessage(ChatColor.RED + "実行中の generate コマンドはありません");
            return true;
        }
        this.task.cancel();;
        this.task = null;
        player.sendMessage("タスクを終了させました");
        return true;
    }

    class GenerateTask extends BukkitRunnable {
        static final long kPeriod = 20; // game ticks

        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final World world;
        boolean cancelSignaled;
        final String version;
        final int dimension;
        final File indexDirectory;

        int x;
        int z;
        int numInspectedAfterLogged;
        int numGeneratedAfterLogged;
        long lastLoggedTimeMillis;

        long start = -1;
        long numInspectedTotal = 0;
        long numGeneratedTotal = 0;

        GenerateTask(World world, int minX, int minZ, int maxX, int maxZ, String version) throws Exception {
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
            switch (world.getEnvironment()) {
                case NETHER:
                    this.dimension = -1;
                    break;
                case THE_END:
                    this.dimension = 1;
                    break;
                case NORMAL:
                    this.dimension = 0;
                    break;
                default:
                    throw new Exception("");
            }
            File jar = getFile();
            this.indexDirectory = new File(new File(new File(new File(jar.getParent(), "giji34"), "wildblocks"), version), Integer.toString(dimension));
        }

        @Override
        public void run() {
            if (x > maxX || this.cancelSignaled) {
                return;
            }
            final long start = System.currentTimeMillis();
            if (this.start < 0) {
              this.start = start;
            }
            final long msPerTick = 1000 / 20;
            final long maxElapsedMS = kPeriod * msPerTick;
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
                        getLogger().info("finished");
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
          getLogger().info("[" + x + ", " + z + "] "
            + "(" + progress + "%) "
            + this.numInspectedTotal + " inspected (+" + this.numInspectedAfterLogged + ", " + (this.numInspectedTotal / sec) + " [chunk/s]), "
            + this.numGeneratedTotal + " generated (+" + this.numGeneratedAfterLogged + ", " + (this.numGeneratedTotal / sec) + " [chunk/s])");
          this.numInspectedAfterLogged = 0;
          this.numGeneratedAfterLogged = 0;
          this.lastLoggedTimeMillis = now;
        }

        @Override
        public void cancel() {
            getLogger().info("cancel");
            super.cancel();
            this.cancelSignaled = true;
            this.logStatistics();
        }
    }

    private boolean invalidGameMode(Player player) {
        GameMode current = player.getGameMode();
        return current != GameMode.CREATIVE && current != GameMode.SPECTATOR;
    }
}
