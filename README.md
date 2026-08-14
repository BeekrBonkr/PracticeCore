# PracticeCore

A modular practice minigame plugin for Paper 1.21+. Five modes ship in the
box — **bridging**, **bed breaking**, **rush**, **MLG clutching** and an
**AI PvP bot** — and
every player gets their own ephemeral schematic-built arena in a self-cleaning
void world, a millisecond-honest timer, and per-arena personal bests.

Drop the jar in and it works — ready-to-play arenas ship inside it: the
bridging arena unpacks itself, and the bedbreak shafts and the MLG tower are
built block-by-block by the plugin on first start.

Licensed **GPL-3.0** (see `LICENSE`) — chosen deliberately so architecture and
code from the GPL ecosystem of reference plugins (SpeedBridge2, Infinite
Parkour) can be adapted where useful.

## How it works

- On every plugin enable the practice world's folder is **deleted and
  recreated** (void chunk generator, natural mob spawning/insomnia/weather/
  daylight disabled, auto-save off, difficulty NORMAL so plugin-spawned
  hostiles — the PvP bot — exist and hit). Nothing in it ever persists —
  crash recovery for the world is free.
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

## The modes

A template names its mode in `arena.yml` (`mode:`), set during creation with
`/practice setup mode <id>`. Mode-specific tuning lives in the template's
`settings:` section — every generated arena writes its defaults there, so the
keys are always visible in a working example. All modes share the same
machinery: bounds are enforced every tick (walls push back, falling below the
arena fails and resets the run), and every reset reverts or regenerates the
arena rather than re-pasting it.

### bridging

The original mode: run the course, hit the button or pressure plate at the
end. Timer starts on first movement (or first block, per `timer.start-mode`).

A **speedometer** sits above the hotbar during bridging sessions: your current
speed in m/s (smoothed over the last few samples), the distance traveled this
run and the blocks placed. Tune it with `speedometer.enabled` /
`speedometer.update-ticks` in `config.yml`; the text itself is
`speedometer.bar` in `messages.yml` (set to `''` to silence it).

### bedbreak

A fixed set of defense blocks stands between you and a bed; you break through
to the bed as fast as possible. The blocks are **reshuffled every run but
their composition is fixed**, so times are comparable between players. Broken
blocks never drop, only the defenses and the bed are breakable, the timer
starts on your first broken block, and breaking the bed finishes the run.

Two orientations, chosen per arena with `settings.bedbreak.orientation`:

- `VERTICAL` — a column above the bed inside a sealed barrier shaft; you spawn
  on top and dig straight down. One block per step.
- `HORIZONTAL` — a wall filling a sealed corridor in front of the bed; you dig
  forward through it. Each step is a two-high column of the same material, so
  the only way to the bed is straight through.

The mode also tracks **reaction time** as its own stat: whenever finishing a
step means the next one needs a different tool, the clock runs from the
moment the step breaks until you switch to that tool (already holding it
counts as a zero — pre-switching is the skill being measured). Consecutive
steps of the same material, or sharing a best tool, never count. The run's
average shows on the sidebar and is persisted per arena
(`reaction-last-ms` / `reaction-best-ms` in playerdata).

Rearranging the tools in your inventory is remembered **per player**: however
your hotbar was ordered when the run ended is how the kit is handed to you
next time.

`settings.bedbreak`: `orientation`, `bed-x/y/z` (bed head, relative to the
paste origin), `bed-facing` (also the direction a horizontal wall extends
from the head), `bed-material`, and `blocks` — a `MATERIAL: count` map; one
step per count, rising from one block above the bed's head (vertical) or
extending from the head (horizontal).

### rush

An empty bedwars map with reset conditions: you spawn at a team base of your
choice and race to an objective. **Every objective the map supports is armed
on every run** — enemy beds are standing, and an emerald and a diamond sit
waiting on their generators; whichever you complete first ends the run, no
choosing beforehand. Each map+objective pair still keeps its own personal
bests and leaderboard (stored under `map#bed`, `map#emerald`, `map#diamond`),
so a 6-second diamond grab never shares a board with a 40-second bed rush,
and the sidebar shows your best on all three at once.

The timer always starts the moment you first move off the spawn —
`timer.start-mode` does not apply to rush. Runs come in two flavors:

