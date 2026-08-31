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
| 1.20.1 | Forge | Active port; bounded Storage and Channel client E2E, shared sound resources, the shared game-event/loot-condition/enchantment/particle/material-item/metal-block registry foundation, and Common-owned Ether-source reload behavior with dedicated-server proof are accepted; the broader registry, network, and release gates remain closed |
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
are complete. The three behavior-free metal blocks and their placeable block
items are complete as another bounded step.
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

`SharedMetalBlocks` and `SharedMetalBlockItems` are the sole Common deferred
owners of `etherology:azel_block`, `etherology:ethril_block`, and
`etherology:ebony_block` as three vanilla `Block` instances and their exact
`BlockItem` mappings. Azel copies iron-block settings and uses
`MapColor.LAPIS_BLUE`; ethril copies gold-block settings; ebony copies
diamond-block settings and uses `MapColor.ORANGE`. All three require the
correct tool, use the metal sound group, are pickaxe-mineable and require an
iron-tier tool; only ethril and ebony are beacon bases. In the two tag files
packaged by this bounded Forge slice—`mineable/pickaxe` and
`needs_iron_tool`—still-unported IDs are optional while the three accepted
metal-block IDs remain required. `needs_stone_tool` is unchanged and outside
this Forge resource slice.

The current cumulative Java 17 Forge 47.4.9 dedicated-server proof used the
fresh repository-owned `etherology-e2e-forge-server-1.20.1-v13` profile and
the `metal-block-registry` scenario with a real `reload` command. Report
schema 8 passed all 188 ordered assertions. It cumulatively re-proved the
material-item and earlier registry/reload states, then checked the three exact
block and block-item IDs, runtime classes and mappings, block properties,
selected mining/beacon tags, and exact maximum-count `ItemStack` NBT round
trips. It placed the blocks directly in the bounded server world at
`8,200,8`, `9,200,8`, and `10,200,8` and observed the same exact block IDs
after reload. The world then saved, the server stopped normally, and the
launcher exited with code zero. Because this was a headless dedicated-server
scenario, it produced no screenshots. The immutable archive is
[`metal-block-registry-server-v13`](docs/evidence/forge-1.20.1/metal-block-registry-server-v13),
with profile-manifest SHA-256
`c4112b8c4073168af573b4bb555d2f1d775ce57911046aaf352e8f569f10bd11`,
report SHA-256
`b6b48f567fda9f3b170c4bd0407c786123bf0487ef8248216bf92f36b681d452`,
server-log SHA-256
`f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`,
and archive-manifest SHA-256
`0dae07208c3b14bab4a6af4f6a5c71f8c98ba76147cba7da20fb246f3377a9cc`.
The v6 Ether-source, v7 enchantment, and v10 particle archives remain immutable
historical evidence. The v11 material-item archive is the immediate historical
predecessor; all of their accepted states are re-proved by this cumulative v13
run. The v12 profile was consumed by a failed diagnostic launch that exposed
required unported references in those two packaged tag files; it was never
accepted or archived.

This bounded proof is limited to exact registry/classes/mappings/properties,
selected tags, in-process `ItemStack` NBT, direct server-world placement through
reload, and normal save/stop in one process. It did not launch a second JVM or
prove restart persistence, player `/give` or player placement, mining/drop
behavior, beacon activation, recipe execution, creative-tab placement, client
rendering/gameplay, the full authoritative registry, or release readiness. The
inherited particle, enchantment, sound, sculk-frequency, Attrahite-drop, and
combat caveats also remain.

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
alongside the current v13 cumulative metal-block proof, historical v11
material-item proof, and historical v10 particle-registry, v7
enchantment-registry, and v6 Ether-source reload proofs.
Native sound playback and Forge's custom sculk frequency remain
deferred. The fresh repository-owned Fabric
`etherology-e2e-fabric-1.20.1-v22` profile ran the packaged artifact after the
metal-block rebuild. Its Phase 0 smoke passed 42 of 42 assertions in 235 client
ticks, captured two native 1920x1080 screenshots with a
`0.9796228780864198` changed-pixel ratio, and is frozen in
[`phase0-smoke-v22`](docs/evidence/fabric-1.20.1/phase0-smoke-v22). The production
JAR SHA-256 is
`5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd`.
This bounded client run proves capture-time packaged-artifact startup and
rendering, integrated-world entry, the existing four-machine fixture mirror,
save, and normal shutdown. The fixture and screenshots do not show or directly
interact with the three metal blocks. They do not prove material or food
gameplay, mining or drops, beacon behavior, recipes, creative-tab behavior, or
any other unexercised consumer mechanic. The Fabric `v21` material-item and
`v20` SharedSounds archives are immutable historical evidence.

Frozen archives preserve capture-time observations, artifact identity, and
payload integrity; neither the Fabric v22 archive nor the Forge v13 archive
compares or cryptographically binds later sources or rebuilt JARs. Current
static artifact gates are a separate proof boundary. Every later rebuilt JAR
needs another fresh isolated profile before it can claim equivalent runtime
evidence.

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
