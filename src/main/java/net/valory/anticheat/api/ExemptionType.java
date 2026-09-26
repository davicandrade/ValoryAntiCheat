package net.valory.anticheat.api;

public enum ExemptionType {
  FLIGHT,
  TELEPORT,
  VELOCITY,
  CUSTOM_MOVEMENT,
  MINIGAME,
  STAFF_ACTION,
  WORLD_CHANGE;

  public boolean affects(Family family) {
    return switch (this) {
      case FLIGHT, CUSTOM_MOVEMENT, MINIGAME, STAFF_ACTION -> family == Family.MOVEMENT;
      case VELOCITY -> family == Family.MOVEMENT;
      case TELEPORT, WORLD_CHANGE ->
          family == Family.MOVEMENT || family == Family.COMBAT || family == Family.ACTION;
    };
  }
}
