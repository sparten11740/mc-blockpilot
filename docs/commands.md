# Commands

Every command by group, with the details its row in the README leaves out. In the game, `help <command>` gives its
forms, options and reply fields, `help <group>` a group's commands, and `help errors` the error codes. The syntax of
scripts, positions and predicates is in [command-language.md](command-language.md).

## perception

- `status`: one line with position, health, the crosshair target with the hit point `hit=u,v,w` in the block,
  velocity, vehicle, camera angles and more.
- `look [r]`: the blocks and entities around the player.
- `entity e<id>|me [side=server]`: one entity's `e<id>`, position, yaw, pitch, velocity and age per tick; for a living
  entity its walk animation as `walk=<limb speed>` and `walk_pos=<swing position>`. `me` is the player. On the client
  it adds the updates it applied since its last tick, `net move1 data1`; `side=server` reads the integrated server's
  copy, so `sample entity e5 and entity e5 side=server every 1t x20` shows the client's drift.
- `find <block|#tag|*pattern|entity> [r [limit]]`: the nearest matches in a cube, r up to 128, limit up to 1000, with
  `count=` when it left some out. A block, tag or pattern may carry states as Minecraft writes them, `oak_log[axis=y]`,
  and then matches only the blocks holding them. `~` is from the player's block as the find runs, and JSON's `at_abs`
  gives each row absolute. Blocks aboard a sub-level: `sable:find`.
- `block <pos> [light]`: every state property with its value, defaults too; `light` adds the light the level stores for
  the cell as `sky=` and `block_light=`, each 0 to 15.
- `blocks <from> <to> [grid]`: a region's blocks as run lengths; `grid` gives one palette symbol per cell instead.
- `inv [detail]`: the inventory.

Repeating `look`, `inv` or `screen` in a session reports only what changed (`full` forces everything).

## observe

- `screenshot [width] [burst=n every=Nt [sheet]] [crop=x0,y0,x1,y1] [hud=off]`: saves the last rendered frame as PNG and
  answers its path with `t<tick>`, the tick it was captured on. Width is up to 1280 and the frame's or crop's own, with
  `width=` when narrower than asked; `sheet` saves a burst as one PNG in a labelled grid. A frame rendered from a placed
  camera carries `camera <x,y,z>`.
- `camera at <pos> [look <pos|e<id>>] [clear]` / `camera reset|off`: renders from a placed camera. `unloaded` when the
  client hasn't loaded the camera's chunk. `clear` moves a camera that sits in a block with a collision shape toward
  the look point, a quarter block at a time and at most 8 blocks, to the first clear point, answering
  `cleared=<blocks>`, or `uncleared` when there is none. For an effect spanning hundreds of blocks, `option fov 110`
  and `option renderClouds off` widen and clear the view.
- `settle [timeout=]`: waits until the chunks around the player have arrived and those in view have compiled, e.g.
  after `camera at`, a teleport or a render distance change.
- `sounds [since=] [match=] [limit=]`: the sounds that started, the last 2000. `vol` and `pitch` are what plays, and
  `asked:<vol>/<pitch>` the values it was played with where its sounds.json entry scales them. A `match=` without `*`
  is a sound event id, `minecraft:` without a namespace, and an unknown one fails naming the closest. `~` is from where
  the player stands now, and JSON's `at_abs` is where the sound started.
- `sounds playing [since=] [match=] [limit=]`: what plays now, with `loop` and `played=<s>`. A sound whose category or
  the master volume is 0 never plays.
- `messages [since=] [kind=] [match=]`: chat, action bar, titles and subtitles; `match=*engaged*` matches the whole
  text in any case.
- `particles [since=]`: particles spawned per type over a window.
- `option [<name> [<value>]]`, `option reset`: client options (see below).
- `pixels [[name=]x0,y0,x1,y1|[name=]@<pos>]… [r=]`: mean rgb and luma, max and p90 luma of parts of the last frame,
  with `camera <x,y,z>` when a placed camera rendered it.
- `record start [name] [width=] [fps=] [hud=off] [audio=off]`, `record stop`, `record status`,
  `record <duration> [name] …`: a video of the game with its sound; see [recording.md](recording.md).
