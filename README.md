# PracticeCore

A modular practice minigame plugin for Paper 1.21+. The first bundled mode is
**bridging**: every player gets their own ephemeral schematic-built arena in a
self-cleaning void world, a millisecond-honest timer, and per-arena personal
bests.

Drop the jar in and it works — a ready-to-play bridging arena ships inside it
and unpacks itself on first start.

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
- Every personal best also lands in an in-memory **leaderboard** per arena,
  built once from `playerdata/` off-thread on enable and maintained
  incrementally afterwards — ranks are exact and free to read.
- Players can **switch arenas** straight from one to another. The new arena is
  fully validated and built before the old one is torn down, so "you can't join
  that" never costs someone the arena they were in; and the switch keeps their
  original snapshot, which is what eventually gets restored. Joining the arena
  you are already in restarts the run.
- **Leaving by teleport is a first-class exit.** Any teleport that actually
  lands outside your arena — `/spawn`, `/tpa`, a warp, another world — ends the
  session and restores everything except your location, letting the destination
  win. That check runs at `MONITOR` on the teleport, so a teleport some other
  plugin cancels can never end a run that never moved.

## Messages

Every piece of text the plugin shows a player lives in `messages.yml` —
chat, action bars, titles, broadcasts, and all GUI titles, button names and
lore. Formatting is MiniMessage, so colours, gradients, hover and click are
available everywhere. Set any message to `''` to silence it.

Placeholder values a player supplied (names, arena display names) are inserted
with `Placeholder.unparsed`, so a name containing MiniMessage syntax is shown
literally instead of being interpreted. Admin-written text inserted into
another message (a status line, a state label) goes in through `Messages.ref`
and *is* formatted.

The jar's copy is indexed first and your file overlaid on top, so a key can
never go missing however the file is edited; the migrator then writes any
absent key back into your file. The two exceptions are the menu item's own
name and lore, which stay in `config.yml` beside its material and slot — they
define the item rather than being something the plugin says.

## The bundled arena

`bundled-template` in `config.yml` unpacks the arena that ships in the jar to
`templates/<name>/` (default `turtle`) the **first** time the plugin starts.
A marker file records that this happened, so deleting or renaming the arena
does not bring it back on the next restart. Delete the marker
(`plugins/PracticeCore/.bundled-installed`) to reinstall it.

## The menu item and GUI

