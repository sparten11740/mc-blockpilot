# Design

## Context

- A script runs as a `ScriptJob`: it steps its statements, adds a result
  for each, skips the rest after an error, and completes its reply. A
  cancel (`stop`, real input, a closed session) fails the statement it is
  at, skips the rest and completes at once; the engine then drops the job
  from its queue.
- The job is the `CommandContext` its commands get; the context offers only
  the session and position resolving.
- A recording outlasts the call that started it, by design: `record start`
  and `record stop` may come in separate calls.
- `record stop` is an action that answers once the encoder has finished
  and the file is saved.

## Goals / Non-Goals

**Goals:**
- A take that can't outlive its script, saved whatever ends the script.
- A general way for a command to clean up when its script ends.

**Non-Goals:**
- Changing `record start`: a recording across calls stays possible.
- Endings for queries, or endings that change the script's results.

## Decisions

### D1: Endings belong to the script

`CommandContext.atEnd(Action ending)` registers an action with the job the
command runs in. After the job's last statement, or once a failure has
skipped the rest, the job runs its endings in the order given, on the
client thread, before it completes its reply. Each ending's result is
delivered to the job's session as an event named after the ending's label,
with the result's fields, or `err` and the code for an error, so the reply
carries it among its events, or the session pushes it as it does any event.
Callers give their ending a `label()`, as the default is the class's name,
and an ending with nothing to report answers `skip`, which leaves no event.
An ending that hasn't answered after 60 s is cancelled and answers `err
timeout`, as an action's default timeout would. Blockpilot's own endings may
time themselves with the engine-internal `Engine.SelfTimed`, as the
recording's ending does, since saving a long take can take longer; an
extension's ending always has the 60 s cap, so a longer cleanup is not one
for `atEnd`, and `docs/extending.md` says so. The default `atEnd` throws
`UnsupportedOperationException`, as a context outside a script, such as a
test's, has nowhere to run it.

### D2: A cancelled script still runs its endings

A cancel, which runs on the caller's thread (`stop`, real input, a closing
session), fails and skips as now but completes only after the endings have
run, so `stop` and real input still save the take. The engine moves a
cancelled job that still has endings to `free`, the list of jobs it steps
off the queue for query-only scripts, so no new engine concept is needed;
the job's endings step only on the client tick, as the server tick's step
returns at once. The queue goes on meanwhile, and the cancelled script's
reply comes once its endings answer.

### D3: `record script` stops its own recording

`record script` starts a recording as `record start` does and registers an
ending that, if that same recording object is still the running one, stops it as
`record stop` would and answers with its summary; if the script stopped it
already, or another recording runs by then, the ending answers `skip` at once.
The ending's label is `record`, so the summary arrives as `! record …`.

## Risks / Trade-offs

- [A script's reply waits for the encoder] → It does, as `record stop`
  does; the recording's ending times itself, as a long take's encoder may
  take longer than 60 s to finish.
- [The API grows] → One default method, additive; extensions built against
  2.0.0 keep working.
