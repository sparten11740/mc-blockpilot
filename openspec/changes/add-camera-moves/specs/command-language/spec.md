## ADDED Requirements

### Requirement: Repeated groups
An optional group in a form MAY be declared to repeat up to a limit. It SHALL
take its parts again each time its first part fits the next token, and the
values its slots take SHALL keep the order written. One repeat more than the
limit SHALL fail with `err syntax` naming the limit. Help SHALL show such a
group as `[…]…`, and the grammar file as a group that repeats.

#### Scenario: A repeated group
- **WHEN** an agent runs `camera move ~10,2,10 via ~10,2,0 via ~5,2,5 over 2s`
- **THEN** both via points are taken, in that order, and `help camera` shows
  the group as `[via <pos>]…`

#### Scenario: Not repeated
- **WHEN** an agent runs `camera move ~10,2,0 over 2s`
- **THEN** the move takes no via points and goes straight
