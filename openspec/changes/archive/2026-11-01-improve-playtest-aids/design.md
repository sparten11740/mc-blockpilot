# Design

## Context

- `PerceptionCommands.describe` builds `block`'s reply from `properties(state)`,
  which keeps only values that differ from the block's default state.
  `Regions` uses the same helper for the `blocks` palette. A predicate's
  `Condition.lookup` reads a missing field as `none`.
- `find` rows already carry `at` (`Rel.of`, relative to the player's block when
  the reply is built) and a JSON-only `at_abs`.
- `Screens.describe` lists slots and widgets. The creative inventory's tabs
  are drawn by `CreativeModeInventoryScreen` and aren't widgets. Which tabs a
  page shows is loader-specific: NeoForge patches in pages with a public
  `getCurrentPage().getVisibleTabs()`, and Fabric API exposes pages through
  `FabricCreativeInventoryScreen` (1.21.1) and
  `FabricCreativeModeInventoryScreen` (26.1). The tab rectangle comes from the
  screen's private `getTabX`/`getTabY` (26 by 32 GUI units from
  `leftPos`/`topPos`), which every loader's page logic feeds.
- `LivingEntity.walkAnimation` (`WalkAnimationState`) has public `speed()` and
  `position()` in both versions.
- `camera at` places the camera through `CameraControl.place` and reports the
  camera cell's block, with `inside=` when it is a full collision block.
- `place` throws `err no_item` from `InventoryOps.select` and `err syntax`
  "is occupied by" when the target can't be replaced.

## Goals / Non-Goals

**Goals:**
- Fix the default-value predicate bug without making `blocks` dumps longer.
- Make each gap a reply field or message, so it needs no screenshot or second
  try.

**Non-Goals:**
- A `click t<i>` target or a `creative tab` command: the bounds suffice.
- Clearing a camera that is aboard a sub-level but looks at a point in the
  world: `clear` steps in the coordinates as given.

## Decisions

### D1: Every property for `block`, non-default ones for `blocks`

`block` switches to a helper that lists every property in the state's order.
`blocks` keeps the non-default helper, since its palette lists many states and
grows quickly. The alternative, marking defaults (`lit=false*`), would still
leave predicates to know the default; listing every value lets
`props.lit==false` hold.

### D2: Creative tabs through the platform, positions through the screen

A new `ClientPlatform.Services` method returns the tabs the creative screen
shows on its current page: on NeoForge `getCurrentPage().getVisibleTabs()`, on
Fabric `getItemGroupsOnPage(getCurrentPage())` (1.21.1) or
`getTabsOnPage(getCurrentPage())` (26.1), behind the entry point's version
switch. A `CreativeModeInventoryScreenAccessor` mixin in each version's source
set invokes `getTabX`/`getTabY` and reads the static `selectedTab`.
`Screens.describe` adds a `t<i>` row per tab after the widgets, in the widget
row's format, with the tab's display name. Hit-testing every tab in
`CreativeModeTabs.tabs()` instead would also list the tabs of other pages
whose rectangles sit at the same column.

### D3: Walk animation always present for living entities

`walk` and `walk_pos` are given for every living entity, zero included, so
`walk==0` holds for legs at rest. They aren't left out at zero as `vel` is.
Other entities have no walk animation and carry neither field.

### D4: Clearing toward the look point

The play-test asked for the camera to step back, but backing away along the
view line keeps the block it started in between the camera and the look point,
so the shot stays blocked. Stepping toward the look point leaves the block on
the side facing the target. Steps are a quarter block, up to 8 blocks and
never past the look point (or the player's eyes when there is no `look`). A
cell is clear when its block has an empty collision shape, which treats glass
and partial hull blocks as blocking, while the existing `inside=` warning only
flags full blocks.

### D5: Hints in the existing errors

`place` checks for the item before selecting it and throws `no_item` with
"not in the inventory; give it first, e.g. cmd give @s <item>". The taken-cell
message appends "; <pos> above is free" when the cell above can be replaced.
The codes stay the same, so scripts matching on them keep working.

## Risks / Trade-offs

- [Longer `block` replies] → One line per query. Properties come in the
  state's own order, so a reply stays easy to scan.
- [Another mod could change the creative screen's paging] → The platform
  method answers with what the loader's page API reports. A mod that bypasses
  it gets tabs listed from the loader's view.
- [`clear` on a target inside the same solid region] → It stops at the look
  point and reports `uncleared`.