- `log [level=] [since=] [grep=] [logger=] [limit=]`, `log mark`, `log detail <seq>`: the game log.

`sounds` and `messages` add `from=<tick>`, where their log begins, when it dropped some of the asked window. `sounds`,
`messages` and `particles` name the ticks they cover as `window=t<first>..t<now>`; a bare `since=` tick from before
the world was joined adds `joined=t<tick>`, since `100t`, not `100`, counts back.

Video and sound options an agent changes with `option` (graphics, render distance, particles, `hud`, sound volumes, …)
apply at once, are never written to `options.txt`, and are put back when the player or the agent leaves the world, when
the player turns agent control off (F8) and when the game closes; Blockpilot's own world switches (`join`,
`create world`, snapshots) keep them. The log names what was put back. Names are those of the running version's
`options.txt`: from 26.1, where Fast, Fancy and Fabulous became presets, `graphicsPreset`, `cutoutLeaves` and
`improvedTransparency` take the place of `graphicsMode`. A preset sets a dozen other options; `option reset` puts
those back too. Each sound category's volume, from 0 to 1, is `soundCategory_<category>` (`soundCategory_master`,
`soundCategory_music`, `soundCategory_block`, …; `soundCategory_ui` from 26.1).

## movement

- `face <pos|e<id>> [at <u,v,w>]`: aims at a position or entity. When the crosshair ends on nothing, `dist=` to the
  target and the player's `reach=` tell out of reach from empty air.
- `turn <yaw> <pitch> [Nt]`: sets the camera angles, over ticks if given.
- `move <dir> <blocks|Nt>`: walks a direction by blocks or ticks; by blocks, `err stuck` after 20 ticks without
  progress.
- `jump`.
- `sneak [on|off]`: alone, a quick tap, as to leave a seat or vehicle.
- `sprint on|off`. `sneak on` and `sprint on` last as held keys do; see input.
- `fly on|off`.
- `goto <pos|e<id>> [near=]`: walks there along a path. To a position without `near=`, `err no_path` comes at once
  with the reason when the player can't stand there, e.g. `stone fills ~2,-1,0; stand at ~2,0,0`.

## interact

- `use [target [at <u,v,w>]] [any] [sneak] [hold=Nt]`: clicks a block or entity only once the crosshair rests on it,
  else `err obstructed <what>` or `err out_of_reach`. `any` clicks whatever the crosshair ends on, adding
  `off_target=<target>` when that was something else. `sneak` with an item in either hand uses the item instead of the
  block, and the reply names it as `sneak_item`. `at` is a point inside the block, each 0 to 1 in the frame of its
  model facing north, turned with the block (seen from the model's north side, usually the block's front, u runs right
  to left), as `status` shows it in `hit=`. The server gets the crosshair hit, where the aimed ray first meets a
  block's shape, which the reply reports as `aim` and `hit=`.
- `attack [e<id>|type] [x<n>]`: hits entities.
- `mine [pos|block|#tag] [x<n>]`: breaks blocks by position or type. A block or tag may carry states:
  `mine oak_log[axis=x] x2`. `err unbreakable <block>` comes at once for a block the player can't break; by type such
  blocks are passed over. `err out_of_reach <pos> is behind <block> <pos>` names what is in the way.
- `place <item> <pos>`: aims at the face of the neighbouring block's shape, so also on a carpet or against a fence
  post. `err no_item` says to give the item first; a taken cell names the cell above when that one is free; when the
  crosshair hits something else or another face, `err out_of_reach` names it and nothing is placed.

## inventory

- `select <slot|item>`: selects hotbar slot 0-8, or puts the item into the selected slot.
- `swap <a> <b>`: swaps two inventory slots (0-35, `head`, `chest`, `legs`, `feet`, `offhand`).
- `drop <item|slot> [n]`.
- `offhand <item>`: moves an item to the offhand.
- `equip <item>`: puts on an armour item.
- `open <pos>`: opens a container.
- `take <item|s<i>> [n]`, `put <item|s<i>> [n]`: move items out of and into the open container.
- `craft <item> [x<n>]`: crafts through the recipe book, in the 2x2 grid or at a table within 4 blocks. From 26.1 a
  client on a remote server knows only unlocked recipes, so there `craft` finds only those.

