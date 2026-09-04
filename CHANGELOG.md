# Changelog

Notable changes to PracticeCore. Versions follow the plugin's own numbering;
config file format versions (`config-version`) migrate automatically on start.

## 0.10.0

- **Bed defense practice.** A new mode with a category of its own in the Play
  menu, played on every rush map: spawn at your base, your own bed standing,
  and build a saved bed defense around it against the clock. The defenses are
  player-designed, stored server-wide under `defenses/`, and shared through a
  gallery with Public / Mine / Favorites tabs, sorted by likes and by how many
  different players have built each one. Likes and favorites are separate: a
  like is public and ranks the gallery, a favorite is your own bookmark (and
  a shuffle pool). Boards are kept per defense, not per map, under their own
  leaderboard category.
- **Two ways to play.** Competitive is a real match opening, sword, team-dyed
  leather, base generators and the mirrored shop, blocks bought, emerald
  generators on when the defense needs obsidian, and the only rounds that are
  recorded and ranked. Practice deals the defense's exact blocks (one water
  bucket per water block), with shuffle, a timer-start choice, and a
  strict-order variant that keeps its own boards. Completion is judged by
  block kind at each spot in any order: any wool is wool, only water source
  blocks count, a waterlogged ladder is a ladder. A chosen defense's
  footprint is carved out of the map so it fits any base.
- **Preview and guided building.** Drop any item (or use the bed defense
  item) before an attempt and the defense assembles itself block by block
  while you fly around it, with hotbar controls to play, pause and step.
  Mid-attempt the same gesture keeps your blocks and switches to guided
  building: the next block glows and blinks over its spot until you place it,
  untimed. A hologram over the bed says how, and fades on its own.
- **The editor.** Full refilling stacks of the allowed blocks, a ten-block
  radius around the bed, instant breaking of your own blocks, and a menu to
  name, save, load, clear, set visibility and leave. Saving a defense that
  already exists, anyone's published one or one of your own with the same
  blocks, is refused loudly with clickable play / like / favorite actions
  for the original. The first player on a server with no defenses is put
  straight into the editor.
- **MBedwars lime teams render lime.** MBedwars names its lime team
  `LIGHT_GREEN`, which no Bukkit dye color knows, so imported maps with that
  team showed white wool on the base picker and undyed leather on defender
  bots. Every string-to-color lookup now goes through one helper that folds
  the MBedwars and chat-color aliases onto dye colors.
- **The block list follows your shop.** With MBedwars installed the blocks a
  defense may use are read off the item shop (every plain block product it
  sells), so a shop that stocks end stone bricks instead of end stone gets
  bricks in the editor and in practice kits; `beddefense.blocks` in
  `config.yml` is the fallback without it.
- `config.yml` v7 (`beddefense:` section), `messages.yml` v8 and `guis.yml`
  v5, all additive. The mode SPI gained session-aware timer-start hooks.

## 0.9.0

- **Imported maps wear their MBedwars icons.** Menus now show an imported
  rush arena with the same icon its MBedwars selector uses, resolved through
  the API once per map and cached in memory (cleared by `/practice reload`),
  so the menus never ping MBedwars per redraw. New imports also persist the
  icon into `arena.yml`; existing imports still carrying the old automatic
  red bed pick their MBedwars face up live, no re-import needed, any other
  icon an admin configured stands.
- **Leaderboards are organized by category.** The leaderboards button and
  `/practice top` now open a category picker first, the same grouping the
  Play button uses, with each category opening its own board list. Turning
  `categories.enabled` off in `guis.yml` flattens leaderboards back into one
  menu, exactly like Play.
- **One-click presets on the rush menu.** A strip along the bottom border
  starts a run under a known-good loadout: **Competitive Race** and
  **Competitive Team Wipe** (both ranked), **Bridge Optimization** (64 wool,
  nothing in the way), **Bed Break** (pickaxe, TNT, wool against the standard
  defense), **Warmup** (competitive conditions off the books), **Bot
  Skirmish** (two defenders per base) and **Sandbox** (everything on). The
  casual presets write the dials so the menu shows what they chose; the
  competitive ones touch only the bots toggle and leave your casual dials
  alone.
- **Competitive follows the bots toggle.** Bots off is the classic ranked
  race; bots on is the ranked **team wipe**, pinned to the
  `rush.bots.competitive` lineup so the team-wipe board compares like with
  like. `config.yml` v6 raises an untouched `per-team: 0` to 4; setting it
  back to 0 disables competitive team wipes (and hides their preset).
