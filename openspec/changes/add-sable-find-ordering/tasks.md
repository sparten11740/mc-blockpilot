# Tasks

## 1. Ordering (sable-sublevels)

- [x] 1.1 Add `sort=x|y|z|-x|-y|-z` and `near=<pos>` to `sable:find`, ordering all matches before the limit and adding `count=` when the limit left some out (D1, D2, D3), with `help sable:find`, the README's sable row and With Sable section; verify on the dev client with a sub-level that `sort=z` and `sort=-z` with limit 1 give the matches with the smallest and largest plot z (checked against the full list), that `near=` with a plot position of one match lists that match first, that `count=` appears only when the limit cut the list, and that `sort=w` fails with `err syntax`

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
