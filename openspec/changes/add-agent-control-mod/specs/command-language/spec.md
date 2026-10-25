# Spec Delta

## Purpose

The compact text language that agents and scripts use to observe and control the
game. Both transports share it. It is built to spend as few tokens as possible
per round trip while staying unambiguous for language models.

## ADDED Requirements

### Requirement: Scripts of chained commands
A request SHALL be a script of one or more commands separated by `;` or
newlines. Commands run in order. Each command produces exactly one result line,
in order.

#### Scenario: Three-command script
- **WHEN** an agent runs `select iron_pickaxe; mine ~0,-1,0; status`
- **THEN** the reply has three result lines, one per command, in that order

### Requirement: Grammar
Requests and scripts SHALL follow this grammar, written in W3C EBNF (XML 1.0,
section 6). `A - B` matches what `A` matches unless `B` matches it too. Where
alternatives overlap, the earlier one is taken, and `"\;"` is a semicolon that
doesn't end a step.

```ebnf
/* Line protocol requests; MCP's run tool takes a script directly */
request      ::= ( "#" request-id ( " " script )? | script ) line-end
request-id   ::= [^ #xA#xD]+
line-end     ::= #xD? #xA

/* Scripts: steps in order; empty steps are ignored */
script       ::= step ( separator step )*
separator    ::= ";" | #xA | #xD
step         ::= blank* ( "?"? statement blank* )?
blank        ::= #x20 | #x9
statement    ::= wait | sample | assert | if | call

/* Constructs */
wait         ::= "wait" blank+ ( duration | "idle" | query blank+ "until"
                 blank+ predicates ( blank+ option )* )
sample       ::= "sample" blank+ query blank+ "every" blank+ duration blank+
                 count ( blank+ option )*
assert       ::= "assert" blank+ query blank+ predicates
if           ::= "if" blank+ query blank+ predicates blank+ "then" blank+
                 "?"? statement
query        ::= command-name ( blank+ ( argument - stop-word ) )*
stop-word    ::= predicate | "until" | "every" | "then"

/* Calls: the arguments match one of the command's forms, whose last slot
   may be text */
call         ::= command-name ( blank+ argument )*
command-name ::= ( namespace ":" )? name
name         ::= [a-z] [a-z0-9_]*
argument     ::= option | token
option       ::= option-name "=" ( ( token-char - "=" ) token-char* )?
option-name  ::= identifier ( ":" identifier )?

/* Predicates */
predicates   ::= predicate ( blank+ ( "and" blank+ )? predicate )*
predicate    ::= "ok" | test
test         ::= field-path comparison operand
comparison   ::= "==" | "!=" | "<=" | ">=" | "<" | ">"
operand      ::= "$" field-path | token-char+
field-path   ::= identifier ( "." ( identifier | digits ) )*

/* Tokens */
token        ::= token-char+
token-char   ::= "\;" | [^#x20#x9#xA#xD;]
identifier   ::= [A-Za-z_] [A-Za-z0-9_]*
digits       ::= [0-9]+

/* Slot types */
position     ::= "~"? decimal "," decimal "," decimal
point        ::= decimal "," decimal
entity       ::= "e" digits
widget       ::= "w" digits
slot         ::= "s" digits
uuid         ::= hex8 "-" hex4 "-" hex4 "-" hex4 "-" hex8 hex4
hex8         ::= hex4 hex4
hex4         ::= [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]
integer      ::= "-"? digits
number       ::= decimal
decimal      ::= "-"? digits ( "." digits )?
count        ::= "x" digits
duration     ::= digits "t" | decimal "s"
id           ::= ( namespace ":" )? path
namespace    ::= [a-z0-9_.-]+
path         ::= [a-z0-9_./-]+
tag          ::= "#" id
pattern      ::= [a-z0-9_.:/*-]* "*" [a-z0-9_.:/*-]*
word         ::= token - ( option | test )
text         ::= ( "\;" | [^;#xA#xD] )*
```

#### Scenario: A construct in a script
- **WHEN** an agent runs `wait block ~0,-1,0 until id==air timeout=5s; status`
- **THEN** the first step is a wait on the query `block ~0,-1,0` with the
  predicate `id==air` and the option `timeout=5s`, and the second is a call of
  `status`