- **Starter TNT is a match modifier.** A sixth dial on the rush menu (none /
  1 / 2 / 4) puts auto-igniting TNT in the starter kit for demolition
  practice. Casual runs only, competitive still pins no starting items.

## 0.8.0

- **Bed defenses are pyramids now, not boxes.** The shell over each enemy bed
  is placed wherever horizontal distance + height stays within the layer
  count, so the footprint is widest at bed level and loses a ring per level,
  tapering to a cap over the bed, the shape players actually build. The
  innermost material still hugs the bed and the outermost is the skin a
  rusher meets; seeding both bed blocks makes it a ridge rather than a point.
- **A gallery of defense presets, and they are data.** `rush.defense-presets`
  in `config.yml` is a curated section, one you delete stays deleted, one
  you add shows up. Twelve ship, from single-layer wool to a four-layer Keep.
  The defense button now opens a picker instead of cycling, and each tile
  spells its pyramid out layer by layer, outermost first. Each preset is just
  a list of materials, innermost first, and that list's length is how far the
  pyramid reaches out and up. Old `DefensePreset` names fold onto the new
  lower-case ids, so saved preferences carry over untouched.
- **The rush setup menu is reorganized.** Four rows reading top to bottom as
  the order the choices are made, team base, match modifiers, defender
  lineup, then go, instead of start and competitive stranded among the
  modifiers. Hovering **Start Casual** now summarizes the whole match: base,
  starter blocks, resources, pickaxe, defenses, generators and defenders.
  `guis.yml` v3 migrates existing layouts, moving each slot only while it
  still sits where the last version put it.
- **PvP bots can use blocks to reach you.** Tower up and the bot hops and
  seals the spot underneath itself, riding its own pillar after you; put a
  hole between you and it and it bridges, laying a block ahead along the
  dominant axis (never diagonally, a mob cannot walk corner to corner) and
  stepping out onto it. It builds only once walking has actually failed, and
  faster the higher its thinking layer. Everything it places is tracked, so
  you can break it and the stock reset clears it away; a per-stock budget
  stops a long spar becoming a build-off. Tuned under `behavior.building` in
  `pvpbot.yml`, with a server-wide switch and a per-player **Building**
  toggle.
- **Queued work that takes a moment now says so on a title.** Joins and setup
  pastes raise one, the action bar is easy to miss at exactly the moment the
  screen looks frozen. It appears only if the work is still running after
  `effects.title-delay-ticks`, so the common instant join flashes nothing,
  and comes down when the work reports back.
- **Fixed: a respawn could leave a player short of a full bar.** The heal was
  never the last thing to happen, it ran before potion effects were cleared
  and before the kit was re-dealt, both of which can move the max-health
  attribute, so it landed against a maximum that had not settled. The
  bot-death path also healed the instant the bot dropped and never
  re-asserted it when the round actually restarted. Health is now restored
  last on every reset path, absorption included, through one shared helper;
  arena resets (failed runs, void falls, `/practice restart`) restored no
  health at all and now do.

## 0.7.4

- **Ender chests work in rush practice.** Right-clicking any ender chest
  block on the map opens the run's own 27-slot chest, it survives combat
  respawns, is wiped by the next reset, and is never the player's real ender
  chest (which stays guarded everywhere, as before). Special items held in
  hand no longer fire when clicking an ender chest, the chest opens, like
  any other container.
- **Punch-to-deposit.** Punching a chest or ender chest sweeps the
  configured resources (`rush.deposit-items`, default iron/gold/diamonds/
  emeralds) from the inventory into it in one hit, with an action-bar tally;
  what the chest has no room for stays with the player. Sneak-punch
  bypasses it so a player-placed chest can still be broken;
  `rush.punch-to-deposit: false` turns the whole mechanic off.

## 0.7.3

- **Every rush spawn now carries a starter sword** in the first hotbar slot,
  on the initial spawn and on every combat respawn, like a real game.
  Configured by `rush.starter-sword` (default `WOODEN_SWORD`, `''` disables);
  a kit that already puts a sword in that slot keeps its own, and anything
  else there moves to the first free slot.
