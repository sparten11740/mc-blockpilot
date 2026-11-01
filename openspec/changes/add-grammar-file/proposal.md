# Proposal

## Why

The command language's grammar exists in two halves. The core productions are
in the `command-language` spec and copied in code, and the productions of the
commands are generated from their declarations and printed only by
`help grammar` in a running game. Nobody can read the whole grammar without
starting Minecraft, and nothing but Blockpilot's own parser checks that it is
well-formed EBNF.

## What Changes

- `docs/grammar.ebnf` holds the entire grammar: the core productions and one
  production per built-in command and option, exactly as `help grammar`
  prints them. A script writes it from a dev client.
- `docs/grammar.xhtml` shows the grammar as railroad diagrams, generated from
  the file by the Railroad Diagram Generator's library (`rr-lib`, Apache-2.0).
- A unit test checks the file:
  - it parses as W3C EBNF with `rr-lib`'s parser, an implementation
    independent of Blockpilot's;
  - every production is defined once, every name a production refers to is
    defined, and every production is reachable;
  - its core productions are the spec's, and its productions of the commands
    the engine itself declares are the generated ones;
  - the diagrams cover exactly its productions.
- The smoke test checks that what a dev client's `help grammar` prints is in
  the file, so the game's own commands can't drift from it either.
- The command-language documentation links both files.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: the grammar file and its diagrams.

## Impact

- New `docs/grammar.ebnf`, `docs/grammar.xhtml` and `tools/grammar.py`.
- A `grammarDiagrams` Gradle task, and `rr-lib` with Saxon as test and tool
  dependencies only; nothing is added to the mod's jars.
- A new grammar file test, and a smoke-test step.
- `docs/command-language.md`.
