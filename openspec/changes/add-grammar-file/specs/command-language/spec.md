# Spec Delta

## ADDED Requirements

### Requirement: The grammar file
The repository SHALL hold the entire grammar in `docs/grammar.ebnf`: the core
productions and one production per built-in command and option, as
`help grammar` prints them. The file SHALL parse as W3C EBNF with an EBNF
implementation other than Blockpilot's, define each production once and every
name it refers to, and have no production that nothing reaches.
`docs/grammar.xhtml` SHALL show its productions as railroad diagrams.

#### Scenario: A command's forms change
- **WHEN** a built-in command gets a new form and the grammar file isn't
  regenerated
- **THEN** the tests or the smoke test fail, naming the production that
  differs

#### Scenario: An undefined name
- **WHEN** a production in the file refers to a name no production defines
- **THEN** the grammar file test fails, naming it
