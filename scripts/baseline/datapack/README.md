# Etherology 0.1.7 visual gallery datapack

This directory is an unprovisioned historical gallery fixture. It was designed
to stage a compact visual gallery for Etherology `1.21-0.1.7` on Minecraft
1.21.1, but the repository-owned baseline controller does not currently copy,
install, enable, or execute it. The `phase0-smoke` harness creates its own fresh
world and does not use this datapack.

Do not treat this fixture or its identifiers as runtime evidence for the pinned
published binary. It becomes an executable baseline input only after a future
repository-owned world/harness contract explicitly pins its hash, stages it
under `.state/runtimes/...`, and verifies the resulting behavior. There are no
instructions here for installing it into any external launcher or user profile.

The gallery has a fixed origin at `(0, 96, 0)` and occupies the inclusive
bounding box `(0, 96, 0)` through `(58, 105, 10)`. Run the functions from the
Overworld; commands act in the executor's current dimension. Setup functions
erase and rebuild their own bay, while full setup and `gallery/clear` erase the
complete bounding box. Use a disposable creative test world whose builds do
not overlap that area.

## Historical function inventory

When a future owned harness adopts this fixture, its former full-gallery entry
points are:

```mcfunction
/function etherology_baseline:gallery/setup
/function etherology_baseline:gallery/teleport
```

Its former individual-bay entry points are:

```mcfunction
/function etherology_baseline:gallery/decorative_worldgen
/function etherology_baseline:gallery/functional_machines
/function etherology_baseline:gallery/seals_aspects
/function etherology_baseline:gallery/storage_utilities
/function etherology_baseline:gallery/combat_equipment
```

Its cleanup entry point removes the gallery and only armor stands tagged by the
fixture:

```mcfunction
/function etherology_baseline:gallery/clear
```

The bays run west to east:

1. `x=0..10`: decorative and world-generation materials
2. `x=12..22`: functional machine blocks
3. `x=24..34`: seals, sedimentary stages, and Primoshards
4. `x=36..46`: ether storage, transport, and utility blocks/items
5. `x=48..58`: representative combat equipment

## Scope

These functions only stage registered content for visual comparison. They do
not configure recipes, machine inventories, ether flow, staff components,
Jewelry Table state, Teldecore progression, player energy, seal discovery,
combat behavior, generated biomes/structures, multiplayer synchronization, or
save/reload behavior. Record those mechanics with purpose-built test cases;
the presence of an object in this gallery is not evidence that its mechanic
works.
