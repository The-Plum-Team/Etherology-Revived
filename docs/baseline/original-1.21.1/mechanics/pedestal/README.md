# Pedestal baseline contract

This directory records the planned native `published-0.1.7` Pedestal baseline.
The reserved, repository-owned Fabric 1.21.1 profile is v11. It has never been
provisioned or launched, and no screenshot or runtime evidence is claimed yet.

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

Expected screenshot names are:

1. `pedestal-gallery.png`
2. `pedestal-transition-drops.png`
3. `pedestal-persistence-initial.png`
4. `pedestal-persistence-reopened.png`

The fresh v1.4.0 harness has passed its clean build, 47 Java tests, remap, and
artifact validation. The active manifest pins its `339,617` bytes and SHA-256
`09272e04b122b20da33d1964b4e1ca9f67af768fb0db0c0fa1f74f0579799e57`.
The v11 runtime remains unprovisioned and has never been launched.
