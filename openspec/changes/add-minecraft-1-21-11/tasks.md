## 1. 1.21.11 nodes

- [ ] 1.1 Declare `1.21.11-fabric` and `1.21.11-neoforge` (Java 21, Fabric API 0.141, NeoForge 21.11, Parchment 2025.12.20, `version_sources=26.1`); give the access widener the node's namespace header
- [ ] 1.2 Recheck every `//? if >=26.1` condition and 26.1 rename rule against 1.21.11; both nodes compile
- [ ] 1.3 Check every mixin target against 1.21.11; both clients load to a world without a missing target

## 2. Tests

- [ ] 2.1 Full test script passes on both 1.21.11 nodes with their loader's JSON shapes; the smoke test learns any 1.21.11 data differences
- [ ] 2.2 All fifteen targets compile; 1.21.1-neoforge and 26.3-fabric pass the test script

## 3. Release and docs

- [ ] 3.1 `release.yml` builds fifteen jars and uploads the 1.21.11 rows
- [ ] 3.2 README, `docs/building.md`, `docs/dev-environment.md` and the changelog cover 1.21.11