#### Scenario: Escaped semicolon
- **WHEN** an agent runs `chat a\; b; status`
- **THEN** the chat message is `a; b` and `status` is a step of its own

### Requirement: Command forms
Every command SHALL declare one or more forms, and a call SHALL match one of
them. A form is a sequence of keywords, slots and optional groups. A slot takes
one or more of the slot types, and the first type that matches the token is
used. A form's last slot may be text, which takes the rest of the step as
written. Optional slots and groups take the next token when it fits. Forms are
tried in order. When none matches, the error is that of the form that matched
the most tokens.

#### Scenario: Form chosen by keyword
- **WHEN** an agent runs `mouse move 30 -10 4t`, then `mouse move 30 up`
- **THEN** the first matches the `move` form, and the second fails with `err
  syntax col 15: …` naming the number it expected instead of `up`

#### Scenario: Slot with several types
- **WHEN** an agent runs `click w3`, `click 120,40` and `click s5 shift`
- **THEN** the first two match the form taking a point or a widget, and the
  third the form taking a container slot

### Requirement: Options
Options SHALL be written `<name>=<value>` and may stand anywhere after the
command name and before any text, each at most once. A command takes only the
options it declares, and every action also takes `timeout=`.

#### Scenario: Option position doesn't matter
- **WHEN** an agent runs `mine timeout=90 #logs x6` or `mine #logs x6
  timeout=90`
- **THEN** both mean the same

#### Scenario: Option given twice
- **WHEN** an agent runs `mine #logs timeout=9 timeout=90`
- **THEN** the result is `err syntax` naming the repeated option

### Requirement: Parsing before running
A script SHALL be parsed completely before any of it runs. A syntax error in a
step that isn't soft makes the script run nothing: that step's line is `err
syntax col <n>: <hint>`, counting columns from the step's first non-blank
character, and every other line is `skip`. A soft step's syntax error is its
own result, and the rest of the script runs.

#### Scenario: Typo at the end
- **WHEN** an agent runs `status; turn 90 up`
- **THEN** the reply is `skip` followed by `err syntax col 9: …`, and nothing
  ran

#### Scenario: Soft step with an unknown command
- **WHEN** an agent runs `?frobnicate; status`
- **THEN** the reply is `err syntax col 2: unknown command frobnicate…`
  followed by the `status` result

### Requirement: Fail-fast with skipped remainder
When a command fails while running, the script SHALL stop. The failing
command's line reports the error, and each remaining command gets a line `skip`.
A command written `?<command>` is soft: its error is reported the same way, but
the script goes on. Syntax errors are found before anything runs (see Parsing
before running).

#### Scenario: Middle command fails
- **WHEN** an agent runs `select diamond_pickaxe; mine ~0,-1,0` and has no
  diamond pickaxe
- **THEN** the reply is `err no_item diamond_pickaxe` followed by `skip`

#### Scenario: Soft probes
- **WHEN** an agent runs `?block 90000,64,0; status`
- **THEN** the reply is `err unloaded …` followed by the `status` result

### Requirement: Result line format
Each result line SHALL start with `ok`, `err <code>` or `skip`. Any extra
payload goes on the same line or on following lines indented by two spaces.
Error codes are stable lowercase identifiers listed in `help errors`.

#### Scenario: Multi-line payload
- **WHEN** `inv` succeeds
- **THEN** the result is `ok` followed by indented payload lines

