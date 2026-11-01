# Proposal

## Why

The README has grown to 415 lines and 41 KB. Most of it is reference detail
packed into table cells a screen wide, so a person landing on the project can't
tell quickly what Blockpilot does, how to connect to it, or which commands
exist. The project pages on CurseForge and Modrinth also need gallery images
for the 1.0 release, and the README has no picture of the mod at work.

## What Changes

- The README becomes about one page for human readers:
  - what Blockpilot is and how to install it;
  - the two ways to drive it, MCP for AI agents and commands typed directly
    through the launcher;
  - a short example;
  - a table with one row per command, saying in a few words what it does;
  - one or two screenshots;
  - links to the detailed documentation.
- Everything detailed moves, unchanged in substance, to `docs/`: connecting,
  the command reference, the command language, recording videos, Sable, using
  Blockpilot in another mod's dev environment, extending it, and building and
  releasing.
- The extension examples, and the help examples that name one mod's test
  world, use a generic `mymod` instead.
- Gallery media for CurseForge and Modrinth: numbered screenshots and short
  animated GIFs in `docs/images/` (the folder `modrinth/gallery` syncs), and
  MP4 videos with sound for a project description.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `dev-integration`: the documentation, rather than the README, covers adding
  Blockpilot to another mod's dev environment.

## Impact

- `README.md`, new `docs/*.md` and `docs/images/`.
- `ReadmeSnippets` becomes `GuideSnippets`, copying the examples from
  `docs/extending.md`; `ExtensionApiTest` follows the new namespace.
- Help examples in `TestWorlds`, `Snapshots` and `ObservationCommands`.
- No behaviour changes.
