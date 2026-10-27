# Proposal

## Why

Play-tests often need a mod's own setting flipped mid-run, say a client switch
that turns a light effect off. Today an agent edits the mod's config file by
hand and relies on NeoForge reloading it, with nothing to say when the mod has
taken the new value, and the change stays in the developer's file afterwards.
`option` already does this for the game's video options: it sets, answers once
the change has taken, and puts back what agents changed.

## What Changes

- A new `config` command reads and sets the values of mods' config files as
  NeoForge keeps them (client, common, server and startup configs):
  - `config` lists the mods with config files.
  - `config <mod> [<type>] [match=<pattern>]` lists a mod's values by dotted
    key.
  - `config <mod> [<type>] <key>` reports a value, its default, what it takes,
    whether a change needs a restart, and the mod's comment on it.
  - `config <mod> [<type>] <key> <value>` sets it in the running game, tells
    the mod as a reload of its file does, and answers after that. The file is
    never written.
  - `config reset` puts back what agents changed, as leave, quitting, the
    agent-control key and shutdown also do, as for options.
- Values that only take effect after a game restart, or after a world load
  that reads a server config's file again, are refused with the new
  `err needs_restart`, naming the file to edit.
- Server configs are set only with the integrated server (`err no_server` on
  a remote one); their changes last until the world closes.
- On Fabric, which has no shared config system, `config` fails with the new
  `err unsupported`.

## Capabilities

### New Capabilities
- `mod-config`: reading, setting and restoring mods' config values from
  scripts.

### Modified Capabilities

None.

## Impact

- New `game/ConfigCommands.java` with loader-neutral value parsing and the
  record of agent changes, a new `platform/ModConfigs` view that
  `ClientPlatform.Services` hands out, its NeoForge implementation in
  `platform/neoforge`, and an empty one in `platform/fabric`.
- `game/BlockpilotClient.java` restores config changes wherever it restores
  options; `protocol/Errors.java` gains `needs_restart` and `unsupported`.
- README: a row for `config`.