### Requirement: Position tokens
A position SHALL be written either `x,y,z` (absolute; decimals allowed) or
`~dx,dy,dz` (relative to the player's block position). Every position printed in
a reply SHALL use one of these forms, so it can be pasted unchanged into a later
command.

#### Scenario: Relative position from look reused
- **WHEN** `look` reports `iron_ore ~3,-1,2` and the agent then runs `mine
  ~3,-1,2` without moving
- **THEN** the same block is targeted

### Requirement: Count, duration and option tokens
A count SHALL be written `x<n>` (e.g. `x6`), a duration `<n>t` (ticks) or `<n>s`
(seconds), and an optional named argument `name=value` (e.g. `near=2`,
`timeout=30`). Replies SHALL print counts and durations in these same forms.

#### Scenario: Count and option together
- **WHEN** an agent runs `mine #logs x6 timeout=90`
- **THEN** up to six logs are mined, with a 90-second timeout

#### Scenario: Count printed in a reply
- **WHEN** the inventory holds 32 cobblestone
- **THEN** it is printed as `cobblestone x32`

### Requirement: Identifier tokens without default namespace
Block, item, entity-type and other registry identifiers SHALL be printed without
the `minecraft:` namespace. Identifiers from other namespaces SHALL keep their
namespace. Input SHALL accept both forms.

#### Scenario: Modded and vanilla ids
- **WHEN** inventory holds vanilla stone and Create's andesite alloy
- **THEN** they are printed as `stone` and `create:andesite_alloy`
- **AND** `select minecraft:stone` and `select stone` select the same item

### Requirement: Entity references
Entities SHALL be referenced as `e<id>`, where `<id>` is the client-side entity
id. Every entity in a reply carries its reference.

#### Scenario: Attack listed entity
- **WHEN** `look` lists `zombie e812 ~4,0,-2 hp14`
- **THEN** `attack e812` targets that zombie

### Requirement: Omit default and empty fields
Replies SHALL leave out fields that have their default or empty value (no
effects, full armor durability, no custom name, etc.). The defaults are
documented in `help`.

#### Scenario: Healthy player status
- **WHEN** the player has no status effects and full air
- **THEN** `status` includes neither an effects field nor an air field

### Requirement: Diffs on repeated observation
Within a session, repeating an observation command whose output is diffable
(`look`, `inv`, `screen`) SHALL return only the changes since that session's
last output of the same command: `+` added, `-` removed, `~` changed. A `full`
argument forces a complete output, and an unchanged result prints `ok same`.

#### Scenario: Nothing changed
- **WHEN** an agent runs `inv` twice with no inventory change between
- **THEN** the second result is `ok same`

#### Scenario: Entity moved
- **WHEN** a listed zombie moved between two `look` calls
- **THEN** the second `look` contains a `~` line for that zombie with its new
  position and nothing for unchanged entries

### Requirement: Pending events appended to replies
The mod SHALL buffer game events per session (chat received, damage taken,
death, item picked up, screen opened/closed, action finished/interrupted,
disconnect) and append them as `!`-prefixed lines after the result lines of the
next reply. The buffer holds at most 50 events, after which it reports `!
dropped <n>`.

#### Scenario: Damage while waiting
- **WHEN** the player takes 4 damage from a skeleton between two agent requests
- **THEN** the next reply ends with a line like `! hurt 4 skeleton e933 hp16`

### Requirement: Structured results in text or JSON
Every command SHALL produce a structured result: status, error code, named
fields and keyed rows. `fmt text|json` SHALL choose how a session renders
results. Text, the compact form described above, is the default. Both formats
carry the same fields with the same default omissions, and the setting affects
only the session that sets it.

#### Scenario: JSON status
- **WHEN** a session runs `fmt json` and then `status`
- **THEN** the reply is a JSON object whose `results` array holds one object
  like `{"ok":true,"pos":[104.5,64,-33.2],"hp":20,"max_hp":20,"food":18}`

#### Scenario: Error and skip in JSON
- **WHEN** a JSON-mode session runs `select diamond_pickaxe; mine ~0,-1,0`
  without a diamond pickaxe
- **THEN** `results` holds
  `{"ok":false,"err":"no_item","arg":"diamond_pickaxe"}` followed by
  `{"skip":true}`

#### Scenario: Diff in JSON
- **WHEN** a JSON-mode session repeats `inv` after picking up 3 raw iron
- **THEN** the result has a `changed`/`added`/`removed` row array with a row for
  `raw_iron` and nothing for unchanged slots

#### Scenario: Format is per session
- **WHEN** session A runs `fmt json`
- **THEN** session B still receives text replies

### Requirement: Wait conditions
`wait <query> until <predicate> [and <predicate>…]` SHALL re-run a read-only
command until every predicate over its result holds. A predicate is
`<field><op><value>` with `== != < <= > >=`, or `ok`; `count` is the number of
rows. A value written `$<field>` is that other field of the same result, so two
fields can be compared. Options after `until`: `stable=<Nt>` (consecutive
matches needed, default 1), `every=<Nt>` (default 1t), `timeout=<duration>`
(default 60s).

#### Scenario: Wait for a block change
- **WHEN** an agent runs `wait block 0,-60,0 until id==air`
- **THEN** the result is `ok <elapsed> id=air` on the first tick the block is
  air

#### Scenario: Wait until values settle
- **WHEN** an agent runs `wait sable:info <uuid> until speed<0.01 stable=20t
  timeout=30s`
- **THEN** the result returns once `speed` has stayed below 0.01 for 20
  consecutive ticks

#### Scenario: Timeout reports the last values
- **WHEN** the predicate never holds before the timeout
- **THEN** the result is `err timeout` with the fields named in the predicates,
  as last observed

#### Scenario: Action used as condition
- **WHEN** an agent runs `wait mine ~0,-1,0 until ok`
- **THEN** the result is `err syntax` because `mine` is not a read-only command

#### Scenario: Two fields compared
- **WHEN** an agent runs `wait status until hp<$max_hp`
- **THEN** the wait returns once the player has taken damage, whatever their
  maximum health is

### Requirement: Assertions
`assert <query> <predicate> [and <predicate>…]` SHALL run a read-only command
once and check the predicates of `wait … until` against its result: `ok` with
the observed fields when they hold, otherwise `err assert_failed` with them
(and the query's error code if it failed). The predicates are the trailing
tokens; the query runs on the thread and behind the gate it would have on its
own. `?assert` is soft like any command.

#### Scenario: Script as a test
- **WHEN** an agent runs `use 10,64,5; wait block 12,60,5 until id==air
  timeout=8s; assert block 14,65,5 props.lit==true; status`
- **THEN** the third line is `ok props.lit=true` when the lamp is lit, or `err
  assert_failed props.lit=false` followed by `skip` when it is not

### Requirement: Conditionals
`if <query> <predicate> [and …] then <command>` SHALL run the command when the
predicates hold, with its result standing for the `if`; otherwise the result is
`ok false` with the observed fields. The query is checked as for `assert`. A
`?if` makes its command soft too. `assert` and `if` only work as commands of a
script, not inside `wait` or `sample`.

#### Scenario: Conditional action
- **WHEN** an agent runs `if block ~0,-1,0 id==grass_block then place torch
  ~0,0,1`
- **THEN** the torch is placed only on grass, and otherwise the line is `ok
  false id=dirt`

#### Scenario: Assert inside wait
- **WHEN** an agent runs `wait assert status hp>5 until ok`
- **THEN** the result is `err syntax`

### Requirement: In-band help
`help` SHALL list command groups. `help <command|group|errors>` SHALL return
syntax, arguments, result fields and one example. Help text SHALL report
commands this target does not support. `help grammar` SHALL print the grammar
of every command's forms, extensions included, as EBNF productions in the
notation of the grammar, with blanks between items and options allowed between
any two of them.

#### Scenario: Help for a command
- **WHEN** an agent runs `help goto`
- **THEN** the reply gives the `goto` syntax, its options and an example

#### Scenario: Grammar of the commands
- **WHEN** an agent runs `help grammar`
- **THEN** the reply has one production per command, such as `mouse ::= "mouse"
  ( ( "down" | "up" ) button | "click" button duration? | … )`

#### Scenario: Help lists result fields
- **WHEN** an agent runs `help status`
- **THEN** the reply lists the field names `status` produces (e.g. `pos`, `hp`,
  `food`), which can be used in `wait … until` predicates

### Requirement: Unambiguous tokens
Arguments SHALL be separated by blanks. Free text (chat messages, sign text)
SHALL be the last argument and runs to the end of the command; a literal `;`
inside it is written `\;`. Unknown commands and arguments that match none of a
command's forms SHALL fail with `err syntax col <n>: <hint>`.

#### Scenario: Chat containing semicolon
- **WHEN** an agent runs `chat hi\; brb; status`
- **THEN** the chat message sent is `hi; brb` and `status` runs as its own
  command
