package net.valory.anticheat.api;

import java.time.Duration;
import java.util.*;
import net.valory.anticheat.check.CheckResult;
import net.valory.anticheat.math.Vec3;

public interface AntiCheatService {
  double risk(UUID player);

  List<CheckResult> violations(UUID player);

  void exempt(UUID player, ExemptionType type, Duration duration, String source);

  void removeExemption(UUID player, ExemptionType type, String source);

  void customVelocity(UUID player, Vec3 velocity, String source);

  void teleport(UUID player, String source);

  boolean investigating(UUID player);

  void clientProfile(UUID player, boolean javaCompatible, String source);
}
