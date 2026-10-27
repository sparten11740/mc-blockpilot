# sable-sublevels Specification

## Purpose

Lets agents work with Sable sub-levels (assembled, moving structures whose
blocks live in a plot away from where they are seen) when Sable is installed,
without making Sable a dependency of Blockpilot.

## Requirements

### Requirement: Sable stays optional
Blockpilot SHALL install, load and behave as it does without Sable when Sable
is not installed, on every loader it ships for. Nothing of Sable's is bundled
with it, and its metadata declares no Sable dependency the loader enforces. The
`sable:` commands exist only while the integration is on.

#### Scenario: Without Sable
- **WHEN** Blockpilot runs in a client without Sable
- **THEN** it starts normally, every command answers as without this change,
  and `help` lists no `sable` namespace

#### Scenario: Integration on
- **WHEN** Blockpilot runs in a client with a compatible Sable
- **THEN** `help` lists the `sable` namespace and its commands

### Requirement: Compatible Sable versions
The integration SHALL turn on only when the installed Sable offers a sub-level
API of a version Blockpilot supports. Otherwise Blockpilot SHALL run as
without Sable, log once which Sable version it found and why the integration
is off, and say the same in `help sable`. An unsupported Sable SHALL never stop
the game from starting.

#### Scenario: Newer Sable with a changed API
- **WHEN** a Sable whose sub-level API Blockpilot doesn't support is installed
- **THEN** the game starts, the log and `help sable` name that version and say
  the integration is off, and the other commands work

### Requirement: Failures stay inside the integration
When a call into Sable fails while a command runs, whether a method is missing
or an error is thrown, that command SHALL fail with `err sable <message>` and
the game SHALL keep running. After a missing class or method, the integration
SHALL turn off until the game restarts, as if Sable were unsupported.

#### Scenario: Method missing at run time
- **WHEN** a Sable call made by `sable:info` finds a method missing
- **THEN** the result is `err sable …`, later `sable:` commands are gone, and
  the built-in commands work as without Sable

### Requirement: Plot positions in replies
With the integration on, a position inside a sub-level's plot SHALL be printed
as absolute plot coordinates, never as an offset from the player, followed by
the sub-level's name and the position's place in the world as
`sub=<name> world=<x,y,z>`. Printed plot positions paste back unchanged.

#### Scenario: Aim at a block aboard
- **WHEN** the crosshair rests on a button aboard an assembled ship and an
  agent runs `status`
- **THEN** the aim reads like `aim:stone_button 20481129,140,20487316
  sub=ship world=12.5,71.2,-40.8`, and `block 20481129,140,20487316` reads
  that button

### Requirement: Plot positions as arguments
With the integration on, commands that take a position SHALL accept a plot
position and read or act on the block aboard. A reply about a block aboard
SHALL name its sub-level and world position as replies do for aim. `goto`
SHALL refuse a plot position and point to `sable:tp`.

#### Scenario: Reading a block aboard
- **WHEN** an agent runs `block <plot position of a seat>`
- **THEN** the reply is the seat with its state, `sub=<name>` and its world
  position

#### Scenario: Walking to a block aboard
- **WHEN** an agent runs `goto <plot position>`
- **THEN** the result is `err syntax` saying `goto` can't walk onto a
  sub-level and naming `sable:tp`

### Requirement: Aiming follows a sub-level
`face` and `use` at a plot position SHALL aim at that block where its sub-level
is on each tick, so a moving or turning sub-level doesn't throw the aim off,
whether the player stands beside it or rides it.

#### Scenario: Use while the ship moves
- **WHEN** an agent runs `use <plot position of a lever>` on a ship under way,
  with a clear line of sight
- **THEN** the lever is used, although its world position changed during the
  aim

#### Scenario: Use while riding the ship
- **WHEN** an agent seated aboard a ship flying at speed runs `use <plot
  position of a lever aboard>`
- **THEN** the lever is used on the first try

### Requirement: Use clicks only its target aboard
`use` at a plot position SHALL click only on a tick when the crosshair rests on
the target. When the crosshair keeps ending on another block for half a
second, it SHALL fail with `err obstructed <block> <position>`, naming what is
in the way instead of using it. `use <pos> any` SHALL skip that check and click
whatever the crosshair ends on.

#### Scenario: Seat behind a wall panel
- **WHEN** an agent runs `use <plot position of a seat>` and a wall panel of
  the same ship covers the seat from where the player stands
- **THEN** the result is `err obstructed` with the wall panel's id and plot
  position, and nothing is used

#### Scenario: Key on a multi-block panel
- **WHEN** an agent runs `use <point on a touch key> any` and another block of
  the same panel is first on the line of sight
- **THEN** the click goes to the panel, which the panel's mod resolves to the
  key

### Requirement: Cameras follow a sub-level
`camera at` and its `look` target SHALL accept plot positions and, for a plot
position, follow its sub-level each rendered frame, so a camera placed on or
aimed at a ship rides along with it.