`/practice item` gives an admin the hotbar menu item — a tagged item (a
persistent-data key, not a name match, so look-alikes can't spoof it). Drop it
into the hotbar slot you want, arrange the rest of the kit, then
`/practice setup kit`: it is saved with the kit and handed to every player who
joins that arena. `menu-item.force-in-kit` retro-fits it into arenas built
before the item existed. Right-clicking it opens:

- **Play** — the arena picker, filtered by the same permission check the join
  command uses, showing your best, your rank and the arena record per entry
- **Random Arena** — straight into one of the arenas you can play
- **Leaderboards** — per-arena top times, your standing, and the gap to the
  player one place ahead
- **My Stats** — every arena you've finished, ranked
- **Restart Run** — reverts your blocks and puts you back on the spawn
- **Sidebar** — show/hide the live timer scoreboard (remembered per player)
- **Help** and **Leave**

The leave button restores everything and returns you to the world you came
from — or hands you to the proxy server named in `leave.server`, if set.

## Requirements

- Paper 1.21.x, Java 21+
- WorldEdit 7.3+ (or FastAsyncWorldEdit — recommended on busy servers; the
  plugin compiles against the WorldEdit API, which FAWE implements, and pastes
  become async automatically)
- FastBoard is fetched automatically at runtime via `plugin.yml` `libraries:`

## Commands

| Command | Permission | Description |
|---|---|---|
| `/practice join [arena]` | `practicecore.use` | Start practicing |
| `/practice leave` | `practicecore.use` | Restore state and return |
| `/practice menu` | `practicecore.menu` | Open the GUI |
| `/practice list` | `practicecore.use` | List arenas |
| `/practice top [arena]` | `practicecore.leaderboard` | Leaderboards |
| `/practice stats [player]` | `practicecore.stats.other` for others | Personal bests |
| `/practice sidebar` | `practicecore.use` | Show/hide the live timer |
| `/practice setup …` | `practicecore.setup` | Arena configuration wizard |
| `/practice edit <arena>` | `practicecore.setup` | Reopen a saved arena |
| `/practice arena …` | `practicecore.arena` | Administer saved arenas |
| `/practice item [player]` | `practicecore.item` | Get the hotbar menu item |
| `/practice pb reset <player> [arena\|all]` | `practicecore.pb.reset` | Wipe personal bests |
| `/practice world info\|regen` | `practicecore.world` | Practice world management |
| `/practice reload` | `practicecore.reload` | Reload config and arenas |
| — | `practicecore.bypass` | Enter the practice world without a session |

`practicecore.admin` is a parent of every admin node above.

`/practice pb reset` tab-completes **every player the plugin has records for**,
online or not — the name index is built from `playerdata/` on startup and kept
current as players join.

### The player permission kit

`practicecore.user` is the node a normal player needs, and it is on by default.
It is a parent of `practicecore.use`, `practicecore.menu` and
`practicecore.leaderboard` (each of which defaults to false on its own), so
revoking `practicecore.user` from a group switches the plugin off for them in
one move, while the children stay available for finer control.
`practicecore.admin` is the equivalent kit for every admin node.

### Per-arena permissions

**Arenas are open by default.** Every arena has a node — whatever `permission:`
its `arena.yml` names, or `<arenas.permission-prefix><arena>` otherwise — and a
player is refused only where that node is explicitly set to **false** for them:

```
/lp user Steve permission set practicecore.arena.turtle false
```

Set `arenas.access-mode: ALLOW` to invert it into a whitelist, where only an
explicit grant admits anyone. Either way an explicit setting always wins; the
mode only decides the answer for players who have no setting at all, so
switching modes never changes what any existing node means. Operators still
pass under ALLOW, since they hold undeclared nodes implicitly.

Set an arena's node with `/practice arena permission <arena> <node|default>`,
or `/practice setup permission <node>` mid-wizard. Locked arenas show as locked
in the GUI (admins additionally see which node is stopping the player), or
vanish entirely with `arenas.hide-locked: true`. The join command and the GUI
share one check, so the menu can never offer something the command would
refuse.

### The default arena

`default-arena.name` in `config.yml` — or `/practice arena default <arena>`,
which writes it for you — names the arena players end up in without asking for
one. Three independent switches decide when it applies:

| Setting | Effect |
|---|---|
| `on-bare-join` | A bare `/practice join` goes there |
| `on-world-enter` | Arriving in the practice world any other way joins it, instead of being turned away |
| `on-server-join` | Connecting to the server drops you straight in |

If the named arena is missing, unfinished, or one the player may not use, each
path falls back to that player's first available arena rather than failing.

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

Optional polish, either mid-wizard or on a saved arena:

| Wizard | Saved arena | Effect |
|---|---|---|
| `/practice setup display <text…>` | `/practice arena display <arena> <text…>` | Name shown in menus |
| `/practice setup icon [material]` | `/practice arena icon <arena> <material\|auto>` | Menu icon (defaults to the kit's main block) |
| `/practice setup permission <node\|none>` | `/practice arena permission <arena> <node\|none>` | Gate the arena |
| `/practice setup blocks <true\|false>` | `/practice arena blocks <arena> <true\|false>` | Require a placed block for a PB |
| `/practice setup mode <id>` | — | Which `Mode` the arena belongs to |

## Editing a saved arena

`/practice edit <arena>` pastes the arena into a fresh grid slot, stamps its
finish trigger back in, and pre-loads **every** setting — so any single part
can be changed without redoing the rest. An admin who is mid-run when they open
the editor is taken out of their arena **in place**: no bounce back to the
lobby and no second teleport. Their original snapshot is kept rather than
re-captured, so closing the wizard still returns them to where they were before
any of it started — never to a kit-wearing pose inside an arena that no longer
exists.

- Move the spawn: stand where you want it, `/practice setup spawn`
- Move the finish: place another button or pressure plate
- Change the kit: `/practice setup kit load` puts the saved kit in your
  inventory, rearrange it, `/practice setup kit` saves it back
- **Reshape the arena itself**: build in place, then `/practice setup capture`
  writes the region back over `arena.schem`. Trigger blocks are stripped out
  of the capture — including any you moved away from — since the plugin stamps
  those in after every paste.
- Swap the build entirely: `//copy` something new, `/practice setup schematic`
- `/practice setup info` prints the pending state; `/practice setup save`
  commits, `/practice setup cancel` leaves the saved arena untouched.

Other arena administration is available without entering the wizard:
`/practice arena list`, `info <arena>`, `default [arena|none]`, and
`delete <arena> confirm`. Deleting
evicts anyone currently in the arena and removes it completely: the folder, its
schematic, its leaderboard, and every recorded time on it across all
playerdata. (The on-disk sweep matters — leaving the times behind would have
the next startup scan rebuild a leaderboard for an arena that no longer
exists.) The sweep runs off-thread and reports how many player records it
cleared when it finishes.

## Reload and world regeneration

`/practice reload` is all-or-nothing. It parses config.yml on a throwaway
object **before** touching anything, because Bukkit's own `reloadConfig()`
swallows a syntax error and hands back an empty config — on a live server that
would silently reset every setting to its default. A broken file changes
nothing and says why. The previous `PCConfig` is likewise kept until the new
one has been built without throwing, and arenas are collected into a local map
and swapped in only once the whole pass succeeds.

Because grid spacing, base Y and arena definitions all describe arenas that are
already pasted, a reload with anyone practicing (or the setup wizard open)
stops and asks: `/practice reload confirm` ends and fully restores those
sessions first. Changing `world.name` is reported as needing
`/practice world regen`.

`/practice world regen confirm` unloads, deletes and rebuilds the practice
world from nothing, then restarts the grid at slot 0. Every session is ended
and restored to where it came from first, the wizard is cancelled, and anyone
still standing in the world is moved to `leave.fallback-world` (or the main
world). Arenas, kits and leaderboards are untouched. `/practice world info`
shows the world, session count, allocated slots and loaded chunks.

## Config versioning

Every file an admin can edit carries a `config-version`, and every file the
plugin owns carries a `data-version` (see `config/Versions.java`):

| File | Key | Migrator |
|---|---|---|
| `config.yml` | `config-version` | `PracticeCorePlugin.configSteps` |
| `messages.yml` | `config-version` | `Messages.steps` |
| `templates/<name>/arena.yml` | `config-version` | `ArenaTemplate.migrate` |
| `playerdata/<uuid>.yml` | `data-version` | `StatsStore.migrate` |
| `snapshots/<uuid>.yml` | `data-version` | version-checked on restore |

The two admin-editable YAML files share one engine, `config/YamlMigrator`.

On startup, an out-of-date file is copied into `backups/` and then upgraded:
renamed keys are moved by the version steps, and `config.yml` additionally gets
any key the jar defines but your file lacks — values and comments you already
had are preserved, so a new setting never silently runs on a hard-coded
default. A file stamped **newer** than this build understands is left strictly
alone and reported, rather than being "fixed up" into a downgrade. Playerdata
migrates in memory and stamps itself on the next write, so reading someone's
stats never rewrites their file.

To add a format change: bump the constant in `Versions`, add the step to that
file's migrator, and ship it. The backup and top-up are automatic. `config.yml`
v1 → v2 is a worked example: it replaced the `arenas.require-permission`
boolean with `arenas.access-mode`, mapping `true` to `ALLOW` and `false` to
`DENY` so existing servers keep the behaviour they had.

## Building

```
./gradlew build      # → build/libs/PracticeCore-<version>.jar
```

## Roadmap

- Additional modes via the `Mode` registry (clutch practice, pearl practice —
  per-template item allow-lists are already half-way there)
- SQLite storage backend with full run history (the YAML store already backs
  in-memory leaderboards; history is what it lacks)
- Warm-pool pre-pasted arenas for join bursts; staggered "dissolve" reset
  animation
- Marker-block template import (spawn/finish baked into the schematic)
- Holographic leaderboard signs/armour stands in the lobby world
