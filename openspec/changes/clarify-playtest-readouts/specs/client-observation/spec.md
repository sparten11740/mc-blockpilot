## ADDED Requirements

### Requirement: Volume and pitch asked for
A row of `sounds` or `sounds playing` SHALL give as `vol` and `pitch` what the
sound plays with: the volume and pitch it was played with, times those its
resource (sounds.json) gives the variant chosen. When these differ from the
values it was played with, the row SHALL also carry those as
`asked:<volume>/<pitch>`, in the fields `asked_volume` and `asked_pitch`.

#### Scenario: Sound with variants
- **WHEN** a mod plays `minecraft:item.spyglass.use`, whose resource gives
  volume 0.5 and variants pitched 1.15 to 1.55, at volume 0.6 and pitch 1.2,
  and an agent runs `sounds match=item.spyglass.use`
- **THEN** the row has `vol0.3`, a pitch between 1.38 and 1.86, and
  `asked:0.6/1.2`

#### Scenario: Sound its resource leaves as played
- **WHEN** a sound whose resource sets neither volume nor pitch is played at
  volume 0.6 and pitch 1.2
- **THEN** its row has `vol0.6 pitch1.2` and no `asked:`

### Requirement: Placed camera in frame replies
While a camera placed with `camera at` is in effect, a `screenshot` reply
SHALL carry `camera <x,y,z>`, the camera's absolute position, for each frame
captured from it, and a `pixels` reply SHALL carry it for the frame it
measured. A `pixels` region SHALL NOT be named `camera`.

#### Scenario: Camera left in place
- **WHEN** an agent ran `camera at 40,90,-20` earlier and now runs `screenshot`
- **THEN** the reply carries `camera 40,90,-20`, and after `camera reset` a
  screenshot's reply carries none

#### Scenario: Region named camera
- **WHEN** an agent runs `pixels camera=0,0,1,1`
- **THEN** the result is `err syntax` saying `camera` is taken
