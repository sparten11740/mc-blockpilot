## MODIFIED Requirements

### Requirement: Message log
The client SHALL record chat, action-bar, title and subtitle messages with their
kind and tick. It keeps the last 500. `messages [since=<tick>]
[kind=chat|actionbar|title|subtitle] [match=<pattern>]` SHALL return them
oldest first; `match=` keeps those whose whole text fits the pattern, ignoring
case, with `*` for any run of characters. `chat` SHALL be every line the chat
window shows, as it shows it: from the server, replies to client-side commands
and lines mods add on the client alike, each once. Lines Blockpilot itself shows
a person in the chat window SHALL NOT be logged, and action-bar text SHALL be
logged only as `actionbar`.

#### Scenario: Waiting for a chat line
- **WHEN** an agent runs `wait messages since=<tick> match=*engaged* until
  count>0 timeout=30s` and a mod later says "Autopilot engaged" in chat
- **THEN** the wait returns `ok` with `count=1` as soon as that message is
  logged, other chat lines in between don't end it, and `messages
  since=<tick>` then reads the message

#### Scenario: A client-side command's reply
- **WHEN** an agent types a client-side command that a mod registers, and the
  command answers "3 lights tracked" in the chat window
- **THEN** the next reply carries `! chat 3 lights tracked`, and `messages
  kind=chat` lists it once

#### Scenario: Action-bar text isn't chat
- **WHEN** a mod shows "Gear down" in the action bar on NeoForge
- **THEN** `messages kind=actionbar` lists it, and `messages kind=chat` doesn't
