# Tasks

## 1. Counting and summary (performance-capture)

- [x] 1.1 Count block changes per dimension and position in `Perf.blockChanged`, under its lock, with the count, last block and sub-level per position, at most 65,536 positions per capture and the changes beyond that in an untracked counter, all reset by `perf start` (D1, D2), and pass the dimension and position from `BlockChanges.changed`; verify with a `PerfTest` case that changes at three positions rank by count with their block, sub-level and `dim` only outside the overworld
- [x] 1.2 Add `block_change_positions` (the 50 busiest, `pos` as `[x,y,z]`, block, `sub`, `dim`, count, `per_s`) and `block_change_positions_untracked` (only above zero) to the summary's server object (D3); verify with `PerfTest` cases that the list stops at 50 and that changes past the bound count as untracked while the per-block counts still include them

## 2. Overview and docs

- [x] 2.1 Show the top five positions as `change at <x,y,z> <block> [on <sub>] [in <dim>] <rate>/s` rows in the `perf stop` text overview, and name the positions in the `perf` help and the README's performance row (D4); verify on the 1.21.1 dev client with two observers facing each other (a clock) that `perf start; wait 100t; perf stop` lists both observers' positions as `change at` rows, with rates that add up to the observer's rate in the per-block list, and that the saved JSON has them in `block_change_positions`

## 3. Integration checks

- [x] 3.1 Run `./gradlew build` for all four targets; verify all pass
