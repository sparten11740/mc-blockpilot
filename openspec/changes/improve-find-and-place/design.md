# Design

## Context

- Block arguments are typed slots: `find`, `mine`, `sable:find` and `sable:tp`
  take `tag`, `pattern` or `id`, and `BlockMatcher.parse` turns the token
  into a predicate. The matcher caches its answer per block, since every
  test so far depends on the block alone.
- The lexer and the option scanner already pass `oak_log[axis=y]` through as
  one plain word, not an option or a predicate. The slot types reject it.
- `GrammarTest` compares `help grammar` with the grammar of an active change
  that modifies it, else the main spec's. `SpecGrammarTest` checks the
  lexicon's recognizers against the main spec's token productions only.
- Minecraft requires state names and values to match `[a-z0-9_]+` on both
  1.21.1 and 26.1 (`StateDefinition.NAME_PATTERN`). Both look a state up by
  name with `StateDefinition.getProperty` and name a value with
  `Property.getName`.
- `place` picks the first neighbour that can't be replaced and aims at the
  middle of that neighbour's cell face toward the target cell. `use` aims at
  the middle of a block's shape (`Shapes.centre`).
- The specs already say that held input is released when its session closes
  and that `\;` escapes a semicolon. Only `help grammar` mentions the escape,
  and neither the help nor the README says how long held input lasts.

## Goals / Non-Goals

**Goals:**
- Find, count and mine blocks by their state.
- See every match of a find in one call for any realistic build.
- Place against any block with a shape the crosshair can hit.
- Errors that say what blocks the view.

**Non-Goals:**
- States on items, entities or events: only block slots take them.
- Negated or alternative values (`axis=!y`, `axis=x|z`): Minecraft's syntax
  has neither. Two finds do it.
- Paging or writing a find to a file.
- Grouping a find's matches by state.

## Decisions

### D1: Minecraft's own syntax

`<block>[<state>=<value>,...]`, the syntax of `/setblock` and of block state
arguments in commands. Agents already know it, and the `name=value` pairs
that `block` prints as `props` go into the brackets as they are. Tags and
patterns take brackets too, as Minecraft's block predicates do for tags.

Alternative: an option such as `state:axis=y`. That would need declaring on
every command with a block slot. It would also part the state from the block
it qualifies.

### D2: A `block-state` slot type

A new production and `ArgType.BLOCK_STATE`, rather than widening `id`, `tag`
and `pattern`:

```ebnf
block-state  ::= ( pattern | tag | id ) "[" state ( "," state )* "]"
state        ::= state-name "=" state-value
state-name   ::= [a-z0-9_]+
state-value  ::= [a-z0-9_]+
```

Those three slots also take items, entity types and events, where states
mean nothing. Forms that take a block add the new type. The type is in the
API, so extension commands can take it too. `pattern` comes first because
alternatives are atomic: tried first, `id` would take `create` from
`create:*[a=b]` and then fail at `:`.

### D3: States checked against the block, matched per state

A pure `BlockStates` splits the bracket part into ordered name–value pairs
and rejects a state named twice. It needs no Minecraft classes, so it is
unit-tested.
- For a block id, `BlockMatcher` checks each name and value against the
  block's state definition and fails with the states or values it has.
- For a tag or pattern, a block without the state, or with another value,
  doesn't match.
- The cache answers per block state, which Minecraft shares between all
  blocks in that state. The id, tag or pattern and the states are then
  tested once per state a scan meets, not once per block.

### D4: A higher limit, not paging

`find`'s and `sable:find`'s limit go from 100 to 1000; the radius stays 128.
1000 text rows are about 40 KB, which an agent reads in one reply. `count=`
still says when matches were left out.

Alternatives:
- `offset=` paging: its nearest-first order shifts whenever the player
  moves.
- A file: the agent would have to read it back.

### D5: Aim at the face of the support's shape

`place` aims at the centre of the support's shape bounds, moved to the side
of those bounds that faces the target cell. On a carpet that is its top
surface; on a fence post, the side of the post. A pure `ShapeBounds` does
the arithmetic so a unit test covers it, including the choice of bounds:
those enclosing the shape's boxes, or the whole cell for an empty shape,
which keeps aiming at the cell face, as today. A support whose bounds' face
centre isn't on the shape, such as the top of stairs, can still miss; D6
then says what was hit.

The click places the block beside whichever face it hits. Until now `place`
only checked that the crosshair hit the support, so a ray that met another
face first, as it does for a narrow post seen from the side, put the block
in another cell. `place` now also checks the face and fails instead.

### D6: Blocked-view errors name the hit

The error text names what the crosshair hits:
- a block: its id and position (in its plot aboard a sub-level);
- an entity: its type and `e<id>`;
- a miss: `nothing`.

The error codes stay `out_of_reach`.

### D7: Help and README only, for held input and `\;`

The behaviour is specified and implemented. The movement and input help and
the README say how long held input lasts. A failing step not marked `?`, in
any script with an action, releases everything its connection holds, not
only what that script held. `help server` and the README give
`\;` with the NBT int-array example.

### D8: `SpecGrammarTest` reads the change's grammar

It takes the grammar the way `GrammarTest` does: an active change's that
modifies it, else the main spec's. The lexicon then changes together with
the delta, and the tests pass before the change is archived.

### D9: `mine` aims at the block's shape

`mine` aimed at the middle of the cell. Over a carpet, a rail or a pressure
plate that point is in the air, so the crosshair passed over the block, hit
the ground behind it, and `mine` failed naming the ground as what was in the
way. It now aims at the centre of the block's shape (`Shapes.centre`), as
`use` already does, for the reach check too.

## Risks / Trade-offs

- [A block id's state check needs the registry] → Parsing happens while the
  command runs, on the client thread, as `BlockMatcher.parse` does now.
- [A reply of 1000 rows in JSON mode is larger] → Only when an agent asks for
  that many; the default stays 5.
- [Two matchers for the same token cache separately] → Unchanged: a matcher
  lives for one command.
