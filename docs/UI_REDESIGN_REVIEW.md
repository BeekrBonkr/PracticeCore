# UI Redesign — Phase 3 review and Phase 4 test checklist

Branch `ui-redesign`, 2026-09-04. Everything here is presentation layer. Game logic, permissions, command names and click behavior are unchanged except where section 2 says so explicitly.

Foundation: `gui/Button.java` (one builder for every control), `Menu.nav()` (back bottom-left, close bottom-right, paging bottom-middle, computed from the row count), `Menu.arm()/isArmed()/disarm()` (inline delete confirmation), `Messages.usage()/warn()/confirmPrompt()`, the `label.*`, `gui.hint.*`, `gui.reason.*`, `gui.unavailable` and `gui.locked` keys. `messages.yml` is v9 and `guis.yml` v6: on upgrade, any value still at its old default is reset to the new wording; anything an admin edited is kept (`migrations/messages-v8.yml`, `migrations/guis-v5.yml`).

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
| BedDefenseConfigMenu | Bed Defense — <arena> | Own team-base keys; Mode = LEVER, Strict Order = CHAIN, Timer Start = REPEATER, Shuffle = ENDER_EYE; pinned/unavailable controls disabled with reasons |
| BedDefenseGalleryMenu | Defenses — Public / Mine / Favorites | Tabs at footer slots; Mine tab = your head; Favorites = AMETHYST_SHARD |
| BedDefenseActionsMenu | <name> | Delete = LAVA_BUCKET inline arm ("Confirm Delete"); Like/Favorite/Visibility play toggle sounds; Leaderboard locked with a reason |
| BedDefenseBoardsMenu | Boards — Bed Defense / Boards — <name> | Shared board lore skeleton |
| BedDefenseSessionMenu | Bed Defense — <arena> | "Choose Defense", "Edit Defense", "New Defense", "Other Maps"; Preview = PAINTING; Guided Building name never changes |
| BedDefenseEditMenu | Defense Editor | Save = EMERALD, Load = BOOKSHELF, Clear Build = RED_DYE, Delete = LAVA_BUCKET with its own keys, Leave Editor red door |
| admin ArenaListMenu | Arena Setup | Border and nav from the base class; white tile names with a `Status:` line; Create New Arena = CRAFTING_TABLE |
| admin ConfirmDeleteMenu | Delete <arena>? | Full border; Delete Forever = LAVA_BUCKET red; Keep It = BARRIER yellow; Close bottom-right |
| admin SetupActionsMenu | Creating: / Editing: <arena> | All buttons white (no section colors); Save Arena = EMERALD, disabled as a gray pane with the reason; Cancel Setup = RED_DYE; Mode = LEVER; Permission = TRIPWIRE_HOOK; Replace Schematic = CARTOGRAPHY_TABLE |

### Commands and chat

- Every usage string goes through one format: `Usage: /practice sub <required> [optional]`. Preconditions moved to a second gray line. `/practice pb reset` has one rendering.
- Help for `/practice arena`, `/practice world`, `/practice rush`, and bare `/practice pb` moved from hard-coded blocks into `help.*-detail` lists in messages.yml.
- Confirmations for `arena delete`, `world regen`, and `reload` use the yellow warn line plus the gray "Run … confirm" line. Failures stay red.
- Quoted values (`'arena'`) became white values everywhere. Terminal periods normalized; exclamation only on personal bests, records, and team wipes. "cancelled" → "canceled".
- Arena list, stats entries, and bed defense list entries are clickable with hover text.
- Action bar lines: no prefix, no period, under 45 characters. "Not ranked — play competitive to set records" unified across rush and bed defense.
- Rush combat deaths now read `rush.title.*` keys instead of the PvP bot's.
- Scoreboard labels Title Case; difficulty line reads `label.difficulty.short`.
- Setup chat prompts carry the prefix; the cancel acknowledgement is "Canceled."
- Spectate hotbar items follow the button conventions.

## 2. Behavior that changed (review these)

These are the only places a player will notice something other than text, color, or icon:

