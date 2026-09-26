package net.valory.anticheat.check;

import java.util.Map;
import net.valory.anticheat.api.Family;
import net.valory.anticheat.packet.PacketFrame;
import net.valory.anticheat.processor.RotationSeries;
import net.valory.anticheat.state.PlayerData;

public final class AimPatternCheck implements Check {
  public String name() {
    return "AimPattern";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    if (p.kind() != PacketFrame.Kind.MOVE || !p.rotation()) return;
    if (d.exempt(Family.COMBAT, p.nanos())
        || p.nanos() - d.lastAttack > 2_000_000_000L
        || d.latency.jitterMillis() > 40) {
      d.rotationSeries.clear();
      return;
    }
    var summary = d.rotationSeries.add(Math.abs(RotationSeries.yawDelta(p.yaw(), d.yaw)));
    if (summary != null
        && summary.mean() > .2
        && summary.mean() < 30
        && summary.variance() < .00001
        && summary.repeated() >= 60)
      c.flag(
          d,
          "AimAssist",
          "A",
          Family.COMBAT,
          "rotation-statistics",
          .25,
          .25,
          "Repeated combat rotation; may be legitimate constant input",
          Map.of(
              "meanYaw",
              "" + summary.mean(),
              "variance",
              "" + summary.variance(),
              "repeated",
              "" + summary.repeated(),
              "samples",
              "64"),
          p.nanos());
  }
}
