# Tasks

## 1. Trusted players (agent-safety)

- [x] 1.1 Add `trustedPlayers` (empty by default) and `agentCommands` (`all` by default, or `play`) to `protocol/Config`, read from the config file and never written by Blockpilot beyond the defaults of missing keys (D4); verify with unit tests for the defaults and an unknown `agentCommands` value
- [x] 1.2 Pass the sender with player chat from the NeoForge, Fabric and vanilla chat hooks, and withhold an untrusted player's text as `withheld from=<name>` before `Observations` and `Events` see it (D2), the agent's own player and system messages passing; verify with unit tests of the decision and that `messages match=` can't see withheld text
- [x] 1.3 Detect servers that don't verify accounts (D3) and trust nobody but the agent's own player there; verify with unit tests of the offline-UUID check
- [ ] 1.4 Add the `trust` query, taking no arguments, and `BlockpilotApi.isTrusted(UUID)` (D6); verify with unit tests, that `trust add Mallory` answers `err syntax`, and that the 2.0.0 API fixture test still passes; document `trust`, withheld chat and setting trust in the config file in docs/commands.md and `isTrusted` in docs/extending.md
- [ ] 1.5 Reload `trustedPlayers` and `agentCommands` when the config file changes (D7), within a second and without writing the file, keeping the last good settings on a broken file with one logged error; verify with unit tests of the reload decision
- [ ] 1.6 Add the development-only `-Pblockpilot.trustUnverified` switch (D3) through the platform seam, ignored in production and on the vanilla targets, with `trust` reporting `trust_unverified`; verify with unit tests and that the 1.21.1 NeoForge and Fabric dev runs pass the property

## 2. Play-only agents (agent-safety)

- [ ] 2.1 Refuse the D5 command set at parse time under `agentCommands: "play"`, nested steps included, with `err not_allowed <command>` before any step runs; verify with unit tests on the engine, including a refused step after an allowed one and inside `if`
- [ ] 2.2 Drop slash commands sent with agent origin under `play` at the send hooks, `sendCommand` and the click-event path of each version (D8), and fail the sending step with `err not_allowed cmd` through `Refusal`; verify with unit tests of the refusal in `ScriptJob` and in a 1.21.1 NeoForge dev client that an agent's `/say hi` typed into chat isn't sent; document the profile in docs/commands.md

## 3. Safety guide

- [ ] 3.1 Write docs/safety.md covering trusted players set in the config file, withheld chat, unverified servers and the dev switch, the play profile, and text from players as untrusted input (plugin-relayed chat, signs, books, names, and the voice add-on's speech); add the BREAKING note and the new settings to the 1.1.0 section of CHANGELOG.md; verify every setting and command named there exists
- [ ] 3.2 Add the agent setup to docs/safety.md, every flag and settings key checked against the current Claude Code docs and none quoted from memory: a settings.json for the agent's working folder allowing only Blockpilot's MCP tools (`run`, `screenshot`, `help`) and denying shell, file editing and writing, and web access; the matching command-line launch if Claude Code has flags for it; a plain warning against bypass or skip-permissions modes for an agent that takes requests from players; and the strongest option, a separate OS user, VM or container with nothing but the loopback endpoint; verify each flag and key against the docs page it came from, cited in the doc
- [ ] 3.3 Add a short "Security" section to the README beside License or Connecting, in its style: player chat reaches agents only from `trustedPlayers` in config/blockpilot/config.json, empty by default and set in the file only; `agentCommands: "play"` keeps agents from slash commands, world files, recordings, mod configs, switching servers and quitting; Blockpilot limits only what goes through the game, and keeping the agent off the host machine is its own setup's job, linking docs/safety.md; verify the README's links resolve

## 4. Integration checks

- [ ] 4.1 Add tools/security_smoke_test.py, its own `main()` with `--game-dir` and `--json`, run against a running client and reusing smoke_test.py's helpers by import; it writes the client's config and restores it in a `finally`, waiting for the reload before each next check, and reports each check as passed, failed or skipped with the reason: (a) `trust` lists nobody by default; (b) a name written to or removed from the config file shows up in or drops out of `trust` within a second; (c) `trust add Mallory` answers `err syntax` and changes nothing; (d) with `agentCommands: "play"` in the file: `jump; cmd say hi` and `server time query gametime` fail with `err not_allowed <command>` before any step, a refused command inside `if` is refused, `create world`, `delete world` and `quit` are refused (refusal only), `goto` and an extension command still work, and an agent-typed `/say hi` in the chat screen isn't sent (no feedback in `messages`) and fails the `key` with `err not_allowed cmd`; (e) the two-client checks, skipped until `--clients 2` exists; add `--suite smoke|security|all` (default `all`) to tools/test_targets.py, which runs the security suite after smoke_test.py and reports its checks per target; document both ways to run it in docs/dev-environment.md; verify on 1.21.1 NeoForge
- [ ] 4.2 Run `tools/test_targets.py --suite all` on all 11 targets; verify every run passes, and list per target which security checks passed or were skipped and why

## Workflow follow-up

- Once the `two-client-test-runs` change is applied, fill in check (e) behind `--clients 2`: (1) without the dev switch, the second client's chat reaches the first as `withheld from=<name>` even when the second is in the first's `trustedPlayers`, and the first's `trust` reports `unverified`; (2) with the first launched with `-Pblockpilot.trustUnverified`, an untrusted second client's chat is withheld, then has its text once its name is written into the first's config, and `messages match=` can't see the withheld text.
- Archive this change.
- Pushing needs Jan's go-ahead.
