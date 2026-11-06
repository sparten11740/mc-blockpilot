## MODIFIED Requirements

### Requirement: Sampling beside an action
With `while <action>`, `sample` SHALL run the action as if written alone and
sample beside it until the action ends or n (at most 1,000) samples are taken:
from before the action's first tick when both run on one side, else from the
query side's next tick. The line is the action's result, success or error, with
the samples as its rows. A `wait` (a duration, `idle` or `until`) SHALL serve
as the action too.

#### Scenario: Sampling a walk
- **WHEN** an agent runs `sample status every 2t while move forward 3`
- **THEN** the result is the move's `ok moved 3.0`, with one row of `status`
  fields every 2 ticks from before the first step to the end of the walk

#### Scenario: Sampling across a wait
- **WHEN** an agent runs `sample status every 20t while wait 200t`
- **THEN** the result is the wait's `ok` with eleven rows of `status` fields,
  the first taken before the wait began and the last as it ends

#### Scenario: Something other than an action after while
- **WHEN** an agent runs `sample status every 2t while look`
- **THEN** the result is `err syntax` saying `while` takes an action or a
  `wait`