1. **Clear Build (defense editor) now needs two clicks.** It arms on the first click, executes on the second. R59 lists it as destructive. `gui/BedDefenseEditMenu.java`.
2. **Disabled controls are shown instead of hidden** where visibility was never permission-based: already-selected kit, pinned round settings while competitive, single-base team cycler, preview outside the play phase, guided building with no defense, save with no blocks, deleted arenas in stats, already-selected gallery tab. Clicking still does nothing (deny sound).
3. **Locked tiles show a reason line.** Admins (`practicecore.arena`) see the node; everyone else sees "no permission".
4. **Nav positions moved** in menus that had non-standard slots: rush shop close (49 → 53), PvP bot menus close (40 → 44), bed defense actions/session/editor per the row rule. Admins who customized `guis.yml` keep their own slots.
5. **Bed defense list entries** lost the `[play]` chip; the name itself is now clickable.
6. **Missing values show `???`.** `gui.none` had become `<dark_gray>—`, but menus substitute it as plain text, so the tag was printed literally ("Record: <dark_gray>—"). It is now `???`, tag-free (R12a). A server that already ran v9 keeps the old value in its `messages.yml`; set `gui.none` to `'???'` there by hand.

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
| R25 | Admin GUI and chat-prompt strings remain in Java, restyled but not in `messages.yml` | `gui/admin/*`, `setup/ChatPrompts.java` |
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

### Every menu
- [ ] Back is bottom-left, Close is bottom-right, paging bottom-middle, in every menu including the hub
- [ ] Kit preview: Back and Close in the top corners
- [ ] Full glass border everywhere, including the rush shop and the admin delete confirmation
- [ ] Every clickable item ends with a yellow "Click to …" line; no item has two hint lines or a hint followed by nothing
- [ ] No item name changes when you click it, except an armed delete and the page indicator
- [ ] Glow appears only on the selected kit/defense/preset/tab and on toggles that are on

### Hub and play
- [ ] `/practice menu`: Close works; Random Arena and Settings are white; Leave plays the normal click sound
- [ ] Play → category → arena: titles read `Play`, `Play — <category>`
- [ ] A locked arena shows iron bars, a gray name, and `Locked: …` (node visible only with `practicecore.arena`)
- [ ] Arena tile for an arena you hold the record on shows "You hold the record", no glow
- [ ] Leaderboards → category → arena: titles `Leaderboards`, `Leaderboards — <category>`, `Top Times — <arena>`; your row is aqua with "That's you"
- [ ] Stats: title `Stats — <you>`; a deleted arena's tile is a gray pane saying `Unavailable: no longer exists`
- [ ] Settings: night vision / wool / time toggle and cycle; state words on/off show

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
- [ ] Map picker title `Bed Defense Maps`; config menu `Bed Defense — <arena>` with `Choose Defense`, `New Defense`, `Edit Defense`
- [ ] In competitive mode, Shuffle and Timer Start are gray panes with `Unavailable: pinned by competitive mode`
- [ ] Gallery tabs at the footer; Mine tab shows your head; empty tabs read `No … yet`
- [ ] Right-click a tile → actions menu: Like/Favorite/Visibility play toggle sounds; Delete arms to `Confirm Delete` (glowing lava bucket), any other click disarms, second click deletes
- [ ] Editor: Save and Play disabled with `place some blocks first` when empty; Clear Build arms then clears; Leave Editor is a red door
- [ ] Session menu: Preview is a painting and is disabled outside the play phase; Guided Building's name never changes

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

### Admin setup GUI
- [ ] `/practice setup gui`: tiles white with a `Status:` line; empty state cobweb `No arenas yet`; Create New Arena crafting table
- [ ] Right-click a tile → delete confirmation: red lava bucket `Delete Forever`, yellow barrier `Keep It`, Close bottom-right keeps
- [ ] Setup panel: all buttons white; Save Arena gray pane with `Unavailable: needs a spawn first` until a spawn is set, then green emerald; Cancel Setup red dye
- [ ] Chat prompts (rename, category, permission) show the prefix; typing `cancel` replies `Canceled.`
