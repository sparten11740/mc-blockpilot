# player-control Specification

## Purpose

Commands that act as the local player. High-level actions such as pathfinding,
mining, crafting and container transfer run inside the mod at tick rate. A raw
mouse and keyboard layer covers any interaction, vanilla or modded, that a human
performs with clicks, holds and drags.

## Requirements

### Requirement: Actions go through the normal client input path
Every action SHALL be performed the way a human's input would be: player
movement input, rotation, the game's use/attack/pick handling, screen slot
clicks and chat. Server-side and mod code SHALL see ordinary player behavior. No
action sends packets a vanilla client could not send.

#### Scenario: Mining on a multiplayer server
- **WHEN** the agent mines a block on a vanilla server
- **THEN** the server sees normal digging packets, with the vanilla dig time for
  the held tool

### Requirement: Long actions complete in the mod
Actions that take more than one tick (`goto`, `mine`, `craft`, `wait`, timed
inputs) SHALL run tick by tick inside the mod. The script's result is returned
only when the action finishes, fails, is interrupted, or hits its timeout
(default 60 s, override with `timeout=<seconds>`).

#### Scenario: Goto returns on arrival
- **WHEN** an agent runs `goto 120,64,-30`
- **THEN** a single reply arrives after the player reaches the target, like `ok
  goto 120,64,-30 9.2s`

#### Scenario: Timeout
- **WHEN** an action exceeds its timeout
- **THEN** it is cancelled, and the result is `err timeout <elapsed>` with the
  remaining commands `skip`

### Requirement: Pathfinding movement
`goto <pos|e<id>> [near=<r>]` SHALL compute a path through loaded chunks and
walk it. The path may walk, sprint, jump one block up, drop up to three blocks,
swim, and climb ladders/vines. It SHALL NOT break or place blocks. It fails with
`err no_path`, or with `err stuck <pos>` if progress stops for 3 s.

#### Scenario: Unreachable target
- **WHEN** the target is enclosed by solid blocks
- **THEN** the result is `err no_path` without the player moving

#### Scenario: Follow entity
- **WHEN** an agent runs `goto e812 near=2`
- **THEN** the player walks until within 2 blocks of the entity's current
  position

### Requirement: Basic movement and orientation
The mod SHALL provide `move <forward|back|left|right> <blocks|Nt>`, `jump`,
`sneak on|off`, `sprint on|off`, `face <pos|e<id>>` (aim at the target's center
or a block face), and `turn <yaw> <pitch> [Nt]` (set absolute rotation,
optionally smoothed over N ticks). A move by distance SHALL fail with `err
stuck moved=<blocks>` once the player has made no progress for 20 ticks.

#### Scenario: Face a block
- **WHEN** an agent runs `face ~2,0,1`
- **THEN** the crosshair target afterwards is that block

#### Scenario: Walking into a wall
- **WHEN** an agent runs `move forward 16` facing a wall
- **THEN** the result is `err stuck moved=0` about a second later, not a
  timeout

### Requirement: Block interaction
The mod SHALL provide `mine <pos>`, `mine <block|#tag> x<n>` (find, walk to and
mine the n nearest), `place <item> <pos> [face]` (select the item and place it
against an adjacent solid face), and `use [<pos>|e<id>]` (right-click a block,
an entity, or the held item). Targets out of reach without a walk fail with `err
out_of_reach`.

#### Scenario: Mine several logs
- **WHEN** an agent runs `mine #logs x6`
- **THEN** the player walks to and mines up to six log blocks, and the result
  reports `ok mine 6/6 oak_log`

#### Scenario: Place without item
- **WHEN** an agent runs `place torch ~1,0,0` with no torch in the inventory
- **THEN** the result is `err no_item torch`

### Requirement: Combat
`attack <e<id>|entity-type> [x<n>]` SHALL aim at the target and attack with
vanilla attack cooldown timing. It follows the target within 16 blocks until it
dies, n targets are defeated, or the action times out.

#### Scenario: Attack until dead
- **WHEN** an agent runs `attack e812`
- **THEN** the player attacks the entity at full cooldown charge until it dies,
  and the result is `ok attack e812 killed`

### Requirement: Inventory management
The mod SHALL provide `select <slot|item>` (hotbar or move to hotbar), `swap
<slotA> <slotB>`, `offhand <item>`, `equip <item>` (armor), and `drop
<item|slot> [n]`.

