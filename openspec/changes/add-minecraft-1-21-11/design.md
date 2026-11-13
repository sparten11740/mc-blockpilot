# Design

## Context

See `proposal.md` for why. A spike in this change's worktree declared
`1.21.11-fabric` and `1.21.11-neoforge` against `src/26.1/` and compiled them.

- **Dependencies:** Fabric API `0.141.6+1.21.11` (Fabric Loader 0.17.3 or
  newer), NeoForge `21.11.48` and Parchment `1.21.11` / `2025.12.20` resolve.
  1.21.11 ships obfuscated, with Mojang's mappings, so Fabric builds it with
  the remapping Loom, as 1.21.1.
- **Setup:** Loom stopped first on `src/26.1/`'s access widener, whose header
  says `official`, the namespace of 26.x's own names; a remapped game wants
  `named`.
- **Errors:** 57 on Fabric and 59 on NeoForge. Most are shared code taking
  the 1.21.1 branch of a `//? if >=26.1` condition where 1.21.11 already has
  the newer API (window handle, permission sets, value output, key mapping
  categories, game profiles, `serverLevel()`, the timer, per-stage render
  events on NeoForge). The rest are `src/26.1/` naming what only 26.1 has
  (render state packages, `DeviceList`, `CharacterEvent`'s constructor).
- **What the compiler can't see:** mixin targets, checked against 1.21.11's
  decompiled sources as for 1.20.1.

## Goals / Non-Goals

**Goals:**
- Both 1.21.11 jars pass the test script with the JSON shapes of their
  loader's other targets.
- The thirteen existing jars keep their behaviour.

**Non-Goals:**
- A 1.21.11 vanilla agent, Sable on 1.21.11, a release.
- Baritone in 1.21.11 dev runs.

## Decisions

### D1. src/26.1 serves 1.21.11

The 1.21.11 nodes build from `src/26.1/` (`version_sources=26.1`): 1.21.11
shares most of 26.1's client, from the render state split to the input
events, which `src/1.21.1/` predates.

- **Conditions:** each `//? if >=26.1` and each rename rule dated 26.1 is
  rechecked against 1.21.11's sources. Where 1.21.11 has the newer form, the
  boundary moves to 1.21.11; where only 26.1 has it, 26.1 stays and 1.21.11
  takes the older form. No release between 1.21.1 and 1.21.11, nor between
  1.21.11 and 26.1, is built, so these are the only boundaries.
- **Rename rules:** a rule moves to 1.21.11 where that release already has
  the new name; the existing rule that two rules mustn't overlap still holds.
- *Alternative:* `src/1.21.1/` with conditions for 1.21.11. Rejected: the
  differences from 1.21.1 are the larger set.

### D2. The access widener's header per node

The Fabric build gives `src/26.1/`'s access widener the header of the node's
kind: `official` where the game ships with Mojang's names, `named` where Loom
maps the game to them. Loom reads the file while it sets the game up, before
any task runs, so the build writes the copy as it configures; the jar's copy
gets the same header as it is processed.

## Risks / Trade-offs

- **A wrong boundary can compile on both and still differ** → both 1.21.11
  nodes run the full test script, and so do a 26.x node and a 1.21.1 node,
  since moving a boundary changes what they compile too.
- **Mixins the compiler can't check** → checked against 1.21.11's sources
  before the first launch, and any not found shows in the client log.
