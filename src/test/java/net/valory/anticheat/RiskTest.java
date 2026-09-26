package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.risk.*;
import org.junit.jupiter.api.Test;

class RiskTest {
  private CheckResult result(String name, String group, long now) {
    return new CheckResult(name, "A", Family.COMBAT, group, 1, 10, "test", Map.of(), 100, now);
  }

  @Test
  void preciseHalfLifeAndTimeReversal() {
    RiskEngine risk = new RiskEngine(30);
    risk.add(result("Reach", "geometry", 1_000_000_000));
    assertEquals(5, risk.vl("Reach.A", 31_000_000_000L), 1e-9);
    assertEquals(5, risk.vl("Reach.A", 1), 1e-9);
  }

  @Test
  void perCheckDecayIsEffective() {
    RiskEngine risk = new RiskEngine(30);
    risk.add(result("Reach", "geometry", 1_000_000_000), 10);
    assertEquals(5, risk.vl("Reach.A", 11_000_000_000L), 1e-9);
  }

  @Test
  void correlatedChecksAreNotIndependent() {
    RiskEngine r = new RiskEngine(30);
    r.add(result("Reach", "geometry", 1));
    r.add(result("Hitbox", "geometry", 1));
    assertEquals(1, r.independent(1));
    double score = r.score(1);
    RiskEngine single = new RiskEngine(30);
    single.add(result("Reach", "geometry", 1));
    assertEquals(single.score(1), score);
    r.add(result("Timer", "clock", 1));
    assertEquals(2, r.independent(1));
    assertTrue(r.score(1) > score);
  }

  @Test
  void scoresAreBoundedAndExpire() {
    RiskEngine r = new RiskEngine(30);
    for (int i = 0; i < 1000; i++) r.add(result("Reach", "geometry", 1));
    assertTrue(r.score(1) < 100);
    assertTrue(r.score(3_600_000_000_000L) < .001);
  }

  @Test
  void statisticalAndUnvalidatedChecksCannotBan() {
    PunishmentPolicy p = new PunishmentPolicy();
    List<CheckResult> results =
        List.of(
            result("AimAssist", "aim", 1),
            result("Reach", "geometry", 1),
            result("Timer", "clock", 1));
    assertFalse(p.eligible(results, 2, true, 100));
    assertFalse(p.eligible(results, 2, false, 100));
  }

  @Test
  void malformedConfidenceRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CheckResult("X", "A", Family.COMBAT, "g", Double.NaN, 1, "", Map.of(), 0, 0));
  }
}
