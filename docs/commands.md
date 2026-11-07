# Commands

Every command by group, with the details its row in the README leaves out. In the game, `help <command>` gives its
forms, options and reply fields, `help <group>` a group's commands, and `help errors` the error codes. The syntax of
scripts, positions and predicates is in [command-language.md](command-language.md).

## perception

- `status`: one line with position, health, the crosshair target with the hit point `hit=u,v,w` in the block,
  velocity, vehicle, camera angles, a placed camera's position as `camera <x,y,z>` from the last frame, and more.
- `look [r]`: the blocks and entities around the player, or around a camera placed with `away`.
- `entity e<id>|me [side=server]`: one entity's `e<id>`, position, yaw, pitch, velocity and age per tick; for a living
  entity its walk animation as `walk=<limb speed>` and `walk_pos=<swing position>`. `me` is the player. On the client
  it adds the updates it applied since its last tick, `net move1 data1`; `side=server` reads the integrated server's
  copy, so `sample entity e5 and entity e5 side=server every 1t x20` shows the client's drift.
- `find <block|#tag|*pattern|entity> [r [limit]]`: the nearest matches in a cube round the player, or round a camera
  placed with `away`, r up to 128, limit up to 1000, with `count=` when it left some out. A block, tag or pattern may
  carry states as Minecraft writes them, `oak_log[axis=y]`, and then matches only the blocks holding them. `~` is from
  the player's block as the find runs, and JSON's `at_abs` gives each row absolute. Blocks aboard a sub-level:
  `sable:find`.
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
- `camera at <pos> [look <pos|e<id>>] [clear] [away]` / `camera move <pos> [via <pos>]… [look <pos|e<id>|ahead>] over
  <duration> [ease=] [background]` / `camera follow <entity> [at <~dx,dy,dz>] [turning]` / `camera orbit <pos|entity>
  [by <degrees>] over <duration> [ease=] [background]` / `camera wait` / `camera reset|off`: renders from a placed
  camera. `unloaded` when the client hasn't loaded the camera's chunk. `clear` moves a camera that sits in a block with
  a collision shape toward the look point, a quarter block at a time and at most 8 blocks, to the first clear point,
  answering `cleared=<blocks>`, or `uncleared` when there is none. An entity look target is read where each frame draws
  it. `camera move` glides the camera from where it is drawn now, a placed camera or the player's eyes, to the point
  over the duration, placed anew in every frame, and answers as `camera at` does at the end: through up to 32 via points
  on one smooth curve at a constant speed; without `look` it keeps looking at what it looked at, with a target it turns
  toward it, and `look ahead` faces the way it travels; `ease=` is `linear` (the default, which keeps a chain of moves
  at speed), `in`, `out` or `inout` (for a single move from rest). It counts client ticks, so it goes on through `tick
  freeze`, and `stop` or real input leave the camera where the last frame drew it. With `background` it answers at once,
  `ok moving over=<n>t`, and the camera glides on its own while the script and the scripts after it go on, with server
  steps, `sound` or other actions, until a `camera at`, `camera move` or `camera reset` replaces it; `camera wait`
  answers once it has ended, as `camera at` would there, and at once with nothing moving. `camera follow` keeps the
  camera at an offset from the entity's feet in every frame, looking at it as `camera at … look` does, until the next
  `camera` command; `stop` and real input leave it. Without `at` the offset is the one the camera has now, so a shot
  framed with `camera at … look` carries on as the entity walks; with `turning` the offset is in the entity's frame, as
  Minecraft's `^` coordinates (`x` to its left, `z` the way it faces), and turns with it, catching up with a quick turn
  over a second or two. A removed entity leaves the camera where it was last drawn. `camera orbit` circles the camera
  round a point or an entity, followed as it moves, from where it is drawn now, keeping its distance across and its
  height and looking at the centre: a full turn without `by`, a positive angle to the camera's right as it faces the
  centre. It is timed as `camera move` is, with `ease=`, `background`, `camera wait` and `stop`, and leaves the camera
  where it ended, looking at the centre. `fov=<degrees>` (1 to 170) and `roll=<degrees>` (-180 to 180, a positive roll
  leaning the camera to its right) give a placed camera its own vertical field of view, in place of the player's and its
  effects, and tilt: `camera at` and `follow` set them at once, `move` and `orbit` glide to them along the way with
  their `ease=`, a value left out stays, and `camera reset` returns to the player's view; `status` names the field of
  view as `cam_fov` and the roll in `cam:`. With `away`, in singleplayer with commands allowed, the integrated server
  treats the camera as where the player is for the world it loads, ticks and sends: the chunks around it load and
  arrive, the entities there are sent, and sounds, particles, explosions and `playsound` there are heard. The player
  stands still meanwhile, and actions that move it make no progress; how near a player mobs spawn and despawn still goes
  by the player, so summon actors with `{PersistenceRequired:1b}`, and Sable still sends its sub-levels by where the
  player is, so the camera doesn't show them there. It answers once the client has the camera's chunk, after at most 10
  s. A `camera move`, `follow` or `orbit` keeps the camera away; `camera reset` or `off`, a `camera at` without `away`,
  leaving the world, a dimension change and agent control off end it. For an effect spanning hundreds of blocks, `option
  fov 110` and `option renderClouds off` widen and clear the view.
