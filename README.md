# ValoryAntiCheat

Packet-aware anti-cheat for Paper 1.21.8, designed around measured violations, staff review and evidence before enforcement.

## Detection coverage

- Movement: speed, fly, air-jump, step, phase, timer, invalid move, no-fall and spider.
- Combat: reach, hitbox, autoclicker, multi-aura and aim-pattern analysis.
- Block/action: impossible place/break, scaffold and fast-place.
- Protocol: bad and malformed packet handling.

VAC processes packet input through bounded queues, samples Bukkit state on the server thread, applies exemptions for legitimate mechanics, aggregates risk and records evidence in SQLite.

## Dependencies

- **Required:** Paper 1.21.8 and PacketEvents.
- **Optional:** ValoryPunish, TAB, LuckPerms, ViaVersion/ViaBackwards, Floodgate and Geyser.

ValoryPunish integration is optional and loaded reflectively; VAC starts normally without it.

## Commands

`/vac alerts`, `/vac profile`, `/vac violations`, `/vac logs`, `/vac debug`, `/vac status`, `/vac checks`, `/vac performance`, `/vac reload`.

## Build

Requires JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

The release artifact is `target/ValoryAntiCheat-1.0.0.jar`.

## Safe rollout

Automatic penalties are intentionally disabled by default. Run the plugin in observation mode, validate alerts and evidence against real traffic, and calibrate `checks.yml` for the server's mechanics before enabling setbacks or configured automatic enforcement in `punishments.yml`.

## Verification

The Maven suite executes 47 unit and simulation tests for checks, geometry, movement prediction, risk, configuration, lifecycle and SQLite evidence storage.
