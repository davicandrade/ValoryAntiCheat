package net.valory.anticheat.check;

import net.valory.anticheat.packet.PacketFrame;
import net.valory.anticheat.state.PlayerData;

public interface Check {
  String name();

  void evaluate(PlayerData player, PacketFrame frame, CheckContext context);
}
