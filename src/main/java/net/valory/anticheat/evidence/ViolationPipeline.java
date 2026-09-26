package net.valory.anticheat.evidence;

import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.valory.anticheat.api.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.config.*;
import net.valory.anticheat.integration.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.metrics.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.risk.*;
import net.valory.anticheat.setback.*;
import net.valory.anticheat.state.*;
import net.valory.anticheat.storage.*;
import net.valory.anticheat.world.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

public final class ViolationPipeline {
  private final org.bukkit.plugin.java.JavaPlugin plugin;
  private Settings settings;
  private final NetworkAdapter network;
  private final EvidenceStore store;
  private final SetbackEngine setbacks;
  private final PunishmentPolicy punishmentPolicy = new PunishmentPolicy();
  private final Set<UUID> alertSubscribers = new HashSet<>(), punishing = new HashSet<>();
  private final java.util.function.LongSupplier tick;
  private final java.util.function.Consumer<Runnable> completion;
  private volatile boolean closed;

  public ViolationPipeline(
      org.bukkit.plugin.java.JavaPlugin plugin,
      Settings settings,
      NetworkAdapter network,
      EvidenceStore store,
      SetbackEngine setbacks,
      java.util.function.LongSupplier tick,
      java.util.function.Consumer<Runnable> completion) {
    this.plugin = plugin;
    this.settings = settings;
    this.network = network;
    this.store = store;
    this.setbacks = setbacks;
    this.tick = tick;
    this.completion = completion;
  }

  public void reload(Settings settings) {
    this.settings = settings;
  }

  public boolean alerts(UUID id) {
    if (alertSubscribers.remove(id)) return false;
    alertSubscribers.add(id);
    return true;
  }

  public void remove(UUID id) {
    alertSubscribers.remove(id);
    punishing.remove(id);
  }

  public void close() {
    closed = true;
    alertSubscribers.clear();
    punishing.clear();
  }

  public void accept(PlayerData d, CheckResult original) {
    Settings.Policy policy = settings.checks.get(original.key());
    if (policy == null) return;
    CheckResult r =
        new CheckResult(
            original.check(),
            original.subtype(),
            original.family(),
            original.independenceGroup(),
            original.confidence(),
            original.weight() * policy.weight(),
            original.description(),
            original.debugData(),
            original.timestamp(),
            original.monotonicNanos());
    ViolationEvent event = new ViolationEvent(d.uuid, r);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) return;
    double vl = d.risk.add(r, policy.decay());
    d.violations.add(r);
    long now = r.monotonicNanos();
    double risk = d.risk.score(now);
    Player p = Bukkit.getPlayer(d.uuid);
    if (p == null) return;
    if (vl >= policy.alert() && now - d.lastEvidence > 30_000_000_000L && network.available()) {
      network
          .preserve(
              p,
              r,
              Map.of(
                  "check",
                  r.key(),
                  "risk",
                  "" + risk,
                  "vl",
                  "" + vl,
                  "ping",
                  "" + d.ping,
                  "tps",
                  "" + d.tps,
                  "server",
                  settings.server,
                  "position",
                  d.position.toString(),
                  "debug",
                  r.debugData().toString()))
          .ifPresent(
              id -> {
                d.evidenceId = id;
                d.lastEvidence = now;
              });
    }
    String context =
        "tick="
            + tick.getAsLong()
            + " clientTick="
            + d.movementTick
            + " world="
            + d.world
            + " position="
            + d.position
            + " delta="
            + d.delta
            + " velocity="
            + d.velocity
            + " ping="
            + d.ping
            + " tps="
            + d.tps
            + " exemptions="
            + d.exemptions.describe(now)
            + " profile="
            + d.compatibility
            + " history="
            + d.history.snapshot();
    if (!store.append(
        new EvidenceRecord(d.uuid, d.name, settings.server, r, risk, vl, context, d.evidenceId)))
      d.lastDebug = "Evidence storage unavailable/full";
    if (vl >= policy.alert() && now - d.lastAlert >= 2_000_000_000L) {
      d.lastAlert = now;
      Component alert =
          Component.text(
                  settings.prefix
                      + " "
                      + d.name
                      + " "
                      + r.check()
                      + " "
                      + r.subtype()
                      + " | VL "
                      + Math.round(vl)
                      + " | qualidade "
                      + Math.round(r.confidence() * 100)
                      + "% | "
                      + Math.round(d.ping)
                      + "ms",
                  NamedTextColor.LIGHT_PURPLE)
              .hoverEvent(
                  HoverEvent.showText(
                      Component.text(
                          r.description()
                              + "\n"
                              + r.debugData()
                              + "\nRisk é índice de evidência, não probabilidade.")))
              .clickEvent(ClickEvent.runCommand("/vac profile " + d.name));
      for (UUID staff : alertSubscribers) {
        Player viewer = Bukkit.getPlayer(staff);
        if (viewer != null && viewer.hasPermission("valoryanticheat.alerts"))
          viewer.sendMessage(alert);
      }
    }
    if (settings.setbacks
        && r.family() == Family.MOVEMENT
        && policy.action().equals("setback-only")
        && vl >= policy.setback()
        && r.confidence() >= .7) setbacks.apply(p, d, now);
    if (settings.punishments
        && vl >= policy.punish()
        && store.ready()
        && punishmentPolicy.eligible(
            d.violations.snapshot(), now, network.durable(d.evidenceId), risk)
        && punishing.add(d.uuid)) {
      var decision = new PunishmentDecisionEvent(d.uuid, d.evidenceId, risk);
      Bukkit.getPluginManager().callEvent(decision);
      if (decision.isCancelled()) {
        punishing.remove(d.uuid);
        return;
      }
      network
          .punish(p, d.evidenceId, "VAC: " + r.key(), context)
          .whenComplete(
              (id, error) -> {
                if (!closed)
                  completion.accept(
                      () -> {
                        punishing.remove(d.uuid);
                        if (error != null) {
                          plugin.getLogger().severe("VAC punishment not committed: " + error);
                          return;
                        }
                        d.punishmentId = id;
                        Player online = Bukkit.getPlayer(d.uuid);
                        if (online != null)
                          online.kick(
                              Component.text(
                                  "ValoryAntiCheat | " + id + " | Evidência " + d.evidenceId));
                      });
              });
    }
  }
}
