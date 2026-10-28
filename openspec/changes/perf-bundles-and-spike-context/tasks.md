# Tasks

## 1. Bundle contents (performance-capture)

- [x] 1.1 Count a bundle's sub-packets by type in `ConnectionMixin` for both versions (D1), with the README's performance row; verify on the dev client that a capture while entities spawn (`server summon` of several) lists `add_entity` with their count beside `bundle`

## 2. What the server did on each tick (performance-capture)

- [x] 2.1 Add the per-tick counters to `Perf` with their one-tick hold (D2), platform hooks for chunk loads and generations on NeoForge and Fabric, and mixins on `PlayerChunkSender.sendChunk` and `PersistentEntitySectionManager.addEntity` for both versions; put the counts on spikes, in the file's timeline and in the summary's totals, with the text overview (D3); add unit tests for the hold and for leaving zero counts out; verify on the dev client that flying or teleporting into terrain never visited during a capture gives spikes with `chunks_generated`, and returning over it gives `chunks_loaded` or `chunks_sent` instead

## 3. Integration checks

- [x] 3.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
