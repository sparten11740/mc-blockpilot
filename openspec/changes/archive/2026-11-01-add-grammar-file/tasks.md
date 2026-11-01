# Tasks

## 1. The file and its diagrams

- [x] 1.1 Add `tools/grammar.py --game-dir <run>` writing `docs/grammar.ebnf` from `help grammar` (D1), and a `grammarDiagrams` Gradle task writing `docs/grammar.xhtml` with `rr-lib` (D3); generate both from the 1.21.1 NeoForge dev client; verify the file has the core and a production per command including `sable:`, and that the page opens with a diagram per production
- [x] 1.2 Link both files from `docs/command-language.md`, with the two commands that regenerate them; verify the links resolve

## 2. Checks

- [x] 2.1 Add a grammar file test (D2): `rr-lib` parse, unique and defined names, reachability, core lines, the engine's command productions, diagrams for exactly the productions; verify it passes, and fails on an unclosed group, a name defined twice, an undefined name and a stale core line
- [x] 2.2 Add a smoke-test step checking every `help grammar` row is in the file; verify it passes against the 1.21.1 and 26.1 dev clients and fails when a row is removed from the file

## 3. Integration checks

- [x] 3.1 Run `./gradlew build` and `openspec validate add-grammar-file --strict`; verify both pass
