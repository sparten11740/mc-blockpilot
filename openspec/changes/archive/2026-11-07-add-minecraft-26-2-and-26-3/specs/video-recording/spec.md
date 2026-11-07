# Spec Delta

## MODIFIED Requirements

### Requirement: What ends a recording
Only `record stop`, the end of a timed recording, the end of the script that
ran `record script`, an encoder failure, a GPU that doesn't finish reading a
frame back and the game closing SHALL end a recording. Closing the game SHALL
save it first. Leaving a world, closing the connection that started a
recording begun with `record start` and turning agent control off SHALL NOT
end it.

#### Scenario: Game closed while recording
- **WHEN** a recording runs and the player closes the game
- **THEN** the file is saved, playable and complete up to the close, before
  the game exits

#### Scenario: Leaving the world
- **WHEN** an agent runs `record start; leave; wait 2s; record stop`
- **THEN** the video goes on from the world to the title screen

#### Scenario: The GPU stalls during a recording
- **WHEN** the GPU takes too long reading a frame back while recording
- **THEN** the recording ends with `err record_failed`, and the game keeps
  running

#### Scenario: The GPU stalls while a recording stops
- **WHEN** the GPU takes too long reading the last frames back as a recording
  stops
- **THEN** the video is finished with the frames read back so far, and the game
  keeps running