- **Competitive** — one button in the rush menu, instant start, and the only
  way times are recorded and ranked. The loadout is pinned so every entry on
  a board raced the same conditions: no starting items, bed defenses on (the
  preset in `rush.competitive-defense`, default wool + end stone), base
  generators running.
- **Casual** — your own mix of the difficulty modifiers below. Nothing is
  written to stats: no time, no finish count, no personal best; the finish
  message still shows your time and the action bar reminds you it was
  unranked.

Either way each map keeps its three leaderboards — bed rush, emerald rush
and diamond rush — fed only by competitive runs.

Maps come from two places:

- **MBedwars import** — `/practice rush import <mbedwars-arena>` captures the
  arena's region as a schematic and resolves everything else from the MBedwars
  data: team spawns, bed positions, every item generator and the shop dealer
  spots. The map is playable immediately, under the Rush category. Re-import
  with `overwrite` after map changes; times and settings survive.

  **Mass import**: `/practice rush importall teams:<n> size:<n>` pulls every
  MBedwars arena matching that shape in one go — either filter alone or both
  together (`teams:4 size:2` = four teams of two). The batch is filed into one
  `templates/<category>/` folder so it groups in the menu, named after the
  filter (`4x2`, `8-teams`, `solo`/`doubles`/`triples`/`quads`) or set
  explicitly with `category:<name>`; give it an icon and display name in `guis.yml`
  (`categories.entries`). Existing arenas are skipped unless `overwrite` is
  appended. `/practice rush list` shows each arena's shape.
- **By hand** — build or `//copy` a map, `/practice setup start <name>`,
  `/practice setup mode rush`, then walk the map placing markers:
  `rush team <color>` (stand at that base's spawn), `rush bed <color>` (look at
  that team's bed), `rush gen <iron|gold|diamond|emerald>` (stand on the
  spawner block), `rush dealer` (shop NPC spot), `rush clear` to start over.
  At least one team needs both a spawn and a bed to save.

Clicking a rush arena in the GUI opens the **rush setup menu** instead of
joining straight away: cycle the team base, hit **Start Competitive** for the
ranked loadout, or dial the casual difficulty modifiers — **starter blocks**
(none/16/32/64 wool), **starting resources** (iron/gold to spend
immediately), **pickaxe tier** (none → diamond), **bed defenses**
(auto-generated shells over every enemy bed: wool, wool + end stone, or end
stone + obsidian, reusing nothing from the map itself), and whether the
**iron/gold generators** run — every team base produces, exactly like a real
game, with a per-generator item cap so idle bases don't flood — and **Start
Casual**. Every choice
(including which mode you last played) is remembered per player — a plain
`/practice join <map>` replays your last setup.

During a run the **mirrored MBedwars shop** is open for business: villager
NPCs stand at the map's dealer spots and sell the real MBedwars item shop —
same pages, icons, items, prices and slot layout — settled directly against
your inventory, no MBedwars game involved. Purchased wool follows your
wool-color setting, and auto-wear armor equips itself just like the real
shop. **Special items** resolve to the exact stacks MBedwars sells and the
ones that make sense solo are fully emulated: **fireballs** launch on right
click, **TNT auto-ignites** on place, the **bridge egg** trails a wool
bridge, the **rescue platform** builds a slime disc underfoot and the mini
shop opens the shop; explosions break only blocks you placed and generated
bed defenses (never the map or a bed), with proper bedwars knockback for TNT
and fireball jumps. Special items that need enemies (trap, tracker, guard
dog, …) are still sold but say so when used.

Generator items and shop stock are exempt from the inventory validator (rush
manages its own economy); everything else about the session is standard
machinery: full reset on death or void fall, blocks revert, beds and defenses
are re-placed, NPCs and items respawn, and every storage block in the arena
(team chests included) is emptied so nothing stashed survives a death. Ender
chests stay closed during sessions — their contents are real player data that
would leak out of practice.

MBedwars is a **soft dependency**: importing and the shop need it installed;
hand-built rush arenas play fine without it (dealer clicks then explain the
shop is unavailable). Generator pacing is configurable under `rush:` in
`config.yml` (`iron-interval-ticks`, `gold-interval-ticks`,
`generator-item-cap`, `base-generators-default`).

### mlg

