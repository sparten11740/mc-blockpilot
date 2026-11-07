# The command language

A script is commands separated by `;`; each command answers with one result line (`ok …`, `err <code> …` or `skip` after
a failure) plus indented rows. Positions are `x,y,z` or `~dx,dy,dz` (relative to the player's block), entities `e<id>`
or `@<name>`, counts `x<n>`, durations `20t` or `3s`, options `name=value`. `@<name>` is the entity with that scoreboard
tag or custom name, ignoring case, the nearest when several have it, looked up as the command runs, so `wait` and
`sample` follow one that appears later; tags need the integrated server, and `err no_entity @<name>` means no loaded
entity has the name. Ids drop the `minecraft:` namespace. Repeating `look`, `inv` or `screen` in a session reports only
what changed (`full` forces everything). Game events (hurt, got, chat, actionbar, screen, died, …) arrive as `! …` lines
with the next reply. A script stops at the first `err` unless the command is written `?cmd`, which reports its error and
goes on (handy for probes). `assert <query> <predicates>` turns a script into a test: it fails with `err assert_failed`
and the observed values when the predicates (as in `wait … until`) don't hold, and `if <query> <predicates> then
<command>` runs the command only when they do. A predicate's value can name another field of the same result, as in
`hp<$max_hp`. Every tick number in a reply (`tick query`, `sample` rows, waits, `sounds`, `messages` and their `since=`,
which also takes a duration back from now such as `since=60t`) is the same clock: the integrated server's game time in
singleplayer, else the client's. `sample … fields=a,b` keeps only those fields in each row. `help`, `help
<command|group>` and `help errors` give the full reference.

The language has a grammar, written in EBNF in the `command-language` spec, and a recursive-descent parser that follows
it; `help grammar` prints it, with one production per command generated from that command's declaration. A whole script
is parsed before any of it runs, so a typo in its last step stops it before the first one acts: that step answers `err
syntax col <n>: <hint>`, naming what the command takes there, and every other step `skip`. Common slips get a pointed
hint, e.g. `id=air` in a predicate (predicates compare with `==`), `hp < 10` (a predicate is one token), or `wait 5` (a
duration needs its unit).

The whole grammar, every command included, is in [grammar.ebnf](grammar.ebnf), and drawn as railroad diagrams in
[grammar.xhtml](grammar.xhtml) (GitHub shows that file as source; open it in a browser). A test parses the file with the
[Railroad Diagram Generator](https://github.com/GuntherRademacher/rr)'s W3C EBNF parser and checks that every name is
defined once and used. After a command's forms change, rewrite the file from the 1.21.1 NeoForge dev client, which has
Sable, and redraw the diagrams:

```sh
python3 tools/grammar.py --game-dir blockpilot/versions/1.21.1-neoforge/run
./gradlew grammarDiagrams
```

```
> select iron_pickaxe; mine #logs x3; inv
ok hand:iron_pickaxe
ok mine 3/3 oak_log
ok
  ~ 1:iron_pickaxe dur247/250
  + 5:oak_log x3
! got oak_log x3
```

[Back to the README](../README.md)
