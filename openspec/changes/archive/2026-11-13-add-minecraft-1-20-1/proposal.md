# Proposal

## Why

Minecraft 1.20.1 is still the most-played modded version, and Blockpilot starts
at 1.21.1, so mods that ship for 1.20.1 can't be play-tested there. Their
authors fall back to headless GameTests and manual checklists for their
biggest audience.

## What Changes

- Two new targets: `1.20.1-fabric` (Fabric API 0.92) and `1.20.1-forge`
  (Forge 47), both on Java 17. Fabric comes first, then Forge. The Forge jar
  also loads on NeoForge 47.1, which runs Forge 1.20.1 mods. The existing
  eleven targets stay.
- Code shared by every target, and the extension API jar, move to the Java 17
  language and library: pattern `switch` becomes `instanceof` chains, and
  Java 21 calls get Java 17 equivalents. Newer targets still build with their
  own Java.
- Every command answers the same on 1.20.1 as on 1.21.1. 1.20.1 has no tick
  rate manager, which arrived in 1.20.3, so `tick freeze`, `unfreeze`, `step`
  and `rate` run on Blockpilot's own port of it. Reach, item names and
  enchantments, packet names in `perf` and the GameTest runner use 1.20.1's
  APIs. Any command 1.20.1 can't support is reported as a gap through `help`.
- A new `forge` loader beside `neoforge`, `fabric` and `vanilla`: its own build
  script (ModDevGradle's legacyforge plugin), entry point and platform services,
  and `//? if forge` in the sources.
- The test script knows the new targets, comparing Forge's replies with
  NeoForge's, and the release workflow builds thirteen jars, with 1.20.1 rows
  for CurseForge.
- README and docs list the new targets.

Out of scope: a vanilla agent for 1.20.1, Sable on 1.20.1 (Sable doesn't
support it), Forge for versions after 1.20.1, and cutting a release.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `multi-target-build`: the targets include `1.20.1-fabric` and
  `1.20.1-forge`; shared code and the API jar keep to Java 17; the Forge jar
  carries Forge's metadata; the test script covers the new targets and compares
  Forge with NeoForge.
- `tick-control`: freeze, step and rate behave the same on 1.20.1, without the
  game's tick rate manager.
- `extension-api`: the one API jar works with the 1.20.1 jars too.

## Impact

- **Build:** two new nodes and a `forge` loader script; `src/1.20.1/` as the
  1.20.1 tree beside `src/1.21.1/`; the API jar compiled for Java 17.
- **Code:** about 70 Java 21 constructs in shared code rewritten for Java 17;
  inline version conditions and rename rules for about 90 API differences;
  mixins checked against 1.20.1's targets; a tick-rate port in the 1.20.1 tree;
  `dev.blockpilot.platform.forge`.
- **Dependencies:** Fabric API 0.92 and Parchment for 1.20.1; Forge 47 through
  ModDevGradle's legacyforge plugin.
- **Tests:** `tools/test_targets.py` and `tools/smoke_test.py` learn the new
  targets and the `forge` loader.
- **Release:** `.github/workflows/release.yml` builds thirteen jars.
- **Docs:** `README.md`, `docs/building.md`, `docs/dev-environment.md`,
  `docs/commands.md` and `CHANGELOG.md`.
- **Existing jars:** behaviour stays, though the shared code they compile from
  changes, so a newer target is smoke-tested again.
