# Tasks

## 1. Stack samples (performance-capture)

- [x] 1.1 Add `ClientPlatform.modOf(String className)` with the services taking a class name (D3), and `StackSamples` with unit tests for the innermost-mod rule, library frames passed over, the `minecraft` and `java` fallbacks, `native` top frames and the rankings; verify the tests pass on all four targets
- [x] 1.2 Add the `profile` keyword, the in-tick flag in `MinecraftServerMixin` (both versions), the sampler thread (D1, D2), `server.stacks` in the summary with `mod` and `frame` rows, the help and the README performance row; verify on the 1.21.1 dev client that `perf start a profile` then `perf stop` after 10 s with a moving test sub-level lists `mod` rows including sable and `frame` rows, with the sample counts in the JSON, and that a capture without `profile` has no `stacks`

## 2. Sections, timed form and loaded counts (performance-capture)

- [x] 2.1 Run the server's metrics recorder with `profile` and add `server.sections`, `sections_s` and `profile_report` with `section` rows (D4), with `err busy` and `err no_server`; verify on the 1.21.1 dev client that a 15 s profiled capture lists `section root/tick` among its rows with sections covering 10 s and a saved report, that a 5 s one covers about 5 s, and that `key f3+l` then `perf start x profile` answers `err busy` and `perf status` shows no capture
- [ ] 2.2 Add the timed form `perf <duration> [name] [profile]` (D5) with the help and README; verify on the 1.21.1 dev client that `perf 5s` answers with a summary of about 5 s, and that `perf 30s` cancelled by `stop` from a second connection leaves `perf status` showing the capture, which `perf stop` then ends
- [ ] 2.3 Add the chunks split into `world` and `plots` and the sub-level count to `server.loaded` with a `loaded` row (D6); verify on the 1.21.1 dev client with one test sub-level that `perf 3s` gives `plots` above 0 and `sub_levels=1`, and without it `plots=0`

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client; verify all pass
