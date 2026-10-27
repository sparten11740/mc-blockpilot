# Design

## Context

- NeoForge keeps every registered mod config in FML's `ModConfigs`: one
  `ModConfig` per file, with its mod id, type (`CLIENT`, `COMMON`, `SERVER`,
  `STARTUP`), file name and spec. Nearly all mods use `ModConfigSpec`, whose
  `getValues()` holds a `ConfigValue` per key, nested by section. The API is
  the same in NeoForge 21.1 (FML 4) and 26.1 (FML 11).
- `ConfigValue.set` changes the loaded config in memory without writing the
  file or firing an event. It updates the value `get()` returns only for a
  value without a restart type; NeoForge clears the cache of world-restart
  values when the client logs out and when the server stops, and that of
  game-restart values only at start.
- When FML re-reads an edited file, its file watcher posts
  `ModConfigEvent.Reloading` to the owning mod's event bus, from the watcher's
  own thread. Mods that copy values into fields do it there.
- Server configs are per world: the server loads them from the world's
  `serverconfig` folder at start and unloads them at stop. In single-player
  the client shares those `ModConfig` objects. On a remote server, the client
  holds a copy the server sent.
- Fabric has no shared config system.
- `ClientOptions` keeps the originals of the options agents changed, and
  `BlockpilotClient.restoreOptions` puts them back on `leave`, `quit`, a
  world exit Blockpilot didn't start, the agent-control key and shutdown.
  Blockpilot's own world switches keep them.

## Goals / Non-Goals

**Goals:**
- An agent's change never outlives the test: not written, and put back.
- The game layer stays loader-neutral; NeoForge types stay in
  `platform/neoforge`.

**Non-Goals:**
- Mod config systems other than NeoForge's (Cloth Config, own JSON files,
  Forge Config API Port on Fabric).
- Values that can only take effect through a restart (D5).
- Setting values for another player on a remote server.

## Decisions

### D1. A loader-neutral view in the platform layer

`ClientPlatform.Services` hands out a `ModConfigs` view: whether the loader
has a config system, and its files, each with mod id, type, file name,
whether it is loaded, and its entries. An entry has the dotted key, the
current and default value, the range, the comment, the restart type, a test
of a candidate value, and `set`, which also tells the mod. The NeoForge
implementation walks `ModConfigs.getFileMap()` and each `ModConfigSpec`'s
values; the Fabric one reports no config system. The command, the parsing of
values from text and the record of agent changes live in the game layer.

### D2. In memory only

`set` calls `ConfigValue.set` and never `save`. A crash or a killed client
then leaves the developer's file as it was, and FML's file watcher doesn't
fire on Blockpilot's own change. A later edit of the file by hand still
reloads it and wins.

Alternative: `set` and `save`, as NeoForge's config screen does. That writes
the file, so a crash would keep the agent's value, and the watcher would
post a second reload.

### D3. Telling the mod

After the set, the implementation posts `ModConfigEvent.Reloading` for the
`ModConfig` through the owning `ModContainer`'s `acceptEvent`, on the client
thread. Its handlers have run by the time the reply is built, which is what
"answers when the value has taken" needs. FML itself posts the event from its
watcher thread, so mods already accept it off their own threads.

### D4. Values from text

The value's type is that of the default: `Boolean` takes `true|false` or
`on|off`; `Integer`, `Long` and `Double` take numbers; an enum takes a
constant's name, in any case; `String` takes the text as written, so the
value slot is free text; a `List` takes items separated by commas, each read
as the default's items are, or as text. The parsed value must pass the
spec's own test, else `err syntax` names what the key takes: the range, the
enum names the test accepts, or the type.

### D5. Restart types

A value without a restart type takes effect at once. A world-restart value in
a client or common config is set, and the reply says `restart=world`: the
in-memory value survives the logout that clears the cache, and Blockpilot's
own world switches keep agent changes, so a `join` applies it. A
game-restart value, or a world-restart value in a server config (whose world
load reads the file again), fails with `err needs_restart`, naming the file.

Alternative: set them anyway and report the restart. The change could never
take effect, which is the trap this command removes.

### D6. Server configs

Setting a server config needs the integrated server, whose configs the client
shares. On a remote server the client's copy is only what the server sent,
so setting it fails with `err no_server`; reading it is allowed. An unloaded
server config (outside a world) fails with `err not_in_world` when named, and
a listing without a type leaves it out. When the
integrated server stops, Blockpilot forgets the originals of server values,
since the next world load reads that world's file anyway.

### D7. Putting back

The first agent change of an entry records its original. `config reset` and
`BlockpilotClient.restoreOptions` set every recorded entry back and tell its
mod, then forget them. A mod's reload handler that throws while values are put
back is logged and doesn't stop the others or the shutdown.

### D8. Finding a key

Keys are the dotted paths of the file (`section.key`). The type may be left
out when only one of the mod's files has the key. When several do, the error
names their types and files. An unknown mod or key fails with `err syntax`
naming the closest ones, as `key` does for key names.

## Risks / Trade-offs

- [A mod caches a value outside both `get()` and its reload handler] → it
  won't see the change until it reads again; the reply can't tell. The
  comment and `restart=` show what the mod declared.
- [A mod's config screen saves while an agent's value is set] → the value is
  written to the file. Agents and players rarely share the screen; `config
  reset` still puts the in-memory value back.
- [Thread safety of server configs in single-player] → the client thread sets
  a value the server thread reads. The fields are single references, as with
  FML's own watcher thread.
