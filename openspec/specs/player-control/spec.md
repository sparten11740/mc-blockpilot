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
`err no_path`, or with `err stuck <pos>` if progress stops for 3 s. Its jumps
SHALL NOT turn creative flight on or off.

#### Scenario: Stairs in creative
- **WHEN** an agent in creative, not flying, runs `goto` to the top of a flight
  of stairs
- **THEN** the player walks up the stairs and arrives without flying

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
`err missing <item> x<n>` or `err need_table`. From Minecraft 26.1 on, a
client on a remote server knows only the recipes the player has unlocked, so
there `craft` finds only those, and `help craft` says so. In singleplayer it
finds every recipe, as on 1.21.1.

#### Scenario: Craft planks
- **WHEN** an agent with 6 oak logs runs `craft oak_planks x24`
- **THEN** 24 oak planks end up in the inventory and the result is `ok craft 24
  oak_planks`

#### Scenario: Locked recipe in singleplayer
- **WHEN** an agent in a singleplayer world on 26.1 holds the ingredients of a
  recipe the player hasn't unlocked and runs `craft` for it
- **THEN** the item is crafted, as on 1.21.1

#### Scenario: Locked recipe on a remote server
- **WHEN** an agent on a remote 26.1 server runs `craft` for a recipe the
  player hasn't unlocked
- **THEN** the result is `err no_recipe <item>`

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
its currently bound key. A key name may separate its words with `_` or `.`
(`left_shift`, `left.shift`), and an unknown one fails with the names closest
to it. `type <text>` SHALL send character input to the focused text field.

#### Scenario: Modded key mapping
- **WHEN** a mod registers a key mapping and the agent runs `key press <that
  mapping id>`
- **THEN** the mod's handler fires as if the user pressed the bound key

#### Scenario: Key name as help writes it
- **WHEN** an agent runs `key press left_shift 5t`
- **THEN** the left shift key is held for 5 ticks, the same as with
  `left.shift`

#### Scenario: Misspelled key
- **WHEN** an agent runs `key press leftshift`
- **THEN** the result is `err syntax` naming `left_shift` among the closest
  key names

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

### Requirement: Aiming at a point within a block
`face <pos> at <u,v,w>` and `use <pos> at <u,v,w>` SHALL aim at a point inside
the block at `pos`, each coordinate from 0 to 1, in the block's own frame: the
frame its model is drawn in facing north (u west to east, v bottom to top, w
north to south), turned about the vertical by the block's horizontal facing as
block models turn. Without a horizontal facing the frame is unturned. A
coordinate outside 0 to 1 SHALL fail with `err syntax`.

#### Scenario: Key on a panel facing east
- **WHEN** a panel block's model has a key at 0.75,0.5,0.06 and an agent runs
  `use <pos> at 0.75,0.5,0.06` on such a panel placed facing east
- **THEN** the click lands on that key, where the turn by the block's facing
  has put it in the world

#### Scenario: Left and right seen from the front
- **WHEN** a panel's model has its front on its north side, and an agent aims at
  the left one of two buttons as seen from in front of the panel
- **THEN** that button has the higher u, whichever horizontal way the panel
  faces

#### Scenario: Point read off the crosshair
- **WHEN** an agent aims at a key by hand, reads `hit=0.73,0.86,0.34` from
  `status`, and later runs `use <pos> at 0.73,0.86,0.34`
- **THEN** the click lands on the same point of the block

#### Scenario: Block aboard a sub-level
- **WHEN** an agent runs `use <plot position> at <u,v,w>` on a block aboard a
  moving sub-level
- **THEN** the point follows the sub-level as a whole-block target does

### Requirement: The hit a click sends
A `use` SHALL report, as `aim` and `hit=u,v,w`, the crosshair hit its click
was sent to the server with, read on the tick the game handled the click. That
hit is where the look ray, aimed at the target, first meets a block's shape,
so with `at <u,v,w>` it differs from the point asked for when the block's
shape does not hold that point on the ray.

#### Scenario: Point off a thin block's shape
- **WHEN** an agent runs `use <pos of a button> at 0.5,0.9,0.5`, above the
  button's shape
- **THEN** the reply's `aim` and `hit=` name where the aimed ray met a shape,
  which is the hit the server received

