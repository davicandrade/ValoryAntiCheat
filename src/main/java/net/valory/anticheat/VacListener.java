package net.valory.anticheat;

import java.time.Duration;
import net.valory.anticheat.api.*;
import net.valory.anticheat.math.Vec3;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

public final class VacListener implements Listener {
  private final VacEngine engine;

  public VacListener(VacEngine engine) {
    this.engine = engine;
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    engine.join(e.getPlayer());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    engine.quit(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void teleport(PlayerTeleportEvent e) {
    engine.teleport(e.getPlayer().getUniqueId(), "bukkit:" + e.getCause());
  }

  @EventHandler
  public void respawn(PlayerRespawnEvent e) {
    engine.teleport(e.getPlayer().getUniqueId(), "respawn");
  }

  @EventHandler
  public void world(PlayerChangedWorldEvent e) {
    engine.teleport(e.getPlayer().getUniqueId(), "world");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void velocity(PlayerVelocityEvent e) {
    var v = e.getVelocity();
    engine.customVelocity(
        e.getPlayer().getUniqueId(), new Vec3(v.getX(), v.getY(), v.getZ()), "bukkit-velocity");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageEvent e) {
    if (e.getEntity() instanceof org.bukkit.entity.Player p) {
      var d = engine.player(p.getUniqueId());
      if (d != null) d.lastDamage = System.nanoTime();
      engine.exempt(p.getUniqueId(), ExemptionType.VELOCITY, Duration.ofMillis(750), "damage");
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void flight(PlayerToggleFlightEvent e) {
    engine.exempt(
        e.getPlayer().getUniqueId(),
        ExemptionType.FLIGHT,
        Duration.ofSeconds(2),
        "flight-transition");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void mode(PlayerGameModeChangeEvent e) {
    engine.exempt(
        e.getPlayer().getUniqueId(),
        ExemptionType.CUSTOM_MOVEMENT,
        Duration.ofSeconds(2),
        "game-mode");
  }
}
