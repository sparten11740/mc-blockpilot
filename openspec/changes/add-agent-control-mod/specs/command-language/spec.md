# Spec Delta

## Purpose

The compact text language that agents and scripts use to observe and control the game. Both transports share it. It is built to spend as few tokens as possible per round trip while staying unambiguous for language models.

## ADDED Requirements

### Requirement: Scripts of chained commands
A request SHALL be a script of one or more commands separated by `;` or newlines. Commands run in order. Each command produces exactly one result line, in order.

#### Scenario: Three-command script
- **WHEN** an agent runs `select iron_pickaxe; mine ~0,-1,0; status`
- **THEN** the reply has three result lines, one per command, in that order

### Requirement: Fail-fast with skipped remainder
When a command fails, the script SHALL stop. The failing command's line reports the error, and each remaining command gets a line `skip`.

#### Scenario: Middle command fails
- **WHEN** an agent runs `select diamond_pickaxe; mine ~0,-1,0` and has no diamond pickaxe
- **THEN** the reply is `err no_item diamond_pickaxe` followed by `skip`

### Requirement: Result line format
Each result line SHALL start with `ok`, `err <code>` or `skip`. Any extra payload goes on the same line or on following lines indented by two spaces. Error codes are stable lowercase identifiers listed in `help errors`.

#### Scenario: Multi-line payload
- **WHEN** `inv` succeeds
- **THEN** the result is `ok` followed by indented payload lines

### Requirement: Position tokens
A position SHALL be written either `x,y,z` (absolute; decimals allowed) or `~dx,dy,dz` (relative to the player's block position). Every position printed in a reply SHALL use one of these forms, so it can be pasted unchanged into a later command.

#### Scenario: Relative position from look reused
- **WHEN** `look` reports `iron_ore ~3,-1,2` and the agent then runs `mine ~3,-1,2` without moving
- **THEN** the same block is targeted

### Requirement: Count, duration and option tokens
A count SHALL be written `x<n>` (e.g. `x6`), a duration `<n>t` (ticks) or `<n>s` (seconds), and an optional named argument `name=value` (e.g. `near=2`, `timeout=30`). Replies SHALL print counts and durations in these same forms.

#### Scenario: Count and option together
- **WHEN** an agent runs `mine #logs x6 timeout=90`
- **THEN** up to six logs are mined, with a 90-second timeout

#### Scenario: Count printed in a reply
- **WHEN** the inventory holds 32 cobblestone
- **THEN** it is printed as `cobblestone x32`

### Requirement: Identifier tokens without default namespace
Block, item, entity-type and other registry identifiers SHALL be printed without the `minecraft:` namespace. Identifiers from other namespaces SHALL keep their namespace. Input SHALL accept both forms.

#### Scenario: Modded and vanilla ids
- **WHEN** inventory holds vanilla stone and Create's andesite alloy
- **THEN** they are printed as `stone` and `create:andesite_alloy`
- **AND** `select minecraft:stone` and `select stone` select the same item

### Requirement: Entity references
Entities SHALL be referenced as `e<id>`, where `<id>` is the client-side entity id. Every entity in a reply carries its reference.

#### Scenario: Attack listed entity
- **WHEN** `look` lists `zombie e812 ~4,0,-2 hp14`
- **THEN** `attack e812` targets that zombie

### Requirement: Omit default and empty fields
Replies SHALL leave out fields that have their default or empty value (no effects, full armor durability, no custom name, etc.). The defaults are documented in `help`.

#### Scenario: Healthy player status
- **WHEN** the player has no status effects and full air
- **THEN** `status` includes neither an effects field nor an air field

### Requirement: Diffs on repeated observation
Within a session, repeating an observation command whose output is diffable (`look`, `inv`, `screen`) SHALL return only the changes since that session's last output of the same command: `+` added, `-` removed, `~` changed. A `full` argument forces a complete output, and an unchanged result prints `ok same`.

#### Scenario: Nothing changed
- **WHEN** an agent runs `inv` twice with no inventory change between
- **THEN** the second result is `ok same`

#### Scenario: Entity moved
- **WHEN** a listed zombie moved between two `look` calls
- **THEN** the second `look` contains a `~` line for that zombie with its new position and nothing for unchanged entries

### Requirement: Pending events appended to replies
The mod SHALL buffer game events per session (chat received, damage taken, death, item picked up, screen opened/closed, action finished/interrupted, disconnect) and append them as `!`-prefixed lines after the result lines of the next reply. The buffer holds at most 50 events, after which it reports `! dropped <n>`.

#### Scenario: Damage while waiting
- **WHEN** the player takes 4 damage from a skeleton between two agent requests
- **THEN** the next reply ends with a line like `! hurt 4 skeleton e933 hp16`

### Requirement: In-band help
`help` SHALL list command groups. `help <command|group|errors>` SHALL return syntax, arguments and one example. Help text SHALL report commands this target does not support.

#### Scenario: Help for a command
- **WHEN** an agent runs `help goto`
- **THEN** the reply gives the `goto` syntax, its options and an example

### Requirement: Unambiguous tokens
Arguments SHALL be separated by spaces. Free text (chat messages, sign text) SHALL be the last argument and runs to the end of the command; a literal `;` inside it is written `\;`. Unknown commands and malformed arguments SHALL fail with `err syntax <hint>`.

#### Scenario: Chat containing semicolon
- **WHEN** an agent runs `chat hi\; brb; status`
- **THEN** the chat message sent is `hi; brb` and `status` runs as its own command
