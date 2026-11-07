## ADDED Requirements

### Requirement: A recording that ends with its script
`record script [name]`, with the options `record start` takes, SHALL start
a recording as `record start` does and end it when its script ends: after
the last step, at an error that stops the script, or when `stop`, real
input or a closing session cancels it. The script's reply SHALL come once
the file is saved, with the take's summary, the fields `record stop` gives,
as the event `! record …`.

#### Scenario: Filmed to the end
- **WHEN** an agent runs `record script take fps=20 audio=off; wait 20t`
- **THEN** the reply comes after the file is saved, with `ok recording
  take` and `ok`, and the event `! record` naming `take.mp4`, about 1 s
  long

#### Scenario: A failing step
- **WHEN** an agent runs `record script take2 fps=20 audio=off; wait 10t;
  entity e999999; wait 10t`
- **THEN** `entity` fails with `err no_entity`, the rest is skipped, the
  reply carries `! record` with `take2.mp4`, and `record status` then
  answers that no recording runs

#### Scenario: Stopped from another call
- **WHEN** session B runs `stop` while session A's `record script take3
  audio=off; wait 200t` waits
- **THEN** A's reply ends with `err cancelled`, carries `! record` with
  `take3.mp4`, and no recording runs afterwards

### Requirement: A script's recording stopped earlier
A `record stop` in the script SHALL end its `record script` recording
earlier, as it ends any recording, and the script's end then SHALL leave
any recording running by then alone.

#### Scenario: Stopped by the script
- **WHEN** an agent runs `record script take4 audio=off; wait 10t; record
  stop; record start take5 audio=off`
- **THEN** `record stop` answers with `take4.mp4`, the reply carries no `!
  record` event, and `take5` still records after the reply

## MODIFIED Requirements

### Requirement: What ends a recording
Only `record stop`, the end of a timed recording, the end of the script that
ran `record script`, an encoder failure and the game closing SHALL end a
recording. Closing the game SHALL save it first. Leaving a world, closing
the connection that started a recording begun with `record start` and
turning agent control off SHALL NOT end it.

#### Scenario: Game closed while recording
- **WHEN** a recording runs and the player closes the game
- **THEN** the file is saved, playable and complete up to the close, before
  the game exits

#### Scenario: Leaving the world
- **WHEN** an agent runs `record start; leave; wait 2s; record stop`
- **THEN** the video goes on from the world to the title screen
