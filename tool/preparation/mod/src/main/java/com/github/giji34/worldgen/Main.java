package com.github.giji34.worldgen;

import org.bukkit.*;
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
        Server server = getServer();
        GenerateTask task;
        try {
            task = new GenerateTask(world, minX, minZ, maxX, maxZ, version);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "タスクの起動エラー");
            return true;
        }
        server.getScheduler().runTaskTimer(this, task, 10, 1);
        this.task = task;
        player.sendMessage("タスクを起動しました");
        return true;
    }

    boolean onGenerateStopCommand(Player player) {
        if (this.task == null) {
            player.sendMessage(ChatColor.RED + "実行中の generate コマンドはありません");
            return true;
        }
        getServer().getScheduler().cancelTasks(this);
        this.task = null;
        player.sendMessage("タスクを終了させました");
        return true;
    }

    class GenerateTask extends BukkitRunnable {
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final World world;
        boolean cancelSignaled;
        final String version;
        final int dimension;

        int x;
        int z;
        int numInspectedAfterLogged;
        int numGeneratedAfterLogged;
        long lastLoggedTimeMillis;

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
        }

        @Override
        public void run() {
            if (x > maxX || this.cancelSignaled) {
                return;
            }
            long start = System.currentTimeMillis();
            long msPerTick = 1000 / 20;
            long maxElapsedMS = 5 * msPerTick;
            int count = 0;
            File jar = getFile();
            File directory = new File(new File(new File(new File(jar.getParent(), "giji34"), "wildblocks"), version), Integer.toString(dimension));
            int generated = 0;
            while (!cancelSignaled) {
                String name = "c." + x + "." + z + ".idx";
                File idxFile = new File(directory, name);
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
                        System.out.println("finished");
                        break;
                    }
                }
                if (elapsed > maxElapsedMS) {
                    break;
                }
            }
            this.numInspectedAfterLogged += count;
            this.numGeneratedAfterLogged += generated;
            long end = System.currentTimeMillis();
            if (end - this.lastLoggedTimeMillis > 10 * 1000) {
              System.out.println("[" + x + ", " + z + "] " + this.numInspectedAfterLogged + " chunks inspected, " + this.numGeneratedAfterLogged + " chunks generated");
              this.numInspectedAfterLogged = 0;
              this.numGeneratedAfterLogged = 0;
              this.lastLoggedTimeMillis = end;
            }
        }

        @Override
        public void cancel() {
            System.out.println("cancel called");
            super.cancel();
            this.cancelSignaled = true;
        }
    }

    private boolean invalidGameMode(Player player) {
        GameMode current = player.getGameMode();
        return current != GameMode.CREATIVE && current != GameMode.SPECTATOR;
    }
}
