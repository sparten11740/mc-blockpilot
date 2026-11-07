# Adding your own commands

A person who holds agents to playing (`"agentCommands": "play"`) lets Blockpilot use your commands: the namespace you
register under has to be in `allowedExtensions` in `config/blockpilot/config.json` (`"allowedExtensions": ["mymod"]`),
empty by default. Until it is, a call answers `err not_allowed mymod:<name>` and `help` lists the namespace as not
allowed, naming the setting; tell those users to add it. Under `all`, the default, your commands work as they are, and
your events always reach agents. A change to the file applies within a second.

Other mods can add namespaced commands that behave like built-in ones (scripts, `wait`, `sample`, JSON). Compile against
the API only (`compileOnly("dev.blockpilot:blockpilot-api:2.1.0")`, from mavenLocal as in
[dev-environment.md](dev-environment.md)) and register them during mod loading, in a class only touched when Blockpilot
is installed:

```java
if (ModList.get().isLoaded("blockpilot")) {
    BlockpilotIntegration.register();
}

// BlockpilotIntegration.java
BlockpilotApi.register("mymod", CommandSpec.query("gear", (context, args) ->
        Result.ok(new Fields().put("extended", Gear.isExtended(), "extended=" + Gear.isExtended())))
    .help("landing gear state").field("extended", "true when down"));
```

`mymod:gear` is then available, e.g. `wait mymod:gear until extended==true timeout=10s`.

Handlers run on the client thread. A command that touches the server world declares `onServer()`: its query runs at the
end of a server tick, and its action starts, ticks and is cancelled on the server thread. It needs the same cheats world
as `server`, and its queries work with `sample … clock=server` or beside client queries in one `sample … and …`:

```java
BlockpilotApi.register("mymod", CommandSpec.action("build", (context, args) -> {
        Build build = Build.start(args.text("blueprint"));
        // null while still running; the result ends the action.
        return () -> build.done()
            ? Result.ok(new Fields().put("blocks", build.count(), "blocks=" + build.count()))
            : null;
    }).onServer().arg("blueprint", ArgType.TEXT).help("builds a blueprint at the player"));
```

A command declares its arguments as one or more forms, matched in order against a call's tokens; the first that takes
them all wins. A form is a sequence of keywords (`keyword`, bound to a name such as `op`), slots of one or more slot
types (`arg`, `optionalArg`; the first type that matches is used), optional groups (`optional`, taken when their first
part fits) and, last, free text (`ArgType.TEXT`). Options declare the slot types of their value, so a bad value fails
before the script runs, and a name ending in `:*` declares a family such as `rule:<name>=…`. A command with a single
form can declare its parts on the spec itself, as `build` does above:

```java
BlockpilotApi.register("mymod", CommandSpec.action("flaps", (context, args) -> {
        double degrees = args.word("op").equals("retract") ? 0 : args.number("degrees", 0);
        Duration over = args.has("ticks") ? args.duration("ticks") : new Duration(1);
        Flaps.move(args.option("side"), degrees, over.ticks());
        return () -> Flaps.settled() ? Result.ok() : null;
    })
    .form(form -> form.keyword("op", "set").arg("degrees", ArgType.NUMBER)
        .optionalArg("ticks", ArgType.DURATION))
    .form(form -> form.keyword("op", "retract"))
    .option("side", "one wing only", List.of("left", "right"))
    .help("moves the flaps").example("mymod:flaps set 15 20t"));
```

`help mymod:flaps` shows both forms, and `help grammar` prints `mymod.flaps-command ::= "mymod:flaps" ( "set" number
duration? | "retract" )`. `args.type(name)` tells which slot type a token was taken as; the typed accessors (`position`,
`entity`, `integer`, `duration`, `point`, `uuid`, `index`, …) check it. Registration throws `IllegalArgumentException`
for a declaration the grammar can't reach (a name or option name it can't write, a keyword such as `then`, two slots
with one name, a type an earlier one always takes first, or an optional slot that would take the next part's token), and
freezes the spec. An example that doesn't parse, or doesn't start with the command's `<namespace>:<name>`, gets the
command refused with a logged error.

