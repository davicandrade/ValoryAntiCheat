package net.valory.anticheat.packet;

import java.util.UUID;

public interface PacketTransport extends AutoCloseable {
  void bind(UUID player, Object platformPlayer);

  void start();

  void probe(UUID player, int token);

  String clientVersion(UUID player);

  boolean nativeProtocol(UUID player);

  void remove(UUID player);

  void close();
}
