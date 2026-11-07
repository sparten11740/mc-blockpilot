# Tasks

## 1. Usage errors in one line (dev-integration)

- [x] 1.1 Change `MainTest.usageErrorsExitTwoAndHelpExitsZero`, which expects the usage text before the error, to expect one stderr line ending `; see --help` and no usage text, and add a test for several script arguments; verify they fail before the change
- [x] 1.2 Print a usage error as that one line in `Main` and reword the several-scripts error in `Options`; verify the CLI tests pass

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
