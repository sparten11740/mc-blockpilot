# Proposal

## Why

Sodium is among the most used client mods and in most modpacks, and shader
packs through Iris need it; on 1.20.1 Forge, Embeddium takes its place.
Blockpilot 1.3.0 stops the game from starting beside either on 1.20.1 and
1.21.1, as both replace the game's `LevelRenderer.setupRender`, into which
Blockpilot injects. Beside Sodium on the versions where it starts, `perf`
counts no section rebuilds, as Sodium meshes sections with its own tasks.

## What Changes

- Blockpilot loads beside a renderer that replaces `setupRender`, on 1.20.1
  and 1.21.1, and leaves that renderer's drawing as it is. A camera placed
  `away` still draws the world around it there, as the renderer centres on
  the camera already.
- `perf` counts the section rebuilds of Sodium (0.5 on 1.20.1 Fabric, 0.6
  and later elsewhere) and of Embeddium, as it counts the game's own.
- The README says Blockpilot runs beside Sodium and Embeddium.

Out of scope: Iris shader packs beyond loading beside them, other renderers,
and testing beside Sodium in CI.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `multi-target-build`: Blockpilot works beside a mod that replaces how the
  game draws the world, such as Sodium.
- `performance-capture`: section rebuilds include those of Sodium and
  Embeddium.

## Impact

- **Mixins:** `LevelRendererMixin` (1.20.1 and 1.21.1) gets a priority above
  the default and optional redirects; two new mixins count Sodium's and
  Embeddium's rebuilds, skipped without them.
- **Docs:** README, CHANGELOG under 1.3.1.
- **Dependencies:** none; Blockpilot doesn't compile against Sodium.
