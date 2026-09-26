package net.valory.anticheat.storage;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class SqliteEvidenceStore implements EvidenceStore {
  private final ThreadPoolExecutor io;
  private final Logger log;
  private final AtomicLong failed = new AtomicLong();
  private Connection connection;
  private volatile boolean ready;
  private int writes;

  public SqliteEvidenceStore(Path file, int retention, Logger log) {
    this.log = log;
    io =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2048),
            r -> {
              Thread t = new Thread(r, "VAC-SQLite");
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
    io.execute(
        () -> {
          try {
            Files.createDirectories(file.getParent());
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement s = connection.createStatement()) {
              s.execute("PRAGMA journal_mode=WAL");
              s.execute("PRAGMA busy_timeout=3000");
              s.execute(
                  "CREATE TABLE IF NOT EXISTS violations(id INTEGER PRIMARY KEY, uuid TEXT, name"
                      + " TEXT, server TEXT, at INTEGER, check_name TEXT, confidence REAL, weight"
                      + " REAL, risk REAL, vl REAL, detail TEXT, debug TEXT, context TEXT, replay"
                      + " TEXT)");
              s.execute(
                  "CREATE INDEX IF NOT EXISTS violation_player_at ON violations(uuid,at DESC)");
              s.execute(
                  "DELETE FROM violations WHERE at < "
                      + (System.currentTimeMillis() - retention * 86_400_000L));
            }
            ready = true;
          } catch (Exception e) {
            failure(e);
          }
        });
  }

  public boolean append(EvidenceRecord e) {
    if (!ready) return false;
    try {
      io.execute(
          () -> {
            try (PreparedStatement s =
                connection.prepareStatement(
                    "INSERT INTO"
                        + " violations(uuid,name,server,at,check_name,confidence,weight,risk,vl,detail,debug,context,replay)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
              var r = e.result();
              s.setString(1, e.player().toString());
              s.setString(2, e.name());
              s.setString(3, e.server());
              s.setLong(4, r.timestamp());
              s.setString(5, r.key());
              s.setDouble(6, r.confidence());
              s.setDouble(7, r.weight());
              s.setDouble(8, e.risk());
              s.setDouble(9, e.vl());
              s.setString(10, r.description());
              s.setString(11, r.debugData().toString());
              s.setString(12, e.context());
              s.setString(13, e.replay());
              s.executeUpdate();
              if (++writes % 1000 == 0)
                try (Statement cleanup = connection.createStatement()) {
                  cleanup.executeUpdate(
                      "DELETE FROM violations WHERE id < (SELECT MAX(id)-1000000 FROM violations)");
                }
            } catch (SQLException error) {
              failure(error);
            }
          });
      return true;
    } catch (RejectedExecutionException full) {
      failed.incrementAndGet();
      return false;
    }
  }

  public CompletableFuture<List<String>> recent(UUID player, int limit) {
    CompletableFuture<List<String>> result = new CompletableFuture<>();
    if (!ready) {
      result.completeExceptionally(new IllegalStateException("Armazenamento indisponível"));
      return result;
    }
    try {
      io.execute(
          () -> {
            try (PreparedStatement s =
                connection.prepareStatement(
                    "SELECT at,check_name,vl,debug,replay FROM violations WHERE uuid=? ORDER BY at"
                        + " DESC LIMIT ?")) {
              s.setString(1, player.toString());
              s.setInt(2, Math.max(1, Math.min(50, limit)));
              List<String> out = new ArrayList<>();
              try (ResultSet r = s.executeQuery()) {
                while (r.next())
                  out.add(
                      r.getLong(1)
                          + " "
                          + r.getString(2)
                          + " VL="
                          + r.getDouble(3)
                          + " "
                          + r.getString(4)
                          + " replay="
                          + r.getString(5));
              }
              result.complete(List.copyOf(out));
            } catch (Exception e) {
              result.completeExceptionally(e);
            }
          });
    } catch (RejectedExecutionException e) {
      result.completeExceptionally(e);
    }
    return result;
  }

  private void failure(Exception e) {
    long count = failed.incrementAndGet();
    if (count == 1 || count % 100 == 0) log.severe("VAC persistence failure (" + count + "): " + e);
  }

  public int queued() {
    return io.getQueue().size();
  }

  public long failures() {
    return failed.get();
  }

  public boolean ready() {
    return ready;
  }

  public void close() {
    ready = false;
    io.shutdown();
    try {
      if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warning("VAC I/O did not drain; remaining=" + io.getQueue().size());
        io.shutdownNow();
        return;
      }
      if (connection != null) connection.close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      io.shutdownNow();
    } catch (SQLException e) {
      failure(e);
    }
  }
}
