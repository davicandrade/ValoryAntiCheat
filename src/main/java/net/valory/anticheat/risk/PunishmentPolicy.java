package net.valory.anticheat.risk;

import java.util.*;
import net.valory.anticheat.check.CheckResult;

public final class PunishmentPolicy {
  private static final Set<String> VALIDATED_AUTOBAN_CHECKS = Set.of();

  public boolean eligible(List<CheckResult> history, long now, boolean durable, double risk) {
    if (!durable || risk < 90) return false;
    Set<String> independent = new HashSet<>();
    for (CheckResult result : history)
      if (VALIDATED_AUTOBAN_CHECKS.contains(result.key())
          && result.confidence() >= .95
          && now - result.monotonicNanos() < 30_000_000_000L)
        independent.add(result.independenceGroup());
    return independent.size() >= 3;
  }
}
