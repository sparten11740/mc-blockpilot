# Proposal

## Why

AI agents can't play or operate Minecraft well today. Screen-and-mouse computer use is slow and costs a lot of tokens, and the existing bot frameworks (Mineflayer and similar) are separate protocol clients. They don't run inside the modded game the user is actually playing. Blockpilot is a mod that gives agents a direct, fast, token-cheap interface to the running game. It is built from one source tree for several Minecraft versions and mod loaders, the same way Ultrasplosive is built with Stonecutter, so the interface stays the same wherever it runs.

## What Changes

- New Gradle project `blockpilot`, laid out like `../ultrasplosive`: one Stonecutter source tree with one node per `<minecraft>-<loader>`, loader-specific code behind a `Platform`/`ClientPlatform` seam, and per-version source folders for code that differs too much for inline `//? if` blocks. First targets: **1.21.1 NeoForge** and **1.21.1 Fabric**. The layout must allow adding **26.1 NeoForge/Fabric** later just by adding nodes.
- A local endpoint in the game client, on one port (localhost only, token-protected), that speaks two protocols:
  - **MCP over Streamable HTTP** (`/mcp`) with a deliberately tiny tool set (`run`, `screenshot`, `help`), so tool schemas cost almost nothing in every request.
  - A **raw line protocol** over TCP for scripts, `nc`, and custom harnesses.
- A **compact command language** shared by both protocols. Commands are short, chainable into one script per round trip, and return terse text replies (no namespaces, no default fields, relative positions, run-length-encoded block dumps, diffs on repeated observations). Pending game events (chat, damage, death, …) are added to the next reply.
- **Perception**: player status, surroundings (entities, notable blocks, crosshair target), targeted searches, region block dumps, inventory, open screen/container, and an optional downscaled screenshot for when vision is needed.
- **Player control**: in-mod pathfinding (`goto`), movement, looking, mining, placing, using/attacking, hotbar/inventory management, crafting, container transfer, chat/commands, waiting. Long actions run in the mod at tick rate, not across agent turns. A human player keypress interrupts the agent, and a HUD indicator shows when an agent is connected or acting.
- **Session control** from the title screen: list worlds and servers, join, and leave, so an agent can get into a game unaided.
- Binary wire formats (protobuf etc.) are **explicitly not used**. The model reads text, so token cost depends on the text format, not the wire encoding. Localhost serialization cost is negligible next to model latency.

Out of scope for this change (planned follow-ups): server-side control (world queries/edits as the server, command execution, fake/bot players), the 26.1 nodes, and multi-agent coordination.

## Capabilities

### New Capabilities
- `multi-target-build`: Building one source tree into a jar per Minecraft version and loader (Stonecutter nodes, platform seam, per-version sources, metadata generation), and how new targets are added.
- `agent-endpoint`: The local network endpoint: port and binding, authentication, protocol detection, MCP Streamable HTTP (tools, sessions), the line protocol framing, concurrency, and threading guarantees.
- `command-language`: The shared command/script syntax, position/item/entity tokens, reply and error format, event delivery, diff semantics, and the in-band `help` reference.
- `game-perception`: Read-only observation commands: status, look, find, blocks, inventory, screen, and screenshot.
- `player-control`: Commands that act as the local player: movement, pathfinding, interaction, inventory, crafting, containers, chat, waiting/cancelling, and human-override behavior.
- `game-session`: Getting the client into and out of a game: listing singleplayer worlds and servers, joining, leaving, and reporting connection state.

### Modified Capabilities
<!-- none: the project has no existing specs -->

## Impact

- **New code**: a whole new repository layout (`settings.gradle.kts`, root `build.gradle.kts`, `blockpilot/` Stonecutter tree, Gradle wrapper 9.x with a JDK 25 daemon toolchain, Java 21 for 1.21.1 targets).
- **Dependencies**: Stonecutter, ModDevGradle, Fabric Loom (remap variant for 1.21.x, plain for 26.x via loom-back-compat), Fabric API, Parchment. No new runtime libraries: Netty and Gson come with Minecraft. The MCP SDK is deliberately not bundled.
- **Runtime surface**: opens a TCP port on `127.0.0.1` while the client runs. A per-install token in the config dir guards it.
- **Users/agents**: MCP clients connect with `--transport http http://127.0.0.1:<port>/mcp` and the token header. Scripts connect with a plain TCP socket.
- **Multiplayer note**: the agent acts through the normal client input path, so servers see ordinary player actions. Server rules on automation still apply, and the HUD indicator makes agent control visible to the user.
