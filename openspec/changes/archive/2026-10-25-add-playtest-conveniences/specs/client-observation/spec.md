# Spec Delta

## MODIFIED Requirements

### Requirement: Message log
The client SHALL record chat, action-bar, title and subtitle messages with their
kind and tick. It keeps the last 500. `messages [since=<tick>]
[kind=chat|actionbar|title|subtitle] [match=<pattern>]` SHALL return them
oldest first; `match=` keeps those whose whole text fits the pattern, ignoring
case, with `*` for any run of characters. Action-bar, title and subtitle
messages are also delivered as events. Mods refresh the action bar every tick,
so the same action-bar text within 2 seconds of its last refresh is neither
logged nor delivered again.

#### Scenario: Action-bar feedback
- **WHEN** a mod shows "Gear refused: airspeed too high" in the action bar
- **THEN** the next reply carries `! actionbar Gear refused: airspeed too high`,
  and `messages kind=actionbar` lists it

#### Scenario: Waiting for a chat line
- **WHEN** an agent runs `wait messages since=<tick> match=*engaged* until
  count>0 timeout=30s` and a mod later says "Autopilot engaged" in chat
- **THEN** the wait returns with that message, and other chat lines in
  between don't end it
