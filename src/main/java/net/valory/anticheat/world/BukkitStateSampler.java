package net.valory.anticheat.world;

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
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class BukkitStateSampler {
  private final NetworkAdapter network;
  private final PacketTransport transport;
  private final WorldSampler sampler;
  private final net.valory.anticheat.compatibility.ClientClassifier clients =
      new net.valory.anticheat.compatibility.ClientClassifier();

  public BukkitStateSampler(
      NetworkAdapter network, PacketTransport transport, WorldSampler sampler) {
    this.network = network;
    this.transport = transport;
    this.sampler = sampler;
  }

  public void sample(Player p, PlayerData d, long now, long tick, Settings settings) {
    UUID world = p.getWorld().getUID();
    if (d.world != null && !d.world.equals(world)) d.resetMotion(now);
    d.world = world;
    d.width = p.getWidth();
    d.height = p.getHeight();
    d.eyeHeight = p.getEyeHeight();
    d.ping = p.getPing();
    d.tps = Bukkit.getTPS()[0];
    d.flying = p.isFlying();
    d.allowFlight = p.getAllowFlight();
    d.gliding = p.isGliding();
    d.swimming = p.isSwimming();
    d.vehicle = p.isInsideVehicle();
    d.crawling = p.getHeight() < 1.0;
    d.usingItem = p.isHandRaised();
    var speed = attribute(p, "movement_speed");
    d.movementSpeed = speed == null ? .1 : speed.getValue();
    var reach = attribute(p, "entity_interaction_range");
    d.reach = reach == null ? 3 : reach.getValue();
    var blockReach = attribute(p, "block_interaction_range");
    d.blockReach = blockReach == null ? 4.5 : blockReach.getValue();
    var jump = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
    d.jumpBoost = jump == null ? 0 : jump.getAmplifier() + 1;
    boolean special =
        Math.abs(p.getWidth() - .6) > .01
            || p.isRiptiding()
            || p.hasPotionEffect(PotionEffectType.LEVITATION)
            || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
            || p.getGameMode() == GameMode.SPECTATOR
            || p.isDead();
    var gravity = attribute(p, "gravity");
    var step = attribute(p, "step_height");
    var jumpStrength = attribute(p, "jump_strength");
    special |=
        gravity != null && Math.abs(gravity.getValue() - .08) > .0001
            || step != null && step.getValue() > .6001
            || jumpStrength != null && jumpStrength.getValue() > .4201;
    if (special)
      d.exemptions.grant(ExemptionType.CUSTOM_MOVEMENT, "paper-physics", now, 1_000_000_000L);
    if (network.territoryFlight(d.uuid))
      d.exemptions.grant(ExemptionType.FLIGHT, "valory-territory", now, 500_000_000L);
    Location current = p.getLocation();
    if (d.environment == null
        || now - d.environment.nanos() >= 100_000_000L
        || !d.environment.world().equals(world)
        || d.environment
                .center()
                .subtract(new Vec3(current.getX(), current.getY(), current.getZ()))
                .length()
            > .25) d.environment = sampler.sample(p, now);
    d.serverGround = d.environment.ground();
    Location location = p.getLocation();
    var b = p.getBoundingBox();
    d.history.add(
        new HistoryFrame(
            now,
            tick,
            world,
            new Vec3(location.getX(), location.getY(), location.getZ()),
            location.getYaw(),
            location.getPitch(),
            new Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()),
            d.teleportEpoch));
    if (tick % 20 == 0 || d.compatibility.equals("UNKNOWN")) {
      transport.bind(d.uuid, p);
      d.bypass = p.hasPermission("valoryanticheat.bypass");
      d.bypassFamilies.clear();
      d.bypassChecks.clear();
      for (Family family : Family.values())
        if (p.hasPermission("valoryanticheat.bypass." + family.name().toLowerCase(Locale.ROOT)))
          d.bypassFamilies.add(family);
      for (String key : settings.checks.keySet()) {
        String check = key.substring(0, key.indexOf('.')).toLowerCase(Locale.ROOT);
        if (p.hasPermission("valoryanticheat.bypass." + check)) d.bypassChecks.add(check);
      }
      if (!d.compatibility.startsWith("API:")) {
        String version = transport.clientVersion(d.uuid);
        var identity = clients.identify(d.uuid, settings.conservativeBridge);
        d.javaChecks =
            identity == net.valory.anticheat.compatibility.ClientClassifier.Identity.JAVA
                && version.startsWith("1.21")
                && transport.nativeProtocol(d.uuid);
        d.compatibility =
            identity
                + ":"
                + version
                + (transport.nativeProtocol(d.uuid) ? "" : ":TRANSLATED_OR_UNKNOWN");
      }
    }
  }

  private static org.bukkit.attribute.AttributeInstance attribute(Player p, String name) {
    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
    if (type == null) type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
    if (type == null) type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("player." + name));
    return type == null ? null : p.getAttribute(type);
  }
}
