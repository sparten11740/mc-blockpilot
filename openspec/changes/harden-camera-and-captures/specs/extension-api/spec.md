# Spec Delta

## MODIFIED Requirements

### Requirement: A sub-level slot type
The extension API SHALL have a slot type `ArgType.SUB_LEVEL` that takes
`sub:<uuid|name|here>`. Blockpilot SHALL resolve it before the handler runs,
as it resolves entity names, so the handler reads `sub:<uuid>` with
`args.word(name)` and the UUID with `args.subLevel(name)`, and an unknown,
ambiguous or unsupported reference fails before the handler runs. It SHALL be
an addition to the 2.1.0 API, as version 2.2.0, that leaves mods built
against 2.0.0 or 2.1.0 working unchanged.

#### Scenario: A mod built against 2.1.0
- **WHEN** a mod compiled against `blockpilot-api` 2.1.0 runs with a
  Blockpilot that has the 2.2.0 API
- **THEN** its commands register and run as before

#### Scenario: A slot that takes a sub-level
- **WHEN** a mod declares `form.arg("ship", ArgType.SUB_LEVEL)` and an agent
  passes `sub:ship`, with Sable installed and one sub-level named `ship`
- **THEN** the handler reads that sub-level's UUID with
  `args.subLevel("ship")`

#### Scenario: No such sub-level
- **WHEN** an agent passes `sub:nosuch` to that slot and no sub-level is
  named `nosuch`
- **THEN** the result is `err no_sublevel nosuch`, and the handler doesn't
  run
