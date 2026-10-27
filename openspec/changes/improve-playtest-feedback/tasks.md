# Tasks

## 1. CLI and help (dev-integration)

- [x] 1.1 Catch the reply's read timeout in the CLI and say how long it waited, that the script was cancelled and to raise `--timeout` (D7), with a `MainTest` case and the README's exit-code paragraph; verify by the test and by running the launcher with `--timeout 3` on `wait 10s` against the dev client
- [x] 1.2 Name `option fov 110` and `option renderClouds off` in `help camera` for effects spanning hundreds of blocks (D8), with the README's observe row; verify the help text on the dev client

## 2. Waits (command-language)

- [x] 2.1 End `wait <query> until` at once when the query fails with `err syntax`, and keep re-running on other errors (D6), with engine tests for both and the README's wait notes if they cover errors; verify on the dev client that `wait find stoen 8 until count>0 timeout=60s` fails on its first tick naming `stoen`

## 3. Sounds (client-observation)

- [ ] 3.1 Fail `sounds` on a `match=` without `*` that names no known sound event, with the closest ids and the `*<name>*` form (D5), with `help sounds` and the README; verify on the dev client that `sounds match=anvil` fails naming `minecraft:block.anvil.land` among the closest and `match=*anvil*`, `sounds match=block.anvil.land` and `sounds match=*anvil*` answer ok, and `wait sounds match=block.anvil.lnad until count>0` fails at once
- [ ] 3.2 Track the newest dropped tick of the sound and message logs and add `from=<tick>` to `sounds` and `messages` when their window lost entries (D4), with a unit test of the ring if it can run without the game, `help sounds`, `help messages` and the README; verify on the dev client that `sounds` carries `from=` after more than 2,000 starts (e.g. by repeating `server playsound` in a loop) while `sounds since=1s` then doesn't, and that a fresh client's `sounds` carries none
- [ ] 3.3 Add the sound manager and channel-map accessors to both version source sets and both mixin configs, record first start ticks, and add `sounds playing [since=] [match=] [limit=]` (D1, D2, D3), with `help sounds` and the README; verify on the dev client that a playing music disc in a jukebox is listed with `loop` absent, its position and a growing `played=`, that it is gone once the disc is taken out, that `wait sounds playing match=*music_disc* until count==0` returns when it is taken out, and that with the record volume at 0 (set in the dev client's options.txt before its start, then put back) the disc's start is in `sounds` and not in `sounds playing`

## 4. Integration checks

- [ ] 4.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
