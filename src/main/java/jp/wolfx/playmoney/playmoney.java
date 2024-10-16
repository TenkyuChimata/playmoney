package jp.wolfx.playmoney;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class playmoney extends JavaPlugin {
  private static HashMap<String, Integer> time;

  private static int money;

  private static Economy econ = null;

  private static final boolean folia = isFolia();

  private static boolean isFolia() {
    try {
      Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private boolean setupEconomy() {
    if (getServer().getPluginManager().getPlugin("Vault") == null)
      return false;
    if (getServer().getServicesManager().getRegistration(Economy.class) == null)
      return false;
    econ = (Economy)((RegisteredServiceProvider<?>)Objects.<RegisteredServiceProvider>requireNonNull(getServer().getServicesManager().getRegistration(Economy.class))).getProvider();
    return true;
  }

  public void onEnable() {
    if (!setupEconomy()) {
      getServer().getPluginManager().disablePlugin(this);
    } else {
      time = new HashMap<>();
      saveDefaultConfig();
      reloadConfig();
      money = getConfig().getInt("money");
      pmScheduler();
    }
  }

  private void pmScheduler() {
    if (!folia) {
      Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::runtime, 20L, 1200L);
    } else {
      Bukkit.getGlobalRegionScheduler().run(this, task -> Bukkit.getAsyncScheduler().runAtFixedRate(this, scheduledTask -> runtime(), 1L, 60L, TimeUnit.SECONDS));
    }
  }

  private void runtime() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (time.containsKey(p.getName())) {
        int pTime = time.get(p.getName());
        time.remove(p.getName());
        time.put(p.getName(), pTime + 1);
        continue;
      }
      time.put(p.getName(), 0);
    }
  }

  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
    if (label.equalsIgnoreCase("pm")) {
      if (!(sender instanceof Player)) {
        sender.sendMessage("§c[PlayMoney] 此命令只能由玩家执行.");
      } else if (time.containsKey(sender.getName())) {
        EconomyResponse r = econ.depositPlayer(sender.getName(), (time.get(sender.getName()) * money));
        if (r.transactionSuccess()) {
          sender.sendMessage("§a[PlayMoney] 您本次累计在线了" + time.get(sender.getName()) * 3 + "分钟，共获得" + econ.format(r.amount) + "元游戏币");
          if (time.get(sender.getName()) != 0) {
            time.remove(sender.getName());
            time.put(sender.getName(), 0);
          }
        }
        if (!r.transactionSuccess())
          sender.sendMessage(String.format("§c[PlayMoney] 转账失败，报错： %s", r.errorMessage));
      } else {
        sender.sendMessage("§a[PlayMoney] 时间还不够呐，别急嗷~");
      }
      return true;
    }
    return false;
  }

  public void onDisable() {
    if (!folia) {
      Bukkit.getScheduler().cancelTasks(this);
    } else {
      Bukkit.getGlobalRegionScheduler().cancelTasks(this);
    }
    time.clear();
  }
}
