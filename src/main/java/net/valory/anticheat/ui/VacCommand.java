package net.valory.anticheat.ui;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.valory.anticheat.*;
import net.valory.anticheat.config.Settings;
import net.valory.anticheat.state.PlayerData;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

public final class VacCommand implements CommandExecutor, TabCompleter, Listener {
  private final ValoryAntiCheatPlugin plugin;
  private final VacEngine engine;

  public VacCommand(ValoryAntiCheatPlugin plugin, VacEngine engine) {
    this.plugin = plugin;
    this.engine = engine;
  }

  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String sub = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
    String permission =
        switch (sub) {
          case "status", "checks", "performance", "reload" -> "admin";
          case "violations" -> "logs";
          default -> sub;
        };
    if (!sender.hasPermission("valoryanticheat." + permission)) {
      sender.sendMessage("[VAC] Sem permissão.");
      return true;
    }
    switch (sub) {
      case "gui" -> {
        if (sender instanceof Player p) list(p, 0, false);
        else sender.sendMessage("[VAC] Use /vac status.");
      }
      case "alerts" -> {
        if (sender instanceof Player p)
          sender.sendMessage(
              "[VAC] Alertas " + (engine.alerts(p.getUniqueId()) ? "ativados" : "desativados"));
      }
      case "status" ->
          sender.sendMessage(
              "[VAC] Observação | "
                  + engine.players().size()
                  + " jogadores | tick="
                  + engine.tickNumber()
                  + " | storage="
                  + engine.store.ready()
                  + " | "
                  + engine.network.status()
                  + " | punição: opt-in com evidência durável");
      case "performance" -> {
        long queued = 0, dropped = 0;
        for (PlayerData d : engine.players()) {
          queued += d.inbox.size();
          dropped += d.inbox.dropped.sum();
        }
        sender.sendMessage(
            String.format(
                Locale.ROOT,
                "[VAC] %.0f packets/s | %.0f checks/s | %.3f ms/tick | players=%d | packet queue=%d"
                    + " | I/O queue=%d | dropped=%d | I/O failures=%d | history capacity=%d",
                engine.metrics.packetsPerSecond,
                engine.metrics.checksPerSecond,
                engine.metrics.meanTickMillis,
                engine.players().size(),
                queued,
                engine.store.queued(),
                dropped,
                engine.store.failures(),
                engine.players().size() * 100));
        sender.sendMessage(engine.metrics.costs());
      }
      case "checks" -> {
        for (var e : engine.settings().checks.entrySet())
          sender.sendMessage(
              e.getKey()
                  + " enabled="
                  + e.getValue().enabled()
                  + " "
                  + e.getValue().action()
                  + " | experimental");
      }
      case "reload" -> {
        try {
          Settings settings = new Settings(plugin);
          engine.reload(settings);
          sender.sendMessage("[VAC] Configuração validada; estados de movimento reinicializados.");
        } catch (RuntimeException bad) {
          sender.sendMessage(
              "[VAC] Reload rejeitado; configuração anterior mantida: " + bad.getMessage());
        }
      }
      case "profile", "debug", "violations", "logs" -> {
        if (args.length < 2) {
          sender.sendMessage("/vac " + sub + " <jogador> [check]");
          return true;
        }
        if (sub.equals("debug")
            && args[1].equalsIgnoreCase("off")
            && sender instanceof Player staff) {
          engine.stopDebug(staff.getUniqueId());
          sender.sendMessage("[VAC] Debug desativado.");
          return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        PlayerData d = target == null ? null : engine.player(target.getUniqueId());
        if (d == null) {
          sender.sendMessage("[VAC] Jogador não encontrado nesta sessão.");
          return true;
        }
        if (sub.equals("debug")) {
          if (sender instanceof Player staff) {
            engine.debug(staff.getUniqueId(), d.uuid, args.length > 2 ? args[2] : "");
            sender.sendMessage("[VAC] Debug ativado para " + d.name);
          }
          return true;
        }
        if (sub.equals("logs")) {
          UUID staff = sender instanceof Player p ? p.getUniqueId() : null;
          engine
              .store
              .recent(d.uuid, 15)
              .whenComplete(
                  (rows, error) ->
                      engine.completeOnMain(
                          () -> {
                            CommandSender recipient =
                                staff == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(staff);
                            if (recipient != null
                                && recipient.hasPermission("valoryanticheat.logs")) {
                              if (error != null)
                                recipient.sendMessage("[VAC] Consulta indisponível.");
                              else rows.forEach(recipient::sendMessage);
                            }
                          }));
          return true;
        }
        if (sub.equals("profile")) {
          if (sender instanceof Player staff && staff.hasPermission("valoryanticheat.gui"))
            profile(staff, d);
          else sender.sendMessage(summary(d));
        } else
          for (var v : d.violations.snapshot()) sender.sendMessage(v.key() + " " + v.debugData());
      }
      default ->
          sender.sendMessage(
              "/vac [alerts|profile|violations|logs|debug|status|checks|performance|reload]");
    }
    return true;
  }

