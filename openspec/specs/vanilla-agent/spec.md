# vanilla-agent Specification

## Purpose

Runs Blockpilot on the vanilla Minecraft client without a mod loader, as a
Java agent added to the launcher profile's JVM arguments.

## Requirements

### Requirement: Installed as a Java agent
Each vanilla jar SHALL run Blockpilot when the vanilla client of its version is
started with `-javaagent:<path to the jar>` among its JVM arguments, with no
other change to the installation: `26.1-vanilla` on 26.1.2, `26.2-vanilla` on
26.2 and `26.3-vanilla` on 26.3. It SHALL then open the agent endpoint and write
the launcher as the loader builds do.

#### Scenario: Vanilla launcher profile
- **WHEN** a player adds `-javaagent:/path/blockpilot-1.0.0+26.1.2-vanilla.jar`
  to a 26.1.2 profile's JVM arguments in the Minecraft Launcher and starts it
- **THEN** `<game dir>/blockpilot/endpoint.json` and the launcher appear, and
  `status` answers once a world is joined

#### Scenario: Newer vanilla profile
- **WHEN** a player adds `-javaagent:/path/blockpilot-1.0.0+26.3-vanilla.jar`
  to a 26.3 profile's JVM arguments in the Minecraft Launcher and starts it
- **THEN** `<game dir>/blockpilot/endpoint.json` and the launcher appear, and
  `status` answers once a world is joined

### Requirement: The same commands as with a loader
On the vanilla client, Blockpilot SHALL accept the same commands with the same
replies as the Fabric build of that version, except where they need a loader:
`config` SHALL fail with `err unsupported`, as on Fabric.

#### Scenario: Playing on vanilla
- **WHEN** an agent runs `create world test flat; goto ~5,0,0; screenshot` on
  the vanilla client
- **THEN** the replies have the same form as on the Fabric build

#### Scenario: Mod configs need a loader
- **WHEN** an agent runs `config` on the vanilla client
- **THEN** the result is `err unsupported`

### Requirement: Only on its own version
The vanilla agent SHALL check the game's version as it starts. On any other
version than the one its jar was built for, it SHALL log why and leave the game
unchanged, applying no mixins.

#### Scenario: Wrong version
- **WHEN** a 1.21.1 client is started with the 26.1 vanilla agent
- **THEN** the game starts as without it, and its log says the agent supports
  only 26.1.2

#### Scenario: Another 26.x version
- **WHEN** a 26.3 client is started with the 26.2 vanilla agent
- **THEN** the game starts as without it, and its log says the agent supports
  only 26.2
