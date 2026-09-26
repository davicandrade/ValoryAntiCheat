package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import net.valory.anticheat.api.*;
import net.valory.anticheat.packet.*;
import net.valory.anticheat.processor.*;
import net.valory.anticheat.state.*;
import org.junit.jupiter.api.Test;

class StateAndTimingTest {
  @Test
  void ringWrapOrderAndCleanup() {
    Ring<Integer> ring = new Ring<>(3);
    for (int i = 0; i < 10; i++) ring.add(i);
    assertEquals(List.of(9, 8, 7), ring.snapshot());
    assertThrows(IndexOutOfBoundsException.class, () -> ring.newest(3));
    ring.clear();
    assertEquals(0, ring.size());
  }

  @Test
  void flightExemptsMovementOnly() {
    Exemptions e = new Exemptions();
    e.grant(ExemptionType.FLIGHT, "territory", 1, 100);
    assertTrue(e.applies(Family.MOVEMENT, 100));
    assertFalse(e.applies(Family.COMBAT, 100));
    assertFalse(e.applies(Family.PACKET, 100));
    assertFalse(e.applies(Family.MOVEMENT, 101));
  }

  @Test
  void distinctSourceCannotRevokeAnotherLease() {
    Exemptions e = new Exemptions();
    e.grant(ExemptionType.FLIGHT, "territory", 1, 100);
    e.grant(ExemptionType.FLIGHT, "minigame", 1, 200);
    e.remove(ExemptionType.FLIGHT, "territory");
    assertTrue(e.applies(Family.MOVEMENT, 150));
  }

  @Test
  void unknownClientsStillHaveProtocolChecks() {
    PlayerData d = new PlayerData(UUID.randomUUID(), "Bedrock", 1);
    assertTrue(d.exempt(Family.COMBAT, 10));
    assertTrue(d.exempt(Family.MOVEMENT, 10));
    assertFalse(d.exempt(Family.PACKET, 10));
  }

  @Test
  void latencyRejectsWrongDuplicateAndStaleAcknowledgements() {
    LatencyTracker l = new LatencyTracker();
    l.sent(123, 100_000_000);
    assertFalse(l.acknowledge(321, 140_000_000));
    assertTrue(l.acknowledge(123, 150_000_000));
    assertEquals(50, l.rttMillis());
    assertFalse(l.acknowledge(123, 160_000_000));
    l.sent(9, 200_000_000);
    assertFalse(l.acknowledge(9, 20_000_000_000L));
  }

  @Test
  void replayRewindUsesMonotonicRttAndInterpolation() {
    LatencyTracker l = new LatencyTracker();
    l.sent(1, 1_000_000_000);
    l.acknowledge(1, 1_100_000_000);
    assertEquals(100_000_000, l.rewindNanos());
  }

  @Test
  void normalClockAndJitterBurstsDoNotFlag() {
    MovementClock c = new MovementClock();
    long t = 1_000_000_000;
    for (int i = 0; i < 2000; i++) {
      t += i % 20 == 0 ? 1_000_000_000 : 0;
      c.accept(t);
      assertFalse(c.sustained());
    }
  }

  @Test
  void sustainedTimerFlagsButIdleCannotBankUnlimitedCredit() {
    MovementClock c = new MovementClock();
    long t = 1_000_000_000;
    c.accept(t);
    t += 60_000_000_000L;
    c.accept(t);
    for (int i = 0; i < 500; i++) {
      t += 25_000_000;
      c.accept(t);
    }
    assertTrue(c.sustained());
    c.reset();
    assertFalse(c.sustained());
  }

  @Test
  void queueSaturationMarksUncertaintyAndBoundsMemory() {
    PacketInbox q = new PacketInbox(8);
    for (int i = 0; i < 100; i++) q.offer(PacketFrame.simple(PacketFrame.Kind.MOVE, i, 0, "test"));
    assertEquals(8, q.size());
    assertEquals(92, q.dropped.sum());
    assertTrue(q.desynchronized.get());
    q.close();
    q.offer(PacketFrame.simple(PacketFrame.Kind.MOVE, 1, 0, "test"));
    assertEquals(0, q.size());
  }

  @Test
  void independentNetworkProducersAreSafe() throws Exception {
    PacketInbox q = new PacketInbox(4096);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> a =
          pool.submit(
              () -> {
                for (int i = 0; i < 1000; i++)
                  q.offer(PacketFrame.simple(PacketFrame.Kind.MOVE, i, i, "a"));
              });
      Future<?> b =
          pool.submit(
              () -> {
                for (int i = 0; i < 1000; i++)
                  q.offer(PacketFrame.simple(PacketFrame.Kind.PONG, i, i, "b"));
              });
      a.get();
      b.get();
      Set<String> seen = new HashSet<>();
      PacketFrame f;
      while ((f = q.poll()) != null) seen.add(f.detail() + f.id());
      assertEquals(2000, seen.size());
      assertEquals(0, q.dropped.sum());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void teleportClearsMotionAndSafeHistory() {
    PlayerData d = new PlayerData(UUID.randomUUID(), "Test", 1);
    d.hasPosition = true;
    d.stableMoves = 100;
    d.resetMotion(1_000_000_000);
    assertFalse(d.hasPosition);
    assertNull(d.safe);
    assertEquals(0, d.stableMoves);
    assertEquals(3_000_000_000L, d.quarantineUntil);
  }
}
