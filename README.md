# AdvancedMobAI

A Minecraft Paper plugin that improves the AI of several hostile mobs, giving them tactical
behaviors beyond vanilla AI.

Fork maintained by **ll33ee**, updated to run on Paper 26.1.2 / Java 25, based on the
original project by [**Diamondxv0**](https://github.com/Diamond-xv0/AdvancedMobAI). See
[CHANGELOG.md](CHANGELOG.md) for the full version history.

## Requirements

- Paper 26.1.2
- Java 25

## Features

### Creeper
- **Wall breaching**: breaks blocks obstructing its path to the target.
- **Proactive targeting**: actively searches for nearby players instead of waiting to be
  detected.
- **Pillar explosion**: detonates if it detects the player attacking it from a pillar/tower.
- Configurable friendly fire control between Creepers and other hostile mobs.

### Enderman
- **Dismantler**: breaks blocks obstructing its path to the target.

### Spider
- **Web-shot**: shoots a web projectile at range to trap the player, with a temporary speed
  boost on a successful hit.

### Witch
- **Supportive AI**: throws beneficial potions (Resistance, Strength, Invisibility, Speed) at
  nearby allied hostile mobs.

All of the above features are configurable in `config.yml` (detection ranges, cooldowns,
probabilities, block types, potion effects, etc.).

## Commands

- `/am reload` (alias: `/advancedmobai reload`) — reloads the configuration without
  restarting the server. Requires the `advancedmobai.reload` permission (op by default).

## Building

Requires Maven and JDK 25.

```
mvn clean package
```

The resulting `.jar` is placed in `target/`.
