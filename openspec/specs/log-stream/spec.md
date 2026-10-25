# log-stream Specification

## Purpose

Gives agents the game log as structured, filterable data and as live events, so
warnings and exceptions from an experiment can be found and checked without
reading log files.

## Requirements

### Requirement: Log capture
Blockpilot SHALL keep the last 5,000 log entries at INFO level or above from the
whole game JVM: client, integrated server, and all mods. Each entry gets a
sequence number, time, level, thread, logger name and message.

#### Scenario: Server-thread error captured
- **WHEN** a mod logs an ERROR on the server thread
- **THEN** that entry is available through `log` with `thread=Server thread` and
  the mod's logger name

### Requirement: Marks and filtered reads
`log mark` SHALL return the current sequence number as `mark`. `log [level=<min
level>] [since=<mark>] [grep=<text>] [logger=<prefix>] [limit=<n>]` SHALL return
the newest `limit` matching entries, oldest first (default `level=warn`,
`limit=50`), with the current `mark` and the `count` of all matches, and report
how many older matches were left out.

#### Scenario: Errors from one experiment
- **WHEN** an agent runs `log mark`, then an experiment, then `log since=<mark>
  level=warn`
- **THEN** only WARN and ERROR entries logged after the mark are returned

#### Scenario: Nothing logged
- **WHEN** no entries match
- **THEN** the result is `ok mark=<n> count=0` with no rows

### Requirement: Condensed stack traces
An entry with an exception SHALL show the exception class and message, the first
three stack frames outside the JDK and the logging framework, and each `caused
by` with its class and message. `log detail <seq>` SHALL return the full stack
trace.

#### Scenario: Exception entry
- **WHEN** a mod logs a NullPointerException with a 60-frame trace caused by an
  IllegalStateException
- **THEN** `log` shows the NPE line, three frames and a `caused by
  IllegalStateException: …` line, and `log detail <seq>` shows all frames

### Requirement: Live log events
`sub log <level>` SHALL deliver new entries at or above that level as `! log
<LEVEL> <logger> <message>` events, through the normal event delivery. `sub log
off` stops them. Delivery is capped at 20 entries per second per session, and
the overflow is reported as `! log_dropped <n>` with the next reply.

#### Scenario: Error pushed during an action
- **WHEN** a session has run `sub log error` and a mod logs an error while that
  session's `goto` runs
- **THEN** the `goto` reply carries a `! log ERROR …` event line

### Requirement: Log capture works without a world
Log capture and `log` commands SHALL work from the title screen and need no
integrated server or cheats.

#### Scenario: Startup warnings
- **WHEN** an agent connects at the title screen and runs `log level=warn`
- **THEN** warnings logged during mod loading, from the moment Blockpilot was
  constructed onward, are returned
