# Tasks

## 1. Trusted players (agent-safety)

- [x] 1.1 Add `trustedPlayers` (empty by default) and `agentCommands` (`all` by default, or `play`) to `protocol/Config`, saved atomically when the list changes; verify with unit tests for the defaults, a round trip and an unknown `agentCommands` value
- [ ] 1.2 Pass the sender with player chat from the NeoForge, Fabric and vanilla chat hooks, and withhold an untrusted player's text as `withheld from=<name>` before `Observations` and `Events` see it (D2), the agent's own player and system messages passing; verify with unit tests of the decision and that `messages match=` can't see withheld text
- [ ] 1.3 Detect servers that don't verify accounts (D3) and trust nobody but the agent's own player there; verify with unit tests of the offline-UUID check
- [ ] 1.4 Handle `/blockpilot trust <player>`, `untrust <player>` and `trusted` at the `sendCommand` hook on every target (D4), never sent to the server, with local replies, saved to the config, and taken only from a person: refused after agent input reached that chat screen, for an injected enter, from `cmd` and from `chat`, failing the agent's command with `err not_allowed`; verify with unit tests of the origin rules and in a 1.21.1 NeoForge dev client that typing it works while `cmd blockpilot trust X` and `type` plus `key enter` are refused
- [ ] 1.5 Add the `trust` query and `BlockpilotApi.isTrusted(UUID)` (D6); verify with unit tests and that the 2.0.0 API fixture test still passes; document `trust`, withheld chat and `err not_allowed` in docs/commands.md and `isTrusted` in docs/extending.md

## 2. Play-only agents (agent-safety)

- [ ] 2.1 Refuse the D5 command set at parse time under `agentCommands: "play"`, nested steps included, with `err not_allowed <command>` before any step runs; verify with unit tests on the engine, including a refused step after an allowed one and inside `if`
- [ ] 2.2 Drop slash commands sent with agent origin under `play` at the `sendCommand` hook and fail the sending command with `err not_allowed cmd`; verify in a 1.21.1 NeoForge dev client that an agent's `/op` typed into chat isn't sent while a person's is; document the profile in docs/commands.md

## 3. Safety guide

- [ ] 3.1 Write docs/safety.md covering trusted players and the in-game commands, withheld chat, unverified servers, the play profile, text from players as untrusted input (plugin-relayed chat, signs, books, names, and the voice add-on's speech), and running the agent with Blockpilot's tools only, with a ready Claude Code permission setup; link it from the README, and add the BREAKING note and the new settings to the 1.1.0 section of CHANGELOG.md; verify every setting and command named there exists

## 4. Integration checks

- [ ] 4.1 Add smoke checks: `trust` lists nobody by default; `cmd blockpilot trust X` answers `err not_allowed` and trusts nobody; with `agentCommands: "play"` in the run's config, `cmd` and `server` scripts fail with `err not_allowed` before any step while `goto` works; verify they pass on 1.21.1 NeoForge
- [ ] 4.2 Run tools/test_targets.py on all 11 targets; verify every run passes

## Workflow follow-up

- Once the `two-client-test-runs` change is applied, add a two-client check: the second client's chat is withheld, then has its text once trusted in the first client's config.
- Archive this change.
- Pushing needs Jan's go-ahead.
