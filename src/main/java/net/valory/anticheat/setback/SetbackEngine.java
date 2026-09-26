package net.valory.anticheat.setback;

import net.valory.anticheat.state.PlayerData;
import net.valory.anticheat.world.WorldSampler;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public final class SetbackEngine {
  private final WorldSampler world;

  public SetbackEngine(WorldSampler world) {
    this.world = world;
  }

  public boolean apply(Player p, PlayerData d, long now) {
    if (d.safe == null
        || now - d.safeAt > 5_000_000_000L
        || now - d.lastSetback < 3_000_000_000L
        || !p.getWorld().getUID().equals(d.safeWorld)
        || d.pendingTeleport != Integer.MIN_VALUE) return false;
    Location target =
        new Location(p.getWorld(), d.safe.x(), d.safe.y(), d.safe.z(), d.yaw, d.pitch);
    if (!world.safe(p, target)) return false;
    d.lastSetback = now; // reserve before teleport, preventing reentrant loops
    if (!p.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) return false;
    p.setVelocity(new Vector());
    d.resetMotion(now);
    d.velocity = net.valory.anticheat.math.Vec3.ZERO;
    return true;
  }
}
