# Proposal

## Why

A `sample` of a wide query, `status` or an extension's readout of a vehicle
with twenty-odd fields, gives rows too long to read, and a script that
wants three of those fields picks them out of every row with patterns that
break when a field moves. JSON output helps a program, not an agent reading
the reply; the agent knows which fields it wants and could say so.

## What Changes

- `sample … fields=<name>[,<name>…]` keeps only the named fields in each
  row, besides the row's tick and, with several queries, its query, and
  `err` with the code for a query that failed, in text and JSON alike and
  hidden fields too; a field a row doesn't have is simply absent, not an
  error.
- A name that can't be a field name, by the grammar's `name`, fails with
  `err syntax` before the script runs.
- The help of `sample`, `docs/command-language.md`, the grammar files and
  the 1.1.0 section of `CHANGELOG.md` describe the option.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: `fields=` on `sample`.

## Impact

- `protocol/Grammar.java`, `protocol/Parser.java`: the option on `sample`.
- `protocol/Statement.java`: the fields a sample keeps.
- `protocol/Engine.java`: rows filtered as `Sampler.row` builds them, so
  the queries still run in full and every format draws on the same rows.
- Tests: parser tests for the option and a bad name, engine tests for the
  filtered rows, with one query and with two, and for a failed query.
- `docs/command-language.md`, `docs/grammar.ebnf`, `docs/grammar.xhtml`,
  `CHANGELOG.md`.
