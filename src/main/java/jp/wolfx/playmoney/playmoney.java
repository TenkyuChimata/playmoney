package jp.wolfx.playmoney;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class playmoney extends JavaPlugin {

    // 玩家在线分钟数（每分钟 +1）
    private final Map<UUID, Integer> minutes = new HashMap<>();

    // 每分钟发放金额（配置项：money）
    private int moneyPerMinute;

    private Economy econ;

    private static final boolean FOLIA = detectFolia();

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("[PlayMoney] 未检测到 Vault 或经济系统，插件已禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        reloadConfig();
        moneyPerMinute = getConfig().getInt("money", 3);

        startMinuteCounter();

        getLogger().info("[PlayMoney] 启动成功。每分钟发放: " + moneyPerMinute);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        econ = rsp.getProvider();
        return true;
    }

    private void startMinuteCounter() {
        final long initialDelayTicks = 20L;      // 1 秒后开始
        final long periodTicks = 20L * 60L;      // 每 60 秒（1 分钟）

        if (!FOLIA) {
            Bukkit.getScheduler().runTaskTimer(this, this::tickMinute, initialDelayTicks, periodTicks);
        } else {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, _ -> tickMinute(), initialDelayTicks, periodTicks);
        }
    }

    private void tickMinute() {
        // 先把当前在线玩家收集到一个临时集合里用于清理
        //（不用 Stream，保持简单清晰）
        var online = Bukkit.getOnlinePlayers();

        // 1) 离线清理：保留在线玩家的 UUID
        minutes.keySet().removeIf(uuid -> {
            for (Player p : online) {
                if (p.getUniqueId().equals(uuid)) return false;
            }
            return true;
        });

        // 2) 在线累加：每分钟 +1
        for (Player p : online) {
            UUID id = p.getUniqueId();
            int cur = minutes.getOrDefault(id, 0);
            minutes.put(id, cur + 1);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String @NotNull [] args) {

        if (!label.equalsIgnoreCase("pm")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[PlayMoney] 此命令只能由玩家执行。");
            return true;
        }

        int mins = minutes.getOrDefault(player.getUniqueId(), 0);
        if (mins <= 0) {
            player.sendMessage("§a[PlayMoney] 时间还不够呐，别急喵~");
            return true;
        }

        double amount = (double) mins * moneyPerMinute;

        EconomyResponse r = econ.depositPlayer(player, amount);
        if (!r.transactionSuccess()) {
            player.sendMessage(String.format("§c[PlayMoney] 转账失败，错误：%s", r.errorMessage));
            return true;
        }

        // 发放成功：清零计时
        minutes.put(player.getUniqueId(), 0);

        player.sendMessage(
                "§a[PlayMoney] 您本次累计在线了 " + mins + " 分钟，共获得 " + econ.format(r.amount) + " 元游戏币"
        );
        return true;
    }

    @Override
    public void onDisable() {
        // Paper/Spigot 的 scheduler 直接 cancelTasks
        // Folia 的 GlobalRegionScheduler/AsyncScheduler 任务会随插件禁用停止，但 cancelTasks 仍是好习惯
        try {
            Bukkit.getScheduler().cancelTasks(this);
        } catch (Throwable ignored) {
            // 某些实现差异下可能不需要，忽略即可
        }

        if (FOLIA) {
            try {
                Bukkit.getGlobalRegionScheduler().cancelTasks(this);
            } catch (Throwable ignored) {
            }
        }

        minutes.clear();
    }
}
