## ADDED Requirements

### Requirement: Client query
`client` SHALL answer the Minecraft version, the loader with its version,
Blockpilot's version, the extension API version, the Java version and the
operating system, and how many mods are loaded. On the vanilla agent the
loader is `vanilla`.

#### Scenario: A NeoForge client
- **WHEN** an agent runs `client` on a NeoForge 1.21.1 client
- **THEN** the result names `minecraft=1.21.1`, the loader `neoforge` with
  its version, `blockpilot=` and `api=` with their versions, `java=`, `os=`
  and `mods=` with the count of loaded mods

### Requirement: Renderer in the client query
`client` SHALL name the GPU backend the game runs on as `backend=opengl` or
`backend=vulkan`, and the GPU, its vendor and its driver as the game reports
them. From 26.2, where the options choose a graphics API, it SHALL also name
the option's value as `asked=default|opengl|vulkan`, so a backend that differs
from the one asked for shows a fallback.

#### Scenario: Vulkan asked, OpenGL running
- **WHEN** a 26.3 client whose options ask for Vulkan started on OpenGL, as on
  a Mac, and an agent runs `client`
- **THEN** the result holds `backend=opengl asked=vulkan` and the GPU's name

### Requirement: Loaded mods query
`mods [match=<pattern>]` SHALL list a row per loaded mod, ordered by id: its
id, its version and the file it was loaded from, with the file's SHA-256.
`match=` keeps the mods whose id fits the pattern, ignoring case, with `*` for
any run of characters. Files inside the game directory are named relative to
it, others absolutely.

#### Scenario: Checking the build under test
- **WHEN** a harness runs `mods match=mymod` on a client that loaded
  `mods/mymod-0.3.0.jar`
- **THEN** the one row names `mymod`, `0.3.0`, `file=mods/mymod-0.3.0.jar` and
  the jar's SHA-256, which the harness compares with its own build's

### Requirement: Mods without a file of their own
Where the loader tells that a mod is nested in another mod's file, as Fabric
and NeoForge do, its row SHALL carry `nested` with `in=<id>` naming that mod.
A mod a development run loads from folders SHALL name the first folder as
`folder=`. A mod whose path names no file on disk SHALL show only its id and
version. None of these has a hash.

#### Scenario: A mod nested in another's jar
- **WHEN** a mod's jar carries a library mod inside it, on Fabric or NeoForge
- **THEN** the library's row carries `nested`, `in=` with the carrying mod's
  id, and no file or hash

#### Scenario: A loader that doesn't tell
- **WHEN** a Forge mod's jar carries a library mod inside it
- **THEN** the library's row names its id and version, with no file or hash

#### Scenario: The mod under development
- **WHEN** an agent runs `mods match=mymod` in a development run of `mymod`
- **THEN** the row names the folder its classes load from, without a hash

### Requirement: Hashes from load time
A mod file's SHA-256 SHALL be taken from the file as it was when the game
loaded it: Blockpilot hashes the files in the background as the game finishes
loading, and keeps each file's size and modification time with its hash. A
row whose file differs from those now SHALL carry `changed`, because the jar
on disk is no longer the one the game runs.

#### Scenario: A jar rebuilt under a running client
- **WHEN** a client kept running between two runs loaded `mymod-0.3.0.jar`,
  the second run builds a new `mymod-0.3.0.jar` over it, and runs `mods
  match=mymod`
- **THEN** the row carries the old jar's hash and `changed`

### Requirement: Hashes still being taken
While the hashing is under way, `mods` SHALL answer `hashing=<files left>` and
leave out the hashes not taken yet, so `wait mods until hashing==0` waits for
them. Once done, `hashing=0`.

#### Scenario: Right after the start
- **WHEN** a harness runs `mods` as soon as `endpoint.json` appears, before
  the hashing is done
- **THEN** the result holds `hashing=` with the number of files left, and
  `wait mods until hashing==0 timeout=30s` returns once every hash is there
