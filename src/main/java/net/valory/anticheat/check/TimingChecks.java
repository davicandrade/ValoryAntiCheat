package net.valory.anticheat.check;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.state.*;

public final class TimingChecks implements Check {
  public String name() {
    return "Timing";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    if (p.kind() != PacketFrame.Kind.MOVE) return;
    if (d.exempt(Family.TIMING, p.nanos())
        || d.tps < 19
        || !d.latency.ready()
        || d.latency.jitterMillis() > 80) {
      d.clock.reset();
      return;
    }
    double debt = d.clock.accept(p.nanos());
    if (d.clock.sustained())
      c.flag(
          d,
          "Timer",
          "A",
          Family.TIMING,
          "client-clock",
          .8,
          3,
          "Sustained client tick lead beyond burst allowance",
          Map.of(
              "debtMs",
              "" + debt / 1e6,
              "rttMs",
              "" + d.latency.rttMillis(),
              "jitterMs",
              "" + d.latency.jitterMillis()),
          p.nanos());
  }
}
