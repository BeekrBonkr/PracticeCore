# PracticeCore

<div align="center">

<a href="https://ko-fi.com/bkrbnkr"><img alt="Support me on Ko-fi" src="https://img.shields.io/badge/Ko--fi-buy_me_a_coffee-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white"></a>

</div>

A modular practice minigame plugin for Paper 1.21+. Five modes ship in the
box, **bridging**, **bed breaking**, **rush**, **MLG clutching** and an
**AI PvP bot**, and
every player gets their own ephemeral schematic-built arena in a self-cleaning
void world, a millisecond-honest timer, and per-arena personal bests.

Drop the jar in and it works, ready-to-play arenas ship inside it: the
bridging arena unpacks itself, and the bedbreak shafts and the MLG tower are
built block-by-block by the plugin on first start.

Licensed **GPL-3.0** (see `LICENSE`), chosen deliberately so architecture and
code from the GPL ecosystem of reference plugins (SpeedBridge2, Infinite
Parkour) can be adapted where useful.

At a glance, the five modes:

- **bridging**: run the course and hit the button at the end; timer, personal
  bests and a speedometer above the hotbar.
- **bedbreak**: dig through a reshuffled-but-fixed set of defense blocks to a
  bed, vertically or horizontally; tracks tool-switch reaction time too.
- **rush**: an empty bedwars map (hand-built or imported straight from
  MBedwars) with every objective armed: enemy bed, emerald, diamond, first one
  wins, each with its own leaderboard.
- **beddefense**: build a saved bed defense at your base against the clock,
  from a gallery of player-designed defenses with likes, favorites, a
  block-by-block preview, guided building and an in-world editor.
- **mlg**: water-bucket clutches from a random drop height; the score is your
  streak.
- **pvpbot**: an endless spar against an AI opponent that strafes, combos,
  rods, bows, blocks and builds, with named difficulties from Rookie to Suffer.

## Table of Contents

