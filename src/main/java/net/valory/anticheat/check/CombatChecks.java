package net.valory.anticheat.check;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.state.*;

public final class CombatChecks implements Check {
  public String name() {
    return "Combat";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    if (p.kind() != PacketFrame.Kind.ATTACK
        || d.exempt(Family.COMBAT, p.nanos())
        || !d.hasPosition
        || !d.latency.ready()
        || d.tps < 19) return;
    PlayerData target = c.entity(p.id());
    if (target == null
        || !Objects.equals(d.world, target.world)
        || p.nanos() - target.lastTeleport < 2_000_000_000L
        || p.nanos() - d.lastMove > 150_000_000L
        || d.latency.rttMillis() > 500
        || d.latency.jitterMillis() > 80) return;
    Vec3 eye = d.position.add(new Vec3(0, d.eyeHeight, 0)),
        direction = Vec3.direction(d.yaw, d.pitch);
    long rewind = p.nanos() - d.latency.rewindNanos(),
        window = (long) ((100 + 2 * d.latency.jitterMillis()) * 1e6);
    double min = Double.POSITIVE_INFINITY, ray = Double.POSITIVE_INFINITY;
    int candidates = 0;
    long oldest = Long.MAX_VALUE, newest = Long.MIN_VALUE;
    for (int i = 0; i < target.history.size(); i++) {
      HistoryFrame h = target.history.newest(i);
      if (h.teleportEpoch() != target.teleportEpoch || !h.world().equals(d.world)) continue;
      oldest = Math.min(oldest, h.nanos());
      newest = Math.max(newest, h.nanos());
      if (Math.abs(h.nanos() - rewind) > window) continue;
      Box box = h.bounds().expand(.15);
      min = Math.min(min, box.distance(eye));
      ray = Math.min(ray, box.ray(eye, direction, d.reach + .2));
      candidates++;
    }
    if (candidates < 3 || oldest > rewind - window || newest < rewind) return;
    Map<String, String> debug =
        Map.of(
            "minDistance",
            "" + min,
            "rayDistance",
            "" + ray,
            "maxReach",
            "" + d.reach,
            "targetRewindMs",
            "" + (p.nanos() - rewind) / 1e6,
            "candidateFrames",
            "" + candidates,
            "target",
            target.uuid.toString(),
            "yaw",
            "" + d.yaw,
            "pitch",
            "" + d.pitch);
    d.lastDebug = debug.toString();
    if (c.streak(d, "Reach.A", min > d.reach + .2, 3))
      c.flag(
          d,
          "Reach",
          "A",
          Family.COMBAT,
          "attack-geometry",
          .75,
          3,
          "Outside every plausible historical bounding box",
          debug,
          p.nanos());
    if (c.streak(d, "Hitbox.A", !Double.isFinite(ray), 5))
      c.flag(
          d,
          "Hitbox",
          "A",
          Family.COMBAT,
          "attack-geometry",
          .65,
          2,
          "Ray missed all candidate hitboxes",
          debug,
          p.nanos());
  }
}
