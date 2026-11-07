# Tasks

## 1. Logs per world (client-observation)

- [ ] 1.1 Add `TickLog.clear`, which empties the log and forgets what it dropped, with a unit test; have `Observations` clear the sound, message and particle logs, the sounds' start ticks and the repeated-message memory when the player enters a world, under the same lock as its other methods, as sounds and chat arrive from the game's own paths; add a smoke check that plays a sound and sends a chat line, joins the world again and finds neither in `sounds` or `messages`; verify the check fails before the change and passes after it on the 1.21.1 NeoForge dev client
- [ ] 1.2 Say in `docs/commands.md` and the 1.1.0 section of `CHANGELOG.md` that the logs start anew in each world

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, the new check included

## Workflow follow-up

- Archive this change, dropping its tasks.md.
- Pushing needs Jan's go-ahead.
