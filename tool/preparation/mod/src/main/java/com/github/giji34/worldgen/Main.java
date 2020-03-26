package com.github.giji34.worldgen;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class Main extends JavaPlugin implements Listener {
    private WorldGenerateTask task;

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
        switch (label) {
            case "generate":
                return this.onGenerateCommand(sender, args);
            case "generate-stop":
                return this.onGenerateStopCommand(sender);
            default:
                return false;
        }
    }

    private boolean onGenerateCommand(CommandSender sender, String[] args) {
        if (this.task != null) {
            sender.sendMessage(ChatColor.RED + "別の generate コマンドが実行中です");
            return true;
        }
        if (args.length != 6) {
            sender.sendMessage(ChatColor.RED + "引数が足りません /generate minX minZ maxX maxZ version dimension (0: overworld, -1: nether, 1: end)");
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
        int dimension = Integer.parseInt(args[5]);
        Server server = sender.getServer();
        World world = null;
        for (World w : server.getWorlds()) {
            World.Environment  environment = w.getEnvironment();
            if (environment == World.Environment.NORMAL && dimension == 0) {
                world = w;
                break;
            } else if (environment == World.Environment.NETHER && dimension == -1) {
                world = w;
                break;
            } else if (environment == World.Environment.THE_END && dimension == 1) {
                world = w;
                break;
            }
        }
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "dimension = " + dimension + " に該当するディメンジョンがありません");
            return true;
        }
        File jar = getFile();
        File dbDirectory = new File(new File(jar.getParent(), "giji34"), "wildblocks");
        Loc min = new Loc(minX, minZ);
        Loc max = new Loc(maxX, maxZ);
        WorldGenerateTask task;
        try {
            task = new WorldGenerateTask(getLogger(), dbDirectory, world, min, max, version);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "タスクの起動エラー");
            return true;
        }
        task.runTaskTimer(this, 1, WorldGenerateTask.kTimerIntervalTicks);
        this.task = task;
        sender.sendMessage("タスクを起動しました");
        return true;
    }

    boolean onGenerateStopCommand(CommandSender sender) {
        if (this.task == null) {
            sender.sendMessage(ChatColor.RED + "実行中の generate コマンドはありません");
            return true;
        }
        this.task.cancel();;
        this.task = null;
        sender.sendMessage("タスクを終了させました");
        return true;
    }
}
