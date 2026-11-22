## ADDED Requirements

### Requirement: Chat lines in the message log
`chat` in the message log SHALL be every line the chat window shows, as it
shows it: from the server, replies to client-side commands and lines mods add
on the client alike, each once. Lines Blockpilot itself shows a person in the
chat window SHALL NOT be logged, and action-bar text SHALL be logged only as
`actionbar`.

#### Scenario: A client-side command's reply
- **WHEN** an agent types a client-side command that a mod registers, and the
  command answers "3 lights tracked" in the chat window
- **THEN** the next reply carries `! chat 3 lights tracked`, and `messages
  kind=chat` lists it once

#### Scenario: Action-bar text isn't chat
- **WHEN** a mod shows "Gear down" in the action bar on NeoForge
- **THEN** `messages kind=actionbar` lists it, and `messages kind=chat` doesn't
