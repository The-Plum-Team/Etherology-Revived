# Etherology 0.1.7 visual gallery datapack

This datapack stages a compact, repeatable visual gallery for the published
Etherology `1.21-0.1.7` Fabric build on Minecraft 1.21.1. Its Etherology block
and item identifiers were checked against upstream commit
`cfd75166248049c8f20ad1172e99603c884a551e`.

The gallery has a fixed origin at `(0, 96, 0)` and occupies the inclusive
bounding box `(0, 96, 0)` through `(58, 105, 10)`. Run the functions from the
Overworld; commands act in the executor's current dimension. Setup functions
erase and rebuild their own bay, while full setup and `gallery/clear` erase the
complete bounding box. Use a disposable creative test world whose builds do
not overlap that area.

## Install

The baseline launcher's `world` action provisions the validated gallery save
with this datapack already installed, enabled, and hash-checked. The manual
steps below are only for a different disposable reference world.

Copy this directory into the isolated reference world's `datapacks` directory
under a distinct pack name. That produces:

```text
~/Library/Application Support/ModrinthApp/profiles/Etherology E2E 1.21.1/saves/<world>/datapacks/etherology_baseline/pack.mcmeta
```

The directory containing `pack.mcmeta` must be directly below `datapacks`.
Start the world or run `/reload`, then confirm the pack is enabled:

```mcfunction
/datapack list enabled
```

The world must run Minecraft 1.21.1, Etherology `1.21-0.1.7`, and that mod's
required Fabric dependencies. Commands require cheats or operator permission.

## Use

Build or reset all five bays, then move to the fixed overview position:

```mcfunction
/function etherology_baseline:gallery/setup
/function etherology_baseline:gallery/teleport
```

Each bay can also be reset independently:

```mcfunction
/function etherology_baseline:gallery/decorative_worldgen
/function etherology_baseline:gallery/functional_machines
/function etherology_baseline:gallery/seals_aspects
/function etherology_baseline:gallery/storage_utilities
/function etherology_baseline:gallery/combat_equipment
```

Remove the gallery and only the armor stands tagged by this pack:

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
