package net.valory.anticheat.state;

import java.util.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.processor.*;
import net.valory.anticheat.risk.*;
import net.valory.anticheat.world.*;

/** All fields except inbox are main-thread confined. No Player references retained. */
public final class PlayerData {
  public final UUID uuid;
  public final String name;
  public final int entityId;
  public final PacketInbox inbox = new PacketInbox(256);
  public final Ring<HistoryFrame> history = new Ring<>(100);
  public final Ring<CheckResult> violations = new Ring<>(64);
  public final RotationSeries rotationSeries = new RotationSeries();
  public final Ring<Double> clicks = new Ring<>(64);
  public final Exemptions exemptions = new Exemptions();
  public final LatencyTracker latency = new LatencyTracker();
  public final MovementClock clock = new MovementClock();
  public final RiskEngine risk = new RiskEngine(30);
  public final Map<String, Integer> streaks = new HashMap<>();
  public final Map<String, Long> emitted = new HashMap<>();
  public final EnumSet<Family> bypassFamilies = EnumSet.noneOf(Family.class);
  public final Set<String> bypassChecks = new HashSet<>();
  public double predictedHorizontal, predictedMinY, predictedMaxY;
  public Vec3 position = Vec3.ZERO, delta = Vec3.ZERO, velocity = Vec3.ZERO;
  public float yaw, pitch, lastYaw, lastPitch;
  public UUID world;
  public Environment environment;
  public double width = .6,
      height = 1.8,
      eyeHeight = 1.62,
      movementSpeed = .1,
      reach = 3,
      blockReach = 4.5;
  public long movementTick,
      lastMove,
      lastAttack,
      lastSwing,
      lastVelocity,
      lastTeleport,
      lastDamage,
      lastPlace,
      lastAlert,
      lastEvidence,
      lastSetback,
      teleportEpoch,
      quarantineUntil,
      worldUncertainUntil,
      lastValidMove;
  public long reportedFloodDrops;
  public long safeAt;
  public Vec3 safe;
  public UUID safeWorld;
  public int pendingTeleport = Integer.MIN_VALUE,
      jumpBoost,
      attacksThisMove,
      lastTarget = -1,
      stableMoves,
      protocol;
  public boolean hasPosition,
      ground,
      previousGround,
      serverGround,
      sprint,
      sneak,
      swimming,
      crawling,
      gliding,
      flying,
      vehicle,
      usingItem,
      allowFlight,
      bypass,
      javaChecks,
      inventoryOpen;
  public String compatibility = "UNKNOWN",
      evidenceId = "",
      punishmentId = "",
      lastDebug = "Aguardando amostras";
  public double ping, tps = 20;
  public int actionsThisTick;

  public PlayerData(UUID uuid, String name, int entityId) {
    this.uuid = uuid;
    this.name = name;
    this.entityId = entityId;
  }

  public void resetMotion(long now) {
    hasPosition = false;
    delta = Vec3.ZERO;
    safe = null;
    stableMoves = 0;
    lastTeleport = now;
    teleportEpoch++;
    history.clear();
    clock.reset();
    rotationSeries.clear();
    clicks.clear();
    streaks.clear();
    quarantineUntil = Math.max(quarantineUntil, now + 2_000_000_000L);
  }

  public boolean exempt(Family family, long now) {
    return bypass
        || bypassFamilies.contains(family)
        || exemptions.applies(family, now)
        || ((family == Family.MOVEMENT
                || family == Family.COMBAT
                || family == Family.TIMING
                || family == Family.ACTION)
            && (now < quarantineUntil || !javaChecks))
        || family == Family.MOVEMENT
            && (allowFlight
                || flying
                || vehicle
                || gliding
                || swimming
                || crawling
                || now < worldUncertainUntil
                || pendingTeleport != Integer.MIN_VALUE);
  }

  public boolean consistent(long now) {
    return !exempt(Family.MOVEMENT, now)
        && hasPosition
        && stableMoves >= 6
        && latency.ready()
        && latency.rttMillis() < 400
        && latency.jitterMillis() < 60
        && tps >= 19
        && environment != null
        && environment.covers(position, now);
  }
}
