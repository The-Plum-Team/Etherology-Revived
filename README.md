**English** | [Russian](README-ru.md)

# Etherology Revived

Etherology Revived is a multi-version, multi-loader continuation of
[feytox/Etherology](https://github.com/feytox/Etherology). The repository uses
Stonecutter version branches with loader-neutral Common code and thin loader
modules. The exact branch, Java, loader, and API roadmap is tracked in
[`release/support-catalog.json`](release/support-catalog.json) and explained in
[`docs/porting/VERSION-MATRIX.md`](docs/porting/VERSION-MATRIX.md).

## Port status

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.20.1 | Fabric | Active port; build, datagen, unit tests, and three real-client E2E slices pass; broader parity is pending |
| 1.20.1 | Forge | Active port; bounded Storage and Channel client E2E, shared sound resources, and the shared game-event/loot-condition registry foundation with dedicated-server proof are accepted; the broader registry, network, and release gates remain closed |
| 1.21.1–1.21.11 | Fabric + NeoForge | Declared follow-up branches |
| 26.1, 26.1.1, 26.1.2, 26.2 | Fabric + NeoForge | Declared follow-up branches using the no-remap architecture |

Forge artifacts are intentionally blocked until the remaining gameplay,
client, native-loader, and E2E parity gates pass. See
[`BUILDING.md`](BUILDING.md), the
[`Forge 1.20.1 completion plan`](docs/porting/FORGE-1.20.1-COMPLETION-PLAN.md),
the frozen [Fabric 1.20.1 runtime evidence](docs/evidence/fabric-1.20.1/README.md),
and the bounded [Forge 1.20.1 runtime evidence](docs/evidence/forge-1.20.1/README.md).
The next Forge forward gate is the remainder of the authoritative registry
spine. Its bounded shared-sound, game-event, and loot-condition steps are
complete.
`SharedGameEvents` is the sole Common deferred owner of
`etherology:etherology_resonance`, whose internal ID is
`etherology_resonance` and whose range is 16. Both loaders package exact
membership in the vanilla `vibrations` and `warden_can_listen` tags. Fabric
uses its supported `SculkSensorFrequencyRegistry` hook for frequency 10; Forge
47 has no supported equivalent, so custom sculk-frequency behavior remains
deferred instead of being approximated. `SharedLootConditions` is likewise the
sole Common deferred owner of
`etherology:random_chance_with_fortune`, backed by the canonical
`RandomChanceWithFortuneConditionSerializer` on both loaders.

The registry foundation has both exact cross-loader artifact gates and a real
Java 17 Forge 47.4.9 dedicated-server run in the fresh repository-owned
`etherology-e2e-forge-server-1.20.1-v4` Loom-userdev profile. The headless
`registry-foundation` run passed 39 of 39 assertions. Separately, all 63 Python
runner and evidence safety tests pass. The run proved the sole game event and its two tags, the
sole Fortune-scaled condition type and serializer, and a synthetic table whose
sorted outputs were `[minecraft:gold_ingot, minecraft:stone]` for an empty tool and
`[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` for Fortune I, including the mixed
`0.99 + 0.01` case. It also saved the world, stopped normally, exited with code
zero, and contained no `ERROR` or `FATAL` marker. This server-only proof creates
no screenshots.

The canonical Attrahite loot-table resource remains Fabric-only because its
items have not been ported. The synthetic table proves the shared condition and
serializer, not Attrahite gameplay or drop parity. The historical v2
game-event archive is retained, but the frozen `registry-foundation-server-v4`
archive supersedes it as the current registry proof. Native sound playback and Forge's custom sculk frequency remain
deferred. The earlier Fabric `v20` packaged client evidence predates this
registry rebuild and does not claim equality with the current Fabric artifact.
Frozen archives preserve capture-time observations and payload integrity; the
current-artifact gates are separate. Every later rebuilt JAR needs another
isolated native run before it can claim equivalent runtime evidence.

## About Etherology

**Etherology** is a fabulous mod that plays on immersion and disclosure of its *global lore*. This project is bound to give you an unforgettable feeling of exploring a new world with detailed *mechanics* and eye-pleasing *vanilla* design.

Immerse yourself in the study of ancient knowledge and try to curb all the mysteries of the **Ether**! Discover new technologies, alchemical crafting recipes, create your own staff and distort reality as you please! If you wish, visit the ***Golden Forest*** - a fabulous forest of yellow-leaved trees, or dive into the bowels of the earth in search of a source of *unimaginable power*!

Etherology is not strict on attention, play for fun, and the mod will only *sweeten* your stay in the game. But if you are ready to discover this world in a new way - *go for the truth*!

## Fabric requirements
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [oωo (owo-lib)](https://modrinth.com/mod/owo-lib)
- [Cardinal Components API](https://modrinth.com/mod/cardinal-components-api)
- [Geckolib](https://modrinth.com/mod/geckolib)
- [Trinkets](https://modrinth.com/mod/trinkets)
- [Biolith](https://modrinth.com/mod/biolith)
- [Fabric Shield Lib](https://modrinth.com/mod/fabricshieldlib)

### Optional dependencies:
- [Mod Menu](https://modrinth.com/mod/modmenu)
- [EMI](https://modrinth.com/mod/emi) / [Roughly Enough Items (REI)](https://modrinth.com/mod/rei)

## Special thanks
It's no secret that this mod was inspired by another similar project - *Thaumcraft*. We respect Azanor's work for his wonderful work, and we aim to *rethink* many successful ideas of that time so that even years later you can return to that vast world.

## Contributing
This project is under development and a lot of things can be changed. See a bug, an issue or want to fix/refactor something? We welcome any contributions to improve **Etherology**.

## Mod guide
[Guide on Github Wiki](https://github.com/feytox/Etherology/wiki/Guide)

## Discord
- [Developer's Server](https://discord.gg/U23C6ewP2X)
- [Author's Server](https://discord.com/invite/HruRuhw)


##### Have a nice game!
