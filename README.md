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
| 1.20.1 | Fabric | Active port; build, datagen, unit tests, and six real-client E2E slices pass, including the dedicated Slitherite, metal-block, and Forest Lantern visual proofs; broader parity is pending |
| 1.20.1 | Forge | Active port; bounded Storage, Channel, and Forest Lantern native proofs plus the shared registry/reload foundation and Warp Counter static slice are accepted; the broader registry, network, and release gates remain closed |
| 1.21.1–1.21.11 | Fabric + NeoForge | Declared follow-up branches |
| 26.1, 26.1.1, 26.1.2, 26.2 | Fabric + NeoForge | Declared follow-up branches using the no-remap architecture |

The newest Fabric proof used the fresh repository-owned, now permanently
consumed `etherology-e2e-fabric-1.20.1-v31` profile. Its packaged Slitherite
scenario passed 185 of 185 assertions across all 17 blocks, 1,262 states, 11
tags, 79 visual resources, 29 owned recipes and their 29 advancements, five
related recipes, real placement, button and pressure-plate behavior, and a
complete save/disconnect/reopen lifecycle. The two unedited native screenshots
and full report are frozen in
[`slitherite-block-registry-v31`](docs/evidence/fabric-1.20.1/slitherite-block-registry-v31).
The report SHA-256 is
`e7648183de3bafb8da9c1f31466e78868077b43304af93b16902f10f9bc74e44`,
the initial and reopened screenshot SHA-256 values are
`7f51f94f7564faecffb4b45fcdcbc53b23e0afcc09ee02f016d1f1e361cf8c09`
and
`0dc45779791d6f3eb48af93830b57f510953184780df857289dc3843d0ee957f`,
and the archive-manifest SHA-256 is
`12c12e1d2772ae2a449a2c140e6af77e32f1f8eefb35aec06718eead1a4140c5`.
The v31 identity must never be rerun. The accepted Forge packaged-client v19
Slitherite proof satisfies the real pedestal, alchemy, lens, and complete
five-related-recipe dependency gate for the equivalent dedicated-server
contract. Dedicated-server v20 was consumed by a fail-closed Gradle process-group
handoff rejection before the controller acknowledged the Java 17 server; its
exact failed bytes are retained in
[`slitherite-block-registry-server-v20-pgid-handoff-failure`](docs/evidence/forge-1.20.1/slitherite-block-registry-server-v20-pgid-handoff-failure)
and v20 must never be rerun. The active replacement is the fresh,
never-provisioned and never-launched v21 profile. Its two byte-identical
4,478-byte manifests have SHA-256
`8df3f9c15f03c1ea9d5b6adea71ee352e9a2735ecdd22e26d03033d62f49f764`;
no v21 runtime result or acceptance is claimed yet.