A part that may come several times is a repeated group: `form.arg("pos", ArgType.POSITION).repeated(8, via ->
via.keyword("through", "via").arg("via", ArgType.POSITION))` takes `mymod:path 1,2,3 via 4,5,6 via 7,8,9`, up to 8
times; one more is `err syntax` naming the limit. Help shows it as `[via <via>]…` and the grammar as `( "via" position
)*`. `args.values("via")` gives every value the slot took, in order (`args.positions("via")` as positions), while
`args.word("via")` and the other accessors read the first. Repeated groups are new in blockpilot-api 2.1.0; an
integration built against 2.0.0 keeps working unchanged, and one that uses them needs Blockpilot 1.1.0 or later.

A slot of type `ArgType.ENTITY` takes `e<id>` and, from Blockpilot 1.1.0, `@<name>`, an entity's tag or custom name. The
engine looks the name up before the handler runs, so `args.entity(name)` always reads an id and a command needs no
change to take names; an unknown name answers `err no_entity @<name>` without running it.

A command can leave work for the end of its script: `context.atEnd(action)` runs the action when the script ends,
however it ends (its last step, an error, `stop` or real input), on the client thread and before the reply. Its result
arrives in the reply as an event named after the action's `label()`, which an ending should therefore give, and an
ending with nothing to report answers `Result.skip()`, which leaves none. An ending that hasn't answered after 60 s is
cancelled and answers `err timeout`, so a longer cleanup doesn't belong there. `atEnd` is new in blockpilot-api 2.1.0.

Your events reach agents whatever the config says, so an extension that turns players' words into agent input, such as
speech, must pass on only those of players Blockpilot trusts: `BlockpilotApi.isTrusted(uuid)` answers as Blockpilot
filters chat, true for the players on `trustedPlayers` in its config and the agent's own player, false on a server that
doesn't verify accounts and while Blockpilot isn't running. Extensions can read it but not change it. `isTrusted` is new
in blockpilot-api 2.1.0.

Extensions can emit events too: `BlockpilotApi.emit("mymod", "assembled", new Fields().put("uuid", id, "uuid=" + id))`
reaches every session as `! mymod:assembled uuid=…`. `BlockpilotApi.VERSION`, the version of `blockpilot-api`, bumps its
minor version for additions and its major version for breaking changes; declare the range of Blockpilot versions your
integration supports in your mod's metadata. Registrations made after mod loading has finished are refused.

## Upgrading from blockpilot-api 1.x

2.0.0 replaces single-shape commands with forms and checks declarations, so a 1.x integration needs recompiling and
these changes:

- `arg` and `optionalArg` take one or more types; `choice(name, optional, words…)` is `keyword(name, words…)` or
  `optionalKeyword(name, words…)`, and further ways to write a command are `form(…)`s.
- `ArgType.CHOICE` is `KEYWORD`; `POINT`, `WIDGET`, `MENU_SLOT`, `UUID`, `ID`, `TAG`, `PATTERN` and `SECONDS` are new,
  and `WORD` no longer takes option- or predicate-shaped tokens such as `a=b` or `hp<5`.
- `Args.kind` is `Args.type`, and the accessors throw `IllegalStateException` when asked for a type the slot didn't
  take; `Args` is built from `Args.Value`s. `CommandSpec.options()` maps names to `CommandSpec.Option`s.
- Options are typed with `option(name, description, types…)` or `option(name, description, words, types…)`; one declared
  without types takes any value, which the handler checks (`intOption`, `numberOption`, `durationOption`). Every action
  takes `timeout=` already and can't declare it.
- A registered spec can't change; invalid declarations throw at registration.

[Back to the README](../README.md)
