# Design

## Context

The repository is empty apart from OpenSpec scaffolding. The reference for the build is `../ultrasplosive`: a Gradle monorepo using Stonecutter 0.9.x, ModDevGradle for NeoForge, Fabric Loom with `loom-back-compat` (the remap variant up to 1.21.x, plain Loom for the unobfuscated 26.x), Parchment mappings, Gradle 9.8 with a JDK 25 daemon toolchain, one node per `<minecraft>-<loader>` with its own `gradle.properties`, `//? if fabric`-style conditions and string rename rules in `stonecutter.gradle.kts`, and `src/<version>/` folders for code that differs too much between versions. Loader-specific code lives in `platform.neoforge` / `platform.fabric` and is excluded from the other loader's jar. Shared code reaches the loader only through `Platform` / `ClientPlatform`.

See `proposal.md` for motivation and the `specs/` files for the behavior contract.

## Goals / Non-Goals

**Goals:**
- Build layout copied from Ultrasplosive so knowledge and fixes carry over between the projects.
- Keep as much code as possible free of Minecraft imports, so the protocol, parser, formatter and diff engine are unit-testable and identical on every target.
- Make the version-sensitive surface (input injection, recipe placement, screenshot capture, HUD rendering) small and isolated, so adding 26.1 means adding per-version copies of a few files.
- No third-party runtime dependencies in the jar.

**Non-Goals:**
- Server-side control, fake players, world editing as the server. A later change adds these as a server half of the same mod, behind the same command language.
- Pathfinding that breaks or places blocks (parkour, pillaring, tunnelling).
- Remote (non-loopback) access by default. TLS.
- A GUI for configuration. A config file is enough for now.

## Decisions

### D1. Repository layout mirrors Ultrasplosive

```
settings.gradle.kts         stonecutter { create("blockpilot") { target("1.21.1", "neoforge", "fabric"); vcsVersion = "1.21.1-neoforge" } }
build.gradle.kts            plugin versions pinned once (moddev, fabric-loom(-remap), loom-back-compat)
gradle.properties           repo-wide (license, jvm args)
blockpilot/
  stonecutter.gradle.kts    constants (fabric/neoforge), rename rules
  build.fabric.gradle.kts   build.neoforge.gradle.kts
  gradle.properties         mod_id=blockpilot, mod_group_id=dev.blockpilot, mod_version
  versions/1.21.1-{neoforge,fabric}/gradle.properties
  src/main/java/dev/blockpilot/...      shared
  src/main/templates/                   fabric.mod.json, META-INF/neoforge.mods.toml
  src/1.21.1/java/...                   version-specific copies
  src/test/java/...                     JUnit tests for the protocol layer
```

The build scripts are adapted from Ultrasplosive's: Supplementaries handling removed, `environment: client` / `side = "CLIENT"` in the metadata, and a JUnit test task added. Adding 26.1 later means a `target("26.1", ...)` line, two `versions/26.1-*` folders, rename rules, and `src/26.1/` copies of the version-sensitive files (see D9).

*Alternative:* Architectury, or a multi-project common/fabric/neoforge split. Rejected because the user wants the Ultrasplosive approach, and Stonecutter already handles both the version and the loader dimensions with one tree.

### D2. Three layers inside the one tree

- `dev.blockpilot.protocol`: **no `net.minecraft` imports.** Tokenizer, script parser, command registry and help generator, reply/line formatter, diff engine, event ring buffer, MCP JSON-RPC handling, line-protocol framing, config/token handling. Uses only the JDK, Gson and Netty, all of which ship with Minecraft on every target.
- `dev.blockpilot.game`: command implementations against Minecraft (perception, actions, input, session). Version differences are handled with Stonecutter conditions or `src/<version>/` copies.
- `dev.blockpilot.platform`: the `ClientPlatform` seam (config dir, key mapping registration, client tick hook, HUD render hook, mod-loaded checks) with `neoforge` / `fabric` implementations.

A JUnit test asserts that nothing in `protocol` imports `net.minecraft`. This keeps the layer portable, and the server half can reuse it later.

