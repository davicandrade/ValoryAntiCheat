package net.valory.anticheat.state;

import java.util.*;

/** Single-owner fixed-capacity ring. Offset zero is newest. */
public final class Ring<T> {
  private final Object[] entries;
  private int next, size;

  public Ring(int capacity) {
    if (capacity < 1) throw new IllegalArgumentException();
    entries = new Object[capacity];
  }

  public void add(T item) {
    entries[next] = Objects.requireNonNull(item);
    next = (next + 1) % entries.length;
    size = Math.min(size + 1, entries.length);
  }

  @SuppressWarnings("unchecked")
  public T newest(int offset) {
    if (offset < 0 || offset >= size) throw new IndexOutOfBoundsException();
    return (T) entries[Math.floorMod(next - 1 - offset, entries.length)];
  }

  public int size() {
    return size;
  }

  public int capacity() {
    return entries.length;
  }

  public void clear() {
    Arrays.fill(entries, null);
    next = size = 0;
  }

  public List<T> snapshot() {
    List<T> out = new ArrayList<>(size);
    for (int i = 0; i < size; i++) out.add(newest(i));
    return List.copyOf(out);
  }
}
