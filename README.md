# PracticeCore

A modular practice minigame plugin for Paper 1.21+. The first bundled mode is
**bridging**: every player gets their own ephemeral schematic-built arena in a
self-cleaning void world, a millisecond-honest timer, and per-arena personal
bests.

Licensed **GPL-3.0** (see `LICENSE`) — chosen deliberately so architecture and
code from the GPL ecosystem of reference plugins (SpeedBridge2, Infinite
Parkour) can be adapted where useful.

## How it works

- On every plugin enable the practice world's folder is **deleted and
  recreated** (void chunk generator, mob spawning/insomnia/weather/daylight
  disabled, auto-save off). Nothing in it ever persists — crash recovery for
  the world is free.
- Joining players are assigned a slot on a **square spiral grid** (default
  1000-block spacing); their arena schematic is pasted there, then they're
  teleported (async) and given the template's kit. Freed slots are reused
  lowest-index-first.
- The player's full prior state (inventory, location, gamemode, XP, effects,
  flight, …) is **snapshotted to disk** before their inventory is touched. Any
  exit path — command, teleport by another plugin, quit, kick, crash, /reload —
  funnels through the same restore. A player logging in with an orphaned
  snapshot gets it restored automatically.
- Resets don't re-paste: every placed block is **tracked and reverted**, which
  is near-instant. Full erase + re-paste happens only when a slot is recycled.
- Timer starts on first movement off the spawn block (or first block placed —
  configurable), runs on `System.nanoTime()`, shows tenths live on the
  scoreboard (FastBoard, flicker-free) and exact milliseconds on finish.

## Requirements

- Paper 1.21.x, Java 21+
- WorldEdit 7.3+ (or FastAsyncWorldEdit — recommended on busy servers; the
  plugin compiles against the WorldEdit API, which FAWE implements, and pastes
  become async automatically)
- FastBoard is fetched automatically at runtime via `plugin.yml` `libraries:`

## Commands

| Command | Permission | Description |
|---|---|---|
| `/practice join [template]` | `practicecore.use` | Start practicing |
| `/practice leave` | `practicecore.use` | Restore state and return |
| `/practice list` | `practicecore.use` | List arena templates |
| `/practice setup …` | `practicecore.admin` | Arena configuration wizard |
| — | `practicecore.bypass` | Enter the practice world without a session |

## Creating an arena template

1. Build your arena anywhere (even another world). Include the start island,
   the gap, and the finish island. **Don't** place the finish button yet.
2. Select it and run `//copy` (stand somewhere sensible — the copy point
   becomes the paste origin).
3. `/practice setup start <name>` — the schematic is saved, pasted in the
   practice world, and you're teleported there in creative.
4. Stand on the start island facing the gap: `/practice setup spawn`
5. Place the provided **button or pressure plate** where runs should finish
   (the plugin stamps it onto the arena after every paste — it doesn't need to
   be in the schematic).
6. Optional: arrange your inventory exactly as players should receive it
   (e.g. 2×64 wool) and run `/practice setup kit`
7. `/practice setup save` — the template goes live immediately.

Templates live in `plugins/PracticeCore/templates/<name>/` as `arena.schem` +
`arena.yml`; they can be copied between servers as folders.

## Building

```
./gradlew build      # → build/libs/PracticeCore-<version>.jar
```

## Roadmap

- Additional modes via the `Mode` registry (clutch practice, pearl practice —
  per-template item allow-lists are already half-way there)
- SQLite storage backend with full run history + leaderboards
- Warm-pool pre-pasted arenas for join bursts; staggered "dissolve" reset
  animation
- Marker-block template import (spawn/finish baked into the schematic)
