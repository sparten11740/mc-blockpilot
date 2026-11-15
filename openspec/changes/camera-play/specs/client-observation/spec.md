## ADDED Requirements

### Requirement: A shot list on the camera's clock
`camera play <shot> [| <shot>]…` SHALL play its shots one after another on
the camera's own clock and answer at once with `shots=` and `over=`, while
the script and later scripts go on. A shot SHALL be a `camera at`, `move`,
`orbit` or `follow` without `camera`, or `hold <duration>`, starting as its
command would then. All shots SHALL be checked before the first starts.

#### Scenario: A cut, a hold and a move beside the script
- **WHEN** an agent runs `camera play at ~0,3,-6 look ~0,1,0 | hold 10t |
  move ~6,3,0 look ~0,1,0 over 20t fov=40; wait 2t; status; camera wait; status`
- **THEN** the play answers `shots=3 over=30t` at once, the first status
  shows the camera at the cut, and the second, after `camera wait`, at the
  move's end with `cam_fov=40`

#### Scenario: A shot that isn't one
- **WHEN** an agent runs `camera play at ~0,2,0 | jump ~1,2,3`
- **THEN** the result is `err syntax`, naming the shot, and the camera
  doesn't change

### Requirement: Ending a shot list
Another camera command, `stop`, real input or a reset SHALL end a play,
leaving the camera where it is drawn. `camera wait` SHALL answer once the
last shot has ended, or with the error of a shot that couldn't start, such
as one whose entity is gone.

#### Scenario: A camera at in the middle
- **WHEN** a play of two 60-tick moves runs and an agent runs `camera at
  ~0,2,-4` after 20 ticks
- **THEN** the camera stays at that point, and no later shot moves it

#### Scenario: A shot that can't start
- **WHEN** an agent runs `camera play hold 5t | follow e999999; camera wait`
- **THEN** the play answers at once, and `camera wait` answers
  `err no_entity e999999`
