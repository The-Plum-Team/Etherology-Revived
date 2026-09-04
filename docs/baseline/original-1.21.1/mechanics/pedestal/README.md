# Pedestal baseline contract

This directory records the native `published-0.1.7` Pedestal baseline contract.
The repository-owned Fabric 1.21.1 v11 profile consumed its sole launch on
2026-09-04, reached a fresh integrated world, and then exceeded the
controller's 1,800-second deadline before publishing a report or screenshot.
V11 is diagnostic history, not accepted runtime evidence. A fresh v12 profile
is prepared for the next attempt and has not been provisioned or launched.

The dedicated scenario is designed to record:

- the `etherology:pedestal` block, item, and
  `etherology:pedestal_block_entity` registry identities, all 1,024 states,
  default properties, horizontal facings, outline shapes, recipe, advancement,
  loot, tags, and 64 byte-pinned client/data resources;
- real `BlockItem` standalone, waterlogged, two-high, and three-high placement,
  including the `full`, `bottom`, `middle`, and `top` shape transitions and the
  block-entity presence rule;
- carpet placement, no-op, swap, retrieval, ordinary item placement/retrieval,
  the two one-item inventory slots, serialized `Items`/`removed` data, and the
  closed `SidedInventory` contract;
- ordinary-item dispensing into a Pedestal from all six directions and carpet
  dispensing from the four horizontal directions, plus upward carpet
  dispensing into an occupied carpet slot and empty display slot;
- the item/carpet drops and stale block-entity removal caused by converting a
  populated `full` Pedestal to `bottom`, plus ordinary replacement with air;
- a forced save, full disconnect/reopen, exact state/inventory equality, and
  four unedited 1920x1080 framebuffer captures after stable renders.

Read-only bytecode inspection of the hash-pinned published `0.1.7` Etherology
JAR (`2,743,963` bytes; SHA-256
`38de3c1aad47fc715c2226266dec4c70c02d16370034a4e0350508131ac15c43`)
confirms that the empty-carpet-slot path stores and decrements the carpet,
takes the dispenser direction's opposite, and passes it through
`PedestalBlockEntity.setCarpetColor` to the Pedestal's horizontal-only facing
property. The scenario records `UP` and `DOWN` as explicit
`hash-pinned-published-0.1.7-bytecode-not-executed-safety-guard` limitations and
never executes a vertical carpet against an empty carpet slot. Item dispensing
still exercises all six directions; ordinary empty-slot carpet dispensing
exercises north, south, west, and east; and the occupied-carpet fallthrough is
exercised upward.

The harness reaches the original only through registries and ordinary
Minecraft block/item/inventory/interaction APIs. Dotted original class names
are observations to compare, not linked implementation types.

The four required screenshot names for a successful fresh run are:

1. `pedestal-gallery.png`
2. `pedestal-transition-drops.png`
3. `pedestal-persistence-initial.png`
4. `pedestal-persistence-reopened.png`

The v1.4.0 harness passed its clean build, 47 Java tests, remap, and artifact
validation. The v11 manifest pins its `339,617` bytes and SHA-256
`09272e04b122b20da33d1964b4e1ca9f67af768fb0db0c0fa1f74f0579799e57`.
Its run emitted no direct camera telemetry, but two saved player snapshots
preserve an identical pose away from the exact camera contract. Source
inspection shows the render-ready failure returning to the client-mirror stage
while resetting the per-stage timer, which explains the eventual outer timeout
as an inference rather than mechanic evidence. The compact diagnostic record is
[`pedestal-v11`](../../../../evidence/original-1.21.1/pedestal-v11/). The
ignored v11 runtime remains preserved and must never be launched again.

The v12 v1.4.1 harness preserves the exact 74-assertion contract and camera
coordinates. It clears input after each client tick, restores the exact player
pose at `GameRenderer.render` HEAD before the framebuffer is drawn, and no
longer returns from render readiness to a stage that resets the watchdog. Its
51 Java tests, remap, artifact validation, and two reproducible clean builds
passed. Both builds produced a `340,250`-byte JAR with SHA-256
`a99809d6443a4757c860e98d2f09e1d5775667a69e331a7e631930eb5728c7eb`.
These are static preparation facts, not native mechanic evidence.