Water bucket clutch practice: you spawn on a glass platform looking straight
down at the landing pad, jump off whenever you're ready — **the platform
breaks away behind you the moment you do** — fall a **random distance**, and
have to place your water before impact. Touching the water is an instant win
and resets the arena; hitting the pad without it fails. The drop height is
re-rolled on every reset, so the fall's length can't be learned by muscle
memory.

There is no timer — the score is the **streak**: consecutive successful
clutches, shown on the sidebar next to your highest ever. It is persisted per
arena in playerdata (`streak` / `streak-best`) and survives arena resets and
relogs; a failed run resets it to zero, and so does bailing out mid-air
(restarting, switching arenas or logging off between jump and splash), so a
fall can never be abandoned to dodge the loss.

The bucket only works **mid-fall**: emptying it while still standing on the
platform is refused, so a pre-placed pool can never count. The clutch water
(and anywhere it flows) is dried back up on every reset.

MLG arenas always allow buckets regardless of `session.allow-buckets`, and
the shaft below the spawn is mode-owned space — the platform and pad are
built by the plugin, so a hand-made arena needs nothing but a spawn in
mid-air. The generated arena is a 110-block shaft whose barrier walls run
its **full height**, so the fall is walled in from platform to floor.
Everything else is `settings.mlg`, all optional: `platform-radius` (glass
platform half-size, default 1 → 3×3), `pad-radius` (default 5 → 11×11),
`pad-material` (default `GRASS_BLOCK`) and `min-drop` / `max-drop`
(platform-to-pad distance range, defaults **20–100**). The sidebar shows the
round's rolled drop next to your streak.

### pvpbot

PvP practice against an **AI opponent**: an endless spar in an admin-built
arena against a bot that fights like a 1.8.9 player — it **strafes out of
your crosshair** (the better you track, the harder it works to slip off your
cursor), holds duel spacing, **spam-clicks at a configured CPS** with a
configurable miss chance, goes for **jump-crits and W-tap knockback resets**,
and — each individually toggleable — **rods you at range**, takes **bow
potshots**, and raises a **1.8 sword block** when it gets comboed (halving
what lands). Hits it takes put it into a short **hitstun** where it rides
the knockback like a real player — comboing it works the way it should.

At the high accuracy tiers the bot also fights with its head (the
**cerebral layer**, on by default for Veteran and above): it **times its
hits to your immunity window** — no wasted clicks, every swing lands the
tick your i-frames expire; it acts on a **perceived position** refreshed at
its reaction speed and dead-reckoned along your motion, so it leads you
instead of chasing your past; it swaps between **stances** — pressing hard
after winning an exchange, and **kiting behind rod chip and sword blocks**
when it's badly losing; it plays the **arena edge** both ways, never
strafing itself over the rim while angling its shoves to walk *you* toward
it; it **reads your habits** — constant hopping gets met with timed
crit-fishing, swinging at air gets whiff-punished with a lunge through the
gap; and it occasionally **feints** a retreat, countering hard if you take
the bait. Lower tiers instead carry honest human weaknesses: a stale
picture of where you are and no rhythm to their clicks.

With **ProtocolLib** installed the bot renders as a **real player model
wearing your own skin** (a packet-level disguise: the server still runs a
husk, clients see a player). Without it, the bot is a named husk scaled
down to player height. Either way its floating tag shows its **live
health**, and its brain is entirely this plugin's — no NPC dependencies.

There is **no timer and no leaderboard** — the sidebar keeps session stats
instead: kills, deaths, hits landed/taken, current and best combo, and the
bot's **name tag doubles as its health bar**. Every real hit on the bot pops
a **floating damage indicator** (a DeluxeCombat-style hologram showing the
hearts dealt, drifting upward before vanishing). Dying never shows a death
screen: the fatal hit is intercepted, a **"YOU DIED" title** with your K/D
appears, it costs a **stock**, and both fighters snap back to their spawn
points at full health. Killing the bot gets its own title, but only the
bot respawns — **you keep your ground**, with health and kit refreshed in
place. Either way a **chat line sums up the exchange** with the hearts the
survivor had left. Getting knocked off the arena is a **ring-out** — same
thing.