## gui

- `screen`: what is on screen, with its widgets and slots; lists rows of lists as `w<i>` too, and the creative
  inventory's tabs on its current page as `t<i>:tab "<name>" @x,y,w,h`, the open one `selected`.
- `cursor <x,y|w<i>>`: moves the mouse pointer.
- `click <x,y|w<i>|s<slot>> [button]`: `w<i>` is a widget's centre, as `screen` numbers them.
- `close`: closes the screen.

## input

- `mouse down|up|click <button>`, `mouse move <dx> <dy> [Nt]`.
- `drag <button> <dx> <dy> [Nt]`, `drag <button> <from> <to> [Nt]`.
- `scroll <n>`.
- `key down|up|press <key|key.mapping>`: a key alone is pressed: `key escape`; `+` joins keys into a chord, the
  earlier held while the last is pressed: `key f3+l`.
- `type <text>`: types into the focused text field.
- `release`: lets go of held keys, buttons and movement.

Held keys, buttons and movement last until let go with `up`, `off` or `release`, or until `stop`, a failing step (not
marked `?`) of any script with an action on the same connection, a human taking over, leaving the world, or their
connection closing: over MCP the session, so they last across `run` calls; through the launcher, one connection per
call.

## talk

- `chat <text>`: sends a chat message.
- `cmd <command>`: sends a `/command` as the player; its feedback arrives as `! chat` events (`server` captures
  output instead).

## session

- `worlds`, `servers`: the saved worlds and the server list.
- `join world|server <name>`: `join world tick=frozen <folder>` freezes the world's server before its first tick, so
  it reads as saved until `tick unfreeze` or `tick step`.
- `leave`: after a singleplayer world answers `saved_at=t<tick>`, the game time it was saved at.
- `quit`: leaves, answers `ok pid=<pid>` and `saved_at` as `leave` does, and closes the game.
- `respawn`: respawns from the death screen.
- `create world <name> [flat|void|normal] …`: a new world with cheats on, joined when ready (by default flat,
  peaceful and creative).
- `snapshot save|restore|delete <name>`, `snapshot list`: saves and restores a world's state.
- `delete world <folder>`: test worlds only.
- `config`: the mods with NeoForge config files. `config <mod> [client|common|server|startup] [match=]` lists their
  values by dotted key. `config <mod> [<type>] <key> [<value>]` gives a value's default, what it takes and the mod's
  comment; with a value, sets it in memory, never in the file, and tells the mod as a reload of its file does.
  `err needs_restart` comes for a value only a game restart, or for a server config a world load, would apply; server
  configs need a world, and setting one the integrated server. `config reset` puts back what agents changed, as
  leave, quitting, the agent-control key and shutdown do.

## integrated

- `server [as=player] [settle=Nt] <command>`: runs a server command and answers with its result value and feedback
  lines. It runs at the player's position and dimension, so `~` and `@p` count from the player, and with `as=player` as
  the player. A failing command, a failing `execute if` test among them, is `err command_failed` with its `result`,
  which `?server …` lets the script carry on past. A `;` inside the command, as in an NBT int array, is written `\;` so
  it doesn't end the step: `server data modify storage test:x ids set value [I\;1,2]`.
- `nbt <pos|e<id>|uuid> [path=]`: the server-side data of a block entity or entity, SNBT in text and JSON in json.
- `tick freeze|unfreeze|step <n>|rate <tps>|query`.
- `block`, `blocks` and `find … side=server` read the integrated server's copy.

The `integrated` commands, `snapshot` and `side=server` reads need a singleplayer world with commands allowed (cheats
on, or opened to LAN with cheats); `create world` makes one. That is deliberate: an agent playing a survival world
can't use them to cheat. While the game is paused (a pause screen in singleplayer), the server runs no ticks, so
commands that need them fail at once with `err paused` instead of holding up the queue. `snapshot restore` leaves the
world, copies the save back and rejoins, the same as quitting to the title screen and loading it again. Mods that keep
static state across worlds may therefore behave differently than after a fresh launch; relaunch the client when a test
needs full isolation.

