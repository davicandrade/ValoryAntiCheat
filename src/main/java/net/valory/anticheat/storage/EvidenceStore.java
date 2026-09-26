package net.valory.anticheat.storage;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface EvidenceStore extends AutoCloseable {
  boolean append(EvidenceRecord record);

  CompletableFuture<List<String>> recent(UUID player, int limit);

  int queued();

  long failures();

  boolean ready();

  void close();
}
