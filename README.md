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
| 1.20.1 | Forge | Active port; bounded Storage and Channel client E2E, shared sound resources, the shared game-event/loot-condition/enchantment/particle registry foundation, and Common-owned Ether-source reload behavior with dedicated-server proof are accepted; the broader registry, network, and release gates remain closed |
| 1.21.1–1.21.11 | Fabric + NeoForge | Declared follow-up branches |
| 26.1, 26.1.1, 26.1.2, 26.2 | Fabric + NeoForge | Declared follow-up branches using the no-remap architecture |

Forge artifacts are intentionally blocked until the remaining gameplay,
client, native-loader, and E2E parity gates pass. See
[`BUILDING.md`](BUILDING.md), the
[`Forge 1.20.1 completion plan`](docs/porting/FORGE-1.20.1-COMPLETION-PLAN.md),
the frozen [Fabric 1.20.1 runtime evidence](docs/evidence/fabric-1.20.1/README.md),
and the bounded [Forge 1.20.1 runtime evidence](docs/evidence/forge-1.20.1/README.md).
The next Forge forward gate is the remainder of the authoritative registry
spine. Its bounded shared-sound, game-event, loot-condition, enchantment,
particle, and Ether-source reload-listener steps are complete.
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

Common is now the sole implementation and resource owner for the
`EtherSourceLoader` server-data listener and its default `ether_sources` data.
`SharedEnchantments` is the sole Common deferred owner of exactly
`etherology:peal` and `etherology:reflection`, backed by the canonical
`ru.feytox.etherology.registry.misc.PealEnchantment` and
`ru.feytox.etherology.registry.misc.ReflectionEnchantment` classes. Peal has
maximum level 3, minimum powers `1, 12, 23`, and maximum powers `21, 32, 43`;
Reflection has maximum level 1, minimum power `1`, and maximum power `21`. Both
are the only Etherology entries in the singular vanilla
`minecraft:non_treasure` tag.

`SharedParticleTypes` is the sole Common deferred owner of the exact 22
Etherology particle-type IDs. It preserves all eight payload families, the
canonical codecs and command/packet formats, the seal order/colors/textures,
and `shouldAlwaysSpawn = false` across both loaders. The item payload parser
accepts namespaced IDs such as `minecraft:diamond`.

The current cumulative Java 17 Forge 47.4.9 dedicated-server proof used the
fresh repository-owned `etherology-e2e-forge-server-1.20.1-v10` profile and a
real `reload` command. Report schema 6 passed all 138 ordered assertions. It
reproved the earlier game-event, loot-condition, Ether-source, and enchantment
states, then proved the exact particle registry, payload factory, codec,
command, packet, and seal contracts at data load, server start, and after the
reload. The server saved the world, stopped normally, exited with code zero,
and logged no fatal, forbidden client-startup, or unexpected client-class
marker. Because this was a headless
dedicated-server scenario, it produced no screenshots. The immutable archive
is [`particle-registry-server-v10`](docs/evidence/forge-1.20.1/particle-registry-server-v10),
with profile-manifest SHA-256
`5b3def0df2aacfea5db04b92975925c25223f117941ceb576cc6b3e6616f14e4`,
report SHA-256
`ab829d182e648385f6052fea469bef3a18a6a972f0baa98be6b83569897f3d75`,
server-log SHA-256
`44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f`,
and archive-manifest SHA-256
`29fddf549c4b8728911fe1d048816d0353256ef7a7e533b62d0461249c485ed1`.
The v6 Ether-source and v7 enchantment archives remain historical evidence;
their accepted state is included in and superseded by this cumulative v10 run.

This bounded proof does not register Forge client particle factories or prove
particle rendering, enchantment applicability, Peal's shockwave behavior,
projectile reflection, client visuals or screenshots, furnace or machine
consumption, the wider Ether network, the full authoritative registry, native
sound playback, Forge custom sculk-frequency behavior, Attrahite drops, combat
parity, or release readiness.

The historical registry foundation has both exact cross-loader artifact gates and a real
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
archive superseded it as the registry-foundation proof and is now retained
alongside the current v10 cumulative particle-registry proof and historical v7
enchantment-registry and v6 Ether-source reload proofs. Native sound playback and Forge's custom sculk frequency remain
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
