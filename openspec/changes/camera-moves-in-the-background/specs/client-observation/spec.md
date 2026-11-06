## ADDED Requirements

### Requirement: Camera moves in the background
`camera move … over <duration> background` SHALL answer at once with `ok
moving over=<n>t` and glide the camera on its own over the duration, as the
move would in the foreground, while the script and the scripts after it go
on. A `camera at`, `camera move` or `camera reset` SHALL replace it, and
`stop` and real input SHALL end it where the last frame drew it.

#### Scenario: Server steps while the camera moves
- **WHEN** an agent runs `camera at ~0,2,0; camera move ~10,2,0 over 40t
  background; server settle=1t time query gametime; wait 20t; status`
- **THEN** the move answers before the server step runs, and `status`
  names the camera about 5 blocks along

#### Scenario: Stopped in the background
- **WHEN** session B runs `stop` 20 ticks into a move session A started in
  the background
- **THEN** `status` names the camera about 5 blocks along, where it stays

### Requirement: Waiting for a camera move
`camera wait` SHALL answer once the camera move running in the background
has ended, as `camera at` answers for its end point, and at once with the
camera's position when no move runs.

#### Scenario: Wait for the end
- **WHEN** an agent runs `camera move ~10,2,0 over 40t background; camera
  wait` from a camera placed at `~0,2,0`
- **THEN** `camera wait` answers about 40 ticks later with `camera` at the
  absolute position of `~10,2,0`

#### Scenario: Nothing moving
- **WHEN** an agent runs `camera at ~0,2,0; camera wait`
- **THEN** `camera wait` answers with `camera` at the absolute position of
  `~0,2,0`
