# Tasks

## 1. Sounds (client-observation)

- [x] 1.1 Add an accessor for `AbstractSoundInstance`'s `volume` and `pitch` to both version source sets and both mixin configs, record the asked values with each logged start, and add `asked:<volume>/<pitch>` (`asked_volume`, `asked_pitch`) to `sounds` and `sounds playing` rows where they differ from the played values (D1, D2), with `help sounds` and the README's observe row; verify on the dev client that `server playsound minecraft:item.spyglass.use master @a ~ ~ ~ 0.6 1.2` is listed with `vol0.3`, a pitch between 1.38 and 1.86 and `asked:0.6/1.2`, that a sound whose resource sets neither volume nor pitch has no `asked:`, and that in `sounds playing` a music disc played at volume 0.5, which its resource leaves as played, has no `asked:` while `ambient.basalt_deltas.loop` played at 0.8, which its resource halves, shows `vol0.4 asked:0.8/1`

## 2. Placed camera (client-observation)

- [x] 2.1 Record the placed camera's position for each rendered frame, add `camera <x,y,z>` to `screenshot` frames and `pixels` replies taken from it, and refuse a `pixels` region named `camera` (D3, D4), with `help screenshot`, `help pixels` and the README's observe row; verify on the dev client that `camera at ~5,5,5 look ~0,1,0; screenshot burst=2 every=5t; pixels` carries `camera` on both frames and on `pixels`, that after `camera reset` and a frame `screenshot` and `pixels` carry none, and that `pixels camera=0,0,1,1` fails with `err syntax`

## 3. Integration checks

- [x] 3.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