## performance

- `perf start [name] [profile]`, `perf stop`: a performance capture, summarized as JSON and saved to
  `blockpilot/perf/<name>.json`. It covers:
  - MSPT with p99 and TPS, and the ticks over 50 ms by game tick, the `t<n>` of `sample` rows, with the chunks
    generated, loaded and sent and the entities added in them (every tick's in the file);
  - the block changes the integrated server sends to clients per block, block entity and sub-level, and the 50
    busiest positions (absolute, in the plot aboard a sub-level; the overview's `change at` rows; changes at positions
    past the first 65,536 a capture sees count as `block_change_positions_untracked`);
  - FPS and 1% low, time per block-entity and entity type, per renderer, per particle type with live counts and the
    mod and class that spawned them, or for those the server sent `server` and, in singleplayer, the mod and class that
    sent them, and per render stage on NeoForge;
  - network, counting the packets inside bundles by their own type too;
  - the chunks loaded in the world and in sub-level plots and, with Sable, the sub-levels loaded, in a `loaded` row.

  With `profile` (integrated server) it adds stack samples of the server thread while it ticks, about 20 a second,
  each counted toward the mod owning the innermost frame outside the JDK, Minecraft, the loaders and Mixin and toward
  its top frame, native ones marked: `stacks` in the summary, `mod` and `frame` rows. It also runs the server's metrics
  recorder, the one F3+L and `/perf start` use, for the capture's first 10 s, its own limit: `sections` with each
  section's share of its parent and of the whole down to 0.5%, `unspecified` kept, the seconds covered and the
  recorder's report, kept as `blockpilot/perf/<name>-profile`, and `section` rows at 5% or more. `err busy` comes
  while that recorder already runs.
- `perf status`.
- `perf <duration> [name] [profile]`: a capture in one line; it ends after that long and answers as `perf stop`.
  `stop` cancels the wait and leaves the capture running.
- `load server|client|net <ms> [jitter=<ms>]`: idle time added on purpose to every integrated-server tick, counted in
  its MSPT, or every frame, or a delay held on every packet of the client's connection each way, order kept, to
  reproduce a busy server, a slow machine or a laggy network without touching the world. `load` reports it, and
  `load off` ends it, as do leaving the world and closing.

## sable

With Sable only: `sable:list [r] [side=server]`, `sable:info <sub> [brief] [side=server]` (`brief`: yaw, pitch, roll,
speed, climb, world y, height above ground), `sable:find <sub> <block|#tag|*pattern> [limit]
[sort=x|y|z|-x|-y|-z] [near=<pos>]`, `sable:world <pos>`, `sable:plot <sub> <pos>`, `sable:tp <pos>` and
`sable:tp <sub> <block|#tag|*pattern>`. See [sable.md](sable.md).

## meta

- `help`, `help <command|group|namespace:|errors|grammar>`: the reference.
- `fmt text|json`: the reply format for the session.
- `sub on|off`, `sub log <level>|off`: pushes events, and log lines from a level up, as they happen.
- `stop`: cancels the running and queued actions of every session and releases held input.
- `wait <Nt>`, `wait <query> until <field><op><value> [clock=server]`: waits a number of ticks, or until a query's
  fields match.
- `sample <query> [and <query>…] every <Nt> x<n> [clock=server]`: a row per query and run. Client and server queries
  together, also `side=server` ones, run on the client's ticks with one server read per run, and those rows add the
  client's own tick `c<n>`; under `load server` such reads hold the client, which `held_client=` reports.
- `sample <query> [and <query>…] every <Nt> [x<n>] while <action|wait>`: samples beside the action or wait.
- `assert <query> <field><op><value>`: fails the script with `err assert_failed` and the observed values unless the
  query matches.
- `if <query> <field><op><value> then <command>`: runs the command only when the query matches.

[Back to the README](../README.md)
