## 1. Java 17

- [x] 1.1 Rewrite the Java 21 constructs in shared code (switch patterns, `case null`, `when` guards, `List.getFirst`/`reversed`, `HttpClient.shutdown`, `Math.ceilDiv`) for Java 17, keeping behaviour; unit tests pass on 1.21.1-neoforge
- [x] 1.2 Compile the API jar with `--release 17`; the 1.20.1 nodes compile main with Java 17 and tests with Java 21

## 2. 1.20.1 on Fabric

- [x] 2.1 Declare the `1.20.1-fabric` node (Java 17, Fabric API 0.92, Parchment 2023.09.03, `version_sources=1.21.1`)
- [x] 2.2 Make shared code and `src/1.21.1/` compile on 1.20.1 with inline conditions and backward rename rules (reach, items, world loading, screens, packets, chunks, GameTests, recipes)
- [x] 2.3 Port tick freeze, step and rate to 1.20.1 behind a facade that uses the tick rate manager from 1.20.3 on
- [x] 2.4 Check every mixin target against 1.20.1 and fix the stale ones; the client loads to a world
- [x] 2.5 Full smoke test on 1.20.1-fabric passes with 1.21.1-fabric's JSON shapes; tell the Glowline session

## 3. 1.20.1 on Forge

- [x] 3.1 Add the `forge` loader: `build.forge.gradle.kts` with legacyforge, mods.toml template, refmap, the `1.20.1-forge` node
- [x] 3.2 Port the NeoForge entry point and services to `dev.blockpilot.platform.forge`, with a no-argument mod constructor
- [ ] 3.3 Full smoke test on 1.20.1-forge passes with 1.21.1-neoforge's JSON shapes; load the jar once on NeoForge 47.1

## 4. Tools, release and docs

- [x] 4.1 `tools/test_targets.py` and `tools/smoke_test.py` know the 1.20.1 targets and the `forge` loader, comparing Forge with NeoForge
- [x] 4.2 `release.yml` builds thirteen jars and uploads the 1.20.1 rows
- [x] 4.3 README, `docs/building.md`, `docs/dev-environment.md`, `docs/commands.md` and the changelog cover 1.20.1
- [ ] 4.4 All thirteen targets compile; the existing ones pass their unit tests and a smoke run on 1.21.1-fabric
