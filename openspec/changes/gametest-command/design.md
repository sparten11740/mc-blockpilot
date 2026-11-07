# Design

## Context

- GameTests run on the integrated server. On 1.21.1 they are registered
  through the loader, NeoForge's or Fabric's, and enabled per namespace in
  a dev environment; the 1.21.5 game is expected to bring its own test
  command and registry, which 26.x would keep. Both claims are to be
  confirmed by the spike (D1).
- The runner reports results through listeners and the log, not through a
  query; each test needs a structure template.
- The vanilla agent targets have no loader to register tests through.

## Goals / Non-Goals

**Goals:**
- GameTest results through a Blockpilot command, as rows an agent reads.

**Non-Goals:**
- Writing GameTests, or a GameTest that drives Blockpilot.
- The vanilla agent targets, unless the spike finds tests can run there.

## Decisions

### D1: A spike first

For 1.21.1 NeoForge, 1.21.1 Fabric and 26.x, find the hook that gives a
result per test and its failure message (a listener on the test's info, or
the runner's tracker), whether it is reachable without a mixin, and what
enables GameTests on each. The rest of this design follows what it finds,
and versions without a hook answer `err unsupported`.

### D2: `gametest` listens to the runner

`gametest run <selector>` starts the selected tests through the runner the
version offers and listens for each test's end, one row per test with
`passed` or `failed` and its message, answering once all have finished or
the timeout passes. One adapter per version family hides the runner; a game
without GameTests enabled answers `err unsupported` naming the property or
setting that enables them on its loader.

### D3: A dev-only fixture

Each loader that can register tests gets a small fixture with one passing
and one failing test and its structure template, in a dev-only source set
the published jars leave out; the smoke check runs it where it exists and
skips elsewhere.

## Risks / Trade-offs

- [Runners differ widely between versions and loaders] → The spike decides
  the versions covered; the rest answer `err unsupported`.
- [Fixtures for several loaders are work of their own] → Kept minimal and
  dev-only.