#### Scenario: Moving target
- **WHEN** an agent runs `use <plot position> any` on a block of a moving
  sub-level
- **THEN** the reported hit is the one of the tick the click was handled, not
  of the tick before

### Requirement: Use clicks only its target
`use <pos>` on a block in the world and `use e<id>` SHALL click only when,
after aiming, the crosshair rests on the target. Otherwise they SHALL fail
with `err obstructed <what> <where>`, naming what is in the way, or with `err
out_of_reach`, and use nothing. A target block without a shape, such as air,
only sets the aim. `use … any` SHALL skip the check.

#### Scenario: Lever behind glass
- **WHEN** an agent runs `use <pos of a lever>` and a glass pane stands
  between the player and the lever
- **THEN** the result is `err obstructed` with the glass pane's id and
  position, and the lever is not switched

#### Scenario: Entity behind a fence
- **WHEN** an agent runs `use e<id>` on a villager and a fence is first on
  the line of sight
- **THEN** the result is `err obstructed` naming the fence, and the fence is
  not used

#### Scenario: Clear line
- **WHEN** an agent runs `use <pos of a lever>` with nothing in the way
- **THEN** the lever is switched and the reply's `aim` names it

### Requirement: Use any reports a click elsewhere
When `use <target> any` clicks a block or entity other than its target, at a
world or a plot position, the reply SHALL carry `off_target=<target>` beside
the `aim` of what it clicked.

#### Scenario: Another block of a panel
- **WHEN** an agent runs `use <pos of a key> any` and another block of the
  same panel is first on the line of sight
- **THEN** the panel's block is clicked, and the reply's `aim` names it and
  carries `off_target=<pos of the key>`

#### Scenario: On target
- **WHEN** an agent runs `use <pos> any` and the crosshair rests on that block
- **THEN** the reply carries no `off_target`

### Requirement: Sneaking use names the item it used
`use … sneak` SHALL report `sneak_item=<id>` when either hand holds an item,
naming the main hand's item, else the off hand's: the game then skips the
block's own use and uses that item on it instead.

#### Scenario: Block in hand
- **WHEN** an agent holding cobblestone runs `use <pos of a chest> sneak`
- **THEN** the reply carries `sneak_item=cobblestone`, and no chest screen
  opens

#### Scenario: Empty hands
- **WHEN** an agent with both hands empty runs `use <pos of a lever> sneak`
- **THEN** the reply carries no `sneak_item`

### Requirement: Mining a block that can't be broken
When the player can't break the block `mine` is about to dig (it would make no
progress: bedrock outside creative, a block adventure mode forbids, a block a
mod makes unbreakable), `mine <pos>` and `mine` at the crosshair SHALL fail at
once with `err unbreakable <block> <pos>` instead of digging until a timeout.
`mine <block|#tag> x<n>` SHALL pass such blocks over, and fail at once with
`err unbreakable <block>` when it finds matching blocks but none it can break.

#### Scenario: Bedrock in survival
- **WHEN** an agent in survival runs `mine <pos of bedrock in reach>`
- **THEN** the result is `err unbreakable bedrock <pos>` on the first tick

#### Scenario: A floor of bedrock
- **WHEN** an agent in survival runs `mine bedrock` in a flat world
- **THEN** the result is `err unbreakable bedrock` at once

#### Scenario: The same block in creative
- **WHEN** an agent in creative runs `mine <pos of bedrock in reach>`
- **THEN** the bedrock is broken, as creative breaks any block

### Requirement: A key alone is pressed
`key <key> [Nt]` without `down`, `up` or `press` SHALL press the key, the same
as `key press <key> [Nt]`. The words `up` and `down` alone name the arrow
keys.

#### Scenario: Escape
- **WHEN** an agent runs `key escape`
- **THEN** escape is pressed for one tick, as with `key press escape`

#### Scenario: Arrow key
- **WHEN** an agent runs `key up`
- **THEN** the up arrow key is pressed

### Requirement: Goto checks its target cell
`goto <pos>` without `near=` SHALL fail at once with `err no_path` and the
reason when the player can't stand in the target cell: a block fills it or the
cell above, nothing below holds the player up, it is dangerous, or its chunk
isn't loaded. When the target is a block with room to stand on top, the error
SHALL name the cell above. Water and climbable cells count as cells to stand
in.