Kits come from the **kit gallery**: fourteen built-in presets — Sword,
NoDebuff, Boxing, BuildUHC, Combo, Gapple, Iron, Diamond, Classic, Soup,
Archer, Axe, Bedwars and Skywars — some carrying **blocks to place**
(BuildUHC, Bedwars, Skywars; every stock reset reverts what you built).
Left-click a tile to select, **right-click for a full preview** laid out
exactly like your inventory (armor up top, hotbar at the bottom). The plugin
**remembers the last kit you chose** and hands it to you on your next join,
**remembers how you arranged its items** (per preset — rearrange your
NoDebuff hotbar once and every future deal matches it), and keeps
consumables (pots, gapples, arrows, blocks) **topped up** so a spar never
runs dry. The practice menu item always keeps a hotbar slot unless the kit
truly needs all nine.

The bot is configured from the **practice menu**: while sparring, a **Bot
Settings** entry appears next to the regular settings — the kit gallery, bot
gear (mirror your kit, or a fixed tier), a named **difficulty preset**
(**Rookie → Brawler → Veteran → Demon → Unfair → Suffer**, one click setting every
AI knob; hand-tuned mixes read as Custom), plus the individual knobs —
evasiveness, CPS, accuracy, crits/W-taps, reach, aggression — and the
rod/bow/block toggles. The near-top tier of two knobs is Demon's own:
**extreme** evasiveness (fastest strafe, hops mid-fight, shrugs off hitstun
sooner and escapes long combos more often) and **frenzied** aggression
(fastest approach, tightest spacing, closes distance with sprint-jumps like
a player holding W and spamming space) — every tier cycles like any other
value, so they can be mixed into custom setups too.

Above Demon sits **Unfair**, for the insane: four knobs gain an **unfair**
tier and the accuracy knob's unfair setting switches on a gloves-off layer
above cerebral. It strafes faster than Demon and *darts* sideways the
instant your crosshair settles on it, hops twice as often, slips out of
combos a hit earlier, re-reads the fight almost twice as often and starts
kiting before it is actually desperate, feints, whiff-punishes and
crit-fishes far more freely, leans on the rod harder in neutral, w-taps or
crits on most clean hits, and angles every shove harder toward the rim.
What it deliberately does **not** do is cheat the numbers: reach stays
capped at long (3.4), CPS is still tick-capped, its accuracy is Demon's
same perfect 1.0, and hitstun still applies — so wall combos, rod resets
and edge discipline beat it. Barely.

And above Unfair sits **Suffer** — the same dirty playbook with every dial
at its ceiling, while movement and combat stay strictly inside a player's
physical envelope (no extra reach, no extra CPS, barely any extra speed;
only the decisions are inhuman). It re-reads the fight near-continuously
and starts kiting the moment the trade math tips, slips combos at 90% and
turns every slip into an immediate counter-offensive, darts harder off a
settled crosshair and hops constantly, whiff-punishes almost every wasted
click, crit-fishes and feints and max-range-baits at will, w-taps or crits
on nearly every clean hit, and shoves hardest of all toward the rim. It is
the honest ceiling of the engine: everything Suffer does, a human could do
once — it does all of it, every exchange, forever.

The bot also keeps an **AFK watch** at every difficulty: if your position,
aim and clicks all freeze for a few seconds, it stands down — stops
attacking, stops closing, and just stares — then resumes the moment
anything stirs (moving, looking, or clicking), with a short grace beat so
returning from AFK never costs a free hit. **The bot freezes and
holds fire while any menu is open** and re-reads everything on close; every
choice is persisted per player. Combat *mechanics* — 1.8 cooldowns,
knockback shaping, the player's own sword-block damage halving — are left to
the server's combat plugins (OldCombatMechanics, vanilla-sword-blocking),
with one correction: modern servers treat a raised block-animation sword as
a *shield* that negates hits entirely, so the mode strips that full negation
and restores the true 1.8 behavior (blocking halves damage, on both sides).

Building an arena needs just a platform and a spawn: `/practice setup start
<name>`, `/practice setup mode pvpbot`, `/practice setup spawn`, optionally
`/practice setup pvpbot bot` standing where the bot should spawn (without a
marker it spawns a few blocks ahead of the player spawn, facing back), then
`save`. No kit and no trigger are required.

## Spectator mode

