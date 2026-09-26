package net.valory.anticheat.packet;

import static net.valory.anticheat.packet.PacketFrame.Kind.*;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import java.util.*;
import java.util.concurrent.*;

/** The ONLY PacketEvents-dependent implementation. Netty callbacks never use Bukkit. */
public final class PacketEventsTransport implements PacketTransport {
  private final ConcurrentMap<UUID, PacketInbox> inboxes;
  private final ConcurrentMap<UUID, User> users = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, FloodGuard> rates = new ConcurrentHashMap<>();
  private PacketListenerAbstract listener;

  public PacketEventsTransport(ConcurrentMap<UUID, PacketInbox> inboxes) {
    this.inboxes = inboxes;
  }

  public void bind(UUID id, Object player) {
    User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
    if (user != null) users.put(id, user);
  }

  public void start() {
    listener =
        new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
          @Override
          public void onPacketReceive(PacketReceiveEvent e) {
            if (e.isCancelled()
                || e.getConnectionState() != ConnectionState.PLAY
                || e.getUser().getUUID() == null) return;
            UUID id = e.getUser().getUUID();
            PacketInbox inbox = inboxes.get(id);
            if (inbox == null || users.get(id) != e.getUser()) return;
            long now = System.nanoTime();
            String name = e.getPacketType().getName();
            FloodGuard guard = rates.computeIfAbsent(id, ignored -> new FloodGuard());
            if (!guard.allow(now)) {
              e.setCancelled(true);
              inbox.desynchronized.set(true);
              inbox.dropped.increment();
              inbox.floodDropped.increment();
              inbox.lastFloodPacket = name;
              return;
            }
            try {
              if (WrapperPlayClientPlayerFlying.isFlying(e.getPacketType())) {
                var w = new WrapperPlayClientPlayerFlying(e);
                var l = w.getLocation();
                if (w.hasPositionChanged()
                        && (!Double.isFinite(l.getX())
                            || !Double.isFinite(l.getY())
                            || !Double.isFinite(l.getZ())
                            || Math.abs(l.getX()) > 30_000_000
                            || Math.abs(l.getZ()) > 30_000_000
                            || Math.abs(l.getY()) > 30_000_000)
                    || w.hasRotationChanged()
                        && (!Float.isFinite(l.getYaw()) || !Float.isFinite(l.getPitch()))) {
                  e.setCancelled(true);
                  inbox.offer(PacketFrame.simple(INVALID, now, 0, name));
                  return;
                }
                inbox.offer(
                    new PacketFrame(
                        MOVE,
                        now,
                        l.getX(),
                        l.getY(),
                        l.getZ(),
                        l.getYaw(),
                        l.getPitch(),
                        0,
                        (w.hasPositionChanged() ? 1 : 0)
                            | (w.hasRotationChanged() ? 2 : 0)
                            | (w.isOnGround() ? 4 : 0),
                        name));
                return;
              }
              PacketFrame frame =
                  switch (name) {
                    case "INTERACT_ENTITY" -> {
                      var w = new WrapperPlayClientInteractEntity(e);
                      yield w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
                          ? PacketFrame.simple(ATTACK, now, w.getEntityId(), name)
                          : null;
                    }
                    case "PONG" ->
                        PacketFrame.simple(PONG, now, new WrapperPlayClientPong(e).getId(), name);
                    case "TELEPORT_CONFIRM" ->
                        PacketFrame.simple(
                            TELEPORT_ACK,
                            now,
                            new WrapperPlayClientTeleportConfirm(e).getTeleportId(),
                            name);
                    case "PLAYER_DIGGING" -> {
                      var w = new WrapperPlayClientPlayerDigging(e);
                      var b = w.getBlockPosition();
                      yield new PacketFrame(
                          DIG,
                          now,
                          b.x,
                          b.y,
                          b.z,
                          0,
                          0,
                          w.getSequence(),
                          w.getBlockFaceId(),
                          w.getAction().name());
                    }
                    case "PLAYER_BLOCK_PLACEMENT" -> {
                      var w = new WrapperPlayClientPlayerBlockPlacement(e);
                      var b = w.getBlockPosition();
                      var hit = w.getCursorPosition();
                      yield new PacketFrame(
                          PLACE,
                          now,
                          b.x,
                          b.y,
                          b.z,
                          hit.x,
                          hit.y,
                          w.getSequence(),
                          w.getFaceId(),
                          "hitZ=" + hit.z);
                    }
                    case "ENTITY_ACTION" ->
                        PacketFrame.simple(
                            ACTION,
                            now,
                            0,
                            new WrapperPlayClientEntityAction(e).getAction().name());
                    case "ANIMATION" -> PacketFrame.simple(SWING, now, 0, name);
                    case "USE_ITEM" -> PacketFrame.simple(USE, now, 0, name);
                    case "CLICK_WINDOW",
                            "CLOSE_WINDOW",
                            "HELD_ITEM_CHANGE",
                            "CREATIVE_INVENTORY_ACTION" ->
                        PacketFrame.simple(INVENTORY, now, 0, name);
                    case "PLAYER_ABILITIES" -> PacketFrame.simple(ABILITIES, now, 0, name);
                    case "VEHICLE_MOVE", "STEER_VEHICLE" ->
                        PacketFrame.simple(VEHICLE, now, 0, name);
                    case "KEEP_ALIVE" -> PacketFrame.simple(KEEPALIVE, now, 0, name);
                    case "CLIENT_SETTINGS" -> PacketFrame.simple(SETTINGS, now, 0, name);
                    default -> null;
                  };
              if (frame != null) inbox.offer(frame);
            } catch (RuntimeException malformed) {
              e.setCancelled(true);
              inbox.offer(
                  PacketFrame.simple(
                      INVALID, now, 0, name + ":" + malformed.getClass().getSimpleName()));
            }
          }

          @Override
          public void onPacketSend(PacketSendEvent e) {
            if (e.isCancelled()
                || e.getConnectionState() != ConnectionState.PLAY
                || e.getUser().getUUID() == null) return;
            PacketInbox inbox = inboxes.get(e.getUser().getUUID());
            if (inbox == null || users.get(e.getUser().getUUID()) != e.getUser()) return;
            long now = System.nanoTime();
            String name = e.getPacketType().getName();
            try {
              PacketFrame frame =
                  switch (name) {
                    case "PING" ->
                        PacketFrame.simple(
                            PING_OUT, now, new WrapperPlayServerPing(e).getId(), name);
                    case "PLAYER_POSITION_AND_LOOK" ->
                        PacketFrame.simple(
                            TELEPORT_OUT,
                            now,
                            new WrapperPlayServerPlayerPositionAndLook(e).getTeleportId(),
                            name);
                    case "ENTITY_VELOCITY" -> {
                      var w = new WrapperPlayServerEntityVelocity(e);
                      var v = w.getVelocity();
                      yield w.getEntityId() == e.getUser().getEntityId()
                          ? new PacketFrame(
                              VELOCITY, now, v.x, v.y, v.z, 0, 0, w.getEntityId(), 0, name)
                          : null;
                    }
                    case "EXPLOSION" -> PacketFrame.simple(EXPLOSION, now, 0, name);
                    case "RESPAWN" -> PacketFrame.simple(WORLD_CHANGE, now, 0, name);
                    case "BLOCK_CHANGE", "MULTI_BLOCK_CHANGE" ->
                        PacketFrame.simple(BLOCK_CHANGE, now, 0, name);
                    case "KEEP_ALIVE" -> PacketFrame.simple(KEEPALIVE_OUT, now, 0, name);
                    default -> null;
                  };
              if (frame != null) inbox.offer(frame);
            } catch (RuntimeException decodeFailure) {
              inbox.desynchronized.set(true);
            }
          }
        };
    PacketEvents.getAPI().getEventManager().registerListener(listener);
  }

  public void probe(UUID id, int token) {
    User user = users.get(id);
    if (user != null) user.sendPacket(new WrapperPlayServerPing(token));
  }

  public String clientVersion(UUID id) {
    User user = users.get(id);
    return user == null ? "UNKNOWN" : user.getClientVersion().getReleaseName();
  }

  public boolean nativeProtocol(UUID id) {
    User user = users.get(id);
    return user != null
        && user.getClientVersion().getProtocolVersion()
            == PacketEvents.getAPI()
                .getServerManager()
                .getVersion()
                .toClientVersion()
                .getProtocolVersion();
  }

  public void remove(UUID id) {
    users.remove(id);
    rates.remove(id);
  }

  public void close() {
    if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    users.clear();
    rates.clear();
  }

  private static final class FloodGuard {
    private long previous;
    private double tokens = 4000;

    boolean allow(long now) {
      if (previous != 0) tokens = Math.min(4000, tokens + Math.max(0, now - previous) * .000002);
      previous = now;
      if (tokens < 1) return false;
      tokens--;
      return true;
    }
  }
}
