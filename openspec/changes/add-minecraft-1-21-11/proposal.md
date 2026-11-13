# Proposal

## Why

Minecraft 1.21.11 is the last release before 26.1 and has the most Fabric
modpacks after 1.20.1, yet Blockpilot skips it, so mods built for it can't be
play-tested there.

## What Changes

- Two new targets: `1.21.11-fabric` (Fabric API 0.141) and `1.21.11-neoforge`
  (NeoForge 21.11), on Java 21. The existing thirteen stay.
- They build from `src/26.1/`, which then serves 1.21.11 to 26.3: 1.21.11
  already has most of what came after 1.21.1, so its differences from 26.1
  are fewer than from 1.21.1.
- Every command answers as on 1.21.1 and 26.1. Conditions and rename rules
  that name 26.1 are rechecked: what 1.21.11 already has moves to 1.21.11, and
  what only 26.1 has gets a branch for 1.21.11.
- The test script and the release workflow cover fifteen targets, with
  1.21.11 rows for CurseForge.
- README and docs list the new targets.

Out of scope: a vanilla agent for 1.21.11, Sable on 1.21.11, and cutting a
release.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `multi-target-build`: the targets include `1.21.11-fabric` and
  `1.21.11-neoforge`.
- `extension-api`: the one API jar works with the 1.21.11 jars too.

## Impact

- **Build:** two new nodes; the Fabric build gives `src/26.1/`'s access
  widener the header of the node's namespace, as 1.21.11 is remapped and 26.x
  isn't.
- **Code:** about 60 compile differences in the spike, mostly conditions
  whose boundary moves from 26.1 to 1.21.11; mixins checked against 1.21.11.
- **Dependencies:** Fabric API, NeoForge and Parchment for 1.21.11.
- **Tests:** `tools/test_targets.py` runs the new targets; the smoke test
  learns any 1.21.11 data differences.
- **Release:** `.github/workflows/release.yml` builds fifteen jars.
- **Docs:** `README.md`, `docs/building.md`, `docs/dev-environment.md`, the
  changelog.
