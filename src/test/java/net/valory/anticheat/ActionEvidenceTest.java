package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.packet.PacketFrame;
import net.valory.anticheat.processor.RotationSeries;
import net.valory.anticheat.state.PlayerData;
import org.junit.jupiter.api.Test;

class ActionEvidenceTest {
  PlayerData subject() {
    PlayerData d = new PlayerData(UUID.randomUUID(), "Player", 1);
    d.javaChecks = true;
    d.hasPosition = true;
    for (int i = 0; i < 3; i++) {
      d.latency.sent(i, 1);
      d.latency.acknowledge(i, 50_000_001);
    }
    return d;
  }

  @Test
  void placementSpeedAloneIsNotScaffoldOrFastPlace() {
    PlayerData d = subject();
    var context = new CheckSimulationTest.Context();
    var checks = new BlockActionChecks();
    d.position = new Vec3(0, 1, 0);
    d.delta = new Vec3(.28, 0, 0);
    for (int i = 0; i < 200; i++) {
      long now = 5_000_000_000L + i * 20_000_000L;
      d.lastMove = now;
      d.lastPlace = now - 20_000_000;
      checks.evaluate(
          d,
          new PacketFrame(PacketFrame.Kind.PLACE, now, 0, 1, 1, .5f, .5f, i, 1, "hitZ=.5"),
          context);
    }
    assertTrue(context.results.isEmpty(), context.results.toString());
  }

  @Test
  void repeatedOutOfRangeBlocksGenerateMeasuredEvidence() {
    PlayerData d = subject();
    var context = new CheckSimulationTest.Context();
    var check = new BlockActionChecks();
    for (int i = 0; i < 30; i++) {
      long now = 5_000_000_000L + i * 50_000_000L;
      d.lastMove = now;
      check.evaluate(
          d,
          new PacketFrame(PacketFrame.Kind.PLACE, now, 30, 0, 0, .5f, .5f, i, 1, "hitZ=.5"),
          context);
    }
    assertTrue(context.results.stream().anyMatch(r -> r.check().equals("ImpossiblePlace")));
  }

  @Test
  void yawWrapDoesNotLookLikeSnap() {
    assertEquals(2, RotationSeries.yawDelta(-179, 179));
    assertEquals(-2, RotationSeries.yawDelta(179, -179));
  }

  @Test
  void stationaryAimNeverGeneratesSuspicion() {
    PlayerData d = subject();
    var context = new CheckSimulationTest.Context();
    var check = new AimPatternCheck();
    for (int i = 0; i < 256; i++) {
      long now = 5_000_000_000L + i * 50_000_000L;
      d.lastAttack = now;
      check.evaluate(
          d, new PacketFrame(PacketFrame.Kind.MOVE, now, 0, 0, 0, 0, 0, 0, 2, "rotation"), context);
    }
    assertTrue(context.results.isEmpty());
  }
}
