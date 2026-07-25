# Changelog

All notable changes to OmniWard are documented here. This project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-07-25

Initial release.

### Added
- **Ward planting** — `/ward plant` places a runtime ward at the player's location with per-player caps, a server-wide total cap, a plant cooldown, and a configurable lifetime.
- **Aura projection** — a repeating region-thread task applies configurable PotionEffects to allied players inside each ward's radius.
- **Channel capture** — the nearest enemy in range channels a capture over a configurable number of seconds. The channel resets if the channeler takes damage (via the damage listener) or leaves range.
- **Flip or destroy** — `capture.mode` chooses whether a completed capture transfers ward control to the capturer's side (`FLIP`) or removes the ward (`DESTROY`).
- **Pluggable team resolution** — Bukkit scoreboard teams are used when the owner has one; otherwise the plugin falls back to an owner-only model.
- **Visuals** — a particle ring marks each ward and turns hostile while contested; capture progress is streamed to attacker and defender action bars.
- **Non-destructive markers** — an optional marker block is placed on plant and the original block is restored on removal, tracked in ward state.
- **Ward GUI** — `/ward list` (and `/ward`) opens a Triumph GUI showing each ward's location, radius, aura count, remaining lifetime and capture state; clicking removes a ward.
- **Hot reload** — `/ward reload` re-reads config.yml and safely stops and restarts the repeating task.
- **Folia support** — all entity effect application and block edits are dispatched onto the owning region thread via FoliaLib; `folia-supported: true`.
- **Optional soft dependencies** — Vault and PlaceholderAPI are detected if present and the plugin degrades gracefully without them.