- `settle [timeout=]`: waits until the chunks around the player, or a camera placed with `away`, have arrived and those
  in view have compiled, e.g. after `camera at`, a teleport or a render distance change.
- `sounds [since=] [match=] [limit=]`: the sounds that started, the last 2000. `vol` and `pitch` are what plays, and
  `asked:<vol>/<pitch>` the values it was played with where its sounds.json entry scales them. A `match=` without `*`
  is a sound event id, `minecraft:` without a namespace, and an unknown one fails naming the closest. `~` is from where
  the player stands now, and JSON's `at_abs` is where the sound started.
- `sounds playing [since=] [match=] [limit=]`: what plays now, with `loop` and `played=<s>`. A sound whose category or
  the master volume is 0 never plays.
- `sound <id> [at <pos>] [volume=] [pitch=] [source=]`: plays a sound event on this client only, sending nothing to any
  server. With `at` it starts there and fades with distance from the camera as rendered, so a placed camera hears it
  from its own seat; without, it is an interface sound. `volume=` (default 1) above 1 carries it farther, as with
  `playsound`, `pitch=` takes 0.5 to 2 (default 1) and `source=` a sound category (default `master`). It answers with
  the id, category, absolute position or `ui`, volume, pitch and tick, and `muted` when the category or the master
  volume is 0. An unknown id fails as `sounds match=` does. It is an action, so `sample`, `wait` and `assert` can't
  run it.
- `messages [since=] [kind=] [match=]`: chat, action bar, titles and subtitles; `match=*engaged*` matches the whole
  text in any case.
- `particles [since=]`: particles spawned per type over a window.
- `option [<name> [<value>]]`, `option reset`: client options (see below).
- `pixels [[name=]x0,y0,x1,y1|[name=]@<pos>]… [r=]`: mean rgb and luma, max and p90 luma of parts of the last frame,
  with `camera <x,y,z>` when a placed camera rendered it.
- `record start [name] [width=] [fps=] [hud=off] [audio=off]`, `record stop`, `record status`, `record mark <label>`,
  `record <duration> [name] …`, `record script [name] …`: a video of the game with its sound; `record script` ends the
  take with its script, however it ends, and the reply carries its summary as `! record …`; `record mark` notes the
  video time and frame the script reached it at, `record stop` lists the marks and `<name>.marks.json` keeps them beside
  the video; see [recording.md](recording.md).
- `log [level=] [since=] [grep=] [logger=] [limit=]`, `log mark`, `log detail <seq>`: the game log.

