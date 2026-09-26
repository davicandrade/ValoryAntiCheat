package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.valory.anticheat.config.Settings;
import net.valory.anticheat.integration.NoNetworkAdapter;
import net.valory.anticheat.packet.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class LifecycleTest {
  ServerMock server;
  JavaPlugin plugin;
  VacEngine engine;
  FakeTransport transport;

  @BeforeEach
  void setup() throws Exception {
    server =
        MockBukkit.mock(
            new ServerMock() {
              @Override
              public double[] getTPS() {
                return new double[] {20, 20, 20};
              }
            });
    plugin = MockBukkit.createMockPlugin();
    Files.createDirectories(plugin.getDataFolder().toPath());
    for (String file :
        List.of(
            "config.yml", "checks.yml", "messages.yml", "punishments.yml", "compatibility.yml")) {
      try (var stream = getClass().getClassLoader().getResourceAsStream(file)) {
        Files.copy(
            Objects.requireNonNull(stream),
            plugin.getDataFolder().toPath().resolve(file),
            StandardCopyOption.REPLACE_EXISTING);
      }
    }
    engine =
        new VacEngine(
            plugin,
            new Settings(plugin),
            new NoNetworkAdapter(),
            inboxes -> {
              transport = new FakeTransport(inboxes);
              return transport;
            });
  }

  @AfterEach
  void cleanup() {
    if (engine != null) engine.close();
    MockBukkit.unmock();
  }

  @Test
  void aggregateLifecycleReloadAndShutdown() {
    engine.start();
    assertTrue(transport.started);
    server.getScheduler().performTicks(3);
    assertEquals(3, engine.tickNumber());
    engine.reload(new Settings(plugin));
    server.getScheduler().performTicks(2);
    assertEquals(5, engine.tickNumber());
    engine.close();
    assertTrue(transport.closed);
    server.getScheduler().performTicks(2);
    assertEquals(5, engine.tickNumber());
    engine = null;
  }

  @Test
  void playerLifecycleCleansNetworkState() {
    var player =
        new org.mockbukkit.mockbukkit.entity.PlayerMock(server, "TestPlayer") {
          @Override
          public boolean isHandRaised() {
            return false;
          }
        };
    server.addPlayer(player);
    engine.start();
    assertEquals(1, engine.players().size());
    assertTrue(transport.inboxes.containsKey(player.getUniqueId()));
    PacketInbox inbox = transport.inboxes.get(player.getUniqueId());
    engine.quit(player.getUniqueId());
    assertEquals(0, engine.players().size());
    assertTrue(transport.inboxes.isEmpty());
    assertFalse(inbox.active);
    engine.join(player);
    assertNotSame(inbox, transport.inboxes.get(player.getUniqueId()));
  }

  @Test
  void serviceRejectsOffMainMutations() throws Exception {
    try (ExecutorService thread = Executors.newSingleThreadExecutor()) {
      var future = thread.submit(() -> engine.risk(UUID.randomUUID()));
      ExecutionException error = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(IllegalStateException.class, error.getCause());
    }
  }

  static final class FakeTransport implements PacketTransport {
    final ConcurrentMap<UUID, PacketInbox> inboxes;
    boolean started, closed;

    FakeTransport(ConcurrentMap<UUID, PacketInbox> inboxes) {
      this.inboxes = inboxes;
    }

    public void bind(UUID id, Object player) {}

    public void start() {
      started = true;
    }

    public void probe(UUID id, int token) {}

    public String clientVersion(UUID id) {
      return "1.21.8";
    }

    public boolean nativeProtocol(UUID id) {
      return true;
    }

    public void remove(UUID id) {}

    public void close() {
      closed = true;
    }
  }
}
