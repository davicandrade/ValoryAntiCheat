package net.valory.anticheat.packet;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.*;

/** Bounded MPSC queue: independent outbound and inbound Netty producers, main-thread consumer. */
public final class PacketInbox {
  private final ArrayBlockingQueue<PacketFrame> queue;
  public final LongAdder received = new LongAdder(), dropped = new LongAdder();
  public final LongAdder floodDropped = new LongAdder();
  public volatile String lastFloodPacket = "";
  public final AtomicBoolean desynchronized = new AtomicBoolean();
  public volatile boolean active = true;

  public PacketInbox(int capacity) {
    queue = new ArrayBlockingQueue<>(capacity);
  }

  public void offer(PacketFrame frame) {
    if (!active) return;
    received.increment();
    if (!queue.offer(frame)) {
      dropped.increment();
      desynchronized.set(true);
    }
  }

  public PacketFrame poll() {
    return queue.poll();
  }

  public int size() {
    return queue.size();
  }

  public void clear() {
    queue.clear();
  }

  public void close() {
    active = false;
    queue.clear();
  }
}