The implemented v21 launch controller acquires and pins one repository-global
native-run lock before any Java version probe or loader-free topology check,
rejects retained active topology or wrapper-guard transients, and creates a
bounded JDK 21 source-file broker as the stable launch session and process-group
leader. The broker starts
no Gradle JVM until the controller has authenticated the anchor, sampled its
physical footprint, and proved that the detached watchdog is ready. It then
releases one exact direct Gradle 9.6.1 wrapper child with a 2-GiB heap cap,
offline arguments, and an allowlisted environment, and remains alive until the
entire launch is quiescent. While awaiting START, the broker requires its exact
controller to remain its live parent and limits that wait to 30 seconds. An
accepted START commits the release; subsequent failures retain the process
group for the watchdog. The controller requires a zero-Java baseline. The
watchdog binds Java processes inside the owned group and session, permits at
most three concurrently, retains at most 16 identities over the launch, and
rejects Java outside that group. Normal cleanup requires exact process/group
absence and terminal watchdog proof of global Java absence; failure cleanup
additionally requires 15 continuous seconds of global absence. Successful
topology and wrapper-guard transients
are atomically renamed to owner-pinned completed diagnostic directories rather
than deleted by pathname. The repository-owned Gradle home imports no user init
or property files, validates the shared cache root and bridges as owner-only,
and pins the extracted Gradle distribution's non-executable `init.d` inventory.
The profile pins the 63,659-byte broker controller source at SHA-256
`8cb41e0ce36d1fa91fd2472b73e54e7e9b44974e5c13c27b692f35ce404699a5`
and the 41,275-byte Java source at SHA-256
`baca2862e9df7dd6c3da1f41583cbc4b013c45423ec77914883961dcfd202d2b`.
The wrapper JAR and wrapper properties complete the four pinned repository
inputs. The Java source, JAR, and properties are staged as three `0400` files
inside the isolated runtime and preserved alongside the five anchor records
when a successful run is archived. The
[v21 sealing workflow](scripts/e2e/README.md#sealing-the-successful-v21-dedicated-server-run)
validates the stopped runtime, copies the exact payload inventory, creates its
manifest once, and verifies the resulting archive without launching Java.
Persistent `strict-2g-v1` monitors protect both the wrapper and Java 17 server,
while the independent watchdog covers the anchor, wrapper, and server as one
owned group. The monitors warn above 3 GiB, stop a sustained breach
above 4 GiB after at least 10 of 15 high samples including all final five, and
emergency-stop after one sample above 5 GiB. Synchronous authenticated checks
also reject any individual Java identity above 5 GiB or the deduplicated Java
set above 6 GiB of current physical footprint. These are prepared safety
controls, not evidence that v21 has run.

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
items are complete as another bounded step. The plain
`etherology:forest_lantern_crumb` food item and the paired
`etherology:forest_lantern` block/item mechanic are also accepted bounded
steps. The original crumb recipes/advancements, age-gated loot, state/shape and
growth behavior, Forge cutout rendering, four-facing placement, and save/reopen
persistence are present and verified. Natural immature attachment remains
coupled to the deferred peach-log/Golden-Forest graph.

`SharedToolItems` is now the sole Common deferred owner of the plain
`etherology:warp_counter` item with maximum stack count 1; Fabric's retained
`ToolItems.WARP_COUNTER` field is only an alias. Forge packages the canonical
static fifteen-frame presentation: the base model with its ordered overrides,
14 child models, textures `warp_counter_0` through `warp_counter_14`, the exact
English `Warp Counter` and Russian `Варп счётчик` names, and the original recipe
and advancement. This static acceptance does not claim the corruption-driven
model predicate, sound playback or the corruption/component graph, native E2E,
or broader gameplay parity. Its positive gate is
`:forge:1.20.1:validateForgeWarpCounterStaticMilestone`; the remainder of the
authoritative registry spine is still the next forward stage.

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

`SharedFoodItems` is the sole Common deferred owner of the plain
`etherology:forest_lantern_crumb` food item. It restores hunger 3 and saturation
modifier 2.0 without always-edible behavior, status effects, or a recipe
remainder. Its exact English name is `Mushroom Crumb` and its Russian name is
`Грибной мякиш`. The packaged model and texture have SHA-256
`6ba61590386580a2f70526313d501eec44cd88ff9d86cd1d13d9092b41a42fbe`
and `44f9d92ccf36c3555d21ace9eea0268e43eb4a8e95f1e81b74f22977d4928d65`
respectively. Its three original recipes and three advancements were deferred
at the v14 food-only checkpoint; the later accepted Forest Lantern slice now
packages and verifies all six without changing their ingredients.

The historical food-only Java 17 Forge 47.4.9 dedicated-server proof used the
fresh repository-owned `etherology-e2e-forge-server-1.20.1-v14` profile and
the `food-item-registry` scenario. Its tracked 1192-byte profile manifest has
SHA-256
`442d11e6a5072c8ec418bced406529dc15caa6d4f4d4c5c68edc8a79ce2e493d`.
Report schema 9 passed all 219 ordered assertions. It cumulatively re-proved the
v13 metal-block and earlier registry/reload states, then used two real
`ServerPlayerEntity` instances named `EtherFoodStart` and `EtherFoodReload`.
Each observed exact `forest_lantern_crumb` registry, class, food-component,
stack, and save-representation state; the reloaded observation was exactly
stable. Native consumption changed hunger from 10 to 13, saturation from 0 to
12, and the stack from 2 to 1 while retaining the same `ItemStack` instance. The
world saved, the server stopped normally, and the launcher exited with code
zero. Because this was a headless dedicated-server scenario, it produced no
screenshots.

The immutable archive is
[`food-item-registry-server-v14`](docs/evidence/forge-1.20.1/food-item-registry-server-v14),
with archive-manifest SHA-256
`eacab05996569a78a55ed21117d2a7e0768d87c258c93757cdc4ab4205881927`,
report SHA-256
`3ccd86d4ef6f5b31fd37b686254bdf427e351019c778d2d8e3f03958de0e1f6c`,
launcher-result SHA-256
`4ec610688bf030ea722772c40d871ec1b954fcbc0b15f90cbad41acec6278ad0`,
completion-marker SHA-256
`37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`,
and server-log SHA-256
`e2bba0e01d27a7f9f4511d00b2d3fa3a12c8d9fa4b5aaa74cca09ca536d599db`.
All 87 pinned v14 Python runner and verifier safety tests pass. Separately,
`:forge:1.20.1:serverProbeSafetyInterlockTest` passes 20 non-Minecraft Gradle
interlock fixture cases and is wired into `forgeFoodItemRegistryServerSafetyTest`.
A sealed archive now
durably consumes its profile: provisioning and environment checks reject it
even if the ignored runtime is removed, while the raw Gradle launch task also
requires the exact runner token, lock, profile marker, and pristine evidence
directories. The token and lock are accidental-misuse interlocks, not
provenance authentication; same-account adversarial or concurrent filesystem
mutation and TOCTOU are outside the bounded threat model.
The strict archive verifier is
`python3 -B scripts/e2e/forge_server_food_item_evidence_v14.py --archive docs/evidence/forge-1.20.1/food-item-registry-server-v14`.
The accepted predecessor task is
`:forge:1.20.1:validateForgeFoodItemRegistryMilestone`.

The v6 Ether-source, v7 enchantment, v10 particle, v11 material-item, and v13
metal-block and v14 food-item archives remain immutable historical evidence;
all of their accepted states are re-proved by the cumulative v16 run. The v12 profile was
consumed by a failed diagnostic launch and was never accepted or archived.
At its checkpoint this bounded proof did not launch a second JVM or prove
restart persistence, multiplayer, the then-deferred recipe/advancement
resources, creative-tab interaction, client rendering/gameplay, the full
authoritative registry, or release readiness. The
inherited particle, enchantment, sound, sculk-frequency, Attrahite-drop, block
interaction, and combat caveats also remain.

The latest accepted cumulative dedicated-server proof is the consumed
`etherology-e2e-forge-server-1.20.1-v19` Attrahite profile. Its schema-11 report
passed 310 of 310 assertions on Java 17 and Forge 47.4.9, including the exact
four-block Attrahite contract and all earlier registry, reload, and Forest
Lantern checks. Its immutable archive is
[`attrahite-block-registry-server-v19`](docs/evidence/forge-1.20.1/attrahite-block-registry-server-v19).
The consumed v20 Slitherite attempt was rejected at its process-group ownership
handoff and does not supersede that evidence. Fresh v21 has not been
provisioned or launched, so it also carries no acceptance. The historical v16
Forest Lantern archive remains immutable.

The consumed Forge packaged-client v13 profile separately passed 69 of 69
assertions in 575 ticks and wrote seven unedited 1920x1080 native framebuffers.
It proves the effective cutout layer, baked models, complete twenty-state
fixture, real four-facing `BlockItem` placement, forced save, full
disconnect/reopen, and exact persistence. Its production and harness JAR
SHA-256 values are
`45a628ddbe67dd90c0d713fa0788caebb17bff071689e2e05b45dbbaed4c8732`
and `2082b006646371e9339467bb59daa80d7d0c62d1102864ba56fa9b668c00dde9`;
the archive is
[`forest-lantern-v13`](docs/evidence/forge-1.20.1/forest-lantern-v13).
The Fabric companion v24 passed 68 of 68 assertions with the same seven-frame
progression. Forge client v12 remains an unaccepted, consumed harness-diagnostic
identity. Any later Fabric client run requires v25 or newer; any later Forge
client run requires v14 or newer.

The active positive static and diagnostic tasks are
`:forge:1.20.1:validateForgeWarpCounterStaticMilestone` and
`:forge:1.20.1:verifyForgePortGateClosed`. The latter reports the broader
authoritative registry spine as the first incomplete forward stage.

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
alongside the latest accepted v19 cumulative Attrahite proof, historical v16
Forest Lantern proof, historical v14 food-item proof, historical v13
metal-block proof, v11 material-item proof, and historical v10 particle-registry, v7
enchantment-registry, and v6 Ether-source reload proofs.
Native sound playback and Forge's custom sculk frequency remain deferred. The
historical repository-owned Fabric `etherology-e2e-fabric-1.20.1-v23` profile ran a
dedicated `metal-block-registry` visual scenario against the packaged artifact.
All 25 ordered assertions passed. From one fixed first-person camera, the run
captured the three empty display pedestals and then the exact client mirror of
server-side placement of `azel_block`, `ethril_block`, and `ebony_block`. Each
unedited native 1920x1080 framebuffer followed 120 consecutive stable renders;
the before/after changed-pixel ratio is `0.087510`. The world force-saved and the
client shut down normally. The immutable archive is
[`metal-block-registry-v23`](docs/evidence/fabric-1.20.1/metal-block-registry-v23).
Its production-JAR SHA-256 is
`5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd`,
its harness-JAR SHA-256 is
`0cc892f41399eec903af57c3270f19db027b0e7611e392a0fc817876e373b111`,
and its archive-manifest SHA-256 is
`69717273eac7b543378aa1a804573e27805e33b771601abba7c49923a5a42f44`.

This bounded client run proves the three exact block IDs and default-state
network IDs, nine render resources, packaged-root provenance, integrated-world
and chunk readiness, exact before/after server-client fixture states, stable
rendering, native captures, and save publication. Direct server-side fixture
placement does not prove `BlockItem` inventory or player placement, mining or
drops, tool-tier enforcement, beacon activation, recipes, creative tabs,
restart persistence, multiplayer, or release readiness. The consumed v23
profile and runtime are immutable. The later accepted v24 Forest Lantern
profile is also consumed; any next client lifecycle action requires a fresh
v25-or-newer profile. The Fabric `v22` Phase 0 smoke, `v21` material-item
smoke, and `v20` SharedSounds smoke remain immutable historical evidence. In
particular, v22 passed 42 of 42 assertions and captured the existing
four-machine fixture, but its screenshots did not show the three metal blocks.

Frozen archives preserve capture-time observations, artifact identity, and
payload integrity; none of the Fabric v24, Forge v16 server, or Forge v13 client
archives compare or cryptographically bind later sources or rebuilt JARs. Current
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
