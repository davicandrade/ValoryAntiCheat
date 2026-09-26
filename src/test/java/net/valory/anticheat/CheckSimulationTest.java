package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.state.*;
import net.valory.anticheat.world.*;
import org.junit.jupiter.api.Test;

class CheckSimulationTest {
  static final class Context implements CheckContext {
    List<CheckResult> results = new ArrayList<>();
    Map<Integer, PlayerData> players = new HashMap<>();

    public PlayerData entity(int id) {
      return players.get(id);
    }

    public boolean enabled(String key) {
      return true;
    }

    public void result(PlayerData d, CheckResult r) {
      results.add(r);
    }
  }

  PlayerData player() {
    PlayerData d = new PlayerData(UUID.randomUUID(), "Simulation", 1);
    d.javaChecks = true;
    d.world = UUID.randomUUID();
    d.hasPosition = true;
    d.stableMoves = 20;
    d.ground = d.previousGround = true;
    d.serverGround = true;
    for (int i = 0; i < 3; i++) {
      d.latency.sent(i, 1);
      d.latency.acknowledge(i, 50_000_001);
    }
    return d;
  }

  void frame(PlayerData d, double step, long now, MovementChecks checks, Context context) {
    d.environment =
        new Environment(
            now - 1,
            d.world,
            d.position,
            List.of(new Box(d.position.x() - 2, -1, -2, d.position.x() + 2, 0, 2)),
            true,
            false,
            true,
            .6,
            "STONE",
            "SIMPLE");
    d.lastMove = now - 50_000_000;
    PacketFrame f =
        new PacketFrame(
            PacketFrame.Kind.MOVE, now, d.position.x() + step, 0, 0, 0, 0, 0, 5, "MOVE");
    checks.evaluate(d, f, context);
    d.delta = new Vec3(step, 0, 0);
    d.position = new Vec3(f.x(), 0, 0);
  }

  @Test
  void normalWalkDoesNotFlag() {
    PlayerData d = player();
    Context c = new Context();
    MovementChecks checks = new MovementChecks();
    for (int i = 0; i < 400; i++) frame(d, .287, 5_000_000_000L + i * 50_000_000L, checks, c);
    assertTrue(c.results.isEmpty(), c.results.toString());
  }

  @Test
  void persistentImpossibleAccelerationProducesEvidence() {
    PlayerData d = player();
    Context c = new Context();
    MovementChecks checks = new MovementChecks();
    for (int i = 0; i < 100; i++) frame(d, 1.1, 5_000_000_000L + i * 50_000_000L, checks, c);
    assertTrue(c.results.stream().anyMatch(r -> r.check().equals("Speed")));
    assertTrue(c.results.getFirst().debugData().containsKey("predictedMax"));
  }

  @Test
  void lagAndHighPingProduceNoMovementVerdict() {
    for (boolean highPing : List.of(false, true)) {
      PlayerData d = player();
      if (highPing)
        for (int i = 0; i < 100; i++) {
          d.latency.sent(i, 1);
          d.latency.acknowledge(i, 800_000_001);
        }
      else d.tps = 15;
      Context c = new Context();
      for (int i = 0; i < 100; i++)
        frame(d, 1.1, 5_000_000_000L + i * 50_000_000L, new MovementChecks(), c);
      assertTrue(c.results.isEmpty());
    }
  }

  @Test
  void territoryFlightKeepsCombatActive() {
    PlayerData d = player();
    d.exemptions.grant(ExemptionType.FLIGHT, "ValoryTerritory", 1, 20_000_000_000L);
    Context c = new Context();
    for (int i = 0; i < 100; i++)
      frame(d, 1.1, 5_000_000_000L + i * 50_000_000L, new MovementChecks(), c);
    assertTrue(c.results.isEmpty());
    assertFalse(d.exempt(Family.COMBAT, 6_000_000_000L));
  }

  @Test
  void loadSimulation200PlayersProducesNoViolations() {
    PlayerData[] players = new PlayerData[200];
    for (int i = 0; i < players.length; i++) players[i] = player();
    Context c = new Context();
    MovementChecks checks = new MovementChecks();
    long start = System.nanoTime();
    for (int t = 0; t < 200; t++)
      for (PlayerData d : players) frame(d, .287, 5_000_000_000L + t * 50_000_000L, checks, c);
    assertTrue(c.results.isEmpty());
    System.out.printf(
        Locale.ROOT,
        "Pure-core simulation: 40,000 moves / 200 sessions in %.2f ms (not a live server"
            + " benchmark)%n",
        (System.nanoTime() - start) / 1e6);
  }
}
