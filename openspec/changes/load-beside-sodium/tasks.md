# Tasks

## 1. Loading beside a renderer (multi-target-build)

- [x] 1.1 Give `LevelRendererMixin` priority 1100 and make its `setupRender` redirects optional (D1); verify the 1.21.1 NeoForge client crashes beside Sodium 0.8 without it, as reported, and reaches a screen with it, and the same with Embeddium on 1.20.1 Forge; verify 1.21.1 Fabric with Sodium 0.8, 1.20.1 Fabric with Sodium 0.5, 1.21.11 Fabric with Sodium 0.8.15 and 26.3 Fabric with Sodium 0.9 reach a screen

## 2. Rebuilds (performance-capture)

- [x] 2.1 Add the two meshing-task mixins (D2) to both mixin configs; verify all targets compile and the unit tests pass

## 3. Checks

- [x] 3.1 Run the test script beside each renderer (D3): 1.21.1 NeoForge and 1.21.1 Fabric with Sodium 0.8, 1.20.1 Fabric with Sodium 0.5, 1.20.1 Forge with Embeddium and 26.3 Fabric with Sodium 0.9; verify every target passes, `perf` rebuilds included
- [x] 3.2 Verify a camera placed away beside Sodium on 1.21.1 NeoForge draws the world around it, by its frame's pixels
- [x] 3.3 Run the test script without a renderer on 1.21.1 NeoForge and 26.3 Fabric; verify both pass
- [x] 3.4 Say in the README that Blockpilot runs beside Sodium and Embeddium, and add the fix to the CHANGELOG under 1.3.1

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
