package net.valory.anticheat.check;

import java.util.Map;
import net.valory.anticheat.api.Family;
import net.valory.anticheat.math.*;
import net.valory.anticheat.packet.PacketFrame;
import net.valory.anticheat.state.PlayerData;

public final class BlockActionChecks implements Check {
  public String name() {
    return "BlockAction";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    boolean place = p.kind() == PacketFrame.Kind.PLACE;
    boolean finish = p.kind() == PacketFrame.Kind.DIG && p.detail().equals("FINISHED_DIGGING");
    if ((!place && !finish)
        || d.exempt(Family.ACTION, p.nanos())
        || !d.hasPosition
        || !d.latency.ready()
        || d.latency.jitterMillis() > 60
        || d.tps < 19
        || p.nanos() - d.lastMove > 150_000_000L
        || p.nanos() < d.worldUncertainUntil) return;
    Vec3 eye = d.position.add(new Vec3(0, d.eyeHeight, 0));
    Box target = new Box(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1).expand(.15);
    double distance = target.distance(eye);
    boolean missed =
        !Double.isFinite(target.ray(eye, Vec3.direction(d.yaw, d.pitch), d.blockReach + .5));
    Map<String, String> debug =
        Map.of(
            "block",
            p.x() + "," + p.y() + "," + p.z(),
            "distance",
            "" + distance,
            "maxReach",
            "" + d.blockReach,
            "face",
            "" + p.flags(),
            "cursorX",
            "" + p.yaw(),
            "cursorY",
            "" + p.pitch(),
            "detail",
            p.detail(),
            "rayMiss",
            "" + missed,
            "deltaXZ",
            "" + d.delta.horizontal());
    String name = place ? "ImpossiblePlace" : "ImpossibleBreak";
    if (c.streak(d, name + ".A", distance > d.blockReach + .5, 4))
      c.flag(
          d,
          name,
          "A",
          Family.ACTION,
          "block-geometry",
          .7,
          2,
          "Repeated block target outside attribute reach",
          debug,
          p.nanos());
    if (place) {
      long interval = p.nanos() - d.lastPlace;
      boolean scaffold =
          missed
              && d.delta.horizontal() > .15
              && p.y() < d.position.y()
              && interval > 20_000_000
              && interval < 150_000_000;
      if (c.streak(d, "Scaffold.A", scaffold, 8))
        c.flag(
            d,
            "Scaffold",
            "A",
            Family.ACTION,
            "block-geometry",
            .5,
            1,
            "Placement sequence combines ray misses and movement; observational",
            debug,
            p.nanos());
      if (c.streak(
          d,
          "FastPlace.A",
          missed && distance > d.blockReach + .5 && interval > 0 && interval < 40_000_000,
          6))
        c.flag(
            d,
            "FastPlace",
            "A",
            Family.ACTION,
            "block-geometry",
            .6,
            1,
            "Fast sequence also violates block geometry",
            debug,
            p.nanos());
    }
  }
}
