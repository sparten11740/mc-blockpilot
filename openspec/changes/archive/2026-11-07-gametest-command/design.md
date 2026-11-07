# Design

## Context

What the spike (D1) found in the game's and the loaders' sources:

- 1.21.1 registers each test as Java code: a `TestFunction` in the static
  `GameTestRegistry`, named `<class>.<method>` in lower case, with a
  namespaced structure template. NeoForge registers `@GameTest` methods of
  `@GameTestHolder` classes at server start, only outside production and
  with `-Dneoforge.enableGameTest=true` (or in an IDE), limited to the
  namespaces in `neoforge.enabledGameTestNamespaces` when that is set.
  Fabric registers the test classes a mod names under its `fabric-gametest`
  entrypoint, in development or not, and its `/test` command is on in every
  client. `GameTestRegistry.register(Method)` is public, but Fabric's mixin
  on turning a method into a test refuses a class its entrypoint didn't
  name; `getAllTestFunctions()` returns the registry's own collection and
  `TestFunction` is a public record, so a ready-made test can be added to
  it on either loader.
- 26.1, 26.2 and 26.3 share one framework, unchanged between them. Tests are
  entries of the `test_instance` registry, loaded from data packs and named
  `namespace:path`: a function test points at code in the built-in
  `test_function` registry, while a block-based test is a structure whose
  test blocks (start, log, fail, accept) decide the result, with no code.
  `/test` is registered in every environment.
- The game ticks running tests through `GameTestTicker`: vanilla 26.x on
  every tick the tick rate runs normally, Fabric on 1.21.1 through its
  API's mixin, and NeoForge, on 1.21.1 and on 26.x alike, only with
  GameTests enabled as above. A test started without that never moves.
- Test structures are `.nbt` templates: on 26.x from a data pack's
  `structure` folder, on 1.21.1 from resources or from the world's
  `generated/<namespace>/structures` folder, read when first needed.
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
- Registry entries a data pack adds make the game count a world's settings as
  experimental, so on 26.x it asks before loading a world whose data pack has
  GameTests whether to make a backup first, every time on vanilla and Fabric,
  once on NeoForge, which remembers the answer. `join world` waited on that
  question until it timed out. The question is a `BackupConfirmScreen` with
  the title `selectWorld.backupQuestion.experimental` on every version, and
  its `onProceed` listener is a protected field.

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

It fails, in this order: with `err no_server` on a client without an
integrated server, as every server command does; with `err unsupported`
naming `-Dneoforge.enableGameTest=true` in a development environment on
NeoForge without GameTests enabled, on 1.21.1 and 26.x alike, whatever the
selector; and with `err no_test` when the selector matches nothing.

### D3: No test mod: Blockpilot tests itself

The tests the smoke check runs need no mod of their own, and the smoke check
writes their structures itself, with an NBT writer of its own:

- On 26.x they are data, not code: the smoke check writes a data pack into its
  world with two block-based tests, one whose start block powers an accept block
  beside it and one whose start block powers a fail block with a message, and
  joins the world again so the registry loads it. That works the same on every
  loader and on the vanilla agent.
- On 1.21.1, where tests are code, Blockpilot adds its own two tests to the
  registry's collection as ready-made `TestFunction`s, from a source set
  only the 1.21.1 dev runs include, the first time `gametest run` lists the
  tests; their template is an empty structure the smoke check writes into
  its world's `generated` folder. The published jars and their metadata
  never name them. On NeoForge they are added only with GameTests enabled,
  which every NeoForge dev run turns on.

The failing test fails on purpose, so the check can read a `failed` row and
its message. The check ends with `server test clearall`, which removes the
tests' structures from its world.

### D4: Test worlds skip the experimental-settings question

When `join world`, or `snapshot save` or `restore`, waits for a world it opened
and the game asks about experimental settings, a test world, made by `create
world` and marked so (the same gate `delete world` uses), loads as the
question's skip button does: without a backup, and the reply says
`experimental=true`. Test worlds are throwaway, and a backup on every join of
one would cost time for nothing. Any other world may be a player's own, with
experimental features the question protects, so Blockpilot doesn't answer for
it: the wait fails at once with `err join_failed` naming the question, which
stays on screen for a person, or an agent told to, to answer. Before, it waited
until its timeout. Only that question is met: the others on the same screen,
such as for a world last played in another version, are left alone. The
question's listener is a protected field of the same name on every version,
reached through one accessor mixin in the shared tree, listed in both trees'
mixin configs.

## Risks / Trade-offs

- [A data pack's test instances load only with the world] → The smoke check
  joins its world again after writing the pack; confirm in 2.1 that this is
  enough.
- [Tests build structures in the player's world] → As `/test` does; the help
  says so, and `server test clearall` removes them.
- [1.21.1's dev-only tests need the dev runs to include an extra source set]
  → One source set for both loaders; the published code looks for its class
  by name and finds none outside the dev runs.
- [A dev world of a mod author's own, with a data pack's GameTests, fails to
  join] → The question stays on screen to answer once; on NeoForge the game
  remembers that answer, and `create world` makes a test world that joins
  without it.
- [NeoForge runs GameTests only in development] → `gametest run` answers
  `err unsupported` there, naming the switch; mod authors test in their dev
  environment anyway.
