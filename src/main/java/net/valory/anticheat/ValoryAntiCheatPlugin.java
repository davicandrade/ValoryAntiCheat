package net.valory.anticheat;

import net.valory.anticheat.api.*;
import net.valory.anticheat.config.*;
import net.valory.anticheat.integration.*;
import net.valory.anticheat.ui.*;
import org.bukkit.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class ValoryAntiCheatPlugin extends JavaPlugin {
  private VacEngine engine;

  @Override
  public void onEnable() {
    try {
      Settings settings = new Settings(this);
      NetworkAdapter adapter = new NoNetworkAdapter();
      Plugin punish = getServer().getPluginManager().getPlugin("ValoryPunish");
      if (punish != null && punish.isEnabled()) adapter = new ValoryAdapter(punish);
      engine = new VacEngine(this, settings, adapter);
      getServer().getPluginManager().registerEvents(new VacListener(engine), this);
      VacCommand commands = new VacCommand(this, engine);
      getServer().getPluginManager().registerEvents(commands, this);
      getCommand("vac").setExecutor(commands);
      getCommand("vac").setTabCompleter(commands);
      getServer()
          .getServicesManager()
          .register(AntiCheatService.class, engine, this, ServicePriority.Normal);
      engine.start();
      getLogger()
          .info(
              "VAC 1.0.0: observação e evidência ativas; punição automática permanece opt-in. "
                  + adapter.status());
    } catch (Exception | LinkageError failure) {
      getLogger().log(java.util.logging.Level.SEVERE, "Falha ao iniciar o VAC", failure);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    getServer().getServicesManager().unregisterAll(this);
    HandlerList.unregisterAll(this);
    if (engine != null) engine.close();
  }
}
