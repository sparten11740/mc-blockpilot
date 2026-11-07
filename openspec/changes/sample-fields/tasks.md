# Tasks

## 1. Fields on a sample (command-language)

- [ ] 1.1 Add `fields=` to the core grammar's `sample` and to the parser's sample options, checking each name against `name`, and carry the names in `Statement.Sample`; add parser tests for one and several names, with `clock=` and with `while`, and a bad name; verify the protocol tests and `theCoreGrammarIsTheSpecs` pass
- [ ] 1.2 Keep only the named fields in the sampler's rows, besides the tick, the query and `err`; add engine tests with one query, with two and with a failed query; verify the protocol tests pass
- [ ] 1.3 Describe `fields=` in `help sample` (the constructs' help), `docs/command-language.md` and the 1.1.0 section of `CHANGELOG.md`, and regenerate `docs/grammar.ebnf` and `docs/grammar.xhtml`; verify the grammar checks pass

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on the 1.21.1 NeoForge and 26.3 Fabric targets; verify both pass

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
