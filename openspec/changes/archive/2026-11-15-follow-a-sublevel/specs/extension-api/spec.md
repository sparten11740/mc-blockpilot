## ADDED Requirements

### Requirement: A sub-level slot type
The extension API SHALL have a slot type `ArgType.SUB_LEVEL` that takes
`sub:<uuid|name|here>`, read with `args.word(name)` as written. It SHALL be an
addition to the 2.1.0 API, as version 2.2.0, that leaves mods built against
2.0.0 or 2.1.0 working unchanged.

#### Scenario: A mod built against 2.1.0
- **WHEN** a mod compiled against `blockpilot-api` 2.1.0 runs with a
  Blockpilot that has the 2.2.0 API
- **THEN** its commands register and run as before

#### Scenario: A slot that takes a sub-level
- **WHEN** a mod declares `form.arg("ship", ArgType.SUB_LEVEL)` and an agent
  passes `sub:ship`
- **THEN** the handler reads `sub:ship` with `args.word("ship")`
