# command-language Specification

## Purpose

The compact text language that agents and scripts use to observe and control the
game. Both transports share it. It is built to spend as few tokens as possible
per round trip while staying unambiguous for language models.

## Requirements

### Requirement: Scripts of chained commands
A request SHALL be a script of one or more commands separated by `;` or
newlines. Commands run in order. Each command produces exactly one result line,
in order.

#### Scenario: Three-command script
- **WHEN** an agent runs `select iron_pickaxe; mine ~0,-1,0; status`
- **THEN** the reply has three result lines, one per command, in that order

### Requirement: Grammar
Requests and scripts SHALL follow this grammar, in W3C EBNF (XML 1.0, section
6) read the way the comment at its top says.

```ebnf
/* Read as the parser reads it: alternatives are tried in order and the first
   that matches is taken; ?, * and + take as much as they can and never give
   it back; a production for a token matches the whole token; A - B matches
   what A matches unless B matches it too. [C] marks value limits: whole
   numbers are below 2^31, decimals have at most 9 digits before their point,
   and a duration is at most 2^31 - 1 ticks. */

/* Line protocol: an auth line, then one request per line */
connection   ::= auth-line request*
auth-line    ::= "auth" #x20 auth-token line-end
auth-token   ::= [^#x20#x9#xA#xD]+
request      ::= ( "#" request-id ( #x20 line-script )? | line-script )
                 line-end
request-id   ::= [^#x20#x9#xA#xD;]+
line-script  ::= step ( ";" step )*
line-end     ::= #xD? #xA

/* Scripts; MCP's run tool also ends steps at line breaks */
script       ::= step ( separator step )*
separator    ::= ";" | #xA | #xD
step         ::= blank* ( "?"? statement blank* )?
blank        ::= #x20 | #x9
statement    ::= wait | sample | assert | if | call

/* Constructs */
construct    ::= "wait" | "sample" | "assert" | "if"
wait         ::= "wait" blank+ ( duration | "idle" | query blank+ "until"
                 blank+ predicates ( blank+ wait-option )* )
wait-option  ::= ( "stable=" | "every=" ) duration
               | "timeout=" ( duration | seconds ) | clock
sample       ::= "sample" blank+ query blank+ "every" blank+ duration
                 ( blank+ count ( blank+ clock )? ( blank+ beside )?
                 | ( blank+ clock )? blank+ beside )
beside       ::= "while" blank+ call
clock        ::= "clock=" ( "server" | "client" )
assert       ::= "assert" blank+ query blank+ predicates
if           ::= "if" blank+ query blank+ predicates blank+ "then" blank+
                 "?"? statement
query        ::= ( command-name - construct ) ( blank+ ( token - stop ) )*
stop         ::= "ok" | "until" | "every" | "then" | test-shape

/* Calls: the tokens are matched against the command's forms */
call         ::= ( command-name - construct ) ( blank+ token )*
command-name ::= ( namespace ":" )? name
name         ::= [a-z] [a-z0-9_]*

/* Predicates */
predicates   ::= predicate ( blank+ ( "and" blank+ )? predicate )*
predicate    ::= "ok" | test
test         ::= field-path ( ( "==" | "!=" ) operand
                 | ( "<=" | ">=" | "<" | ">" ) ( decimal | reference ) )
operand      ::= reference | value
reference    ::= "$" field-path
value        ::= ( token-char - [$<>=!&|] ) ( token-char - [<>=!&|] )*
test-shape   ::= field-path comparison token-char+
comparison   ::= "==" | "!=" | "<=" | ">=" | "<" | ">"
field-path   ::= identifier ( "." ( identifier | digits ) )*      /* [C] */

/* Tokens */
token        ::= token-char+
token-char   ::= "\;" | [^#x20#x9#xA#xD;]
identifier   ::= [A-Za-z_] [A-Za-z0-9_]*
digits       ::= [0-9]+

/* What forms are made of */
option       ::= option-name "=" option-value
option-name  ::= identifier ( ":" identifier )?
option-value ::= ( token-char - "=" ) token-char*
text         ::= token ( blank+ token )*
word         ::= token - ( option | test-shape )

/* Slot types */
position     ::= "~"? decimal "," decimal "," decimal
point        ::= decimal "," decimal
entity       ::= "e" digits                                       /* [C] */
widget       ::= "w" digits                                       /* [C] */
menu-slot    ::= "s" digits                                       /* [C] */
uuid         ::= hex8 "-" hex4 "-" hex4 "-" hex4 "-" hex8 hex4
hex8         ::= hex4 hex4
hex4         ::= hex hex hex hex
hex          ::= [0-9a-fA-F]
integer      ::= "-"? digits                                      /* [C] */
number       ::= decimal
decimal      ::= "-"? digits ( "." digits )?                      /* [C] */
count        ::= "x" digits                                       /* [C] */
duration     ::= digits "t" | seconds "s"                         /* [C] */
seconds      ::= digits ( "." digits )?                           /* [C] */
id           ::= namespace ":" path | bare-id
bare-id      ::= path - ( decimal | count | duration | entity | widget
                        | menu-slot | uuid )
namespace    ::= [a-z0-9_.-]+
path         ::= [a-z0-9_./-]+
tag          ::= "#" id
pattern      ::= ( [a-z0-9_.:/-]* "*" )+ [a-z0-9_.:/-]*
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

#### Scenario: Optional slot that doesn't fit
- **WHEN** an agent runs `turn 90 20 10`
- **THEN** the result is `err syntax col 12:` naming the duration the optional
  `ticks` slot takes, like `10t`

### Requirement: Declarations follow the grammar
Registration SHALL reject a command whose names the grammar can't reach: a
command name that isn't `name`, a namespace that isn't `namespace`, an option
name that isn't `option-name` (or `<identifier>:*` for a family of options), a
field name that isn't an identifier, or a keyword word that isn't a word or is
one of `ok`, `until`, `every`, `then`, `and` and `full`.

#### Scenario: Option that can't be written
- **WHEN** an extension declares an option named `max-speed`
- **THEN** registration fails, naming the option

### Requirement: Unambiguous forms
Registration SHALL reject forms whose parts can't all be reached: two slots of
one form with the same name, a slot type that an earlier type of the same slot
always takes first (any type after `word`, `integer` or `seconds` after
`number`), an optional slot whose types could take a token the next part is
meant for (text, which takes any token, excepted), and an optional group whose
first part is optional.

#### Scenario: Optional word before a number
- **WHEN** an extension declares a form `[name:word] <amount:integer>`
- **THEN** registration fails, because `name` would take the amount

### Requirement: Options
Options SHALL be written `<name>=<value>`, with a value, anywhere after the
command name and before any text, each at most once. A command takes only the
options it declares, each value checked against the slot types the declaration
gives it, and every action takes `timeout=` (a duration or seconds). A family
`rule:*` takes `rule:<identifier>=<value>`. Where text may start, a token is an
option only when the command declares it, so text can begin with `name=value`.

#### Scenario: Option position doesn't matter
- **WHEN** an agent runs `mine timeout=90 #logs x6` or `mine #logs x6
  timeout=90`
- **THEN** both mean the same

#### Scenario: Text that looks like an option
- **WHEN** an agent runs `chat a=b`
- **THEN** the chat message is `a=b`

#### Scenario: Option given twice
- **WHEN** an agent runs `mine #logs timeout=9 timeout=90`
- **THEN** the result is `err syntax` naming the repeated option