- **Explosions now knock defender bots around.** The manual TNT/fireball
  knockback only pushed players (whose cancelled damage event needs it);
  bots were left to vanilla explosion physics, which is weak on mobs and was
  immediately overridden by the brain's per-tick steering, a fireball at a
  defender's feet read as a dud. Bots now take the same falloff-scaled shove
  as players, with hitstun so they visibly ride the blast, and being blasted
  aggros them.

## 0.7.2

- **Fixed the shop still failing to open** for players whose MBedwars
  quick-buy bar has any empty slot, which is nearly everyone. The pin list
  is positional, with nulls marking the empty slots, and the accessor copied
  it with `List.copyOf`, which refuses nulls; it now copies null-tolerantly.

## 0.7.1

- **Fixed the rush shop failing to open** on servers whose MBedwars shop
  contains addon-provided special items: the `PLUGIN` special-item type
  carries a null id, and the 0.7.0 id normalization crashed on it
  (`NullPointerException` in `MBedwarsHook.resolve` on every dealer click).
  Those products now fall back to the "plugin" id, and any shop entry that
  cannot be read is skipped with a console warning instead of taking the
  whole shop down.

## 0.7.0

### Rush practice

- **Gold generators now run at the 4:1 forge ratio**: one gold per four iron
  (`rush.gold-interval-ticks` default 120 → 100; an untouched default is
  migrated, a custom value stands).
- **Fixed every shop special item.** MBedwars spells its special-item ids
  CamelCase (`Fireball`, `Bridge`, `RescuePlatform`, `MiniShop`) while the
  use-listener matched lowercase, so every special landed in the
  "unsupported" branch, TNT only worked because it is matched by material.
  Ids are now normalized on both ends, old tagged purchases included, and an
  untagged fire charge fires as a fireball too.
- **Four more special items work:** the teleporter (stand-still channel back
  to your base; moving cancels without consuming), the tracker (compass and
  action bar to the nearest defender or standing enemy bed), the TNT sheep
  (hunts the nearest defender, detonates map-safely) and the guard dog (a
  loyal wolf that fights beside you).
- **The shop now mirrors the MBedwars HypixelV2 layout, Quick Buy included.**
  Quick Buy is the first tab, page tabs sit beside it over a separator row
  that marks the open page, and items fill a 7×3 grid. Pins are read from and
  written to the player's real MBedwars profile, sneak-click to pin or
  unpin, and the same list appears in real games.
- **Defender bots at enemy bases.** The rush menu gains bots-per-team,
  difficulty (the pvpbot.yml presets), armor tier (leather is dyed team
  colors) and sword tier. Defenders hold their post, engage inside an aggro
  range (or when hit, or when their bed or its defenses are touched), fight
  at the chosen preset's cadence, leash back home, and respawn on a delay
  while their bed stands, a broken bed ends their respawns, exactly like a
  real game.
- **New rush goal: Team Wipe.** With defenders enabled the run ends when one
  enemy team is fully out, bed gone and every defender eliminated, on its
  own per-map leaderboard (`<map>#team_wipe`). Bed breaks no longer end
  combat runs; diamond and emerald generators produce on real cycles (30s /
  65s defaults) as spendable resources; dying to a defender is a bedwars
  respawn at your own base (kit reset, short hold, clock running), not a
  failed run. Competitive combat lineups are pinned by
  `rush.bots.competitive.*` (default 0 keeps competitive the classic race).

### PvP practice

- **Spectators are told each round's result in chat**: who was killed, and
  the health the victor had left.
- **Both fighters teleport home the moment a death lands** instead of right
  before the respawn; the corpse hold is served at the spawn.
- **New session stats on the sidebar:** accuracy (hits landed over swings
  thrown), K/D ratio, and hits dodged (bot attacks that failed to land),
  alongside hits, combos, kills and deaths.
- **Session stats reset when the bot's difficulty or settings change**, with
  a chat notice, numbers against one opponent never blend into another's.

### Misc

- **`/spectate <player>`** (alias `/spec`): the `/practice spectate` flow,
  one word shorter, with tab completion.
- **Bots render from further away:** `bot.tracking-range` in pvpbot.yml
  (default 96) raises the practice world's entity tracking ranges so bots
  and their tags stop popping in at the stock ~48 blocks.
- Fixed the rush leaderboard picker crashing once a fourth objective exists.

File format bumps, all migrated automatically with backups: config.yml v5,
messages.yml v6, guis.yml v2 (the rush menu grows to five rows).

## 0.6.0 and earlier

Predate this changelog, see the git history.
