package net.valory.anticheat.risk;

import java.util.*;
import net.valory.anticheat.check.CheckResult;

/** Review gate: experimental model checks cannot become autoban-capable by changing YAML. */
public final class PunishmentPolicy {
  // This release has no production-calibrated checks. Deliberately empty, not a hidden bypass.
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
