# Forge 1.20.1 gameplay completion plan

This document is the implementation plan for turning the native Forge 1.20.1 bootstrap into a
complete Etherology gameplay port. It is intentionally stricter than a compilation checklist:
each slice must own a coherent mechanic, preserve the canonical registry identifiers and save
semantics, and finish with deterministic acceptance. Every gameplay behavior must also have native
runtime evidence before the release gate advances.

The [multi-version testing policy](VERSION-MATRIX.md#testing-policy) retains the original
published-0.1.7 and 1.20.1 foundational native evidence. Later lanes receive build, static, and
mechanic tests while porting. After all release branches are materialized, run their batched
visual E2E matrix on a reliable graphical macOS/self-hosted runner. Local visual reruns are
reserved for renderer/API boundaries or failures; ordinary headless Linux CI is not
screenshot acceptance. The source-0.1.8 tree is source/build context only, not the original
captured runtime identity.

The shared Ether item, bounded Ethereal Storage vertical, bounded Ethereal Channel foundation,
bounded SharedSounds registry/resource milestone, bounded SharedGameEvents/SharedLootConditions
registry/server foundation, bounded Common Ether-source reload path, and bounded
SharedEnchantments, SharedParticleTypes, SharedMaterialItems, SharedMetalBlocks, and
SharedFoodItems registry/server milestones, the bounded SharedToolItems Warp Counter static
milestone, plus the bounded SharedForestLanternBlocks and SharedForestLanternBlockItems
mechanic/server/packaged-client milestone, are
accepted, not the finished port.
Storage now has canonical per-Glint Ether arithmetic, native Forge item-handler capability
lifecycle, synchronized Gecko animation, and
packaged save/restart/reopen proof. The channel foundation now has directed fifth-tick transfer,
redstone gating, exact `0.2` evaporation, storage endpoints, native Forge lever support, and its own
packaged save/restart proof. SharedSounds closes the exact Common sound declarations and packaged
resource inventory, but does not claim native playback. SharedGameEvents closes the sole deferred
resonance declaration and both vanilla listening tags. SharedLootConditions closes the sole
deferred `random_chance_with_fortune` declaration and serializer. One real Forge dedicated-server
registry-foundation proof covers both registries. A separate real reload proof covers the
Common-owned Ether-source listener and default data, now re-proved cumulatively by the current v19
server record. SharedEnchantments closes the two canonical enchantment declarations, concrete
types, and exact non-treasure tag on both loaders; enchanting applicability and the Peal/reflection
combat mechanics are not part of that acceptance. SharedParticleTypes closes the exact 22 particle
declarations and their server payload/codec/wire contracts; Forge client factories, rendering, and
particle-emitting mechanics are not part of that acceptance. SharedMaterialItems closes 14
behavior-free item declarations, their exact packaged assets/names, registry properties, and
in-process `ItemStack` NBT round-trip/reload stability. Player `/give`, a second JVM/restart,
Forge fuel registration, creative-tab placement, recipes, and client gameplay are not part of
that acceptance. SharedMetalBlocks and SharedMetalBlockItems close exactly `azel_block`,
`ethril_block`, and `ebony_block` as behavior-free blocks and their mapped block items. Their
bounded server proof covers registry/classes/mappings/properties, selected tags, stack NBT, direct
world placement through reload, and normal save/stop; it does not prove restart persistence,
player `/give` or placement, mining/drop behavior, beacon activation, recipe execution, creative
tabs, or client rendering. `SharedFoodItems` closes the plain `forest_lantern_crumb` declaration,
exact hunger 3 and saturation modifier 2.0 behavior, packaged model/texture/translations, and
native player consumption through reload. `SharedForestLanternBlocks` and
`SharedForestLanternBlockItems` now close the Forest Lantern declaration, state/shape/growth,
support, jump, loot, recipe/advancement, render-resource, cutout, placement, save/reopen, and
bounded native client/server contracts. Natural immature attachment remains coupled to the
deferred peach-log/Golden-Forest graph, and multiplayer is not claimed. `SharedToolItems` closes
only the plain maximum-stack-one Warp Counter declaration and its exact packaged static visual,
translation, recipe, and advancement resources. The corruption-driven predicate and its sound,
component, and corruption graph remain deferred with native E2E. The broader authoritative
registry spine is still the first incomplete forward
milestone. Broad content migration must follow the ownership and dependency order below.

## Audit snapshot

The initial audit found 353 canonical main Java files and 211 client Java files. After the accepted
sole-owner moves, including Primoshards, tool materials, pattern tablets, and their acquisition hooks, the active
canonical roots contain 279 main and 211 client files, while Common contains 130 main files,
Forge contains 18, and the Fabric adapter module contains 12. The
initial direct loader/vendor scan found 22 bound main owners and 64 bound client owners. In that
initial snapshot, removing the full REI, EMI, and Fabric datagen families left 23 directly bound
required-client owners, and the explicit-import reverse closure from those bound roots reached 367
of the original 564 canonical files because the initializer, registry catalogs, and packet types
were central dependencies. Those closure figures are historical scope evidence, not current
post-move counts.

This has two consequences:

1. The canonical source roots must not be added wholesale to the Forge source set.
2. Porting isolated classes without their registry, state, networking, and client lifecycle will
   repeatedly produce shells that compile but do not implement the mechanic.

Loader-neutral behavior belongs in the Stonecutter common lane. Fabric and Forge retain small
entrypoint, lifecycle, capability, tracking-recipient, and client-registration adapters. The
canonical Fabric graph remains authoritative until each bounded slice is accepted.

## Registry ownership rule

`SharedItems`, `SharedBlocks`, and `SharedBlockEntities` are temporary catalogs used to establish
the loader-neutral lifecycle without replacing canonical Fabric classes. They cannot remain a
second permanent catalog for the same identifiers. `SharedSounds`, `SharedGameEvents`,
`SharedLootConditions`, `SharedEnchantments`, `SharedParticleTypes`, `SharedMaterialItems`,
`SharedMetalBlocks`, `SharedMetalBlockItems`, and `SharedFoodItems` are
the accepted parts of the authoritative registry spine and the single Common declaration owners
for their IDs. Forge attaches them before
lifecycle registry events, while Fabric attaches the same owners from its canonical initializer.
The legacy eager `EtherSounds`, `EventsRegistry`, `LootConditions`, and `EtherParticleTypes`
owners are removed, the accepted material fields are removed from `EItems` and `DecoBlockItems`,
and the legacy concrete enchantment and particle-effect classes moved to Common. The three former
eager metal-block fields are removed from `DecoBlocks`. Fabric's legacy
`EItems.FOREST_LANTERN_CRUMB` field aliases the shared supplier instead of registering a second
item. `EtherEnchantments` remains only a
policy/gameplay consumer of `SharedEnchantments`, not another registry owner. Before broad content
migration, converge the remaining catalogs on one active
declaration owner for every registry ID and attach that declaration once per loader.

The Ether-source data path follows the same one-owner rule outside a registry:
`EtherSourceLoader`, its deserializer and state holder, `ResourceReloaders`, and
the default `ether_sources` resource are Common-owned. Fabric and Forge may
install that idempotent listener, but neither may copy its implementation or
shadow its default data.

The convergence must replace eager `Registry.register` calls and eager construction through
`RegistrableBlock` and `EBlock` with Architectury `DeferredRegister` and `RegistrySupplier`.
Suppliers must not be dereferenced while declarations are still being constructed. This is
especially important for block-entity builders and the generated arrays for seals, sedimentary
stones, and furniture blocks.

Each accepted registry slice must compare its ID manifest with Fabric, reject duplicate IDs, and
prove that no canonical Fabric class is shadowed by the transformed common JAR.

## Execution order

### Slice 0: build and authoritative registry spine — current forward gate

This is the current forward milestone. Its bounded shared-sound, game-event, loot-condition,
Ether-source reload, enchantment-registry, particle-registry, behavior-free material-item, and
behavior-free metal-block, food-item, and Warp Counter static steps are accepted. The broader
catalogs and lifecycle hooks in this slice remain the first incomplete work.

Source owners:

- Blocks: accepted Common `SharedMetalBlocks`; remaining `ExtraBlocksRegistry`, `DecoBlocks`,
  `EBlocks`, `DevBlocks`, and `EBlockFamilies` declarations and behavior.
- Items: Common `SharedMaterialItems`, `SharedMetalBlockItems`, `SharedFoodItems`,
  `SharedToolItems` (Warp Counter plus five Ebony tools), `SharedLensItems`,
  `SharedPrimoShardItems`, and `SharedPatternTabletItems`; the remaining `EItems`, `ToolItems`,
  `ArmorItems`, `DecoBlockItems`, and `EItemGroups` declarations and behavior. Static item
  foundations do not accept their deferred native gameplay or unported consumer graphs.
- Other registries: accepted Common `SharedSounds`, `SharedGameEvents`,
  `SharedLootConditions`, `SharedEnchantments`, and `SharedParticleTypes`; remaining
  `EntityRegistry`, `RecipesRegistry`, `ScreenHandlersRegistry`, `EffectsRegistry`,
  `TreesRegistry`, and `WorldGenRegistry`.
- Enchantment consumers, not registry owners: Fabric `EtherEnchantments`, `ShockwaveUtil`, and the
  applicability/projectile mixins remain part of later gameplay work.
- Eager helpers: `RegistrableBlock` and `EBlock`.

Accepted bounded sound foundation:

- `SharedSounds` is the single Common declaration owner for the exact 14 canonical sound-event
  IDs. Forge attaches it before lifecycle registry events without eager supplier resolution.
- Fabric attaches `SharedSounds` directly from its canonical initializer, and all canonical sound
  consumers resolve its suppliers only when they need to play an event. Forge attaches the same
  owner through `EtherologyBootstrap`.
- The Common, Fabric-transformed Common, Forge-transformed Common, remapped Fabric production, and
  Forge shadow artifacts preserve `SharedSounds` and exclude the removed eager `EtherSounds`
  owner.
- The packaged resource inventory is exactly 21 hash-locked mono 44.1 kHz OGG files, with exact
  per-event `sounds.json` mappings and attenuation entries and complete English subtitle
  references.
- `validateForgeSoundRegistryMilestone` consumes the Common, Fabric, and Forge tests and all
  declaration/production artifact boundaries plus the accepted Channel implementation and
  immutable archive. No native
  sound-playback E2E was run; later consumer mechanics must prove playback.

Accepted bounded game-event and loot-condition foundation:

- `SharedGameEvents` is the sole Common deferred declaration owner for
  `etherology:etherology_resonance`. The event's internal ID is `etherology_resonance`, its range
  is 16, and neither loader resolves the supplier while declarations are being built.
- Both the remapped Fabric production artifact and Forge shadow artifact package exact
  `minecraft:vibrations` and `minecraft:warden_can_listen` tag resources containing the resonance
  event. The removed eager `EventsRegistry` owner is absent from every checked artifact.
- Fabric alone uses the supported `SculkSensorFrequencyRegistry` API to assign frequency 10.
  Forge 47 has no supported equivalent API, so the Forge custom sculk-frequency behavior remains
  explicitly deferred instead of mutating vanilla internals or claiming an approximation.
- `validateForgeGameEventRegistryMilestone` checks exact ownership, multiplicity, constructor and
  range data flow, bootstrap paths, resources, and prohibited registration alternatives across
  Common, both transformed Common JARs, the Fabric development and remapped production JARs, and
  the Forge shadow JAR.
- `SharedLootConditions` is the sole Common deferred owner of
  `etherology:random_chance_with_fortune`. Both loaders bind it to
  `ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer`, with exact
  ownership checked across the Common and loader artifact boundaries.
- At the v4 checkpoint, the canonical Attrahite block/drop graph was Fabric-only because its
  block and item set had not yet been ported. That probe-owned synthetic table proves the
  condition and serializer, not Attrahite gameplay or drop parity. The later accepted v19
  dedicated-server and v17 packaged-client archives provide the separate bounded Attrahite proof.
- `validateForgeRegistryFoundationMilestone` combines the sound, game-event, and loot-condition
  artifact gates with current probe isolation checks, the 63 runner/verifier safety tests, and the
  immutable `registry-foundation-server-v4` evidence archive.
- The fresh repository-owned `etherology-e2e-forge-server-1.20.1-v4` profile ran the exact
  `:forge:1.20.1:runRegistryFoundationServerProbe` Loom-userdev task on a real Java 17 Forge 47.4.9
  dedicated server. Its headless `registry-foundation` scenario passed 39 of 39 ordered assertions
  covering the full loaded-mod inventory, an empty forbidden intersection, the exact sole game
  event and its two tags, and the exact sole loot-condition type and serializer.
- The synthetic table returned `[minecraft:gold_ingot, minecraft:stone]` with an empty tool and
  `[minecraft:diamond, minecraft:gold_ingot, minecraft:stone]` with Fortune I. Its pools include
  chance `1`, chance `0` plus Fortune multiplier `1`, and the mixed chance `0.99` plus multiplier
  `0.01` cases.
- The runner additionally required a saved world, normal `stop(false)`, process exit code zero,
  and no `ERROR` or `FATAL` log marker.
- The probe publishes its report only after observing `ServerStoppedEvent`. It then schedules a
  probe-only terminator that joins the actual stopped-event server thread before `System.exit`,
  working around a proven Loom-userdev non-daemon thread leak. The external runner validates the
  report, log, world, lifecycle, and exit result. Production Etherology contains no such exit path.
- This is a headless registry proof, so it produces no screenshots. It does not satisfy native
  sound playback, Forge's deferred custom sculk frequency, or the full-catalog placement/save
  smoke. The immutable v2 game-event archive remains historical evidence; v4 superseded it as the
  registry-foundation proof. The repository-owned Fabric `v23` profile exercised the packaged
  client artifact with a dedicated metal-block visual scenario. Its 25 ordered assertions passed;
  two native 1920x1080 captures recorded the exact empty and populated fixtures after 120 stable
  renders each, with changed-pixel ratio `0.087510`. This proves exact client mirroring and
  rendering of direct server-side azel, ethril, and ebony placement, not player placement, mining,
  drops, beacons, recipes, creative tabs, restart persistence, multiplayer, or release readiness.
  The later Fabric `v24` Forest Lantern profile passed 68 assertions and seven native
  1920x1080 captures spanning the empty, twenty-state, cumulative-facing, and reopened fixtures.
  Its consumed identity is immutable and any next run requires v25 or newer. The Fabric `v23`
  metal-block and `v22` Phase 0 archives remain historical; the latter's four-machine screenshots
  did not show the metal blocks. The immutable Fabric `v21` and `v20` archives likewise remain
  historical. These archives bind capture-time profile, artifact, report, and screenshot
  identities, not later sources or rebuilt JARs. Current cross-loader declarations remain a
  separate static artifact proof.

Accepted bounded Ether-source reload foundation:

- Common is the sole implementation/resource owner of `EtherSourceLoader`, its deserializer and
  state, `ResourceReloaders`, and the default `ether_sources` data. Both loaders install that same
  idempotent listener.
- `validateForgeEtherSourceReloadMilestone` combines exact Common/Fabric/Forge ownership and
  packaged-resource checks with the immutable v6 dedicated-server archive.
- The fresh repository-owned `etherology-e2e-forge-server-1.20.1-v6` profile ran a real `reload`
  on Java 17 and Forge 47.4.9. Its schema-4 report passed 72 of 72 assertions: exactly 23 initial
  entries with corrected `etherology:primoshard_rella = 4` and `minecraft:redstone = 2`, then
  exactly 24 entries with `minecraft:redstone = 9.5` and added `minecraft:diamond = 13`.
- The game-event registry and tags stayed stable. The loot-condition registry and evaluated
  behavior stayed stable while the probe `LootTable` instance was replaced. The world saved, the
  server stopped normally, the process exited zero, and the log contained no `ERROR` or `FATAL`
  marker. This headless proof has no screenshots.
- The archive at `docs/evidence/forge-1.20.1/ether-source-reload-server-v6` binds profile-manifest
  SHA-256 `2e6b937169d7bf8d765d181de93837371fb32940b31a480f5fde9620d96d21f0`,
  server-log SHA-256 `0be91a9c231e12d00066a2924ba755820da0a8be9f3ef654bb243a375ee5628f`,
  and archive-manifest SHA-256
  `6d552536f74c018ce56e238fcb5a3aacd8fa363c76293863514adf9d7bafc2e0`.
- The v6 archive remains immutable historical evidence for the Ether-source slice. The cumulative
  v7 enchantment-registry, v10 particle-registry, and v11 material-item archives below are
  historical; v13 is the historical metal-block predecessor, v14 is the historical food-item
  predecessor, and v16 is the historical Forest Lantern dedicated-server proof. The accepted
  [Attrahite server v19 archive](../evidence/forge-1.20.1/attrahite-block-registry-server-v19/)
  is the current cumulative proof and re-proves those earlier assertions.
- The v6 capture does not prove furnace or machine consumption, the wider Ether network, the full
  registry, native sound playback, Forge custom sculk frequency, Attrahite drops, or release readiness.

Accepted bounded enchantment-registry milestone:

- `SharedEnchantments` is the sole Common deferred declaration owner for exactly
  `etherology:peal` and `etherology:reflection`. Their concrete classes are exactly
  `ru.feytox.etherology.registry.misc.PealEnchantment` and
  `ru.feytox.etherology.registry.misc.ReflectionEnchantment`.
- Peal is `COMMON`, targets `WEAPON`, occupies `MAINHAND`, has maximum level 3, minimum powers
  `[1, 12, 23]`, and maximum powers `[21, 32, 43]`. Reflection is `COMMON`, targets `BREAKABLE`,
  occupies `MAINHAND` and `OFFHAND`, has maximum level 1, minimum power `[1]`, and maximum power
  `[21]`.
- Fabric directly attaches `SharedEnchantments` from its canonical initializer, without invoking
  the Common bootstrap, while Forge attaches it through `EtherologyBootstrap` before registry
  events. Fabric's retained `EtherEnchantments` class owns only policy and gameplay consumption.
- `validateForgeEnchantmentRegistryStaticMilestone` accepts exact ownership, construction,
  supplier lifecycle, and artifact isolation across Common, both transformed Common artifacts,
  Fabric development/remapped production, and the Forge shadow artifact. The loader artifacts
  contain the exact additive singular resource
  `data/minecraft/tags/enchantment/non_treasure.json` (`minecraft:non_treasure`) with
  `replace: false` and exactly the two Etherology enchantment IDs.
- `validateForgeEnchantmentRegistryMilestone` combines that static/resource gate with the immutable
  [`enchantment-registry-server-v7`](../evidence/forge-1.20.1/enchantment-registry-server-v7)
  archive. A fresh `etherology-e2e-forge-server-1.20.1-v7` Java 17 Forge 47.4.9 dedicated server
  ran the `enchantment-registry` scenario through
  `:forge:1.20.1:runRegistryFoundationServerProbe`; schema 5 passed all 95 ordered assertions.
- The v7 run captured both enchantments after server-data load, rechecked the same registry state
  at `ServerStartedEvent`, performed a real `reload`, and proved exact IDs, classes, powers,
  `minecraft:non_treasure` membership, object/registry identity, properties, and tag membership
  remained stable. Its cumulative assertions also re-proved the prior game-event/tags,
  loot-condition registry and evaluated behavior, and initial/reloaded Ether-source maps.
- The exact lifecycle was `tags_updated_initial > server_started > reload_requested >
  tags_updated_reload > reload_command_returned > stop_requested > server_stopping >
  server_stopped`. The world saved, `stop(false)` completed, the process exited zero, and the log
  contained no `ERROR` or `FATAL` marker. This headless proof produces no screenshots.
- The archive binds profile-manifest SHA-256
  `36b0f67d7ef55cd8e34aac92dd4e5866e17d5be4ffa91987793c913dd60f5773`, report SHA-256
  `1f5209c53fab524db662e7e7ef8ba044ba773fc9800dc3d3086e840093dc5aef`, server-log SHA-256
  `b4be8474c32062765fc5915993d28fe209315354a5b139ccf8478b1cacbbb12c`, and archive-manifest
  SHA-256 `377acc9241417a169ac2f9dbe1f555d5918509a486daf40d89533de4d414feec`.
- This milestone does not prove enchanting-table, anvil, book, or item applicability; the Peal
  shockwave; projectile reflection; client visuals or screenshots; full combat parity; or release
  readiness. Those remain later consumer-mechanic and native-client/full-E2E work.

Accepted bounded particle-registry milestone:

- `SharedParticleTypes` is the sole Common deferred declaration owner for exactly 22 canonical
  particle-type IDs. Fabric attaches it before the retained initializer path and Forge attaches it
  through `EtherologyBootstrap`; the eager `EtherParticleTypes` registry owner is removed.
- The moved Common types preserve exactly eight payload families, their concrete type classes,
  `shouldAlwaysSpawn = false`, codecs, parameter factories, command strings, packet/codec round
  trips, and the canonical seal order, colors, and textures. `ItemParticleEffect` uses
  `Identifier.fromCommandInput` and accepts namespaced item IDs such as `minecraft:diamond`.
- `validateForgeParticleRegistryStaticMilestone` checks exact ownership, multiplicity, bootstrap,
  supplier lifecycle, parser bytecode, consumer access, and isolation across Common, both
  transformed Common artifacts, Fabric development/remapped production, and the Forge shadow
  artifact.
- `validateForgeParticleRegistryMilestone` combines that gate with the immutable
  [`particle-registry-server-v10`](../evidence/forge-1.20.1/particle-registry-server-v10) archive.
  A fresh `etherology-e2e-forge-server-1.20.1-v10` Java 17 Forge 47.4.9 dedicated server ran the
  `particle-registry` scenario through `:forge:1.20.1:runRegistryFoundationServerProbe`; schema 6
  passed all 138 ordered assertions.
- The v10 run captured the exact registry and wire state after server-data load, rechecked it at
  server start, performed a real `reload`, and proved the IDs, types, factories, codecs, sample
  strings, packet/codec round trips, spawn flags, and seal metadata remained stable. Its cumulative
  assertions re-proved the prior enchantment, game-event/tag, loot-condition, and Ether-source
  contracts. The world saved, `stop(false)` completed, the process exited zero, and the log was
  clean of fatal or forbidden client-startup markers.
- The archive binds profile-manifest SHA-256
  `5b3def0df2aacfea5db04b92975925c25223f117941ceb576cc6b3e6616f14e4`, report SHA-256
  `ab829d182e648385f6052fea469bef3a18a6a972f0baa98be6b83569897f3d75`, server-log SHA-256
  `44db5078f575b7f561728652371bc857affff52f6a19ead6290653b906b1609f`, and archive-manifest
  SHA-256 `29fddf549c4b8728911fe1d048816d0353256ef7a7e533b62d0461249c485ed1`.
- This server-only milestone does not install or exercise Forge client factories/renderers, emit
  particles through gameplay, or produce screenshots. Those remain later native-client and
  mechanic-level evidence.

Accepted bounded material-item-registry milestone:

- `SharedMaterialItems` is the sole Common deferred declaration owner for exactly
  `etheroscope`, `thuja_oil`, `azel_ingot`, `azel_nugget`, `ethril_ingot`, `ethril_nugget`,
  `ebony_ingot`, `ebony_nugget`, `enriched_attrahite`, `raw_azel`, `attrahite_brick`, `binder`,
  `ebony`, and `resonating_wand`.
- All 14 declarations produce vanilla `Item` instances. `enriched_attrahite` has maximum stack
  count 16; the other 13 have maximum stack count 64. Fabric attaches the owner directly before
  its retained item initializer, and Forge attaches it through `EtherologyBootstrap` before
  registry events.
- `validateForgeMaterialItemRegistryStaticMilestone` checks exact Common ownership, supplier
  fields, ID order, lazy declaration, loader attachments, and absence of the former eager
  `EItems` and `DecoBlockItems` fields. Across the Common JAR, both transformed Common JARs,
  Fabric development/remapped production JARs, and the Forge shadow JAR it requires exactly one
  `SharedMaterialItems` class, rejects direct registration or eager supplier resolution inside
  that owner, and rejects known legacy-field and asset drift. The loader application JARs package
  byte-exact models and textures and exact English names for all 14 IDs. The migrated Fabric
  consumers compile and datagen completes, but this gate does not individually assert every
  consumer mapping or behavior.
- `validateForgeMaterialItemRegistryMilestone` combines that static gate with the immutable
  [`material-item-registry-server-v11`](../evidence/forge-1.20.1/material-item-registry-server-v11)
  archive. A fresh `etherology-e2e-forge-server-1.20.1-v11` Java 17 Forge 47.4.9 dedicated server
  ran the `material-item-registry` scenario through
  `:forge:1.20.1:runRegistryFoundationServerProbe`; schema 7 passed all 163 ordered assertions.
- The v11 run captured exact IDs, vanilla runtime classes, maximum counts, and maximum-count
  `ItemStack` NBT ID/count/key round trips after server-data load, rechecked them at server start,
  performed a real `reload`, and proved those observations remained stable. Its cumulative
  assertions re-proved the v10 particle, v7 enchantment, game-event/tag, loot-condition, and
  Ether-source contracts. The world saved, `stop(false)` completed, the process exited zero, and
  the log was clean of fatal or forbidden client-startup markers.
- The archive binds profile-manifest SHA-256
  `63ee2c8707f276cc87df2e0b162b2f3174e1fe1b3d689b26135298154ed1b171`, report SHA-256
  `f3cc85b8514704f6c789e5abbdb835ede8aea062f5bae5b7ade0ab20da26bd4f`, server-log SHA-256
  `ee447e0dbf8a5c823f51a20bdeb2f115b6baa7ac58d9db15535fc50f6d8cc4f4`, and archive-manifest
  SHA-256 `9f5ff60298d9066e92c3f3e8ff6e5ab97fba5b35369f6ed09e1359bed7afe347`.
- This proof is limited to registry properties and in-process `ItemStack` NBT
  round-trip/reload stability. It does not execute player `/give`, a second JVM or server restart,
  Forge fuel registration, creative-tab placement, recipes, client rendering/gameplay, or the
  remaining consumer mechanics.

Accepted bounded metal-block-registry milestone:

- `SharedMetalBlocks` and `SharedMetalBlockItems` are the sole Common deferred declaration owners
  for exactly `azel_block`, `ethril_block`, and `ebony_block` as vanilla `Block` instances and
  corresponding mapped `BlockItem`s. The former eager `DecoBlocks` fields are absent.
- The static gate checks exact settings, deferred attachment order, block-item map enrollment, and
  packaged models/textures/names, self-drop tables, compression/decompression recipes, and selected
  mining/beacon tags across the Common and loader artifacts. In the `mineable/pickaxe` and
  `needs_iron_tool` files packaged by this bounded Forge slice, still-unported IDs are optional
  while the three accepted metal-block IDs remain required. `needs_stone_tool` is unchanged and
  outside this Forge resource slice.
- `validateForgeMetalBlockRegistryMilestone` combines that current-artifact proof with the
  immutable capture-time
  [`metal-block-registry-server-v13`](../evidence/forge-1.20.1/metal-block-registry-server-v13)
  archive. A fresh repository-owned `etherology-e2e-forge-server-1.20.1-v13` Java 17 Forge 47.4.9
  dedicated server ran report schema 8 with all 188 ordered assertions passing and launcher exit
  code zero.
- The v13 run checked exact block/item classes and mappings, properties, selected tags, and stack
  NBT, directly placed the three exact block IDs at bounded world positions, and proved those
  observations stable through a real `reload`. The world saved and the server stopped normally.
  Its cumulative assertions re-proved v11 and the earlier registry/reload contracts.
- The archive binds profile-manifest SHA-256
  `c4112b8c4073168af573b4bb555d2f1d775ce57911046aaf352e8f569f10bd11`, report SHA-256
  `b6b48f567fda9f3b170c4bd0407c786123bf0487ef8248216bf92f36b681d452`, server-log SHA-256
  `f894973c95660d7a5b9e075a05b09874b27d63321c55d4513dfadee648c06ca4`, and archive-manifest
  SHA-256 `0dae07208c3b14bab4a6af4f6a5c71f8c98ba76147cba7da20fb246f3377a9cc`.
- The v12 profile was consumed by a failed tag-load diagnostic and has no accepted archive. After
  the optional-reference fix, v13 used a distinct fresh runtime.
- This bounded proof does not establish restart persistence, player `/give` or player placement,
  mining/drop behavior, beacon activation, recipe execution, creative-tab interaction, client
  rendering, the complete port, or release readiness. The archive proves capture-time userdev
  observations and payload integrity only; it does not cryptographically bind later sources or
  rebuilt JARs.

Accepted bounded food-item-registry milestone:

- `SharedFoodItems` is the sole Common deferred owner of the plain
  `etherology:forest_lantern_crumb` vanilla `Item`. Its food component has hunger 3, saturation
  modifier 2.0, no always-edible behavior, no status effects, and no recipe remainder. Fabric's
  retained `EItems.FOREST_LANTERN_CRUMB` is an alias of that supplier, not a second registration.
- The exact English and Russian names are `Mushroom Crumb` and `Грибной мякиш`. The model and
  texture SHA-256 values are
  `6ba61590386580a2f70526313d501eec44cd88ff9d86cd1d13d9092b41a42fbe` and
  `44f9d92ccf36c3555d21ace9eea0268e43eb4a8e95f1e81b74f22977d4928d65`.
  The three recipes and their three matching advancements were deferred at this checkpoint; the
  later accepted Forest Lantern slice packages and verifies all six resources.
- `validateForgeFoodItemRegistryMilestone` combines the static/resource/artifact gate with the
  immutable capture-time
  [`food-item-registry-server-v14`](../evidence/forge-1.20.1/food-item-registry-server-v14)
  archive. A fresh repository-owned `etherology-e2e-forge-server-1.20.1-v14` Java 17 Forge 47.4.9
  dedicated server used a tracked 1192-byte profile manifest with SHA-256
  `442d11e6a5072c8ec418bced406529dc15caa6d4f4d4c5c68edc8a79ce2e493d`; report schema 9 passed
  all 219 ordered assertions.
- Real `ServerPlayerEntity` instances `EtherFoodStart` and `EtherFoodReload` proved exact
  registry/class/food/stack/save-representation state, hunger `10 → 13`, saturation `0 → 12`,
  stack `2 → 1`, retention of the same `ItemStack` instance, and exact stability through a real
  `reload`. The cumulative run re-proved v13 and every earlier registry/reload contract. The world
  saved, the server stopped normally, and the launcher exited zero.
- The archive binds archive-manifest SHA-256
  `eacab05996569a78a55ed21117d2a7e0768d87c258c93757cdc4ab4205881927`, report SHA-256
  `3ccd86d4ef6f5b31fd37b686254bdf427e351019c778d2d8e3f03958de0e1f6c`, launcher-result SHA-256
  `4ec610688bf030ea722772c40d871ec1b954fcbc0b15f90cbad41acec6278ad0`, completion-marker SHA-256
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`, and server-log SHA-256
  `e2bba0e01d27a7f9f4511d00b2d3fa3a12c8d9fa4b5aaa74cca09ca536d599db`.
- All 87 pinned v14 Python runner/verifier safety tests pass. Separately,
  `:forge:1.20.1:serverProbeSafetyInterlockTest` passes 20 non-Minecraft Gradle interlock fixture
  cases and is wired into `forgeFoodItemRegistryServerSafetyTest`; these are distinct suites, not
  one 107-test count. The sealed archive durably blocks normal
  reprovisioning and environment checks for that identity, even after runtime removal; the raw
  Gradle launch also requires the runner's exact token/lock, profile marker, and pristine evidence
  directories. These are accidental-misuse interlocks, not provenance authentication;
  same-account adversarial or concurrent filesystem mutation and TOCTOU are outside their threat
  model.
- This headless proof has no client visual evidence and does not establish a second JVM/restart,
  multiplayer, the deferred recipe behavior, creative-tab interaction, client rendering/gameplay,
  the complete registry, or release readiness. The broader authoritative registry spine remains
  the next forward gate.

Accepted bounded Warp Counter static milestone:

- `SharedToolItems` is the sole Common deferred owner of `etherology:warp_counter`. It constructs
  exactly one plain vanilla `Item` with maximum stack count 1. Fabric attaches the same owner and
  retains `ToolItems.WARP_COUNTER` only as a resolved alias, so there is no second item
  registration.
- Both loader artifacts package the canonical fifteen-frame static presentation: one base model
  with 15 ordered overrides, 14 child models, and the 15 textures numbered 0 through 14. They also
  package the exact `Warp Counter` and `Варп счётчик` names and the original shaped recipe plus its
  recipe-unlock advancement.
- `:forge:1.20.1:validateForgeWarpCounterStaticMilestone` is the positive bytecode,
  cross-artifact, accepted-data, and resource gate. Packaging the override declarations does not
  install or prove the corruption-driven model predicate. Predicate evaluation, Warp Counter
  sound playback, the corruption/component/synchronization graph, native client or server E2E,
  and wider gameplay parity remain deferred.
- The remainder of the authoritative registry spine is still the next fail-closed forward
  milestone; this static item slice does not advance native or release readiness.

Implementation:

- Make Architectury deferred registries the single declaration path.
- Add common compile/annotation-processing support for Lombok before moving Lombok-owned classes.
- Add loader-matched GeckoLib common and Forge dependencies before moving Gecko-owned behavior.
- Add MixinExtras explicitly to Forge before enabling any retained MixinExtras injector.
- Keep creative tabs, fuel registration, reload listeners, lifecycle hooks, and trade hooks on
  Architectury APIs where those APIs preserve the required behavior.
- Use a Forge brewing registration adapter and Forge or vanilla wood/block-set registration. Keep
  the custom sculk frequency deferred until a supported Forge 47 path can preserve it exactly;
  Fabric's supported frequency registry is not a portable implementation.
- Register commands through Architectury's command event or Forge `RegisterCommandsEvent`.

Access and mixin blockers:

- Peach signs require either the current valid-block mutation in `BlockEntityTypeMixin` or custom
  sign and hanging-sign block-entity types.
- Peach flammability requires Forge block-state flammability hooks, a platform helper, or one
  narrow access transformer. The complete Fabric access widener must not be imported.
- Block-entity builders must receive resolved blocks only after the relevant suppliers exist.

Remaining full-slice proof:

- Exact Fabric/Forge registry-ID manifest comparison.
- Duplicate-ID and premature-supplier-resolution unit tests.
- Dedicated-server datapack startup with no missing registry or codec errors across the full
  accepted catalog; the bounded registry-foundation server proof does not satisfy this broader
  check.
- `/give`, placement, save, and reload smoke coverage for every ID in the accepted catalog.

This slice blocks broad content migration. The already-started storage mechanic may finish first
because its distinct shared declarations are intentionally bounded; those declarations must be
folded into the authoritative catalog during convergence.

### Slice 1: Forge state and capability backend

Source owners:

- `EtherologyComponents` and its four handles:
  - `CORRUPTION` on chunks.
  - `ETHER` on living entities.
  - `TELDECORE` on players.
  - `VISITED` on players.
- `CorruptionComponent`, `EtherComponent`, `TeldecoreComponent`, and `VisitedComponent`.
- The existing Fabric Cardinal Components adapters, which remain Fabric-only.

Forge implementation:

- Register native capabilities with `CapabilityManager`, `CapabilityToken`, and
  `RegisterCapabilitiesEvent`.
- Attach serializable providers through `AttachCapabilitiesEvent<Entity>` and
  `AttachCapabilitiesEvent<LevelChunk>`.
- Invalidate every `LazyOptional` at owner invalidation.
- Copy the intended player state through `PlayerEvent.Clone`. Teldecore and Visited must retain
  their Fabric `ALWAYS_COPY` behavior. Ether death behavior must be decided from the original
  behavior and tested rather than inferred.
- Tick living/player state from Forge tick events.
- Track loaded server chunks through chunk load/unload events and tick their corruption state at
  the end of the level or server tick. Forge has no native per-chunk capability tick event.
- Sync initial login, start-tracking, respawn, and every state mutation to client-side replicas.

Alternative:

Corruption can be refactored into level-owned `SavedData` with its own scheduler. That is a larger
semantic change. A loaded-chunk tracking set is closer to the current component behavior, but its
tests must prove that unloaded chunks stop ticking and are removed from the set.

Save compatibility risk:

Cardinal Components and Forge capabilities use different outer serialization containers. If a
world must move between loaders, define a stable Etherology-owned compound and explicit migration.
Equivalent values inside different loader containers are not cross-loader save compatibility.

Proof:

- NBT and copy unit tests for all four state types.
- Player death/respawn and relog checks.
- Chunk save, unload, reload, and no-unloaded-tick checks.
- Initial and mutation sync with a second client.
- Client HUD checks that read only the synchronized replica.

### Slice 2: shared packet transport

Source owners:

- `EtherologyNetwork`, `AbstractC2SPacket`, and `AbstractS2CPacket`.
- S2C packets: `StartBlockAnimS2C`, `StopBlockAnimS2C`, `SwitchBlockAnimS2C`,
  `RedstoneLensStreamS2C`, and `RemoveBlockEntityS2C`.
- C2S packets: `StaffMenuSelectionC2S`, `StaffTakeLensC2S`, `QuestCompleteC2S`, and the four
  Teldecore selected/page/tab/opened values owned by `EntityComponentC2SType`.
- Client owners: `EtherologyNetworkClient`, `S2CHandlers`, and `ClientEtherProxy`.

Implementation:

- Move payload codecs and handlers into shared records.
- Use Architectury `NetworkManager.registerReceiver`, `sendToPlayer`, `sendToPlayers`, and
  `sendToServer`.
- Queue every world or entity mutation through `PacketContext.queue`.
- Introduce a two-loader tracking-recipient boundary. Fabric uses `PlayerLookup`; Forge uses
  tracking entity/chunk recipients through `PacketDistributor` or an equivalent native query.
- Never substitute a dimension-wide broadcast for tracking recipients.
- Keep all C2S operations server-authoritative. Validate staff ownership and contents, enum
  ordinals, page values, identifier sets, and quest eligibility.

Proof:

- Round-trip and malformed-payload tests for all 12 logical channels.
- Wrong-side and scheduling tests.
- Malicious C2S rejection tests.
- Two-client tests for tracking range, sender inclusion, and exclusion variants.
- Native storage animation and Redstone Lens particle evidence when their owning slices land.

### Slice 3: complete ethereal storage — bounded milestone accepted

The deliberately bounded shared-foundation vertical in this slice is implemented and accepted.
Its declarations remain temporary owners that must be folded into the authoritative registry spine
during convergence; acceptance here does not establish parity for the channel/network graph or any
other Forge gameplay slice.

Source owners:

- Shared foundation: `EtherealStorageFoundationBlock` and
  `EtherealStorageFoundationBlockEntity`.
- Canonical behavior reference: `EtherealStorageBlock`, `EtherealStorageBlockEntity`, and
  `EtherealStorageScreenHandler`.
- Shared registries: `SharedBlocks`, `SharedBlockEntities`, `SharedItems`, the new
  `SharedScreenHandlers`, and `EtherologyBootstrap`.
- Supporting behavior: `ListBackedInventory`, `TypedSlot`, `ClosedSlot`, `GlintItem`,
  `EtherGlint`, `ComponentTypes.STORED_ETHER`, and the minimal Ether storage/counter contract.
- Forge client completion: `EtherealStorageFoundationScreen`,
  `EtherealStorageFoundationModel`, `EtherealStorageFoundationRenderer`, the canonical Gecko
  resources, and Gecko's built-in trigger synchronization. This vertical does not add a parallel
  Etherology animation packet.

Mechanic contract:

- Persist `storage_ether` and exactly four item slots.
- Slots zero through two accept the storage input item; slot three is server-maintained display
  state and cannot be directly modified by the player.
- `Inventory.clear()` clears those three real inputs while retaining `storage_ether` and rebuilding
  the derived display slot immediately.
- Persist per-Glint Ether under `etherology:components/stored_ether`, bounded to 128 per Glint.
  Charge at most one Ether every fifth server tick, fill input slots from zero through two, and
  keep the display slot derived from internal Ether only.
- Open the menu only from the logical server and let the block entity own the inventory.
- Implement correct quick-move, viewer lifecycle, dirty marking, and break-drop behavior.
- Expose vanilla sided inventory behavior plus cached Forge `ITEM_HANDLER` wrappers for the
  unsided view and all six faces. Expose only the three insertion slots, reject extraction, and
  invalidate every `LazyOptional` with the block entity.
- Register the menu with a shared deferred `ScreenHandlerType`. Register its Forge client screen
  from enqueued client setup and keep the class distribution-safe.
- Drive one shared Gecko controller from first-viewer open and final-viewer close, using Gecko's
  tracking synchronization for the open and close triggers.

Accepted bounded proof:

- Ether and four-slot NBT round trip.
- Typed slot insertion and display-slot rejection, compiled quick-move structure, and Forge
  item-handler automation behavior.
- Viewer-count transitions, first/final-viewer guards, and native single-viewer animation.
- Compiled logical-server break/drop ownership for the three input slots.
- Place, open, insert, mutate ether, save, restart, and reopen in the native E2E scenario.
- Before/after menu captures.

Still required before full storage parity can contribute to release readiness:

- a live hopper insertion sequence;
- two concurrent real viewers proving first-open and final-close behavior;
- a native block break proving that the three real inputs drop exactly once.

The positive `validateForgeStorageParityMilestone` now covers the accepted bounded Common and Forge
proof above; it does not claim the three deferred live checks. The packaged `ethereal-storage`
scenario adds a real isolated Forge 47.4.9 client and
integrated-world run: 34 of 34 assertions passed in 438 client ticks, with five native framebuffers,
forced save, disconnect, restart, exact persistent-state comparison, and menu reopen. Its frozen
record is the
[`Forge 1.20.1 runtime evidence`](../evidence/forge-1.20.1/README.md).

The bounded `validateForgeChannelNetworkMilestone` has since been accepted with its own fresh
native evidence; the storage run was not reused as channel proof. The bounded
`validateForgeSoundRegistryMilestone`, SharedGameEvents/SharedLootConditions registry-foundation,
Ether-source reload, SharedEnchantments, SharedParticleTypes, SharedMaterialItems, and
SharedMetalBlocks and SharedFoodItems registry milestones are also accepted, so the broader authoritative registry spine is now the first
incomplete stage in the release dependency graph.
The unconditional
`validateForgeReleaseReadinessMilestone` remains behind every forward gate and cannot be satisfied
by class or method stubs or by reusing either bounded archive. The release graph must never remove
or relax a stage merely to allow `remapJar`.

### Slice 4: static content and storage utilities

Source owners:

- Static content: `DecoBlocks`, `ExtraBlocksRegistry`, and the `beamer`, `peach`, `thuja`, and
  `forestLantern` block packages.
- Utility/storage content: the `furniture`, `closet`, `shelf`, `crate`, `jug`, `spill_barrel`,
  `samovar`, `tuningFork`, and `arcanelightDetector` block packages.
- Closet and Crate menu handlers plus their client screens and renderers.

Implementation:

- Declare all block, item, block-entity, and menu IDs in the authoritative shared catalogs.
- Keep the Forest Lantern jump callback on Architectury's event.
- Preserve tuning-fork resonance registration and tag behavior now; keep its custom Forge sculk
  frequency deferred until Forge 47 exposes a supported equivalent to Fabric's frequency registry.
- Expose Forge item handlers for real inventories.
- Register GeckoLib renderers on the Forge client when required.

Proof:

- The `storage-utilities` E2E scenario covers every interaction, inventory save/drop behavior,
  redstone/game-event behavior, vanilla hopper behavior, Forge capability interoperability, and
  before/after captures.

### Slice 5: Ether graph and machines — bounded channel foundation accepted

Only the shared ethereal-channel foundation within this slice is accepted. The channel case,
particles, client ticker, loot and recipe data, forks, sockets, furnaces, generators, Levitator,
and the wider graph remain incomplete.

Source owners:

- `EtherStorage`, `EtherPipe`, `EtherFork`, `EtherDisplay`, `EtherCounter`, and `EtherGlint`.
- The `etherealChannel`, `etherealFork`, `etherealSocket`, `etherealFurnace`, `generators`, and
  `levitator` block packages, plus `ChannelCase` and `ChannelShapes`.
- The Ethereal Furnace menu.
- `EtherSourceLoader`, `EtherSources`, and their deserializer.

Implementation:

- Keep Ether as Etherology's own unit and transfer semantics. Forge Energy is not a compatible
  replacement.
- Retain the accepted Common-owned reload listener and default Ether-source data.
- Use the tracking-recipient packet boundary for particles and Gecko animations.
- Register every machine block entity, menu, particle, renderer, and codec through the shared
  catalogs and Forge client events.

Dependencies:

Completing the full graph still requires storage, shared packets, authoritative deferred
block-entity registration, and the Forge client registration spine. The bounded foundation uses
the accepted storage endpoint and deliberately excludes packet-driven particles and other client
effects.

Accepted bounded proof:

- One Common listener/default-data owner, exact 23-entry startup data, and exact 24-entry result
  after a real Forge `reload` override/addition, frozen by the historical schema-4, 72-assertion
  v6 record and re-proved cumulatively by the historical schema-5, 95-assertion v7 and schema-6,
  138-assertion v10, schema-7, 163-assertion v11, schema-8, 188-assertion v13, and schema-9,
  219-assertion v14 and schema-10, 266-assertion v16 records, plus the current schema-11,
  310-assertion v19 record. Registry and tags remain stable; loot-condition registry and evaluated
  behavior remain stable while the
  probe loot-table instance is replaced.
- Shared Ether storage/pipe contracts, exact `ethereal_channel` block and block-entity IDs, storage
  endpoints, persistent direction and Ether state, and NBT reconstruction.
- Redstone-gated retention followed by exact one-Ether transfer on the fifth-tick cadence, with no
  reverse motion.
- Missing-output evaporation of exactly `0.2` Ether on the fifth-tick cadence, including natural
  evaporation-flag clearing after reactivation.
- Native Forge support for a vanilla wall lever attached to the channel, with exact connection
  topology retained after restart.
- Fresh isolated `etherology-e2e-forge-1.20.1-v11` proof with 42 of 42 assertions, three native
  `1920x1080` framebuffers, 120 consecutive exact mirror/render/camera frames before each capture,
  force-save, disconnect, restart, and exact persistent-state comparison. Its immutable record is
  the [`Forge 1.20.1 runtime evidence`](../evidence/forge-1.20.1/README.md), which does not claim
  that later sources or rebuilt artifacts still match the capture.

`validateForgeChannelEvidenceArchiveIntegrity` continues to accept that immutable capture and its
capture-time provenance. The separate `validateForgeChannelCurrentArtifactDiagnostic` now fails
because the later sound work changed the whole production JAR. That expected digest mismatch does
not imply a Channel regression or prove equality for current artifacts; only another fresh
isolated native run can establish current equality.

Remaining full-slice proof:

- Furnace and other machine consumption of the accepted Ether-source values.
- Fixed-tick storage to channel to fork to socket/furnace flows.
- Direction, redstone gate, cross-evaporation, generator stall/restart, and glint fill/drain.
- Ether conservation across chunk unload/reload.
- Server/client state, particles, and animations with two clients.
- The complete `ether-network` E2E scenario.

### Slice 6: recipes, synced data, aspects, progression, and workstations

Registry/data owners:

- `RecipesRegistry`, `FeyRecipe`, `FeyInputRecipe`, `FeyRecipeSerializer`, and every runtime
  serializer/type in `recipes/alchemy`, `recipes/empower`, `recipes/jewelry`, `recipes/matrix`,
  and `recipes/staff`.
- `ResourceReloaders`, `RegistriesRegistry`, `AspectsLoader`, and `magic/aspects`.
- Teldecore `data`, `task`, and `content` owners.

Mechanic owners:

- The `brewingCauldron`, `pedestal`, `sedimentary`, `empowerTable`, `matrix`, `inventorTable`,
  `jewelryTable`, and `seal` block packages.
- Their menu handlers, screens, renderers, sounds, and particles.

Forge implementation:

- Deferred-register recipe serializers and recipe types.
- Register ASPECTS, CHAPTERS, and TABS in `DataPackRegistryEvent.NewRegistry` with both the data
  codec and network codec.
- Invalidate aspect caches from a common reload listener or a Forge datapack reload hook.
- Read synchronized client registries through the active client connection registry manager.

Risk:

An unsynchronized datapack registry can appear correct on the server while breaking Teldecore and
Oculus on clients. All three registries are required client data.

Proof:

- Serializer round-trip and malformed-data tests.
- `/reload`, recipe matching, consumption, cancellation, and result tests.
- Client registry synchronization and reconnect tests.
- Server-authoritative quest completion and persistence.
- Native `alchemy`, `armillary`, `seals-aspects`, `staff-lenses`, and `progression-oculus`
  scenarios.

Recipe builders and `FeyRecipeJsonProvider` are datagen tooling. They do not block Forge runtime.

### Slice 7: items, combat, equipment, entities, and boats

Source owners:

- `EItems`, `ToolItems`, `ArmorItems`, all runtime `item` classes, and the `staff`, `lens`, and
  `corruption` magic packages.
- `EntityRegistry`, `RedstoneChargeEntity`, the peach boat helpers, and boat mixins.
- Redstone Charge renderer, arm poses, Prana Vision renderer/HUD, staff UI, and staff model.

Forge implementation:

- Reimplement `IronShield` on vanilla `ShieldItem` and Forge tool-action/item hooks. Retain the
  Etherology shield cone and reflection behavior. Fabric Shield Lib is not a Forge dependency.
- Use Curios Forge for the Prana Vision face/head slot, ticking, rendering, save, and death
  behavior. A vanilla helmet redesign is the only dependency-free alternative and is not
  behaviorally equivalent.
- Spawn Redstone Charge through Architectury's add-entity packet or a Forge entity spawn packet.
- Replace Fabric enum injection with Forge `UseAction.CUSTOM`,
  `BipedEntityModel.ArmPose.create`, and `IClientItemExtensions.getArmPose`.

Boat decision:

`BoatEntity.Type` is not a Forge extensible enum. Either retain and aggressively smoke-test the
current enum-values/codec/BY_ID mixin, or replace peach boats with custom entity types. The custom
entity route is larger but safer. The decision must be explicit before implementation.

Proof:

- Shield angle, projectile reflection, axe behavior, and enchantment behavior.
- Two-handed and staff poses in first and third person.
- Staff construction, lens insertion/removal/mode, malformed packet rejection, and item NBT
  persistence.
- Prana Vision equip, relog, death, tick, and rendering.
- Redstone Charge spawn, tracking, save, and reload.
- Peach and chest boat placement, passenger behavior, save, and reload.
- Native `combat-equipment` and `staff-lenses` scenarios.

### Slice 8: world generation

Source owners:

- `WorldGenRegistry`, `TreesRegistry`, `BiomesGen`, `ConfiguredFeaturesGen`,
  `PlacedFeaturesGen`, and `StructuresGen`.
- Custom feature, placement modifier, tree placer/decorator, and pool-element classes.
- Promoted worldgen JSON and structure NBT resources.

Forge implementation:

- Deferred-register custom `Feature`, placement modifier, trunk placer, foliage placer, tree
  decorator, and structure-pool element codecs.
- Use Forge biome modifier JSON for key/tag based feature additions.
- Use a custom Forge `BiomeModifier` codec for exact temperature predicates.
- Use Forge's extensible grass-color enum hook instead of Fabric ASM and the grass access widener.

Golden Forest decision:

Plain Forge biome modifiers cannot reproduce Biolith's 30-percent replacement of Birch Forest and
Old Growth Birch Forest. Choose one approach and document its expected parity:

1. TerraBlender Forge climate insertion. This is maintainable, but fixed-seed distribution will
   not be byte-for-byte identical.
2. A Forge-specific biome-source/climate mixin. This can approach the original replacement rule,
   but is a high-risk core-worldgen injection.

The port must not claim exact distribution parity if it chooses TerraBlender.

Proof:

- Fixed-seed coordinate and distribution audit over new chunks.
- Attrahite, Thuja, every seal biome/temperature case, Golden Forest, peach trees, bonemeal, and
  monolith placement/rotation.
- Save/reload and native screenshots in the `golden-forest` scenario.

### Slice 9: Forge client foundation and completion

The registration spine should be established early, while each preceding mechanic adds its own
screen or renderer only when its server behavior is accepted.

Source owners:

- `EtherologyClient`.
- `BlockRenderersRegistry`, `ScreenRegistry`, `BlockRenderLayerMapRegistry`,
  `ClientParticleRegistry`, `ColorProvidersRegistry`, `EntityClientRegistry`,
  `KeybindsRegistry`, `RenderingRegistry`, `EventsClientRegistry`, and `ModelPredicates`.
- Loader-neutral screens, particles, Gecko renderers, entity renderers, and block-entity
  renderers.
- `SealBlockRenderer`, `RevelationRenderer`, `OcularOverlay`, `StaffIndicator`, and the Teldecore
  and Oculus UI.

Forge event replacements:

- `FMLClientSetupEvent` with enqueued menu-screen, model-predicate, and render-layer setup.
- `EntityRenderersEvent.RegisterRenderers`.
- `RegisterParticleProvidersEvent`.
- Block and item color registration events.
- `RegisterKeyMappingsEvent`.
- `RegisterGuiOverlaysEvent` and the matching overlay render events.
- Forge client tick events.
- `RenderLevelStageEvent`, with the chosen stage verified against Fabric's final world-render
  behavior.
- `ModelEvent.RegisterAdditional` and `ModelEvent.ModifyBakingResult`.

High-risk model work:

`EtherologyModelProvider`, `MultiItemModel`, and `StaffModel` depend on Fabric's model-loading and
renderer APIs. Forge must register every staff part, then install a Forge delegating `BakedModel`
that selects and composes the parts through item overrides or render passes. FabricBakedModel must
not be compiled into Forge.

UI replacements:

- Rewrite Owo-owned `AspectComponent`, `OculusItemClient`, `ScaledLabelComponent`, and
  `LensWidget` with vanilla widgets and draw APIs.
- Replace `FabricLoader.isModLoaded` with Architectury `Platform.isModLoaded`.
- Replace every direct `ClientPlayNetworking` send with the shared transport.

Proof:

- Missing-model, missing-texture, and fatal client-log gate.
- Resource reload and GUI-scale checks.
- Normal and fabulous graphics checks.
- First-person and third-person equipment/staff captures.
- Every menu, HUD, particle, entity renderer, block-entity renderer, and world overlay.
- A render-thread crash-free soak after all scenario captures.

## Dependency policy

Required for Forge gameplay parity:

- Forge 1.20.1-47.4.9.
- Architectury API 9.2.14.
- GeckoLib 4.8.4 for the shared compile API and Forge GeckoLib 4.7.4 at runtime, constrained by
  metadata to the compatible `[4.7.4,5)` range.
- MixinExtras 0.5.0 whenever retained MixinExtras injectors are enabled.
- Lombok 1.18.36 as compile-only plus annotation processor for common sources that use it.
- Curios Forge for Prana Vision parity.

Conditional:

- TerraBlender Forge only if the maintainable Golden Forest climate-insertion option is selected.

Fabric-only implementations that must not become Forge runtime dependencies:

- Cardinal Components adapters.
- Trinkets.
- Fabric Shield Lib.
- Biolith.
- Fabric ASM/ClassTinkerers.
- Owo registration/UI adapters.
- MixinSquared compatibility.

Optional integrations:

- `client/compat/rei` contains 18 classes. Exclude it from the initial Forge source and runtime;
  later add Forge plugin discovery with optional/compile-only dependency metadata.
- `client/compat/emi` contains 12 classes and follows the same policy.
- `client/datagen` contains 13 Fabric datagen classes. Fabric remains the asset generator and
  Forge consumes the promoted JSON. A Forge datagen lane is required only for Forge-only generated
  resources, not runtime.
- Cloth Config, Mod Menu, and MidnightLib are not required gameplay dependencies.

## Forge mixin policy

Forge now has a minimal Forge-owned `etherology.forge.mixins.json` for the accepted native channel
lever support. Extend that configuration only with mixins required by an owning slice and its
proof. Do not include either broad Fabric configuration unchanged.

Retain initially where no clean Forge hook preserves the behavior:

- Main: `AbstractBlockStateMixin`, `BlockEntityTypeMixin`, `BoatEntityMixin`,
  `BoatEntityTypeAccessor`, `BoatEntityTypeMixin`, `ChestBoatEntityMixin`,
  `DispenserBlockMixin`, `GrassBlockMixin`, `ItemStackMixin`, `LivingEntityAccessor`,
  `LivingEntityMixin`, `PlayerEntityMixin`, `RedstoneViewMixin`, `RedstoneWireBlockMixin`,
  `VibrationListenerAccessor`, and `WallMountedBlockMixin`.
- Client: `GameRendererAccessor`, `GameRendererMixin`, `HeldItemRendererMixin`,
  `InGameHudMixin`, and `PlayerHeldItemFeatureRendererMixin`.

Prefer Forge APIs and remove these injections when the owning slice is ported:

- `AxeItemMixin`: Forge tool actions or modified-state hook.
- `EnchantmentHelperMixin` and `EnchantmentMixin`: Forge item/enchantment applicability hooks.
- `EntityHitResultAccessor` and `ProjectileEntityMixin`: `ProjectileImpactEvent` where it preserves
  the exact behavior.
- `FlowerPotBlockMixin`: reject the seed from its item behavior.
- Registry invokers for foliage, trunk, decorator, placement, and structure-pool element types:
  deferred registration through accessible Forge constructors.
- `ShearsItemMixin`: tool action/mining hooks.
- `SmithingTemplateItemAccessor`: Etherology-owned tooltip text and formatting constants.
- `AbstractClientPlayerEntityMixin`: Forge FOV event.
- `BipedEntityModelMixin` and `PlayerEntityRendererMixin`: Forge arm poses and item client
  extensions.
- `ClientPlayerInteractionManagerMixin`: Forge reach attributes.
- `DrawContextAccessor`, `HandledScreenMixin`, and `ScreenAccessor`: Forge tooltip and container
  screen events.
- `InGameHudAccessor`: Forge GUI overlay drawing.
- `ItemRendererMixin` and `ModelLoaderMixin`: Forge model events and baked-model render passes.
- `KeyBindingAccessor` and `StickyKeyBindingAccessor`: owned movement mappings and public key APIs.
- `TessellatorAccessor`: public Forge tessellator/buffer API.
- `TooltipComponentMixin`: `RegisterClientTooltipComponentFactoriesEvent`.

Exclude from Forge:

- `FabricShieldLibMixin` and `FabricShieldLibClientMixin`.
- `EarlyRisers`.
- `EMixinCanceller`.
- The Fabric grass-color enum bridge in `GoldenForestGrassModifier`.

Dead or datagen-only mixin owners must not enter the Forge runtime config:

- Confirmed dead: `DefaultedListAccessor`, `ModelAccessor`, `WorldRendererAccessor`,
  `TagEntryAccessor`, and `TagGroupLoaderMixin`. `TagExcludeUtil` is deprecated and has no caller
  that populates its exclusions.
- Datagen-only: `BlockFamilyMixin`, `BlockTexturePoolMixin`,
  `OverworldBiomeCreatorAccessor`, and `TreeConfiguredFeaturesAccessor`.

High-risk retained targets require mandatory-injection startup checks on both a dedicated server
and client. The most fragile are boat enum initialization, player/living combat local captures,
the redstone injections, dispenser behavior, `GrassBlockMixin`, and private render-pipeline
injections.

## Access widener replacement

The Fabric access widener contains runtime access for:

- `InGameHud.HeartType`.
- Bundle tooltip row and column methods.
- `FireBlock.registerFlammableBlock`.
- `KeyBinding.boundKey`.
- `BiomeEffects.GrassColorModifier`.

It also contains unused or datagen-only access for `Model.createTextureMap` and
`KeyBinding.timesPressed`.

Prefer Forge enum, tooltip, input, block flammability, and overlay APIs. If a runtime member still
requires access after the owning feature is rewritten, add only that member to
`META-INF/accesstransformer.cfg`. Forge currently neither transforms nor packages the Fabric
access widener.

## Test and release-gate discipline

Every slice has four levels of proof:

1. Pure unit tests for codecs, arithmetic, state transitions, malformed input, and NBT.
2. Loader integration tests for registrations, lifecycle, resource packaging, collision safety,
   and canonical Fabric byte preservation.
3. Native dedicated-server and client startup checks for registries, datapacks, mixins, and
   classloading.
4. The matching packaged E2E scenarios and, where the contract is visual, screenshots. A headless
   dedicated-server registry proof has no screenshot requirement.

The ordered forward gates advance in the same change that completes a positive milestone:

1. Require the just-completed mechanic positively.
2. Keep an explicit next concrete incomplete mechanic in the release dependency graph.
3. Make `verifyForgePortGateClosed` report the first incomplete stage without making that
   diagnostic task an artifact dependency.
4. Keep `validateForgePortInputs`, `remapJar`, and all publishing tasks dependent on every positive
   gate and every declared forward gate, including the unconditional final native-readiness
   backstop.

Current positive gates run real Common, Fabric, and Forge test/compile paths and inspect compiled
classes and loader-transformed artifacts; they do not rely solely on source/comment tokens.
Compilation, deterministic bytecode structure, and resource presence still cannot replace native
gameplay evidence. Conversely, native evidence cannot excuse missing deterministic unit and
integration tests.

The bounded storage, channel-foundation, SharedSounds registry/resource, SharedGameEvents,
SharedLootConditions, Ether-source reload, SharedEnchantments, SharedParticleTypes, and
SharedMaterialItems, SharedMetalBlocks, SharedMetalBlockItems, SharedFoodItems,
SharedForestLanternBlocks, SharedForestLanternBlockItems, and the SharedToolItems Warp Counter
static registry/resource gate are currently positive. The v4 registry-foundation, v6 Ether-source,
v7 enchantment, v10 particle, v11
material-item, v13 metal-block, v14 food-item, and v16 Forest Lantern archives remain historical
evidence. The accepted v16 run passed all 266 schema-10 assertions; the latest accepted cumulative
proof is Attrahite server v19, a schema-11 Forge 47.4.9 run with 310 of 310 assertions, including
the four-block Attrahite contract and the earlier Forest Lantern checks. The Forge v13 and
Fabric v24 packaged clients supply the separate accepted native
rendering and restart-persistence boundary for Forest Lantern.
The v12 diagnostic profile was consumed by required unported references in the packaged
`mineable/pickaxe` and `needs_iron_tool` files; those references are now optional, while
`needs_stone_tool` is unchanged and outside that Forge resource slice. v12 was never accepted or
archived. Both v16 and the current v19 proof remain narrower than multiplayer, creative-tab
interaction, natural immature attachment before the peach-log graph, full-catalog placement/save, and combat
acceptance. Each archive records capture-time userdev
observations and payload integrity; it does not cryptographically bind later sources or rebuilt
JARs. The broader
authoritative registry spine is the next fail-closed milestone. Enchanting applicability,
Peal shockwave behavior, projectile reflection, particle factories/rendering and emitted visuals,
client screenshots, full combat, sound
playback, and Forge's custom sculk frequency remain owned by later consumer-mechanic evidence; the
Warp Counter corruption-driven predicate and corruption graph, and the deferred portions of the
channel and machine graph likewise remain part of later full-slice
acceptance rather than being inferred from bounded registry proofs.

The final release-readiness task intentionally fails unconditionally until all required slices
pass, a dedicated server starts cleanly, an isolated Forge client completes the required scenario
matrix, fixed-seed worldgen checks pass, and no fatal mixin, access, model, registry, codec, or
synchronization marker remains
in the logs.

## Decisions that must be explicit

Before the relevant slice begins, record the selected alternative and its accepted parity:

1. Golden Forest: maintainable TerraBlender climate insertion or high-risk biome-source mixin.
2. Prana Vision: required Curios integration or a consciously incompatible vanilla-equipment
   redesign.
3. Peach boats: fragile vanilla boat-enum extension or custom boat entity types.
4. Component saves: loader-local persistence only or explicit Cardinal Components to Forge
   capability migration.
5. Staff rendering: native Forge composite baked model; there is no valid direct Fabric renderer
   reuse.

None of these decisions can be hidden behind a successful compile or an approximation described
as exact parity.
