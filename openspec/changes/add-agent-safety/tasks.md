# Tasks

## 1. Trusted players (agent-safety)

- [x] 1.1 Add `trustedPlayers` (empty by default) and `agentCommands` (`all` by default, or `play`) to `protocol/Config`, saved atomically when the list changes; verify with unit tests for the defaults, a round trip and an unknown `agentCommands` value
- [x] 1.2 Pass the sender with player chat from the NeoForge, Fabric and vanilla chat hooks, and withhold an untrusted player's text as `withheld from=<name>` before `Observations` and `Events` see it (D2), the agent's own player and system messages passing; verify with unit tests of the decision and that `messages match=` can't see withheld text
- [x] 1.3 Detect servers that don't verify accounts (D3) and trust nobody but the agent's own player there; verify with unit tests of the offline-UUID check
- [ ] 1.4 Handle `/blockpilot trust <player>`, `untrust <player>` and `trusted` at the `sendCommand` hook on every target (D4), never sent to the server, with local replies, saved to the config, and taken only from a person: refused after agent input reached that chat screen, for an injected enter, from `cmd` and from `chat`, failing the agent's command with `err not_allowed`; verify with unit tests of the origin rules and in a 1.21.1 NeoForge dev client that typing it works while `cmd blockpilot trust X` and `type` plus `key enter` are refused
- [ ] 1.5 Add the `trust` query and `BlockpilotApi.isTrusted(UUID)` (D6); verify with unit tests and that the 2.0.0 API fixture test still passes; document `trust`, withheld chat and `err not_allowed` in docs/commands.md and `isTrusted` in docs/extending.md
- [ ] 1.6 Reload `trustedPlayers` and `agentCommands` when the config file changes (D7), within a second, keeping the last good settings on a broken file with one logged error, without racing `/blockpilot` saves; verify with unit tests of the reload decision
- [ ] 1.7 Add the development-only `-Pblockpilot.trustUnverified` switch (D3) through the platform seam, ignored in production and on the vanilla targets, with `trust` reporting `trust_unverified`; verify with unit tests and that the 1.21.1 NeoForge and Fabric dev runs pass the property

## 2. Play-only agents (agent-safety)

- [ ] 2.1 Refuse the D5 command set at parse time under `agentCommands: "play"`, nested steps included, with `err not_allowed <command>` before any step runs; verify with unit tests on the engine, including a refused step after an allowed one and inside `if`
- [ ] 2.2 Drop slash commands sent with agent origin under `play` at the `sendCommand` hook and fail the sending command with `err not_allowed cmd`; verify in a 1.21.1 NeoForge dev client that an agent's `/op` typed into chat isn't sent while a person's is; document the profile in docs/commands.md

## 3. Safety guide

- [ ] 3.1 Write docs/safety.md covering trusted players and the in-game commands, withheld chat, unverified servers, the play profile, text from players as untrusted input (plugin-relayed chat, signs, books, names, and the voice add-on's speech), and running the agent with Blockpilot's tools only, with a ready Claude Code permission setup; link it from the README, and add the BREAKING note and the new settings to the 1.1.0 section of CHANGELOG.md; verify every setting and command named there exists

## 4. Integration checks

- [ ] 4.1 Add the security smoke checks to tools/smoke_test.py, each reported as skipped with the reason when it can't run, never as passed: (a) `trust` lists nobody by default; (b) a name written into the config file appears in `trust` within a second and disappears when removed; (c) `cmd blockpilot trust Mallory` answers `err not_allowed`, `trust` stays empty and the file unchanged; (d) `chat /blockpilot trust Mallory` answers `err not_allowed`; (e) agent-typed `/blockpilot trust Mallory` in the chat screen fails the `key` with `err not_allowed`, sends nothing (no server feedback in `messages`) and trusts nobody; (f) the same typed as real OS input (xdotool on Linux, osascript on macOS; skipped on Windows or where typing doesn't reach the window, judged by the chat screen opening) trusts `Steve` and saves him; (g) with `agentCommands: "play"` written to the file and restored after: `jump; cmd say hi` and `server time query gametime` fail with `err not_allowed <command>` before any step, a refused command inside `if` is refused, `create world`, `delete world` and `quit` are refused (refusal only), `goto` and an extension command work, and an agent-typed `/say hi` isn't sent and fails the `key` with `err not_allowed cmd`; verify they pass on 1.21.1 NeoForge
- [ ] 4.2 Run tools/test_targets.py on all 11 targets; verify every run passes

## Workflow follow-up

- Once the `two-client-test-runs` change is applied, add two-client checks behind `--clients 2`: (1) without the dev switch, the second client's chat reaches the first as `withheld from=<name>` even when the second is in the first's `trustedPlayers`, and the first's `trust` reports `unverified`; (2) with the first launched with `-Pblockpilot.trustUnverified`, an untrusted second client's chat is withheld, then has its text once its name is written into the first's config, and `messages match=` can't see the withheld text.
- Archive this change.
- Pushing needs Jan's go-ahead.
