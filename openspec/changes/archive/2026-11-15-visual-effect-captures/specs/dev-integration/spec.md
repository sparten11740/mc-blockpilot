## ADDED Requirements

### Requirement: A launcher for Git Bash
On Windows, beside `blockpilot.cmd`, the game SHALL write a plain `blockpilot`
sh script that runs the same client on the game's Java with Windows' paths and
classpath separator, for Git Bash, which runs a `.cmd` through cmd.exe and
breaks on a path with a space.

#### Scenario: A game folder with a space
- **WHEN** the game directory is `C:\Bomb Test` and an agent runs
  `"/c/Bomb Test/blockpilot/blockpilot" status` from Git Bash
- **THEN** the sh script runs the client, which answers as the `.cmd` would
  from cmd.exe
