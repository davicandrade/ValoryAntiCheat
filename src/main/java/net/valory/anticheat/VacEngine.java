package net.valory.anticheat;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class VacEngine implements AntiCheatService, CheckContext, AutoCloseable {
  private final org.bukkit.plugin.java.JavaPlugin plugin;
  private Settings settings;
  private final Map<UUID, PlayerData> players = new LinkedHashMap<>();
  private final Map<Integer, PlayerData> entities = new HashMap<>();
  private final ConcurrentMap<UUID, PacketInbox> inboxes = new ConcurrentHashMap<>();
  private final Check[] checks = {
    new ProtocolChecks(),
    new TimingChecks(),
    new MovementChecks(),
    new CombatChecks(),
    new ActionChecks(),
    new BlockActionChecks(),
    new AimPatternCheck()
  };
  private final WorldSampler sampler = new WorldSampler();
  private final SetbackEngine setbacks = new SetbackEngine(sampler);
  private final SecureRandom random = new SecureRandom();
  private final Map<UUID, UUID> debugSubscriptions = new HashMap<>();
  private final Map<UUID, String> debugFilters = new HashMap<>();
  private final ArrayBlockingQueue<Runnable> completions = new ArrayBlockingQueue<>(256);
  public final NetworkAdapter network;
  public final EvidenceStore store;
  public final Performance metrics = new Performance();
  private final net.valory.anticheat.processor.PacketProcessor processor;
  private final BukkitStateSampler stateSampler;
  private final net.valory.anticheat.evidence.ViolationPipeline pipeline;
  private final PacketTransport transport;
  private BukkitTask task;
  private long tick, lastTick;
  private int cursor;
  private volatile boolean closed;

  public VacEngine(
      org.bukkit.plugin.java.JavaPlugin plugin, Settings settings, NetworkAdapter network) {
    this(plugin, settings, network, PacketEventsTransport::new);
  }

  public VacEngine(
      org.bukkit.plugin.java.JavaPlugin plugin,
      Settings settings,
      NetworkAdapter network,
      java.util.function.Function<ConcurrentMap<UUID, PacketInbox>, PacketTransport> factory) {
    this.plugin = plugin;
    this.settings = settings;
    this.network = network;
    store =
        new SqliteEvidenceStore(
            plugin.getDataFolder().toPath().resolve("evidence.db"),
            settings.retentionDays,
            plugin.getLogger());
    transport = factory.apply(inboxes);
    stateSampler = new BukkitStateSampler(network, transport, sampler);
    processor = new net.valory.anticheat.processor.PacketProcessor(checks, this, metrics);
    pipeline =
        new net.valory.anticheat.evidence.ViolationPipeline(
            plugin, settings, network, store, setbacks, () -> tick, this::completeOnMain);
  }

  public void start() {
    transport.start();
    for (Player p : Bukkit.getOnlinePlayers()) join(p);
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
  }

  public void join(Player p) {
    requireMain();
    quit(p.getUniqueId());
    PlayerData d = new PlayerData(p.getUniqueId(), p.getName(), p.getEntityId());
    d.resetMotion(System.nanoTime());
    players.put(d.uuid, d);
    entities.put(d.entityId, d);
    transport.bind(d.uuid, p);
    inboxes.put(d.uuid, d.inbox);
    stateSampler.sample(p, d, System.nanoTime(), tick, settings);
  }

  public void quit(UUID id) {
    requireMain();
    PacketInbox inbox = inboxes.remove(id);
    if (inbox != null) inbox.close();
    PlayerData d = players.remove(id);
    if (d != null) entities.remove(d.entityId);
    transport.remove(id);
    pipeline.remove(id);
    debugSubscriptions.remove(id);
    debugFilters.remove(id);
    debugSubscriptions.values().removeIf(id::equals);
  }

  private void tick() {
    long start = System.nanoTime();
    tick++;
    boolean lag = lastTick != 0 && start - lastTick > 100_000_000L;
    lastTick = start;
    Runnable completion;
    int completed = 0;
    while (completed++ < 64 && (completion = completions.poll()) != null) completion.run();
    List<PlayerData> active = new ArrayList<>(players.values());
    int size = active.size();
    long deadline = start + (long) (settings.tickBudgetMs * 1e6);
    for (int i = 0; i < size; i++) {
      PlayerData d = active.get((cursor + i) % size);
      Player p = Bukkit.getPlayer(d.uuid);
      if (p == null) continue;
      if (lag) {
        d.quarantineUntil = start + 2_000_000_000L;
        d.clock.reset();
        d.streaks.clear();
      }
      long flood = d.inbox.floodDropped.sum();
      if (flood - d.reportedFloodDrops >= 100) {
        flag(
            d,
            "BadPackets",
            "B",
            Family.PACKET,
            "packet-flood",
            .95,
            3,
            "Extreme inbound packet flood throttled",
            Map.of(
                "packet",
                d.inbox.lastFloodPacket,
                "rejected",
                "" + (flood - d.reportedFloodDrops),
                "limit",
                "2000/s; burst=4000",
                "interval",
                "since previous aggregate report"),
            start);
        d.reportedFloodDrops = flood;
      }
      if (d.inbox.desynchronized.getAndSet(false)) {
        d.inbox.clear();
        d.resetMotion(start);
        d.lastDebug = "Packet loss/backpressure: checks suspended";
      }
      int count = 0;
      PacketFrame frame;
      while (count++ < settings.budget
          && System.nanoTime() < deadline
          && (frame = d.inbox.poll()) != null) {
        if (start - frame.nanos() > 250_000_000L) {
          d.resetMotion(start);
          continue;
        }
        processor.process(d, frame);
        metrics.packet();
      }
      if (d.inbox.size() > 128) {
        d.inbox.clear();
        d.resetMotion(start);
        metrics.overBudget++;
      }
      stateSampler.sample(p, d, System.nanoTime(), tick, settings);
      d.actionsThisTick = 0;
      d.exemptions.expire(start);
      if (tick % 20 == Math.floorMod(d.entityId, 20) && !d.latency.pending(start)) {
        int token = random.nextInt();
        d.latency.sent(token, start);
        transport.probe(d.uuid, token);
      }
    }
    if (size > 0) cursor = (cursor + 1) % size;
    if (tick % 20 == 0) debugTick();
    metrics.tick(System.nanoTime() - start);
  }

  public void result(PlayerData data, CheckResult result) {
    pipeline.accept(data, result);
  }

  public boolean enabled(String key) {
    Settings.Policy policy = settings.checks.get(key);
    return policy != null && policy.enabled();
  }

  public PlayerData entity(int id) {
    return entities.get(id);
  }

  public PlayerData player(UUID id) {
    return players.get(id);
  }

  public Collection<PlayerData> players() {
    return List.copyOf(players.values());
  }

  public Settings settings() {
    return settings;
  }

  public long tickNumber() {
    return tick;
  }

  public boolean alerts(UUID staff) {
    requireMain();
    return pipeline.alerts(staff);
  }

  public void stopDebug(UUID staff) {
    requireMain();
    debugSubscriptions.remove(staff);
    debugFilters.remove(staff);
  }

  public void debug(UUID staff, UUID subject, String filter) {
    requireMain();
    debugSubscriptions.put(staff, subject);
    debugFilters.put(staff, filter.toLowerCase(Locale.ROOT));
  }

  private void debugTick() {
    for (var entry : debugSubscriptions.entrySet()) {
      Player staff = Bukkit.getPlayer(entry.getKey());
      PlayerData d = players.get(entry.getValue());
      if (staff == null || d == null || !staff.hasPermission("valoryanticheat.debug")) continue;
      String filter = debugFilters.getOrDefault(entry.getKey(), "");
      String values =
          "horizontalLimit="
              + d.predictedHorizontal
              + " verticalRange=["
              + d.predictedMinY
              + ","
              + d.predictedMaxY
              + "]"
              + " lastViolation="
              + d.lastDebug;
      if (!filter.isBlank())
        for (CheckResult result : d.violations.snapshot())
          if (result.check().equalsIgnoreCase(filter) || result.key().equalsIgnoreCase(filter)) {
            values = result.debugData().toString();
            break;
          }
      staff.sendMessage(
          Component.text(
              "[VAC debug] "
                  + d.name
                  + " "
                  + values
                  + " env="
                  + d.environment.reason()
                  + " exemptions="
                  + d.exemptions.describe(System.nanoTime()),
              NamedTextColor.GRAY));
    }
  }

  public void reload(Settings next) {
    requireMain();
    if (next.retentionDays != settings.retentionDays)
      throw new IllegalArgumentException("retention-days requires restart");
    settings = next;
    pipeline.reload(next);
    long now = System.nanoTime();
    for (PlayerData d : players.values()) d.resetMotion(now);
  }

  public double risk(UUID id) {
    requireMain();
    PlayerData d = players.get(id);
    return d == null ? 0 : d.risk.score(System.nanoTime());
  }

  public List<CheckResult> violations(UUID id) {
    requireMain();
    PlayerData d = players.get(id);
    return d == null ? List.of() : d.violations.snapshot();
  }

  public void exempt(UUID id, ExemptionType type, Duration duration, String source) {
    requireMain();
    PlayerData d = players.get(id);
    if (d != null) d.exemptions.grant(type, source, System.nanoTime(), duration.toNanos());
  }

  public void removeExemption(UUID id, ExemptionType type, String source) {
    requireMain();
    PlayerData d = players.get(id);
    if (d != null) d.exemptions.remove(type, source);
  }

  public void customVelocity(UUID id, Vec3 velocity, String source) {
    requireMain();
    if (!velocity.finite()) throw new IllegalArgumentException();
    PlayerData d = players.get(id);
    if (d != null) {
      d.velocity = velocity;
      d.lastVelocity = System.nanoTime();
      exempt(id, ExemptionType.VELOCITY, Duration.ofSeconds(3), source);
    }
  }

  public void teleport(UUID id, String source) {
    requireMain();
    PlayerData d = players.get(id);
    if (d != null) {
      d.resetMotion(System.nanoTime());
      exempt(id, ExemptionType.TELEPORT, Duration.ofSeconds(2), source);
    }
  }

  public boolean investigating(UUID id) {
    return risk(id) >= 35;
  }

  public void clientProfile(UUID id, boolean javaCompatible, String source) {
    requireMain();
    PlayerData d = players.get(id);
    if (d != null) {
      d.javaChecks = javaCompatible;
      d.compatibility = "API:" + source;
      d.resetMotion(System.nanoTime());
    }
  }

  public void completeOnMain(Runnable r) {
    if (!closed && !completions.offer(r))
      plugin.getLogger().warning("VAC completion queue full; result was not delivered to the UI");
  }

  public void close() {
    requireMain();
    closed = true;
    if (task != null) task.cancel();
    transport.close();
    for (PacketInbox inbox : inboxes.values()) inbox.close();
    inboxes.clear();
    players.clear();
    entities.clear();
    pipeline.close();
    debugSubscriptions.clear();
    debugFilters.clear();
    completions.clear();
    store.close();
  }

  private static void requireMain() {
    if (!Bukkit.isPrimaryThread())
      throw new IllegalStateException("AntiCheatService requires Bukkit primary thread");
  }
}
