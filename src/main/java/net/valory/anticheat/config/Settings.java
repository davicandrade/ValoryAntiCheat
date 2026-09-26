package net.valory.anticheat.config;

import java.io.File;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Settings {
  private static final Set<String> IMPLEMENTED_CHECKS =
      Set.of(
          "Speed.A",
          "Fly.A",
          "AirJump.A",
          "Step.A",
          "Phase.A",
          "Timer.A",
          "Reach.A",
          "Hitbox.A",
          "AutoClicker.A",
          "MultiAura.A",
          "BadPackets.A",
          "InvalidMove.A",
          "ImpossiblePlace.A",
          "ImpossibleBreak.A",
          "Scaffold.A",
          "FastPlace.A",
          "AimAssist.A",
          "BadPackets.B",
          "NoFall.A",
          "Spider.A");

  public record Policy(
      boolean enabled,
      double alert,
      double setback,
      double punish,
      double weight,
      double decay,
      String action) {}

  public final Map<String, Policy> checks;
  public final boolean setbacks, punishments, conservativeBridge;
  public final String server, prefix;
  public final int budget, retentionDays;
  public final double tickBudgetMs;

  public Settings(JavaPlugin plugin) {
    for (String name :
        List.of("config.yml", "checks.yml", "messages.yml", "punishments.yml", "compatibility.yml"))
      if (!new File(plugin.getDataFolder(), name).exists()) plugin.saveResource(name, false);
    var config = load(plugin, "config.yml");
    var check = load(plugin, "checks.yml");
    var punishment = load(plugin, "punishments.yml");
    var compatibility = load(plugin, "compatibility.yml");
    server = config.getString("server-id", "valory-paper");
    budget = integer(config.getInt("packets-per-player-per-tick", 64), 8, 256);
    tickBudgetMs = number(config.getDouble("processing-budget-ms", 4), .5, 20);
    retentionDays = integer(config.getInt("retention-days", 30), 1, 365);
    setbacks = config.getBoolean("setbacks", false);
    punishments = punishment.getBoolean("enabled", false);
    conservativeBridge = compatibility.getBoolean("unknown-bridge-disable-java-checks", true);
    prefix = load(plugin, "messages.yml").getString("prefix", "[VAC]");
    Map<String, Policy> out = new LinkedHashMap<>();
    var section = check.getConfigurationSection("checks");
    if (section == null) throw new IllegalArgumentException("checks.yml: checks ausentes");
    for (String key : section.getKeys(false)) {
      String path = "checks." + key;
      out.put(
          key.replace('_', '.'),
          new Policy(
              check.getBoolean(path + ".enabled", true),
              number(check.getDouble(path + ".alert-threshold", 6), 1, 1000),
              number(check.getDouble(path + ".setback-threshold", 18), 1, 1000),
              number(check.getDouble(path + ".punish-threshold", 50), 1, 1000),
              number(check.getDouble(path + ".weight", 1), .01, 20),
              number(check.getDouble(path + ".decay", 30), 1, 3600),
              check.getString(path + ".classification", "alert")));
    }
    for (var e : out.entrySet()) {
      if (!IMPLEMENTED_CHECKS.contains(e.getKey()))
        throw new IllegalArgumentException("Unregistered check: " + e.getKey());
      Policy p = e.getValue();
      if (!java.util.Set.of("informational", "alert", "setback-only").contains(p.action()))
      throw new IllegalArgumentException("Classificação não suportada: " + e.getKey());
      if (p.alert() > p.setback() || p.setback() > p.punish())
        throw new IllegalArgumentException("Threshold order: " + e.getKey());
    }
    checks = Collections.unmodifiableMap(out);
  }

  public static YamlConfiguration load(JavaPlugin p, String file) {
    return YamlConfiguration.loadConfiguration(new File(p.getDataFolder(), file));
  }

  private static double number(double n, double min, double max) {
    if (!Double.isFinite(n) || n < min || n > max)
    throw new IllegalArgumentException("Configuração numérica inválida: " + n);
    return n;
  }

  private static int integer(int n, int min, int max) {
    if (n < min || n > max) throw new IllegalArgumentException("Configuração inteira inválida: " + n);
    return n;
  }
}