#### Scenario: Chase camera
- **WHEN** an agent runs `camera at <plot position behind the stern> look
  <plot position of the helm>` while the ship moves
- **THEN** screenshots taken along the way keep the ship in the same place in
  the frame

### Requirement: Referring to a sub-level
A command that takes a sub-level SHALL accept its UUID, its name, or `here`:
the sub-level the player stands on or rides, else the one under the crosshair.
A name more than one sub-level has SHALL fail with `err ambiguous` listing
their UUIDs. One that matches none fails with `err no_sublevel`.

#### Scenario: Two ships with one name
- **WHEN** two sub-levels are named `ship` and an agent runs `sable:info
  ship`
- **THEN** the result is `err ambiguous` with both UUIDs

### Requirement: Listing sub-levels
`sable:list [radius]` SHALL list the sub-levels the client knows within the
radius (default 128 blocks), nearest first. Each row gives the name, UUID,
world position, distance, speed and size. `sable:info <sub-level>` SHALL give
one sub-level's world position, heading, pitch and roll, velocity, world and
plot bounds, and whether the player rides it.

#### Scenario: Ship nearby
- **WHEN** an agent runs `sable:list` next to a ship at rest
- **THEN** a row names it with its UUID, world position and speed 0

### Requirement: Converting between plot and world
`sable:world <plot position>` SHALL give the world position of a point in a
plot as its sub-level lies now. `sable:plot <sub-level> <world position>`
SHALL give the plot position that is at that world point.

#### Scenario: Round trip
- **WHEN** an agent runs `sable:plot here <world position>` and then
  `sable:world` with the plot position it got
- **THEN** the second reply is the world position the agent started from,
  within a hundredth of a block

### Requirement: Finding blocks aboard
`sable:find <sub-level> <block|#tag|*pattern> [limit]` SHALL find matching
blocks aboard the sub-level, nearest to the player first, and give each one's
plot and world position. With `side=server` it reads the server's copy, as
`find` does, and finds the sub-level there too, so also one the client has not
received.

#### Scenario: Door aboard
- **WHEN** an agent runs `sable:find here *door* 3` aboard a ship
- **THEN** the doors aboard are listed with plot and world positions, in one
  call

#### Scenario: Sub-level the client has not received
- **WHEN** an agent runs `sable:find <UUID> *door* side=server` for a ship the
  server holds and the client has not received
- **THEN** the doors aboard are listed from the server's copy

#### Scenario: Tilted ship
- **WHEN** an agent runs `sable:find` and `sable:tp` with a block on a long
  ship pitched by 15°
- **THEN** both find the block as they do while the ship flies level

### Requirement: Teleporting next to a block aboard
`sable:tp <plot position>` SHALL put the player on a free spot next to that
block aboard, in a world with commands allowed, and answer with where the
player now stands. When no spot within two blocks has room, it SHALL fail with
`err no_room`.

#### Scenario: Onto the bridge
- **WHEN** an agent runs `sable:tp <plot position of the captain's seat>`
- **THEN** the player stands next to the seat aboard the ship, and
  `status` shows them riding it

### Requirement: Targets built without Sable
A target for a Minecraft version Sable had no release for when the target was
built SHALL ship without the Sable integration. With a Sable installed, it
SHALL run as without Sable, and the log and `help sable` SHALL say that this
Blockpilot build has no Sable integration.

#### Scenario: Sable on a target without the integration
- **WHEN** a Sable is installed beside a Blockpilot jar built without the
  Sable integration
- **THEN** the game starts, the log and `help sable` say that this build has
  no Sable integration, and the other commands work

### Requirement: Height above ground
`sable:info` SHALL give a sub-level's height above ground as `agl=<blocks>`:
from the bottom of its world bounds down to the top of the highest block with a
collision shape under the centre of those bounds, not counting blocks aboard
any sub-level. With nothing below in loaded chunks it SHALL give `agl=none`.

#### Scenario: Ship parked on the ground
- **WHEN** an agent runs `sable:info <ship>` for a ship resting on grass
- **THEN** the reply carries `agl=0`

#### Scenario: Ship in flight
- **WHEN** a ship hovers with its lowest block 30 blocks above the terrain
- **THEN** `sable:info <ship>` carries `agl=30`

### Requirement: Flight readout
`sable:info <sub-level> brief` SHALL answer with heading, pitch and roll in
degrees, speed and climb rate in blocks per second, and the height above
ground, and nothing else, so `sample sable:info <sub-level> brief every <Nt>`
traces a take-off or a climb in short rows.

#### Scenario: Climb trace
- **WHEN** an agent runs `sample sable:info ship brief every 10t x6` while a
  ship climbs
- **THEN** each of the six rows gives `yaw`, `pitch`, `roll`, `speed`, `climb`
  and `agl`, and `agl` grows from row to row