#### Scenario: Target is a block to stand on
- **WHEN** an agent runs `goto <pos of a stone block>` with air above it
- **THEN** the result is `err no_path` naming the stone, and the cell above it
  as the one to walk to, without the player moving

#### Scenario: Target in mid-air
- **WHEN** an agent runs `goto ~0,5,0` with nothing under that cell
- **THEN** the result is `err no_path` saying nothing is there to stand on

### Requirement: An interrupt names its input
`err interrupted` and the `interrupted` event SHALL name the input that took
control back: `input=key` with `key=<name>`, the name `key press` takes for
that key; `input=button` with `button=left`, `right`, `middle` or the button's
number; `input=scroll`; or `input=move` with `dx=` and `dy=`, how far the
cursor moved in window pixels since its previous position.

#### Scenario: A key press
- **WHEN** the user presses W in the focused game window while an agent's
  `wait 200t` runs
- **THEN** the wait ends with `err interrupted input=key key=w`, and the next
  reply carries `! interrupted player input=key key=w`

#### Scenario: A mouse move
- **WHEN** the user moves the mouse to the right in the focused game window
  while an agent action runs
- **THEN** the action ends with `err interrupted input=move` and a positive
  `dx=`: how far the cursor moved in the event that took control back, which
  the game reports a few pixels at a time

### Requirement: Place errors say how to recover
When the inventory lacks the item, `place`'s `err no_item` SHALL say to give it
first. When the target cell is taken, the error SHALL name the cell above if
that one is free.

#### Scenario: Hint for a missing item
- **WHEN** an agent runs `place torch ~1,0,0` with no torch in the inventory
- **THEN** the result is `err no_item torch` with a hint to give it first

#### Scenario: Place on a taken cell
- **WHEN** an agent runs `place stone ~0,-1,1` where grass is and air is above
- **THEN** the error names `grass_block` and says `~0,0,1` above is free

### Requirement: A bare sneak taps it
A bare `sneak` SHALL tap sneak: hold it for a few ticks and let go, long enough
for the server to see it, which dismounts a seated or riding player. `sneak on`
and `sneak off` keep holding and releasing it.

#### Scenario: Dismount with a tap
- **WHEN** an agent riding a minecart runs `sneak`
- **THEN** the player leaves the minecart, and sneak is no longer held once
  the command has answered

### Requirement: Face says how far a missed target is
When `face` ends with the crosshair on nothing, its reply SHALL add
`dist=<blocks>`, from the player's eyes to the point it aimed at, and
`reach=<blocks>`, the player's interaction range for that kind of target:
the block range for a position, the entity range for an entity. Both are
given to the hundredth. A reply whose crosshair rests on a block or entity
carries neither.

#### Scenario: Target beyond reach
- **WHEN** an agent runs `face ~8,0,0` on a block 8 blocks away, in survival
- **THEN** the reply is `aim:nothing` with `dist=` above 7 and `reach=4.5`

#### Scenario: Empty air within reach
- **WHEN** an agent runs `face ~2,1,0` where that cell and the line to it are
  air
- **THEN** the reply is `aim:nothing` with `dist=` below `reach=`

#### Scenario: Target hit
- **WHEN** an agent runs `face ~2,0,1` on a block within reach
- **THEN** the reply names the block and carries no `dist=` or `reach=`

### Requirement: Key chords
In `key`, `+` SHALL join keys into a chord, each part a key name or key-mapping
id. A press SHALL hold the earlier keys in order, press the last for the given
ticks, then release all of them in reverse order. `key down` SHALL hold every
key of the chord in order, and `key up` SHALL release them in reverse. A chord
with an empty part SHALL fail with `err syntax` and press nothing.

#### Scenario: A debug combination
- **WHEN** an agent runs `key f3+b` in a world
- **THEN** hitboxes are shown, the debug screen stays closed, and no key is
  held once the command has answered

#### Scenario: Holding a chord
- **WHEN** an agent runs `key down left_shift+w`, waits, and then runs `key up
  left_shift+w`
- **THEN** the player sneaks forward in between, and afterwards stands still
  and no longer sneaks

#### Scenario: Empty part
- **WHEN** an agent runs `key f3+`
- **THEN** the result is `err syntax` and no key is pressed