#### Scenario: Option value checked before running
- **WHEN** an agent runs `status; goto 1,64,1 near=abc`
- **THEN** the reply is `skip` followed by `err syntax` naming the number
  `near=` takes, and nothing ran

#### Scenario: Family of options
- **WHEN** an agent runs `create world w1 rule:doDaylightCycle=false
  rule:keepInventory=true`
- **THEN** both game rules are set, in that order

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

### Requirement: Soft steps and nesting
A step that starts with `?` SHALL be soft even when what follows doesn't parse.
A `?` before the statement after `then` makes only that statement soft, while
`?if` makes the `if` and its statement soft. An `if` nests at most 16 deep.

#### Scenario: Blank after the question mark
- **WHEN** an agent runs `? frobnicate; status`
- **THEN** the first line is `err syntax col 1: …` and `status` runs

### Requirement: Hints for likely mistakes
A syntax error SHALL say how to fix the likely mistake when a predicate is
written with `=` or with blanks around its comparison, a duration lacks its
unit, a construct's option is written inside its query, or a construct's
keyword is written as an option (`every=` in `sample`) or the other way round.

#### Scenario: Predicate with a single equals sign
- **WHEN** an agent runs `assert block ~0,-1,0 id=air`
- **THEN** the result is `err syntax` saying that predicates compare with `==`

