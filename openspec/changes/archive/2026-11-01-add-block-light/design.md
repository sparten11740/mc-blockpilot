# Design

## Context

- `block` reads through `Sides.read`: the client level by default, the
  integrated server's level with `side=server`. `Rel.sub` adds `sub=` and
  `world=` for a plot position.
- Sable 2.0.6 gives each plot its own light engine on the server. Its
  `BlockAndTintGetter.getBrightness` overwrite routes a `ServerLevel` query at
  a plot position to that engine. The client receives plot light with the
  chunk packets and stores it in its ordinary light engine, so the client's
  `getBrightness` at a plot position is the plot's light.
- Section meshes light a quad lying on a block face from the neighbouring
  cell, and a quad set in from the faces from the block's own cell. Block
  entities aboard get the light at their cell with sky light scaled by the
  sub-level's exposure, which Sable's public API doesn't offer.

## Goals / Non-Goals

**Goals:**
- Read the light a cell holds, on either side, also aboard a sub-level.

**Non-Goals:**
- The six neighbours in one reply: one `block` per cell in a script does it.
- The light a block entity renderer is passed: Sable's sky scale isn't in its
  public API.
- Light in `blocks` dumps or `look`.

## Decisions

### D1: A keyword, not new default fields

`block` replies stay as short as they are for the common case. The light is
added only when asked for with `light`.

Alternative: always add the two fields. That would lengthen every `block`
reply and change the documented `ok stone`.

### D2: Stored light from `getBrightness`

`sky=` and `block_light=` are `level.getBrightness(LightLayer.SKY|BLOCK,
pos)`: what the light engine stores, which is what meshes and renderers read
before the light texture applies the time of day. A light source's own cell
already holds its emission.

Alternative: `LevelRenderer.getLightColor`, packed and with emissive blocks
forced to full. That mixes rendering rules into a light readout.

### D3: The world cell from the block's centre

For a plot position, the world cell is the one holding the block's centre as
`toWorld` turns it into the world now, read from the same level as the plot
light. On the server that is the world's light engine, since only plot
positions go to a plot's engine. The world fields are left out while the
world cell's chunk isn't loaded, because an unloaded chunk reads as full sky
light.

## Risks / Trade-offs

- [A turned sub-level's block straddles several world cells] → The centre's
  cell is one sample. Other world cells can be read with `block` directly.
