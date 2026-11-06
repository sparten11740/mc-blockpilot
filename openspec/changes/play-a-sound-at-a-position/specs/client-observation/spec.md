## ADDED Requirements

### Requirement: Playing a sound on the client
`sound <id> [at <pos>] [volume=<v>] [pitch=<p>] [source=<category>]` SHALL
play the sound event on this client only, sending nothing to any server.
With `at`, it SHALL start there and fade with distance from the camera as
rendered; without, it SHALL play as an interface sound. `volume=` (default 1)
above 1 SHALL carry it farther, as with `playsound`; `pitch=` takes 0.5 to 2
(default 1), `source=` a sound category (default `master`).

#### Scenario: Heard from a placed camera
- **WHEN** an agent runs `camera at ~40,2,0; sound minecraft:block.bell.use
  at ~40,0,3`
- **THEN** `sounds playing match=block.bell.use` lists the bell at `~40,0,3`
  with `asked:1/1`, the volume and pitch it was played with, playing about
  3.6 blocks from the listener, which is the camera, not 40 from it

#### Scenario: An interface sound
- **WHEN** an agent runs `sound minecraft:ui.button.click`
- **THEN** `sounds` lists the click with `ui` for its position

#### Scenario: On a remote server
- **WHEN** an agent connected to a multiplayer server runs `sound
  minecraft:block.bell.use at ~0,0,3`
- **THEN** the bell plays on this client and nothing is sent to the server

### Requirement: What playing a sound answers
The reply to `sound` SHALL give the sound's id, its absolute position or
`ui`, its volume, pitch and category, and the tick it started, plus `muted`
when the volume of its category or the master volume is 0. An id the client
doesn't know SHALL fail as `sounds match=` does, with `err syntax unknown
sound <id>` naming the closest ids. A volume below 0 or a pitch outside 0.5
to 2 SHALL fail with `err syntax`.

#### Scenario: Muted category
- **WHEN** an agent runs `option soundCategory_block 0; sound
  minecraft:block.bell.use at ~0,0,3 source=block`
- **THEN** the reply carries `muted`, `sounds` lists the start, and `sounds
  playing` doesn't list it

#### Scenario: Unknown id
- **WHEN** an agent runs `sound minecraft:block.bell.uze`
- **THEN** the result is `err syntax unknown sound minecraft:block.bell.uze`
  naming `minecraft:block.bell.use`, and nothing plays

#### Scenario: Pitch out of range
- **WHEN** an agent runs `sound minecraft:block.bell.use pitch=3`
- **THEN** the result is `err syntax` naming the range 0.5 to 2
