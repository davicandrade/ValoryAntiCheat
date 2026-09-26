package net.valory.anticheat.check;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.state.*;

public final class ProtocolChecks implements Check {
  public String name() {
    return "Protocol";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    if (p.kind() == PacketFrame.Kind.INVALID)
      c.flag(
          d,
          "BadPackets",
          "A",
          Family.PACKET,
          "packet-structure",
          1,
          5,
          "Invalid packet rejected before state update",
          Map.of("packet", p.detail(), "reason", "non-finite coordinate or malformed payload"),
          p.nanos());
    if (p.kind() == PacketFrame.Kind.MOVE && p.rotation() && Math.abs(p.pitch()) > 90.001)
      c.flag(
          d,
          "InvalidMove",
          "A",
          Family.PACKET,
          "packet-structure",
          .9,
          3,
          "Pitch outside protocol movement domain",
          Map.of("pitch", "" + p.pitch()),
          p.nanos());
  }
}