#### Scenario: Duration without a unit
- **WHEN** an agent runs `wait 5`
- **THEN** the result is `err syntax` asking for `5t` or `5s`

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
namespace. Input SHALL accept both forms, in arguments and in predicates, where
`id==minecraft:air` matches `air`.

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
last output of the same command: `+` added, `-` removed, `~` changed. `full`,
which may stand anywhere an option may and is never taken by a slot, forces a
complete output, and an unchanged result prints `ok same`.

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
rows; an ordering compares with a number or `$<field>`, another field of the
same result. Options after the predicates: `stable=` how long the predicates
must keep holding, `every=` how often to check (both default 1t), `timeout=`
(default 60s, a plain number is seconds).

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

#### Scenario: Ordering against text
- **WHEN** an agent runs `wait status until hp<max_hp`
- **THEN** the result is `err syntax` saying an ordering compares with a number
  or `$field`

### Requirement: Samples
`sample <query> every <duration> x<n>` SHALL run a read-only query n times, 1
to 1,000, every N ticks, at least 1t. A server-side query is sampled on the
server clock: `clock=server` asserts that, and `clock=client` is refused for a
server-side query.

#### Scenario: Too many samples
- **WHEN** an agent runs `sample status every 1t x1001`
- **THEN** the result is `err syntax` naming the 1,000 limit

### Requirement: Sampling beside an action
With `while <action>`, `sample` SHALL run the action as if written alone and
sample beside it until the action ends or n (at most 1,000) samples are taken:
from before the action's first tick when both run on one side, else from the
query side's next tick. The line is the action's result, success or error, with
the samples as its rows.

#### Scenario: Sampling a walk
- **WHEN** an agent runs `sample status every 2t while move forward 3`
- **THEN** the result is the move's `ok moved 3.0`, with one row of `status`
  fields every 2 ticks from before the first step to the end of the walk

#### Scenario: Something other than an action after while
- **WHEN** an agent runs `sample status every 2t while look`
- **THEN** the result is `err syntax` saying `while` takes an action

### Requirement: Assertions
`assert <query> <predicate> [and <predicate>…]` SHALL run a read-only command
once and check the predicates of `wait … until` against its result: `ok` with
the observed fields when they hold, otherwise `err assert_failed` with them
(and the query's error code if it failed). The query ends at its first stop
token (a predicate, `ok`, `until`, `every` or `then`) and runs on the thread and
behind the gate it would have on its own. `?assert` is soft.

#### Scenario: Script as a test
- **WHEN** an agent runs `use 10,64,5; wait block 12,60,5 until id==air
  timeout=8s; assert block 14,65,5 props.lit==true; status`
- **THEN** the third line is `ok props.lit=true` when the lamp is lit, or `err
  assert_failed props.lit=false` followed by `skip` when it is not

### Requirement: Conditionals
`if <query> <predicate> [and …] then <statement>` SHALL run the statement when
the predicates hold, with its result standing for the `if`; otherwise the
result is `ok false` with the observed fields. The query is checked as for
`assert`. A construct (`wait`, `sample`, `assert`, `if`) is never a query.

#### Scenario: Conditional action
- **WHEN** an agent runs `if block ~0,-1,0 id==grass_block then place torch
  ~0,0,1`
- **THEN** the torch is placed only on grass, and otherwise the line is `ok
  false id=dirt`

#### Scenario: Assert inside wait
- **WHEN** an agent runs `wait assert status hp>5 until ok`
- **THEN** the result is `err syntax` saying `assert` can't be a query

### Requirement: In-band help
`help` SHALL list command groups. `help <command|group|errors>` SHALL return
syntax, arguments, result fields and one example. Help text SHALL report
commands this target does not support. `help grammar` SHALL print every
command's forms, extensions included, as EBNF productions named
`<command>-command` (`.` for `:`), with keyword choices inline, blanks between
items, options allowed between any two items and `full` on diffable commands.

#### Scenario: Help for a command
- **WHEN** an agent runs `help goto`
- **THEN** the reply gives the `goto` syntax, its options and an example

#### Scenario: Grammar of the commands
- **WHEN** an agent runs `help grammar`
- **THEN** the reply has one production per command, such as `mouse-command
  ::= "mouse" ( ( "down" | "up" ) ( "left" | "right" | "middle" ) | … )`

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