- [How it works](#how-it-works)
- [The modes](#the-modes): [bridging](#bridging) · [bedbreak](#bedbreak) ·
  [rush](#rush) · [beddefense](#beddefense) · [mlg](#mlg) · [pvpbot](#pvpbot) ([Tuning the bot](#tuning-the-bot))
- [Spectator mode](#spectator-mode)
- [Configuration](#configuration)
- [Messages](#messages)
- [The bundled arenas](#the-bundled-arenas)
- [The menu item and GUI](#the-menu-item-and-gui)
- [Requirements](#requirements)
- [Commands](#commands)
- [Permissions](#permissions)
- [Creating an arena template](#creating-an-arena-template)
- [Editing a saved arena](#editing-a-saved-arena)
- [Reload and world regeneration](#reload-and-world-regeneration)
- [Config validation](#config-validation)
- [Config versioning](#config-versioning)
- [Building from Source](#building-from-source)
- [Known limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Support](#support)
- [License](#license)

## How it works

- On every plugin enable the practice world's folder is **deleted and
  recreated** (void chunk generator, auto-save off, and the gamerules,
  difficulty and time of day from `config.yml`, by default natural mob
  spawning/insomnia/weather/daylight off and difficulty NORMAL, so
  plugin-spawned hostiles like the PvP bot exist and hit). Nothing in it ever
  persists, crash recovery for the world is free.
- Joining players are assigned a slot on a **square spiral grid** (default
  1000-block spacing); their arena schematic is pasted there, then they're
  teleported (async) and given the template's kit. Freed slots are reused
  lowest-index-first.
- The player's full prior state (inventory, location, gamemode, XP, effects,
  flight, …) is **snapshotted to disk** before their inventory is touched. Any
  exit path, command, teleport by another plugin, quit, kick, crash, /reload,
  funnels through the same restore. A player logging in with an orphaned
  snapshot gets it restored automatically.
- Resets don't re-paste: every placed block is **tracked and reverted**, which
  is near-instant. Full erase + re-paste happens only when a slot is recycled.
- Timer starts on first movement off the spawn block (or first block placed,
  configurable), runs on `System.nanoTime()`, shows tenths live on the
  scoreboard (FastBoard, flicker-free) and exact milliseconds on finish.
- Every personal best also lands in an in-memory **leaderboard** per arena,
  built once from `playerdata/` off-thread on enable and maintained
  incrementally afterwards, ranks are exact and free to read.
- Players can **switch arenas** straight from one to another. The new arena is
  fully validated and built before the old one is torn down, so "you can't join
  that" never costs someone the arena they were in; and the switch keeps their
  original snapshot, which is what eventually gets restored. Joining the arena
  you are already in restarts the run.
- **Leaving by teleport is a first-class exit.** Any teleport that actually
  lands outside your arena, `/spawn`, `/tpa`, a warp, another world, ends the
  session and restores everything except your location, letting the destination
  win. That check runs at `MONITOR` on the teleport, so a teleport some other
  plugin cancels can never end a run that never moved.

## The modes

A template names its mode in `arena.yml` (`mode:`), set during creation with
`/practice setup mode <id>`. Mode-specific tuning lives in the template's
`settings:` section, every generated arena writes its defaults there, so the
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

- `VERTICAL`: a column above the bed inside a sealed barrier shaft; you spawn
  on top and dig straight down. One block per step.
- `HORIZONTAL`: a wall filling a sealed corridor in front of the bed; you dig
  forward through it. Each step is a two-high column of the same material, so
  the only way to the bed is straight through.

The mode also tracks **reaction time** as its own stat: whenever finishing a
step means the next one needs a different tool, the clock runs from the
moment the step breaks until you switch to that tool (already holding it
counts as a zero, pre-switching is the skill being measured). Consecutive
steps of the same material, or sharing a best tool, never count. The run's
average shows on the sidebar and is persisted per arena
(`reaction-last-ms` / `reaction-best-ms` in playerdata).

Rearranging the tools in your inventory is remembered **per player**: however
your hotbar was ordered when the run ended is how the kit is handed to you
next time.

`settings.bedbreak`: `orientation`, `bed-x/y/z` (bed head, relative to the
paste origin), `bed-facing` (also the direction a horizontal wall extends
from the head), `bed-material`, and `blocks`, a `MATERIAL: count` map; one
step per count, rising from one block above the bed's head (vertical) or
extending from the head (horizontal).

### rush

An empty bedwars map with reset conditions: you spawn at a team base of your
choice and race to an objective. **Every objective the map supports is armed
on every run**, enemy beds are standing, and an emerald and a diamond sit
waiting on their generators; whichever you complete first ends the run, no
choosing beforehand. Each map+objective pair still keeps its own personal
bests and leaderboard (stored under `map#bed`, `map#emerald`, `map#diamond`),
so a 6-second diamond grab never shares a board with a 40-second bed rush,
and the sidebar shows your best on all three at once.

The timer always starts the moment you first move off the spawn,
`timer.start-mode` does not apply to rush. Runs come in two flavors:

- **Competitive**: one button in the rush menu, instant start, and the only
  way times are recorded and ranked. The loadout is pinned so every entry on
  a board raced the same conditions: no starting items, bed defenses on (the
  preset id in `rush.competitive-defense`, default `endstone`, wool inside,
  end stone out), base generators running.
- **Casual**: your own mix of the difficulty modifiers below. Nothing is
  written to stats: no time, no finish count, no personal best; the finish
  message still shows your time and the action bar reminds you it was
  unranked.

Either way each map keeps its three leaderboards, bed rush, emerald rush
and diamond rush, fed only by competitive runs.

Maps come from two places:

- **MBedwars import**: `/practice rush import <mbedwars-arena>` captures the
  arena's region as a schematic and resolves everything else from the MBedwars
  data: team spawns, bed positions, every item generator and the shop dealer
  spots. The map is playable immediately, under the Rush category. Re-import
  with `overwrite` after map changes; times and settings survive.

  **Mass import**: `/practice rush importall teams:<n> size:<n>` pulls every
  MBedwars arena matching that shape in one go, either filter alone or both
  together (`teams:4 size:2` = four teams of two). The batch is filed into one
  `templates/<category>/` folder so it groups in the menu, named after the
  filter (`4x2`, `8-teams`, `solo`/`doubles`/`triples`/`quads`) or set
  explicitly with `category:<name>`; give it an icon and display name in `guis.yml`
  (`categories.entries`). Existing arenas are skipped unless `overwrite` is
  appended. `/practice rush list` shows each arena's shape.
- **By hand**: build or `//copy` a map, `/practice setup start <name>`,
  `/practice setup mode rush`, then walk the map placing markers:
  `rush team <color>` (stand at that base's spawn), `rush bed <color>` (look at
  that team's bed), `rush gen <iron|gold|diamond|emerald>` (stand on the
  spawner block), `rush dealer` (shop NPC spot), `rush clear` to start over.
  At least one team needs both a spawn and a bed to save.

Clicking a rush arena in the GUI opens the **rush setup menu** instead of
joining straight away. It reads top to bottom as the order the choices are
made: the **team base** on the first row, the five **match modifiers** on the
second, the **defender lineup** on the third, and **Start Casual** / **Start
Competitive** on the fourth. The modifiers are **starter blocks**
(none/16/32/64 wool), **starting resources** (iron/gold to spend
immediately), **pickaxe tier** (none → diamond), **bed defenses** and whether
the **iron/gold generators** run, every team base produces, exactly like a
real game, with a per-generator item cap so idle bases don't flood. The
lineup buttons (count, difficulty, armor, sword) appear as soon as the
defender count leaves zero. Hovering **Start Casual** lists the whole match,
so nobody has to check a row of buttons to see what they are about to play.
Every choice (including which mode you last played) is remembered per player
a plain `/practice join <map>` replays your last setup.

**Bed defenses** are the pyramid a real bedwars player builds, not a box: the
footprint is widest at bed level and loses a ring for every block of height,
tapering to a cap over the bed. The defense button opens a gallery of presets
wool, wood, terracotta, wool + end stone, wool + glass, obsidian + end
stone, and heavier ones up to a four-layer Keep, each tile spelling out what
it is made of, layer by layer, outermost first. The gallery is
`rush.defense-presets` in `config.yml` and it is yours to curate: a preset you
delete stays deleted, and one you add shows up in the picker. Each is just a
list of materials, **innermost first**, the first touches the bed, the last
is the skin a rusher meets, and the length of that list is how far the
pyramid reaches out and up. Only air is ever written, and never outside the
map's own bounds, so a bed tucked under a roof simply gets a smaller pyramid.

During a run the **mirrored MBedwars shop** is open for business: villager
NPCs stand at the map's dealer spots and sell the real MBedwars item shop,
same pages, icons, items, prices and slot layout, settled directly against
your inventory, no MBedwars game involved. Purchased wool follows your
wool-color setting, and auto-wear armor equips itself just like the real
shop. **Special items** resolve to the exact stacks MBedwars sells and the
ones that make sense solo are fully emulated: **fireballs** launch on right
click, **TNT auto-ignites** on place, the **bridge egg** trails a wool
bridge, the **rescue platform** builds a slime disc underfoot and the mini
shop opens the shop; explosions break only blocks you placed and generated
bed defenses (never the map or a bed), with proper bedwars knockback for TNT
and fireball jumps. The **teleporter**, **tracker**, **TNT sheep** and
**guard dog** work too. Special items that genuinely need a real multiplayer
game around them (traps, magic milk, magnet shoes, …) are still sold but say
so when used.

Generator items and shop stock are exempt from the inventory validator (rush
manages its own economy); everything else about the session is standard
machinery: full reset on death or void fall, blocks revert, beds and defenses
are re-placed, NPCs and items respawn, and every storage block in the arena
(team chests included) is emptied so nothing stashed survives a death. Ender
chests stay closed during sessions, their contents are real player data that
would leak out of practice.

MBedwars is a **soft dependency**: importing and the shop need it installed;
hand-built rush arenas play fine without it (dealer clicks then explain the
shop is unavailable). Generator pacing is configurable under `rush:` in
`config.yml` (`iron-interval-ticks`, `gold-interval-ticks`,
`generator-item-cap`, `base-generators-default`).

### beddefense

Bed defense practice: you spawn at a team base of a rush map, your own bed
standing, and build a saved **bed defense** around it as fast as you can. The
mode has no arenas of its own, every rush map is a bed defense map, and the
Play menu lists it as its own **Bed Defense** category (with categories off,
a button on the flat arena list). Picking a map opens the bed defense setup
menu, not the rush one.

The defenses are designed by players, not shipped: the plugin comes with none
on purpose, and the first player in is put straight into the **editor** to
build one. A defense is the blocks placed within ten blocks of the bed, in
the order they were placed, stored relative to the bed so it fits any base on
any map, rotated with the bed's facing. When a defense is chosen for a round
its footprint is **carved out of the map**, so a design made on an open
island fits a bed tucked against a wall.

**A round is complete** when every block of the defense stands with the right
kind of material at the right spot, in any order. Kinds, not exact blocks:
any wool is wool, any planks are planks, any terracotta is terracotta, so
your wool-color setting and shop purchases all count. Only water **source**
blocks count (flowing water never does), and a waterlogged ladder is a
ladder.

Two ways to play, chosen in the setup menu:

- **Competitive**: a real match opening: sword, team-dyed leather, the
  base's iron and gold generators and the mirrored MBedwars shop; blocks are
  bought. When the defense needs obsidian an emerald generator runs on your
  own base gold spawner, so obsidian never costs a trip out to the middle.
  The timer starts on first movement, shuffle is off, and these are the only
  rounds ranked against other players. Without MBedwars there is no shop to
  buy from, so competitive plays as practice and says so.
- **Practice**: the same start with the defense's exact blocks already in the
  kit (one water bucket per water block). Rearrange the kit once and it deals
  the same way every time. Practice keeps a personal best of its own, shown
  on your sidebar and in `/practice stats`, but it is never ranked or
  broadcast — the blocks are handed to you, so the times are not comparable
  with competitive ones. Extras: **shuffle** (a different defense every
  round from your favorites or the public gallery) and **timer start** (first
  movement or first block).

Boards are kept **per defense**, not per map: `beddefense#<id>` is the ranked
competitive board and appears under its own category in the leaderboards
menu, while `beddefense#<id>#practice` holds your private practice bests.
Competitive records and personal bests broadcast exactly like every other
mode; practice ones stay with you.

**Preview**: drop any item (or use the bed defense item's menu) before an
attempt starts and the defense assembles itself block by block in front of
you while you fly around it, with hotbar items to play, pause, step forward
and back, switch to guided building, or leave. A hologram over the bed says
so at the start of each round and fades after a few seconds or when you walk
up. Nothing can be placed or broken during a preview.

**Guided building**: the next block in the designer's order floats over its
spot, glowing and blinking, until you place it. Untimed. Dropping an item
*mid-attempt* switches to guided building instead of a preview, the timer
is cancelled but the blocks you already placed stay, and the preview hotbar
has a guided item too. Coming from a competitive round you are handed the
blocks. Finishing a guided build resets for a timed attempt.

**The editor**: from the setup menu (a fresh bed, or one of your own saved
defenses), from the in-arena menu, or forced on you when no defense exists
yet. You get full stacks of every allowed block that refill as you build:
with MBedwars installed that is every plain block your own item shop sells
(`beddefense.blocks-from-shop`), so a shop stocking end stone bricks hands
out bricks; without it, the `beddefense.blocks` list in `config.yml`. Water
comes as buckets either way. You may place only
within the radius, and break only your own blocks, instantly. The bed
defense item opens the editor menu: **name** (asked in chat), **save** (and
go play it), **load** one of yours, **clear**, **visibility** and **leave**.
Saving refuses a defense that already exists, any published one or one of
your own with the exact same blocks (order ignored), with a title, a chat
line, and clickable **Play it / Like it / Favorite it** actions for the
original. Your own saved defenses can be reshaped under the same id, keeping
their likes and boards.

**The gallery** (the setup menu's defense button, or the in-arena menu) has
three tabs: **Public**, sorted by likes, then by how many different players
have built it; **Mine**; and **Favorites**. Left-click chooses a defense,
right-click opens its actions: like (public, one per player), favorite (your
private bookmark, also a shuffle pool), its boards, and, on your own, edit,
publish/unpublish and delete (two clicks). Admins may delete anyone's.
`/practice beddefense` opens the map picker; `play|like|favorite|publish|
unpublish|edit|delete <id>` and `list` do the same from chat.

Defenses live in `defenses/<id>.yml`, one file each, shared by everyone;
`/practice reload` re-reads them. Tuning is under `beddefense:` in
`config.yml`: the block list, the editor radius, defenses per player, name
length, preview speed, the marker's blink, the hologram, and the hotbar
items.

### mlg

Water bucket clutch practice: you spawn on a glass platform looking straight
down at the landing pad, jump off whenever you're ready, **the platform
breaks away behind you the moment you do**, fall a **random distance**, and
have to place your water before impact. Touching the water is an instant win
and resets the arena; hitting the pad without it fails. The drop height is
re-rolled on every reset, so the fall's length can't be learned by muscle
memory.

There is no timer, the score is the **streak**: consecutive successful
clutches, shown on the sidebar next to your highest ever. It is persisted per
arena in playerdata (`streak` / `streak-best`) and survives arena resets and
relogs; a failed run resets it to zero, and so does bailing out mid-air
(restarting, switching arenas or logging off between jump and splash), so a
fall can never be abandoned to dodge the loss.

The bucket only works **mid-fall**: emptying it while still standing on the
platform is refused, so a pre-placed pool can never count. The clutch water
(and anywhere it flows) is dried back up on every reset.

MLG arenas always allow buckets regardless of `session.allow-buckets`, and
the shaft below the spawn is mode-owned space, the platform and pad are
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
arena against a bot that fights like a 1.8.9 player, it **strafes out of
your crosshair** (the better you track, the harder it works to slip off your
cursor), holds duel spacing, **spam-clicks at a configured CPS** with a
configurable miss chance, goes for **jump-crits and W-tap knockback resets**,
and, each individually toggleable, **rods you at range**, takes **bow
potshots**, raises a **1.8 sword block** when it gets comboed (halving what
lands), and **places blocks to reach you**. Hits it takes put it into a short
**hitstun** where it rides the knockback like a real player, comboing it
works the way it should.

**Building** is how it gets somewhere its legs cannot. Tower up and it
**towers after you**, hopping and sealing the spot underneath itself, riding
its own pillar; put a hole between you and it and it **bridges**, laying one
block ahead of its feet and stepping out onto it. It only builds once walking
has actually failed, a bot with a path is going somewhere and does not stop
to build, and it builds faster the higher its thinking layer, the way a
better player does. Everything it places you can break, and every stock reset
takes the lot back down with the rest of the arena. A per-stock block budget
keeps a long spar from becoming a build-off. Tune it under
`behavior.building` in `pvpbot.yml` (material, budget, placement interval,
how high above you it starts climbing, how deep a hole counts as one worth
bridging), switch it off server-wide with `behavior.building.enabled`, or let
each player switch it off for their own bot with the **Building** toggle.

At the high accuracy tiers the bot also fights with its head (the
**cerebral layer**, on by default for Veteran and above): it **times its
hits to your immunity window**, no wasted clicks, every swing lands the
tick your i-frames expire; it acts on a **perceived position** refreshed at
its reaction speed and dead-reckoned along your motion, so it leads you
instead of chasing your past; it swaps between **stances**, pressing hard
after winning an exchange, and **kiting behind rod chip and sword blocks**
when it's badly losing; it plays the **arena edge** both ways, never
strafing itself over the rim while angling its shoves to walk *you* toward
it; it **reads your habits**, constant hopping gets met with timed
crit-fishing, swinging at air gets whiff-punished with a lunge through the
gap; and it occasionally **feints** a retreat, countering hard if you take
the bait. Lower tiers instead carry honest human weaknesses: a stale
picture of where you are and no rhythm to their clicks.

With **ProtocolLib** installed the bot renders as a **real player model
wearing your own skin** (a packet-level disguise: the server still runs a
husk, clients see a player). Without it, the bot is a named husk scaled
down to player height. Either way its floating tag shows its **live
health**, and its brain is entirely this plugin's, no NPC dependencies.

There is **no timer and no leaderboard**, the sidebar keeps session stats
instead: the arena, your kit, the **bot's difficulty**, kills, deaths, hits
landed/taken, current and best combo, and the bot's **name tag doubles as
its health bar**. Every real hit on the bot pops
a **floating damage indicator** (a DeluxeCombat-style hologram showing the
health points dealt, drifting upward before vanishing). Dying never shows a death
screen: the fatal hit is intercepted, it costs a **stock**, and you spend a
**3-second respawn hold** pinned where you fell, blind, untouchable and
unable to walk, the **"YOU DIED" title counting the respawn down**. The
**teleport to your spawn is the respawn itself**: it lands as the countdown
hits zero, together with the heal, the fresh kit, the bot's own reset and a
"FIGHT" title. Killing the bot gets its own title with your K/D; its body
vanishes for its own 3-second timer, **counted down above your hotbar**, and
when it comes back **you are put back on your spawn with it**, so a kill
can never be turned into a spawn camp. Either way a **chat line sums up the
exchange** with the health the survivor had left (in health points,
half-hearts, out of a normal max of 20). Getting knocked off the arena is a
**ring-out**, same thing.

Kits come from the **kit gallery**, defined in `pvpbot.yml`. Fourteen ship
with the plugin, Sword, NoDebuff, Boxing, BuildUHC, Combo, Gapple, Iron,
Diamond, Classic, Soup, Archer, Axe, Bedwars and Skywars, some carrying
**blocks to place** (BuildUHC, Bedwars, Skywars; every stock reset reverts what
you built). They are data, not code: retune one, drop one, or add your own, and
the gallery follows.
Left-click a tile to select, **right-click for a full preview** laid out
exactly like your inventory (armor up top, hotbar at the bottom). The plugin
**remembers the last kit you chose** and hands it to you on your next join,
**remembers how you arranged its items** (per preset, rearrange your
NoDebuff hotbar once and every future deal matches it), and keeps
consumables (pots, gapples, arrows, blocks) **topped up** so a spar never
runs dry. The practice menu item always keeps a hotbar slot unless the kit
truly needs all nine.

The bot is configured from the **practice menu**: while sparring, a **Bot
Settings** entry appears next to the regular settings, the kit gallery, bot
gear (mirror your kit, or a fixed tier), a named **difficulty preset**
(**Rookie → Brawler → Veteran → Demon → Unfair → Suffer**, one click setting every
AI knob; hand-tuned mixes read as Custom), plus the individual knobs,
evasiveness, CPS, accuracy, crits/W-taps, reach, aggression, and the
rod/bow/block/build toggles. The near-top tier of two knobs is Demon's own:
**extreme** evasiveness (fastest strafe, hops mid-fight, shrugs off hitstun
sooner and escapes long combos more often) and **frenzied** aggression
(fastest approach, tightest spacing, closes distance with sprint-jumps like
a player holding W and spamming space), every tier cycles like any other
value, so they can be mixed into custom setups too.

Demon is also where the bot stops swinging like a mob and starts duelling
like a person. Its perfect accuracy switches on the **duellist layer**:
**reach discipline** (it holds the tip of its own reach instead of hugging
you, and gives ground while your immunity window burns), a **combo
follow-up** (a clean hit opens a short window where it travels with its own
knockback at a sprint rather than waiting for you to drift back into
range), **s-tapping** (the backward tap as a hit lands, eating part of the
knockback and shortening its own hitstun), **block-hitting** (with the block
toggle on, swinging with the sword still raised and dropping back behind it
after a clean hit), and **jukes**, the strafe side flips on its own beat
and the approach weaves instead of walking a straight line in. Every one of
these is honest at Demon: enough that spamming clicks stops working, not
enough to be unfair. Which is the next tier's job.

Above Demon sits **Unfair**, for the insane: four knobs gain an **unfair**
tier and the accuracy knob's unfair setting switches on a gloves-off layer
above cerebral. It strafes faster than Demon and *darts* sideways the instant
your crosshair settles on it, hops more often, slips out of combos far more
reliably, re-reads the fight more often and starts kiting before it is actually
desperate, feints, whiff-punishes and crit-fishes more freely, leans on the rod
harder in neutral, w-taps or crits on more of its clean hits, and angles every
shove harder toward the rim.

It takes every technique of Demon's duellist layer past honest. Its **reach
discipline** holds further out and steps clean out of *your* range while your
immunity burns, so a wasted click is thrown at air; its **combo follow** runs
noticeably longer; it **s-taps** about half the hits it takes rather than a
quarter; it **block-hits** more freely; and it **jukes** on a distinctly faster
beat, so a crosshair that has learned the rhythm is already wrong.

Both Demon and Unfair are deliberately tuned to be *beatable*: they miss
sometimes, they react a tick late, and they ride enough hitstun to be comboed.
Every one of those numbers is a line in `pvpbot.yml`, see
[Tuning the bot](#tuning-the-bot), so if they land wrong for your server, move
them rather than living with them.

What it deliberately does **not** do is cheat the numbers: reach stays capped
at long (3.4), CPS is still tick-capped, its aim still misses now and then, it
still reads you a tick late, and hitstun still applies, so wall combos, rod
resets and edge discipline beat it.

The bot also **crouches strategically**: while sneaking it takes **reduced
knockback** (and visibly sinks into the sneak, on the player model too), at
the price of slower strafing and no jumps. A cerebral-tier bot (Veteran and
up) shift-anchors when it is cornered against the rim with you on top of
it; the Unfair tiers additionally answer a building combo with a short
crouch so the chain never carries them across the arena, deliberately
absent below Unfair, where combos staying earnable is the point.

And above Unfair sits **Suffer**, the same dirty playbook with every dial
at its ceiling, while movement and combat stay strictly inside a player's
physical envelope (no extra reach, no extra CPS, barely any extra speed;
only the decisions are inhuman). It re-reads the fight near-continuously
and starts kiting the moment the trade math tips, slips combos at 90% and
turns every slip into an immediate counter-offensive, darts harder off a
settled crosshair and hops constantly, whiff-punishes almost every wasted
click, crit-fishes and feints and max-range-baits at will, w-taps or crits
on nearly every clean hit, and shoves hardest of all toward the rim. The
duellist layer is dialed to its ceiling with it: it s-taps almost every hit
it takes, block-hits more often, chases its combos a beat longer, and steps
a little further out of your reach while your immunity runs. It is
the honest ceiling of the engine: everything Suffer does, a human could do
once, it does all of it, every exchange, forever.

The bot also keeps an **AFK watch** at every difficulty: if your position,
aim and clicks all freeze for a few seconds, it stands down, stops
attacking, stops closing, and just stares, then resumes the moment
anything stirs (moving, looking, or clicking), with a short grace beat so
returning from AFK never costs a free hit. **The bot freezes and
holds fire while any menu is open** and re-reads everything on close; every
choice is persisted per player. Combat *mechanics*, 1.8 cooldowns,
knockback shaping, the player's own sword-block damage halving, are left to
the server's combat plugins (OldCombatMechanics, vanilla-sword-blocking),
with one correction: modern servers treat a raised block-animation sword as
a *shield* that negates hits entirely, so the mode strips that full negation
and restores the true 1.8 behavior (blocking halves damage, on both sides).

#### Tuning the bot

Every number the bot runs on lives in `pvpbot.yml`, and `/practice reload`
applies it to fights already in progress. Two ideas run through the file.

**Tiers keep their names, not their numbers.** `EXTREME` evasiveness is always
called `EXTREME`, saved player preferences and message keys never move, but
how fast it actually strafes is a line under `tiers`. That is what makes
retuning a difficulty safe while people are playing. The tier *names* are fixed
(they are what the GUI cycles and what playerdata stores); to change what
players see one called, edit `gui.pvpbot.*` in `messages.yml`.

**Behaviors scale by layer.** The brain has four layers on top of its plain
behavior, cerebral, duellist, unfair, suffer, and which accuracy tier unlocks
each is itself configurable under `layers` (including `never`, to switch a
whole layer off server-wide). Any knob under `behavior` may be one value, or a
block that scales:

```yaml
combo-escape:
  chance:
    base: 0.4        # every bot
    unfair: 0.6      # …unless it has the unfair layer
    suffer: 0.9      # …or suffer
```

The best layer a bot qualifies for wins. Write the knob as a single number and
it stops scaling at all. Windows written as `{min: x, max: y}` are re-rolled
every time they are used, that is deliberate, and it is why the bot does not
fall into a rhythm a player can learn.

The rest of the file covers the bot's body (`bot`: respawn hold, refill
cadence, AFK threshold, grace beats, follow range, jump velocity, knockback
factors, damage indicators), the named difficulties (`presets`, reorder,
retune, rename or add), the gear tiers, and the per-player defaults a bot
starts on before anyone opens the settings menu.

`kits` and `presets` are **curated**: an entry you delete stays deleted rather
than reappearing on the next start.

Building an arena needs just a platform and a spawn: `/practice setup start
<name>`, `/practice setup mode pvpbot`, `/practice setup spawn`, optionally
`/practice setup pvpbot bot` standing where the bot should spawn (without a
marker it spawns a few blocks ahead of the player spawn, facing back), then
`save`. No kit and no trigger are required.

## Spectator mode

`/practice spectate` (also `spec`, `watch`) opens a picker of everyone
currently practicing, one head per player, and clicking one drops you into
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

Spectators are leashed to the arena they are watching, drift more than a
dozen blocks past its walls and you are pulled back to the target, which also
auto-follows them through arena switches. Leaving spectator mode (the bed,
`/practice spectate leave`, or the watched player stopping) restores you and
then drops you straight into the **default arena**, on a practice server
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
session and no grid slot, so they cost nothing and count toward no limits,
nothing needs to be sized for them.

## Configuration

Everything an admin can change lives in five YAML files under
`plugins/PracticeCore/`, and every one of them is re-read by
`/practice reload`, see [Reload and world regeneration](#reload-and-world-regeneration).

| File | What it controls |
|---|---|
| `config.yml` | how the plugin behaves: world, grid, session rules, mode defaults, effects |
| `messages.yml` | every piece of text shown to a player |
| `guis.yml` | menu *structure*: which slot each button sits in, its icon, row counts |
| `sounds.yml` | every sound the plugin plays |
| `pvpbot.yml` | the PvP bot's difficulty tuning, its named presets, and the kit gallery |

Each is heavily commented, the file itself is the reference, and this section
only covers what is worth knowing before opening one.

**`config.yml`** has a section per subsystem. The parts most worth knowing
about:

- `world.gamerules` is an open list, matched against the server's own registry
  rather than a fixed set, a gamerule added by a later Minecraft release can
  be set here without waiting for a plugin update, and one this server does not
  know is reported in the console and skipped. `world.difficulty` and
  `world.time` sit beside it. All three are pushed onto the live world on
  reload.
- `session` carries the arena rules *and* the protection switches that keep the
  practice world inert, pistons, crafting, ender chests, vehicles, elytra,
  hunger, item drops. Each one has a comment saying what it was protecting
  against; turn one off knowing that.
- `mlg` and `bedbreak` hold the **server-wide defaults** for those modes. An
  arena's own `settings:` block in its `arena.yml` overrides whichever keys it
  names, so you can retune every MLG arena at once and still let one of them
  disagree.
- `rush` covers the generators, the emulated MBedwars combat items (TNT fuse,
  fireball power, explosion knockback, the bridge egg, the rescue platform) and
  the dealer's villager profession.
- `spectate` can be switched off entirely, and carries the leash margin and the
  three hotbar tools' materials and slots.

**`sounds.yml`** names each cue by what it *means*, `run.finish-pb`, not
`ENTITY_PLAYER_LEVELUP`, and maps it to `SOUND [volume] [pitch]`. Set one to
`''` to silence just that cue; `effects.sounds: false` in `config.yml` silences
all of them. Sounds resolve through the server's registry, so either spelling
works (`ENTITY_PLAYER_LEVELUP` or `minecraft:entity.player.levelup`) and a name
this server does not know is reported once rather than throwing.

**`pvpbot.yml`** is the largest of them, and is documented in full under
[the pvpbot mode](#pvpbot).

## Messages

Every piece of text the plugin shows a player lives in `messages.yml`,
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
name and lore, which stay in `config.yml` beside its material and slot, they
define the item rather than being something the plugin says.

**Sidebar footer**: set `scoreboard.server-ip` in `config.yml` and every
board, every mode, signs off with your server's address on its bottom
line, styled by the `board.footer` lines in `messages.yml` (a gold gradient
by default). While it stays `''`, no footer renders at all.

Anything that takes more than an instant tells the player so as it starts,
and it says so **on a title**, a queued action that goes quiet, mid-teleport,
reads as a frozen client, and the action bar is too easy to miss at exactly
that moment. Joining puts up *Loading, Building &lt;arena&gt;…* and holds it
until the player is actually standing in the arena; the setup wizard does the
same while it pastes. Both keep their action bar line as well, and imports and
world regeneration announce themselves in chat, a command that goes quiet
mid-work reads as a broken server.

A title only appears if the work is **still running** after
`effects.title-delay-ticks` (default half a second), so the common instant
join flashes nothing at all, and it comes down when the work reports back
rather than timing out, `effects.title-hold-ms` is only the backstop for a
wait that never does, so keep it comfortably longer than your slowest paste.
Blank both lines of a pair in `messages.yml` (`session.building-title` /
`-subtitle`, `setup.pasting-title` / `-subtitle`) to switch one off entirely.

## The bundled arenas

`bundled-template` in `config.yml` unpacks the bridging arena that ships in
the jar to `templates/<name>/` (default `turtle`) the **first** time the
plugin starts. A marker file records that this happened, so deleting or
renaming the arena does not bring it back on the next restart. Delete the
marker (`plugins/PracticeCore/.bundled-installed`) to reinstall it.

`generated-arenas` works the same way for the bedbreak and MLG arenas, except
nothing is unpacked: the plugin builds each arena block-by-block in code and
writes it out as a normal template folder (defaults `bedbreak` and
`bedbreak-horizontal`, one per orientation, plus `mlg`), fully editable,
deletable, and guarded by its own marker file (`.generated-installed`; remove
a line from it to regenerate that arena). Set a name to `''` or
`generated-arenas.enabled: false` to skip them.

## The menu item and GUI

`/practice item` gives an admin the hotbar menu item, a tagged item (a
persistent-data key, not a name match, so look-alikes can't spoof it). Include
it when you `/practice setup kit` and it is handed to every player who joins
that arena. Wherever a kit was saved with it, the item is **normalized into
`menu-item.slot`** (default 8, the last hotbar slot) on every deal, swapping
whatever sat there into its old spot, so it always shows up in the same
place. `menu-item.force-in-kit` retro-fits it into arenas built before the
item existed, relocating (never destroying) the slot's occupant; a kit that
genuinely fills all 36 slots wins, and the menu stays reachable via
`/practice`. Right-clicking it opens:

- **Play**: a category picker (one tile per arena category, each with its
  own menu), then the arena picker: filtered by the same permission check the
  join command uses, showing your best, your rank and the arena record per
  entry. An arena's category is **the folder its folder sits in**:
  `templates/<category>/<arena>/` is listed under `<category>`, and an arena
  straight in `templates/` is listed under its mode id. Re-categorising is a
  drag-and-drop plus `/practice reload`, or `/practice setup category
  <name|default>` during the wizard, which moves the folder for you. Give a
  category an icon and display name under `categories.entries` in `guis.yml`;
  turn `categories.enabled` off there to go back to one flat list.
- **Random Arena**: straight into one of the arenas you can play
- **Leaderboards**: per-arena top times, your standing, and the gap to the
  player one place ahead
- **My Stats**: every arena you've finished, ranked
- **Restart Run**: reverts your blocks and puts you back on the spawn
- **Spectate**: the spectator target picker: one head per player currently
  practicing (needs `practicecore.spectate`; see [Spectator mode](#spectator-mode))
- **Sidebar**: show/hide the live timer scoreboard (remembered per player)
- **Settings**: per-player, persisted in playerdata: permanent night vision
  while practicing, the color of the wool in your kit (and so of your
  bridges), and a client-side time of day for your arena. Changes apply
  immediately mid-session and are undone when you leave.
- **Help** and **Leave**

Every one of these menus can also be opened directly from chat:
`/practice menu <main|arenas|categories|leaderboards|stats|settings>` (a bare
`/practice menu` opens the hub).

The leave button restores everything and returns you to the world you came
from, or hands you to the proxy server named in `leave.server`, if set.

Two server-wide announcements keep leaderboards alive (`effects` in
`config.yml`): taking the **#1 spot** on an arena broadcasts loudly and plays
a small chime for everyone online (`broadcast-records`), and quietly, beating
your **own personal best** posts a one-line note (`broadcast-pbs`), never on
top of the record shout, and never for a first finish.

Menu **layout** is configurable in `guis.yml`: every button's slot, icon
material and visibility, row counts, the border filler, and the shared
navigation row of the paged menus. All menu text stays in `messages.yml`.

Kits are exact: while practicing, anything in your inventory that is not a
kit item (or your wool recolor of one, or the menu item) is swept back out
on a fixed schedule, and crafting is blocked, `session.validate-inventory`
and `session.validate-inventory-ticks` in `config.yml`.

## Requirements

| Dependency | Version | Required |
|---|---|---|
| [Paper](https://papermc.io/) | 1.21.x (built against the 1.21.1 API) | ✅ |
| Java | 21+ | ✅ |
| [WorldEdit](https://enginehub.org/worldedit) | 7.3+, [FastAsyncWorldEdit](https://github.com/IntellectualSites/FastAsyncWorldEdit) strongly recommended instead, see below | ✅ |
| [FastBoard](https://github.com/MrMicky-FR/FastBoard) | 2.2.1, fetched automatically at runtime via `plugin.yml` `libraries:`, nothing to install | ✅ |
| [MBedwars](https://mbedwars.com/) | 5.5+, enables `/practice rush import` and the mirrored item shop for the rush mode | Optional |
| [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) | 5.3+, renders the PvP bot as a real player model wearing your skin; without it the bot is a husk scaled to player height | Optional |

**FastAsyncWorldEdit is strongly recommended** over plain WorldEdit: with FAWE
detected, arena pastes, arena erases and rush map imports all run off the main
thread, so even an 8-team map loads without a lag spike. With vanilla
WorldEdit those operations must run on the main thread and big maps will stall
the tick loop. Parsed schematics are cached either way, so repeated joins never
re-read the file.

## Commands

`/practice` also answers to `/prac` and `/bridge`.

| Command | Permission | Description |
|---|---|---|
| `/practice join [arena]` | `practicecore.use` | Start practicing |
| `/practice leave` | `practicecore.use` | Restore state and return |
| `/practice spectate [player\|leave]` | `practicecore.spectate` | Watch someone practice (bare opens the picker) |
| `/spectate [player\|leave]` | `practicecore.spectate` | The same flow, one word shorter (alias `/spec`) |
| `/practice menu [menu]` | `practicecore.menu` | Open the GUI (or one menu directly) |
| `/practice list` | `practicecore.use` | List arenas |
| `/practice top [arena]` | `practicecore.leaderboard` | Leaderboards |
| `/practice stats [player]` | `practicecore.stats.other` for others | Personal bests |
| `/practice sidebar` | `practicecore.use` | Show/hide the live timer |
| `/practice setup …` | `practicecore.setup` | Arena configuration wizard |
| `/practice edit <arena>` | `practicecore.setup` | Reopen a saved arena |
| `/practice rush import\|importall\|list` | `practicecore.setup` | Pull rush maps from MBedwars (one, or all matching a shape) |
| `/practice beddefense [play\|like\|favorite\|publish\|unpublish\|edit\|delete <id>\|list]` | `practicecore.use` | Bed defense practice: maps, gallery actions, editor |
| `/practice arena …` | `practicecore.arena` | Administer saved arenas |
| `/practice item [player]` | `practicecore.item` | Get the hotbar menu item |
| `/practice pb reset <player> [arena\|all]` | `practicecore.pb.reset` | Wipe personal bests |
| `/practice world info\|regen` | `practicecore.world` | Practice world management |
| `/practice reload` | `practicecore.reload` | Reload config and arenas |
| none | `practicecore.bypass` | Enter the practice world without a session |

`practicecore.admin` is a parent of every admin node above.

`/practice pb reset` tab-completes **every player the plugin has records for**,
online or not, the name index is built from `playerdata/` on startup and kept
current as players join.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `practicecore.user` | `true` | Everything a normal player needs, parent of `use`, `menu`, `leaderboard` and `spectate` |
| `practicecore.use` | `false` (via `user`) | Join and leave practice arenas |
| `practicecore.menu` | `false` (via `user`) | Open the practice GUI |
| `practicecore.leaderboard` | `false` (via `user`) | View leaderboards |
| `practicecore.spectate` | `false` (via `user`) | Spectate other players' practice sessions |
| `practicecore.stats.other` | `op` | View another player's practice stats |
| `practicecore.setup` | `op` | Create and edit arena templates in-world; import rush maps |
| `practicecore.arena` | `op` | Administer saved arenas (delete, permission, icon, …) |
| `practicecore.pb.reset` | `op` | Wipe personal bests |
| `practicecore.item` | `op` | Give yourself the hotbar menu item |
| `practicecore.reload` | `op` | Reload config and templates |
| `practicecore.world` | `op` | Inspect and regenerate the practice world |
| `practicecore.bypass` | `op` | Enter the practice world without a session (no confinement) |
| `practicecore.admin` | `op` | Parent of every admin node above |
| `practicecore.arena.<arena>` | none | Per-arena gate, see [Per-arena permissions](#per-arena-permissions) |

### The player permission kit

`practicecore.user` is the node a normal player needs, and it is on by default.
It is a parent of `practicecore.use`, `practicecore.menu`,
`practicecore.leaderboard` and `practicecore.spectate` (each of which defaults
to false on its own), so
revoking `practicecore.user` from a group switches the plugin off for them in
one move, while the children stay available for finer control.
`practicecore.admin` is the equivalent kit for every admin node.

### Per-arena permissions

**Arenas are open by default.** Every arena has a node, whatever `permission:`
its `arena.yml` names, or `<arenas.permission-prefix><arena>` otherwise, and a
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

`default-arena.name` in `config.yml`, or `/practice arena default <arena>`,
which writes it for you, names the arena players end up in without asking for
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
each step, text answers like the display name are asked for in chat. That
GUI is intentionally **not** configurable: it reads neither `messages.yml`
nor `guis.yml`, so a half-edited config can never break the tool you would
use to fix it.

1. Build your arena anywhere (even another world). Include the start island,
   the gap, and the finish island. **Don't** place the finish button yet.
2. Select it and run `//copy` (stand somewhere sensible, the copy point
   becomes the paste origin).
3. `/practice setup start <name>`: the schematic is saved, pasted in the
   practice world, and you're teleported there in creative.
4. Stand on the start island facing the gap: `/practice setup spawn`
5. Place **buttons or pressure plates** where runs should finish, as many as
   you like; touching any one of them ends the run (the plugin stamps them
   onto the arena after every paste, they don't need to be in the
   schematic). `/practice setup trigger clear` removes them all to start over.
6. Optional: arrange your inventory exactly as players should receive it
   (e.g. 2×64 wool) and run `/practice setup kit`
7. `/practice setup save`: the template goes live immediately.

Templates live in `plugins/PracticeCore/templates/<name>/` as `arena.schem` +
`arena.yml`; they can be copied between servers as folders. Put an arena
folder inside another folder, `templates/<category>/<name>/`, and that
folder's name becomes its menu category (see below).

Optional polish, either mid-wizard or on a saved arena:

| Wizard | Saved arena | Effect |
|---|---|---|
| `/practice setup display <text…>` | `/practice arena display <arena> <text…>` | Name shown in menus |
| `/practice setup icon [material]` | `/practice arena icon <arena> <material\|auto>` | Menu icon (defaults to the kit's main block) |
| `/practice setup permission <node\|none>` | `/practice arena permission <arena> <node\|none>` | Gate the arena |
| `/practice setup blocks <true\|false>` | `/practice arena blocks <arena> <true\|false>` | Require a placed block for a PB |
| `/practice setup mode <id>` | none | Which `Mode` the arena belongs to |
| `/practice setup category <name\|default>` | move the folder | Which menu group it is listed under |

## Editing a saved arena

`/practice edit <arena>` pastes the arena into a fresh grid slot, stamps its
finish triggers back in, and pre-loads **every** setting, so any single part
can be changed without redoing the rest. An admin who is mid-run when they open
the editor is taken out of their arena **in place**: no bounce back to the
lobby and no second teleport. Their original snapshot is kept rather than
re-captured, so closing the wizard still returns them to where they were before
any of it started, never to a kit-wearing pose inside an arena that no longer
exists.

- Move the spawn: stand where you want it, `/practice setup spawn`
- Change the finishes: place more buttons or pressure plates (each one is
  added), or `/practice setup trigger clear` and start fresh
- Change the kit: `/practice setup kit load` puts the saved kit in your
  inventory, rearrange it, `/practice setup kit` saves it back
- **Reshape the arena itself**: build in place, then `/practice setup capture`
  writes the region back over `arena.schem`. Trigger blocks are stripped out
  of the capture, including any you moved away from, since the plugin stamps
  those in after every paste.
- Swap the build entirely: `//copy` something new, `/practice setup schematic`
- `/practice setup info` prints the pending state; `/practice setup save`
  commits, `/practice setup cancel` leaves the saved arena untouched.

Other arena administration is available without entering the wizard:
`/practice arena list`, `info <arena>`, `default [arena|none]`, and
`delete <arena> confirm`. Deleting
evicts anyone currently in the arena and removes it completely: the folder, its
schematic, its leaderboard, and every recorded time on it across all
playerdata. (The on-disk sweep matters, leaving the times behind would have
the next startup scan rebuild a leaderboard for an arena that no longer
exists.) The sweep runs off-thread and reports how many player records it
cleared when it finishes.

## Reload and world regeneration

`/practice reload` re-reads **every** admin-editable file, `config.yml`,
`messages.yml`, `guis.yml`, `sounds.yml`, `pvpbot.yml` and every arena folder.
The plugin is built so that changing a setting never needs a server restart.

**All or nothing.** Each file is parsed on a throwaway object *before*
anything live is replaced, because Bukkit's own `reloadConfig()` swallows a
syntax error and hands back an empty config, on a live server that would
silently reset every setting to its default. One broken file changes nothing at
all and says why. The previous `PCConfig` is likewise kept until the new one
has been built without throwing, and arenas are collected into a local map and
swapped in only once the whole pass succeeds.

**Runs are only interrupted when they have to be.** Almost everything in these
files describes what happens *next*, text, menu layout, sounds, bot tuning and
kits, mode settings, protection rules, effects, and is simply picked up by
whatever asks for it after the swap. A reload that touches only those applies
live and says so; nobody's run ends.

Four things are different, because they describe arenas that are *already
pasted into the world*:

| Change | Why it interrupts |
|---|---|
| `world.name` | the arenas live in the old world |
| `grid.spacing` | slot positions would move under pasted arenas |
| `grid.base-y` | same, vertically |
| `grid.max-schematic-size` | the size rule the pasted arenas were admitted under |
| an edited `arena.yml` or `arena.schem` | the file no longer describes the copy in the ground |

Only then does the reload stop and ask, listing exactly what changed;
`/practice reload confirm` ends and fully restores those sessions first. Arena
files are spotted by a fingerprint of their size and timestamp, taken fresh on
each reload and committed only once the reload actually succeeds.

Gamerules, difficulty and the fixed time of day are pushed onto the live world
on every reload, so changing one of those needs neither a regeneration nor a
restart. Changing `world.name` is reported as needing `/practice world regen
confirm`; the old world stays in use until you run it.

The one thing a reload genuinely cannot pick up is `plugin.yml`, Bukkit
registers permissions and commands at load time, so adding a permission node
there still needs a restart. Nothing PracticeCore itself owns is in that
position.

`/practice world regen confirm` unloads, deletes and rebuilds the practice
world from nothing, then restarts the grid at slot 0. Every session is ended
and restored to where it came from first, the wizard is canceled, and anyone
still standing in the world is moved to `leave.fallback-world` (or the main
world). Arenas, kits and leaderboards are untouched. `/practice world info`
shows the world, session count, allocated slots and loaded chunks.

## Config validation

Syntax and versions are only half of a config file being *right*. On every
server start and every `/practice reload`, PracticeCore also sweeps the loaded
files for values that parse as YAML but will not resolve in game:

- materials, sounds and villager professions the server does not know;
- misspelled mode, tier and layer names (`timer.start-mode`, the pvpbot tier
  tables, `layers`, `defaults`, the gear sets);
- menu slots outside a chest and row counts that do not fit one;
- MiniMessage that would render as raw text (at play time a broken line
  deliberately degrades to its raw text so nothing breaks, the validation
  sweep is where you actually hear about it);
- numbers a silent clamp would rewrite, and cross-checks no single key can
  see (`grid.max-schematic-size` vs `grid.spacing`, `mlg.min-drop` vs
  `mlg.max-drop`).

Every finding is a warning, never a refusal: each value falls back to its
shipped default either way. The sweep just makes sure that happens as a
console line at boot, and a chat line on reload, instead of as a surprise
discovered mid-fight. Only values you actually wrote are checked, so a clean
install validates clean.

## Config versioning

Every file an admin can edit carries a `config-version`, and every file the
plugin owns carries a `data-version` (see `config/Versions.java`):

| File | Key | Migrator |
|---|---|---|
| `config.yml` | `config-version` | `PracticeCorePlugin.configSteps` |
| `messages.yml` | `config-version` | `Messages.steps` |
| `guis.yml` | `config-version` | `GuiConfig.steps` |
| `sounds.yml` | `config-version` | `SoundConfig.steps` |
| `pvpbot.yml` | `config-version` | `BotTuning.steps` |
| `templates/[<category>/]<name>/arena.yml` | `config-version` | `ArenaTemplate.migrate` |
| `playerdata/<uuid>.yml` | `data-version` | `StatsStore.migrate` |
| `snapshots/<uuid>.yml` | `data-version` | version-checked on restore |

The admin-editable YAML files share one engine, `config/YamlMigrator`, wrapped
by `config/ConfigFile`, which also lays the jar's copy underneath yours as
defaults, so a key you delete by hand still resolves instead of collapsing to
whatever fallback the call site happened to pass.

On startup, an out-of-date file is copied into `backups/` and then upgraded:
renamed keys are moved by the version steps, and every file gets any key the
jar defines but yours lacks, **with the comments that explain it**, so a new
setting arrives documented rather than as a bare line. Values you already had
are never touched. A file stamped **newer** than this build understands is left
strictly alone and reported, rather than being "fixed up" into a downgrade.
Playerdata migrates in memory and stamps itself on the next write, so reading
someone's stats never rewrites their file.

**Curated sections are the exception to that top-up.** Where a file defines a
*collection* rather than a set of settings, the list is yours outright and an
entry you delete stays deleted:

| File | Curated section |
|---|---|
| `pvpbot.yml` | `kits`, `presets` |
| `guis.yml` | `categories.entries` |
| `config.yml` | `world.gamerules` |

(Removing such a section *entirely* still gets you the shipped one back, that
is a lost file, not a decision.)

To add a format change: bump the constant in `Versions`, add the step to that
file's migrator, and ship it. The backup and top-up are automatic. `config.yml`
v1 → v2 is a worked example: it replaced the `arenas.require-permission`
boolean with `arenas.access-mode`, mapping `true` to `ALLOW` and `false` to
`DENY` so existing servers keep the behavior they had. `arena.yml` v1 → v2 is
another: the single `trigger:` section became the `triggers:` list when arenas
gained multiple finish triggers, old files migrate to a one-entry list.

## Building from Source

```bash
./gradlew build
```

Output: `build/libs/PracticeCore-<version>.jar`

Requires Java 21+ (the Gradle toolchain asks for 21 and compiles with
`--release 21`). Every dependency is `compileOnly`, WorldEdit, MBedwars,
ProtocolLib and FastBoard are all resolved from their own repositories at build
time and none of them is shaded into the jar, so `build` is the only task you
need. `plugin.yml` is expanded with the version from `build.gradle.kts`, so a
version bump alone re-processes the resources.

## Known limitations

- **Big pastes stall on vanilla WorldEdit.** Only FAWE lets pastes, erases and
  rush imports run off the main thread, with plain WorldEdit an 8-team map
  paste blocks the tick loop for its duration. See [Requirements](#requirements).
- **Storage is YAML only.** Times, preferences and snapshots live in
  `playerdata/` and `snapshots/` as one file per player. There is no database
  backend, no run history beyond personal bests, and leaderboards are
  per-server, nothing is shared across a network.
- **`plugin.yml` cannot be reloaded.** Bukkit registers commands and permission
  nodes at load time, so those (and only those) still need a restart,
  everything the plugin owns is picked up by `/practice reload`.
- **Rush needs MBedwars for its map import and shop.** Hand-built rush arenas
  play fine without it, but the dealer just explains the shop is unavailable,
  and special items that genuinely need a real multiplayer game around them
  (traps, magic milk, magnet shoes, …) are sold but do nothing in solo practice.
- **The PvP bot's player model needs ProtocolLib.** Without it the bot is a
  named husk scaled to player height. Either way its body is still a server-side
  husk, combat *mechanics* (1.8 cooldowns, knockback shaping, the player's own
  sword-block halving) are left to your combat plugins; the plugin only strips
  the modern shield-style full negation.
- **No timer or leaderboard for `pvpbot`, no timer for `mlg`.** Those modes
  keep session stats and a streak respectively; only bridging, bedbreak and
  rush record ranked times.
- **The practice world is disposable by design.** It is deleted and recreated
  on every enable, so nothing built in it by hand survives a restart, arenas
  live in `templates/`, and that is the only place to edit them.

## Roadmap

- More modes via the `Mode` registry (clutch practice, pearl practice,
  fireball jumping, the mode hook surface now covers custom triggers, kits
  and per-mode boards)
- SQLite storage backend with full run history (the YAML store already backs
  in-memory leaderboards; history is what it lacks)
- Warm-pool pre-pasted arenas for join bursts; staggered "dissolve" reset
  animation
- Marker-block template import (spawn/finish baked into the schematic)
- Holographic leaderboard signs/armor stands in the lobby world

---

## Support

<div align="center">

This plugin is free and open source, and I work on it in my spare time.<br>
If it saved you some time, you can buy me a coffee. No pressure - the code stays free either way.

<a href="https://ko-fi.com/bkrbnkr"><img alt="Support me on Ko-fi" src="https://img.shields.io/badge/Ko--fi-bkrbnkr-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white"></a>

</div>

<!-- more ways to support go here -->
<!-- - [PayPal](...) -->
<!-- - [GitHub Sponsors](...) -->

## License

GPL-3.0, see [LICENSE](LICENSE). The intro explains why: it keeps the door
open to adapting code from the GPL practice plugins this one learned from.
