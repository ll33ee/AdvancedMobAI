# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.2.1] - 2026-07-05

Fork/port maintained by **ll33ee**, based on [Diamondxv0's](https://github.com/Diamond-xv0/AdvancedMobAI) original 1.2 Beta.

### Added
- Maven build (`pom.xml`) — the project previously had no build system at all.
- `.gitignore` for build output and IDE files.
- Hidden config option `ignore_invulnerable_players`:
  Creeper/Enderman/Spider AI ignore players with Bukkit's `Player#isInvulnerable()` flag set
  (e.g. via Skript's `make player invulnerable`, or vanilla `/data`), independent of gamemode.

### Changed
- `api-version` bumped to `26.1.2`.
- `ChatColor` usages replaced with Adventure API (`Component` / `NamedTextColor`).
- `JavaPlugin#getDescription()` replaced with `getPluginMeta()` (deprecated in 26.1.2).
- Spider web-shot projectile tagging migrated from `Metadatable`/`FixedMetadataValue`
  (deprecated — does not clean up values for removed entities) to `PersistentDataContainer`.

### Fixed
- `Particle.BLOCK_CRACK` / `Particle.BLOCK_DUST` (removed from current Paper API) replaced with
  `Particle.BLOCK`.
- Compile errors from decompiling anonymous `BukkitRunnable` classes in `ProjectileListener`.
- Raw-type `List` declarations that lost their generic type during decompilation
  (`AdvancedMobAI`, `SpiderAIUpdater`, `WitchAIUpdater`).

## [1.2.0] - 2025-06-28 ("1.2 Beta")

Original release by **Diamondxv0**. Recovered from the distributed `.jar`; this version's
source was never pushed to the public GitHub repo.

### Added
- Witch AI: supportive behavior — buffs nearby hostile mobs with beneficial potion effects.

### Changed
- `CreeperAIUpdater`: per-creeper error handling with rate-limited error logging, proactive
  targeting now scans nearby entities instead of every player on the server, added a
  same-world check and chunk-loaded guards in the wall-breach raycast.

## [1.1.0] - 2025-05-20 ("1.1 Beta")

Original release by **Diamondxv0**.

### Added
- Spider AI: web-shot attack (webs the target player from range) with enhanced targeting and
  a speed boost on a successful hit.
- `ProjectileListener`: handles the web-shot snowball's impact, temporarily replacing the
  target block with cobweb and applying a potion effect to a trapped player.
- `creeper.creeper_explosions.friendly_fire` config toggle.

## [1.0.0] - 2025-04-27

Initial public release by **Diamondxv0**.

### Added
- Creeper AI: wall breaching, proactive targeting, pillar-explosion detonation.
- Enderman AI: dismantler behavior (breaks obstructing blocks to reach its target).
- `DamageListener`: protects select hostile mobs from a normal (non-charged) Creeper's
  explosion damage.