`/practice spectate` (also `spec`, `watch`) opens a picker of everyone
currently practicing — one head per player — and clicking one drops you into
their arena as a spectator; `/practice spectate <player>` goes straight there,
and the main menu has a Spectate button. Spectating from inside your own
session ends your run first, fully restored.

A spectator can watch but never touch: flying, invulnerable, non-collidable,
**invisible to everyone except other spectators**, and unable to break or
place blocks, press triggers or pressure plates, pick up items (a rush
objective is safe under a hovering spectator), deal or take damage, throw
projectiles, open shops or rearrange their own hotbar. They hold three tools:
a **compass** (teleport back to the player being watched), a **spyglass**
(switch to another player), and a **bed** (stop spectating). The sidebar
shows who they watch, the arena, the mode and the live timer.

Spectators are leashed to the arena they are watching — drift more than a
dozen blocks past its walls and you are pulled back to the target, which also
auto-follows them through arena switches. Leaving spectator mode (the bed,
`/practice spectate leave`, or the watched player stopping) restores you and
then drops you straight into the **default arena** — on a practice server
that is home; when no arena is available to you, the restore alone stands.
`/practice leave` still exits practice entirely. The pre-spectate state uses
the same crash-safe snapshot store as sessions, so even a server crash
mid-spectate restores you on next login. The watched player is told when
someone starts and stops watching them.

For admins: the permission is `practicecore.spectate` (part of the default
`practicecore.user` kit), the main-menu button is configured at
`main.buttons.spectate` in `guis.yml`, and every piece of text lives in
`messages.yml` under `spectate.*` (chat, the three tools), `gui.spectate.*`
(the picker) and `board.spectate-lines` (the sidebar). Spectators hold no
session and no grid slot, so they cost nothing and count toward no limits —
nothing needs to be sized for them.

## Messages

Every piece of text the plugin shows a player lives in `messages.yml` —
chat, action bars, titles, broadcasts, the sidebar (title and every line of
every board layout), the speedometer bar, and all GUI titles, button names
and lore. Formatting is MiniMessage, so colors, gradients, hover and click
are available everywhere. Set any message to `''` to silence it. Menu
*structure* (slots, icons, rows, fillers) lives separately in `guis.yml`.

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

**Sidebar footer**: set `scoreboard.server-ip` in `config.yml` and every
board — every mode — signs off with your server's address on its bottom
line, styled by the `board.footer` lines in `messages.yml` (a gold gradient
by default). While it stays `''`, no footer renders at all.

Anything that takes more than an instant tells the player so as it starts:
joining shows *Building &lt;arena&gt;…* and *teleporting you in…* on the
action bar while the schematic pastes, the setup wizard says *Pasting…*
while it builds, and imports and world regeneration announce themselves in
chat — a command that goes quiet mid-work reads as a broken server.

## The bundled arenas

`bundled-template` in `config.yml` unpacks the bridging arena that ships in
the jar to `templates/<name>/` (default `turtle`) the **first** time the
plugin starts. A marker file records that this happened, so deleting or
renaming the arena does not bring it back on the next restart. Delete the
marker (`plugins/PracticeCore/.bundled-installed`) to reinstall it.

`generated-arenas` works the same way for the bedbreak and MLG arenas, except
nothing is unpacked: the plugin builds each arena block-by-block in code and
writes it out as a normal template folder (defaults `bedbreak` and
`bedbreak-horizontal`, one per orientation, plus `mlg`) — fully editable,
deletable, and guarded by its own marker file (`.generated-installed`; remove
a line from it to regenerate that arena). Set a name to `''` or
`generated-arenas.enabled: false` to skip them.

## The menu item and GUI

