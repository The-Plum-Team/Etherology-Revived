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
| 1.20.1 | Forge | Active port; bounded Storage and Channel client E2E, shared sound resources, the shared game-event/loot-condition/enchantment/particle/material-item registry foundation, and Common-owned Ether-source reload behavior with dedicated-server proof are accepted; the broader registry, network, and release gates remain closed |
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
particle, behavior-free material-item, and Ether-source reload-listener steps
are complete.
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

`SharedMaterialItems` is the sole Common deferred owner of these 14
behavior-free items:

```text
etheroscope, thuja_oil, azel_ingot, azel_nugget, ethril_ingot,
ethril_nugget, ebony_ingot, ebony_nugget, enriched_attrahite, raw_azel,
attrahite_brick, binder, ebony, resonating_wand
```

Both loaders attach that same catalog without eager supplier resolution.
Every entry has the vanilla `Item` runtime class; `enriched_attrahite` has a
maximum stack count of 16 and the other 13 have a maximum stack count of 64.
The static gate checks the exact owner, fields, IDs, loader attachments,
absence of the former eager fields, and byte-exact models, textures, and
English names across the Common and loader artifacts.

The current cumulative Java 17 Forge 47.4.9 dedicated-server proof used the
fresh repository-owned `etherology-e2e-forge-server-1.20.1-v11` profile and
the `material-item-registry` scenario with a real `reload` command. Report
schema 7 passed all 163 ordered assertions. It re-proved the earlier
game-event, loot-condition, Ether-source, enchantment, and particle states,
then resolved all 14 material items after server-data load, at server start,
and after reload. It checked their exact IDs, vanilla `Item` class, maximum
stack counts, and exact `ItemStack` NBT ID/count/key round trips. The server
saved the world, stopped normally, exited with code zero, and logged no fatal,
forbidden client-startup, or unexpected client-class marker. Because this was
a headless dedicated-server scenario, it produced no screenshots. The
immutable archive is
[`material-item-registry-server-v11`](docs/evidence/forge-1.20.1/material-item-registry-server-v11),
with profile-manifest SHA-256
`63ee2c8707f276cc87df2e0b162b2f3174e1fe1b3d689b26135298154ed1b171`,
report SHA-256
`f3cc85b8514704f6c789e5abbdb835ede8aea062f5bae5b7ade0ab20da26bd4f`,
server-log SHA-256
`ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4`,
and archive-manifest SHA-256
`9f5ff60298d9066e92c3f3e8ff6e5ab97fba5b35369f6ed09e1359bed7afe347`.
The v6 Ether-source, v7 enchantment, and v10 particle archives remain immutable
historical evidence; their accepted states are included in and superseded by
this cumulative v11 run.

This bounded material-item proof is limited to registry properties and
`ItemStack` NBT round-trip/reload stability in one server process. It did not
give items to a player with `/give`, launch a second JVM or restart the server,
or prove Forge fuel registration, creative-tab placement, recipes, client
rendering/gameplay, furnace or machine consumption, the wider Ether network,
the full authoritative registry, or release readiness. The inherited particle,
enchantment, sound, sculk-frequency, Attrahite-drop, and combat caveats also
remain.

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

The canonical Attrahite block/drop graph remains Fabric-only because the full
block and item set has not been ported. The synthetic table proves the shared condition and
serializer, not Attrahite gameplay or drop parity. The historical v2
game-event archive is retained, but the frozen `registry-foundation-server-v4`
archive superseded it as the registry-foundation proof and is now retained
alongside the current v11 cumulative material-item proof and historical v10
particle-registry, v7 enchantment-registry, and v6 Ether-source reload proofs.
Native sound playback and Forge's custom sculk frequency remain
deferred. The fresh repository-owned Fabric
`etherology-e2e-fabric-1.20.1-v21` profile ran the current packaged artifact
after the shared material-item rebuild: its Phase 0 smoke passed 42 of 42
assertions, captured two native 1920x1080 screenshots, and is frozen in
[`phase0-smoke-v21`](docs/evidence/fabric-1.20.1/phase0-smoke-v21). That bounded
client run proves current-artifact startup, integrated-world entry, fixture
mirroring, save, and normal shutdown; the exact 14 material IDs and properties
are enforced by the cross-artifact registry tests rather than direct client
gameplay assertions. The earlier Fabric `v20` archive remains historical.
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
