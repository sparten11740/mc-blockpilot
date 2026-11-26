## MODIFIED Requirements

### Requirement: Endpoint readiness file
Once the endpoint is listening, Blockpilot SHALL write
`<gameDir>/blockpilot/endpoint.json` with the port, the process id, the mod and
API versions, the token file path, and `mods`: each loaded mod's id, version
and file, as `mods` names them, without hashes. It removes the file on clean
shutdown. A stale file from a dead process is recognizable by its process id.

#### Scenario: Agent waits for launch
- **WHEN** an agent starts `runClient` in the background and polls for
  `run/blockpilot/endpoint.json`
- **THEN** the file appears only once the endpoint accepts connections

#### Scenario: Which client runs which build
- **WHEN** a harness finds two clients' `endpoint.json` files
- **THEN** each lists that client's mods with their versions and files, so the
  harness can pick the one running its build before it connects