`/practice item` gives an admin the hotbar menu item — a tagged item (a
persistent-data key, not a name match, so look-alikes can't spoof it). Include
it when you `/practice setup kit` and it is handed to every player who joins
that arena. Wherever a kit was saved with it, the item is **normalized into
`menu-item.slot`** (default 8, the last hotbar slot) on every deal, swapping
whatever sat there into its old spot — so it always shows up in the same
place. `menu-item.force-in-kit` retro-fits it into arenas built before the
item existed, relocating (never destroying) the slot's occupant; a kit that
genuinely fills all 36 slots wins, and the menu stays reachable via
`/practice`. Right-clicking it opens:

- **Play** — a category picker (one tile per arena category, each with its
  own menu), then the arena picker: filtered by the same permission check the
  join command uses, showing your best, your rank and the arena record per
  entry. An arena's category is **the folder its folder sits in**:
  `templates/<category>/<arena>/` is listed under `<category>`, and an arena
  straight in `templates/` is listed under its mode id. Re-categorising is a
  drag-and-drop plus `/practice reload` — or `/practice setup category
  <name|default>` during the wizard, which moves the folder for you. Give a
  category an icon and display name under `categories.entries` in `guis.yml`;
  turn `categories.enabled` off there to go back to one flat list.
- **Random Arena** — straight into one of the arenas you can play
- **Leaderboards** — per-arena top times, your standing, and the gap to the
  player one place ahead
- **My Stats** — every arena you've finished, ranked
- **Restart Run** — reverts your blocks and puts you back on the spawn
- **Spectate** — the spectator target picker: one head per player currently
  practicing (needs `practicecore.spectate`; see [Spectator mode](#spectator-mode))
- **Sidebar** — show/hide the live timer scoreboard (remembered per player)
- **Settings** — per-player, persisted in playerdata: permanent night vision
  while practicing, the color of the wool in your kit (and so of your
  bridges), and a client-side time of day for your arena. Changes apply
  immediately mid-session and are undone when you leave.
- **Help** and **Leave**

Every one of these menus can also be opened directly from chat:
`/practice menu <main|arenas|categories|leaderboards|stats|settings>` (a bare
`/practice menu` opens the hub).

The leave button restores everything and returns you to the world you came
from — or hands you to the proxy server named in `leave.server`, if set.

Two server-wide announcements keep leaderboards alive (`effects` in
`config.yml`): taking the **#1 spot** on an arena broadcasts loudly and plays
a small chime for everyone online (`broadcast-records`), and quietly, beating
your **own personal best** posts a one-line note (`broadcast-pbs`) — never on
top of the record shout, and never for a first finish.

Menu **layout** is configurable in `guis.yml`: every button's slot, icon
material and visibility, row counts, the border filler, and the shared
navigation row of the paged menus. All menu text stays in `messages.yml`.

Kits are exact: while practicing, anything in your inventory that is not a
kit item (or your wool recolor of one, or the menu item) is swept back out
on a fixed schedule, and crafting is blocked — `session.validate-inventory`
and `session.validate-inventory-ticks` in `config.yml`.

## Requirements

- Paper 1.21.x, Java 21+
- WorldEdit 7.3+ — but **FastAsyncWorldEdit is strongly recommended**: with
  FAWE detected, arena pastes, arena erases and rush map imports all run off
  the main thread, so even an 8-team map loads without a lag spike. With
  vanilla WorldEdit those operations must run on the main thread and big maps
  will stall the tick loop. Parsed schematics are cached either way, so
  repeated joins never re-read the file.
- FastBoard is fetched automatically at runtime via `plugin.yml` `libraries:`
- MBedwars 5.5+ (optional) — enables `/practice rush import` and the mirrored
  item shop for the rush mode
- ProtocolLib 5.3+ (optional) — renders the PvP bot as a real player model
  wearing your skin; without it the bot is a husk scaled to player height

## Commands

| Command | Permission | Description |
|---|---|---|
| `/practice join [arena]` | `practicecore.use` | Start practicing |
| `/practice leave` | `practicecore.use` | Restore state and return |
| `/practice spectate [player\|leave]` | `practicecore.spectate` | Watch someone practice (bare opens the picker) |
| `/practice menu [menu]` | `practicecore.menu` | Open the GUI (or one menu directly) |
| `/practice list` | `practicecore.use` | List arenas |
| `/practice top [arena]` | `practicecore.leaderboard` | Leaderboards |
| `/practice stats [player]` | `practicecore.stats.other` for others | Personal bests |
| `/practice sidebar` | `practicecore.use` | Show/hide the live timer |
| `/practice setup …` | `practicecore.setup` | Arena configuration wizard |
| `/practice edit <arena>` | `practicecore.setup` | Reopen a saved arena |
| `/practice rush import\|importall\|list` | `practicecore.setup` | Pull rush maps from MBedwars (one, or all matching a shape) |
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
It is a parent of `practicecore.use`, `practicecore.menu`,
`practicecore.leaderboard` and `practicecore.spectate` (each of which defaults
to false on its own), so
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

Everything below can also be done from a menu: `/practice setup gui` lists
every arena (left-click edits, right-click deletes, the anvil creates a new
one) and, while the wizard is open, shows a control panel with a button for
each step — text answers like the display name are asked for in chat. That
GUI is intentionally **not** configurable: it reads neither `messages.yml`
nor `guis.yml`, so a half-edited config can never break the tool you would
use to fix it.

1. Build your arena anywhere (even another world). Include the start island,
   the gap, and the finish island. **Don't** place the finish button yet.
2. Select it and run `//copy` (stand somewhere sensible — the copy point
   becomes the paste origin).
3. `/practice setup start <name>` — the schematic is saved, pasted in the
   practice world, and you're teleported there in creative.
4. Stand on the start island facing the gap: `/practice setup spawn`
5. Place **buttons or pressure plates** where runs should finish — as many as
   you like; touching any one of them ends the run (the plugin stamps them
   onto the arena after every paste — they don't need to be in the
   schematic). `/practice setup trigger clear` removes them all to start over.
6. Optional: arrange your inventory exactly as players should receive it
   (e.g. 2×64 wool) and run `/practice setup kit`
7. `/practice setup save` — the template goes live immediately.

Templates live in `plugins/PracticeCore/templates/<name>/` as `arena.schem` +
`arena.yml`; they can be copied between servers as folders. Put an arena
folder inside another folder — `templates/<category>/<name>/` — and that
folder's name becomes its menu category (see below).

Optional polish, either mid-wizard or on a saved arena:

| Wizard | Saved arena | Effect |
|---|---|---|
| `/practice setup display <text…>` | `/practice arena display <arena> <text…>` | Name shown in menus |
| `/practice setup icon [material]` | `/practice arena icon <arena> <material\|auto>` | Menu icon (defaults to the kit's main block) |
| `/practice setup permission <node\|none>` | `/practice arena permission <arena> <node\|none>` | Gate the arena |
| `/practice setup blocks <true\|false>` | `/practice arena blocks <arena> <true\|false>` | Require a placed block for a PB |
| `/practice setup mode <id>` | — | Which `Mode` the arena belongs to |
| `/practice setup category <name\|default>` | move the folder | Which menu group it is listed under |

## Editing a saved arena

`/practice edit <arena>` pastes the arena into a fresh grid slot, stamps its
finish triggers back in, and pre-loads **every** setting — so any single part
can be changed without redoing the rest. An admin who is mid-run when they open
the editor is taken out of their arena **in place**: no bounce back to the
lobby and no second teleport. Their original snapshot is kept rather than
re-captured, so closing the wizard still returns them to where they were before
any of it started — never to a kit-wearing pose inside an arena that no longer
exists.

- Move the spawn: stand where you want it, `/practice setup spawn`
- Change the finishes: place more buttons or pressure plates (each one is
  added), or `/practice setup trigger clear` and start fresh
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
and restored to where it came from first, the wizard is canceled, and anyone
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
| `guis.yml` | `config-version` | `GuiConfig.steps` |
| `templates/[<category>/]<name>/arena.yml` | `config-version` | `ArenaTemplate.migrate` |
| `playerdata/<uuid>.yml` | `data-version` | `StatsStore.migrate` |
| `snapshots/<uuid>.yml` | `data-version` | version-checked on restore |

The admin-editable YAML files share one engine, `config/YamlMigrator`.

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
`DENY` so existing servers keep the behavior they had. `arena.yml` v1 → v2 is
another: the single `trigger:` section became the `triggers:` list when arenas
gained multiple finish triggers — old files migrate to a one-entry list.

## Building

```
./gradlew build      # → build/libs/PracticeCore-<version>.jar
```

## Roadmap

- More modes via the `Mode` registry (clutch practice, pearl practice,
  fireball jumping — the mode hook surface now covers custom triggers, kits
  and per-mode boards)
- SQLite storage backend with full run history (the YAML store already backs
  in-memory leaderboards; history is what it lacks)
- Warm-pool pre-pasted arenas for join bursts; staggered "dissolve" reset
  animation
- Marker-block template import (spawn/finish baked into the schematic)
- Holographic leaderboard signs/armor stands in the lobby world
