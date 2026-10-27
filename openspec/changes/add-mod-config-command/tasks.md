# Tasks

## 1. Loader view and values

- [x] 1.1 Add the `ModConfigs` view to `ClientPlatform.Services`, with the NeoForge implementation (walks the registered `ModConfigSpec` files; `set` changes the value in memory and posts `ModConfigEvent.Reloading` to the owning mod) and the Fabric one reporting no config system (D1, D2, D3); verify that all four targets compile
- [ ] 1.2 Parse a value's text by its default's type (booleans, whole and decimal numbers, enum names, text, comma-separated lists) and describe what a key takes, in the game layer with unit tests (D4); verify by the tests

## 2. The config command (mod-config)

- [ ] 2.1 Add `config`, `config <mod> [<type>] [match=]`, `config <mod> [<type>] <key>` and `config <mod> [<type>] <key> <value>`, with the restart rules, server-config rules, key lookup, closest-name errors, `err needs_restart` and `err unsupported` in `help errors`, `help config` and a README row (D5, D6, D8); verify on the dev client that `config` lists neoforge and create, `config create client` lists its values, `config neoforge client showLoadWarnings` gives its value, default, takes and comment, `config neoforge client experimentalForgeLightPipelineEnabled true` answers with `was false` while the file is unchanged and a `settle` after it shows the chunks rebuilt by NeoForge's reload handler, an out-of-range number, an unknown enum name, an unknown mod and an unknown key fail with `err syntax` naming what fits, a server world-restart value (`config neoforge server removeErroringBlockEntities true`) fails with `err needs_restart`, a server value without a restart type can be set in single-player, and `config neoforge server …` outside a world fails with `err not_in_world`
- [ ] 2.2 Record the originals of agent changes, put them back and tell the mods on `config reset` and wherever options are restored, and forget server-config originals when the integrated server stops (D7); verify on the dev client that `config reset` and `leave` each put a changed client value back (`config` then shows no `was`), that a changed server value causes no error when the world is left, and that the client closes cleanly with a changed value

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client, and check `config` on the Fabric dev client fails with `err unsupported` if it starts; verify all pass
