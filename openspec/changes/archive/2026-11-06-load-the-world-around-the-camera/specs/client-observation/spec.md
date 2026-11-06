## ADDED Requirements

### Requirement: A camera away from the player
With `away`, `camera at` SHALL make the integrated server treat the camera as
where the player is for the world it loads, ticks and sends, while that camera
is placed: chunks within the view distance of the camera load and reach the
client, those near it tick and may spawn mobs, entities within tracking range of
it are sent, and sounds, particles and explosions within range of it play. How
near a player mobs spawn and despawn SHALL still go by the player.

#### Scenario: A set far from the player
- **WHEN** an agent standing at 0,-60,0 in a flat world runs
  `camera at 1000,-58,0 look 1000,-60,5 away; settle; block 1000,-61,0`
- **THEN** the camera's reply names `air` as its block, `settle` answers
  `settled=`, and `block` reads `grass_block`

#### Scenario: A sound at the set
- **WHEN** an agent runs `server playsound minecraft:block.bell.use master @a
  1000 -60 5` with that camera placed
- **THEN** `sounds match=block.bell.use` lists the bell at `1000,-60,5`, not
  next to the player

#### Scenario: An entity at the set
- **WHEN** an agent runs `server summon minecraft:lightning_bolt 1000 -60 5`
  with that camera placed
- **THEN** `sounds` lists `entity.lightning_bolt.thunder`, which the bolt
  sounds only on a client that it reached

#### Scenario: Particles at the set
- **WHEN** an agent runs `server particle minecraft:flame 1000 -59 5 0.2 0.2
  0.2 0 50` with that camera placed
- **THEN** `particles` counts flame particles

### Requirement: An away camera answers once its chunk has come
`camera at … away` SHALL answer once the client has the camera's chunk, or
after 10 seconds, and a camera move from a camera placed with `away` SHALL
keep it away, so the server's world follows the camera along the move.

#### Scenario: A move from the set
- **WHEN** an agent runs `camera move 1400,-58,0 look 1400,-60,5 over 40t;
  settle; block 1400,-61,0` with that camera placed
- **THEN** the move's reply names `air` as its block, and `block` reads
  `grass_block`: the server's world followed the camera along the move

### Requirement: The player waits while the camera is away
While a camera placed with `away` is farther from the player than the view
distance, the server SHALL send nothing around the player, and the player
SHALL stand where it was, neither moving nor falling, until the camera
resets; then the server SHALL send the chunks around the player again.

#### Scenario: Standing still
- **WHEN** an agent runs `status; wait 1s; status` with a camera placed 1000
  blocks away with `away`
- **THEN** both `status` replies give the same position

#### Scenario: Back to the player
- **WHEN** an agent runs `camera reset; settle; block ~0,-1,0`
- **THEN** `settle` answers `settled=`, `block` reads the block under the
  player, and the player moves again

### Requirement: What ends a camera away
A camera placed with `away` SHALL stop moving the server's world when it is
reset or turned off, when `camera at` places a camera without `away`, when
the player leaves the world or changes dimension, and when agent control is
turned off. `camera at … away` SHALL fail with `err no_server` without an
integrated server and with `err no_cheats` where commands aren't allowed.

#### Scenario: Placed again without away
- **WHEN** an agent runs `camera at ~5,2,0` after a camera placed with `away`
- **THEN** the server sends the chunks around the player again, and the new
  camera renders from `~5,2,0`

#### Scenario: On a remote server
- **WHEN** an agent connected to a multiplayer server runs `camera at
  1000,70,0 away`
- **THEN** the result is `err no_server`, and no camera is placed

## MODIFIED Requirements

### Requirement: Waiting for the view to settle
`settle [timeout=<duration>]` SHALL answer once every chunk within the view
distance of the player, or of a camera placed with `away`, has arrived and
the chunk compile queue has stayed empty, with the same number of sections
rendered, for 3 ticks, with `settled=<seconds>`, or at `timeout=` (default
20s) with `unsettled pending=<n>`, plus `missing=<n>` while chunks have yet
to arrive. It SHALL change nothing.

#### Scenario: Camera moved far
- **WHEN** an agent runs `camera at ~150,0,0; settle; screenshot`
- **THEN** `settle` answers with `settled=` once the sections around the
  camera have compiled, and the screenshot shows them

#### Scenario: Nothing new in view
- **WHEN** an agent runs `settle` while the view hasn't changed
- **THEN** it answers with `settled=` after a few ticks

#### Scenario: New terrain still generating
- **WHEN** an agent raises the render distance where the terrain around the
  player isn't generated yet, and runs `settle timeout=120s`
- **THEN** `settle` answers once the chunks within the new view distance have
  arrived, so `block` at the edge of that distance finds its chunk loaded

#### Scenario: A camera away
- **WHEN** an agent runs `camera at 1000,-58,0 away; settle`
- **THEN** `settle` answers once the chunks within the view distance of the
  camera have arrived and compiled, not those around the player