*Alternative:* a separate plain-Java `blockpilot-protocol` Gradle project included into each jar. Rejected for now because it means jar-in-jar or shading on both loaders. An import rule plus a package boundary gets the same separation more cheaply.

### D3. One port, protocol sniffing, Netty

A Netty `ServerBootstrap` on a single NIO event-loop thread, separate from the game's network threads. The first handler reads the opening bytes. If they start with an HTTP method (`GET `, `POST `, `DELETE `, `OPTIONS `) it installs an HTTP pipeline, otherwise a line pipeline (`LineBasedFrameDecoder`, UTF-8). HTTP uses `netty-codec-http` (`HttpServerCodec` + `HttpObjectAggregator`) if it is on the runtime classpath of every target. Task 3.1 checks this. If it isn't, a minimal HTTP/1.1 decoder (request line, headers, `Content-Length` body, keep-alive; no chunked request bodies) is written in `protocol` instead.

*Alternatives:* the JDK `HttpServer` (can't share a port with the line protocol, and has its own thread pool). The MCP Java SDK (pulls in Jackson and Reactor, several MB, version conflicts across loaders, built around servlet/WebFlux transports). Both rejected.

### D4. MCP: hand-rolled, stateless-ish, JSON responses only

Supported: `initialize` (negotiating protocol versions `2025-11-25`, `2025-06-18`, `2025-03-26`), `notifications/initialized`, `ping`, `tools/list`, `tools/call`. POST requests get `application/json` responses (no SSE). `GET /mcp` returns `405`, which the spec allows for servers that don't offer a server-initiated stream. `DELETE /mcp` ends a session. `Mcp-Session-Id` is a random UUID mapped to a `Session` object. Requests with an `Origin` header that isn't `localhost`/`127.0.0.1` are rejected (`403`), as the MCP spec requires against DNS rebinding.

Tools: `run {script: string}` → text, `screenshot {width?: int}` → image, `help {topic?: string}` → text. The `run` description is a short cheat sheet (`goto`, `mine`, `place`, `use`, `look`, `find`, `inv`, `screen`, `mouse`/`drag`/`key`, `craft`, `stop`, `help`). Everything else is fetched via `help` on demand. This keeps the per-request schema cost to a few hundred tokens instead of thousands for a tool per command.

Events are not pushed over MCP (no SSE). They are appended to the next `run` result (`!` lines), which suits the request/response rhythm of agent loops.

### D5. The command language

The grammar is defined in the `command-language` spec. Implementation notes:

- A **command registry** holds each command's name, argument grammar, kind (`query` or `action`), help text and example, and a per-target `supported` predicate. The parser, `help`, the `run` cheat sheet and unsupported-command errors all come from this one registry.
- **Script scheduling:** a script made only of queries runs immediately as one client-thread task. A script with any action goes into the shared FIFO **action queue** as a unit. Queries inside it run in order, at their position in the script, so `select; mine; status` sees consistent state.
- **Diffs:** `look`, `inv` and `screen` produce *keyed lines* (key = entity ref / block type / slot / widget index). The session keeps the last keyed snapshot per command, and the diff engine emits `+`, `-` and `~` lines. This is pure `protocol` code and unit-tested.
- **Reply budget:** every list output has a hard cap (e.g. `look` lists at most 24 entities and 24 block groups, then prints `+N more`), so a crowded area can't blow up an agent's context.

Example round trip (line protocol):

```
> #7 select iron_pickaxe; mine #iron_ores x3; inv
< #7 ok
  ok mine 3/3 iron_ore 14.1s
  ok
  ~1:iron_pickaxe dur231/250
  +5:raw_iron x3
  ! hurt 2 zombie e1204 hp18
.
```

### D6. Execution model and threading

- The network thread parses the script (cheap, no game access) and then either `Minecraft.execute(...)` a query batch or enqueues an action batch. The reply is completed through a `CompletableFuture` that writes back on the Netty channel.
- An action is a tick-driven state machine: `start(ctx)`, `tick(ctx) → RUNNING | DONE(result) | FAILED(code)`, `cancel()`. The `ActionRunner` advances the head of the queue on each client tick (end-of-tick hook via `ClientPlatform`) and enforces timeouts.
- Session close cancels that session's queued actions, and releases held inputs if it owned the running one.

### D7. Two input layers: key mappings for high-level actions, handler injection for raw input

- **High-level actions** (`goto`, `move`, `mine`, `attack`, `use`) drive `KeyMapping` down-state (`keyUp`, `keyAttack`, `keyUse`, `keyJump`, `keyShift`, `keySprint`, …) and set player rotation directly (smoothed over ticks). This is what vanilla's `KeyboardInput` and `continueAttack` read, so movement and digging behave exactly as for a human, including server-visible timing.
- **Raw input** (`mouse`, `drag`, `cursor`, `click`, `scroll`, `key`, `type`) calls the game's own GLFW callback handlers (`MouseHandler.onPress`/`onMove`/`onScroll`, `KeyboardHandler.keyPress`/`charTyped`) through invoker mixins, with the real window handle. This is the layer that makes modded interactions work without the mod knowing about them. For example, a Create Simulated physics assembler drag is right-button down → per-tick `onMove` deltas → right-button up, and whatever the mod hooks (loader input events, mixins into `MouseHandler`, polling `KeyMapping`/`MouseHandler` state) sees real-looking input. The mod keeps a **virtual cursor** (absolute position for `onMove`). In-world, moves turn the camera through vanilla sensitivity handling. On a screen, the virtual cursor is what `screen` reports and what pointer commands target.
- **Telling injected from human input:** a thread-local `injecting` flag is set around each injected call. The same mixins see every callback, so a callback arriving without the flag is human input and triggers human override (cancel, release, `err interrupted`).
- **Unfocused window:** while at least one agent session is connected, mixins make `Minecraft.isWindowActive()` effectively true for input and pause purposes, stop `pauseOnLostFocus` from opening the pause menu, and treat the mouse as grabbed in-world *without* calling GLFW's cursor capture while the window is unfocused, so the user's OS cursor is never stolen. All of this is undone as soon as the last session disconnects.
- **Held-input ledger:** every press goes through an `InputState` that records what is held (raw buttons, raw keys, key mappings). `release` / `stop` / error / override / session close / world leave all run `InputState.releaseAll()`, which sends matching release events.

*Alternative:* only key mappings (no raw layer). Rejected because modded drag interactions and custom screens read raw mouse state and deltas, which key mappings can't express. *Alternative:* OS-level input (java.awt.Robot / platform APIs). Rejected because it needs focus, steals the user's mouse, and is platform-specific.

### D8. Perception implementation

- `look`: walk chunk sections within the radius. Skip a section if its palette has nothing on the notable list and it holds no block entities. Iterate block entities from the chunk's block-entity map. This keeps a radius-16 scan well under 1 ms. The notable list is matched by id and id pattern (`*_ore`, `*_log`, …) plus client-known vanilla tags, because server-synced tags won't contain anything Blockpilot defines when the server is vanilla.
- `find`: the same section-palette pruning, nearest-first by expanding shells. Entity search via `ClientLevel.getEntities` over an AABB.
- `blocks`: palette symbols `a`–`z`, `A`–`Z`, then `#n`. Short block-state properties only where they differ from defaults (`oak_stairs[facing=east]`).
- `screen`: walk `Screen.children()` recursively. Recognize `AbstractWidget` (bounds, message, active), `EditBox` (value), `AbstractSliderButton` (value), `Checkbox`/`CycleButton` (state), and `AbstractContainerScreen` slots (`leftPos + slot.x`). Unknown `GuiEventListener`s that have bounds are listed as `widget` with their class's simple name. Custom-rendered regions with no widgets are left to `screenshot` plus pointer commands.
- `screenshot`: capture the main render target on the render thread at the next frame end, downscale with `NativeImage` resize, encode PNG with the game's own (STB) encoder. No AWT, which avoids headless/AppKit trouble on macOS. Image token cost depends on pixel count, not bytes, so PNG costs nothing extra over JPEG.
- Events are detected mostly by polling in the tick hook (health delta + `getLastDamageSource`, inventory delta → `! got`/`! lost`, screen change, death screen). Chat comes from a platform hook (NeoForge `ClientChatReceivedEvent`, Fabric `ClientReceiveMessageEvents`). Polling keeps the platform seam narrow.

### D9. Version-sensitive surface (for 26.1 later)

These files are expected to need `src/<version>/` copies or `//? if` blocks:
- Input invokers: 1.21.9+ replaced the raw `(window, key, scancode, action, mods)` callbacks with `KeyEvent`/`MouseButtonInfo` records.
- Crafting: 1.21.2+ moved the recipe book to server-provided `RecipeDisplayId`s. 1.21.1 uses `RecipeHolder` with `handlePlaceRecipe`.
- Screenshot / render-thread capture: the 1.21.5+ GPU device abstraction.
- HUD indicator rendering: the `GuiGraphics` → `GuiGraphicsExtractor` rename (rename rule, as in Ultrasplosive).
- `ResourceLocation` → `Identifier` (rename rule).

Everything in `protocol`, plus pathfinding, diffing and command parsing, stays a single copy.

### D10. Pathfinding

Blockpilot has its own A* on block positions over loaded chunks. Moves: walk, diagonal, jump up 1, drop ≤ 3, swim, ladder/vine climb. Costs penalize water, lava adjacency and fall height. Searches are capped by a node budget per tick (spread over ticks, so the frame rate doesn't hitch), with a total cap. If nothing is found, the result is `err no_path`. Path following steers by look-ahead (yaw toward the next node), jumps when the next node is higher, re-plans when the player strays more than 2 blocks, and reports `stuck` after 3 s without progress.

*Alternative:* depend on Baritone. Rejected: it is not available for every target/loader we need, it is heavy, and its control model fights an external agent. A soft integration (delegate `goto` to Baritone if installed) can come later.

### D11. Security and config

`config/blockpilot/config.json` holds the port, bind address, default timeouts, the notable-block list and caps. `config/blockpilot/token` holds a random 32-byte token, base64url, created with owner-only permissions where the OS supports it. The token is printed to the log on first start, and `/blockpilot token` (client command) copies it to the clipboard. Line-protocol and HTTP auth compare tokens in constant time.

## Risks / Trade-offs

- [Modded handlers that read GLFW state directly (`glfwGetMouseButton`, `InputConstants.isKeyDown`) rather than callbacks or `KeyMapping`s won't see injected input.] → Mixin `InputConstants.isKeyDown` and the mouse-button polling helper so they consult `InputState` first. Verify with a Create Simulated drag during manual testing.
- [Faking focus and grab state can confuse mods or vanilla when the user returns.] → All overrides only apply while a session is connected, and are reverted on disconnect. Real focus regained plus human input triggers override anyway.
- [Servers may forbid automated play.] → Blockpilot does not try to hide automation: the HUD indicator makes agent control visible, and the README states that each server's rules on automation apply. Rotation is smoothed over ticks only because modded drag handlers and vanilla aiming expect gradual motion.
- [`netty-codec-http` may not be on every target's runtime classpath.] → Verified first (task 3.1). The fallback is the minimal in-house HTTP/1.1 decoder.
- [Custom-rendered modded GUIs expose no widgets.] → `screenshot` plus coordinate pointer commands still work. `screen` reports cursor and size so the agent can map coordinates.
- [Unfocused windows render slowly (inactivity FPS limit), which slows screenshots and per-frame mouse processing.] → Ticks keep running at 20 TPS. Raw mouse movement is fed per tick, and screenshots wait for the next rendered frame with a timeout.
- [Line protocol is plaintext on loopback.] → Loopback-only by default, token required, Origin checks on HTTP. Opening it to other machines is an explicit, documented choice.

## Migration Plan

Greenfield, so nothing to migrate. Release as `0.1.0` alpha jars for the two 1.21.1 targets. Rollback means removing the jar.

## Open Questions

- License: assumed *All Rights Reserved* like Ultrasplosive until the user decides. It only touches the repo-wide `mod_license` property.
- Default port `25599` may clash with something on some machines. It is configurable, so the default can change later without affecting the design.
- Whether to also expose an MCP `resources` view of the command reference. `help` covers it for now.
