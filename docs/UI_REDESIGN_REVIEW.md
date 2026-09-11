# UI Redesign — Phase 3 review and Phase 4 test checklist

Branch `ui-redesign`, 2026-09-04. Everything here is presentation layer. Game logic, permissions, command names and click behavior are unchanged except where section 2 says so explicitly.

Foundation: `gui/Button.java` (one builder for every control), `Menu.nav()` (back bottom-left, close bottom-right, paging bottom-middle, computed from the row count), `Menu.arm()/isArmed()/disarm()` (inline delete confirmation), `Messages.usage()/warn()/confirmPrompt()`, the `label.*`, `gui.hint.*`, `gui.reason.*`, `gui.unavailable` and `gui.locked` keys. `messages.yml` is v9 and `guis.yml` v6: on upgrade, any value still at its old default is reset to the new wording; anything an admin edited is kept (`migrations/messages-v8.yml`, `migrations/guis-v5.yml`).

Updated 2026-09-08 for 0.11.0 (branch `dev`): rows and items marked **(0.11.0)** cover the bed defense maps, the publishing gate, notices, reports and moderation, the guis.yml v8 re-layout (from the user's own hand-edited layout; untouched values are reset to the new defaults, `migrations/guis-v7.yml`), and the admin GUI additions. `messages.yml` is now v12, `guis.yml` v8, `config.yml` v8.

Updated 2026-09-11 for 0.12.0 (branch `main`): rows and items marked **(0.12.0)** cover the menu simplification — four bed defense start buttons in place of the Mode lever, the Obsidian and Bed Repair toggles and Start; the hub's Bot Settings generalized to Mode Settings; the team button hidden on one-base maps; one-map categories skipping the picker; every title unique, white and not bold (R41 rewritten). `messages.yml` is now v16, `guis.yml` v12 (untouched values reset to the new defaults, `migrations/messages-v15.yml` and `migrations/guis-v11.yml`).

## 1. What was touched

### Menus (26)

| Menu | Title now | Notable changes |
|---|---|---|
| MainMenu | Practice | Close button added; Random Arena and Settings white; Sidebar = OAK_SIGN; Stats = BOOK; Leave uses the click sound; unreachable idle lore removed |
| CategoryMenu / ArenaMenu | Play / Play — <category> | Locked arenas = IRON_BARS with `Locked: reason`; record holder is a text line, not glow; unranked arenas show a state line |
| LeaderboardCategoryMenu / LeaderboardMenu | Leaderboards / Leaderboards — <category> | Titles no longer collide; metadata labels fixed (Players, Record, Held by, Your rank) |
| ArenaLeaderboardMenu | Top Times — <arena> | Rows no longer use stack count as rank; your row is aqua with "That's you"; "Your Standing" footer |
| StatsMenu | Stats — <player> | Deleted arenas show as disabled ("no longer exists"); tiles gain a view hint |
| SettingsMenu | Settings | Time of day = DAYLIGHT_DETECTOR; state words from `label.state` |
| SpectateMenu | Spectate | Offline targets show as disabled on refresh |
| KitsMenu / KitPreviewMenu | Kits / Preview — <kit> | Already-selected kit is disabled with a reason; selecting plays the select sound; preview keeps top-corner nav (R49) |
| PvpBotSettingsMenu | Bot Settings — <arena> | Kit = CHEST, no unconditional glow; Difficulty = EXPERIENCE_BOTTLE; CPS = SUGAR; knobs never glow |
| RushConfigMenu | Rush — <arena> | "Start Casual" → "Start Practice"; Start Competitive no longer glows unconditionally; preset tiles say Ranked/Not ranked in gold/dark gray; single-base team cycler disabled |
| RushDefenseMenu | Bed Defenses — <arena> | Current preset shows "Currently: selected" |
| RushBoardPickerMenu | Boards — <arena> | Layout now from the new `rushboards` section of guis.yml |
| RushShopMenu | Item Shop | Full border, Close bottom-right, Quick Buy = GOLD_BLOCK, unaffordable items say `Unavailable: cannot afford`, pin hints via shift-click lines |
| BedDefenseArenaMenu | Bed Defense Maps | Own locked-tile keys instead of the generic arena ones |
| BedDefenseConfigMenu | Bed Defense — <arena> | Own team-base keys; Mode = LEVER, Timer Start = REPEATER, Shuffle = ENDER_EYE; pinned/unavailable controls disabled with reasons |
| BedDefenseGalleryMenu | Defenses — Public / Mine / Favorites | Tabs at footer slots; Mine tab = your head; Favorites = AMETHYST_SHARD |
| BedDefenseActionsMenu | <name> | Delete = LAVA_BUCKET inline arm ("Confirm Delete"); Like/Favorite/Visibility play toggle sounds; Leaderboard locked with a reason |
| BedDefenseBoardsMenu | Boards — Bed Defense / Boards — <name> | Shared board lore skeleton |
| BedDefenseSessionMenu | Bed Defense — <arena> | "Choose Defense", "Edit Defense", "New Defense", "Other Maps"; Preview = PAINTING; Guided Building name never changes |
| BedDefenseEditMenu | Defense Editor | Save = EMERALD, Load = BOOKSHELF, Clear Build = RED_DYE, Delete = LAVA_BUCKET with its own keys, Leave Editor red door |
| admin ArenaListMenu | Arena Setup | Border and nav from the base class; white tile names with a `Status:` line; Create New Arena = CRAFTING_TABLE |
| admin ConfirmDeleteMenu | Delete <arena>? | Full border; Delete Forever = LAVA_BUCKET red; Keep It = BARRIER yellow; Close bottom-right |
| admin SetupActionsMenu | Creating: / Editing: <arena> | All buttons white (no section colors); Save Arena = EMERALD, disabled as a gray pane with the reason; Cancel Setup = RED_DYE; Mode = LEVER; Permission = TRIPWIRE_HOOK; Replace Schematic = CARTOGRAPHY_TABLE |

New and changed in 0.11.0:

| Menu | Title now | Notable changes |
|---|---|---|
| BedDefenseModerationMenu **(0.11.0, new)** | Defenses — Moderation / Defenses — Reported | Every defense, private ones included, reported first; left-click opens its actions, right-click its reports; Reported Only footer toggle (BELL, `beddefense-moderation.buttons.reported`, slot 46); refuses to open without `practicecore.beddefense.moderate` |
| BedDefenseReportsMenu **(0.11.0, new)** | Reports — <name> | One PAPER tile per report, newest first, with reason and filing time; click twice to drop one (`Confirm Dismiss`); Dismiss All footer (LAVA_BUCKET, slot 52), two clicks; the defense itself is untouched |
| admin ArenaOptionsMenu **(0.11.0, new)** | Arena — <arena> | Left-click on an arena tile opens it instead of the wizard: default, display name (NAME_TAG), icon, permission (TRIPWIRE_HOOK), PB blocks, category (BOOKSHELF), mode (LEVER), info (PAPER), delete (LAVA_BUCKET, red) and the editor; fixed text like the rest of the admin GUI |
| admin ImportMenu **(0.11.0, new)** | Import Maps | One tile per MBedwars arena; left-click imports as a rush map, right-click as a bed defense map; only reachable while MBedwars is present |
| MainMenu **(0.11.0)** | Practice | Random Arena and Sidebar hidden by default (`enabled: false`); Spectate 20, Settings 22, Leave 24; the PvP bot button on the bottom row (31) |
| SettingsMenu **(0.11.0)** | Settings | Sidebar toggle (OAK_SIGN, slot 16) with `Currently: on/off` |
| RushConfigMenu **(0.11.0)** | Rush — <arena> | Bot count moved up to row 2 (slot 19) beside the modifiers; dead `objective-*` keys dropped from guis.yml |
| PvpBotSettingsMenu **(0.11.0)** | Bot Settings — <arena> | combos, reach, aggression, block and build one cell right (23, 24, 25, 32, 33) |
| BedDefenseConfigMenu **(0.11.0)** | Bed Defense — <arena> | Shuffle shares Start's slot (40) by default and is hidden behind it (Start draws last); give `beddefense.buttons.shuffle.slot` another slot to show it |
| BedDefenseSessionMenu **(0.11.0)** | Bed Defense — <arena> | Whole layout one cell right (11–15, 21–23) |
| BedDefenseGalleryMenu **(0.11.0)** | Defenses — Public / Mine / Favorites / Review | Review tab (LECTERN, slot 52) for moderators only, reported first then everything incl. private; a remembered Review tab falls back to Public once the node is gone; reported defenses carry a red `Reports: <count>` line for moderators |
| BedDefenseActionsMenu **(0.11.0)** | <name> | Report (BELL, 15) on someone else's public defense, asks for the reason in an anvil, shows `Your report: sent/none`; Visibility on your own is disabled with `complete it in competitive first` until cleared (or `hidden by reports — a moderator has to review it` after an auto-hide, with a red `Hidden by player reports` line), lore shows `Cleared by you`; moderators see Visibility (with author and `Cleared by author`, disabled with `complete it in competitive first` on an uncleared defense — no bypass), Delete and Reports (WRITTEN_BOOK, 23, disabled with `no reports`) on anyone's; opening a reported defense as a moderator marks its reports seen |
| BedDefenseEditMenu **(0.11.0)** | Defense Editor | Visibility disabled with `complete it in competitive first` for a fresh or uncleared build; lore says changing the blocks makes it private again |
| Every menu **(0.12.0)** | unique, `<white>`, not bold | `Practice Menu`, `Player Settings`, `Practice Modes`, `<category> Maps` / `All Maps`, `Leaderboard Categories`, `<category> Leaderboards` / `All Leaderboards`, `Rush Setup — <arena>`, `Rush Defenses — <arena>`, `Rush Boards — <arena>`, `Bot Kits`, `Kit Preview — <kit>`, `Round Settings — <arena>`, `Bed Defense — <arena>`, `Defense — <name>`, `Bed Defense Boards`; admin menus and anvil prompts white too |
| MainMenu **(0.12.0)** | Practice Menu | Bot Settings → **Mode Settings** (`main.buttons.mode-settings`, slot 31): PvP bot knobs, rush modifiers or bed defense round settings, icon per mode; hidden for modes without settings. Flat list (categories off) with one playable map skips the picker |
| CategoryMenu **(0.12.0)** | Practice Modes | A category (or the Bed Defense tile) with exactly one playable map skips its picker and opens the map's setup, or joins it |
| RushConfigMenu **(0.12.0)** | Rush Setup — <arena> | Team Base hidden (not disabled) on a one-base map; Start reads `Apply and Restart` inside a run on the map |
| BedDefenseConfigMenu **(0.12.0)** | Round Settings — <arena> | Mode, Obsidian, Bed Repair and Start replaced by **Start Practice / Competitive / Obsidian / Bed Repair** (37/39/41/43); rules row Defense 20, Shuffle 22, Timer Start 24 (Shuffle no longer hidden); Team Base hidden on a one-base map; the set mode glows; a mode with nothing to run on is disabled with its reason, competitive without MBedwars with `needs MBedwars for the shop` |
| BedDefenseSessionMenu **(0.12.0)** | Bed Defense — <arena> | Five rows; Round Settings = RED_BED; the same four start buttons on row 3 (28/30/32/34) switch the mode and restart; Other Maps hidden with one map |
| BedDefenseEditMenu **(0.12.0)** | Defense Editor | Save starts a competitive round on the saved defense |
| BedDefenseArenaMenu **(0.11.0)** | Bed Defense Maps | Lists only arenas of mode `beddefense` with a playable base; empty state points admins at `/practice beddefense import <map>` |
| admin ArenaListMenu **(0.11.0)** | Arena Setup | Left-click opens the arena's options, not the wizard; tiles show `Default: yes`; footer gains Bed Defenses (LECTERN, locked without the moderate node), Import Maps (HOPPER, MBedwars only) and Reload (COMMAND_BLOCK, two clicks, locked without `practicecore.reload`) |
| admin SetupActionsMenu **(0.11.0)** | Creating — / Editing — <arena> | Third row is mode-aware: Team Spawn Here, Bed Here, Generator Here, Dealer Here and a clear for rush and bed defense arenas, Bot Spawn Here / Clear Bot Spawn for PvP bot arenas; Save disabled with `needs a team base with a bed` on those modes until one exists |

### Commands and chat

- Every usage string goes through one format: `Usage: /practice sub <required> [optional]`. Preconditions moved to a second gray line. `/practice pb reset` has one rendering.
- Help for `/practice arena`, `/practice world`, `/practice rush`, and bare `/practice pb` moved from hard-coded blocks into `help.*-detail` lists in messages.yml.
- Confirmations for `arena delete`, `world regen`, and `reload` use the yellow warn line plus the gray "Run … confirm" line. Failures stay red.
- Quoted values (`'arena'`) became white values everywhere. Terminal periods normalized; exclamation only on personal bests, records, and team wipes. "cancelled" → "canceled".
- Arena list, stats entries, and bed defense list entries are clickable with hover text.
- Action bar lines: no prefix, no period, under 45 characters. "Not ranked — play competitive to set records" unified across rush and bed defense.
- Rush combat deaths now read `rush.title.*` keys instead of the PvP bot's.
- Scoreboard labels Title Case; difficulty line reads `label.difficulty.short`.
- Setup text prompts open an anvil (0.12.0): the question is its title, the paper carries the current value and hint, taking the result confirms, closing cancels silently.
- Spectate hotbar items follow the button conventions.
- **(0.11.0)** `/practice beddefense help` is split by permission: `help.beddefense-detail`, `help.beddefense-moderate-detail`, `help.beddefense-admin-detail`. `help.arena-detail` gained `category` and `mode`; `help.setup-detail` and `help.setup-beddefense` cover the bed defense wizard steps.
- **(0.11.0)** Refusals carry clickable actions: an uncleared publish offers `[Play it competitively]` (`beddefense.visibility.needs-clear-actions`), the first competitive clear offers `[Publish it]` (`beddefense.cleared-actions`), a report alert to moderators offers `[Review] [Play it]` (`beddefense.report.alert-actions`), and `info <id>` ends with Play / Hide / Dismiss reports / Delete.
- **(0.11.0)** `/practice beddefense delete <id>` without `confirm` answers with the two-line confirm prompt like `arena delete`; `/practice pb reset <player> all` asks for `confirm` too.
- **(0.11.0)** Notices (`beddefense.notice.*`, `stats.reset-notice`) are the same messages.yml keys whether delivered live or after a login.

## 2. Behavior that changed (review these)

These are the only places a player will notice something other than text, color, or icon:

1. **Clear Build (defense editor) now needs two clicks.** It arms on the first click, executes on the second. R59 lists it as destructive. `gui/BedDefenseEditMenu.java`.
2. **Disabled controls are shown instead of hidden** where visibility was never permission-based: already-selected kit, pinned round settings while competitive, single-base team cycler, preview outside the play phase, guided building with no defense, save with no blocks, deleted arenas in stats, already-selected gallery tab. Clicking still does nothing (deny sound).
3. **Locked tiles show a reason line.** Admins (`practicecore.arena`) see the node; everyone else sees "no permission".
4. **Nav positions moved** in menus that had non-standard slots: rush shop close (49 → 53), PvP bot menus close (40 → 44), bed defense actions/session/editor per the row rule. Admins who customized `guis.yml` keep their own slots.
5. **Bed defense list entries** lost the `[play]` chip; the name itself is now clickable.
6. **Missing values show `???`.** `gui.none` had become `<dark_gray>—`, but menus substitute it as plain text, so the tag was printed literally ("Record: <dark_gray>—"). It is now `???`, tag-free (R12a). A server that already ran v9 kept the old value in its `messages.yml`; messages v15 (0.12.0) rewrites it, and a tagged or empty `gui.none` falls back to `???` at run time.
7. **(0.11.0) Bed defense maps are arenas of their own.** Only templates with `mode: beddefense` (and a team base with a bed) appear in the Bed Defense picker; rush maps no longer do. On an upgraded server the Bed Defense tile is absent until an admin imports (`/practice beddefense import`, or the Import Maps menu, right-click) or builds one (`/practice setup mode beddefense` + `/practice setup beddefense …`), or changes a saved rush map's mode. Bed defense maps always sit under the Bed Defense tile, never in their folder's category or the flat list. `BedDefenseService.supports`, `CategoryMenu`, `ArenaMenu`.
8. **(0.11.0) Publishing is gated.** With `beddefense.require-author-clear: true` (default) an author can publish only a defense they have finished in a competitive round themselves; reshaping withdraws that and a changed public defense saves as private with an explanation. Nobody bypasses it, moderators included. The editor's Visibility button and the actions menu's publish refuse (disabled / chat refusal with a clickable action) until then. `BedDefenseService.canPublish/setPublished/editSave/finishRound`.
9. **(0.11.0) Authors are told about visibility changes, online or not.** Moderator publish/unpublish/delete and the author's own reshape produce a chat line; an offline author gets it queued in playerdata (`notices:`, capped at 50) and delivered 20 ticks after their next login. `/practice pb reset` uses the same path. `notice/NoticeService`, `StatsStore.addNotice/drainNotices`, `ConnectionListener`.
10. **(0.11.0) Hub defaults changed.** Random Arena and the hub Sidebar button are off by default; the Sidebar toggle lives in Settings (slot 16). An admin who never touched `main.buttons.*` loses those two hub buttons on the v8 migration; anyone who moved them keeps them. `main.buttons.random.enabled` / `main.buttons.sidebar.enabled` restore them.
11. **(0.11.0) Reports and moderation.** Any player can report someone else's public defense (one report per player, a second replaces it); every online moderator is told. Moderators (`practicecore.beddefense.moderate`, child of `practicecore.admin`) see private defenses in the gallery's Review tab and the moderation menu, may show/hide/delete anyone's, and dismiss reports. Admin delete used to be gated on `practicecore.arena`; it is now the moderate node. Chat deletes always need `confirm`. Dismissing reports (one or all) takes two clicks (R59). A defense hides itself once `reports.auto-hide.min-reports` of its builders, making up `reports.auto-hide.percent` of everyone but the author who has built it, report it; the author is told and cannot republish until a moderator dismisses the reports (lifting the hide) or publishes it (closing them). Reports no moderator has looked at come back as a reminder on a moderator's join and every `reports.remind-minutes`; a report is seen once the defense's actions/reports menu is opened or `info <id>` runs (`BedDefense.reportsSeen`, `DefenseStore.unseenReported`, `BedDefenseService.remind*`).

## 3. Intentionally left alone (needs a logic change)

Marked in code with `// STYLE-GUIDE: needs logic change (Rnn)`.

| Rule | What | Where |
|---|---|---|
| R53 | Leaderboards and Spectate hub buttons stay hidden without permission; showing them disabled would reveal them per permission | `gui/MainMenu.java:65, :98` |
| R47 | Kit gallery and rush defense gallery truncate beyond the grid instead of paging | `gui/KitsMenu.java:51`, `gui/RushDefenseMenu.java:63` |
| R59 | Finish Triggers (clear all) and Replace Schematic execute on one click; lore now says so, no confirmation added | `gui/admin/SetupActionsMenu.java:85, :112` |
| R23 | Hotbar menu item name and lore live in `config.yml`; moving them is a config migration | `item/MenuItemService.java:35` |
| R22 | Tab completion offers `leaderboards` without checking `practicecore.leaderboard` | `command/PracticeCommand.java:33` |
| R19 | MLG success has chat only, no title; adding a title is a new call in `MlgMode.handleMove` | `mode/MlgMode.java` |
| R25 | Admin GUI and anvil-prompt strings remain in Java, restyled but not in `messages.yml` | `gui/admin/*`, `gui/AnvilPrompts.java` |
| R24 | `RushPreset.messageKey` and `BedDefenseSelection` option labels still live under `gui.*` though non-menu code reads them | `rush/RushPreset.java`, `beddefense/BedDefenseSelection.java` |
| R1 | Rush defender nametag uses gray for the team name; using the team's own color needs the tag builder to resolve it | `messages.yml` `rush.bots.tag` |
| F7 | Material and team names are prettified in Java, untranslatable | `beddefense/BlockKinds.pretty`, `mode/RushMode.prettyTeam` |
| F8 | A custom difficulty preset with no `label.difficulty` key renders uncolored | `gui/RushConfigMenu.java:317` |
| S8 | `board.spectate-lines` still unused on default config | `board/BoardService` |
| — | `pretty()` / `prettyMaterial()` duplicated between two rush menus | `gui/RushDefenseMenu.java:126`, `gui/RushShopMenu.java:274` |

## 4. Manual test checklist

Start a server with a copy of an existing `plugins/PracticeCore/` folder so the migration path is exercised, and once more with the folder deleted for a fresh install.

### Upgrade path
- [ ] On first start, console shows `messages.yml v8 → v9` and `guis.yml v5 → v6` with backups in `backups/`
- [ ] A key you edited by hand before upgrading keeps your text; an untouched key shows the new text
- [ ] `/practice reload` reports no validation problems
- [ ] `label.state.on` / `off` render as the words `on` / `off` (green / red), not blank
- [ ] An arena with no record shows `Record: ???` and `Held by: ???` in white, never a literal `<dark_gray>` tag
- [ ] **(0.11.0)** Console shows `config.yml v7 → v8`, `messages.yml v11 → v12` and `guis.yml v7 → v8`; `beddefense.require-author-clear` and `beddefense.reports` arrive in config.yml with their comments; `beddefense.not-a-rush-map` is gone from messages.yml; `rush.buttons.objective-*` are gone from guis.yml
- [ ] **(0.11.0)** A hub slot you moved by hand before upgrading stays where you put it; an untouched hub loses Random Arena and Sidebar and shows Spectate / Settings / Leave at 20 / 22 / 24
- [ ] **(0.11.0)** An existing `defenses/<id>.yml` loads without `cleared-fingerprint` or `reports` and gains both (plus `data-version: 2`) on its next save; an existing playerdata file loads without `notices`

### Every menu
- [ ] Back is bottom-left, Close is bottom-right, paging bottom-middle, in every menu including the hub
- [ ] Kit preview: Back and Close in the top corners
- [ ] Full glass border everywhere, including the rush shop and the admin delete confirmation
- [ ] Every clickable item ends with a yellow "Click to …" line; no item has two hint lines or a hint followed by nothing
- [ ] No item name changes when you click it, except an armed delete and the page indicator
- [ ] Glow appears only on the selected kit/defense/preset/tab and on toggles that are on

### Hub and play

- [ ] **(0.12.0)** Mode Settings shows on the bottom row only in a PvP bot, rush or bed defense session, wears that mode's icon, and opens its menu with Back returning to the hub; absent in bridging, bed break and MLG
- [ ] **(0.12.0)** A category with one playable map opens that map's setup (rush, bed defense) or joins it directly; with two or more the picker shows; a single locked map still shows the picker
- [ ] `/practice menu`: Close works; Random Arena and Settings are white; Leave plays the normal click sound
- [ ] Play → category → arena: titles read `Play`, `Play — <category>`
- [ ] A locked arena shows iron bars, a gray name, and `Locked: …` (node visible only with `practicecore.arena`)
- [ ] Arena tile for an arena you hold the record on shows "You hold the record", no glow
- [ ] Leaderboards → category → arena: titles `Leaderboards`, `Leaderboards — <category>`, `Top Times — <arena>`; your row is aqua with "That's you"
- [ ] Stats: title `Stats — <you>`; a deleted arena's tile is a gray pane saying `Unavailable: no longer exists`
- [ ] Settings: night vision / wool / time toggle and cycle; state words on/off show
- [ ] **(0.11.0)** Settings: the Sidebar toggle at slot 16 hides and shows the live scoreboard, reads `Currently: on/off`, and survives a relog; `/practice sidebar` flips the same value
- [ ] **(0.11.0)** Hub: the PvP bot button appears at 31 only during a bot spar; `/practice menu beddefense` opens the map picker

### PvP bot
- [ ] Bot Settings: Kit = chest without glow; Difficulty = experience bottle; knobs never glow; toggles glow when on
- [ ] Kits: selected kit is a gray pane with `Unavailable: already selected`; picking another plays the select sound
- [ ] Preview shows real armor tooltips and the kit banner

### Rush
- [ ] Rush menu: `Start Practice` (lime dye) and `Start Competitive` (nether star, no glow); with one team base the Team Base button is a gray pane
- [ ] Preset tiles say `Ranked — times are recorded` (gold) or `Not ranked` (dark gray)
- [ ] Bed Defenses picker: current preset says `Currently: selected` and denies re-pick
- [ ] Board picker: title `Boards — <arena>`; four boards at slots 10/12/14/16
- [ ] Shop from a dealer: full border, Close bottom-right, Quick Buy tab gold block, unaffordable item says `Unavailable: cannot afford` and still denies on click; shift-click pins/unpins with the shift-click hint lines
- [ ] Die to a defender: title text comes from `rush.title.*` (edit it in messages.yml to confirm)

### Bed defense

- [ ] **(0.12.0)** Round Settings: four start buttons, the set mode glowing; each starts the round in its mode and the sidebar/board agree; Team Base absent on a one-base map and present on a multi-base one
- [ ] **(0.12.0)** In-arena menu is five rows; the red bed opens Round Settings; the four start buttons switch the mode and restart; Other Maps absent with one map
- [ ] **(0.12.0)** Saving in the editor lands in a competitive round on the saved defense
- [ ] **(0.12.0)** Upgrade from v11/v15 files: the old Mode/Obsidian/Repair/Start keys are gone, a moved Bot Settings slot becomes Mode Settings' slot, edited lore survives
- [ ] Map picker title `Bed Defense Maps`; config menu `Bed Defense — <arena>` with `Choose Defense`, `New Defense`, `Edit Defense`
- [ ] In competitive mode, Shuffle and Timer Start are gray panes with `Unavailable: pinned by competitive mode`
- [ ] Gallery tabs at the footer; Mine tab shows your head; empty tabs read `No … yet`
- [ ] Right-click a tile → actions menu: Like/Favorite/Visibility play toggle sounds; Delete arms to `Confirm Delete` (glowing lava bucket), any other click disarms, second click deletes
- [ ] Editor: Save and Play disabled with `place some blocks first` when empty; Clear Build arms then clears; Leave Editor is a red door
- [ ] Config menu row 2 reads Defense, Mode, Timer Start; row 3 reads New Defense, Edit Defense; Start alone on row 4 with no Shuffle visible, on a fresh `guis.yml` and on one upgraded from v6
- [ ] Leaderboards list one board per defense, and no board is named "strict order"
- [ ] Start button lore no longer has a `Strict order:` line
- [ ] Practice round: finishing shows a personal best, the sidebar reads `Best:` plus `Your own best — not ranked`, and nothing is broadcast to the server
- [ ] `/practice stats` shows a `(Bed Defense, practice)` tile that is disabled with `practice times are yours alone`; the competitive tile still opens its ranking
- [ ] Competitive round on a defense with obsidian: emeralds drop on your own base gold spawner, and the map's middle emerald spawners stay dead
- [ ] Session menu: Preview is a painting and is disabled outside the play phase; Guided Building's name never changes
- [ ] **(0.11.0)** Import a map: `/practice beddefense import <mbedwars-arena>` on a fresh name creates an arena with `mode: beddefense` and the team/bed/generator layout under `settings.rush`; the same command with `overwrite` over an arena imported as rush re-stamps it `beddefense`; `/practice beddefense maps` lists it
- [ ] **(0.11.0)** Build one: `/practice setup start <name>`, `/practice setup mode beddefense`, `/practice setup save` refuses with the "needs at least one team with both a spawn and a bed" line; after `setup beddefense team red` and `setup beddefense bed red` it saves; in the wizard panel the third row shows Team Spawn Here / Bed Here / Generator Here / Dealer Here and Save Arena is a gray pane with `needs a team base with a bed` until then
- [ ] **(0.11.0)** Category tile: the bed defense map sits under the Bed Defense tile in Play whatever folder it is in (put it in `templates/<other>/` and reload: it does not appear under `<other>`); a rush map does not appear in the Bed Defense picker; with `categories.enabled: false` the flat list has the Bed Defense footer button and no bed defense tiles; `/practice join <map>` opens the bed defense setup menu directly; `/practice top <map>` answers with the per-defense boards line
- [ ] **(0.11.0)** Publishing refused: save a new defense, open the editor's menu, Visibility is a gray pane with `Unavailable: complete it in competitive first`; in the gallery's actions menu, clicking Visibility (or `/practice beddefense publish <id>`) refuses with the "stays private until you complete it yourself in competitive mode" line and a clickable `[Play it competitively]`
- [ ] **(0.11.0)** Clearing: click that action (or `/practice beddefense play <id> competitive`), finish the defense in the competitive round; chat says it can be made public now with `[Publish it]`; the actions menu now shows `Cleared by you: yes` and Visibility publishes; `info <id>` (as a moderator) shows `Cleared by author: yes`
- [ ] **(0.11.0)** Reshape withdraws it: edit the published defense, move one block, save; chat says it is private again because the blocks changed; the gallery's Public tab no longer lists it; Visibility is disabled again until a fresh competitive completion
- [ ] **(0.11.0)** With `beddefense.require-author-clear: false` and `/practice reload`, a fresh defense publishes straight away
- [ ] **(0.11.0)** Moderator hide/show: as a second account holding `practicecore.beddefense.moderate`, unpublish someone's public defense from the actions menu; the author (online) sees "A moderator made your bed defense … private" at once. Repeat with the author logged out: their playerdata gains a `notices:` entry, and about a second after they next log in the line arrives; publish it back and the public line arrives the same way; delete one of theirs and the "deleted" notice follows
- [ ] **(0.11.0)** Reporting: as a non-author, right-click a public defense, Report (bell) closes the menu and asks for a reason in an anvil; the reporter sees "Reported …", the actions menu reads `Your report: sent`; every online moderator sees the alert with `[Review] [Play it]`; a second report from the same player says "updated" and the count stays at 1; Report is absent on your own and on private defenses; `/practice beddefense report <id> some reason` files without the prompt; a reason over `reason-max-length` is refused
- [ ] **(0.11.0)** Auto-hide: with `auto-hide.min-reports: 1` and `percent: 50`, have two non-author accounts start a round on a public defense and one of them report it; it goes private, the author sees the red "was hidden" line, every moderator sees "hid itself", the author's Visibility button reads `hidden by reports — a moderator has to review it`; `dismiss <id>` as a moderator says the hide is lifted and the author is told they may publish again; alternatively `publish <id>` as a moderator says its reports are closed
- [ ] **(0.11.0)** Reminders: file a report, then as a moderator log out and back in — about two seconds later the "report(s) no moderator has looked at" list appears with the defense clickable; open its review menu (or `info <id>`) and relog — no reminder; with `remind-minutes: 1` and a fresh report, the same list arrives after a minute to every moderator online
- [ ] **(0.11.0)** No bypass: as a moderator, `publish <id>` on someone's uncleared defense is refused ("stays private until they complete it themselves") and the actions-menu Visibility is disabled
- [ ] **(0.11.0)** Moderation: `/practice beddefense moderate` opens `Defenses — Moderation` with the reported defense first and private ones listed; Reported Only toggles to `Defenses — Reported`; the gallery shows the Review tab (lectern) only to moderators; right-click a tile in the moderation menu opens `Reports — <name>`; clicking a report twice drops that one; Dismiss All arms then clears the rest; `/practice beddefense reports`, `all`, `info <id>`, `dismiss <id>` match
- [ ] **(0.11.0)** Chat delete: `/practice beddefense delete <id>` without `confirm` answers with the two-line prompt and deletes nothing; with `confirm` it deletes; a moderator can delete anyone's, a player without the node only their own

### Spectate
- [ ] `/spectate <player>` and the menu: title `Spectate`; a target who leaves shows as disabled on the next refresh
- [ ] Hotbar items: names white/red bold, lore ends with `Right-click to …`

### Commands and chat
- [ ] `/practice` bare, `/practice help`, `/practice arena`, `/practice world`, `/practice rush`, `/practice pb`: help lists with the prefix on the first line only
- [ ] Any wrong arguments: `Usage: /practice …` in one format, prose on a second gray line if any
- [ ] `/practice arena delete <x>` without confirm: yellow warning, then gray `Run … confirm to go ahead.`; same for `world regen` and a structural `reload`
- [ ] `/practice list` and `/practice stats`: entries are clickable with hover text
- [ ] No chat line contains a quoted `'value'`; every line ends with a period (or `!` on a PB/record)
- [ ] Finish a run: chat line, title, and broadcast punctuation match the guide
- [ ] Action bar messages have no prefix and no trailing period
- [ ] **(0.11.0)** `/practice beddefense help` shows the player list, plus the moderation list with the moderate node, plus the maps list with `practicecore.setup`; tab completion offers `moderate|review|reports|dismiss|info|all` only to moderators and `import|importall|maps` only to setup admins
- [ ] **(0.11.0)** `/practice arena category <arena> <name>` moves the arena folder under `templates/<name>/` and the Play menu lists it there; `default` puts it back; `/practice arena mode <arena> beddefense` turns a hand-built rush map into a bed defense map (it leaves the Rush list and appears under the Bed Defense tile); both refuse while that arena is open in the wizard
- [ ] **(0.11.0)** `/practice pb reset <player> all` without `confirm` warns and wipes nothing; with `confirm` it wipes and the player is told (now, or on next login)

### Admin setup GUI
- [ ] `/practice setup gui`: tiles white with a `Status:` line; empty state cobweb `No arenas yet`; Create New Arena crafting table
- [ ] Right-click a tile → delete confirmation: red lava bucket `Delete Forever`, yellow barrier `Keep It`, Close bottom-right keeps
- [ ] Setup panel: all buttons white; Save Arena gray pane with `Unavailable: needs a spawn first` until a spawn is set, then green emerald; Cancel Setup red dye
- [ ] Chat prompts (rename, category, permission) show the prefix; typing `cancel` replies `Canceled.`
- [ ] **(0.11.0)** Arena options: left-click an arena tile opens its options menu (not the wizard); default, display, icon, permission, PB blocks, category, mode, delete and info each work and write `arena.yml`; the editor is reachable from there; the tile shows `Default: yes` on the default arena
- [ ] **(0.11.0)** Import menu: with MBedwars installed the footer shows Import Maps (hopper); left-click on an MBedwars arena imports it as rush, right-click as bed defense, and chat reports which; without MBedwars the button is absent
- [ ] **(0.11.0)** Footer: Bed Defenses (lectern) opens the moderation menu for a moderator and is a locked tile naming the node otherwise; Reload arms to `Confirm Reload` and reloads on the second click, and a reload that needs confirming is pointed at `/practice reload confirm` instead of forcing