#### Scenario: Select item not on hotbar
- **WHEN** an agent runs `select torch` and torches are only in main inventory
- **THEN** the torches are moved into the selected hotbar slot

### Requirement: Crafting
`craft <item> [x<n>]` SHALL craft through the vanilla recipe-book placement
mechanism. It uses the 2x2 inventory grid when the recipe fits, or an
open/nearby crafting table (within 4 blocks), and fails with `err no_recipe`,
`err missing <item> x<n>` or `err need_table`.

#### Scenario: Craft planks
- **WHEN** an agent with 6 oak logs runs `craft oak_planks x24`
- **THEN** 24 oak planks end up in the inventory and the result is `ok craft 24
  oak_planks`

### Requirement: Container transfer
The mod SHALL provide `open <pos>` (walk into reach and open), `take
<item|s<index>> [n]`, `put <item> [n]`, `click s<index>
[left|right|shift|<hotbar-number>]` (raw slot click), and `close`.

#### Scenario: Take from chest
- **WHEN** a chest with 10 iron ingots is open and the agent runs `take
  iron_ingot 10`
- **THEN** 10 iron ingots move to the player's inventory

### Requirement: Raw mouse buttons and holds
`mouse down <left|right|middle>`, `mouse up <button>`, and `mouse click <button>
[Nt]` (press, then release after N ticks, default 1) SHALL feed button events
into the game's own mouse handling, as a physical mouse would. Vanilla and
modded handlers see them. A held button stays held across later commands until
released.

#### Scenario: Hold right-click across commands
- **WHEN** an agent runs `mouse down right; wait 40t; mouse up right`
- **THEN** the game sees the right button held for 40 ticks, the same as a human
  holding it

### Requirement: Raw mouse movement and drags
`mouse move <dx> <dy> [Nt]` SHALL feed relative pointer movement, spread evenly
over N ticks. In-world, units are raw pointer pixels and the camera turns
through the game's sensitivity handling. On a screen, units are GUI-scaled, the
same as `screen` and `cursor`, and the cursor moves. `drag <button> <dx> <dy>
[Nt]` SHALL press the button, move, and release it.

#### Scenario: Drag a modded in-world handle
- **WHEN** an agent aims at a Create Simulated physics assembler handle and runs
  `drag right 0 -120 30t`
- **THEN** the game receives right-button down, 30 ticks of upward mouse
  movement, and right-button up, and the mod's drag interaction responds as it
  would to a human drag

#### Scenario: Move while holding
- **WHEN** an agent runs `mouse down right; move forward 3; mouse move 80 0 20t;
  mouse up right`
- **THEN** the button stays held while the player walks and while the camera
  turns

### Requirement: Pointer actions on screens
With a GUI open, `cursor <x,y>`, `click <x,y> [button]`, `drag <button> <x1,y1>
<x2,y2> [Nt]` and `scroll <n> [x,y]` SHALL use GUI-scaled coordinates as
reported by `screen`. Wherever they take a point, `w<i>` stands for the centre
of the widget `screen` lists as `w<i>`. They work on any screen, including
modded screens and widgets the mod does not know.

#### Scenario: Drag a slider
- **WHEN** `screen` reports a slider at `60,40,100,20` and the agent runs `drag
  left 62,50 140,50 10t`
- **THEN** the slider moves as it would under a human drag, and its new value
  appears in the next `screen` diff

#### Scenario: Click a listed widget
- **WHEN** `screen` lists `w3:button "Create New World"` and the agent runs
  `click w3`
- **THEN** that button is clicked at its centre

