# OmniWard

PvP map-control objective for Paper 1.21.11. Players plant **wards** that project a team **aura buff** to nearby allies; enemies **channel-capture** them to flip control or destroy them. Contested control of map points, built for Folia.

Part of the SalyVn **OmniBundle** RPG suite.

## Features

- **Plant wards** with per-player caps, a server total cap, cooldown, and lifetime.
- **Aura projection** — configurable PotionEffects applied to allies within radius on a repeating region-thread task.
- **Channel capture** — the nearest enemy in range channels for N seconds; interrupted by damage or leaving range.
- **Flip or destroy** — a completed capture transfers control (`FLIP`) or removes the ward (`DESTROY`).
- **Pluggable teams** — Bukkit scoreboard teams when available, else an owner-only fallback.
- **Non-destructive marker block** — original block restored on removal.
- **Particle ring** visuals and action-bar capture progress.
- **Triumph GUI** listing your active wards; click to remove.
- **Hot reload** via `/ward reload` — safely restarts the repeating task.
- **Folia-safe** scheduling throughout; optional Vault / PlaceholderAPI soft-deps.

## Installation

1. Drop `OmniWard-1.0.0.jar` into your server's `plugins/` folder.
2. Start the server once to generate `plugins/OmniWard/config.yml`.
3. Tune the config, then run `/ward reload`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ward` | Open the ward GUI | `omniward.use` |
| `/ward plant` | Plant a ward at your location | `omniward.use` |
| `/ward list` | List your active wards (GUI) | `omniward.use` |
| `/ward remove` | Remove your nearest own ward | `omniward.use` |
| `/ward reload` | Reload config | `omniward.admin` |

Alias: `/wards`.

## Documentation

Full docs and configuration reference: https://salyys1.github.io/OmniWard/

## Building

```bash
./gradlew.bat clean shadowJar --no-daemon
```

The shaded jar is produced under `build/libs/`.

## Tech

Kotlin 2.1.0 · JDK 21 · Gradle 8.12 + Shadow · Paper 1.21.11 · FoliaLib · Triumph GUI.
