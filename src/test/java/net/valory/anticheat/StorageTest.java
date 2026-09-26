package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import net.valory.anticheat.api.*;
import net.valory.anticheat.check.*;
import net.valory.anticheat.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
  @TempDir Path temp;

  @Test
  void asyncEvidenceSurvivesRestart() throws Exception {
    UUID id = UUID.randomUUID();
    Path file = temp.resolve("evidence.db");
    SqliteEvidenceStore store = new SqliteEvidenceStore(file, 30, Logger.getAnonymousLogger());
    awaitReady(store);
    CheckResult result =
        new CheckResult(
            "Reach",
            "A",
            Family.COMBAT,
            "geometry",
            .8,
            2,
            "measured",
            Map.of("distance", "3.8"),
            System.currentTimeMillis(),
            1);
    assertTrue(
        store.append(
            new EvidenceRecord(
                id, "Player", "server", result, 10, 6, "world=world velocity=0", "VAL-TEST123")));
    assertEquals(1, store.recent(id, 10).get().size());
    store.close();
    SqliteEvidenceStore reopened = new SqliteEvidenceStore(file, 30, Logger.getAnonymousLogger());
    awaitReady(reopened);
    assertTrue(reopened.recent(id, 10).get().getFirst().contains("distance=3.8"));
    reopened.close();
  }

  void awaitReady(SqliteEvidenceStore store) throws Exception {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (!store.ready() && System.nanoTime() < deadline) Thread.sleep(10);
    assertTrue(store.ready());
  }
}
