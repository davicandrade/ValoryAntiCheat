package net.valory.anticheat.check;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.state.*;

public final class ActionChecks implements Check {
  public String name() {
    return "Action";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    if (p.kind() == PacketFrame.Kind.SWING) {
      if (d.lastSwing > 0 && p.nanos() - d.lastAttack < 2_000_000_000L) {
        double interval = (p.nanos() - d.lastSwing) / 1e6;
        if (interval > 15 && interval < 250) d.clicks.add(interval);
        else d.clicks.clear();
      }
      d.lastSwing = p.nanos();
      if (d.clicks.size() == 64) {
        double mean = 0, var = 0;
        for (int i = 0; i < 64; i++) mean += d.clicks.newest(i) / 64;
        for (int i = 0; i < 64; i++) var += Math.pow(d.clicks.newest(i) - mean, 2) / 64;
        if (var < .15)
          c.flag(
              d,
              "AutoClicker",
              "A",
              Family.COMBAT,
              "click-statistics",
              .35,
              1,
              "Low-variance click series; informational only",
              Map.of("meanMs", "" + mean, "variance", "" + var, "samples", "64"),
              p.nanos());
      }
    }
    if (p.kind() == PacketFrame.Kind.PLACE
        || p.kind() == PacketFrame.Kind.DIG
        || p.kind() == PacketFrame.Kind.USE) d.actionsThisTick++;
    if (p.kind() == PacketFrame.Kind.ATTACK) {
      d.attacksThisMove++;
      if (d.lastTarget != p.id() && d.attacksThisMove > 4)
        c.flag(
            d,
            "MultiAura",
            "A",
            Family.COMBAT,
            "attack-order",
            .45,
            1,
            "Many target switches between movement packets; observational",
            Map.of("attacks", "" + d.attacksThisMove),
            p.nanos());
      d.lastTarget = p.id();
    }
    if (p.kind() == PacketFrame.Kind.MOVE) d.attacksThisMove = 0;
  }
}