### Requirement: Raw keyboard and key mappings
`key <down|up|press> <key> [Nt]` SHALL feed key events into the game's keyboard
handling. `<key>` is a key name (`w`, `space`, `left_shift`, `e`, `f5`) or a
key-mapping id (`key.use`, `key.inventory`, or a mod's mapping id), resolved to
its currently bound key. `type <text>` SHALL send character input to the focused
text field.

#### Scenario: Modded key mapping
- **WHEN** a mod registers a key mapping and the agent runs `key press <that
  mapping id>`
- **THEN** the mod's handler fires as if the user pressed the bound key

### Requirement: Held inputs are always released
Held buttons, keys and movement SHALL be released when the script errors, on
`stop`, on human override, when the session closes, and when the player leaves
the world. `release` SHALL release them explicitly.

#### Scenario: Session dropped mid-hold
- **WHEN** an agent's connection closes while `mouse down right` is active
- **THEN** the right button is released within one tick

### Requirement: Input works without window focus
While an agent is present, injected input SHALL work when the game window is
not focused, and losing focus SHALL NOT open the pause menu or break agent
control. An agent is present while a session is connected and for 60 seconds
after the last one closed, because one-shot clients open a connection per
script. Normal behavior returns after that.

#### Scenario: Window in background
- **WHEN** the user switches to another app while an agent runs `goto`
- **THEN** the game does not pause and the player keeps walking

#### Scenario: Scripts from a one-shot client
- **WHEN** an agent sends one script after another through the CLI, each on its
  own connection, with the window in the background
- **THEN** the game does not pause between the scripts

### Requirement: Chat and commands
`chat <text>` SHALL send a chat message. `cmd <command>` SHALL send a slash
command as the player, with the leading `/` optional.

#### Scenario: Run a command
- **WHEN** an agent runs `cmd time set day` with permission
- **THEN** the command is sent and any feedback arrives as `! chat` events

### Requirement: Waiting and cancelling
`wait <Nt|Ns>` SHALL wait the given ticks or seconds. `wait idle` SHALL wait
until queued actions finish. `wait <query> until <predicate>` SHALL wait until a
condition holds; see command-language. `stop` SHALL cancel the running and
queued actions and release held inputs.

#### Scenario: Wait for a block to disappear
- **WHEN** an agent runs `use ~1,0,0; wait block ~1,0,0 until id==air
  timeout=5s` on a block that breaks when used
- **THEN** the script returns as soon as the block is gone, and fails with `err
  timeout` after 5 s if it isn't

#### Scenario: Stop interrupts goto
- **WHEN** session B runs `stop` while session A's `goto` is in progress
- **THEN** A's script result is `err cancelled`, and B's result is `ok stopped
  1`

### Requirement: Human override
Physical keyboard or mouse input from the user during agent control SHALL
immediately cancel agent actions (`err interrupted`) and release held inputs. A
configurable key mapping SHALL toggle agent control off and on. While off,
actions fail with `err disabled` and perception still works.

#### Scenario: User grabs control
- **WHEN** the user presses W while the agent is mining
- **THEN** the agent's action ends with `err interrupted`, and the user has full
  control

#### Scenario: Agents can't press the toggle
- **WHEN** an agent runs `key press` with the toggle's mapping id or its bound
  key
- **THEN** the result is `err syntax`, and agent control stays on

### Requirement: Leftover screens are closed for world actions
Commands that act on the world or the player's own inventory (`face`, `move`,
`goto`, `use`, `attack`, `mine`, `place`, `select`, `swap`, `drop`, `offhand`,
`equip`) SHALL first close an open screen, as a player would press Esc. Commands
that work on screens (`screen`, `click`, `take`, `put`, `type`) keep it open.

#### Scenario: Table left open by craft
- **WHEN** a screen is still open from an earlier command and an agent runs
  `mine ~1,0,0`
- **THEN** the screen closes and the block is mined

### Requirement: Flying
`fly on|off` SHALL start or stop flying where the game mode allows it (creative,
spectator), and fail with `err syntax` elsewhere. After `fly on` the player
keeps flying through landings and teleports, while an agent is present and in
control, until `fly off`, leaving the world or a game mode that can't fly.

#### Scenario: Hover after a teleport
- **WHEN** an agent in creative teleports into the air and runs `fly on`
- **THEN** the player hovers instead of falling

#### Scenario: Teleport while flying
- **WHEN** an agent has run `fly on` and then `server as=player tp @p ~ ~5 ~`
- **THEN** the player is still flying after the teleport

### Requirement: Agent activity indicator
A small HUD indicator SHALL show while an agent is present, the action running,
and when agent control is disabled. The action stays shown until 2 seconds
without one have passed, and the text changes at most twice a second, so agents
running short actions one after another don't make it flicker.

#### Scenario: Agent acting
- **WHEN** an agent's `goto` is running
- **THEN** the HUD shows the active indicator with the action name

#### Scenario: Short actions in a row
- **WHEN** an agent runs one `server` command after another, a second apart
- **THEN** the indicator keeps showing `server` instead of switching back and
  forth with "agent active"