`sounds` and `messages` add `from=<tick>`, where their log begins, when it dropped some of the asked window. `sounds`,
`messages` and `particles` name the ticks they cover as `window=t<first>..t<now>`; a bare `since=` tick from before
the world was joined adds `joined=t<tick>`, since `100t`, not `100`, counts back. Entering a world, even the one just
left, starts their logs anew, as their ticks are that world's; until the next world, they keep the last one's.

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
  with the reason when the player can't stand there, e.g. `stone fills ~2,-1,0; stand at ~2,0,0`. With
  [Baritone](https://github.com/cabaletta/baritone) installed, it walks through Baritone, with no option to give, and
  the reply adds `by=baritone`. Baritone digs and places blocks on the way, as its settings allow: turning its
  `allowBreak` and `allowPlace` off gives a walk that alters nothing. Its `err no_path` comes once Baritone has walked
  as near as it can, and `stop` or real input ends its walk as any other.

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
  blocks are passed over. `err out_of_reach <pos> is behind <block> <pos>` names what is in the way. Only what the
  player was digging counts as mined. If something else changes the block first, `mine` at a position or the crosshair
  digs what is there now, or gives `err no_block <pos> is air`, and `mine` by type goes on to the next block. By type,
  it walks into reach of each block and, when the crosshair still can't hit one, closer once before passing over it;
  with Baritone installed, both walks go through Baritone, which digs its way to blocks behind others, and the reply
  adds `by=baritone`.
- `place <item> <pos>`: places against the first neighbour, below, above, north, south, west, east, whose face toward
  the cell is in sight within reach, so it fills a hole from its far side, and aims at that face of the neighbour's
  shape, so also on a carpet or against a fence post. With no face in sight it aims at the first neighbour, and `err
  out_of_reach` names what the crosshair hits there; nothing is placed. `err no_item` says to give the item first; a
  taken cell names the cell above when that one is free.

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
  earlier held while the last is pressed: `key f3+l`. Key names are the running game's: 26.3 has no `f25` and adds
  others, such as `ac_home`.
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
- `trust`: the players whose chat reaches agents with its text, each `online` or `offline`, and `unverified` on a
  server that doesn't check accounts, where nobody is trusted. Another player's chat arrives as `withheld from=<name>`,
  in `! chat` events and in `messages`, the agent's own player's and system and mod messages as they came. The list is
  `trustedPlayers` in `config/blockpilot/config.json`, empty by default; a change to the file applies within a second,
  and no command changes it ([safety.md](safety.md)).

With `agentCommands: "play"` in `config/blockpilot/config.json`, agents may only play: a script with `cmd`, `server`,
`nbt`, `tick`, `gametest`, `snapshot`, `config`, `record`, `perf` (all but `perf status`), `load`, `create world`,
`delete world`, `join`, `leave` or `quit` fails before any step with `err not_allowed <command>`, and a slash command an
agent types into the chat screen or clicks in chat isn't sent, failing the command that sent it with `err not_allowed
cmd`. Extension commands that run on the integrated server are refused too. Input reaches only gameplay screens (the
world, container screens, chat, signs, books), else `err not_allowed screen`, and a click on a link fails with `err
not_allowed link`. A change to the setting applies within a second.

## session

- `worlds`, `servers`: the saved worlds and the server list.
- `join world|server <name>`: `join world tick=frozen <folder>` freezes the world's server before its first tick, so
  it reads as saved until `tick unfreeze` or `tick step`. When the game asks before loading a world whether to back it
  up first, as it does for a world whose data pack adds GameTests or other registry entries (experimental settings),
  a test world made by `create world` loads without a backup, with `experimental=true`, and any other world fails at
  once with `err join_failed`, the question left on screen for a person; other backup questions, such as for a world
  last played in another version, are left alone.
- `leave`: after a singleplayer world answers `saved_at=t<tick>`, the game time it was saved at.
- `quit`: leaves, answers `ok pid=<pid>` and `saved_at` as `leave` does, and closes the game.
- `respawn`: respawns from the death screen.
- `create world <name> [flat|void|normal] …`: a new world with cheats on, joined when ready (by default flat,
  peaceful and creative).
- `snapshot save|restore|delete <name>`, `snapshot list`: saves and restores a world's state. Joining a world with
  experimental settings again, `snapshot save` and `restore` meet the game's question as `join world` does.
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
  the player. It answers `settle` server ticks after the command ran (default `2t`), once the client has the changes;
  `settle=0t` answers in the server tick the command ran in. Consecutive server steps (`server`, `nbt`, `tick`) run back
  to back on the server thread, each starting in the server tick the one before it answered in, so `settle=1t` spaces
  them one server tick apart and `settle=0t` runs them in one. A failing command, a failing `execute if` test among
  them, is `err command_failed` with its `result`, which `?server …` lets the script carry on past. A command that
  reports no result, such as a function without `return`, is `ok success=true` without `result`, unless it sent a
  failure. A `;` inside the command, as in an NBT int array, is written `\;` so it doesn't end the step:
  `server data modify storage test:x ids set value [I\;1,2]`.
- `nbt <pos|e<id>|uuid> [path=]`: the server-side data of a block entity or entity, SNBT in text and JSON in json.
- `tick freeze|unfreeze|step <n>|rate <tps>|query`.
- `gametest run <selector>`: runs the game's own GameTests whose ids match the selector, `*` for any run of characters:
  `namespace:path` on 26.x, from data packs and mods (`gametest run mymod:*`), and `class.method` in lower case on
  1.21.1, registered by mods (`gametest run mymod.*`). The tests build their structures beside the player, as `/test
  run` does, and `server test clearall` removes them. It answers once every test has ended, `passed=` and `failed=` with
  a row per test, `passed`, or `failed` and its message; `err no_test` when nothing matches. NeoForge runs GameTests
  only in a development environment started with `-Dneoforge.enableGameTest=true` (Blockpilot's dev runs set it), and
  answers `err unsupported` without it. A data pack's tests load with the world, so after adding one, join the world
  again.
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
- `wait <Nt>`, `wait <query> until <field><op><value> [clock=server]`: waits a number of ticks, so the step after it
  runs that many ticks after the one before, or until a query's fields match.
- `sample <query> [and <query>…] every <Nt> x<n> [clock=server]`: a row per query and run. Client and server queries
  together, also `side=server` ones, run on the client's ticks with one server read per run, and those rows add the
  client's own tick `c<n>`; under `load server` such reads hold the client, which `held_client=` reports.
- `sample <query> [and <query>…] every <Nt> [x<n>] while <action|wait>`: samples beside the action or wait.
- `assert <query> <field><op><value>`: fails the script with `err assert_failed` and the observed values unless the
  query matches.
- `if <query> <field><op><value> then <command>`: runs the command only when the query matches.

[Back to the README](../README.md)
