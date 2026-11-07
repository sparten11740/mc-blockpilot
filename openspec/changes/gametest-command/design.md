# Design

## Context

What the spike (D1) found in the game's and the loaders' sources:

- 1.21.1 registers each test as Java code: a `TestFunction` in the static
  `GameTestRegistry`, named `<class>.<method>` in lower case, with a
  namespaced structure template. Its `register(Class)` and
  `register(Method)` are public and static, so code of any mod can add
  tests. NeoForge registers `@GameTest` methods of
  `@GameTestHolder` classes at server start, only outside production and
  with `-Dneoforge.enableGameTest=true` (or in an IDE), limited to the
  namespaces in `neoforge.enabledGameTestNamespaces` when that is set.
  Fabric registers the test classes a mod names under its `fabric-gametest`
  entrypoint, in development or not, and its `/test` command is on in every
  client.
- 26.1, 26.2 and 26.3 share one framework, unchanged between them. Tests are
  entries of the `test_instance` registry, loaded from data packs and named
  `namespace:path`: a function test points at code in the built-in
  `test_function` registry, while a block-based test is a structure whose
  test blocks (start, log, fail, accept) decide the result, with no code.
  `/test` is registered in every environment.
- Both run tests through `GameTestRunner`, building one `GameTestInfo` per
  test, and every one of those classes is public: `GameTestInfo` takes
  listeners (`addListener`) told when its test passes or fails, and reports
  the failure (`getError`: a `Throwable` on 1.21.1, a `GameTestException`
  with a `getDescription()` on 26.x). No mixin is needed on any version.
- `GlobalTestReporter`, which every result passes through as well, is one
  global that `replaceWith` overwrites without returning the old one, so
  using it would push aside the reporter in place, such as Fabric's report
  file.
- Tests build their structures in the world next to the player, as `/test`
  does.

## Goals / Non-Goals

**Goals:**
- GameTest results through a Blockpilot command, as rows an agent reads, on
  every target.

**Non-Goals:**
- Writing GameTests, or a GameTest that drives Blockpilot.
- Replacing the smoke test with GameTests: they run on the server, while
  most of Blockpilot (input, screens, frames, recording) is on the client.

## Decisions

### D1: A spike first

Done; its findings are the Context above. It covers all 11 targets: the 9 of
26.x, on every loader and the vanilla agent, and the 2 of 1.21.1, where the
loader has registered tests.

### D2: `gametest` owns its run

`gametest run <selector>` matches the selector against the ids of the
registered tests, `namespace:path` on 26.x and `<class>.<method>` on 1.21.1,
where `*` matches any run of characters. It builds a `GameTestInfo` for each
match, adds a listener to each, and starts them in a `GameTestRunner` of its
own beside the player, answering once all have finished or the timeout
passes, one row per test with `passed` or `failed` and its message. Each
test fails by itself once its own time limit passes, so the run always ends
with its last test; `timeout=` caps the wait, as on other actions. Owning
the run keeps it to the tests it started and leaves the global reporter
alone. One adapter per version family, 1.21.1 and 26.x, lists the tests and
builds their infos; the command is shared.

It fails, in this order: with `err unsupported` on a client without an
integrated server; with `err unsupported` naming
`-Dneoforge.enableGameTest=true` in a development environment on NeoForge
1.21.1 without GameTests enabled, whatever the selector; and with `err
no_test` when the selector matches nothing.

### D3: No test mod: Blockpilot tests itself

The tests the smoke check runs need no mod of their own:

- On 26.x they are data, not code: the smoke check writes a data pack into
  its world with two block-based tests, one whose start block reaches an
  accept block and one that reaches a fail block with a message, and joins
  the world again so the registry loads it. That works the same on every
  loader and on the vanilla agent.
- On 1.21.1, where tests are code, Blockpilot registers its own two tests, in a
  source set only the dev runs include, through the public
  `GameTestRegistry.register`, with their templates in that source set's
  resources, as the client starts, before any world loads. The published jars
  and their metadata never name them. On NeoForge they follow its switch,
  registered only with GameTests enabled, and the dev runs enable them; a run
  without the switch shows `err unsupported`.

The failing test fails on purpose, so the check can read a `failed` row and
its message. The check ends with `server test clearall`, which removes the
tests' structures from its world.

## Risks / Trade-offs

- [A data pack's test instances load only with the world] → The smoke check
  joins its world again after writing the pack; confirm in 2.1 that this is
  enough.
- [Tests build structures in the player's world] → As `/test` does; the help
  says so, and `server test clearall` removes them.
- [1.21.1's dev-only tests need the dev runs to include an extra source set]
  → One source set for both loaders, registered by a call only the dev runs
  make.
