# Tasks

## 1. Spike

- [x] 1.1 Prove an agent can boot Mixin on vanilla 26.1.2 (D2, D7): a `26.1-vanilla` node on ModDevGradle's vanilla mode, an agent with its own Mixin service applying one mixin that logs each frame; verify the dev vanilla client logs it; if it can't be made to work, stop and re-plan

## 2. The vanilla target

- [x] 2.1 Make the node build the whole mod (D5): exclude the loader packages, teach Stonecutter's loader constant `vanilla`, give loader conditions that use Fabric API a vanilla branch, bundle Mixin, MixinExtras, ASM and access-widener, and write the agent manifest; verify all five targets compile and the vanilla jar holds the agent, the libraries and no loader metadata
- [x] 2.2 Add the agent's version check (vanilla-agent) and the access widener (D4); verify a 1.21.1 vanilla client started with the agent logs why and starts as without it, and that the 26.1.2 client applies the widener

## 3. The platform layer

- [x] 3.1 Implement `Services` and the hooks through mixins (D3), with the key mapping and translations; verify on the vanilla dev client that the endpoint opens, the launcher is written, the F8 key shows under its translated name, and `status`, `look`, `goto`, `screenshot`, `record 3s` and `server` work, while `config` answers `err unsupported`

## 4. Docs and release

- [ ] 4.1 Describe installing the vanilla jar through the launcher's JVM arguments in the README and `docs/connecting.md`, and attach the jar in the release workflow (D6); verify the instructions on a vanilla launcher profile, and that the workflow collects five mod jars
- [ ] 4.2 Run the smoke test against the vanilla dev client; verify it passes in text and JSON mode

## 5. Integration checks

- [ ] 5.1 Run `./gradlew build` for all targets and the smoke test against the 1.21.1 and 26.1 loader clients; verify all pass
