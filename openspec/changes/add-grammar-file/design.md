# Design

## Context

- `Grammar.CORE` is the core grammar, and `GrammarTest` keeps it equal to the
  spec's EBNF. `Grammar.command` turns a command's declaration into
  productions, and `help grammar` prints the core lines, then each
  registered command's productions, one row each with the command as key.
- The unit tests run without Minecraft on the classpath. The engine's own
  commands (`help`, `wait`, `sample`, …) can be registered in a test with
  `FakeGame`; the game's commands can't, since their classes need Minecraft.
- The core grammar parses any call generically (`call ::= command-name …`).
  The `<command>-command` and `<command>-option` productions describe one
  command's tokens each, and nothing in the core refers to them.
- `help grammar` in the 26.1 dev client prints 209 rows, which `rr-lib` 2.6
  parses as valid W3C EBNF. Its parser rejects a syntax error, such as an
  unclosed group, but accepts a name defined twice or never defined.
- `rr-lib` (`de.bottlecaps.rr:rr-lib`, Apache-2.0) is the library of the
  Railroad Diagram Generator. Its parser class needs Saxon-HE on the
  classpath; its command line writes railroad diagrams as one XHTML page with
  embedded SVG.

## Goals / Non-Goals

**Goals:**
- One readable file with the whole grammar, and diagrams of it.
- An EBNF check that doesn't share code with Blockpilot's parser.
- Drift between the file and the code caught by the tests.

**Non-Goals:**
- Making the file the source of the core grammar: the spec stays normative,
  and changes keep editing the grammar through it.
- Diagrams GitHub renders in place: it shows `.xhtml` as source. The page is
  for opening locally or from a raw link.

## Decisions

### D1: The file comes from a dev client

`tools/grammar.py --game-dir <run>` runs `help grammar` in JSON mode through
the launcher and writes each row's EBNF as a line, under a comment saying it
is generated. It runs against the 1.21.1 NeoForge client, which has Sable, so
the `sable:` productions are in the file too.

Alternative: generating it in a unit test. The game's command classes need
Minecraft, which the tests don't have.

### D2: Three checks, split by what can see what

- **Unit test** (no game): the file parses with `rr-lib`; each production is
  defined once; every referenced name is defined; every production is
  reachable from `connection`, `script` or a command or option production;
  the core lines equal `Grammar.coreLines()`; the engine's command
  productions are in the file; the diagrams page has a diagram for each
  production name and no other.
- **Smoke test** (dev client): every row `help grammar` prints is a line of
  the file. A client without Sable prints fewer rows, which is fine.
- References are read from the right-hand sides with quoted literals,
  character classes, `#x` escapes and comments removed. W3C EBNF names may
  hold `.`, so `sable.find-command` counts as one name.

### D3: Diagrams from `rr-lib`'s command line

A `grammarDiagrams` Gradle task runs `de.bottlecaps.railroad.Railroad
-out:docs/grammar.xhtml docs/grammar.ebnf` on its own configuration, so
`rr-lib` and Saxon never reach the mod's jars. The tests get `rr-lib` and
Saxon as test dependencies.

## Risks / Trade-offs

- [Forgetting to regenerate after a change to a command] → The smoke test
  names the row that's missing from the file; the fix is two commands, which
  `docs/command-language.md` gives.
- [Saxon is large] → Test and tool classpaths only.
