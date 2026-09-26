package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.prediction.*;
import net.valory.anticheat.state.*;
import net.valory.anticheat.world.*;
import org.junit.jupiter.api.Test;

class PredictionTest {
  private final MovementPrediction engine = new MovementPrediction();

  @Test
  void walkingAndSprintJumpFitEnvelope() {
    var e =
        engine.predict(
            new MovementPrediction.Input(
                new Vec3(.287, 0, 0), true, true, .6, .13, 0, true, false));
    assertTrue(e.horizontal() >= .287);
    assertTrue(e.maxY() >= .42);
  }

  @Test
  void potionAndJumpAttributeIncreaseAllowance() {
    var normal =
        engine.predict(
            new MovementPrediction.Input(Vec3.ZERO, true, true, .6, .1, 0, false, false));
    var boosted =
        engine.predict(
            new MovementPrediction.Input(Vec3.ZERO, true, true, .6, .2, 2, false, false));
    assertTrue(boosted.horizontal() > normal.horizontal());
    assertTrue(boosted.maxY() > normal.maxY());
  }

  @Test
  void airborneFallingPredictionIncludesActualStep() {
    var e =
        engine.predict(
            new MovementPrediction.Input(
                new Vec3(.2, -.5, 0), false, false, .6, .1, 0, false, false));
    assertTrue(e.minY() < (-.5 - .08) * .98);
    assertTrue(e.maxY() > (-.5 - .08) * .98);
    assertTrue(e.maxY() < -.5);
  }

  @Test
  void iceRetainsMomentum() {
    var normal =
        engine.predict(
            new MovementPrediction.Input(new Vec3(1, 0, 0), true, true, .6, .1, 0, false, false));
    var ice =
        engine.predict(
            new MovementPrediction.Input(new Vec3(1, 0, 0), true, true, .989, .1, 0, false, false));
    assertTrue(ice.horizontal() > normal.horizontal());
  }

  @Test
  void unsupportedEnvironmentNeverClaimsFullModel() {
    for (String reason :
        List.of("SLIME_BLOCK", "WATER", "LAVA", "ELYTRA", "BUBBLE_COLUMN", "PISTON", "COBWEB")) {
      Environment e =
          new Environment(
              10, UUID.randomUUID(), Vec3.ZERO, List.of(), true, true, false, .6, reason, reason);
      assertFalse(e.covers(Vec3.ZERO, 20));
    }
  }

  @Test
  void unloadedAndStaleWorldDoNotDecide() {
    Environment e =
        new Environment(
            10, UUID.randomUUID(), Vec3.ZERO, List.of(), false, false, false, .6, "?", "UNLOADED");
    assertFalse(e.covers(Vec3.ZERO, 20));
    Environment loaded =
        new Environment(
            10, e.world(), Vec3.ZERO, List.of(), true, false, false, .6, "STONE", "SIMPLE");
    assertFalse(loaded.covers(Vec3.ZERO, 300_000_000));
  }

  @Test
  void knockbackExemptionDoesNotDisableReach() {
    PlayerData d = new PlayerData(UUID.randomUUID(), "Test", 1);
    d.javaChecks = true;
    d.exemptions.grant(ExemptionType.VELOCITY, "server", 1, 100);
    assertTrue(d.exempt(Family.MOVEMENT, 2));
    assertFalse(d.exempt(Family.COMBAT, 2));
  }

  @Test
  void supportRequiresActualFeetCollision() {
    Box floor = new Box(-2, -1, -2, 2, 0, 2);
    Environment e =
        new Environment(
            1,
            UUID.randomUUID(),
            Vec3.ZERO,
            List.of(floor),
            true,
            false,
            true,
            .6,
            "STONE",
            "SIMPLE");
    assertTrue(e.supported(Box.player(Vec3.ZERO, .6, 1.8)));
    assertFalse(e.supported(Box.player(new Vec3(0, 2, 0), .6, 1.8)));
  }
}