  private void list(Player viewer, int page, boolean suspects) {
    List<PlayerData> players = new ArrayList<>(engine.players());
    long now = System.nanoTime();
    players.sort(Comparator.comparingDouble((PlayerData d) -> d.risk.score(now)).reversed());
    if (suspects) players.removeIf(d -> d.risk.score(now) < 15);
    int pages = Math.max(1, (players.size() + 44) / 45);
    page = Math.max(0, Math.min(page, pages - 1));
    Menu holder = new Menu(viewer.getUniqueId(), page, suspects, null);
    Inventory inventory =
        Bukkit.createInventory(
            holder, 54, Component.text("VALORY • Anticheat " + (page + 1) + "/" + pages));
    holder.inventory = inventory;
    for (int slot = 0; slot < 45 && page * 45 + slot < players.size(); slot++) {
      PlayerData d = players.get(page * 45 + slot);
      holder.targets.put(slot, d.uuid);
      inventory.setItem(
          slot,
          item(
              Material.PLAYER_HEAD,
              d.name,
              List.of(
                  "Risk: " + Math.round(d.risk.score(now)) + "/100",
                  "Ping: "
                      + Math.round(d.ping)
                      + "ms | TPS: "
                      + String.format(Locale.ROOT, "%.1f", d.tps),
                  "Checks: " + d.risk.levels(now).keySet(),
                  "Perfil: " + d.compatibility)));
    }
    inventory.setItem(45, item(Material.ARROW, "Anterior", List.of()));
    inventory.setItem(
        49,
        item(
            Material.COMPARATOR,
            suspects ? "Mostrar todos" : "Mostrar suspeitos",
            List.of("Ordenados por risk", "Índice de evidências, não probabilidade")));
    inventory.setItem(53, item(Material.ARROW, "Próxima", List.of()));
    viewer.openInventory(inventory);
  }

  private void profile(Player viewer, PlayerData d) {
    Menu holder = new Menu(viewer.getUniqueId(), 0, false, d.uuid);
    Inventory inv = Bukkit.createInventory(holder, 54, Component.text("VAC • " + d.name));
    holder.inventory = inv;
    inv.setItem(
        4,
        item(
            Material.PLAYER_HEAD,
            d.name,
            List.of(
                summary(d),
                "Exemptions: " + d.exemptions.describe(System.nanoTime()),
                "Environment: " + d.environment.reason())));
    int slot = 9;
    for (var v : d.violations.snapshot()) {
      if (slot >= 36) break;
      inv.setItem(
          slot++,
          item(
              Material.PAPER,
              v.key(),
              List.of(v.description(), "Qualidade: " + v.confidence(), v.debugData().toString())));
    }
    inv.setItem(
        40,
        item(
            Material.ENDER_EYE,
            "Evidência / replay",
            List.of(
                d.evidenceId.isEmpty() ? "Nenhuma evidência" : d.evidenceId,
                "Gravação: " + engine.network.durable(d.evidenceId),
                "Requer valory.replay.view")));
    inv.setItem(
        42,
        item(
            Material.BOOK,
            "Punição vinculada",
            List.of(
                d.punishmentId.isEmpty() ? "Sem punição nesta sessão" : d.punishmentId,
                "Servidor: " + engine.settings().server)));
    inv.setItem(45, item(Material.ARROW, "Voltar", List.of()));
    viewer.openInventory(inv);
  }

  private String summary(PlayerData d) {
    return d.name
        + " | Risk "
        + Math.round(d.risk.score(System.nanoTime()))
        + "/100 | VL "
        + d.risk.levels(System.nanoTime())
        + " | "
        + Math.round(d.ping)
        + "ms | "
        + d.compatibility;
  }

  private ItemStack item(Material type, String title, List<String> lines) {
    ItemStack item = new ItemStack(type);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text(title, NamedTextColor.LIGHT_PURPLE));
    List<Component> lore = new ArrayList<>();
    for (String line : lines) lore.add(Component.text(line, NamedTextColor.GRAY));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  @EventHandler
  public void click(InventoryClickEvent e) {
    if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player p)
        || !p.getUniqueId().equals(m.viewer)
        || !p.hasPermission("valoryanticheat.gui")
        || e.getRawSlot() < 0
        || e.getRawSlot() >= 54) return;
    int slot = e.getRawSlot();
    if (m.subject != null) {
      if (slot == 45) list(p, 0, false);
      if (slot == 40
          && p.hasPermission("valoryanticheat.profile")
          && p.hasPermission("valory.replay.view")) {
        PlayerData d = engine.player(m.subject);
        if (d != null && !d.evidenceId.isEmpty()) engine.network.play(p, d.evidenceId);
      }
      return;
    }
    if (slot == 45) list(p, m.page - 1, m.suspects);
    else if (slot == 53) list(p, m.page + 1, m.suspects);
    else if (slot == 49) list(p, 0, !m.suspects);
    else if (p.hasPermission("valoryanticheat.profile")) {
      PlayerData d = engine.player(m.targets.get(slot));
      if (d != null) profile(p, d);
    }
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
  }

  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1)
      return List.of(
          "alerts",
          "profile",
          "violations",
          "logs",
          "debug",
          "status",
          "checks",
          "performance",
          "reload");
    if (args.length == 2) {
      List<String> names = new ArrayList<>();
      for (Player p : Bukkit.getOnlinePlayers())
        if (!(sender instanceof Player viewer) || viewer.canSee(p)) names.add(p.getName());
      return names;
    }
    return List.of();
  }

  private static final class Menu implements InventoryHolder {
    final UUID viewer, subject;
    final int page;
    final boolean suspects;
    final Map<Integer, UUID> targets = new HashMap<>();
    Inventory inventory;

    Menu(UUID viewer, int page, boolean suspects, UUID subject) {
      this.viewer = viewer;
      this.page = page;
      this.suspects = suspects;
      this.subject = subject;
    }

    public Inventory getInventory() {
      return inventory;
    }
  }
}
