package net.valory.anticheat.packet;

/** Transport-neutral decoded primitive payload. No Bukkit or retained Netty buffers. */
public record PacketFrame(
    Kind kind,
    long nanos,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    int id,
    int flags,
    String detail) {
  public enum Kind {
    MOVE,
    ATTACK,
    SWING,
    DIG,
    PLACE,
    USE,
    INVENTORY,
    ACTION,
    ABILITIES,
    VEHICLE,
    TELEPORT_OUT,
    TELEPORT_ACK,
    VELOCITY,
    EXPLOSION,
    PING_OUT,
    PONG,
    KEEPALIVE_OUT,
    KEEPALIVE,
    SETTINGS,
    WORLD_CHANGE,
    BLOCK_CHANGE,
    INVALID
  }

  public boolean position() {
    return (flags & 1) != 0;
  }

  public boolean rotation() {
    return (flags & 2) != 0;
  }

  public boolean ground() {
    return (flags & 4) != 0;
  }

  public static PacketFrame simple(Kind kind, long time, int id, String detail) {
    return new PacketFrame(kind, time, 0, 0, 0, 0, 0, id, 0, detail);
  }
}
