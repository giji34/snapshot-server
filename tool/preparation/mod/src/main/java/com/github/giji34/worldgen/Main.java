package com.github.giji34.worldgen;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
        File jar = getFile();
        File dbDirectory = new File(new File(jar.getParent(), "giji34"), "wildblocks");
        Loc min = new Loc(minX, minZ);
        Loc max = new Loc(maxX, maxZ);
        WorldGenerateTask task;
        try {
            task = new WorldGenerateTask(getLogger(), dbDirectory, world, min, max, version);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "タスクの起動エラー");
            return true;
        }
        task.runTaskTimer(this, 1, WorldGenerateTask.kTimerIntervalTicks);
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

    private boolean invalidGameMode(Player player) {
        GameMode current = player.getGameMode();
        return current != GameMode.CREATIVE && current != GameMode.SPECTATOR;
    }
}
