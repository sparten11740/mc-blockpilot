# Proposal

## Why

When the launcher can't read its command line, for example a script split
over several arguments, it prints its whole usage text and only then, on the
last line, what was wrong. A caller that keeps only the end of the output
sees the tail of the help and may miss the error, and one that keeps the
start sees only the help.

## What Changes

- A command line the launcher can't read gets one line on stderr, `blockpilot:
  <what is wrong>; see --help`, and exit code 2, as now, so the exit codes of
  the standalone client stay as they are; the usage text is printed only for
  `--help`.
- The error for several script arguments says to put the commands in one
  argument, separated by semicolons.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `dev-integration`: the launcher's usage errors.

## Impact

- `cli/Main.java`: the usage error without the usage text.
- `cli/Options.java`: the wording of the several-scripts error.
- `cli/MainTest.java`, `cli/OptionsTest.java`.
