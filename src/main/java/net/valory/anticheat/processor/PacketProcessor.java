package net.valory.anticheat.processor;

import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.event.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.config.*;
import net.valory.anticheat.integration.*;
import net.valory.anticheat.math.*;
import net.valory.anticheat.metrics.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.risk.*;
import net.valory.anticheat.setback.*;
import net.valory.anticheat.state.*;
import net.valory.anticheat.storage.*;
import net.valory.anticheat.world.*;
import org.bukkit.*;

public final class PacketProcessor {
  private final Check[] checks;
  private final CheckContext context;
  private final Performance metrics;

  public PacketProcessor(Check[] checks, CheckContext context, Performance metrics) {
    this.checks = checks;
    this.context = context;
    this.metrics = metrics;
  }

  public void process(PlayerData d, PacketFrame f) {
    long now = f.nanos();
    switch (f.kind()) {
      case TELEPORT_OUT -> {
        d.resetMotion(now);
        d.pendingTeleport = f.id();
      }
      case TELEPORT_ACK -> {
        if (d.pendingTeleport == f.id()) {
          d.pendingTeleport = Integer.MIN_VALUE;
          d.resetMotion(now);
        }
      }
      case WORLD_CHANGE -> d.resetMotion(now);
      case VELOCITY -> {
        d.velocity = new Vec3(f.x(), f.y(), f.z());
        d.lastVelocity = now;
        d.exemptions.grant(ExemptionType.VELOCITY, "server-packet", now, 2_000_000_000L);
      }
      case EXPLOSION -> {
        d.lastVelocity = now;
        d.exemptions.grant(ExemptionType.VELOCITY, "explosion", now, 3_000_000_000L);
      }
      case PONG -> d.latency.acknowledge(f.id(), now);
      case BLOCK_CHANGE ->
          d.worldUncertainUntil = now + (long) ((250 + d.latency.rttMillis()) * 1e6);
      case ACTION -> {
        if (f.detail().equals("START_SPRINTING")) d.sprint = true;
        if (f.detail().equals("STOP_SPRINTING")) d.sprint = false;
        if (f.detail().equals("START_SNEAKING")) d.sneak = true;
        if (f.detail().equals("STOP_SNEAKING")) d.sneak = false;
      }
      default -> {}
    }
    for (Check check : checks) {
      long start = System.nanoTime();
      check.evaluate(d, f, context);
      metrics.check(check.name(), System.nanoTime() - start);
    }
    switch (f.kind()) {
      case MOVE -> {
        d.movementTick++;
        d.lastYaw = d.yaw;
        d.lastPitch = d.pitch;
        if (f.rotation()) {
          d.yaw = f.yaw();
          d.pitch = f.pitch();
        }
        if (f.position()) {
          Vec3 position = new Vec3(f.x(), f.y(), f.z());
          d.delta = d.hasPosition ? position.subtract(d.position) : Vec3.ZERO;
          d.position = position;
          d.hasPosition = true;
          d.stableMoves++;
          d.previousGround = d.ground;
          d.ground =
              d.environment != null
                  && d.environment.supported(Box.player(position, d.width, d.height));
          if (d.consistent(now)
              && d.ground
              && d.environment.clear(Box.player(position, d.width, d.height).expand(-.001))
              && d.delta.horizontal() < .35
              && d.risk.vl("Speed.A", now) < 3
              && d.risk.vl("Fly.A", now) < 3) {
            d.safe = position;
            d.safeWorld = d.world;
            d.safeAt = now;
          }
        }
        d.lastMove = now;
      }
      case ATTACK -> d.lastAttack = now;
      case PLACE -> d.lastPlace = now;
      default -> {}
    }
  }
}
